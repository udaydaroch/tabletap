package com.tabletap.kitchen;

import java.util.Locale;

/** Station names are free text (owners invent their own), stored upper-case. */
public final class Stations {
    public static final String DEFAULT = "KITCHEN";

    private Stations() {}

    public static String normalise(String station) {
        if (station == null || station.isBlank()) return null;
        return station.trim().replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
    }
}
