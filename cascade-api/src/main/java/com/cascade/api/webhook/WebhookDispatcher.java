package com.cascade.api.webhook;

import com.cascade.core.model.Webhook;
import com.cascade.core.store.WebhookRepository;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Dsl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Delivers project events to subscribed endpoints.
 *
 * <p>Delivery is fire-and-forget: a slow or dead receiver must never delay the
 * request that produced the event.
 */
public class WebhookDispatcher implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final WebhookRepository webhooks;
    private final SsrfGuard guard;
    private final AsyncHttpClient http;

    public WebhookDispatcher(WebhookRepository webhooks, SsrfGuard guard) {
        this.webhooks = webhooks;
        this.guard = guard;
        this.http = Dsl.asyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
                .setConnectTimeout(3_000)
                .setRequestTimeout(5_000)
                .setMaxRedirects(3)
                .setFollowRedirect(true)
                .setUserAgent("Cascade-Webhook/0.9")
                .build());
    }

    /** Hex-encoded HMAC-SHA256 over the exact bytes we send. */
    static String sign(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }

    public CompletableFuture<Integer> deliver(Webhook hook, String event, String payloadJson) {
        if (!guard.isDeliverable(hook.getUrl())) {
            LOG.warn("skipping {}: not an allowed webhook target", hook.getUrl());
            return CompletableFuture.completedFuture(null);
        }

        byte[] body = payloadJson.getBytes(StandardCharsets.UTF_8);
        return http.preparePost(hook.getUrl())
                .addHeader("content-type", "application/json")
                .addHeader("x-cascade-event", event)
                .addHeader("x-cascade-signature", "sha256=" + sign(hook.getSecret(), body))
                .setBody(body)
                .execute()
                .toCompletableFuture()
                .handle((response, error) -> {
                    if (error != null) {
                        LOG.warn("delivery to {} failed: {}", hook.getUrl(), error.getMessage());
                        webhooks.recordStatus(hook.getId(), null);
                        return null;
                    }
                    webhooks.recordStatus(hook.getId(), response.getStatusCode());
                    return response.getStatusCode();
                });
    }

    public void dispatch(String projectId, String event, String payloadJson) {
        List<Webhook> subscribed = webhooks.findDeliverable(projectId, event);
        for (Webhook hook : subscribed) {
            deliver(hook, event, payloadJson);
        }
    }

    @Override
    public void close() {
        try {
            http.close();
        } catch (Exception e) {
            LOG.warn("could not close the webhook client: {}", e.getMessage());
        }
    }
}
