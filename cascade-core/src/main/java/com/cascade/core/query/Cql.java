package com.cascade.core.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cascade Query Language — a small JQL-like filter grammar.
 *
 * <pre>
 * status = in_progress AND priority in (critical, blocker)
 * assignee = me AND updated &gt; -7d
 * labels ~ payments ORDER BY priority DESC
 * </pre>
 *
 * Unknown fields are ignored rather than rejected, so a partly-typed query
 * still returns something useful instead of an error.
 */
public final class Cql {

    public enum Op { EQ, NE, CONTAINS, GT, LT, IN, NOT_IN }

    public enum Direction { ASC, DESC }

    public record Clause(String field, Op op, List<String> values) {
        public String first() {
            return values.isEmpty() ? "" : values.get(0);
        }
    }

    public record OrderBy(String field, Direction direction) { }

    public record Query(List<Clause> clauses, OrderBy orderBy) { }

    private static final Pattern CLAUSE = Pattern.compile(
            "(\\w+)\\s*(!=|>=|<=|=|~|>|<|\\bnot\\s+in\\b|\\bin\\b)\\s*"
                    + "(\\([^)]*\\)|\"[^\"]*\"|'[^']*'|\\S+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ORDER = Pattern.compile(
            "ORDER\\s+BY\\s+(\\w+)(?:\\s+(ASC|DESC))?", Pattern.CASE_INSENSITIVE);

    private Cql() {
    }

    /** Maps the names people type onto the fields actually stored. */
    static String canonicalField(String raw) {
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "assignee": return "assigneeId";
            case "reporter": return "reporterId";
            case "project": return "projectId";
            case "updated": return "updatedAt";
            case "created": return "createdAt";
            case "points": return "storyPoints";
            case "due": return "dueDate";
            case "summary": return "title";
            default: return raw.toLowerCase(Locale.ROOT);
        }
    }

    private static Op parseOp(String raw) {
        String op = raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        switch (op) {
            case "=": case "==": return Op.EQ;
            case "!=": return Op.NE;
            case "~": return Op.CONTAINS;
            case ">": case ">=": return Op.GT;
            case "<": case "<=": return Op.LT;
            case "in": return Op.IN;
            case "not in": return Op.NOT_IN;
            default: return null;
        }
    }

    private static String unquote(String raw) {
        String trimmed = raw.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                    || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    public static Query parse(String input) {
        List<Clause> clauses = new ArrayList<>();
        if (input == null) {
            return new Query(clauses, null);
        }

        Matcher matcher = CLAUSE.matcher(input);
        while (matcher.find()) {
            String field = matcher.group(1);
            // "ORDER BY x" also matches the clause shape; skip its keywords.
            if (field.equalsIgnoreCase("order") || field.equalsIgnoreCase("by")) {
                continue;
            }
            Op op = parseOp(matcher.group(2));
            if (op == null) {
                continue;
            }

            String rawValue = matcher.group(3).trim();
            List<String> values = new ArrayList<>();
            if (rawValue.startsWith("(") && rawValue.endsWith(")")) {
                for (String part : rawValue.substring(1, rawValue.length() - 1).split(",")) {
                    String value = unquote(part);
                    if (!value.isEmpty()) {
                        values.add(value);
                    }
                }
            } else {
                values.add(unquote(rawValue));
            }
            clauses.add(new Clause(canonicalField(field), op, values));
        }

        OrderBy orderBy = null;
        Matcher order = ORDER.matcher(input);
        if (order.find()) {
            Direction direction = "desc".equalsIgnoreCase(order.group(2))
                    ? Direction.DESC : Direction.ASC;
            orderBy = new OrderBy(canonicalField(order.group(1)), direction);
        }

        return new Query(clauses, orderBy);
    }
}
