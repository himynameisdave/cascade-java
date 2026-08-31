package com.cascade.api.web;

import com.cascade.api.notify.MailService;
import com.cascade.api.security.JwtService;
import com.cascade.api.security.PasswordService;
import com.cascade.api.webhook.SsrfGuard;
import com.cascade.api.webhook.WebhookDispatcher;
import com.cascade.core.config.AppConfig;
import com.cascade.core.model.User;
import com.cascade.core.store.ActivityRepository;
import com.cascade.core.store.CommentRepository;
import com.cascade.core.store.Database;
import com.cascade.core.store.IssueRepository;
import com.cascade.core.store.ProjectRepository;
import com.cascade.core.store.UserRepository;
import com.cascade.core.store.WebhookRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wiring shared by every servlet. Constructed once at start-up and stashed in
 * the servlet context.
 */
public class Services implements AutoCloseable {

    public static final String ATTRIBUTE = "cascade.services";

    private final AppConfig config;
    private final Database database;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final IssueRepository issues;
    private final CommentRepository comments;
    private final WebhookRepository webhooks;
    private final ActivityRepository activity;
    private final PasswordService passwords;
    private final JwtService jwt;
    private final WebhookDispatcher dispatcher;
    private final MailService mail;

    public Services(AppConfig config) {
        this.config = config;
        this.database = new Database(config);
        this.users = new UserRepository(database);
        this.projects = new ProjectRepository(database);
        this.issues = new IssueRepository(database);
        this.comments = new CommentRepository(database);
        this.webhooks = new WebhookRepository(database);
        this.activity = new ActivityRepository(database);
        this.passwords = new PasswordService();
        this.jwt = new JwtService(config.getJwtSecret(), config.getTokenTtlHours());
        this.dispatcher = new WebhookDispatcher(webhooks, new SsrfGuard(config.getWebhookAllowlist()));
        this.mail = new MailService(config);
    }

    /**
     * Snapshot of users by id, for the view layer. Cheap at workspace scale and
     * avoids a per-issue lookup while rendering a board.
     */
    public Map<String, User> userIndex() {
        Map<String, User> index = new HashMap<>();
        for (User user : users.findAll()) {
            index.put(user.getId(), user);
        }
        return index;
    }

    public List<String> webhookEvents() {
        return List.of("issue.created", "issue.updated", "issue.ranked", "issue.deleted",
                "comment.created", "project.created", "project.updated");
    }

    public AppConfig config() { return config; }
    public Database database() { return database; }
    public UserRepository users() { return users; }
    public ProjectRepository projects() { return projects; }
    public IssueRepository issues() { return issues; }
    public CommentRepository comments() { return comments; }
    public WebhookRepository webhooks() { return webhooks; }
    public ActivityRepository activity() { return activity; }
    public PasswordService passwords() { return passwords; }
    public JwtService jwt() { return jwt; }
    public WebhookDispatcher dispatcher() { return dispatcher; }
    public MailService mail() { return mail; }

    @Override
    public void close() {
        dispatcher.close();
        database.close();
    }
}
