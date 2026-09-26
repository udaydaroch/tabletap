package com.tabletap.dto;

import com.tabletap.domain.AppUser;
import com.tabletap.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class UserDtos {
    private UserDtos() {}

    public record UserView(Long id, String email, String fullName, Role role, String title,
                           Long restaurantId, boolean active, Instant createdAt) {
        public static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getTitle(),
                u.getRestaurant() == null ? null : u.getRestaurant().getId(), u.isActive(), u.getCreatedAt());
        }
    }

    public record CreateStaffRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = Validation.PASSWORD_REGEX, message = Validation.PASSWORD_MSG) String password,
        @Size(max = 50) String title,
        Role role) {} // WAITER (default) or CHEF

    public record UpdateStaffRequest(@Size(max = 100) String fullName, @Size(max = 50) String title, Boolean active,
                                     @Pattern(regexp = Validation.PASSWORD_REGEX, message = Validation.PASSWORD_MSG) String newPassword) {}

    public record UserDetail(UserView user, String restaurantName, boolean onShift,
                             long ordersLast24h, List<ShiftDtos.ShiftView> recentShifts) {}

    public record OwnerSummary(Long id, String fullName, String email, boolean active,
                               long restaurantCount, Instant createdAt,
                               java.math.BigDecimal monthEstimate) {}
}
