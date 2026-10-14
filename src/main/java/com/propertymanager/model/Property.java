package com.propertymanager.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Represents a row in the "properties" table.
 *
 * A property is a physical location — e.g. "Sunrise Apartments, Karatina" —
 * that contains one or more units.
 */
public class Property {

    private long id;
    private String code;              // short unique code, e.g. "SUN-01"
    private String name;              // display name, e.g. "Sunrise Apartments"
    private String location;          // e.g. "Karatina"
    private String description;       // optional long text
    private String status;            // "ACTIVE" or "INACTIVE"
    private String notes;             // freeform notes
    private LocalDate registeredAt;   // date the property was registered
    private OffsetDateTime createdAt; // audit timestamp

    public Property() {
        this.status = "ACTIVE";
    }

    public Property(String code, String name) {
        this();
        this.code = code;
        this.name = name;
    }

    /* ---- accessors ---- */

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDate getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDate registeredAt) { this.registeredAt = registeredAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    /* ---- convenience ---- */

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    @Override
    public String toString() {
        return name != null ? name : code;
    }
}
