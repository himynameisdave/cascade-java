package com.cascade.api.notify;

import java.io.StringWriter;
import java.util.Properties;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.owasp.esapi.ESAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders notification email bodies.
 *
 * <p>Templates are Velocity; every interpolated value is HTML-encoded through
 * ESAPI before it reaches the template, because issue titles and comment
 * bodies are attacker-controlled and land in a mail client that will render
 * markup.
 */
public final class EmailTemplates {

    private static final Logger LOG = LoggerFactory.getLogger(EmailTemplates.class);

    private static final String ASSIGNED = """
        <p>Hi $recipient,</p>
        <p><strong>$actor</strong> assigned <a href="$baseUrl/issues/$key">$key</a> to you.</p>
        <h3>$title</h3>
        <p>$excerpt</p>
        <p>Priority: $priority</p>
        """;

    private static final String MENTIONED = """
        <p>Hi $recipient,</p>
        <p><strong>$actor</strong> mentioned you on <a href="$baseUrl/issues/$key">$key</a>.</p>
        <blockquote>$excerpt</blockquote>
        """;

    private static final VelocityEngine ENGINE = create();

    private EmailTemplates() {
    }

    private static VelocityEngine create() {
        Properties properties = new Properties();
        properties.setProperty("resource.loader", "string");
        properties.setProperty("runtime.log.logsystem.class",
                "org.apache.velocity.runtime.log.NullLogChute");
        VelocityEngine engine = new VelocityEngine();
        engine.init(properties);
        return engine;
    }

    /** HTML-encodes a value before it is interpolated into a template. */
    private static String safe(String value) {
        return value == null ? "" : ESAPI.encoder().encodeForHTML(value);
    }

    public static String assigned(String recipient, String actor, String key, String title,
                                  String excerpt, String priority, String baseUrl) {
        VelocityContext context = new VelocityContext();
        context.put("recipient", safe(recipient));
        context.put("actor", safe(actor));
        context.put("key", safe(key));
        context.put("title", safe(title));
        context.put("excerpt", safe(excerpt));
        context.put("priority", safe(priority));
        context.put("baseUrl", baseUrl);
        return render(ASSIGNED, context);
    }

    public static String mentioned(String recipient, String actor, String key, String excerpt,
                                   String baseUrl) {
        VelocityContext context = new VelocityContext();
        context.put("recipient", safe(recipient));
        context.put("actor", safe(actor));
        context.put("key", safe(key));
        context.put("excerpt", safe(excerpt));
        context.put("baseUrl", baseUrl);
        return render(MENTIONED, context);
    }

    private static String render(String template, VelocityContext context) {
        StringWriter out = new StringWriter();
        try {
            ENGINE.evaluate(context, out, "cascade-email", template);
            return out.toString();
        } catch (RuntimeException e) {
            LOG.warn("could not render an email template: {}", e.getMessage());
            return "";
        }
    }
}
