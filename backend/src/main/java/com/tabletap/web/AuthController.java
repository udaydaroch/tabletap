package com.tabletap.web;

import com.tabletap.dto.AuthDtos.*;
import com.tabletap.security.CurrentUser;
import com.tabletap.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final CurrentUser current;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return auth.login(req, http.getRemoteAddr());
    }

    @PostMapping("/register-owner")
    public AuthResponse register(@Valid @RequestBody RegisterOwnerRequest req, HttpServletRequest http) {
        return auth.registerOwner(req, http.getRemoteAddr());
    }

    @GetMapping("/me")
    public Me me() {
        return auth.me(current.get(), current.impersonatorId());
    }
}
