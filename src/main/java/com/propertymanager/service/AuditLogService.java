package com.propertymanager.service;

import com.propertymanager.database.Database;
import com.propertymanager.model.AuditLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only access to the audit_logs table.
 *
 * The write path lives in {@link AuditService}. This service is for
 * querying logs — used by the Audit Logs screen.
 */
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private static final String BASE_SELECT = """
        SELECT id, user_id, username, action, entity, entity_id, description, created_at
        FROM audit_logs
        """;

    /* =================================================================== */

    /** Most recent N entries, newest first. */
    public List<AuditLogEntry> findRecent(int limit) {
        String sql = BASE_SELECT + " ORDER BY created_at DESC LIMIT ?";
        List<AuditLogEntry> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            log.error("findRecent({}) failed", limit, e);
            throw new RuntimeException("Unable to load audit logs", e);
        }
    }

    /** All entries (use sparingly — prefer findRecent). */
    public List<AuditLogEntry> findAll() {
        String sql = BASE_SELECT + " ORDER BY created_at DESC";
        List<AuditLogEntry> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            log.error("findAll() failed", e);
            throw new RuntimeException("Unable to load audit logs", e);
        }
    }

    /** Entries for a specific user. */
    public List<AuditLogEntry> findByUsername(String username) {
        String sql = BASE_SELECT + " WHERE username = ? ORDER BY created_at DESC LIMIT 500";
        List<AuditLogEntry> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            log.error("findByUsername({}) failed", username, e);
            throw new RuntimeException("Unable to load audit logs", e);
        }
    }

    /** Count total entries. */
    public long count() {
        String sql = "SELECT COUNT(*) FROM audit_logs";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            log.error("count() failed", e);
            throw new RuntimeException("Unable to count audit logs", e);
        }
    }

    /* =================================================================== */

    private AuditLogEntry map(ResultSet rs) throws SQLException {
        AuditLogEntry e = new AuditLogEntry();
        e.setId(rs.getLong("id"));

        long uid = rs.getLong("user_id");
        if (!rs.wasNull()) e.setUserId(uid);

        e.setUsername(rs.getString("username"));
        e.setAction(rs.getString("action"));
        e.setEntity(rs.getString("entity"));
        e.setEntityId(rs.getString("entity_id"));
        e.setDescription(rs.getString("description"));

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            e.setCreatedAt(ts.toInstant().atOffset(ZoneOffset.UTC));
        }
        return e;
    }
}
