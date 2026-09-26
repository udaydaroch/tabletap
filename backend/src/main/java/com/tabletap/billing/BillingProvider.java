package com.tabletap.billing;

import com.tabletap.domain.AppUser;
import com.tabletap.dto.BillingUsage;

import java.time.YearMonth;

/**
 * Adapter pattern: TableTap talks to this small interface; each payment company gets an adapter that
 * translates it into their API. Switching provider (or running with none) doesn't touch BillingService.
 */
public interface BillingProvider {
    String name();

    boolean enabled();

    /** Creates the customer at the provider the first time; returns their id. */
    String ensureCustomer(AppUser owner);

    /** Adds one charge per restaurant for the month and issues the invoice. Must be safe to retry. */
    String chargeMonth(AppUser owner, BillingUsage usage, YearMonth month);

    /** A link to the provider's own page where the owner manages their card and sees invoices. */
    String customerPortalUrl(AppUser owner, String returnUrl);
}
