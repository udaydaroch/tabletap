package com.tabletap.service;

import com.tabletap.domain.FloorElement.Kind;
import com.tabletap.domain.FloorElement.Shape;
import com.tabletap.dto.FloorDtos.AreaInput;
import com.tabletap.dto.FloorDtos.ElementInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Ready-made layouts, available with the paid floor-plan add-on. */
public final class FloorTemplates {
    private FloorTemplates() {}

    public record Template(String key, String name, String description, boolean advanced, Supplier<List<AreaInput>> areas) {}

    public static final Map<String, Template> ALL = new LinkedHashMap<>();

    static {
        add(new Template("simple", "Simple dining room", "One room with round, square and long tables.", true, () -> List.of(
            area("Dining room", 1000, 700,
                fix(Shape.RECTANGLE, "Kitchen", 740, 30, 230, 120),
                fix(Shape.RECTANGLE, "Counter", 40, 30, 300, 60),
                table(Shape.ROUND, "1", 80, 200, 110, 110, 4),
                table(Shape.ROUND, "2", 280, 200, 110, 110, 4),
                table(Shape.SQUARE, "3", 480, 205, 100, 100, 4),
                table(Shape.SQUARE, "4", 680, 205, 100, 100, 4),
                table(Shape.RECTANGLE, "5", 80, 450, 240, 100, 8),
                table(Shape.RECTANGLE, "6", 420, 450, 240, 100, 8)))));

        add(new Template("cafe", "Café", "Curved counter, window bar and small two-seaters.", true, () -> List.of(
            area("Café", 1000, 700,
                fix(Shape.ARC, "Counter", 20, 20, 260, 260),
                fix(Shape.RECTANGLE, "Window bar", 380, 30, 580, 50),
                table(Shape.ROUND, "1", 400, 160, 80, 80, 2),
                table(Shape.ROUND, "2", 560, 160, 80, 80, 2),
                table(Shape.ROUND, "3", 720, 160, 80, 80, 2),
                table(Shape.ROUND, "4", 400, 330, 80, 80, 2),
                table(Shape.ROUND, "5", 560, 330, 80, 80, 2),
                table(Shape.ROUND, "6", 720, 330, 80, 80, 2),
                table(Shape.RECTANGLE, "7", 80, 500, 320, 100, 8),
                table(Shape.HEXAGON, "8", 560, 500, 130, 120, 6)))));

        add(new Template("bistro", "Bistro with patio", "Main floor with a bar and kitchen, plus an outdoor patio.", true, () -> List.of(
            area("Main floor", 1000, 700,
                fix(Shape.RECTANGLE, "Bar", 60, 40, 320, 70),
                fix(Shape.RECTANGLE, "Kitchen", 760, 40, 200, 130),
                table(Shape.ROUND, "1", 90, 220, 110, 110, 4),
                table(Shape.ROUND, "2", 290, 220, 110, 110, 4),
                el(Kind.TABLE, Shape.SQUARE, "3", 500, 230, 100, 100, 45, 4, null),
                table(Shape.RECTANGLE, "4", 90, 450, 220, 100, 8),
                table(Shape.HEXAGON, "5", 420, 440, 130, 120, 6),
                table(Shape.TRIANGLE, "6", 680, 440, 120, 110, 3),
                el(Kind.TABLE, Shape.POLYGON, "7", 700, 250, 200, 130, 0, 6, "0,0 1,0 1,1 0.55,1 0.55,0.45 0,0.45")),
            area("Patio", 800, 500,
                fix(Shape.ROUND, "Plant", 30, 30, 60, 60),
                table(Shape.ROUND, "P1", 150, 150, 100, 100, 2),
                table(Shape.ROUND, "P2", 350, 150, 100, 100, 2),
                table(Shape.RECTANGLE, "P3", 200, 330, 260, 90, 6)))));

        add(new Template("bar-grill", "Bar & grill", "Long bar, curved corner booths and high-top tables.", true, () -> List.of(
            area("Bar & grill", 1100, 750,
                fix(Shape.RECTANGLE, "Bar", 60, 30, 600, 70),
                fix(Shape.RECTANGLE, "Kitchen", 820, 30, 250, 150),
                el(Kind.TABLE, Shape.ARC, "B1", 30, 470, 250, 250, 0, 6, null),
                el(Kind.TABLE, Shape.ARC, "B2", 820, 470, 250, 250, 90, 6, null),
                table(Shape.HEXAGON, "H1", 150, 200, 110, 100, 4),
                table(Shape.HEXAGON, "H2", 350, 200, 110, 100, 4),
                table(Shape.HEXAGON, "H3", 550, 200, 110, 100, 4),
                table(Shape.RECTANGLE, "10", 380, 450, 300, 100, 10),
                fix(Shape.RECTANGLE, "Door", 500, 735, 100, 15)))));

        add(new Template("fine-dining", "Fine dining", "Spacious dining room with a curved wall and a private room.", true, () -> List.of(
            area("Dining room", 1000, 700,
                fix(Shape.ARC, "", 700, 380, 300, 300),
                table(Shape.ROUND, "1", 100, 100, 130, 130, 4),
                table(Shape.ROUND, "2", 400, 100, 130, 130, 4),
                table(Shape.ROUND, "3", 700, 100, 130, 130, 4),
                table(Shape.ROUND, "4", 100, 400, 130, 130, 4),
                table(Shape.ROUND, "5", 400, 400, 130, 130, 4)),
            area("Private room", 700, 500,
                table(Shape.RECTANGLE, "PR", 150, 170, 400, 140, 12),
                fix(Shape.RECTANGLE, "Door", 300, 485, 100, 15)))));
    }

    private static void add(Template t) { ALL.put(t.key(), t); }

    private static AreaInput area(String name, int w, int h, ElementInput... els) {
        return new AreaInput(null, name, w, h, new ArrayList<>(List.of(els)));
    }

    private static ElementInput table(Shape s, String label, double x, double y, double w, double h, int seats) {
        return el(Kind.TABLE, s, label, x, y, w, h, 0, seats, null);
    }

    private static ElementInput fix(Shape s, String label, double x, double y, double w, double h) {
        return el(Kind.FIXTURE, s, label, x, y, w, h, 0, 0, null);
    }

    private static ElementInput el(Kind k, Shape s, String label, double x, double y, double w, double h, double rot, int seats, String points) {
        return new ElementInput(null, k, s, label, x, y, w, h, rot, seats, points);
    }
}
