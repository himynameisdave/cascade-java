package com.cascade.api.web;

import com.cascade.core.Validation;
import com.cascade.core.model.Role;
import com.cascade.core.model.User;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authenticates the caller and renders every thrown {@link ApiException} as
 * JSON, so no servlet has to write an error body itself.
 */
public class AuthFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(AuthFilter.class);
    public static final String USER_ATTRIBUTE = "cascade.user";
    public static final String SESSION_COOKIE = "cascade_session";

    /** Paths reachable without a token. */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/health", "/api/auth/login", "/api/auth/register", "/api/auth/logout");

    private final Services services;

    public AuthFilter(Services services) {
        this.services = services;
    }

    private Optional<String> token(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Optional.of(header.substring(7).trim());
        }
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if (SESSION_COOKIE.equals(cookie.getName())) {
                    return Optional.ofNullable(cookie.getValue());
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        try {
            String path = request.getRequestURI();
            if (!PUBLIC_PATHS.contains(path)) {
                User user = token(request)
                        .flatMap(services.jwt()::verify)
                        .flatMap(claims -> services.users().findById(claims.userId()))
                        .orElseThrow(() -> ApiException.unauthorized("Authentication required"));
                request.setAttribute(USER_ATTRIBUTE, user);
            }
            chain.doFilter(request, response);
        } catch (ApiException e) {
            Map<String, Object> body = Json.map();
            body.put("error", e.getMessage());
            if (e.details() != null) {
                body.put("details", e.details());
            }
            Json.write(response, e.status(), body);
        } catch (Validation.ValidationException e) {
            Map<String, Object> body = Json.map();
            body.put("error", "Validation failed");
            body.put("details", e.details());
            Json.write(response, 400, body);
        } catch (RuntimeException e) {
            LOG.error("unhandled failure on {}", request.getRequestURI(), e);
            Json.error(response, 500, "Internal server error");
        }
    }

    /** The authenticated caller, or 401 if the servlet is somehow unguarded. */
    public static User currentUser(HttpServletRequest request) {
        Object user = request.getAttribute(USER_ATTRIBUTE);
        if (user instanceof User u) {
            return u;
        }
        throw ApiException.unauthorized("Authentication required");
    }

    public static void require(HttpServletRequest request, Role minimum) {
        User user = currentUser(request);
        if (!user.getRole().atLeast(minimum)) {
            throw ApiException.forbidden("Requires " + minimum.name().toLowerCase(java.util.Locale.ROOT)
                    + " access");
        }
    }
}
