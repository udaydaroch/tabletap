package com.tabletap.web;

import com.tabletap.dto.AuthDtos.AuthResponse;
import com.tabletap.dto.UserDtos.OwnerSummary;
import com.tabletap.dto.UserDtos.UserView;
import com.tabletap.security.CurrentUser;
import com.tabletap.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin-only endpoints (guarded by SecurityConfig: /api/admin/** requires ROLE_ADMIN). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService admin;
    private final CurrentUser current;

    @GetMapping("/owners")
    public List<OwnerSummary> owners() { return admin.owners(); }

    @PostMapping("/impersonate/{userId}")
    public AuthResponse impersonate(@PathVariable Long userId) { return admin.impersonate(current.get(), userId); }

    @PatchMapping("/users/{userId}/active")
    public UserView setActive(@PathVariable Long userId, @RequestBody Map<String, Boolean> body) {
        return admin.setActive(userId, Boolean.TRUE.equals(body.get("active")));
    }
}
