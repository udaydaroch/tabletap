package com.tabletap.dto;

import com.tabletap.domain.FloorArea;
import com.tabletap.domain.FloorElement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class FloorDtos {
    private FloorDtos() {}

    /** Live state of a table for the waiter's floor view. */
    public record TableStatus(int openOrders, BigDecimal openTotal, int ready, Instant since) {
        public static final TableStatus FREE = new TableStatus(0, BigDecimal.ZERO, 0, null);
    }

    public record ElementView(Long id, FloorElement.Kind kind, FloorElement.Shape shape, String label,
                              double x, double y, double w, double h, double rotation, int seats, String points,
                              TableStatus status) {
        static ElementView of(FloorElement e, Map<Long, TableStatus> statuses) {
            TableStatus st = e.getKind() == FloorElement.Kind.TABLE ? statuses.getOrDefault(e.getId(), TableStatus.FREE) : null;
            return new ElementView(e.getId(), e.getKind(), e.getShape(), e.getLabel(), e.getX(), e.getY(), e.getW(), e.getH(),
                e.getRotation(), e.getSeats(), e.getPoints(), st);
        }
    }

    public record AreaView(Long id, String name, int width, int height, List<ElementView> elements) {
        public static AreaView of(FloorArea a, Map<Long, TableStatus> statuses) {
            return new AreaView(a.getId(), a.getName(), a.getWidth(), a.getHeight(),
                a.getElements().stream().map(e -> ElementView.of(e, statuses)).toList());
        }
    }

    /** advanced = paid tier active; advancedMonthlyPrice is shown to the owner before upgrading. */
    public record FloorPlan(Long restaurantId, boolean advanced, BigDecimal advancedMonthlyPrice, List<AreaView> areas) {}

    public record TemplateView(String key, String name, String description, boolean advanced) {}

    public record TemplateRequest(@NotBlank @Size(max = 40) String key) {}

    public record TierRequest(boolean advanced, BigDecimal acceptedMonthlyPrice) {}

    public record ElementInput(Long id, @NotNull FloorElement.Kind kind, @NotNull FloorElement.Shape shape,
                               @Size(max = 20) String label, double x, double y, double w, double h,
                               double rotation, int seats, @Size(max = 2000) String points) {}

    public record AreaInput(Long id, @NotBlank @Size(max = 40) String name, int width, int height,
                            @NotNull @Size(max = 300) @Valid List<ElementInput> elements) {}

    public record FloorSaveRequest(@NotNull @Size(max = 20) @Valid List<AreaInput> areas) {}
}
