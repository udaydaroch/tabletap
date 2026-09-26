package com.tabletap.dto;

import com.tabletap.domain.Restaurant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class RestaurantDtos {
    private RestaurantDtos() {}

    public record RestaurantRequest(@NotBlank @Size(max = 120) String name, @Size(max = 200) String address, @Size(max = 60) String cuisine,
                                    @Size(max = 60) String timeZone) {}

    public record RestaurantView(Long id, String name, String address, String cuisine,
                                 Long ownerId, String ownerName, long onShift, long openOrders, String timeZone) {
        public static RestaurantView of(Restaurant r) {
            return of(r, 0, 0);
        }

        public static RestaurantView of(Restaurant r, long onShift, long openOrders) {
            return new RestaurantView(r.getId(), r.getName(), r.getAddress(), r.getCuisine(),
                r.getOwner().getId(), r.getOwner().getFullName(), onShift, openOrders, r.getTimeZone());
        }
    }
}
