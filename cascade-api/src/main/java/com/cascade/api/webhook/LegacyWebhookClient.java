package com.cascade.api.webhook;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synchronous delivery path for webhook endpoints registered before 0.7.
 *
 * <p>Those receivers were tested against this client's exact header ordering
 * and connection behaviour, and several broke when the async client replaced
 * it. They stay on this path until each one is re-verified;
 * {@link WebhookDispatcher} is the route for everything registered since.
 */
public class LegacyWebhookClient {

    private static final Logger LOG = LoggerFactory.getLogger(LegacyWebhookClient.class);

    private final HttpClient client;
    private final SsrfGuard guard;

    public LegacyWebhookClient(SsrfGuard guard) {
        this.guard = guard;
        this.client = new HttpClient();
        this.client.getHttpConnectionManager().getParams().setConnectionTimeout(3_000);
        this.client.getHttpConnectionManager().getParams().setSoTimeout(5_000);
    }

    /** Returns the response status, or {@code null} if delivery failed. */
    public Integer deliver(String url, String event, String signature, String payloadJson) {
        // The same guard as the modern path: an old receiver is not a reason to
        // skip the SSRF check.
        if (!guard.isDeliverable(url)) {
            LOG.warn("skipping legacy delivery to {}: not an allowed target", url);
            return null;
        }

        PostMethod post = new PostMethod(url);
        try {
            post.setRequestHeader("content-type", "application/json");
            post.setRequestHeader("x-cascade-event", event);
            post.setRequestHeader("x-cascade-signature", signature);
            post.setRequestEntity(new StringRequestEntity(
                    payloadJson, "application/json", StandardCharsets.UTF_8.name()));
            return client.executeMethod(post);
        } catch (HttpException e) {
            LOG.warn("legacy delivery to {} failed: {}", url, e.getMessage());
            return null;
        } catch (IOException e) {
            LOG.warn("legacy delivery to {} could not connect: {}", url, e.getMessage());
            return null;
        } finally {
            post.releaseConnection();
        }
    }
}
