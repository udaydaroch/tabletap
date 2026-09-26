package com.tabletap.kitchen;

import java.util.List;

/**
 * A kitchen docket as plain lines of text. Decorator pattern: the basic docket lists the order,
 * and decorators wrap it to add things (translations, allergy emphasis) without changing it.
 */
public interface Docket {
    /** A line of the docket; big = print double size (table number, dish names). */
    record Line(String text, boolean big) {
        public static Line of(String text) { return new Line(text, false); }
        public static Line big(String text) { return new Line(text, true); }
    }

    List<Line> lines();
}
