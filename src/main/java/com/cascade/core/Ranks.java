package com.cascade.core;

/**
 * Board ordering. Ranks are fractional so dropping a card between two
 * neighbours rewrites only the row that moved.
 */
public final class Ranks {

    public static final double STEP = 1000.0;

    private Ranks() {
    }

    public static double between(Double before, Double after) {
        if (before != null && after != null) {
            return (before + after) / 2.0;
        }
        if (before != null) {
            return before + STEP;
        }
        if (after != null) {
            return after / 2.0;
        }
        return STEP;
    }
}
