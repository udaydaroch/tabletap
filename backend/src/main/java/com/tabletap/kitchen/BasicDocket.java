package com.tabletap.kitchen;

import com.tabletap.domain.CustomerOrder;
import com.tabletap.domain.OrderLine;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** The undecorated docket: header, then every line for one station (or all stations when station is null). */
public class BasicDocket implements Docket {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final CustomerOrder order;
    private final String station;
    private final ZoneId zone;

    public BasicDocket(CustomerOrder order, String station, ZoneId zone) {
        this.order = order;
        this.station = station;
        this.zone = zone;
    }

    public List<OrderLine> items() {
        return order.getLines().stream().filter(l -> station == null || station.equals(l.getStation())).toList();
    }

    @Override
    public List<Line> lines() {
        List<Line> out = new ArrayList<>();
        out.add(Line.big("TABLE " + order.getTableLabel()));
        out.add(Line.of("#" + order.getId() + "  " + TIME.format(order.getCreatedAt().atZone(zone))
            + "  " + order.getWaiter().getFullName()));
        if (station != null) out.add(Line.of("Station: " + station));
        out.add(Line.of("--------------------------------"));
        for (OrderLine l : items()) {
            out.add(Line.big(l.getQuantity() + " x " + l.getItemName()));
            for (String opt : l.getOptions()) out.add(Line.of("   + " + opt));
            if (l.getNote() != null && !l.getNote().isBlank()) out.add(Line.of("   NOTE: " + l.getNote()));
        }
        if (order.getNotes() != null && !order.getNotes().isBlank()) {
            out.add(Line.of("--------------------------------"));
            out.add(Line.of("ORDER NOTE: " + order.getNotes()));
        }
        return out;
    }
}
