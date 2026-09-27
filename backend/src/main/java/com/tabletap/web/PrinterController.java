package com.tabletap.web;

import com.tabletap.dto.PrinterDtos.*;
import com.tabletap.kitchen.PrinterService;
import com.tabletap.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PrinterController {
    private final PrinterService printers;
    private final CurrentUser current;

    @GetMapping("/restaurants/{rid}/printers")
    public List<PrinterView> list(@PathVariable Long rid) { return printers.list(current.get(), rid); }

    @PostMapping("/restaurants/{rid}/printers")
    public PrinterView create(@PathVariable Long rid, @Valid @RequestBody PrinterRequest req) {
        return printers.create(current.get(), rid, req);
    }

    @PutMapping("/printers/{id}")
    public PrinterView update(@PathVariable Long id, @Valid @RequestBody PrinterRequest req) {
        return printers.update(current.get(), id, req);
    }

    @DeleteMapping("/printers/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { printers.delete(current.get(), id); }

    @PostMapping("/printers/{id}/test")
    public PrinterView test(@PathVariable Long id) { return printers.test(current.get(), id); }

    @PostMapping("/orders/{id}/print")
    public Map<String, Integer> reprint(@PathVariable Long id) {
        return Map.of("printed", printers.reprint(current.get(), id));
    }
}
