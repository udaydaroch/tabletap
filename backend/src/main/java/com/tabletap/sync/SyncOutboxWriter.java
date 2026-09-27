package com.tabletap.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tabletap.domain.*;
import com.tabletap.domain.DomainEvents.*;
import com.tabletap.dto.OrderDtos.OrderView;
import com.tabletap.dto.ShiftDtos.ShiftView;
import com.tabletap.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Observer that fills the outbox. BEFORE_COMMIT = it runs inside the same transaction as the change,
 * so a change and its outbox row are saved together or not at all (the heart of the outbox pattern).
 * Only when cloud sync is configured — otherwise the outbox would just grow.
 */
@Component
@RequiredArgsConstructor
public class SyncOutboxWriter {
    private final SyncProperties props;
    private final SyncOutboxRepository outbox;
    private final OrderRepository orders;
    private final BillRepository bills;
    private final ShiftRepository shifts;
    private final ObjectMapper json;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrderPlaced(OrderPlaced e) {
        orders.findById(e.orderId()).ifPresent(o -> write("ORDER_PLACED", orderPayload(o)));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onStatus(OrderStatusChanged e) {
        orders.findById(e.orderId()).ifPresent(o -> write("ORDER_STATUS", orderPayload(o)));
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onBillPaid(BillPaid e) {
        bills.findById(e.billId()).ifPresent(b -> {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("restaurantId", b.getRestaurant().getId());
            p.put("billId", b.getId());
            p.put("tableLabel", b.getTableLabel());
            p.put("total", b.getTotal());
            p.put("split", b.getSplitStrategy());
            p.put("payments", b.getPayments().stream().map(x -> Map.of("label", x.getLabel(), "method", x.getMethod(),
                "amount", x.getAmount())).toList());
            p.put("paidAt", b.getCreatedAt());
            write("BILL_PAID", p);
        });
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onShift(ShiftChanged e) {
        shifts.findById(e.shiftId()).ifPresent(s -> write("SHIFT", ShiftView.of(s)));
    }

    private Object orderPayload(CustomerOrder o) {
        return OrderView.of(o);
    }

    private void write(String type, Object payload) {
        if (!props.enabled()) return;
        SyncOutbox row = new SyncOutbox();
        row.setEventType(type);
        try {
            row.setPayload(json.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialise " + type, ex);
        }
        outbox.save(row);
    }
}
