package com.tabletap.dto;

import com.tabletap.domain.Shift;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class ShiftDtos {
    private ShiftDtos() {}

    public record ShiftView(Long id, Long userId, String userName, Long restaurantId,
                            Instant clockIn, Instant clockOut, long minutes) {
        public static ShiftView of(Shift s) {
            Instant end = s.getClockOut() == null ? Instant.now() : s.getClockOut();
            return new ShiftView(s.getId(), s.getUser().getId(), s.getUser().getFullName(), s.getRestaurant().getId(),
                s.getClockIn(), s.getClockOut(), Duration.between(s.getClockIn(), end).toMinutes());
        }
    }

    public record MyShifts(ShiftView current, List<ShiftView> recent) {}
}
