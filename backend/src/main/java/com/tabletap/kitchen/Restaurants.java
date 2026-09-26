package com.tabletap.kitchen;

import com.tabletap.domain.Restaurant;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

final class Restaurants {
    private Restaurants() {}

    static ZoneId zone(Restaurant r) {
        try {
            return r.getTimeZone() == null ? ZoneOffset.UTC : ZoneId.of(r.getTimeZone());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }
}
