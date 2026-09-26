package com.tabletap.payment;

import com.tabletap.domain.*;
import com.tabletap.live.LiveEvent;
import com.tabletap.payment.BillDtos.*;
import com.tabletap.repository.BillRepository;
import com.tabletap.repository.OrderRepository;
import com.tabletap.service.AccessService;
import com.tabletap.service.ApiException;
import com.tabletap.service.RestaurantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Bills per table: what's owed, how to split it (SplitStrategy) and how each part is paid (PaymentMethod). */
@Slf4j
@Service
@Transactional
public class BillService {
    private final OrderRepository orders;
    private final BillRepository bills;
    private final RestaurantService restaurants;
    private final AccessService access;
    private final ApplicationEventPublisher events;
    private final Map<String, SplitStrategy> splitters;
    private final Map<String, PaymentMethod> methods;

    public BillService(OrderRepository orders, BillRepository bills, RestaurantService restaurants, AccessService access,
                       ApplicationEventPublisher events, List<SplitStrategy> splitters, List<PaymentMethod> methods) {
        this.orders = orders;
        this.bills = bills;
        this.restaurants = restaurants;
        this.access = access;
        this.events = events;
        // registries: Spring hands us every implementation, we index them by key
        this.splitters = splitters.stream().collect(Collectors.toMap(SplitStrategy::key, Function.identity()));
        this.methods = methods.stream().collect(Collectors.toMap(PaymentMethod::key, Function.identity()));
    }

    /** Every table that still owes money (used by the quick table screen). */
    @Transactional(readOnly = true)
    public List<OpenBill> openBills(AppUser u, Long restaurantId) {
        Restaurant r = restaurants.load(restaurantId);
        access.requireWork(u, r);
        Map<String, List<CustomerOrder>> byTable = orders.findUnpaid(restaurantId).stream()
            .collect(Collectors.groupingBy(CustomerOrder::getTableLabel, LinkedHashMap::new, Collectors.toList()));
        return byTable.entrySet().stream().map(e -> toBill(e.getKey(), e.getValue())).toList();
    }

    @Transactional(readOnly = true)
    public OpenBill bill(AppUser u, Long restaurantId, String table) {
        Restaurant r = restaurants.load(restaurantId);
        access.requireFrontOfHouse(u, r);
        return toBill(table, orders.findUnpaidForTable(restaurantId, table));
    }

    @Transactional(readOnly = true)
    public List<BillPart> preview(AppUser u, Long restaurantId, String table, SplitRequest split) {
        OpenBill bill = bill(u, restaurantId, table);
        if (bill.lines().isEmpty()) throw ApiException.badRequest("Nothing to pay on table " + table);
        return splitter(split).split(bill, split);
    }

    public Receipt pay(AppUser u, Long restaurantId, String table, PayRequest req) {
        Restaurant r = restaurants.load(restaurantId);
        access.requireFrontOfHouse(u, r);
        List<CustomerOrder> unpaid = orders.lockUnpaidForTable(restaurantId, table); // row locks until commit
        OpenBill bill = toBill(table, unpaid);
        if (bill.lines().isEmpty()) throw ApiException.badRequest("Nothing to pay on table " + table);
        if (bill.total().compareTo(req.expectedTotal()) != 0)
            throw new ApiException(HttpStatus.CONFLICT, "The bill changed (now " + bill.total() + "). Check it and try again.");

        // parts are always recalculated here — never trusted from the phone
        List<BillPart> parts = splitter(req.split()).split(bill, req.split());
        if (parts.size() != req.payments().size())
            throw ApiException.badRequest("Choose how each of the " + parts.size() + " parts is paid");

        Bill b = new Bill();
        b.setRestaurant(r);
        b.setTableLabel(table);
        b.setTotal(bill.total());
        b.setSplitStrategy(req.split().strategy().toUpperCase(Locale.ROOT));
        b.setClosedBy(u);
        List<PaidPart> receipt = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            BillPart part = parts.get(i);
            PartPayment details = req.payments().get(i);
            PaymentMethod method = methods.get(details.method().toUpperCase(Locale.ROOT));
            if (method == null) throw ApiException.badRequest("Unknown payment method " + details.method());
            PaymentMethod.Outcome out = method.take(part.amount(), details);
            Payment p = new Payment();
            p.setBill(b);
            p.setLabel(part.label());
            p.setMethod(method.key());
            p.setAmount(part.amount());
            p.setTendered(out.tendered());
            p.setChangeGiven(out.change());
            p.setReference(out.reference());
            b.getPayments().add(p);
            receipt.add(new PaidPart(part.label(), method.key(), part.amount(), out.tendered(), out.change(), out.reference()));
        }
        bills.save(b);
        Instant now = Instant.now();
        for (CustomerOrder o : unpaid) {
            o.setBill(b);
            o.setPaidAt(now);
        }
        log.info("Table {} at restaurant {} paid {} ({}) by user {}", table, r.getId(), bill.total(), b.getSplitStrategy(), u.getId());
        events.publishEvent(new DomainEvents.BillPaid(b.getId()));
        events.publishEvent(LiveEvent.of(LiveEvent.BILL_PAID, r, u.getId()));
        return new Receipt(b.getId(), table, bill.total(), b.getSplitStrategy(), receipt, now);
    }

    private SplitStrategy splitter(SplitRequest split) {
        SplitStrategy s = splitters.get(split.strategy().toUpperCase(Locale.ROOT));
        if (s == null) throw ApiException.badRequest("Unknown way to split: " + split.strategy());
        return s;
    }

    private static OpenBill toBill(String table, List<CustomerOrder> list) {
        List<BillLine> lines = list.stream().flatMap(o -> o.getLines().stream().map(l -> new BillLine(l.getId(), o.getId(),
            l.getItemName(), l.getUnitPrice(), l.getQuantity(), l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity()))))).toList();
        BigDecimal total = lines.stream().map(BillLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        Long tableId = list.stream().map(CustomerOrder::getDiningTable).filter(Objects::nonNull).map(FloorElement::getId).findFirst().orElse(null);
        int ready = (int) list.stream().filter(o -> o.getStatus() == OrderStatus.READY).count();
        Instant since = list.stream().map(CustomerOrder::getCreatedAt).min(Comparator.naturalOrder()).orElse(null);
        return new OpenBill(table, tableId, list.stream().map(CustomerOrder::getId).toList(), lines, total, ready, since);
    }
}
