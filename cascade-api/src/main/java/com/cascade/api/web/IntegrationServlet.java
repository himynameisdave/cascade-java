package com.cascade.api.web;

import com.cascade.core.importer.CsvImporter;
import com.cascade.core.importer.ImportReport;
import com.cascade.core.importer.ImportRow;
import com.cascade.core.importer.JiraXmlImporter;
import com.cascade.core.model.Issue;
import com.cascade.core.model.Project;
import com.cascade.core.model.Role;
import com.cascade.core.model.User;
import com.cascade.core.model.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** {@code /api/integrations/*} — webhook management and issue import. */
public class IntegrationServlet extends JsonServlet {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final transient Services services;

    public IntegrationServlet(Services services) {
        this.services = services;
    }

    private static String[] segments(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null || path.equals("/")) {
            return new String[0];
        }
        return path.replaceAll("^/", "").split("/");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AuthFilter.require(request, Role.ADMIN);
        String[] parts = segments(request);
        if (parts.length == 1 && parts[0].equals("webhooks")) {
            List<Map<String, Object>> hooks =
                    services.webhooks().findByProject(request.getParameter("projectId"))
                            .stream().map(Views::webhook).toList();
            Json.write(response, 200, Map.of(
                    "webhooks", hooks, "availableEvents", services.webhookEvents()));
            return;
        }
        throw ApiException.notFound("Unknown endpoint");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String[] parts = segments(request);
        if (parts.length == 1 && parts[0].equals("webhooks")) {
            createWebhook(request, response);
            return;
        }
        if (parts.length == 2 && parts[0].equals("import")) {
            importIssues(request, response, parts[1]);
            return;
        }
        throw ApiException.notFound("Unknown endpoint");
    }

    private void createWebhook(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AuthFilter.require(request, Role.ADMIN);
        Map<String, Object> body = Json.readMap(request);

        String url = String.valueOf(body.getOrDefault("url", "")).trim();
        String projectId = String.valueOf(body.getOrDefault("projectId", "")).trim();
        if (url.isEmpty() || projectId.isEmpty()) {
            throw ApiException.badRequest("projectId and url are required");
        }

        List<String> events = new ArrayList<>();
        if (body.get("events") instanceof List<?> list) {
            list.forEach(entry -> events.add(String.valueOf(entry)));
        }
        if (events.isEmpty()) {
            events.add("*");
        }
        List<String> unknown = events.stream()
                .filter(e -> !e.equals("*") && !services.webhookEvents().contains(e))
                .toList();
        if (!unknown.isEmpty()) {
            throw ApiException.badRequest("Unknown events: " + String.join(", ", unknown));
        }

        // The guard runs before the row is written, so an unreachable target is
        // rejected at configuration time rather than on first delivery.
        if (!new com.cascade.api.webhook.SsrfGuard(services.config().getWebhookAllowlist())
                .isDeliverable(url)) {
            throw ApiException.badRequest("That host is not in the webhook allowlist");
        }

        byte[] raw = new byte[24];
        RANDOM.nextBytes(raw);

        Webhook hook = new Webhook();
        hook.setId(UUID.randomUUID().toString());
        hook.setProjectId(projectId);
        hook.setUrl(url);
        hook.setEvents(events);
        hook.setSecret(HexFormat.of().formatHex(raw));
        hook.setActive(true);
        hook.setCreatedAt(Instant.now());
        services.webhooks().insert(hook);

        Map<String, Object> view = Views.webhook(hook);
        view.put("secret", hook.getSecret()); // shown once, at creation
        Json.write(response, 201, Map.of("webhook", view));
    }

    private void importIssues(HttpServletRequest request, HttpServletResponse response,
                              String mode) throws IOException {
        AuthFilter.require(request, Role.MEMBER);
        if (!services.config().feature("csvImport")) {
            throw ApiException.badRequest("Importing is disabled on this instance");
        }

        String filename = request.getHeader("x-cascade-filename");
        String content = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        ImportReport report = filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".xml")
                ? JiraXmlImporter.parse(content)
                : CsvImporter.parse(content);

        if (mode.equals("preview")) {
            Map<String, Object> body = Json.map();
            body.put("filename", filename);
            body.put("willCreate", report.getRows().size());
            body.put("skipped", report.getSkipped());
            body.put("preview", report.getRows().stream().limit(25).toList());
            Json.write(response, 200, body);
            return;
        }
        if (!mode.equals("commit")) {
            throw ApiException.notFound("Unknown endpoint");
        }

        String projectId = request.getParameter("projectId");
        Project project = services.projects().find(projectId == null ? "" : projectId)
                .orElseThrow(() -> ApiException.badRequest("Unknown projectId"));
        User author = AuthFilter.currentUser(request);

        Map<String, String> byEmail = new HashMap<>();
        for (User user : services.users().findAll()) {
            byEmail.put(user.getEmail().toLowerCase(Locale.ROOT), user.getId());
        }

        List<Map<String, Object>> created = new ArrayList<>();
        for (ImportRow row : report.getRows()) {
            long seq = services.issues().nextSequence(project.getKey());
            Issue issue = new Issue();
            issue.setId(UUID.randomUUID().toString());
            issue.setKey(project.getKey() + "-" + seq);
            issue.setProjectId(project.getId());
            issue.setSeq(seq);
            issue.setTitle(row.getTitle());
            issue.setDescription(row.getDescription());
            issue.setStatus(row.getStatus());
            issue.setPriority(row.getPriority());
            issue.setType(row.getType());
            issue.setReporterId(author.getId());
            issue.setAssigneeId(row.getAssigneeEmail() == null
                    ? null : byEmail.get(row.getAssigneeEmail().toLowerCase(Locale.ROOT)));
            issue.setLabels(row.getLabels());
            issue.setStoryPoints(row.getStoryPoints());
            issue.setDueDate(row.getDueDate());
            issue.setBoardRank(seq * com.cascade.core.Ranks.STEP);
            issue.setCreatedAt(Instant.now());
            issue.setUpdatedAt(Instant.now());
            services.issues().insert(issue);
            created.add(Map.of("key", issue.getKey(), "title", issue.getTitle()));
        }

        services.activity().record(author.getId(), "created", "project", project.getId(),
                project.getId(), Json.stringify(Map.of("imported", created.size())));

        Map<String, Object> body = Json.map();
        body.put("imported", created.size());
        body.put("skipped", report.getSkipped());
        body.put("issues", created);
        Json.write(response, 201, body);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AuthFilter.require(request, Role.ADMIN);
        String[] parts = segments(request);
        if (parts.length == 2 && parts[0].equals("webhooks")) {
            services.webhooks().delete(parts[1]);
            response.setStatus(204);
            return;
        }
        throw ApiException.notFound("Unknown endpoint");
    }
}
