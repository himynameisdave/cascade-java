package com.cascade.api.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/** {@code /api/health} — unauthenticated liveness and build information. */
public class HealthServlet extends JsonServlet {

    private final transient Services services;
    private final long startedAt = System.currentTimeMillis();

    public HealthServlet(Services services) {
        this.services = services;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Map<String, Object> body = Json.map();
        body.put("status", "ok");
        body.put("version", CascadeVersion.VERSION);
        body.put("uptimeSeconds", (System.currentTimeMillis() - startedAt) / 1000);
        body.put("features", services.config().getFeatures());
        body.put("counts", Map.of(
                "users", services.users().count(),
                "projects", services.projects().findAll(true).size(),
                "issues", services.issues().findAll().size()));
        Json.write(response, 200, body);
    }
}
