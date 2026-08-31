package com.cascade.core.store;

import com.cascade.core.model.Project;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class ProjectRepository {

    private static final String COLUMNS =
            "id, project_key, name, description, lead_id, archived, created_at";

    private final Database database;

    public ProjectRepository(Database database) {
        this.database = database;
    }

    private Project map(ResultSet rs) throws SQLException {
        Project project = new Project();
        project.setId(rs.getString("id"));
        project.setKey(rs.getString("project_key"));
        project.setName(rs.getString("name"));
        project.setDescription(rs.getString("description"));
        project.setLeadId(rs.getString("lead_id"));
        project.setArchived(rs.getBoolean("archived"));
        Timestamp created = rs.getTimestamp("created_at");
        project.setCreatedAt(created == null ? null : created.toInstant());
        project.setMemberIds(loadMembers(project.getId()));
        return project;
    }

    private List<String> loadMembers(String projectId) throws SQLException {
        try (Connection c = database.connection();
             PreparedStatement ps =
                     c.prepareStatement("SELECT user_id FROM project_members WHERE project_id = ?")) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getString(1));
                }
                return ids;
            }
        }
    }

    public List<Project> findAll(boolean includeArchived) {
        String sql = "SELECT " + COLUMNS + " FROM projects"
                + (includeArchived ? "" : " WHERE archived = FALSE") + " ORDER BY project_key";
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Project> projects = new ArrayList<>();
            while (rs.next()) {
                projects.add(map(rs));
            }
            return projects;
        } catch (SQLException e) {
            throw new StoreException("could not load projects", e);
        }
    }

    /** Accepts either the opaque id or the project key such as {@code PAY}. */
    public Optional<Project> find(String idOrKey) {
        String sql = "SELECT " + COLUMNS + " FROM projects WHERE id = ? OR UPPER(project_key) = ?";
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, idOrKey);
            ps.setString(2, idOrKey.toUpperCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("could not load project " + idOrKey, e);
        }
    }

    public void insert(Project project) {
        String sql = "INSERT INTO projects (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?)";
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, project.getId());
            ps.setString(2, project.getKey());
            ps.setString(3, project.getName());
            ps.setString(4, project.getDescription());
            ps.setString(5, project.getLeadId());
            ps.setBoolean(6, project.isArchived());
            ps.setTimestamp(7, Timestamp.from(project.getCreatedAt()));
            ps.executeUpdate();
            replaceMembers(c, project);
        } catch (SQLException e) {
            throw new StoreException("could not insert project " + project.getKey(), e);
        }
    }

    private void replaceMembers(Connection c, Project project) throws SQLException {
        try (PreparedStatement delete =
                     c.prepareStatement("DELETE FROM project_members WHERE project_id = ?")) {
            delete.setString(1, project.getId());
            delete.executeUpdate();
        }
        try (PreparedStatement insert = c.prepareStatement(
                "INSERT INTO project_members (project_id, user_id) VALUES (?, ?)")) {
            for (String memberId : project.getMemberIds()) {
                insert.setString(1, project.getId());
                insert.setString(2, memberId);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    public void delete(String projectId) {
        try (Connection c = database.connection()) {
            c.setAutoCommit(false);
            try {
                exec(c, "DELETE FROM comments WHERE issue_id IN (SELECT id FROM issues WHERE project_id = ?)", projectId);
                exec(c, "DELETE FROM issues WHERE project_id = ?", projectId);
                exec(c, "DELETE FROM webhooks WHERE project_id = ?", projectId);
                exec(c, "DELETE FROM project_members WHERE project_id = ?", projectId);
                exec(c, "DELETE FROM projects WHERE id = ?", projectId);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new StoreException("could not delete project " + projectId, e);
        }
    }

    private void exec(Connection c, String sql, String param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            ps.executeUpdate();
        }
    }
}
