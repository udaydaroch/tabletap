package com.tabletap.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class BillDtos {
    private BillDtos() {}

    /** One line on the bill. lineId is the order line's id, used when splitting by item. */
    public record BillLine(Long lineId, Long orderId, String itemName, BigDecimal unitPrice, int quantity, BigDecimal amount) {}

    public record OpenBill(String tableLabel, Long tableId, List<Long> orderIds, List<BillLine> lines, BigDecimal total,
                           int ready, Instant since) {}

    /** How to split. people: EVEN / BY_ITEM guest count. assignments: lineId -> guest number. amounts: CUSTOM. */
    public record SplitRequest(@NotBlank @Size(max = 20) String strategy,
                               @Min(1) @Max(30) Integer people,
                               @Size(max = 200) Map<Long, @Min(1) @Max(30) Integer> assignments,
                               @Size(max = 30) List<@DecimalMin("0.01") @DecimalMax("100000") BigDecimal> amounts) {}

    public record BillPart(String label, BigDecimal amount, List<Long> lineIds) {}

    public record PartPayment(@NotBlank @Size(max = 10) String method,
                              @DecimalMin("0") @DecimalMax("100000") BigDecimal tendered,
                              @Size(max = 40) String reference) {}

    public record PayRequest(@NotNull @Valid SplitRequest split,
                             @NotNull @Size(min = 1, max = 30) @Valid List<PartPayment> payments,
                             @NotNull BigDecimal expectedTotal) {}

    public record PaidPart(String label, String method, BigDecimal amount, BigDecimal tendered, BigDecimal change, String reference) {}

    public record Receipt(Long billId, String tableLabel, BigDecimal total, String strategy, List<PaidPart> parts, Instant paidAt) {}
}
