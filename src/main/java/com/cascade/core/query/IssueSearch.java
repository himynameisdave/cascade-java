package com.cascade.core.query;

import com.cascade.core.model.Issue;
import com.cascade.core.model.IssueStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Applies a parsed {@link Cql.Query} to a list of issues. */
public final class IssueSearch {

    private static final Pattern RELATIVE = Pattern.compile("^([+-])(\\d+)([dwmh])$");

    public record Page(int total, int offset, int limit, List<Issue> issues) { }

    private IssueSearch() {
    }

    /** {@code -7d}, {@code +2w}, {@code now}, or an ISO-8601 instant. */
    static Instant resolveTemporal(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.equalsIgnoreCase("now")) {
            return Instant.now();
        }
        Matcher matcher = RELATIVE.matcher(raw);
        if (matcher.matches()) {
            long amount = Long.parseLong(matcher.group(2));
            ChronoUnit unit = switch (matcher.group(3)) {
                case "h" -> ChronoUnit.HOURS;
                case "d" -> ChronoUnit.DAYS;
                case "w" -> ChronoUnit.WEEKS;
                default -> ChronoUnit.MONTHS;
            };
            // Instant cannot add estimated units, so weeks/months go via days.
            long days = switch (unit) {
                case WEEKS -> amount * 7;
                case MONTHS -> amount * 30;
                default -> amount;
            };
            ChronoUnit effective = unit == ChronoUnit.HOURS ? ChronoUnit.HOURS : ChronoUnit.DAYS;
            long value = unit == ChronoUnit.HOURS ? amount : days;
            return matcher.group(1).equals("-")
                    ? Instant.now().minus(value, effective)
                    : Instant.now().plus(value, effective);
        }
        try {
            return Instant.parse(raw);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String scalar(Issue issue, String field) {
        switch (field) {
            case "id": return issue.getId();
            case "key": return issue.getKey();
            case "title": return issue.getTitle();
            case "description": return issue.getDescription();
            case "status": return issue.getStatus() == null ? null : issue.getStatus().wire();
            case "priority": return issue.getPriority() == null ? null : issue.getPriority().wire();
            case "type": return issue.getType() == null ? null : issue.getType().wire();
            case "projectid": case "projectId": return issue.getProjectId();
            case "reporterid": case "reporterId": return issue.getReporterId();
            case "assigneeid": case "assigneeId": return issue.getAssigneeId();
            case "storypoints": case "storyPoints":
                return issue.getStoryPoints() == null ? null : String.valueOf(issue.getStoryPoints());
            case "createdat": case "createdAt": return String.valueOf(issue.getCreatedAt());
            case "updatedat": case "updatedAt": return String.valueOf(issue.getUpdatedAt());
            case "duedate": case "dueDate":
                return issue.getDueDate() == null ? null : String.valueOf(issue.getDueDate());
            default: return null;
        }
    }

    private static Instant temporalField(Issue issue, String field) {
        switch (field) {
            case "createdat": case "createdAt": return issue.getCreatedAt();
            case "updatedat": case "updatedAt": return issue.getUpdatedAt();
            case "duedate": case "dueDate": return issue.getDueDate();
            default: return null;
        }
    }

    private static boolean matches(Issue issue, Cql.Clause clause, String viewerId) {
        List<String> expected = new ArrayList<>();
        for (String value : clause.values()) {
            expected.add("me".equals(value) ? viewerId : value);
        }

        // labels is the only multi-valued field, so it takes its own path.
        if ("labels".equals(clause.field())) {
            List<String> labels = issue.getLabels();
            switch (clause.op()) {
                case EQ: case CONTAINS: {
                    String needle = expected.get(0).toLowerCase(Locale.ROOT);
                    return labels.stream().anyMatch(l -> l.contains(needle));
                }
                case NE:
                    return labels.stream().noneMatch(l -> l.equalsIgnoreCase(expected.get(0)));
                case IN:
                    return expected.stream().anyMatch(v -> labels.contains(v.toLowerCase(Locale.ROOT)));
                case NOT_IN:
                    return expected.stream().noneMatch(v -> labels.contains(v.toLowerCase(Locale.ROOT)));
                default:
                    return true;
            }
        }

        String actual = scalar(issue, clause.field());
        switch (clause.op()) {
            case EQ:
                return actual != null && actual.equals(expected.get(0));
            case NE:
                return actual == null || !actual.equals(expected.get(0));
            case CONTAINS:
                return actual != null
                        && actual.toLowerCase(Locale.ROOT).contains(expected.get(0).toLowerCase(Locale.ROOT));
            case IN:
                return actual != null && expected.contains(actual);
            case NOT_IN:
                return actual == null || !expected.contains(actual);
            case GT: case LT: {
                Instant lhs = temporalField(issue, clause.field());
                Instant rhs = resolveTemporal(expected.get(0));
                if (lhs != null && rhs != null) {
                    return clause.op() == Cql.Op.GT ? lhs.isAfter(rhs) : lhs.isBefore(rhs);
                }
                try {
                    double a = Double.parseDouble(actual == null ? "" : actual);
                    double b = Double.parseDouble(expected.get(0));
                    return clause.op() == Cql.Op.GT ? a > b : a < b;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
            default:
                return true;
        }
    }

    private static Comparator<Issue> comparatorFor(String field) {
        switch (field) {
            case "priority":
                return Comparator.comparingInt(i -> i.getPriority() == null ? -1 : i.getPriority().ordinal());
            case "status":
                return Comparator.comparingInt(i -> i.getStatus() == null ? -1 : i.getStatus().ordinal());
            case "storyPoints": case "storypoints":
                return Comparator.comparingInt(i -> i.getStoryPoints() == null ? -1 : i.getStoryPoints());
            case "key": case "seq":
                return Comparator.comparingLong(Issue::getSeq);
            case "createdAt": case "createdat":
                return Comparator.comparing(Issue::getCreatedAt);
            case "dueDate": case "duedate":
                return Comparator.comparing(Issue::getDueDate,
                        Comparator.nullsLast(Comparator.naturalOrder()));
            case "title":
                return Comparator.comparing(Issue::getTitle, String.CASE_INSENSITIVE_ORDER);
            default:
                return Comparator.comparing(Issue::getUpdatedAt);
        }
    }

    public static Page search(List<Issue> issues, Cql.Query query, String viewerId,
                              int offset, int limit) {
        List<Issue> hits = new ArrayList<>();
        for (Issue issue : issues) {
            boolean all = true;
            for (Cql.Clause clause : query.clauses()) {
                if (!matches(issue, clause, viewerId)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                hits.add(issue);
            }
        }

        if (query.orderBy() != null) {
            Comparator<Issue> comparator = comparatorFor(query.orderBy().field());
            if (query.orderBy().direction() == Cql.Direction.DESC) {
                comparator = comparator.reversed();
            }
            hits.sort(comparator);
        } else {
            // Most-recently-touched first is the useful default for a tracker.
            hits.sort(Comparator.comparing(Issue::getUpdatedAt).reversed());
        }

        int safeLimit = Math.max(1, Math.min(limit, 200));
        int safeOffset = Math.max(0, offset);
        List<Issue> page = hits.stream().skip(safeOffset).limit(safeLimit).toList();
        return new Page(hits.size(), safeOffset, safeLimit, page);
    }

    /** Column filter used by the board endpoint. */
    public static List<Issue> inColumn(List<Issue> issues, String projectId, IssueStatus status) {
        return issues.stream()
                .filter(i -> projectId.equals(i.getProjectId()) && i.getStatus() == status)
                .sorted(Comparator.comparingDouble(Issue::getBoardRank))
                .toList();
    }
}
