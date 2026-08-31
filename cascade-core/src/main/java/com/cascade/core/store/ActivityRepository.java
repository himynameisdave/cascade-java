package com.cascade.core.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Append-only feed of who changed what. */
public class ActivityRepository {

    private final Database database;

    public ActivityRepository(Database database) {
        this.database = database;
    }

    public void record(String actorId, String verb, String subjectType, String subjectId,
                       String projectId, String metaJson) {
        String sql = "INSERT INTO activity (id, actor_id, verb, subject_type, subject_id, "
                + "project_id, meta, created_at) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, actorId);
            ps.setString(3, verb);
            ps.setString(4, subjectType);
            ps.setString(5, subjectId);
            ps.setString(6, projectId);
            ps.setString(7, metaJson == null ? "{}" : metaJson);
            ps.setTimestamp(8, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("could not record activity", e);
        }
    }

    public List<Map<String, Object>> feed(String projectId, String actorId, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM activity WHERE 1 = 1");
        List<String> params = new ArrayList<>();
        if (projectId != null) {
            sql.append(" AND project_id = ?");
            params.add(projectId);
        }
        if (actorId != null) {
            sql.append(" AND actor_id = ?");
            params.add(actorId);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");

        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int index = 1;
            for (String param : params) {
                ps.setString(index++, param);
            }
            ps.setInt(index, Math.max(1, Math.min(limit, 200)));
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> entries = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("actorId", rs.getString("actor_id"));
                    row.put("verb", rs.getString("verb"));
                    row.put("subjectType", rs.getString("subject_type"));
                    row.put("subjectId", rs.getString("subject_id"));
                    row.put("projectId", rs.getString("project_id"));
                    row.put("meta", rs.getString("meta"));
                    row.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                    entries.add(row);
                }
                return entries;
            }
        } catch (SQLException e) {
            throw new StoreException("could not read the activity feed", e);
        }
    }
}
