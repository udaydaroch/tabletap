package com.tabletap.service;

import com.tabletap.config.AppProperties;
import com.tabletap.domain.*;
import com.tabletap.domain.Restaurant.FloorPlanTier;
import com.tabletap.dto.FloorDtos.*;
import com.tabletap.live.LiveEvent;
import com.tabletap.repository.FloorAreaRepository;
import com.tabletap.repository.OrderRepository;
import com.tabletap.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The restaurant's floor plan and live table status for waiters.
 * Floor plans are a paid add-on: BASIC restaurants have none (waiters type a table number);
 * ADVANCED unlocks everything. Enforced here on every write, so the UI can't be bypassed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FloorService {
    private static final Pattern POINTS = Pattern.compile("^(\\d+(\\.\\d+)?,\\d+(\\.\\d+)?)( \\d+(\\.\\d+)?,\\d+(\\.\\d+)?){2,63}$");
    private static final List<OrderStatus> OPEN = Arrays.stream(OrderStatus.values()).filter(OrderStatus::isOpen).toList();

    private final FloorAreaRepository areas;
    private final OrderRepository orders;
    private final RestaurantRepository restaurants;
    private final AccessService access;
    private final ApplicationEventPublisher events;
    private final AppProperties props;

    private Restaurant load(Long id) {
        return restaurants.findById(id).orElseThrow(() -> ApiException.notFound("Restaurant"));
    }

    @Transactional(readOnly = true)
    public FloorPlan get(AppUser u, Long restaurantId) {
        Restaurant r = load(restaurantId);
        access.requireWork(u, r);
        return view(r);
    }

    public FloorPlan save(AppUser u, Long restaurantId, FloorSaveRequest req) {
        Restaurant r = load(restaurantId);
        access.requireManage(u, r);
        replace(r, req.areas());
        events.publishEvent(LiveEvent.of(LiveEvent.FLOOR_CHANGED, r, u.getId()));
        return view(r);
    }

    public List<TemplateView> templates() {
        return FloorTemplates.ALL.values().stream()
            .map(t -> new TemplateView(t.key(), t.name(), t.description(), t.advanced())).toList();
    }

    public FloorPlan applyTemplate(AppUser u, Long restaurantId, String key) {
        Restaurant r = load(restaurantId);
        access.requireManage(u, r);
        FloorTemplates.Template t = FloorTemplates.ALL.get(key);
        if (t == null) throw ApiException.notFound("Layout");
        requirePaid(r);
        replace(r, t.areas().get());
        events.publishEvent(LiveEvent.of(LiveEvent.FLOOR_CHANGED, r, u.getId()));
        return view(r);
    }

    /** For seeding: apply a template without a user (tier rules still apply). */
    public void applyTemplateInternal(Restaurant r, String key) {
        replace(r, FloorTemplates.ALL.get(key).areas().get());
    }

    /** Turn the paid add-on on/off. Turning it on requires the owner to accept the current price. */
    public FloorPlan setTier(AppUser u, Long restaurantId, TierRequest req) {
        Restaurant r = load(restaurantId);
        access.requireManage(u, r);
        if (req.advanced()) {
            BigDecimal price = props.billing().floorPlanFee();
            if (req.acceptedMonthlyPrice() == null || req.acceptedMonthlyPrice().compareTo(price) != 0)
                throw new ApiException(HttpStatus.CONFLICT, "The price has changed — please review and accept it again");
            if (r.getFloorPlanTier() != FloorPlanTier.ADVANCED) {
                r.setFloorPlanTier(FloorPlanTier.ADVANCED);
                r.setFloorPlanAdvancedSince(Instant.now());
                log.info("AUDIT user {} enabled advanced floor plans for restaurant {} at {}/month", u.getId(), r.getId(), price);
            }
        } else if (r.getFloorPlanTier() == FloorPlanTier.ADVANCED) {
            // cancelling removes the floor plan; past orders are kept (unlinked from their tables)
            r.setFloorPlanTier(FloorPlanTier.BASIC);
            replace(r, List.of());
            r.setFloorPlanAdvancedSince(null);
            log.info("AUDIT user {} cancelled advanced floor plans for restaurant {}", u.getId(), r.getId());
        }
        events.publishEvent(LiveEvent.of(LiveEvent.RESTAURANT_CHANGED, r, u.getId())); // bill changes
        events.publishEvent(LiveEvent.of(LiveEvent.FLOOR_CHANGED, r, u.getId()));
        return view(r);
    }

    /** Replaces the whole floor plan. Existing ids are updated, missing ones deleted, id-less ones created. */
    private void replace(Restaurant r, List<AreaInput> input) {
        validate(input, r.getFloorPlanTier());

        Map<Long, FloorArea> existing = areas.findByRestaurantIdOrderBySortOrderAscIdAsc(r.getId()).stream()
            .collect(Collectors.toMap(FloorArea::getId, Function.identity()));

        // tables that disappear keep their order history, just unlinked
        Set<Long> keptElementIds = input.stream().flatMap(a -> a.elements().stream())
            .map(ElementInput::id).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Long> removed = existing.values().stream().flatMap(a -> a.getElements().stream())
            .map(FloorElement::getId).filter(id -> !keptElementIds.contains(id)).toList();
        if (!removed.isEmpty()) orders.detachTables(removed);

        Set<Long> keptAreaIds = new HashSet<>();
        int order = 0;
        for (AreaInput in : input) {
            FloorArea area = in.id() != null ? existing.get(in.id()) : null;
            if (area == null) {
                area = new FloorArea();
                area.setRestaurant(r);
            }
            area.setName(in.name().trim());
            area.setSortOrder(order++);
            area.setWidth(in.width());
            area.setHeight(in.height());
            syncElements(area, in.elements());
            areas.save(area);
            keptAreaIds.add(area.getId());
        }
        existing.values().stream().filter(a -> !keptAreaIds.contains(a.getId())).forEach(areas::delete);
        areas.flush();
    }

    private void syncElements(FloorArea area, List<ElementInput> inputs) {
        Map<Long, FloorElement> byId = area.getElements().stream()
            .filter(e -> e.getId() != null)
            .collect(Collectors.toMap(FloorElement::getId, Function.identity()));
        List<FloorElement> next = new ArrayList<>();
        for (ElementInput in : inputs) {
            FloorElement e = in.id() != null ? byId.get(in.id()) : null;
            if (e == null) {
                e = new FloorElement();
                e.setArea(area);
            }
            e.setKind(in.kind());
            e.setShape(in.shape());
            e.setLabel(in.label() == null ? null : in.label().trim());
            e.setX(in.x());
            e.setY(in.y());
            e.setW(in.w());
            e.setH(in.h());
            e.setRotation(((in.rotation() % 360) + 360) % 360);
            e.setSeats(in.kind() == FloorElement.Kind.TABLE ? in.seats() : 0);
            e.setPoints(in.shape() == FloorElement.Shape.POLYGON ? in.points() : null);
            next.add(e);
        }
        area.getElements().retainAll(next);           // orphanRemoval deletes the rest
        next.stream().filter(e -> !area.getElements().contains(e)).forEach(area.getElements()::add);
    }

    private void validate(List<AreaInput> input, FloorPlanTier tier) {
        if (tier == FloorPlanTier.BASIC && !input.isEmpty())
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "Floor plans are a paid add-on — upgrade to design your restaurant");
        Set<String> labels = new HashSet<>();
        for (AreaInput a : input) {
            if (a.width() < 200 || a.width() > 5000 || a.height() < 200 || a.height() > 5000)
                throw ApiException.badRequest("Area size must be between 200 and 5000");
            for (ElementInput e : a.elements()) {
                if (e.w() < 10 || e.h() < 4 || e.w() > a.width() || e.h() > a.height()
                    || e.x() < -e.w() || e.y() < -e.h() || e.x() > a.width() || e.y() > a.height())
                    throw ApiException.badRequest("An element in \"" + a.name() + "\" is outside the area or too small");
                if (e.kind() == FloorElement.Kind.TABLE) {
                    if (e.label() == null || e.label().isBlank()) throw ApiException.badRequest("Every table needs a name/number");
                    if (!labels.add(e.label().trim().toLowerCase()))
                        throw ApiException.badRequest("Two tables are both called \"" + e.label().trim() + "\"");
                    if (e.seats() < 0 || e.seats() > 100) throw ApiException.badRequest("Seats must be 0-100");
                }
                if (e.shape() == FloorElement.Shape.POLYGON && (e.points() == null || !POINTS.matcher(e.points()).matches()))
                    throw ApiException.badRequest("Custom shape needs 3-64 points");
            }
        }
    }

    private void requirePaid(Restaurant r) {
        if (r.getFloorPlanTier() != FloorPlanTier.ADVANCED)
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "Floor plans are a paid add-on — upgrade to design your restaurant");
    }

    private FloorPlan view(Restaurant r) {
        Map<Long, TableStatus> statuses = new HashMap<>();
        // a table is occupied until its bill is paid, not just until the food is served
        Map<Long, List<CustomerOrder>> byTable = orders.findUnpaid(r.getId()).stream()
            .filter(o -> o.getDiningTable() != null)
            .collect(Collectors.groupingBy(o -> o.getDiningTable().getId()));
        byTable.forEach((tableId, list) -> {
            BigDecimal total = list.stream().flatMap(o -> o.getLines().stream())
                .map(l -> l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            int ready = (int) list.stream().filter(o -> o.getStatus() == OrderStatus.READY).count();
            Instant since = list.stream().map(CustomerOrder::getCreatedAt).min(Comparator.naturalOrder()).orElse(null);
            statuses.put(tableId, new TableStatus(list.size(), total, ready, since));
        });
        return new FloorPlan(r.getId(), r.getFloorPlanTier() == FloorPlanTier.ADVANCED, props.billing().floorPlanFee(),
            areas.findByRestaurantIdOrderBySortOrderAscIdAsc(r.getId()).stream().map(a -> AreaView.of(a, statuses)).toList());
    }
}
