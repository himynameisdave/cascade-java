package com.cascade.api.notify;

import com.cascade.core.config.AppConfig;
import com.cascade.core.markdown.MarkdownRenderer;
import com.cascade.core.model.Comment;
import com.cascade.core.model.Issue;
import com.cascade.core.model.User;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Assignment and mention notifications.
 *
 * <p>A dead SMTP host must never fail the request that triggered the mail, so
 * every send failure is logged and swallowed.
 */
public class MailService {

    private static final Logger LOG = LoggerFactory.getLogger(MailService.class);

    private final AppConfig config;
    private final Session session;

    public MailService(AppConfig config) {
        this.config = config;
        Properties props = new Properties();
        props.put("mail.smtp.host", config.getSmtpHost());
        props.put("mail.smtp.port", String.valueOf(config.getSmtpPort()));
        props.put("mail.smtp.connectiontimeout", "3000");
        props.put("mail.smtp.timeout", "5000");
        this.session = Session.getInstance(props);
    }

    private void send(User recipient, String subject, String html) {
        if (recipient == null || recipient.getEmail() == null) {
            return;
        }
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(config.getSmtpFrom()));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipient.getEmail()));
            message.setSubject(subject);
            message.setContent(html, "text/html; charset=utf-8");
            Transport.send(message);
        } catch (Exception e) {
            LOG.warn("could not email {}: {}", recipient.getEmail(), e.getMessage());
        }
    }

    public void notifyAssigned(Issue issue, User assignee, User actor) {
        if (assignee == null || assignee.getId().equals(actor.getId())) {
            return;
        }
        String html = "<p>Hi " + assignee.getName() + ",</p>"
                + "<p><strong>" + actor.getName() + "</strong> assigned "
                + "<a href=\"/issues/" + issue.getKey() + "\">" + issue.getKey() + "</a> to you.</p>"
                + "<h3>" + issue.getTitle() + "</h3>"
                + "<p>" + MarkdownRenderer.excerpt(issue.getDescription(), 180) + "</p>"
                + "<p>Priority: " + issue.getPriority().wire() + "</p>";
        send(assignee, "[" + issue.getKey() + "] " + issue.getTitle(), html);
    }

    /** Watchers are the assignee, the reporter, and anyone {@code @mentioned}. */
    public void notifyCommented(Issue issue, Comment comment, Map<String, User> users, User actor) {
        Set<String> recipients = new LinkedHashSet<>();
        if (issue.getAssigneeId() != null) {
            recipients.add(issue.getAssigneeId());
        }
        if (issue.getReporterId() != null) {
            recipients.add(issue.getReporterId());
        }

        List<String> handles = MarkdownRenderer.extractMentions(comment.getBody());
        if (!handles.isEmpty()) {
            for (User candidate : users.values()) {
                if (handles.contains(candidate.handle())) {
                    recipients.add(candidate.getId());
                }
            }
        }
        recipients.remove(actor.getId());

        String html = "<p><strong>" + actor.getName() + "</strong> commented on "
                + "<a href=\"/issues/" + issue.getKey() + "\">" + issue.getKey() + "</a>.</p>"
                + "<blockquote>" + MarkdownRenderer.excerpt(comment.getBody(), 240) + "</blockquote>";

        List<User> targets = new ArrayList<>();
        recipients.forEach(id -> targets.add(users.get(id)));
        targets.forEach(user -> send(user, "[" + issue.getKey() + "] " + issue.getTitle(), html));
    }
}
