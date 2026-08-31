package com.cascade.core.importer;

import com.cascade.core.model.IssuePriority;
import com.cascade.core.model.IssueStatus;
import com.cascade.core.model.IssueType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared value normalization for the CSV and Jira XML importers. */
final class Normalizers {

    /** Formats seen in Jira and Excel exports, tried in order. */
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT),
            DateTimeFormatter.ofPattern("MM/dd/uuuu", Locale.ROOT),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("uuuu/MM/dd", Locale.ROOT));

    private Normalizers() {
    }

    static IssueStatus status(String raw) {
        IssueStatus parsed = IssueStatus.parse(raw);
        return parsed == null ? IssueStatus.BACKLOG : parsed;
    }

    static IssuePriority priority(String raw) {
        IssuePriority parsed = IssuePriority.parse(raw);
        return parsed == null ? IssuePriority.MAJOR : parsed;
    }

    static IssueType type(String raw) {
        return IssueType.parse(raw);
    }

    static List<String> labels(String raw) {
        List<String> labels = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return labels;
        }
        for (String part : raw.split("[,;|]")) {
            String value = part.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty() && !labels.contains(value)) {
                labels.add(value);
            }
        }
        return labels;
    }

    static Integer points(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static Instant date(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            // fall through to the date-only formats
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                LocalDate date = LocalDate.parse(value, format);
                return date.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC);
            } catch (RuntimeException ignored) {
                // try the next format
            }
        }
        return null;
    }

    static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
