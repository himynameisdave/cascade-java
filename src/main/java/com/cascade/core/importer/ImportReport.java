package com.cascade.core.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * The outcome of parsing an import file. Unusable rows are reported with their
 * line number rather than failing the whole batch, so a 500-row export with two
 * bad rows still imports 498.
 */
public class ImportReport {

    public record Skipped(int line, String reason) { }

    private final List<ImportRow> rows = new ArrayList<>();
    private final List<Skipped> skipped = new ArrayList<>();

    public List<ImportRow> getRows() { return rows; }

    public List<Skipped> getSkipped() { return skipped; }

    public void add(ImportRow row) { rows.add(row); }

    public void skip(int line, String reason) { skipped.add(new Skipped(line, reason)); }
}
