package com.propertymanager.service;

import com.propertymanager.model.Tenant;
import com.propertymanager.repository.TenantRepository;
import com.propertymanager.utils.NumberGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

/**
 * Business logic for tenants.
 *
 * Rules:
 *   - National ID must be unique
 *   - Tenant number is auto-generated (TEN-000001)
 *   - Tenants are never deleted — mark as LEFT instead
 *   - Historical records are preserved forever
 */
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository repository;
    private final AuditService audit;

    public TenantService() {
        this.repository = new TenantRepository();
        this.audit = new AuditService();
    }

    /* =================================================================== */

    public List<Tenant> listAll() {
        return repository.findAll();
    }

    public List<Tenant> listActive() {
        return repository.findActive();
    }

    /** Tenants without a current room — for the "Assign Room" dropdown. */
    public List<Tenant> listUnassigned() {
        return repository.findUnassigned();
    }

    public List<Tenant> search(String query) {
        return repository.search(query);
    }

    public Tenant findById(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found."));
    }

    public long count() {
        return repository.count();
    }

    /* =================================================================== */

    /**
     * Registers a new tenant.
     * Auto-generates the tenant number and validates all fields.
     */
    public Tenant register(Tenant t) throws ValidationException {
        validate(t, true);

        // Auto-generate tenant number if missing
        if (t.getTenantNumber() == null || t.getTenantNumber().isBlank()) {
            String number = NumberGenerator.nextTenantNumber();
            // Extremely unlikely collision — but be safe
            int attempts = 0;
            while (repository.tenantNumberExists(number) && attempts < 5) {
                number = NumberGenerator.nextTenantNumber();
                attempts++;
            }
            t.setTenantNumber(number);
        }

        // Unique national ID check
        if (repository.nationalIdExists(t.getNationalId(), 0L)) {
            throw new ValidationException(
                "A tenant with national ID " + t.getNationalId() + " is already registered.");
        }

        if (t.getStatus() == null || t.getStatus().isBlank()) {
            t.setStatus(Tenant.STATUS_ACTIVE);
        }
        if (t.getRegisteredAt() == null) {
            t.setRegisteredAt(LocalDate.now());
        }

        long id = repository.insert(t);
        audit.log("TENANT_REGISTERED", "tenants", t.getTenantNumber(), t.getFullName());
        log.info("Registered tenant '{}' number={}", t.getFullName(), t.getTenantNumber());
        return t;
    }

    /* =================================================================== */

    public Tenant update(Tenant t) throws ValidationException {
        validate(t, false);

        if (repository.nationalIdExists(t.getNationalId(), t.getId())) {
            throw new ValidationException(
                "A tenant with national ID " + t.getNationalId() + " is already registered.");
        }

        repository.update(t);
        audit.log("TENANT_UPDATED", "tenants", t.getTenantNumber(), t.getFullName());
        log.info("Updated tenant id={}", t.getId());
        return t;
    }

    /* =================================================================== */

    /**
     * Marks a tenant as LEFT. Called when their tenancy ends.
     * Does NOT delete the row — historical data is preserved.
     */
    public void markAsLeft(long tenantId) {
        Tenant t = findById(tenantId);
        if (t.hasLeft()) return;

        repository.markAsLeft(tenantId);
        audit.log("TENANT_MARKED_LEFT", "tenants", t.getTenantNumber(), t.getFullName());
        log.info("Tenant id={} marked LEFT", tenantId);
    }

    public void reactivate(long tenantId) {
        Tenant t = findById(tenantId);
        if (t.isActive()) return;

        repository.setActive(tenantId, true);
        audit.log("TENANT_REACTIVATED", "tenants", t.getTenantNumber(), t.getFullName());
        log.info("Tenant id={} reactivated", tenantId);
    }

    /* =================================================================== */

    private void validate(Tenant t, boolean isCreate) throws ValidationException {
        if (t == null)
            throw new ValidationException("Tenant data is required.");
        if (!isCreate && t.getId() <= 0)
            throw new ValidationException("Tenant id is missing.");

        if (isBlank(t.getFullName()))
            throw new ValidationException("Full name is required.");
        if (t.getFullName().length() > 120)
            throw new ValidationException("Full name must be 120 characters or fewer.");

        if (isBlank(t.getNationalId()))
            throw new ValidationException("National ID or passport number is required.");
        if (t.getNationalId().length() > 40)
            throw new ValidationException("National ID must be 40 characters or fewer.");

        if (isBlank(t.getPhone()))
            throw new ValidationException("Phone number is required.");
        if (t.getPhone().length() > 30)
            throw new ValidationException("Phone number must be 30 characters or fewer.");

        if (t.getAltPhone() != null && t.getAltPhone().length() > 30)
            throw new ValidationException("Alternative phone must be 30 characters or fewer.");

        if (t.getEmail() != null && !t.getEmail().isBlank()) {
            if (t.getEmail().length() > 120)
                throw new ValidationException("Email must be 120 characters or fewer.");
            if (!t.getEmail().contains("@"))
                throw new ValidationException("Email address looks invalid.");
        }

        if (t.getNextOfKin() != null && t.getNextOfKin().length() > 120)
            throw new ValidationException("Next of kin must be 120 characters or fewer.");

        if (t.getNextOfKinPhone() != null && t.getNextOfKinPhone().length() > 30)
            throw new ValidationException("Next of kin phone must be 30 characters or fewer.");

        if (t.getOccupation() != null && t.getOccupation().length() > 80)
            throw new ValidationException("Occupation must be 80 characters or fewer.");

        if (t.getStatus() != null
                && !Tenant.STATUS_ACTIVE.equals(t.getStatus())
                && !Tenant.STATUS_LEFT.equals(t.getStatus())) {
            throw new ValidationException("Invalid tenant status.");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /* =================================================================== */

    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
}
