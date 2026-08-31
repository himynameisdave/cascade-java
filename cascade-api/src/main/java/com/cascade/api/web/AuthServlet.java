package com.cascade.api.web;

import com.cascade.core.Validation;
import com.cascade.core.model.Role;
import com.cascade.core.model.User;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** {@code /api/auth/*} — registration, login, logout, and the directory. */
public class AuthServlet extends HttpServlet {

    private static final String[] AVATAR_COLORS = {
        "#6366f1", "#0ea5e9", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6", "#ec4899"
    };

    private final transient Services services;

    public AuthServlet(Services services) {
        this.services = services;
    }

    private static String tail(HttpServletRequest request) {
        String path = request.getPathInfo();
        return path == null ? "/" : path;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        switch (tail(request)) {
            case "/me" -> Json.write(response, 200,
                    Map.of("user", Views.user(AuthFilter.currentUser(request))));
            case "/users" -> {
                List<Map<String, Object>> users = services.users().findAll().stream()
                        .map(Views::user).toList();
                Json.write(response, 200, Map.of("users", users));
            }
            default -> throw ApiException.notFound("Unknown endpoint");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        switch (tail(request)) {
            case "/register" -> register(request, response);
            case "/login" -> login(request, response);
            case "/logout" -> {
                response.addHeader("Set-Cookie",
                        AuthFilter.SESSION_COOKIE + "=; HttpOnly; SameSite=Lax; Path=/; Max-Age=0");
                response.setStatus(204);
            }
            default -> throw ApiException.notFound("Unknown endpoint");
        }
    }

    private void register(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Map<String, Object> body = Json.readMap(request);
        String email = String.valueOf(body.getOrDefault("email", "")).trim().toLowerCase(Locale.ROOT);
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        String password = String.valueOf(body.getOrDefault("password", ""));

        if (password.length() < 10 || password.length() > 128) {
            throw new ApiException(400, "Validation failed",
                    Map.of("password", "password must be between 10 and 128 characters"));
        }

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setEmail(email);
        user.setName(name);
        // The first account to register owns the instance.
        user.setRole(services.users().count() == 0 ? Role.ADMIN : Role.MEMBER);
        user.setAvatarColor(AVATAR_COLORS[(int) (services.users().count() % AVATAR_COLORS.length)]);
        user.setCreatedAt(Instant.now());
        user.setPasswordHash(services.passwords().hash(password));
        Validation.check(user);

        if (services.users().findByEmail(email).isPresent()) {
            throw ApiException.conflict("An account with that email already exists");
        }
        services.users().insert(user);

        String token = services.jwt().issue(user);
        response.addHeader("Set-Cookie", sessionCookie(token));
        Json.write(response, 201, Map.of("token", token, "user", Views.user(user)));
    }

    private void login(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Map<String, Object> body = Json.readMap(request);
        String email = String.valueOf(body.getOrDefault("email", "")).trim().toLowerCase(Locale.ROOT);
        String password = String.valueOf(body.getOrDefault("password", ""));

        // The same message for an unknown account and a bad password, so this
        // endpoint is not a user-enumeration oracle.
        User user = services.users().findByEmail(email)
                .filter(candidate -> services.passwords().verify(password, candidate.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Incorrect email or password"));

        String token = services.jwt().issue(user);
        response.addHeader("Set-Cookie", sessionCookie(token));
        Json.write(response, 200, Map.of("token", token, "user", Views.user(user)));
    }

    private String sessionCookie(String token) {
        return AuthFilter.SESSION_COOKIE + "=" + token
                + "; HttpOnly; SameSite=Lax; Path=/; Max-Age="
                + (services.config().getTokenTtlHours() * 3600);
    }
}
