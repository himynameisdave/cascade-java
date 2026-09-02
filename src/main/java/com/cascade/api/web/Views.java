package com.cascade.api.web;

import com.cascade.core.markdown.MarkdownRenderer;
import com.cascade.core.model.Comment;
import com.cascade.core.model.Issue;
import com.cascade.core.model.Project;
import com.cascade.core.model.User;
import com.cascade.core.model.Webhook;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Response projections.
 *
 * <p>Every handler serializes through here rather than returning entities
 * directly, so a field added to a model can never leak into a response by
 * accident.
 */
public final class Views {

    private Views() {
    }

    public static Map<String, Object> user(User user) {
        if (user == null) {
            return null;
        }
        Map<String, Object> view = Json.map();
        view.put("id", user.getId());
        view.put("email", user.getEmail());
        view.put("name", user.getName());
        view.put("role", user.getRole().name().toLowerCase(java.util.Locale.ROOT));
        view.put("avatarColor", user.getAvatarColor());
        view.put("createdAt", user.getCreatedAt());
        return view;
    }

    public static Map<String, Object> issue(Issue issue,
                                            Function<String, User> users,
                                            int commentCount) {
        Map<String, Object> view = Json.map();
        view.put("id", issue.getId());
        view.put("key", issue.getKey());
        view.put("projectId", issue.getProjectId());
        view.put("seq", issue.getSeq());
        view.put("title", issue.getTitle());
        view.put("description", issue.getDescription());
        view.put("descriptionHtml", MarkdownRenderer.render(issue.getDescription()));
        view.put("status", issue.getStatus().wire());
        view.put("priority", issue.getPriority().wire());
        view.put("type", issue.getType().wire());
        view.put("reporterId", issue.getReporterId());
        view.put("assigneeId", issue.getAssigneeId());
        // An unresolved id stays null: an empty object would read as a real user.
        view.put("assignee", issue.getAssigneeId() == null
                ? null : user(users.apply(issue.getAssigneeId())));
        view.put("reporter", issue.getReporterId() == null
                ? null : user(users.apply(issue.getReporterId())));
        view.put("labels", issue.getLabels());
        view.put("storyPoints", issue.getStoryPoints());
        view.put("dueDate", issue.getDueDate());
        view.put("parentId", issue.getParentId());
        view.put("boardRank", issue.getBoardRank());
        view.put("attachments", issue.getAttachments());
        view.put("commentCount", commentCount);
        view.put("isOverdue", issue.isOverdue());
        view.put("createdAt", issue.getCreatedAt());
        view.put("updatedAt", issue.getUpdatedAt());
        return view;
    }

    /** Lighter shape for search hits: a text excerpt instead of rendered HTML. */
    public static Map<String, Object> issueHit(Issue issue, Function<String, User> users) {
        Map<String, Object> view = Json.map();
        view.put("id", issue.getId());
        view.put("key", issue.getKey());
        view.put("projectId", issue.getProjectId());
        view.put("title", issue.getTitle());
        view.put("excerpt", MarkdownRenderer.excerpt(issue.getDescription(), 140));
        view.put("status", issue.getStatus().wire());
        view.put("priority", issue.getPriority().wire());
        view.put("type", issue.getType().wire());
        view.put("assignee", issue.getAssigneeId() == null
                ? null : user(users.apply(issue.getAssigneeId())));
        view.put("labels", issue.getLabels());
        view.put("storyPoints", issue.getStoryPoints());
        view.put("isOverdue", issue.isOverdue());
        view.put("updatedAt", issue.getUpdatedAt());
        return view;
    }

    public static Map<String, Object> comment(Comment comment, Function<String, User> users) {
        Map<String, Object> view = Json.map();
        view.put("id", comment.getId());
        view.put("issueId", comment.getIssueId());
        view.put("authorId", comment.getAuthorId());
        view.put("author", user(users.apply(comment.getAuthorId())));
        view.put("body", comment.getBody());
        view.put("bodyHtml", MarkdownRenderer.render(comment.getBody()));
        view.put("createdAt", comment.getCreatedAt());
        view.put("editedAt", comment.getEditedAt());
        return view;
    }

    public static Map<String, Object> project(Project project, List<Issue> issues) {
        Map<String, Object> view = Json.map();
        view.put("id", project.getId());
        view.put("key", project.getKey());
        view.put("name", project.getName());
        view.put("description", project.getDescription());
        view.put("leadId", project.getLeadId());
        view.put("memberIds", project.getMemberIds());
        view.put("archived", project.isArchived());
        view.put("createdAt", project.getCreatedAt());
        view.put("issueCount", issues.size());
        view.put("openCount", issues.stream()
                .filter(i -> i.getStatus() != com.cascade.core.model.IssueStatus.DONE).count());
        view.put("lastActivityAt", issues.stream()
                .map(Issue::getUpdatedAt)
                .max(java.time.Instant::compareTo)
                .orElse(project.getCreatedAt()));
        return view;
    }

    /**
     * Webhooks are returned without their signing secret. It is shown exactly
     * once, in the creation response, so it can be copied into the receiver.
     */
    public static Map<String, Object> webhook(Webhook hook) {
        Map<String, Object> view = Json.map();
        view.put("id", hook.getId());
        view.put("projectId", hook.getProjectId());
        view.put("url", hook.getUrl());
        view.put("events", hook.getEvents());
        view.put("active", hook.isActive());
        view.put("createdAt", hook.getCreatedAt());
        view.put("lastStatus", hook.getLastStatus());
        return view;
    }
}
