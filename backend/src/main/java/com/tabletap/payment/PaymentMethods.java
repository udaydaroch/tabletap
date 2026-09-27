package com.tabletap.payment;

import com.tabletap.payment.BillDtos.PartPayment;
import com.tabletap.service.ApiException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

public final class PaymentMethods {
    private PaymentMethods() {}

    /** Cash: the waiter enters what the guest handed over; we work out the change. */
    @Component
    public static class Cash implements PaymentMethod {
        public String key() { return "CASH"; }

        public Outcome take(BigDecimal amount, PartPayment d) {
            BigDecimal tendered = d.tendered() == null ? amount : d.tendered();
            if (tendered.compareTo(amount) < 0)
                throw ApiException.badRequest("Cash handed over (" + tendered + ") is less than " + amount);
            return new Outcome(tendered, tendered.subtract(amount), null);
        }
    }

    /**
     * Card on the restaurant's own terminal: the terminal charges the card, TableTap records it
     * (optionally with the terminal's receipt number). No card data ever touches TableTap.
     */
    @Component
    public static class CardTerminal implements PaymentMethod {
        public String key() { return "CARD"; }

        public Outcome take(BigDecimal amount, PartPayment d) {
            String ref = d.reference() == null || d.reference().isBlank() ? null : d.reference().trim();
            if (ref != null && !ref.matches("[A-Za-z0-9 -]{1,40}")) throw ApiException.badRequest("Invalid receipt number");
            return new Outcome(amount, BigDecimal.ZERO, ref);
        }
    }
}
