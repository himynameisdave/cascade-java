package com.cascade.core.importer;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a CSV export. Column names are matched case- and spacing-insensitively
 * against a set of aliases, so Jira, Linear and hand-made spreadsheets all work
 * without the user having to rename headers first.
 */
public final class CsvImporter {

    private CsvImporter() {
    }

    private static String pick(Map<String, Integer> headers, String[] row, String... names) {
        for (String name : names) {
            Integer index = headers.get(name.toLowerCase(Locale.ROOT));
            if (index != null && index < row.length) {
                String value = row[index];
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }
        return "";
    }

    public static ImportReport parse(String content) {
        ImportReport report = new ImportReport();
        try (Reader source = new StringReader(content); CSVReader reader = new CSVReader(source)) {
            String[] headerRow = reader.readNext();
            if (headerRow == null) {
                report.skip(1, "the file is empty");
                return report;
            }

            Map<String, Integer> headers = new HashMap<>();
            for (int i = 0; i < headerRow.length; i++) {
                headers.put(headerRow[i].trim().toLowerCase(Locale.ROOT), i);
            }

            String[] row;
            int line = 1;
            while ((row = reader.readNext()) != null) {
                line++;
                String title = pick(headers, row, "title", "summary", "name");
                if (title.isEmpty()) {
                    report.skip(line, "missing a title/summary column");
                    continue;
                }

                ImportRow parsed = new ImportRow();
                parsed.setTitle(title);
                parsed.setDescription(pick(headers, row, "description", "body", "details"));
                parsed.setStatus(Normalizers.status(pick(headers, row, "status", "state")));
                parsed.setPriority(Normalizers.priority(pick(headers, row, "priority", "severity")));
                parsed.setType(Normalizers.type(pick(headers, row, "type", "issue type", "issuetype")));
                parsed.setLabels(Normalizers.labels(pick(headers, row, "labels", "tags", "components")));
                parsed.setAssigneeEmail(Normalizers.trimToNull(
                        pick(headers, row, "assignee", "assignee email", "owner")));
                parsed.setStoryPoints(Normalizers.points(
                        pick(headers, row, "story points", "storypoints", "points")));
                parsed.setDueDate(Normalizers.date(pick(headers, row, "due date", "duedate", "due")));
                report.add(parsed);
            }
        } catch (IOException | CsvValidationException e) {
            report.skip(0, "could not read the CSV: " + e.getMessage());
        }
        return report;
    }
}
