package com.propertymanager.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Represents a row in the "payments" table.
 *
 * A payment records that a tenant paid a specific amount, for a specific
 * month, using a specific method. Payments are never edited or deleted —
 * if a mistake is made, the original is voided and a new one recorded.
 *
 * The receipt number is auto-generated (RCT-2026-000001).
 */
public class Payment {

    /* ---- Payment methods (must match the DB CHECK constraint) ---- */
    public static final String METHOD_CASH  = "CASH";
    public static final String METHOD_MPESA = "MPESA";
    public static final String METHOD_BANK  = "BANK";
    public static final String METHOD_OTHER = "OTHER";

    private long id;
    private String receiptNumber;       // RCT-2026-000001

    private long tenantId;
    private Tenant tenant;              // populated when joined

    private Long tenancyId;             // nullable — the tenancy active at payment time
    private Tenancy tenancy;            // populated when joined (optional)

    private LocalDate paymentDate;      // when the money was received
    private BigDecimal amount;          // how much

    private int periodYear;             // which month this payment is for
    private int periodMonth;            // 1-12

    private String method;              // CASH / MPESA / BANK / OTHER
    private String referenceNumber;     // e.g. M-Pesa transaction code
    private String notes;

    private Long recordedByUserId;      // which user recorded it
    private String recordedByUsername;  // populated when joined (for receipts)

    // ---- Void support ----
    private boolean voided;             // true if this payment was reversed
    private OffsetDateTime voidedAt;    // when it was voided
    private Long voidedByUserId;        // who voided it
    private String voidedByUsername;    // populated when joined
    private String voidReason;          // why it was voided

    private OffsetDateTime createdAt;

    public Payment() {
        this.paymentDate = LocalDate.now();
        this.amount = BigDecimal.ZERO;
        this.method = METHOD_CASH;
        this.periodYear = LocalDate.now().getYear();
        this.periodMonth = LocalDate.now().getMonthValue();
    }

    /* ---- accessors ---- */

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getReceiptNumber() { return receiptNumber; }
    public void setReceiptNumber(String receiptNumber) { this.receiptNumber = receiptNumber; }

    public long getTenantId() { return tenantId; }
    public void setTenantId(long tenantId) { this.tenantId = tenantId; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public Long getTenancyId() { return tenancyId; }
    public void setTenancyId(Long tenancyId) { this.tenancyId = tenancyId; }

    public Tenancy getTenancy() { return tenancy; }
    public void setTenancy(Tenancy tenancy) { this.tenancy = tenancy; }

    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) {
        this.amount = amount != null ? amount : BigDecimal.ZERO;
    }

    public int getPeriodYear() { return periodYear; }
    public void setPeriodYear(int periodYear) { this.periodYear = periodYear; }

    public int getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(int periodMonth) { this.periodMonth = periodMonth; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Long getRecordedByUserId() { return recordedByUserId; }
    public void setRecordedByUserId(Long recordedByUserId) { this.recordedByUserId = recordedByUserId; }

    public String getRecordedByUsername() { return recordedByUsername; }
    public void setRecordedByUsername(String recordedByUsername) { this.recordedByUsername = recordedByUsername; }

    /* ---- void fields ---- */

    public boolean isVoided() { return voided; }
    public void setVoided(boolean voided) { this.voided = voided; }

    public OffsetDateTime getVoidedAt() { return voidedAt; }
    public void setVoidedAt(OffsetDateTime voidedAt) { this.voidedAt = voidedAt; }

    public Long getVoidedByUserId() { return voidedByUserId; }
    public void setVoidedByUserId(Long voidedByUserId) { this.voidedByUserId = voidedByUserId; }

    public String getVoidedByUsername() { return voidedByUsername; }
    public void setVoidedByUsername(String voidedByUsername) { this.voidedByUsername = voidedByUsername; }

    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    /* ---- convenience ---- */

    /** Returns the payment method as a friendly display label. */
    public String getMethodDisplay() {
        if (method == null) return "";
        return switch (method) {
            case METHOD_CASH  -> "Cash";
            case METHOD_MPESA -> "M-Pesa";
            case METHOD_BANK  -> "Bank";
            case METHOD_OTHER -> "Other";
            default -> method;
        };
    }

    /** Returns the month name (e.g. "September") for display. */
    public String getPeriodMonthName() {
        return switch (periodMonth) {
            case 1  -> "January";
            case 2  -> "February";
            case 3  -> "March";
            case 4  -> "April";
            case 5  -> "May";
            case 6  -> "June";
            case 7  -> "July";
            case 8  -> "August";
            case 9  -> "September";
            case 10 -> "October";
            case 11 -> "November";
            case 12 -> "December";
            default -> "—";
        };
    }

    /** "September 2026" — handy for receipts and lists. */
    public String getPeriodLabel() {
        return getPeriodMonthName() + " " + periodYear;
    }

    /** True if this payment counts toward balances (not voided). */
    public boolean isActive() {
        return !voided;
    }

    /** Short display label — "VOIDED" or "ACTIVE". */
    public String getStatusLabel() {
        return voided ? "VOIDED" : "ACTIVE";
    }

    @Override
    public String toString() {
        return receiptNumber != null
            ? receiptNumber + " — " + amount
            : "Payment #" + id;
    }
}
