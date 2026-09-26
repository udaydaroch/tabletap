package com.tabletap.dto;

import com.tabletap.domain.CustomerOrder;
import com.tabletap.domain.OrderLine;
import com.tabletap.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {}

    public record LineRequest(@NotNull Long menuItemId, @Min(1) @Max(99) int quantity,
                              @Size(max = 30) List<@Size(max = 60) String> options, @Size(max = 200) String note) {}

    public record CreateOrderRequest(Long tableId, @Size(max = 20) String tableLabel, @Size(max = 500) String notes,
                                     @NotEmpty @Size(max = 100) @Valid List<LineRequest> lines,
                                     @Pattern(regexp = "^[A-Za-z0-9-]{8,64}$") String clientRequestId) {}

    public record StatusRequest(@NotNull OrderStatus status) {}

    public record LineView(String itemName, BigDecimal unitPrice, int quantity, List<String> options, String note) {
        static LineView of(OrderLine l) {
            return new LineView(l.getItemName(), l.getUnitPrice(), l.getQuantity(), List.copyOf(l.getOptions()), l.getNote());
        }
    }

    public record OrderView(Long id, Long restaurantId, Long tableId, String tableLabel, OrderStatus status, String waiterName,
                            String notes, Instant createdAt, BigDecimal total, List<LineView> lines) {
        public static OrderView of(CustomerOrder o) {
            BigDecimal total = o.getLines().stream()
                .map(l -> l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new OrderView(o.getId(), o.getRestaurant().getId(),
                o.getDiningTable() == null ? null : o.getDiningTable().getId(), o.getTableLabel(), o.getStatus(),
                o.getWaiter().getFullName(), o.getNotes(), o.getCreatedAt(), total,
                o.getLines().stream().map(LineView::of).toList());
        }
    }
}
