package com.propertymanager.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Represents a row in the "units" table.
 *
 * A unit is a single rentable space inside a property — e.g. "Room K1"
 * at Green Eden Hostel. It has a monthly rent, a deposit amount, and
 * a status that reflects whether someone is living in it.
 */
public class Unit {

    /** Unit type values allowed in the DB CHECK constraint. */
    public static final String TYPE_BEDSITTER    = "BEDSITTER";
    public static final String TYPE_SINGLE       = "SINGLE";
    public static final String TYPE_STUDIO       = "STUDIO";
    public static final String TYPE_ONE_BEDROOM  = "ONE_BEDROOM";
    public static final String TYPE_TWO_BEDROOM  = "TWO_BEDROOM";
    public static final String TYPE_THREE_BEDROOM= "THREE_BEDROOM";
    public static final String TYPE_OTHER        = "OTHER";

    /** Unit status values allowed in the DB CHECK constraint. */
    public static final String STATUS_VACANT    = "VACANT";
    public static final String STATUS_OCCUPIED  = "OCCUPIED";
    public static final String STATUS_MAINTENANCE = "MAINTENANCE";

    private long id;
    private long propertyId;
    private Property property;             // populated when joined
    private String houseNumber;            // e.g. "K1", "A-12" — unique within a property
    private String unitType;               // e.g. "BEDSITTER", "SINGLE"
    private BigDecimal monthlyRent;        // e.g. 8000.00
    private BigDecimal depositAmount;      // e.g. 8000.00
    private String status;                 // VACANT / OCCUPIED / MAINTENANCE
    private String notes;
    private OffsetDateTime createdAt;

    public Unit() {
        this.status = STATUS_VACANT;
        this.monthlyRent = BigDecimal.ZERO;
        this.depositAmount = BigDecimal.ZERO;
    }

    /* ---- accessors ---- */

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getPropertyId() { return propertyId; }
    public void setPropertyId(long propertyId) { this.propertyId = propertyId; }

    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }

    public String getHouseNumber() { return houseNumber; }
    public void setHouseNumber(String houseNumber) { this.houseNumber = houseNumber; }

    public String getUnitType() { return unitType; }
    public void setUnitType(String unitType) { this.unitType = unitType; }

    public BigDecimal getMonthlyRent() { return monthlyRent; }
    public void setMonthlyRent(BigDecimal monthlyRent) {
        this.monthlyRent = monthlyRent != null ? monthlyRent : BigDecimal.ZERO;
    }

    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) {
        this.depositAmount = depositAmount != null ? depositAmount : BigDecimal.ZERO;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    /* ---- convenience ---- */

    public boolean isVacant()     { return STATUS_VACANT.equalsIgnoreCase(status); }
    public boolean isOccupied()   { return STATUS_OCCUPIED.equalsIgnoreCase(status); }
    public boolean isMaintenance(){ return STATUS_MAINTENANCE.equalsIgnoreCase(status); }

    /** Human-readable unit type for display in tables and forms. */
    public String getUnitTypeDisplay() {
        if (unitType == null) return "";
        return switch (unitType) {
            case TYPE_BEDSITTER     -> "Bedsitter";
            case TYPE_SINGLE        -> "Single";
            case TYPE_STUDIO        -> "Studio";
            case TYPE_ONE_BEDROOM   -> "One Bedroom";
            case TYPE_TWO_BEDROOM   -> "Two Bedroom";
            case TYPE_THREE_BEDROOM -> "Three Bedroom";
            case TYPE_OTHER         -> "Other";
            default -> unitType;
        };
    }

    @Override
    public String toString() {
        return houseNumber != null ? houseNumber : ("Unit #" + id);
    }
}
