package com.tabletap.kitchen;

import com.tabletap.domain.CustomerOrder;

import java.time.ZoneId;

/** Assembles the decorated docket used for both printing and the kitchen screen. */
public final class Dockets {
    private Dockets() {}

    public static Docket forStation(CustomerOrder order, String station, ZoneId zone) {
        BasicDocket basic = new BasicDocket(order, station, zone);
        Docket docket = new TranslatedDocket(basic, basic.items());
        return new AllergyAlertDocket(docket);
    }
}
