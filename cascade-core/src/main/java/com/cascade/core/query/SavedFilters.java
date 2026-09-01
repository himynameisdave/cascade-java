package com.cascade.core.query;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import java.util.List;

/**
 * Dashboard gadgets store a JSONPath expression and evaluate it against the
 * serialized issue set, which lets a saved filter reach fields the CQL grammar
 * does not model.
 *
 * <p>Expressions come from users, so evaluation runs with
 * {@code SUPPRESS_EXCEPTIONS}: a filter that no longer matches anything should
 * render an empty gadget, not fail the dashboard.
 */
public final class SavedFilters {

    private static final Configuration LENIENT = Configuration.defaultConfiguration()
            .addOptions(Option.SUPPRESS_EXCEPTIONS, Option.ALWAYS_RETURN_LIST);

    private SavedFilters() {
    }

    /** Evaluates {@code expression} against a JSON document of issues. */
    public static List<Object> evaluate(String issuesJson, String expression) {
        if (issuesJson == null || expression == null || expression.isBlank()) {
            return List.of();
        }
        try {
            return JsonPath.using(LENIENT).parse(issuesJson).read(expression, List.class);
        } catch (RuntimeException e) {
            // A malformed expression is caller error, not a server fault.
            throw new IllegalArgumentException("invalid JSONPath expression: " + e.getMessage(), e);
        }
    }

    /** Cheap syntax check used when a gadget is saved rather than rendered. */
    public static boolean isValid(String expression) {
        try {
            JsonPath.compile(expression);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
