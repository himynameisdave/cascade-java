package com.cascade.core.store;

import com.cascade.core.model.Issue;
import com.cascade.core.model.IssuePriority;
import com.cascade.core.model.IssueStatus;
import com.cascade.core.model.IssueType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class IssueRepository {

    private static final String COLUMNS =
            "id, issue_key, project_id, seq, title, description, status, priority, type, "
          + "reporter_id, assignee_id, labels, story_points, due_date, parent_id, board_rank, "
          + "created_at, updated_at";

    private final Database database;

    public IssueRepository(Database database) {
        this.database = database;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    /** Labels are stored as a pipe-delimited string; H2 has no portable array type. */
    private static String joinLabels(List<String> labels) {
        return labels == null ? "" : String.join("|", labels);
    }

    private static List<String> splitLabels(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(raw.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static Issue map(ResultSet rs) throws SQLException {
        Issue issue = new Issue();
        issue.setId(rs.getString("id"));
        issue.setKey(rs.getString("issue_key"));
        issue.setProjectId(rs.getString("project_id"));
        issue.setSeq(rs.getLong("seq"));
        issue.setTitle(rs.getString("title"));
        issue.setDescription(rs.getString("description"));
        issue.setStatus(IssueStatus.parse(rs.getString("status")));
        issue.setPriority(IssuePriority.parse(rs.getString("priority")));
        issue.setType(IssueType.parse(rs.getString("type")));
        issue.setReporterId(rs.getString("reporter_id"));
        issue.setAssigneeId(rs.getString("assignee_id"));
        issue.setLabels(splitLabels(rs.getString("labels")));
        int points = rs.getInt("story_points");
        issue.setStoryPoints(rs.wasNull() ? null : points);
        issue.setDueDate(instant(rs.getTimestamp("due_date")));
        issue.setParentId(rs.getString("parent_id"));
        issue.setBoardRank(rs.getDouble("board_rank"));
        issue.setCreatedAt(instant(rs.getTimestamp("created_at")));
        issue.setUpdatedAt(instant(rs.getTimestamp("updated_at")));
        return issue;
    }

    public List<Issue> findAll() {
        String sql = "SELECT " + COLUMNS + " FROM issues";
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Issue> issues = new ArrayList<>();
            while (rs.next()) {
                issues.add(map(rs));
            }
            return issues;
        } catch (SQLException e) {
            throw new StoreException("could not load issues", e);
        }
    }

    public List<Issue> findByProject(String projectId) {
        String sql = "SELECT " + COLUMNS + " FROM issues WHERE project_id = ? ORDER BY board_rank";
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, projectId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Issue> issues = new ArrayList<>();
                while (rs.next()) {
                    issues.add(map(rs));
                }
                return issues;
            }
        } catch (SQLException e) {
            throw new StoreException("could not load issues for project " + projectId, e);
        }
    }

    /** Accepts either the opaque id or the human key such as {@code PAY-3}. */
    public Optional<Issue> find(String idOrKey) {
        String sql = "SELECT " + COLUMNS + " FROM issues WHERE id = ? OR UPPER(issue_key) = ?";
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, idOrKey);
            ps.setString(2, idOrKey.toUpperCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("could not load issue " + idOrKey, e);
        }
    }

    public void insert(Issue issue) {
        String sql = "INSERT INTO issues (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, issue);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("could not insert issue " + issue.getKey(), e);
        }
    }

    private void bind(PreparedStatement ps, Issue issue) throws SQLException {
        ps.setString(1, issue.getId());
        ps.setString(2, issue.getKey());
        ps.setString(3, issue.getProjectId());
        ps.setLong(4, issue.getSeq());
        ps.setString(5, issue.getTitle());
        ps.setString(6, issue.getDescription());
        ps.setString(7, issue.getStatus().wire());
        ps.setString(8, issue.getPriority().wire());
        ps.setString(9, issue.getType().wire());
        ps.setString(10, issue.getReporterId());
        ps.setString(11, issue.getAssigneeId());
        ps.setString(12, joinLabels(issue.getLabels()));
        if (issue.getStoryPoints() == null) {
            ps.setNull(13, java.sql.Types.INTEGER);
        } else {
            ps.setInt(13, issue.getStoryPoints());
        }
        ps.setTimestamp(14, timestamp(issue.getDueDate()));
        ps.setString(15, issue.getParentId());
        ps.setDouble(16, issue.getBoardRank());
        ps.setTimestamp(17, timestamp(issue.getCreatedAt()));
        ps.setTimestamp(18, timestamp(issue.getUpdatedAt()));
    }

    public void update(Issue issue) {
        String sql = "UPDATE issues SET title = ?, description = ?, status = ?, priority = ?, "
                + "type = ?, assignee_id = ?, labels = ?, story_points = ?, due_date = ?, "
                + "parent_id = ?, board_rank = ?, updated_at = ? WHERE id = ?";
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, issue.getTitle());
            ps.setString(2, issue.getDescription());
            ps.setString(3, issue.getStatus().wire());
            ps.setString(4, issue.getPriority().wire());
            ps.setString(5, issue.getType().wire());
            ps.setString(6, issue.getAssigneeId());
            ps.setString(7, joinLabels(issue.getLabels()));
            if (issue.getStoryPoints() == null) {
                ps.setNull(8, java.sql.Types.INTEGER);
            } else {
                ps.setInt(8, issue.getStoryPoints());
            }
            ps.setTimestamp(9, timestamp(issue.getDueDate()));
            ps.setString(10, issue.getParentId());
            ps.setDouble(11, issue.getBoardRank());
            ps.setTimestamp(12, timestamp(Instant.now()));
            ps.setString(13, issue.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("could not update issue " + issue.getKey(), e);
        }
    }

    public void delete(String id) {
        try (Connection c = database.connection();
             PreparedStatement issues = c.prepareStatement("DELETE FROM issues WHERE id = ?");
             PreparedStatement comments = c.prepareStatement("DELETE FROM comments WHERE issue_id = ?")) {
            comments.setString(1, id);
            comments.executeUpdate();
            issues.setString(1, id);
            issues.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("could not delete issue " + id, e);
        }
    }

    /**
     * Allocates the next per-project issue number. The read and the write share
     * one transaction so two concurrent creates cannot claim the same key.
     */
    public long nextSequence(String projectKey) {
        String name = "issue:" + projectKey;
        try (Connection c = database.connection()) {
            c.setAutoCommit(false);
            try {
                long next = 1;
                try (PreparedStatement select =
                             c.prepareStatement("SELECT value FROM counters WHERE name = ? FOR UPDATE")) {
                    select.setString(1, name);
                    try (ResultSet rs = select.executeQuery()) {
                        if (rs.next()) {
                            next = rs.getLong(1) + 1;
                        }
                    }
                }
                try (PreparedStatement upsert = c.prepareStatement(
                        "MERGE INTO counters (name, value) KEY (name) VALUES (?, ?)")) {
                    upsert.setString(1, name);
                    upsert.setLong(2, next);
                    upsert.executeUpdate();
                }
                c.commit();
                return next;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new StoreException("could not allocate an issue number for " + projectKey, e);
        }
    }
}
