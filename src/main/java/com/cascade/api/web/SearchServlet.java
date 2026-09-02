package com.cascade.api.web;

import com.cascade.core.model.Issue;
import com.cascade.core.model.User;
import com.cascade.core.query.Cql;
import com.cascade.core.query.IssueSearch;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** {@code /api/search} — Cascade Query Language, suggestions and the feed. */
public class SearchServlet extends JsonServlet {

    private final transient Services services;

    public SearchServlet(Services services) {
        this.services = services;
    }

    private static int intParam(HttpServletRequest request, String name, int fallback) {
        String raw = request.getParameter(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String path = request.getPathInfo() == null ? "/" : request.getPathInfo();
        switch (path) {
            case "/", "" -> search(request, response);
            case "/suggest" -> suggest(request, response);
            case "/activity" -> activity(request, response);
            default -> throw ApiException.notFound("Unknown endpoint");
        }
    }

    private void search(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String q = request.getParameter("q");
        User viewer = AuthFilter.currentUser(request);
        Cql.Query query = Cql.parse(q == null ? "" : q);

        IssueSearch.Page page = IssueSearch.search(
                services.issues().findAll(), query, viewer.getId(),
                intParam(request, "offset", 0), intParam(request, "limit", 50));

        Map<String, User> users = services.userIndex();
        Map<String, Object> body = Json.map();
        body.put("total", page.total());
        body.put("offset", page.offset());
        body.put("limit", page.limit());
        body.put("clauses", query.clauses().size());
        body.put("results", page.issues().stream()
                .map(i -> Views.issueHit(i, users::get)).toList());
        Json.write(response, 200, body);
    }

    private void suggest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String term = request.getParameter("term");
        if (term == null || term.isBlank()) {
            Json.write(response, 200, Map.of("labels", List.of(), "keys", List.of(),
                    "people", List.of()));
            return;
        }
        String needle = term.trim().toLowerCase(java.util.Locale.ROOT);
        List<Issue> issues = services.issues().findAll();

        List<String> labels = issues.stream()
                .flatMap(i -> i.getLabels().stream())
                .filter(l -> l.contains(needle))
                .distinct().sorted().limit(10).toList();

        List<Map<String, Object>> keys = issues.stream()
                .filter(i -> i.getKey().toLowerCase(java.util.Locale.ROOT).contains(needle)
                        || i.getTitle().toLowerCase(java.util.Locale.ROOT).contains(needle))
                .limit(10)
                .map(i -> Map.<String, Object>of("key", i.getKey(), "title", i.getTitle()))
                .toList();

        List<Map<String, Object>> people = services.users().findAll().stream()
                .filter(u -> u.getName().toLowerCase(java.util.Locale.ROOT).contains(needle)
                        || u.getEmail().contains(needle))
                .limit(10).map(Views::user).toList();

        Json.write(response, 200, Map.of("labels", labels, "keys", keys, "people", people));
    }

    private void activity(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        List<Map<String, Object>> feed = services.activity().feed(
                request.getParameter("projectId"),
                request.getParameter("actorId"),
                intParam(request, "limit", 50));
        Json.write(response, 200, Map.of("activity", feed));
    }
}
