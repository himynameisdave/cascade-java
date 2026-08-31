package com.cascade.api.web;

import com.cascade.core.model.Issue;
import com.cascade.core.model.User;
import com.cascade.core.reports.ReportService;
import com.opencsv.CSVWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** {@code /api/reports/*} — the JSON summary and a CSV export. */
public class ReportServlet extends JsonServlet {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withZone(ZoneOffset.UTC);

    private final transient Services services;

    public ReportServlet(Services services) {
        this.services = services;
    }

    private List<Issue> scoped(HttpServletRequest request) {
        String projectId = request.getParameter("projectId");
        return projectId == null
                ? services.issues().findAll()
                : services.issues().findByProject(projectId);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String path = request.getPathInfo() == null ? "/" : request.getPathInfo();
        switch (path) {
            case "/summary" -> summary(request, response);
            case "/export.csv" -> exportCsv(request, response);
            default -> throw ApiException.notFound("Unknown endpoint");
        }
    }

    private void summary(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (!services.config().feature("reports")) {
            throw ApiException.badRequest("Reporting is disabled on this instance");
        }
        int days = parse(request.getParameter("days"), 14);
        int weeks = parse(request.getParameter("weeks"), 8);
        Json.write(response, 200, ReportService.summary(
                scoped(request), request.getParameter("projectId"), days, weeks));
    }

    private void exportCsv(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        List<Issue> issues = scoped(request);
        Map<String, User> users = services.userIndex();

        response.setStatus(200);
        response.setContentType("text/csv; charset=utf-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"cascade-issues.csv\"");

        try (Writer out = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
             CSVWriter csv = new CSVWriter(out)) {
            csv.writeNext(new String[] {
                "key", "title", "type", "status", "priority", "assignee", "reporter",
                "labels", "storyPoints", "dueDate", "created", "updated"
            });
            for (Issue issue : issues) {
                csv.writeNext(new String[] {
                    issue.getKey(),
                    issue.getTitle(),
                    issue.getType().wire(),
                    issue.getStatus().wire(),
                    issue.getPriority().wire(),
                    email(users, issue.getAssigneeId()),
                    email(users, issue.getReporterId()),
                    String.join("|", issue.getLabels()),
                    issue.getStoryPoints() == null ? "" : String.valueOf(issue.getStoryPoints()),
                    issue.getDueDate() == null ? "" : DAY.format(issue.getDueDate()),
                    issue.getCreatedAt() == null ? "" : DAY.format(issue.getCreatedAt()),
                    issue.getUpdatedAt() == null ? "" : DAY.format(issue.getUpdatedAt())
                });
            }
        }
    }

    private static String email(Map<String, User> users, String id) {
        User user = id == null ? null : users.get(id);
        return user == null ? "" : user.getEmail();
    }

    private static int parse(String raw, int fallback) {
        try {
            return raw == null ? fallback : Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
