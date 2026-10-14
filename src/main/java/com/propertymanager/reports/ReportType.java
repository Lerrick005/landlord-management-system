package com.propertymanager.reports;

/**
 * Catalogue of all reports the system can generate.
 *
 * Each entry knows its own code, title, description and icon.
 * The Reports hub iterates over these to render the cards,
 * and the viewer uses the code to know which data to fetch.
 */
public enum ReportType {

    TENANTS(
        "TENANTS",
        "Tenants",
        "All tenants with contact details and current room",
        "fas-users",
        Category.PEOPLE
    ),

    PROPERTIES(
        "PROPERTIES",
        "Properties",
        "Every property with unit count and occupancy",
        "fas-building",
        Category.ASSETS
    ),

    OCCUPANCY(
        "OCCUPANCY",
        "Occupancy",
        "Occupied vs vacant rooms with occupancy %",
        "fas-door-closed",
        Category.ASSETS
    ),

    RENT_COLLECTION(
        "RENT_COLLECTION",
        "Rent Collection",
        "Expected vs collected vs outstanding, per property",
        "fas-hand-holding-usd",
        Category.FINANCE
    ),

    ARREARS(
        "ARREARS",
        "Outstanding Rent",
        "Tenants who owe money, biggest balances first",
        "fas-exclamation-triangle",
        Category.FINANCE
    ),

    PAYMENTS(
        "PAYMENTS",
        "Payments",
        "All payments recorded within a selected period",
        "fas-money-bill-wave",
        Category.FINANCE
    );

    /* =================================================================== */

    public enum Category {
        PEOPLE("People"),
        ASSETS("Assets"),
        FINANCE("Finance");

        public final String label;
        Category(String label) { this.label = label; }
    }

    /* =================================================================== */

    private final String code;
    private final String title;
    private final String description;
    private final String iconCode;
    private final Category category;

    ReportType(String code, String title, String description,
               String iconCode, Category category) {
        this.code = code;
        this.title = title;
        this.description = description;
        this.iconCode = iconCode;
        this.category = category;
    }

    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getIconCode() { return iconCode; }
    public Category getCategory() { return category; }

    /** Look up a report by its code. Returns null if none matches. */
    public static ReportType fromCode(String code) {
        if (code == null) return null;
        for (ReportType r : values()) {
            if (r.code.equalsIgnoreCase(code)) return r;
        }
        return null;
    }
}
