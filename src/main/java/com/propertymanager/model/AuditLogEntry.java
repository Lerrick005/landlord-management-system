package com.propertymanager.model;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Represents one row in the audit_logs table.
 *
 * Read-only snapshot of "who did what, when". Audit entries are
 * never edited or deleted.
 */
public class AuditLogEntry {

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    private long id;
    private Long userId;             // may be null (system actions)
    private String username;         // "admin" or "SYSTEM"
    private String action;           // e.g. PAYMENT_RECORDED
    private String entity;           // e.g. payments
    private String entityId;         // e.g. RCT-2026-000001
    private String description;      // free text
    private OffsetDateTime createdAt;

    public AuditLogEntry() {}

    /* ---- accessors ---- */

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    /* ---- display helpers ---- */

    /** "18 Sep 2026 14:23" for the WHEN column. */
    public String getWhenLabel() {
        if (createdAt == null) return "—";
        return createdAt.atZoneSameInstant(ZoneId.systemDefault()).format(FMT);
    }

    @Override
    public String toString() {
        return getWhenLabel() + " | " + username + " | " + action;
    }
}
