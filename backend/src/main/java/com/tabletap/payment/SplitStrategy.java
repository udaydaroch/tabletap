package com.tabletap.payment;

import com.tabletap.payment.BillDtos.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Strategy pattern: one interface, several interchangeable ways to split a bill.
 * BillService picks the strategy by key at runtime; adding a new way to split means adding a class.
 */
public interface SplitStrategy {
    String key();

    List<BillPart> split(OpenBill bill, SplitRequest request);

    /** Divide an amount into n parts that add up exactly (the first parts absorb the leftover cents). */
    static List<BigDecimal> divideEvenly(BigDecimal total, int n) {
        long cents = total.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        long base = cents / n, extra = cents % n;
        return java.util.stream.LongStream.range(0, n)
            .mapToObj(i -> BigDecimal.valueOf(base + (i < extra ? 1 : 0), 2))
            .toList();
    }
}
