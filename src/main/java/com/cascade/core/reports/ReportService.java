package com.cascade.core.reports;

import com.cascade.core.model.Issue;
import com.cascade.core.model.IssueStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Sprint reporting: burndown, throughput, cycle time and breakdowns. */
public final class ReportService {

    public record BurndownPoint(String date, long remaining, double ideal) { }

    public record Burndown(long total, List<BurndownPoint> series) { }

    public record ThroughputWeek(String week, int closed, long points) { }

    public record CycleTime(double medianDays, double p90Days, int sample) { }

    private ReportService() {
    }

    private static long points(Issue issue) {
        return issue.getStoryPoints() == null ? 0 : issue.getStoryPoints();
    }

    public static Map<String, Object> counts(List<Issue> issues) {
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        Map<String, Integer> byPriority = new LinkedHashMap<>();
        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<String, Integer> byAssignee = new LinkedHashMap<>();

        for (Issue issue : issues) {
            byStatus.merge(issue.getStatus().wire(), 1, Integer::sum);
            byPriority.merge(issue.getPriority().wire(), 1, Integer::sum);
            byType.merge(issue.getType().wire(), 1, Integer::sum);
            byAssignee.merge(
                    issue.getAssigneeId() == null ? "unassigned" : issue.getAssigneeId(),
                    1, Integer::sum);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", issues.size());
        result.put("byStatus", byStatus);
        result.put("byPriority", byPriority);
        result.put("byType", byType);
        result.put("byAssignee", byAssignee);
        result.put("unassigned", issues.stream().filter(i -> i.getAssigneeId() == null).count());
        result.put("overdue", issues.stream().filter(Issue::isOverdue).count());
        result.put("pointsCommitted", issues.stream().mapToLong(ReportService::points).sum());
        result.put("pointsCompleted", issues.stream()
                .filter(i -> i.getStatus() == IssueStatus.DONE)
                .mapToLong(ReportService::points).sum());
        return result;
    }

    /** Remaining story points per day against a straight-line ideal. */
    public static Burndown burndown(List<Issue> issues, int days) {
        int window = Math.max(1, Math.min(days, 90));
        long total = issues.stream().mapToLong(ReportService::points).sum();
        LocalDate start = LocalDate.now(ZoneOffset.UTC).minusDays(window);

        List<BurndownPoint> series = new ArrayList<>();
        for (int offset = 0; offset <= window; offset++) {
            Instant dayEnd = start.plusDays(offset + 1L).atStartOfDay().toInstant(ZoneOffset.UTC);
            long completed = issues.stream()
                    .filter(i -> i.getStatus() == IssueStatus.DONE)
                    .filter(i -> i.getUpdatedAt() != null && !i.getUpdatedAt().isAfter(dayEnd))
                    .mapToLong(ReportService::points)
                    .sum();
            double ideal = Math.max(0, total - ((double) total / window) * offset);
            series.add(new BurndownPoint(start.plusDays(offset).toString(), total - completed, ideal));
        }
        return new Burndown(total, series);
    }

    /** Issues closed per ISO week, oldest first. */
    public static List<ThroughputWeek> throughput(List<Issue> issues, int weeks) {
        int window = Math.max(1, Math.min(weeks, 52));
        WeekFields fields = WeekFields.ISO;
        List<ThroughputWeek> result = new ArrayList<>();

        for (int back = window - 1; back >= 0; back--) {
            LocalDate anchor = LocalDate.now(ZoneOffset.UTC).minusWeeks(back);
            int week = anchor.get(fields.weekOfWeekBasedYear());
            int year = anchor.get(fields.weekBasedYear());

            List<Issue> closed = issues.stream()
                    .filter(i -> i.getStatus() == IssueStatus.DONE && i.getUpdatedAt() != null)
                    .filter(i -> {
                        LocalDate day = i.getUpdatedAt().atZone(ZoneOffset.UTC).toLocalDate();
                        return day.get(fields.weekOfWeekBasedYear()) == week
                                && day.get(fields.weekBasedYear()) == year;
                    })
                    .toList();

            result.add(new ThroughputWeek(
                    String.format(Locale.ROOT, "%d-W%02d", year, week),
                    closed.size(),
                    closed.stream().mapToLong(ReportService::points).sum()));
        }
        return result;
    }

    /** Median and 90th-percentile days from creation to reaching {@code done}. */
    public static CycleTime cycleTime(List<Issue> issues) {
        List<Double> durations = new ArrayList<>();
        for (Issue issue : issues) {
            if (issue.getStatus() == IssueStatus.DONE
                    && issue.getCreatedAt() != null && issue.getUpdatedAt() != null) {
                double hours = Duration.between(issue.getCreatedAt(), issue.getUpdatedAt())
                        .get(ChronoUnit.SECONDS) / 3600.0;
                durations.add(hours / 24.0);
            }
        }
        if (durations.isEmpty()) {
            return new CycleTime(0, 0, 0);
        }
        Collections.sort(durations);
        double median = durations.get(durations.size() / 2);
        int p90Index = Math.min(durations.size() - 1, (int) Math.floor(durations.size() * 0.9));
        return new CycleTime(round2(median), round2(durations.get(p90Index)), durations.size());
    }

    public static List<Map.Entry<String, Integer>> topLabels(List<Issue> issues, int limit) {
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (Issue issue : issues) {
            for (String label : issue.getLabels()) {
                tally.merge(label, 1, Integer::sum);
            }
        }
        return tally.entrySet().stream()
                .sorted((a, b) -> b.getValue().equals(a.getValue())
                        ? a.getKey().compareTo(b.getKey())
                        : b.getValue() - a.getValue())
                .limit(limit)
                .toList();
    }

    public static Map<String, Object> summary(List<Issue> issues, String projectId,
                                              int days, int weeks) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("generatedAt", Instant.now().toString());
        summary.put("projectId", projectId);
        summary.put("counts", counts(issues));
        summary.put("burndown", burndown(issues, days));
        summary.put("throughput", throughput(issues, weeks));
        summary.put("cycleTime", cycleTime(issues));
        summary.put("topLabels", topLabels(issues, 10));
        return summary;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
