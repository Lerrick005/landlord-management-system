package com.propertymanager.model;

import java.time.OffsetDateTime;

/**
 * Represents a row in the "users" table.
 *
 * Note: this class holds the BCrypt hash (passwordHash), never a plain password.
 */
public class User {

    private long id;
    private String username;
    private String passwordHash;
    private String fullName;
    private int roleId;
    private Role role;                 // populated when joined
    private boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastLoginAt;

    public User() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public int getRoleId() {
        return roleId;
    }

    public void setRoleId(int roleId) {
        this.roleId = roleId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(OffsetDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    /** Convenience: returns the role code (e.g. "ADMIN"), or null if role not loaded. */
    public String getRoleCode() {
        return role != null ? role.getCode() : null;
    }

    /** Convenience: checks if this user has a given role. */
    public boolean hasRole(String code) {
        return role != null && code != null && code.equalsIgnoreCase(role.getCode());
    }

    @Override
    public String toString() {
        return fullName != null ? fullName : username;
    }
}
