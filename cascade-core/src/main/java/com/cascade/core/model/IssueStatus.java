package com.cascade.core.model;

import java.util.List;
import java.util.Locale;

/** Board columns, in workflow order. */
public enum IssueStatus {
    BACKLOG("backlog"),
    TODO("todo"),
    IN_PROGRESS("in_progress"),
    IN_REVIEW("in_review"),
    DONE("done");

    private final String wire;

    IssueStatus(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static IssueStatus parse(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        switch (normalized) {
            case "backlog":
                return BACKLOG;
            case "todo":
            case "to_do":
            case "open":
                return TODO;
            case "in_progress":
            case "doing":
                return IN_PROGRESS;
            case "in_review":
            case "review":
                return IN_REVIEW;
            case "done":
            case "closed":
            case "resolved":
                return DONE;
            default:
                return null;
        }
    }

    /**
     * Forward moves plus a single step back — the transitions the board offers.
     * Anything else is rejected with 422 rather than silently applied.
     */
    public List<IssueStatus> allowedTransitions() {
        switch (this) {
            case BACKLOG:
                return List.of(TODO, IN_PROGRESS);
            case TODO:
                return List.of(BACKLOG, IN_PROGRESS);
            case IN_PROGRESS:
                return List.of(TODO, IN_REVIEW, DONE);
            case IN_REVIEW:
                return List.of(IN_PROGRESS, DONE);
            case DONE:
            default:
                return List.of(IN_REVIEW, TODO);
        }
    }

    public boolean canMoveTo(IssueStatus target) {
        return this == target || allowedTransitions().contains(target);
    }
}
