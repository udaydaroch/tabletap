package com.tabletap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Admin admin, Demo demo, Billing billing) {
    public record Jwt(String secret, long ttlHours) {}
    public record Admin(String email, String password) {}
    public record Demo(boolean enabled, String password) {}
    public record Billing(BigDecimal monthlyFeePerRestaurant, BigDecimal feePerOrder, BigDecimal floorPlanFee) {}
}
