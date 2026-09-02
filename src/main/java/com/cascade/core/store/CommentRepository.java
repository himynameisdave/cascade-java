package com.cascade.core.store;

import com.cascade.core.model.Comment;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CommentRepository {

    private final Database database;

    public CommentRepository(Database database) {
        this.database = database;
    }

    private static Comment map(ResultSet rs) throws SQLException {
        Comment comment = new Comment();
        comment.setId(rs.getString("id"));
        comment.setIssueId(rs.getString("issue_id"));
        comment.setAuthorId(rs.getString("author_id"));
        comment.setBody(rs.getString("body"));
        comment.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        Timestamp edited = rs.getTimestamp("edited_at");
        comment.setEditedAt(edited == null ? null : edited.toInstant());
        return comment;
    }

    public List<Comment> findByIssue(String issueId) {
        String sql = "SELECT * FROM comments WHERE issue_id = ? ORDER BY created_at";
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, issueId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Comment> comments = new ArrayList<>();
                while (rs.next()) {
                    comments.add(map(rs));
                }
                return comments;
            }
        } catch (SQLException e) {
            throw new StoreException("could not load comments for " + issueId, e);
        }
    }

    public Optional<Comment> find(String id) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM comments WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("could not load comment " + id, e);
        }
    }

    public int countByIssue(String issueId) {
        try (Connection c = database.connection();
             PreparedStatement ps =
                     c.prepareStatement("SELECT COUNT(*) FROM comments WHERE issue_id = ?")) {
            ps.setString(1, issueId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new StoreException("could not count comments", e);
        }
    }

    public void insert(Comment comment) {
        String sql = "INSERT INTO comments (id, issue_id, author_id, body, created_at, edited_at) "
                + "VALUES (?,?,?,?,?,?)";
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, comment.getId());
            ps.setString(2, comment.getIssueId());
            ps.setString(3, comment.getAuthorId());
            ps.setString(4, comment.getBody());
            ps.setTimestamp(5, Timestamp.from(comment.getCreatedAt()));
            ps.setTimestamp(6, comment.getEditedAt() == null ? null : Timestamp.from(comment.getEditedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("could not insert comment", e);
        }
    }

    public void update(Comment comment) {
        try (Connection c = database.connection();
             PreparedStatement ps =
                     c.prepareStatement("UPDATE comments SET body = ?, edited_at = ? WHERE id = ?")) {
            ps.setString(1, comment.getBody());
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, comment.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("could not update comment " + comment.getId(), e);
        }
    }

    public void delete(String id) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM comments WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("could not delete comment " + id, e);
        }
    }
}
