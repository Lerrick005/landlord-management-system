package com.propertymanager.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Represents a row in the "tenants" table.
 *
 * A tenant is a person who rents a room. Historical records are never
 * deleted — when a tenant moves out, their status changes to LEFT but
 * the row stays for reporting and audit.
 */
public class Tenant {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_LEFT   = "LEFT";

    private long id;
    private String tenantNumber;        // TEN-000001
    private String fullName;
    private String nationalId;          // ID or passport number (unique)
    private String phone;
    private String altPhone;            // optional
    private String email;               // optional
    private String nextOfKin;           // optional
    private String nextOfKinPhone;      // optional
    private String occupation;          // optional
    private String status;              // ACTIVE / LEFT
    private LocalDate registeredAt;
    private OffsetDateTime createdAt;

    public Tenant() {
        this.status = STATUS_ACTIVE;
        this.registeredAt = LocalDate.now();
    }

    /* ---- accessors ---- */

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTenantNumber() { return tenantNumber; }
    public void setTenantNumber(String tenantNumber) { this.tenantNumber = tenantNumber; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAltPhone() { return altPhone; }
    public void setAltPhone(String altPhone) { this.altPhone = altPhone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNextOfKin() { return nextOfKin; }
    public void setNextOfKin(String nextOfKin) { this.nextOfKin = nextOfKin; }

    public String getNextOfKinPhone() { return nextOfKinPhone; }
    public void setNextOfKinPhone(String nextOfKinPhone) { this.nextOfKinPhone = nextOfKinPhone; }

    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDate registeredAt) { this.registeredAt = registeredAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    /* ---- convenience ---- */

    public boolean isActive() { return STATUS_ACTIVE.equalsIgnoreCase(status); }
    public boolean hasLeft() { return STATUS_LEFT.equalsIgnoreCase(status); }

    /** Returns up to two initials from the full name. */
    public String getInitials() {
        if (fullName == null || fullName.isBlank()) return "?";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return (parts[0].substring(0, 1)
                + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    @Override
    public String toString() {
        return fullName != null ? fullName : tenantNumber;
    }
}
