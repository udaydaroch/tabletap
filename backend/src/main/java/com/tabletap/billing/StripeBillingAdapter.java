package com.tabletap.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tabletap.domain.AppUser;
import com.tabletap.dto.BillingUsage;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Adapter for Stripe's REST API (plain HTTP, no SDK). Every charge carries an Idempotency-Key made from
 * owner + month + restaurant, so if the job is retried Stripe returns the first result instead of
 * charging twice. Card details never touch TableTap: owners add them on Stripe's hosted billing portal.
 */
@Slf4j
public class StripeBillingAdapter implements BillingProvider {
    private final String secretKey;
    private final String currency;
    private final String apiBase;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public StripeBillingAdapter(String secretKey, String currency, String apiBase, ObjectMapper json) {
        this.secretKey = secretKey;
        this.currency = currency.toLowerCase(Locale.ROOT);
        this.apiBase = apiBase.replaceAll("/+$", "");
        this.json = json;
    }

    public String name() { return "stripe"; }

    public boolean enabled() { return true; }

    public String ensureCustomer(AppUser owner) {
        if (owner.getBillingCustomerId() != null) return owner.getBillingCustomerId();
        Map<String, String> form = new LinkedHashMap<>();
        form.put("email", owner.getEmail());
        form.put("name", owner.getFullName());
        form.put("metadata[tabletap_owner_id]", owner.getId().toString());
        String id = post("/v1/customers", form, "tabletap-customer-" + owner.getId()).get("id").asText();
        owner.setBillingCustomerId(id);
        return id;
    }

    public String chargeMonth(AppUser owner, BillingUsage usage, YearMonth month) {
        String customer = ensureCustomer(owner);
        String monthName = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + month.getYear();
        String base = "tabletap-" + owner.getId() + "-" + month;
        for (BillingUsage.Line line : usage.lines()) {
            item(customer, line.subtotal(), line.name() + " — " + monthName + " (" + line.orders() + " orders"
                + (line.floorPlanFee().signum() > 0 ? ", floor plan" : "") + ")", base + "-r" + line.restaurantId());
        }
        Map<String, String> invoice = new LinkedHashMap<>();
        invoice.put("customer", customer);
        invoice.put("collection_method", "charge_automatically");
        invoice.put("pending_invoice_items_behavior", "include");
        invoice.put("auto_advance", "true");
        invoice.put("description", "TableTap — " + monthName);
        String id = post("/v1/invoices", invoice, base + "-invoice").get("id").asText();
        log.info("Stripe invoice {} for owner {} ({}): {}", id, owner.getId(), month, usage.estimatedTotal());
        return id;
    }

    public String customerPortalUrl(AppUser owner, String returnUrl) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("customer", ensureCustomer(owner));
        form.put("return_url", returnUrl);
        return post("/v1/billing_portal/sessions", form, null).get("url").asText();
    }

    private void item(String customer, BigDecimal amount, String description, String idempotencyKey) {
        if (amount.signum() <= 0) return;
        Map<String, String> form = new LinkedHashMap<>();
        form.put("customer", customer);
        form.put("amount", amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()); // cents
        form.put("currency", currency);
        form.put("description", description);
        post("/v1/invoiceitems", form, idempotencyKey);
    }

    private JsonNode post(String path, Map<String, String> form, String idempotencyKey) {
        String body = form.entrySet().stream()
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(apiBase + path))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + secretKey)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (idempotencyKey != null) req.header("Idempotency-Key", idempotencyKey);
        try {
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode node = json.readTree(res.body());
            if (res.statusCode() / 100 != 2) {
                String msg = node.path("error").path("message").asText("HTTP " + res.statusCode());
                throw new BillingException("Stripe: " + msg);
            }
            return node;
        } catch (BillingException e) {
            throw e;
        } catch (Exception e) {
            throw new BillingException("Stripe unreachable: " + e.getMessage());
        }
    }

    public static class BillingException extends RuntimeException {
        public BillingException(String message) { super(message); }
    }
}
