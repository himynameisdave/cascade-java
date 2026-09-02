package com.cascade.api.web;

import com.cascade.core.Validation;
import com.cascade.core.model.Comment;
import com.cascade.core.model.Issue;
import com.cascade.core.model.Role;
import com.cascade.core.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code /api/comments/*} — comments addressed by issue id or key. */
public class CommentServlet extends JsonServlet {

    private final transient Services services;

    public CommentServlet(Services services) {
        this.services = services;
    }

    private static String[] segments(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null || path.equals("/")) {
            return new String[0];
        }
        return path.replaceAll("^/", "").split("/");
    }

    private Issue requireIssue(String idOrKey) {
        return services.issues().find(idOrKey)
                .orElseThrow(() -> ApiException.notFound("Issue not found"));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String[] parts = segments(request);
        if (parts.length != 1) {
            throw ApiException.notFound("Unknown endpoint");
        }
        Issue issue = requireIssue(parts[0]);
        Map<String, User> users = services.userIndex();
        List<Map<String, Object>> comments = services.comments().findByIssue(issue.getId())
                .stream().map(c -> Views.comment(c, users::get)).toList();
        Json.write(response, 200, Map.of("comments", comments));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AuthFilter.require(request, Role.MEMBER);
        String[] parts = segments(request);
        if (parts.length != 1) {
            throw ApiException.notFound("Unknown endpoint");
        }

        Issue issue = requireIssue(parts[0]);
        User author = AuthFilter.currentUser(request);
        Map<String, Object> body = Json.readMap(request);

        Comment comment = new Comment();
        comment.setId(UUID.randomUUID().toString());
        comment.setIssueId(issue.getId());
        comment.setAuthorId(author.getId());
        comment.setBody(String.valueOf(body.getOrDefault("body", "")));
        comment.setCreatedAt(Instant.now());
        Validation.check(comment);

        services.comments().insert(comment);
        services.activity().record(author.getId(), "commented", "comment", comment.getId(),
                issue.getProjectId(), Json.stringify(Map.of("issueKey", issue.getKey())));

        Map<String, User> users = services.userIndex();
        Map<String, Object> view = Views.comment(comment, users::get);
        services.dispatcher().dispatch(issue.getProjectId(), "comment.created",
                Json.stringify(view));
        services.mail().notifyCommented(issue, comment, users, author);

        Json.write(response, 201, Map.of("comment", view));
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String[] parts = segments(request);
        if (parts.length != 2) {
            throw ApiException.notFound("Unknown endpoint");
        }
        Comment comment = services.comments().find(parts[1])
                .orElseThrow(() -> ApiException.notFound("Comment not found"));
        User caller = AuthFilter.currentUser(request);
        if (!comment.getAuthorId().equals(caller.getId()) && caller.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("You can only delete your own comments");
        }
        services.comments().delete(comment.getId());
        response.setStatus(204);
    }
}
