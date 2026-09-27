package com.tabletap.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Picks the billing adapter: Stripe when STRIPE_SECRET_KEY is set, otherwise none. */
@Configuration
public class BillingConfig {
    @Bean
    BillingProvider billingProvider(@Value("${app.stripe.secret-key:}") String key,
                                    @Value("${app.stripe.currency:nzd}") String currency,
                                    @Value("${app.stripe.api-base:https://api.stripe.com}") String apiBase,
                                    ObjectMapper json) {
        return key == null || key.isBlank() ? new NoBillingProvider() : new StripeBillingAdapter(key, currency, apiBase, json);
    }
}
