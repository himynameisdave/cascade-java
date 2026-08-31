package com.cascade.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Fixed-width table rendering for terminal output. */
public final class Tables {

    private Tables() {
    }

    public static String text(JsonObject object, String field) {
        JsonElement value = object == null ? null : object.get(field);
        if (value == null || value.isJsonNull()) {
            return "—";
        }
        return value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }

    public static String nested(JsonObject object, String parent, String field) {
        if (object == null || !object.has(parent) || object.get(parent).isJsonNull()) {
            return "—";
        }
        return text(object.getAsJsonObject(parent), field);
    }

    public static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    public static String render(List<String> headers, List<List<String>> rows) {
        List<Integer> widths = new ArrayList<>();
        for (String header : headers) {
            widths.add(header.length());
        }
        for (List<String> row : rows) {
            for (int i = 0; i < row.size() && i < widths.size(); i++) {
                widths.set(i, Math.max(widths.get(i), row.get(i).length()));
            }
        }

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < headers.size(); i++) {
            out.append(pad(headers.get(i).toUpperCase(java.util.Locale.ROOT), widths.get(i)));
            out.append(i == headers.size() - 1 ? "" : "  ");
        }
        out.append('\n');
        for (int i = 0; i < widths.size(); i++) {
            out.append("-".repeat(widths.get(i)));
            out.append(i == widths.size() - 1 ? "" : "  ");
        }
        out.append('\n');

        for (List<String> row : rows) {
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < row.size() && i < widths.size(); i++) {
                line.append(pad(row.get(i), widths.get(i)));
                line.append(i == row.size() - 1 ? "" : "  ");
            }
            out.append(line.toString().stripTrailing()).append('\n');
        }
        return out.toString();
    }

    private static String pad(String value, int width) {
        return value.length() >= width ? value : value + " ".repeat(width - value.length());
    }

    public static List<JsonObject> objects(JsonArray array) {
        List<JsonObject> items = new ArrayList<>();
        if (array != null) {
            array.forEach(element -> items.add(element.getAsJsonObject()));
        }
        return items;
    }
}
