package com.tabletap.web;

import com.tabletap.payment.BillDtos.*;
import com.tabletap.payment.BillService;
import com.tabletap.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/restaurants/{rid}")
@RequiredArgsConstructor
public class BillController {
    private final BillService bills;
    private final CurrentUser current;

    @GetMapping("/bills")
    public List<OpenBill> open(@PathVariable Long rid) { return bills.openBills(current.get(), rid); }

    @GetMapping("/bills/{table}")
    public OpenBill bill(@PathVariable Long rid, @PathVariable String table) { return bills.bill(current.get(), rid, table); }

    @PostMapping("/bills/{table}/split")
    public List<BillPart> preview(@PathVariable Long rid, @PathVariable String table, @Valid @RequestBody SplitRequest req) {
        return bills.preview(current.get(), rid, table, req);
    }

    @PostMapping("/bills/{table}/pay")
    public Receipt pay(@PathVariable Long rid, @PathVariable String table, @Valid @RequestBody PayRequest req) {
        return bills.pay(current.get(), rid, table, req);
    }
}
