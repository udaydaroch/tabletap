package com.tabletap.billing;

import com.tabletap.domain.AppUser;
import com.tabletap.dto.BillingUsage;
import com.tabletap.service.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.time.YearMonth;

/** Used when no provider is configured: usage is still shown, nobody is charged. */
@Slf4j
public class NoBillingProvider implements BillingProvider {
    public String name() { return "none"; }

    public boolean enabled() { return false; }

    public String ensureCustomer(AppUser owner) { return null; }

    public String chargeMonth(AppUser owner, BillingUsage usage, YearMonth month) {
        log.info("Billing provider off: would charge owner {} {} for {}", owner.getId(), usage.estimatedTotal(), month);
        return null;
    }

    public String customerPortalUrl(AppUser owner, String returnUrl) {
        throw new ApiException(HttpStatus.CONFLICT, "Online billing isn't set up on this server");
    }
}
