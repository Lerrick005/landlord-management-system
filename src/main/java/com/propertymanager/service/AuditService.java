package com.propertymanager.service;

import com.propertymanager.database.Database;
import com.propertymanager.model.User;
import com.propertymanager.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Writes entries to the audit_logs table.
 *
 * Usage:
 *   auditService.log("PAYMENT_RECORDED", "payments", receiptNo, "KSh 8000 via M-Pesa");
 *
 * Each call captures the current user from SessionManager automatically.
 * Failures to write are swallowed (logged only) — audit logging must never
 * break a real business action.
 */
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final String INSERT_SQL = """
        INSERT INTO audit_logs (user_id, username, action, entity, entity_id, description)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

    /**
     * Records an action performed by the currently logged-in user.
     *
     * @param action      short code, e.g. "TENANT_CREATED", "PAYMENT_RECORDED"
     * @param entity      table/area affected, e.g. "tenants", "payments"
     * @param entityId    identifier of the affected row, e.g. "TEN-000124" or "RCT-2026-0001"
     * @param description human-readable detail
     */
    public void log(String action, String entity, String entityId, String description) {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Audit action must not be blank");
        }

        User current = SessionManager.getCurrentUser();
        Long userId = (current != null) ? current.getId() : null;
        String username = (current != null) ? current.getUsername() : "SYSTEM";

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT_SQL)) {

            if (userId != null) ps.setLong(1, userId);
            else ps.setNull(1, java.sql.Types.BIGINT);

            ps.setString(2, username);
            ps.setString(3, action);
            ps.setString(4, entity);
            ps.setString(5, entityId);
            ps.setString(6, description);

            ps.executeUpdate();

            log.debug("AUDIT {} {} {} by {}", action, entity, entityId, username);

        } catch (SQLException e) {
            // Non-fatal — the business action already succeeded
            log.error("Failed to write audit log (action={}, entity={}, id={})",
                      action, entity, entityId, e);
        }
    }

    /** Convenience overload for actions that don't target a specific record. */
    public void log(String action, String description) {
        log(action, null, null, description);
    }
}
