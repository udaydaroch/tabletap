package com.tabletap.kitchen;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Decorator: notes that mention an allergy are shouted in big text so they can't be missed. */
public class AllergyAlertDocket extends DocketDecorator {
    private static final Pattern ALLERGY = Pattern.compile(
        "allerg|gluten|coeliac|celiac|nut|peanut|dairy|lactose|shellfish|egg|sesame|soy", Pattern.CASE_INSENSITIVE);

    public AllergyAlertDocket(Docket inner) {
        super(inner);
    }

    @Override
    public List<Line> lines() {
        return inner.lines().stream()
            .map(l -> l.text().contains("NOTE") && ALLERGY.matcher(l.text()).find()
                ? Line.big("!! " + l.text().trim().toUpperCase(Locale.ROOT) + " !!")
                : l)
            .toList();
    }
}
