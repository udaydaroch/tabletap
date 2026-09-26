package com.tabletap.domain;

/**
 * Domain event (Observer pattern): published by OrderService when an order is saved.
 * Listeners — stock deduction, docket printing, cloud sync — subscribe without OrderService knowing about them.
 */
public record OrderPlaced(Long orderId, Long restaurantId) {}
