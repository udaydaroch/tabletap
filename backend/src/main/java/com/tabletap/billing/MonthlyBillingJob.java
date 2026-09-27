package com.tabletap.billing;

import com.tabletap.domain.AppUser;
import com.tabletap.domain.BillingRun;
import com.tabletap.domain.Role;
import com.tabletap.dto.BillingUsage;
import com.tabletap.repository.AppUserRepository;
import com.tabletap.repository.BillingRunRepository;
import com.tabletap.service.BillingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** On the 1st of each month, charges every active owner for the previous month through the BillingProvider. */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlyBillingJob {
    private final AppUserRepository users;
    private final BillingRunRepository runs;
    private final BillingService billing;
    private final BillingProvider provider;
    private final TransactionTemplate tx;

    @Scheduled(cron = "0 0 3 1 * *", zone = "UTC")
    public void chargeLastMonth() {
        run(YearMonth.now(ZoneOffset.UTC).minusMonths(1));
    }

    /** Also callable by the admin (e.g. to retry failures). Owners already charged for the month are skipped. */
    public List<Map<String, Object>> run(YearMonth month) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (AppUser owner : users.findByRoleOrderByCreatedAtDesc(Role.OWNER)) {
            if (!owner.isActive()) continue;
            Map<String, Object> r = tx.execute(s -> chargeOne(owner.getId(), month));
            if (r != null) results.add(r);
        }
        return results;
    }

    private Map<String, Object> chargeOne(Long ownerId, YearMonth month) {
        AppUser owner = users.findById(ownerId).orElseThrow();
        BillingRun run = runs.findByOwnerIdAndBillingMonth(ownerId, month.toString()).orElse(null);
        if (run != null && "CHARGED".equals(run.getStatus()))
            return Map.of("owner", owner.getEmail(), "status", "ALREADY_CHARGED", "amount", run.getAmount());
        if (run == null) {
            run = new BillingRun();
            run.setOwner(owner);
            run.setBillingMonth(month.toString());
        }
        BillingUsage usage = billing.usageFor(owner, month);
        run.setAmount(usage.estimatedTotal());
        if (!provider.enabled() || usage.estimatedTotal().signum() <= 0) {
            run.setStatus("SKIPPED");
        } else {
            try {
                run.setProviderReference(provider.chargeMonth(owner, usage, month));
                run.setStatus("CHARGED");
                run.setError(null);
            } catch (RuntimeException e) {
                run.setStatus("FAILED");
                run.setError(e.getMessage());
                log.warn("Billing {} for owner {} failed: {}", month, ownerId, e.getMessage());
            }
        }
        runs.save(run);
        return Map.of("owner", owner.getEmail(), "status", run.getStatus(), "amount", run.getAmount(),
            "reference", run.getProviderReference() == null ? "" : run.getProviderReference(),
            "error", run.getError() == null ? "" : run.getError());
    }
}
