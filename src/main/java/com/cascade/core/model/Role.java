package com.cascade.core.model;

/** Workspace roles, ordered so that a higher rank implies every lower capability. */
public enum Role {
    VIEWER(0),
    MEMBER(1),
    ADMIN(2);

    private final int rank;

    Role(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    public boolean atLeast(Role minimum) {
        return rank >= minimum.rank;
    }

    public static Role parse(String value) {
        if (value == null) {
            return VIEWER;
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return VIEWER;
        }
    }
}
