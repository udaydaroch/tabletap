package com.tabletap.web;

import com.tabletap.billing.BillingProvider;
import com.tabletap.billing.MonthlyBillingJob;
import com.tabletap.domain.AppUser;
import com.tabletap.domain.Role;
import com.tabletap.security.CurrentUser;
import com.tabletap.service.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BillingController {
    private final BillingProvider provider;
    private final MonthlyBillingJob job;
    private final CurrentUser current;
    private final com.tabletap.repository.AppUserRepository users;

    /** Owner: open the provider's page to add a card / see invoices. */
    @PostMapping("/billing/portal")
    @Transactional
    public Map<String, String> portal(HttpServletRequest request) {
        AppUser u = current.get();
        if (u.getRole() != Role.OWNER) throw ApiException.badRequest("Billing applies to owner accounts");
        AppUser owner = users.findById(u.getId()).orElseThrow(); // managed entity, so a new customer id is saved
        String home = ServletUriComponentsBuilder.fromContextPath(request).path("/").toUriString();
        return Map.of("url", provider.customerPortalUrl(owner, home));
    }

    /** Admin: run (or retry) a month's billing, e.g. month=2026-09. */
    @PostMapping("/admin/billing/run")
    public List<Map<String, Object>> run(@RequestParam String month) {
        try {
            return job.run(YearMonth.parse(month));
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("month must look like 2026-09");
        }
    }
}
