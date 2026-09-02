package com.cascade.core.model;

import java.util.Locale;

public enum IssuePriority {
    TRIVIAL("trivial"),
    MINOR("minor"),
    MAJOR("major"),
    CRITICAL("critical"),
    BLOCKER("blocker");

    private final String wire;

    IssuePriority(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    /** Accepts Cascade names as well as the Jira labels that imports carry. */
    public static IssuePriority parse(String value) {
        if (value == null) {
            return null;
        }
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "lowest":
            case "trivial":
                return TRIVIAL;
            case "low":
            case "minor":
                return MINOR;
            case "medium":
            case "normal":
            case "major":
                return MAJOR;
            case "high":
            case "critical":
                return CRITICAL;
            case "highest":
            case "blocker":
                return BLOCKER;
            default:
                return null;
        }
    }
}
