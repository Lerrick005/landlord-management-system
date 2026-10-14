package com.propertymanager.service;

import com.propertymanager.model.Unit;
import com.propertymanager.repository.UnitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Business logic for rental units.
 */
public class UnitService {

    private static final Logger log = LoggerFactory.getLogger(UnitService.class);

    private static final Set<String> VALID_TYPES = Set.of(
        Unit.TYPE_BEDSITTER, Unit.TYPE_SINGLE, Unit.TYPE_STUDIO,
        Unit.TYPE_ONE_BEDROOM, Unit.TYPE_TWO_BEDROOM,
        Unit.TYPE_THREE_BEDROOM, Unit.TYPE_OTHER
    );

    private static final Set<String> VALID_STATUSES = Set.of(
        Unit.STATUS_VACANT, Unit.STATUS_OCCUPIED, Unit.STATUS_MAINTENANCE
    );

    private final UnitRepository repository;
    private final AuditService audit;

    public UnitService() {
        this.repository = new UnitRepository();
        this.audit = new AuditService();
    }

    /* =================================================================== */

    public List<Unit> listAll() {
        return repository.findAll();
    }

    public List<Unit> listByProperty(long propertyId) {
        return repository.findByProperty(propertyId);
    }

    public List<Unit> listVacant() {
        return repository.findVacant();
    }

    public List<Unit> search(String query, Long propertyId) {
        return repository.search(query, propertyId);
    }

    public Unit findById(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Unit not found."));
    }

    public long count() {
        return repository.count();
    }

    public long countByStatus(String status) {
        return repository.countByStatus(status);
    }

    /* =================================================================== */

    /** Validates and creates a new unit. */
    public Unit create(Unit u) throws ValidationException {
        validate(u, true);

        if (repository.houseNumberExists(u.getPropertyId(), u.getHouseNumber(), 0L)) {
            throw new ValidationException(
                "House number '" + u.getHouseNumber() + "' already exists in this property.");
        }

        if (u.getStatus() == null || u.getStatus().isBlank()) {
            u.setStatus(Unit.STATUS_VACANT);
        }

        long id = repository.insert(u);
        audit.log("UNIT_CREATED", "units", String.valueOf(id),
                  u.getHouseNumber() + " (" + u.getUnitTypeDisplay() + ")");
        log.info("Created unit id={} house={}", id, u.getHouseNumber());
        return u;
    }

    /* =================================================================== */

    public Unit update(Unit u) throws ValidationException {
        validate(u, false);

        if (repository.houseNumberExists(u.getPropertyId(), u.getHouseNumber(), u.getId())) {
            throw new ValidationException(
                "House number '" + u.getHouseNumber() + "' already exists in this property.");
        }

        repository.update(u);
        audit.log("UNIT_UPDATED", "units", String.valueOf(u.getId()), u.getHouseNumber());
        log.info("Updated unit id={}", u.getId());
        return u;
    }

    /* =================================================================== */

    public void markOccupied(long unitId) {
        setStatus(unitId, Unit.STATUS_OCCUPIED);
    }

    public void markVacant(long unitId) {
        setStatus(unitId, Unit.STATUS_VACANT);
    }

    public void markMaintenance(long unitId) {
        setStatus(unitId, Unit.STATUS_MAINTENANCE);
    }

    private void setStatus(long unitId, String newStatus) {
        Unit u = findById(unitId);
        if (newStatus.equals(u.getStatus())) return;

        repository.updateStatus(unitId, newStatus);
        audit.log("UNIT_STATUS_CHANGED", "units", String.valueOf(unitId),
                  u.getStatus() + " -> " + newStatus);
        log.info("Unit id={} status {} -> {}", unitId, u.getStatus(), newStatus);
    }

    /* =================================================================== */

    public void delete(long id) {
        Unit u = findById(id);
        repository.delete(id);
        audit.log("UNIT_DELETED", "units", String.valueOf(id), u.getHouseNumber());
        log.info("Deleted unit id={}", id);
    }

    /* =================================================================== */

    private void validate(Unit u, boolean isCreate) throws ValidationException {
        if (u == null) throw new ValidationException("Unit data is required.");
        if (!isCreate && u.getId() <= 0)
            throw new ValidationException("Unit id is missing.");

        if (u.getPropertyId() <= 0)
            throw new ValidationException("Please select a property.");

        if (isBlank(u.getHouseNumber()))
            throw new ValidationException("House number is required.");
        if (u.getHouseNumber().length() > 30)
            throw new ValidationException("House number must be 30 characters or fewer.");

        if (isBlank(u.getUnitType()))
            throw new ValidationException("Unit type is required.");
        if (!VALID_TYPES.contains(u.getUnitType()))
            throw new ValidationException("Please choose a valid unit type.");

        if (u.getMonthlyRent() == null)
            throw new ValidationException("Monthly rent is required.");
        if (u.getMonthlyRent().compareTo(BigDecimal.ZERO) < 0)
            throw new ValidationException("Monthly rent cannot be negative.");

        if (u.getDepositAmount() == null)
            u.setDepositAmount(BigDecimal.ZERO);
        if (u.getDepositAmount().compareTo(BigDecimal.ZERO) < 0)
            throw new ValidationException("Deposit cannot be negative.");

        if (u.getStatus() != null && !u.getStatus().isBlank()
                && !VALID_STATUSES.contains(u.getStatus())) {
            throw new ValidationException("Invalid unit status.");
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
