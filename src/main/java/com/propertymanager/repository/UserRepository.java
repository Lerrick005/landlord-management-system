package com.propertymanager.repository;

import com.propertymanager.database.Database;
import com.propertymanager.model.Role;
import com.propertymanager.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for the "users" table.
 * All queries use prepared statements — never string concatenation.
 */
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    /** Base SELECT with a JOIN to roles so the role object is always populated. */
    private static final String BASE_SELECT = """
        SELECT u.id, u.username, u.password_hash, u.full_name,
               u.role_id, u.active, u.created_at, u.last_login_at,
               r.id AS r_id, r.code AS r_code, r.name AS r_name, r.description AS r_desc
        FROM users u
        JOIN roles r ON r.id = u.role_id
        """;

    /** Count all users — used to detect "no admin yet" during first-run setup. */
    public long count() {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            log.error("count() failed", e);
            throw new RuntimeException("Unable to count users", e);
        }
    }

    /** Find user by username (case-insensitive). Used by login. */
    public Optional<User> findByUsername(String username) {
        String sql = BASE_SELECT + " WHERE LOWER(u.username) = LOWER(?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findByUsername({}) failed", username, e);
            throw new RuntimeException("Unable to look up user", e);
        }
    }

    /** Find user by id. */
    public Optional<User> findById(long id) {
        String sql = BASE_SELECT + " WHERE u.id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findById({}) failed", id, e);
            throw new RuntimeException("Unable to look up user", e);
        }
    }

    /** All users, ordered by full name. */
    public List<User> findAll() {
        String sql = BASE_SELECT + " ORDER BY u.full_name";
        List<User> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            log.error("findAll() failed", e);
            throw new RuntimeException("Unable to load users", e);
        }
    }

    /**
     * Insert a new user. Returns the generated id.
     * Pass passwordHash (already hashed with PasswordUtil.hash).
     */
    public long insert(User user) {
        String sql = """
            INSERT INTO users (username, password_hash, full_name, role_id, active)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getFullName());
            ps.setInt(4, user.getRoleId());
            ps.setBoolean(5, user.isActive());

            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("Insert affected 0 rows");

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    user.setId(id);
                    log.info("Inserted user id={} username={}", id, user.getUsername());
                    return id;
                }
                throw new SQLException("No generated key returned");
            }
        } catch (SQLException e) {
            log.error("insert() failed for username={}", user.getUsername(), e);
            throw new RuntimeException("Unable to save user", e);
        }
    }

    /** Update an existing user's details and role. Does NOT touch password_hash. */
    public void update(User user) {
        String sql = """
            UPDATE users
               SET username = ?, full_name = ?, role_id = ?, active = ?
             WHERE id = ?
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getFullName());
            ps.setInt(3, user.getRoleId());
            ps.setBoolean(4, user.isActive());
            ps.setLong(5, user.getId());

            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("No user with id " + user.getId());
            log.info("Updated user id={}", user.getId());
        } catch (SQLException e) {
            log.error("update() failed for id={}", user.getId(), e);
            throw new RuntimeException("Unable to update user", e);
        }
    }

    /** Update a user's password hash. */
    public void updatePassword(long userId, String newHash) {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newHash);
            ps.setLong(2, userId);
            ps.executeUpdate();
            log.info("Password updated for user id={}", userId);
        } catch (SQLException e) {
            log.error("updatePassword() failed for id={}", userId, e);
            throw new RuntimeException("Unable to update password", e);
        }
    }

    /** Record a successful login timestamp. */
    public void touchLastLogin(long userId) {
        String sql = "UPDATE users SET last_login_at = NOW() WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("Failed to update last_login_at for id={}", userId, e);
            // non-fatal
        }
    }

    /** Soft-delete: deactivate instead of DELETE (keeps audit trail intact). */
    public void setActive(long userId, boolean active) {
        String sql = "UPDATE users SET active = ? WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setLong(2, userId);
            ps.executeUpdate();
            log.info("Set active={} for user id={}", active, userId);
        } catch (SQLException e) {
            log.error("setActive() failed for id={}", userId, e);
            throw new RuntimeException("Unable to update user", e);
        }
    }

    /** Maps a ResultSet row (from BASE_SELECT) into a User. */
    private User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setFullName(rs.getString("full_name"));
        u.setRoleId(rs.getInt("role_id"));
        u.setActive(rs.getBoolean("active"));

        Timestamp createdTs = rs.getTimestamp("created_at");
        if (createdTs != null) {
            u.setCreatedAt(createdTs.toInstant().atOffset(java.time.ZoneOffset.UTC));
        }
        Timestamp lastLoginTs = rs.getTimestamp("last_login_at");
        if (lastLoginTs != null) {
            u.setLastLoginAt(lastLoginTs.toInstant().atOffset(java.time.ZoneOffset.UTC));
        }

        Role role = new Role(
            rs.getInt("r_id"),
            rs.getString("r_code"),
            rs.getString("r_name"),
            rs.getString("r_desc")
        );
        u.setRole(role);
        return u;
    }
}
