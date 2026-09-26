package com.tabletap.web;

import com.tabletap.dto.ShiftDtos.*;
import com.tabletap.security.CurrentUser;
import com.tabletap.service.ShiftService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ShiftController {
    private final ShiftService shifts;
    private final CurrentUser current;

    @PostMapping("/shifts/clock-in")
    public ShiftView clockIn() { return shifts.clockIn(current.get()); }

    @PostMapping("/shifts/clock-out")
    public ShiftView clockOut() { return shifts.clockOut(current.get()); }

    @GetMapping("/shifts/me")
    public MyShifts mine() { return shifts.mine(current.get()); }

    @GetMapping("/restaurants/{rid}/shifts")
    public List<ShiftView> forRestaurant(@PathVariable Long rid) { return shifts.forRestaurant(current.get(), rid); }
}
