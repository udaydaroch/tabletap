package com.tabletap.kitchen;

import com.tabletap.domain.MenuItem;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Chain of Responsibility: decides which kitchen station a dish goes to.
 * Each handler either answers or passes the dish to the next one:
 *   item's own station  ->  its category's station  ->  default KITCHEN.
 * New rules (e.g. "drinks after 10pm go to the bar") slot in as another link without touching the others.
 */
@Component
public class StationRouter {

    /** One link in the chain. */
    abstract static class Handler {
        private Handler next;

        Handler then(Handler next) {
            this.next = next;
            return next;
        }

        String route(MenuItem item) {
            return decide(item).orElseGet(() -> next == null ? Stations.DEFAULT : next.route(item));
        }

        abstract Optional<String> decide(MenuItem item);
    }

    static class ItemOverride extends Handler {
        @Override
        Optional<String> decide(MenuItem item) {
            return Optional.ofNullable(item.getStation());
        }
    }

    static class CategoryStation extends Handler {
        @Override
        Optional<String> decide(MenuItem item) {
            return Optional.ofNullable(item.getCategory().getStation());
        }
    }

    static class DefaultKitchen extends Handler {
        @Override
        Optional<String> decide(MenuItem item) {
            return Optional.of(Stations.DEFAULT);
        }
    }

    private final Handler chain;

    public StationRouter() {
        chain = new ItemOverride();
        chain.then(new CategoryStation()).then(new DefaultKitchen());
    }

    public String route(MenuItem item) {
        return chain.route(item);
    }
}
