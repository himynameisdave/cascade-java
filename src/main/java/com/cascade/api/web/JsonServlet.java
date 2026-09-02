package com.cascade.api.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Base servlet that routes PATCH.
 *
 * <p>{@link HttpServlet} predates PATCH and has no {@code doPatch} hook, so
 * {@link #service} intercepts it; everything else falls through to the standard
 * dispatch.
 */
public abstract class JsonServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(request.getMethod())) {
            doPatch(request, response);
            return;
        }
        super.service(request, response);
    }

    protected void doPatch(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Json.error(response, 405, "PATCH is not supported on this endpoint");
    }
}
