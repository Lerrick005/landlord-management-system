package com.propertymanager.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Represents a row in the "tenancies" table.
 *
 * A tenancy is the link between a Tenant and a Unit — "John Kamau rents
 * room K1 at Green Eden Hostel from 01 Sep 2026 at KSh 8,000/month."
 *
 * Rules enforced by the database:
 *   - Only ONE active tenancy per unit
 *   - Only ONE active tenancy per tenant
 *
 * When a tenant moves out, we set status = ENDED and moveOutDate,
 * we do NOT delete the row.
 */
public class Tenancy {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ENDED  = "ENDED";

    private long id;

    private long tenantId;
    private Tenant tenant;              // populated when joined

    private long unitId;
    private Unit unit;                  // populated when joined

    private BigDecimal monthlyRent;     // rent agreed at move-in time
    private BigDecimal depositAmount;   // deposit paid at move-in
    private LocalDate moveInDate;
    private LocalDate moveOutDate;      // null while still active
    private String status;              // ACTIVE / ENDED
    private OffsetDateTime createdAt;

    public Tenancy() {
        this.status = STATUS_ACTIVE;
        this.monthlyRent = BigDecimal.ZERO;
        this.depositAmount = BigDecimal.ZERO;
    }

    /* ---- accessors ---- */

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public long getUnitId() { return unitId; }
    public void setUnitId(long unitId) { this.unitId = unitId; }

    public Unit getUnit() { return unit; }
    public void setUnit(Unit unit) { this.unit = unit; }

    public BigDecimal getMonthlyRent() { return monthlyRent; }
    public void setMonthlyRent(BigDecimal monthlyRent) {
        this.monthlyRent = monthlyRent != null ? monthlyRent : BigDecimal.ZERO;
    }

    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) {
        this.depositAmount = depositAmount != null ? depositAmount : BigDecimal.ZERO;
    }

    public LocalDate getMoveInDate() { return moveInDate; }
    public void setMoveInDate(LocalDate moveInDate) { this.moveInDate = moveInDate; }

    public LocalDate getMoveOutDate() { return moveOutDate; }
    public void setMoveOutDate(LocalDate moveOutDate) { this.moveOutDate = moveOutDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    /* ---- convenience ---- */

    public boolean isActive() { return STATUS_ACTIVE.equalsIgnoreCase(status); }
    public boolean hasEnded() { return STATUS_ENDED.equalsIgnoreCase(status); }

    /**
     * Room label — the unit's house number, e.g. "K1".
     * Falls back to a generic reference if the unit wasn't loaded.
     */
    public String getUnitLabel() {
        return unit != null && unit.getHouseNumber() != null
                ? unit.getHouseNumber()
                : ("Unit #" + unitId);
    }

    /** Property name — "Green Eden Hostel". Falls back to a generic string. */
    public String getPropertyLabel() {
        if (unit == null || unit.getProperty() == null) return "";
        return unit.getProperty().getName();
    }

    @Override
    public String toString() {
        if (tenant != null && unit != null) {
            return tenant.getFullName() + " — " + unit.getHouseNumber();
        }
        return "Tenancy #" + id;
    }
}
