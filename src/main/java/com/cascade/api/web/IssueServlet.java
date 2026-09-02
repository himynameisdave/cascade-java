package com.cascade.api.web;

import com.cascade.core.Ranks;
import com.cascade.core.Validation;
import com.cascade.core.model.Issue;
import com.cascade.core.model.IssuePriority;
import com.cascade.core.model.IssueStatus;
import com.cascade.core.model.IssueType;
import com.cascade.core.model.Project;
import com.cascade.core.model.Role;
import com.cascade.core.model.User;
import com.cascade.core.query.IssueSearch;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /api/issues/*} — list, board, detail, create, patch, rank and delete.
 */
public class IssueServlet extends JsonServlet {

    private final transient Services services;

    public IssueServlet(Services services) {
        this.services = services;
    }

    private static String[] segments(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null || path.equals("/")) {
            return new String[0];
        }
        return path.replaceAll("^/", "").split("/");
    }

    private Issue require(String idOrKey) {
        return services.issues().find(idOrKey)
                .orElseThrow(() -> ApiException.notFound("Issue not found"));
    }

    private Map<String, Object> view(Issue issue, Map<String, User> users) {
        return Views.issue(issue, users::get, services.comments().countByIssue(issue.getId()));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String[] parts = segments(request);
        Map<String, User> users = services.userIndex();

        if (parts.length == 0) {
            list(request, response, users);
            return;
        }
        if (parts.length == 2 && parts[0].equals("board")) {
            board(response, parts[1], users);
            return;
        }
        if (parts.length == 1) {
            Issue issue = require(parts[0]);
            List<Map<String, Object>> children = services.issues().findAll().stream()
                    .filter(i -> issue.getId().equals(i.getParentId()))
                    .map(i -> view(i, users))
                    .toList();
            Map<String, Object> body = Json.map();
            body.put("issue", view(issue, users));
            body.put("children", children);
            body.put("allowedTransitions", issue.getStatus().allowedTransitions().stream()
                    .map(IssueStatus::wire).toList());
            Json.write(response, 200, body);
            return;
        }
        throw ApiException.notFound("Unknown endpoint");
    }

    private void list(HttpServletRequest request, HttpServletResponse response,
                      Map<String, User> users) throws IOException {
        String projectId = request.getParameter("projectId");
        String status = request.getParameter("status");
        String assigneeId = request.getParameter("assigneeId");
        String label = request.getParameter("label");
        User viewer = AuthFilter.currentUser(request);

        List<Issue> issues = new ArrayList<>(projectId == null
                ? services.issues().findAll()
                : services.issues().findByProject(projectId));

        if (status != null) {
            List<String> wanted = List.of(status.split(","));
            issues.removeIf(i -> !wanted.contains(i.getStatus().wire()));
        }
        if (assigneeId != null) {
            String wanted = assigneeId.equals("me") ? viewer.getId() : assigneeId;
            issues.removeIf(i -> !wanted.equals(i.getAssigneeId()));
        }
        if (label != null) {
            String wanted = label.toLowerCase(Locale.ROOT);
            issues.removeIf(i -> !i.getLabels().contains(wanted));
        }

        issues.sort(Comparator.comparingDouble(Issue::getBoardRank));
        Map<String, Object> body = Json.map();
        body.put("total", issues.size());
        body.put("issues", issues.stream().map(i -> view(i, users)).toList());
        Json.write(response, 200, body);
    }

    private void board(HttpServletResponse response, String projectId, Map<String, User> users)
            throws IOException {
        List<Issue> issues = services.issues().findByProject(projectId);
        List<Map<String, Object>> columns = new ArrayList<>();

        for (IssueStatus status : services.config().getBoardColumns()) {
            List<Issue> column = IssueSearch.inColumn(issues, projectId, status);
            Integer limit = services.config().wipLimit(status);
            Map<String, Object> entry = Json.map();
            entry.put("status", status.wire());
            entry.put("wipLimit", limit);
            entry.put("overLimit", limit != null && column.size() > limit);
            entry.put("points", column.stream()
                    .mapToInt(i -> i.getStoryPoints() == null ? 0 : i.getStoryPoints()).sum());
            entry.put("issues", column.stream().map(i -> view(i, users)).toList());
            columns.add(entry);
        }
        Json.write(response, 200, Map.of("columns", columns));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String[] parts = segments(request);
        if (parts.length == 2 && parts[1].equals("rank")) {
            rank(request, response, parts[0]);
            return;
        }
        if (parts.length != 0) {
            throw ApiException.notFound("Unknown endpoint");
        }

        AuthFilter.require(request, Role.MEMBER);
        User author = AuthFilter.currentUser(request);
        Map<String, Object> body = Json.readMap(request);

        String projectId = String.valueOf(body.getOrDefault("projectId", ""));
        Project project = services.projects().find(projectId)
                .orElseThrow(() -> ApiException.badRequest("Unknown projectId"));

        long seq = services.issues().nextSequence(project.getKey());
        Issue issue = new Issue();
        issue.setId(UUID.randomUUID().toString());
        issue.setKey(project.getKey() + "-" + seq);
        issue.setProjectId(project.getId());
        issue.setSeq(seq);
        issue.setTitle(String.valueOf(body.getOrDefault("title", "")).trim());
        issue.setDescription(String.valueOf(body.getOrDefault("description", "")));
        issue.setStatus(parseStatus(body.get("status"), IssueStatus.BACKLOG));
        issue.setPriority(parsePriority(body.get("priority")));
        issue.setType(IssueType.parse(str(body.get("type"))));
        issue.setReporterId(author.getId());
        issue.setAssigneeId(str(body.get("assigneeId")));
        issue.setLabels(labels(body.get("labels")));
        issue.setStoryPoints(points(body.get("storyPoints")));
        issue.setBoardRank(seq * Ranks.STEP);
        issue.setCreatedAt(Instant.now());
        issue.setUpdatedAt(Instant.now());
        Validation.check(issue);

        services.issues().insert(issue);
        services.activity().record(author.getId(), "created", "issue", issue.getId(),
                project.getId(), Json.stringify(Map.of("key", issue.getKey())));

        Map<String, User> users = services.userIndex();
        Map<String, Object> view = view(issue, users);
        services.dispatcher().dispatch(project.getId(), "issue.created", Json.stringify(view));
        services.mail().notifyAssigned(issue, users.get(issue.getAssigneeId()), author);

        Json.write(response, 201, Map.of("issue", view));
    }

    private void rank(HttpServletRequest request, HttpServletResponse response, String key)
            throws IOException {
        AuthFilter.require(request, Role.MEMBER);
        Issue issue = require(key);
        Map<String, Object> body = Json.readMap(request);

        IssueStatus target = IssueStatus.parse(str(body.get("status")));
        if (target == null) {
            throw ApiException.badRequest("Unknown target status");
        }
        int index = points(body.get("index")) == null ? 0 : points(body.get("index"));

        List<Issue> column = new ArrayList<>(
                IssueSearch.inColumn(services.issues().findByProject(issue.getProjectId()),
                        issue.getProjectId(), target));
        column.removeIf(i -> i.getId().equals(issue.getId()));

        int at = Math.max(0, Math.min(index, column.size()));
        Double before = at > 0 ? column.get(at - 1).getBoardRank() : null;
        Double after = at < column.size() ? column.get(at).getBoardRank() : null;

        issue.setStatus(target);
        issue.setBoardRank(Ranks.between(before, after));
        issue.setUpdatedAt(Instant.now());
        services.issues().update(issue);

        Map<String, Object> view = view(issue, services.userIndex());
        services.dispatcher().dispatch(issue.getProjectId(), "issue.ranked", Json.stringify(view));
        Json.write(response, 200, Map.of("issue", view));
    }

    @Override
    protected void doPatch(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AuthFilter.require(request, Role.MEMBER);
        String[] parts = segments(request);
        if (parts.length != 1) {
            throw ApiException.notFound("Unknown endpoint");
        }

        Issue issue = require(parts[0]);
        Map<String, Object> body = Json.readMap(request);
        List<String> changed = new ArrayList<>();

        if (body.containsKey("status")) {
            IssueStatus target = IssueStatus.parse(str(body.get("status")));
            if (target == null) {
                throw ApiException.badRequest("Unknown status");
            }
            if (!issue.getStatus().canMoveTo(target)) {
                throw ApiException.unprocessable("Cannot move " + issue.getKey()
                        + " from " + issue.getStatus().wire() + " to " + target.wire());
            }
            if (target != issue.getStatus()) {
                issue.setStatus(target);
                changed.add("status");
            }
        }
        if (body.containsKey("title")) {
            issue.setTitle(String.valueOf(body.get("title")).trim());
            changed.add("title");
        }
        if (body.containsKey("description")) {
            issue.setDescription(String.valueOf(body.get("description")));
            changed.add("description");
        }
        if (body.containsKey("priority")) {
            issue.setPriority(parsePriority(body.get("priority")));
            changed.add("priority");
        }
        if (body.containsKey("assigneeId")) {
            issue.setAssigneeId(str(body.get("assigneeId")));
            changed.add("assigneeId");
        }
        if (body.containsKey("labels")) {
            issue.setLabels(labels(body.get("labels")));
            changed.add("labels");
        }
        if (body.containsKey("storyPoints")) {
            issue.setStoryPoints(points(body.get("storyPoints")));
            changed.add("storyPoints");
        }

        Validation.check(issue);
        if (!changed.isEmpty()) {
            issue.setUpdatedAt(Instant.now());
            services.issues().update(issue);
            User actor = AuthFilter.currentUser(request);
            services.activity().record(actor.getId(),
                    changed.contains("status") ? "transitioned" : "updated",
                    "issue", issue.getId(), issue.getProjectId(),
                    Json.stringify(Map.of("key", issue.getKey(), "changes", changed)));
        }

        Map<String, Object> view = view(issue, services.userIndex());
        if (!changed.isEmpty()) {
            services.dispatcher().dispatch(issue.getProjectId(), "issue.updated",
                    Json.stringify(view));
        }
        Json.write(response, 200, Map.of("issue", view, "changes", changed));
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AuthFilter.require(request, Role.ADMIN);
        String[] parts = segments(request);
        if (parts.length != 1) {
            throw ApiException.notFound("Unknown endpoint");
        }
        Issue issue = require(parts[0]);
        services.issues().delete(issue.getId());
        services.activity().record(AuthFilter.currentUser(request).getId(), "deleted", "issue",
                issue.getId(), issue.getProjectId(),
                Json.stringify(Map.of("key", issue.getKey())));
        response.setStatus(204);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static IssueStatus parseStatus(Object value, IssueStatus fallback) {
        IssueStatus parsed = IssueStatus.parse(str(value));
        return parsed == null ? fallback : parsed;
    }

    private static IssuePriority parsePriority(Object value) {
        IssuePriority parsed = IssuePriority.parse(str(value));
        if (value != null && parsed == null) {
            throw ApiException.badRequest("Unknown priority: " + value);
        }
        return parsed == null ? IssuePriority.MAJOR : parsed;
    }

    @SuppressWarnings("unchecked")
    private static List<String> labels(Object value) {
        List<String> labels = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object entry : list) {
                String label = String.valueOf(entry).trim().toLowerCase(Locale.ROOT);
                if (!label.isEmpty() && !labels.contains(label)) {
                    labels.add(label);
                }
            }
        }
        return labels;
    }

    private static Integer points(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.valueOf(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
