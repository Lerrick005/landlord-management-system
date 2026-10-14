package com.propertymanager.repository;

import com.propertymanager.database.Database;
import com.propertymanager.model.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for the "properties" table.
 */
public class PropertyRepository {

    private static final Logger log = LoggerFactory.getLogger(PropertyRepository.class);

    private static final String BASE_SELECT = """
        SELECT id, code, name, location, description, status, notes,
               registered_at, created_at
        FROM properties
        """;

    /* =================================================================== */

    public List<Property> findAll() {
        String sql = BASE_SELECT + " ORDER BY name";
        List<Property> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            log.error("findAll() failed", e);
            throw new RuntimeException("Unable to load properties", e);
        }
    }

    /** Only ACTIVE properties — for dropdowns and unit forms. */
    public List<Property> findAllActive() {
        String sql = BASE_SELECT + " WHERE status = 'ACTIVE' ORDER BY name";
        List<Property> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            log.error("findAllActive() failed", e);
            throw new RuntimeException("Unable to load active properties", e);
        }
    }

    /** Total properties — for dashboard stat cards. */
    public long count() {
        String sql = "SELECT COUNT(*) FROM properties";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            log.error("count() failed", e);
            throw new RuntimeException("Unable to count properties", e);
        }
    }

    public Optional<Property> findById(long id) {
        String sql = BASE_SELECT + " WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findById({}) failed", id, e);
            throw new RuntimeException("Unable to look up property", e);
        }
    }

    /** Case-insensitive search across name, code and location. */
    public List<Property> search(String query) {
        if (query == null || query.isBlank()) return findAll();

        String sql = BASE_SELECT + """
            WHERE LOWER(name)     LIKE LOWER(?)
               OR LOWER(code)     LIKE LOWER(?)
               OR LOWER(location) LIKE LOWER(?)
            ORDER BY name
            """;
        String pattern = "%" + query.trim() + "%";
        List<Property> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            log.error("search({}) failed", query, e);
            throw new RuntimeException("Unable to search properties", e);
        }
    }

    public long insert(Property p) {
        String sql = """
            INSERT INTO properties (code, name, location, description, status, notes, registered_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, p.getCode());
            ps.setString(2, p.getName());
            ps.setString(3, p.getLocation());
            ps.setString(4, p.getDescription());
            ps.setString(5, p.getStatus() != null ? p.getStatus() : "ACTIVE");
            ps.setString(6, p.getNotes());
            if (p.getRegisteredAt() != null) {
                ps.setDate(7, Date.valueOf(p.getRegisteredAt()));
            } else {
                ps.setDate(7, Date.valueOf(java.time.LocalDate.now()));
            }

            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("Insert affected 0 rows");

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    p.setId(id);
                    log.info("Inserted property id={} code={}", id, p.getCode());
                    return id;
                }
                throw new SQLException("No generated key returned");
            }
        } catch (SQLException e) {
            log.error("insert() failed for code={}", p.getCode(), e);
            throw new RuntimeException("Unable to save property", e);
        }
    }

    public void update(Property p) {
        String sql = """
            UPDATE properties
               SET code = ?, name = ?, location = ?, description = ?,
                   status = ?, notes = ?, registered_at = ?
             WHERE id = ?
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, p.getCode());
            ps.setString(2, p.getName());
            ps.setString(3, p.getLocation());
            ps.setString(4, p.getDescription());
            ps.setString(5, p.getStatus());
            ps.setString(6, p.getNotes());
            if (p.getRegisteredAt() != null) {
                ps.setDate(7, Date.valueOf(p.getRegisteredAt()));
            } else {
                ps.setDate(7, Date.valueOf(java.time.LocalDate.now()));
            }
            ps.setLong(8, p.getId());

            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("No property with id " + p.getId());
            log.info("Updated property id={}", p.getId());
        } catch (SQLException e) {
            log.error("update() failed for id={}", p.getId(), e);
            throw new RuntimeException("Unable to update property", e);
        }
    }

    /**
     * Hard delete. Prefer setStatus(INACTIVE) where historical data matters.
     * A property cannot be deleted while it has units — the FK RESTRICT will block it.
     */
    public void delete(long id) {
        String sql = "DELETE FROM properties WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
            log.info("Deleted property id={}", id);
        } catch (SQLException e) {
            log.error("delete() failed for id={}", id, e);
            throw new RuntimeException(
                "Unable to delete property. It may still have units attached.", e);
        }
    }

    public boolean codeExists(String code, long excludeId) {
        String sql = "SELECT 1 FROM properties WHERE LOWER(code) = LOWER(?) AND id <> ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setLong(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("codeExists({}) failed", code, e);
            throw new RuntimeException("Unable to check property code", e);
        }
    }

    /* =================================================================== */

    private Property map(ResultSet rs) throws SQLException {
        Property p = new Property();
        p.setId(rs.getLong("id"));
        p.setCode(rs.getString("code"));
        p.setName(rs.getString("name"));
        p.setLocation(rs.getString("location"));
        p.setDescription(rs.getString("description"));
        p.setStatus(rs.getString("status"));
        p.setNotes(rs.getString("notes"));

        Date regDate = rs.getDate("registered_at");
        if (regDate != null) p.setRegisteredAt(regDate.toLocalDate());

        Timestamp createdTs = rs.getTimestamp("created_at");
        if (createdTs != null) {
            p.setCreatedAt(createdTs.toInstant().atOffset(ZoneOffset.UTC));
        }
        return p;
    }
}
