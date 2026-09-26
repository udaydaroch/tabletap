package com.tabletap.kitchen;

import java.util.List;

/** Base decorator: forwards to the wrapped docket; subclasses change the lines. */
public abstract class DocketDecorator implements Docket {
    protected final Docket inner;

    protected DocketDecorator(Docket inner) {
        this.inner = inner;
    }

    @Override
    public List<Line> lines() {
        return inner.lines();
    }
}
