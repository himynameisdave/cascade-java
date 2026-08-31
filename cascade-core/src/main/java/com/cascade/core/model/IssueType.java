package com.cascade.core.model;

import java.util.Locale;

public enum IssueType {
    BUG("bug"),
    TASK("task"),
    STORY("story"),
    EPIC("epic");

    private final String wire;

    IssueType(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static IssueType parse(String value) {
        if (value == null || value.isBlank()) {
            return TASK;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("bug") || v.equals("defect")) {
            return BUG;
        }
        if (v.startsWith("stor")) {
            return STORY;
        }
        if (v.startsWith("epic")) {
            return EPIC;
        }
        return TASK;
    }
}
