package com.tabletap.stock;

/**
 * Specification pattern: a business rule as a small object that answers "does this candidate qualify?".
 * Rules can be combined (and / or / not) instead of growing if-statements inside services.
 */
@FunctionalInterface
public interface Specification<T> {
    boolean isSatisfiedBy(T candidate);

    default Specification<T> and(Specification<T> other) {
        return c -> isSatisfiedBy(c) && other.isSatisfiedBy(c);
    }

    default Specification<T> or(Specification<T> other) {
        return c -> isSatisfiedBy(c) || other.isSatisfiedBy(c);
    }

    default Specification<T> not() {
        return c -> !isSatisfiedBy(c);
    }
}
