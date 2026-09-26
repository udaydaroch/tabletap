package com.tabletap.payment;

import com.tabletap.payment.BillDtos.PartPayment;

import java.math.BigDecimal;

/**
 * Strategy pattern again: how a part of the bill is paid. New methods (vouchers, a card-terminal
 * integration like Stripe Terminal) are new implementations; BillService doesn't change.
 */
public interface PaymentMethod {
    String key();

    Outcome take(BigDecimal amount, PartPayment details);

    record Outcome(BigDecimal tendered, BigDecimal change, String reference) {}
}
