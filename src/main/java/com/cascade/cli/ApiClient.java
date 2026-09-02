package com.cascade.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

/** Thin blocking HTTP client for the Cascade API. */
public class ApiClient implements AutoCloseable {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Non-2xx responses surface the API's own message, not a bare status code. */
    public static class ApiClientException extends RuntimeException {
        public ApiClientException(String message) {
            super(message);
        }
    }

    private final String baseUrl;
    private final String token;
    private final CloseableHttpClient http;

    public ApiClient(String baseUrl, String token) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
        this.http = HttpClients.createDefault();
    }

    private JsonElement execute(HttpUriRequestBase request) {
        if (token != null && !token.isBlank()) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        try {
            return http.execute(request, response -> {
                String body = response.getEntity() == null
                        ? "" : EntityUtils.toString(response.getEntity());
                int status = response.getCode();
                if (status >= 400) {
                    String message = body;
                    try {
                        JsonObject parsed = JsonParser.parseString(body).getAsJsonObject();
                        if (parsed.has("error")) {
                            message = parsed.get("error").getAsString();
                        }
                    } catch (RuntimeException ignored) {
                        // keep the raw body when it is not JSON
                    }
                    throw new ApiClientException(status + ": " + message);
                }
                return body.isBlank() ? JsonParser.parseString("{}") : JsonParser.parseString(body);
            });
        } catch (IOException e) {
            throw new ApiClientException("could not reach " + baseUrl + ": " + e.getMessage());
        }
    }

    private static String queryString(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder query = new StringBuilder("?");
        params.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            if (query.length() > 1) {
                query.append('&');
            }
            query.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                 .append('=')
                 .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return query.length() == 1 ? "" : query.toString();
    }

    public JsonObject get(String path, Map<String, String> params) {
        return execute(new HttpGet(baseUrl + "/api" + path + queryString(params)))
                .getAsJsonObject();
    }

    public JsonObject post(String path, Object body) {
        HttpPost request = new HttpPost(baseUrl + "/api" + path);
        request.setEntity(new StringEntity(GSON.toJson(body), ContentType.APPLICATION_JSON));
        return execute(request).getAsJsonObject();
    }

    public JsonObject patch(String path, Object body) {
        HttpPatch request = new HttpPatch(baseUrl + "/api" + path);
        request.setEntity(new StringEntity(GSON.toJson(body), ContentType.APPLICATION_JSON));
        return execute(request).getAsJsonObject();
    }

    public void delete(String path) {
        execute(new HttpDelete(baseUrl + "/api" + path));
    }

    public String login(String email, String password) {
        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("email", email);
        credentials.put("password", password);
        JsonObject response = post("/auth/login", credentials);
        if (!response.has("token")) {
            throw new ApiClientException("the login response contained no token");
        }
        return response.get("token").getAsString();
    }

    @Override
    public void close() {
        try {
            http.close();
        } catch (IOException ignored) {
            // nothing useful to do while shutting down
        }
    }
}
