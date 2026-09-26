package com.tabletap.payment;

import com.tabletap.payment.BillDtos.*;
import com.tabletap.service.ApiException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/** The concrete split strategies. Each is a Spring bean, collected into a registry by BillService. */
public final class SplitStrategies {
    private SplitStrategies() {}

    @Component
    public static class Full implements SplitStrategy {
        public String key() { return "FULL"; }

        public List<BillPart> split(OpenBill bill, SplitRequest req) {
            return List.of(new BillPart("Whole table", bill.total(), bill.lines().stream().map(BillLine::lineId).toList()));
        }
    }

    @Component
    public static class Even implements SplitStrategy {
        public String key() { return "EVEN"; }

        public List<BillPart> split(OpenBill bill, SplitRequest req) {
            int n = req.people() == null ? 0 : req.people();
            if (n < 2) throw ApiException.badRequest("Split evenly needs at least 2 people");
            List<BigDecimal> amounts = SplitStrategy.divideEvenly(bill.total(), n);
            List<BillPart> parts = new ArrayList<>();
            for (int i = 0; i < n; i++) parts.add(new BillPart("Guest " + (i + 1) + " of " + n, amounts.get(i), List.of()));
            return parts;
        }
    }

    @Component
    public static class ByItem implements SplitStrategy {
        public String key() { return "BY_ITEM"; }

        public List<BillPart> split(OpenBill bill, SplitRequest req) {
            Map<Long, Integer> assigned = req.assignments() == null ? Map.of() : req.assignments();
            int n = req.people() == null ? assigned.values().stream().max(Integer::compare).orElse(0) : req.people();
            if (n < 1) throw ApiException.badRequest("Assign each item to a guest");
            Map<Integer, BigDecimal> totals = new TreeMap<>();
            Map<Integer, List<Long>> lines = new TreeMap<>();
            for (BillLine line : bill.lines()) {
                Integer guest = assigned.get(line.lineId());
                if (guest == null) throw ApiException.badRequest(line.itemName() + " isn't assigned to anyone");
                if (guest > n) throw ApiException.badRequest("Guest " + guest + " doesn't exist");
                totals.merge(guest, line.amount(), BigDecimal::add);
                lines.computeIfAbsent(guest, g -> new ArrayList<>()).add(line.lineId());
            }
            if (!bill.lines().stream().map(BillLine::lineId).toList().containsAll(assigned.keySet()))
                throw ApiException.badRequest("Unknown item on this bill");
            return totals.entrySet().stream()
                .map(e -> new BillPart("Guest " + e.getKey(), e.getValue(), lines.get(e.getKey()))).toList();
        }
    }

    @Component
    public static class Custom implements SplitStrategy {
        public String key() { return "CUSTOM"; }

        public List<BillPart> split(OpenBill bill, SplitRequest req) {
            List<BigDecimal> amounts = req.amounts() == null ? List.of() : req.amounts();
            if (amounts.size() < 2) throw ApiException.badRequest("Enter at least two amounts");
            BigDecimal sum = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(bill.total()) != 0)
                throw ApiException.badRequest("Amounts add up to " + sum + " but the bill is " + bill.total());
            List<BillPart> parts = new ArrayList<>();
            for (int i = 0; i < amounts.size(); i++) parts.add(new BillPart("Part " + (i + 1), amounts.get(i), List.of()));
            return parts;
        }
    }
}
