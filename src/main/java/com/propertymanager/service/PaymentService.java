package com.propertymanager.service;

import com.propertymanager.model.Payment;
import com.propertymanager.model.Tenancy;
import com.propertymanager.repository.PaymentRepository;
import com.propertymanager.session.SessionManager;
import com.propertymanager.utils.NumberGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Business logic for payments.
 *
 * Responsibilities:
 *   - Validate payment inputs
 *   - Auto-generate the receipt number (RCT-2026-000001)
 *   - Record the currently-logged-in user as the recorder
 *   - Void a payment (never delete — keep the audit trail)
 *   - Write audit entries
 *
 * Payments are immutable in every way except being voided.
 */
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository repository;
    private final TenancyService tenancyService;
    private final AuditService audit;

    public PaymentService() {
        this.repository = new PaymentRepository();
        this.tenancyService = new TenancyService();
        this.audit = new AuditService();
    }

    /* =================================================================== */
    /*  QUERIES                                                            */
    /* =================================================================== */

    public List<Payment> listAll() {
        return repository.findAll();
    }

    public List<Payment> listByTenant(long tenantId) {
        return repository.findByTenant(tenantId);
    }

    public List<Payment> listByPeriod(int year, int month) {
        return repository.findByPeriod(year, month);
    }

    public List<Payment> listByDateRange(LocalDate from, LocalDate to) {
        return repository.findByDateRange(from, to);
    }

    public List<Payment> search(String query) {
        return repository.search(query);
    }

    public Payment findById(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found."));
    }

    public Payment findByReceiptNumber(String receiptNumber) {
        return repository.findByReceiptNumber(receiptNumber)
                .orElseThrow(() -> new IllegalArgumentException("Receipt not found."));
    }

    public long count() {
        return repository.count();
    }

    /** Total collected for a specific month — used by the dashboard. */
    public BigDecimal totalCollectedFor(int year, int month) {
        return repository.sumForPeriod(year, month);
    }

    /** Total collected between two dates — used by reports. */
    public BigDecimal totalCollectedBetween(LocalDate from, LocalDate to) {
        return repository.sumBetweenDates(from, to);
    }

    /* =================================================================== */
    /*  RECORD A PAYMENT                                                   */
    /* =================================================================== */

    /**
     * Records a payment. Auto-generates the receipt number if missing.
     *
     * @throws ValidationException if inputs are invalid
     */
    public Payment record(Payment p) throws ValidationException {
        validate(p, true);

        // Auto-generate receipt number if missing
        if (p.getReceiptNumber() == null || p.getReceiptNumber().isBlank()) {
            String receipt = NumberGenerator.nextReceiptNumber();
            int attempts = 0;
            while (repository.receiptNumberExists(receipt) && attempts < 5) {
                receipt = NumberGenerator.nextReceiptNumber();
                attempts++;
            }
            p.setReceiptNumber(receipt);
        }

        // Snapshot the current tenancy (if any) at payment time
        if (p.getTenancyId() == null && p.getTenantId() > 0) {
            tenancyService.findActiveByTenant(p.getTenantId())
                .ifPresent(t -> p.setTenancyId(t.getId()));
        }

        // Record who is taking the payment
        if (SessionManager.isLoggedIn()) {
            p.setRecordedByUserId(SessionManager.getCurrentUser().getId());
        }

        if (p.getPaymentDate() == null) {
            p.setPaymentDate(LocalDate.now());
        }

        long id = repository.insert(p);

        audit.log("PAYMENT_RECORDED", "payments", p.getReceiptNumber(),
                  p.getAmount() + " from tenant #" + p.getTenantId()
                  + " for " + p.getPeriodLabel());

        log.info("Recorded payment id={} receipt={} amount={}",
                 id, p.getReceiptNumber(), p.getAmount());
        return p;
    }

    /* =================================================================== */
    /*  VOID A PAYMENT                                                     */
    /* =================================================================== */

    /**
     * Marks a payment as voided.
     *
     * Voided payments:
     *   - stop counting toward balances
     *   - keep their row and all their data (audit trail)
     *   - record who voided them, when, and why
     *
     * Only ACTIVE payments can be voided. A reason is required.
     *
     * @throws ValidationException if inputs are invalid
     */
    public void voidPayment(long paymentId, String reason) throws ValidationException {
        if (paymentId <= 0) {
            throw new ValidationException("Invalid payment.");
        }
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("Please provide a reason for voiding this payment.");
        }
        String trimmed = reason.trim();
        if (trimmed.length() < 5) {
            throw new ValidationException("The reason must be at least 5 characters.");
        }
        if (trimmed.length() > 500) {
            throw new ValidationException("The reason must be 500 characters or fewer.");
        }

        // Look up the payment first so we can validate state and log details
        Payment p = repository.findById(paymentId)
            .orElseThrow(() -> new ValidationException("Payment not found."));

        if (p.isVoided()) {
            throw new ValidationException("This payment has already been voided.");
        }

        // Who is voiding it?
        if (!SessionManager.isLoggedIn()) {
            throw new ValidationException("You must be logged in to void a payment.");
        }
        long userId = SessionManager.getCurrentUser().getId();

        // Persist the void
        repository.voidPayment(paymentId, userId, trimmed);

        // Audit log
        audit.log("PAYMENT_VOIDED", "payments", p.getReceiptNumber(),
                  "Voided: " + p.getAmount() + " (" + trimmed + ")");

        log.info("Voided payment {} ({}) reason: {}",
                 p.getReceiptNumber(), p.getAmount(), trimmed);
    }

    /* =================================================================== */
    /*  BALANCE CALCULATION                                                */
    /* =================================================================== */

    /**
     * For a specific tenant and month, returns the balance status.
     * Assumes the tenant's monthly rent comes from an ACTIVE tenancy.
     */
    public BalanceSummary balanceFor(long tenantId, YearMonth month) {
        BigDecimal expected = BigDecimal.ZERO;
        String room = "—";

        var activeOpt = tenancyService.findActiveByTenant(tenantId);
        if (activeOpt.isPresent()) {
            Tenancy t = activeOpt.get();
            expected = t.getMonthlyRent() != null ? t.getMonthlyRent() : BigDecimal.ZERO;
            room = t.getUnitLabel();
        }

        BigDecimal paid = repository.sumForTenantAndPeriod(
            tenantId, month.getYear(), month.getMonthValue());

        BigDecimal outstanding = expected.subtract(paid);
        if (outstanding.signum() < 0) outstanding = BigDecimal.ZERO;

        String status;
        if (expected.signum() == 0 && paid.signum() == 0)      status = "N/A";
        else if (paid.signum() == 0)                            status = "UNPAID";
        else if (paid.compareTo(expected) < 0)                  status = "PARTIAL";
        else if (paid.compareTo(expected) == 0)                 status = "PAID";
        else                                                     status = "OVERPAID";

        return new BalanceSummary(room, expected, paid, outstanding, status);
    }

    /* =================================================================== */
    /*  VALIDATION                                                         */
    /* =================================================================== */

    private void validate(Payment p, boolean isCreate) throws ValidationException {
        if (p == null) throw new ValidationException("Payment data is required.");
        if (isCreate && p.getTenantId() <= 0)
            throw new ValidationException("Please select a tenant.");

        if (p.getAmount() == null || p.getAmount().signum() <= 0)
            throw new ValidationException("Payment amount must be greater than zero.");

        if (p.getPaymentDate() == null)
            throw new ValidationException("Payment date is required.");

        if (p.getPeriodMonth() < 1 || p.getPeriodMonth() > 12)
            throw new ValidationException("Invalid month.");
        if (p.getPeriodYear() < 2000 || p.getPeriodYear() > 2100)
            throw new ValidationException("Invalid year.");

        String m = p.getMethod();
        if (m == null || m.isBlank())
            throw new ValidationException("Payment method is required.");
        if (!m.equals(Payment.METHOD_CASH)
                && !m.equals(Payment.METHOD_MPESA)
                && !m.equals(Payment.METHOD_BANK)
                && !m.equals(Payment.METHOD_OTHER)) {
            throw new ValidationException("Invalid payment method.");
        }

        if (p.getReferenceNumber() != null && p.getReferenceNumber().length() > 60)
            throw new ValidationException("Reference must be 60 characters or fewer.");

        if (p.getNotes() != null && p.getNotes().length() > 500)
            throw new ValidationException("Notes must be 500 characters or fewer.");
    }

    /* =================================================================== */
    /*  SUPPORTING TYPES                                                   */
    /* =================================================================== */

    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }

    /** Summary of a tenant's balance for a specific month. */
    public static class BalanceSummary {
        public final String room;
        public final BigDecimal expected;
        public final BigDecimal paid;
        public final BigDecimal outstanding;
        public final String status;   // PAID / PARTIAL / UNPAID / OVERPAID / N/A

        public BalanceSummary(String room, BigDecimal expected, BigDecimal paid,
                              BigDecimal outstanding, String status) {
            this.room = room;
            this.expected = expected;
            this.paid = paid;
            this.outstanding = outstanding;
            this.status = status;
        }
    }
}
