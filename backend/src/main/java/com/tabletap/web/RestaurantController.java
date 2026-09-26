package com.tabletap.web;

import com.tabletap.dto.RestaurantDtos.*;
import com.tabletap.dto.UserDtos.*;
import com.tabletap.security.CurrentUser;
import com.tabletap.service.RestaurantService;
import com.tabletap.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RestaurantController {
    private final RestaurantService restaurants;
    private final StaffService staff;
    private final CurrentUser current;

    @GetMapping("/restaurants")
    public List<RestaurantView> list() { return restaurants.listFor(current.get()); }

    @GetMapping("/restaurants/{id}")
    public RestaurantView get(@PathVariable Long id) { return restaurants.get(current.get(), id); }

    @PostMapping("/restaurants")
    public RestaurantView create(@Valid @RequestBody RestaurantRequest req) { return restaurants.create(current.get(), req); }

    @PutMapping("/restaurants/{id}")
    public RestaurantView update(@PathVariable Long id, @Valid @RequestBody RestaurantRequest req) {
        return restaurants.update(current.get(), id, req);
    }

    @GetMapping("/restaurants/{id}/staff")
    public List<UserView> staff(@PathVariable Long id) { return staff.list(current.get(), id); }

    @PostMapping("/restaurants/{id}/staff")
    public UserView addStaff(@PathVariable Long id, @Valid @RequestBody CreateStaffRequest req) {
        return staff.create(current.get(), id, req);
    }

    @PatchMapping("/staff/{userId}")
    public UserView updateStaff(@PathVariable Long userId, @Valid @RequestBody UpdateStaffRequest req) {
        return staff.update(current.get(), userId, req);
    }

    @GetMapping("/users/{userId}")
    public UserDetail user(@PathVariable Long userId) { return staff.detail(current.get(), userId); }
}
