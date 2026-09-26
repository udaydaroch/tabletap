package com.tabletap.dto;

import com.tabletap.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 200) String password) {}

    public record RegisterOwnerRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = Validation.PASSWORD_REGEX, message = Validation.PASSWORD_MSG) String password,
        @Size(max = 120) String restaurantName) {}

    public record Me(Long id, String email, String fullName, Role role, String title,
                     Long restaurantId, String restaurantName, Long impersonatorId) {}

    public record AuthResponse(String token, Me me) {}
}
