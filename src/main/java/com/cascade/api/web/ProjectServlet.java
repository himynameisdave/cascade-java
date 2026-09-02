package com.cascade.api.web;

import com.cascade.core.Validation;
import com.cascade.core.model.Issue;
import com.cascade.core.model.Project;
import com.cascade.core.model.Role;
import com.cascade.core.model.User;
import com.cascade.core.reports.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** {@code /api/projects/*} — listing, detail with stats, create and delete. */
public class ProjectServlet extends JsonServlet {

    private final transient Services services;

    public ProjectServlet(Services services) {
        this.services = services;
    }

    private static String segment(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null || path.equals("/")) {
            return null;
        }
        return path.replaceAll("^/", "").split("/")[0];
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String id = segment(request);
        if (id == null) {
            boolean archived = Boolean.parseBoolean(request.getParameter("archived"));
            List<Map<String, Object>> projects = new ArrayList<>();
            for (Project project : services.projects().findAll(archived)) {
                projects.add(Views.project(project, services.issues().findByProject(project.getId())));
            }
            Json.write(response, 200, Map.of("projects", projects));
            return;
        }

        Project project = services.projects().find(id)
                .orElseThrow(() -> ApiException.notFound("Project not found"));
        List<Issue> issues = services.issues().findByProject(project.getId());
        List<Map<String, Object>> members = services.users().findAll().stream()
                .filter(u -> project.getMemberIds().contains(u.getId()))
                .map(Views::user)
                .toList();

        Map<String, Object> body = Json.map();
        body.put("project", Views.project(project, issues));
        body.put("members", members);
        body.put("stats", ReportService.counts(issues));
        Json.write(response, 200, body);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AuthFilter.require(request, Role.MEMBER);
        User author = AuthFilter.currentUser(request);
        Map<String, Object> body = Json.readMap(request);

        String key = String.valueOf(body.getOrDefault("key", ""))
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (key.isEmpty()) {
            throw ApiException.badRequest("Project key must contain letters or digits");
        }
        if (services.projects().find(key).isPresent()) {
            throw ApiException.conflict("Project key is taken");
        }

        Project project = new Project();
        project.setId(UUID.randomUUID().toString());
        project.setKey(key);
        project.setName(String.valueOf(body.getOrDefault("name", "")).trim());
        project.setDescription(String.valueOf(body.getOrDefault("description", "")));
        project.setLeadId(author.getId());
        project.setCreatedAt(Instant.now());

        List<String> members = new ArrayList<>();
        if (body.get("memberIds") instanceof List<?> list) {
            list.forEach(entry -> members.add(String.valueOf(entry)));
        }
        if (!members.contains(author.getId())) {
            members.add(author.getId());
        }
        project.setMemberIds(members);
        Validation.check(project);

        services.projects().insert(project);
        services.activity().record(author.getId(), "created", "project", project.getId(),
                project.getId(), Json.stringify(Map.of("key", project.getKey())));

        Json.write(response, 201, Map.of("project", Views.project(project, List.of())));
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AuthFilter.require(request, Role.ADMIN);
        String id = segment(request);
        if (id == null) {
            throw ApiException.notFound("Unknown endpoint");
        }
        Project project = services.projects().find(id)
                .orElseThrow(() -> ApiException.notFound("Project not found"));
        services.projects().delete(project.getId());
        response.setStatus(204);
    }
}
