package com.cascade.core.config;

import com.cascade.core.model.IssueStatus;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * YAML configuration, layered over built-in defaults and then overridden by
 * environment variables. A missing or unreadable file is not fatal.
 */
public final class AppConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AppConfig.class);

    private int port = 4000;
    private String jwtSecret = "change-me-in-production";
    private int tokenTtlHours = 12;
    private String uploadDir = "uploads";
    private long maxUploadBytes = 8L * 1024 * 1024;
    private String jdbcUrl = "jdbc:h2:./data/cascade;AUTO_SERVER=TRUE";
    private String jdbcUser = "sa";
    private String jdbcPassword = "";
    private List<String> webhookAllowlist = new ArrayList<>(List.of("localhost"));
    private String smtpHost = "localhost";
    private int smtpPort = 1025;
    private String smtpFrom = "cascade@example.com";
    private Map<String, Boolean> features = new LinkedHashMap<>(Map.of(
            "webhooks", true, "reports", true, "digests", true, "csvImport", true));
    private List<IssueStatus> boardColumns = new ArrayList<>(List.of(IssueStatus.values()));
    private Map<String, Integer> wipLimits = new LinkedHashMap<>(Map.of(
            "in_progress", 5, "in_review", 3));

    public static AppConfig load(Path file) {
        AppConfig config = new AppConfig();
        if (file != null && Files.isReadable(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                Object parsed = new Yaml().load(in);
                if (parsed instanceof Map) {
                    config.mergeYaml(asMap(parsed));
                }
                LOG.info("loaded configuration from {}", file);
            } catch (Exception e) {
                LOG.warn("could not read {}: {}", file, e.getMessage());
            }
        } else {
            LOG.warn("no config file at {}, using defaults", file);
        }
        config.applyEnvironment();
        return config;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private void mergeYaml(Map<String, Object> doc) {
        if (doc.get("port") instanceof Number n) {
            port = n.intValue();
        }
        if (doc.get("tokenTtlHours") instanceof Number n) {
            tokenTtlHours = n.intValue();
        }
        if (doc.get("uploadDir") instanceof String s) {
            uploadDir = s;
        }
        if (doc.get("maxUploadBytes") instanceof Number n) {
            maxUploadBytes = n.longValue();
        }
        if (doc.get("webhookAllowlist") instanceof List<?> list) {
            webhookAllowlist = new ArrayList<>();
            list.forEach(entry -> webhookAllowlist.add(String.valueOf(entry)));
        }
        Map<String, Object> datasource = asMap(doc.get("datasource"));
        if (datasource.get("url") instanceof String s) {
            jdbcUrl = s;
        }
        if (datasource.get("user") instanceof String s) {
            jdbcUser = s;
        }
        if (datasource.get("password") instanceof String s) {
            jdbcPassword = s;
        }
        Map<String, Object> smtp = asMap(doc.get("smtp"));
        if (smtp.get("host") instanceof String s) {
            smtpHost = s;
        }
        if (smtp.get("port") instanceof Number n) {
            smtpPort = n.intValue();
        }
        if (smtp.get("from") instanceof String s) {
            smtpFrom = s;
        }
        asMap(doc.get("features")).forEach((key, value) -> {
            if (value instanceof Boolean b) {
                features.put(key, b);
            }
        });
        Map<String, Object> board = asMap(doc.get("board"));
        if (board.get("columns") instanceof List<?> list) {
            List<IssueStatus> parsed = new ArrayList<>();
            for (Object entry : list) {
                IssueStatus status = IssueStatus.parse(String.valueOf(entry));
                if (status != null) {
                    parsed.add(status);
                }
            }
            if (!parsed.isEmpty()) {
                boardColumns = parsed;
            }
        }
        Map<String, Object> limits = asMap(board.get("wipLimits"));
        if (!limits.isEmpty()) {
            wipLimits = new LinkedHashMap<>();
            limits.forEach((key, value) -> {
                if (value instanceof Number n) {
                    wipLimits.put(key, n.intValue());
                }
            });
        }
    }

    private void applyEnvironment() {
        env("PORT", value -> port = Integer.parseInt(value));
        env("JWT_SECRET", value -> jwtSecret = value);
        env("UPLOAD_DIR", value -> uploadDir = value);
        env("JDBC_URL", value -> jdbcUrl = value);
        env("JDBC_USER", value -> jdbcUser = value);
        env("JDBC_PASSWORD", value -> jdbcPassword = value);
        env("SMTP_HOST", value -> smtpHost = value);
        env("SMTP_PORT", value -> smtpPort = Integer.parseInt(value));
        env("WEBHOOK_ALLOWLIST", value -> {
            List<String> entries = new ArrayList<>();
            for (String part : value.split(",")) {
                if (!part.isBlank()) {
                    entries.add(part.trim());
                }
            }
            if (!entries.isEmpty()) {
                webhookAllowlist = entries;
            }
        });
    }

    private void env(String name, java.util.function.Consumer<String> setter) {
        String value = System.getenv(name);
        if (value != null && !value.isBlank()) {
            try {
                setter.accept(value.trim());
            } catch (RuntimeException e) {
                LOG.warn("ignoring invalid {}: {}", name, e.getMessage());
            }
        }
    }

    public boolean feature(String name) {
        return features.getOrDefault(name, false);
    }

    public Integer wipLimit(IssueStatus status) {
        return wipLimits.get(status.wire());
    }

    public int getPort() { return port; }
    public String getJwtSecret() { return jwtSecret; }
    public int getTokenTtlHours() { return tokenTtlHours; }
    public String getUploadDir() { return uploadDir; }
    public long getMaxUploadBytes() { return maxUploadBytes; }
    public String getJdbcUrl() { return jdbcUrl; }
    public String getJdbcUser() { return jdbcUser; }
    public String getJdbcPassword() { return jdbcPassword; }
    public List<String> getWebhookAllowlist() { return webhookAllowlist; }
    public String getSmtpHost() { return smtpHost; }
    public int getSmtpPort() { return smtpPort; }
    public String getSmtpFrom() { return smtpFrom; }
    public Map<String, Boolean> getFeatures() { return features; }
    public List<IssueStatus> getBoardColumns() { return boardColumns; }
}
