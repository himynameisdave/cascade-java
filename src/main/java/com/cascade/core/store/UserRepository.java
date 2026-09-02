package com.cascade.core.store;

import com.cascade.core.model.Role;
import com.cascade.core.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class UserRepository {

    private static final String COLUMNS =
            "id, email, name, role, password_hash, avatar_color, created_at";

    private final Database database;

    public UserRepository(Database database) {
        this.database = database;
    }

    private static User map(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getString("id"));
        user.setEmail(rs.getString("email"));
        user.setName(rs.getString("name"));
        user.setRole(Role.parse(rs.getString("role")));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setAvatarColor(rs.getString("avatar_color"));
        Timestamp created = rs.getTimestamp("created_at");
        user.setCreatedAt(created == null ? null : created.toInstant());
        return user;
    }

    public List<User> findAll() {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM users ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            List<User> users = new ArrayList<>();
            while (rs.next()) {
                users.add(map(rs));
            }
            return users;
        } catch (SQLException e) {
            throw new StoreException("could not load users", e);
        }
    }

    public Optional<User> findById(String id) {
        return findBy("id = ?", id);
    }

    public Optional<User> findByEmail(String email) {
        return findBy("email = ?", email == null ? null : email.trim().toLowerCase(Locale.ROOT));
    }

    private Optional<User> findBy(String where, String value) {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM users WHERE " + where)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("could not load user", e);
        }
    }

    public long count() {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new StoreException("could not count users", e);
        }
    }

    public void insert(User user) {
        String sql = "INSERT INTO users (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?)";
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, user.getId());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getName());
            ps.setString(4, user.getRole().name());
            ps.setString(5, user.getPasswordHash());
            ps.setString(6, user.getAvatarColor());
            ps.setTimestamp(7, Timestamp.from(user.getCreatedAt()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("could not insert user " + user.getEmail(), e);
        }
    }
}
