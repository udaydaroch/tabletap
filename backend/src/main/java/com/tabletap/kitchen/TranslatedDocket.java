package com.tabletap.kitchen;

import com.tabletap.domain.OrderLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Decorator: under each dish, adds its name in the kitchen's language (the "kitchen name" the owner
 * entered on the menu item), so staff who read another language can cook from the same docket.
 */
public class TranslatedDocket extends DocketDecorator {
    private final Map<String, String> translations;

    public TranslatedDocket(Docket inner, List<OrderLine> items) {
        super(inner);
        this.translations = items.stream()
            .filter(l -> l.getKitchenName() != null && !l.getKitchenName().isBlank())
            .collect(Collectors.toMap(l -> l.getQuantity() + " x " + l.getItemName(), OrderLine::getKitchenName, (a, b) -> a));
    }

    @Override
    public List<Line> lines() {
        List<Line> out = new ArrayList<>();
        for (Line line : inner.lines()) {
            out.add(line);
            String translated = line.big() ? translations.get(line.text()) : null;
            if (translated != null) out.add(Line.of("   (" + translated + ")"));
        }
        return out;
    }
}
