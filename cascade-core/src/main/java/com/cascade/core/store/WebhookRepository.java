package com.cascade.core.store;

import com.cascade.core.model.Webhook;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class WebhookRepository {

    private final Database database;

    public WebhookRepository(Database database) {
        this.database = database;
    }

    private static Webhook map(ResultSet rs) throws SQLException {
        Webhook hook = new Webhook();
        hook.setId(rs.getString("id"));
        hook.setProjectId(rs.getString("project_id"));
        hook.setUrl(rs.getString("url"));
        hook.setEvents(Arrays.stream(rs.getString("events").split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new)));
        hook.setSecret(rs.getString("secret"));
        hook.setActive(rs.getBoolean("active"));
        hook.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        int status = rs.getInt("last_status");
        hook.setLastStatus(rs.wasNull() ? null : status);
        return hook;
    }

    public List<Webhook> findByProject(String projectId) {
        String sql = projectId == null
                ? "SELECT * FROM webhooks"
                : "SELECT * FROM webhooks WHERE project_id = ?";
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (projectId != null) {
                ps.setString(1, projectId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Webhook> hooks = new ArrayList<>();
                while (rs.next()) {
                    hooks.add(map(rs));
                }
                return hooks;
            }
        } catch (SQLException e) {
            throw new StoreException("could not load webhooks", e);
        }
    }

    /** Active hooks for a project that subscribe to {@code event} or to "*". */
    public List<Webhook> findDeliverable(String projectId, String event) {
        return findByProject(projectId).stream()
                .filter(Webhook::isActive)
                .filter(h -> h.getEvents().contains("*") || h.getEvents().contains(event))
                .toList();
    }

    public Optional<Webhook> find(String id) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM webhooks WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("could not load webhook " + id, e);
        }
    }

    public void insert(Webhook hook) {
        String sql = "INSERT INTO webhooks (id, project_id, url, events, secret, active, "
                + "created_at, last_status) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, hook.getId());
            ps.setString(2, hook.getProjectId());
            ps.setString(3, hook.getUrl());
            ps.setString(4, String.join(",", hook.getEvents()));
            ps.setString(5, hook.getSecret());
            ps.setBoolean(6, hook.isActive());
            ps.setTimestamp(7, Timestamp.from(hook.getCreatedAt()));
            ps.setNull(8, java.sql.Types.INTEGER);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("could not insert webhook", e);
        }
    }

    public void recordStatus(String id, Integer status) {
        try (Connection c = database.connection();
             PreparedStatement ps =
                     c.prepareStatement("UPDATE webhooks SET last_status = ? WHERE id = ?")) {
            if (status == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, status);
            }
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("could not record webhook status", e);
        }
    }

    public void delete(String id) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM webhooks WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("could not delete webhook " + id, e);
        }
    }
}
