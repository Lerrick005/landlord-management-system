package com.propertymanager.service;

import com.propertymanager.database.Database;
import com.propertymanager.model.Tenancy;
import com.propertymanager.model.Unit;
import com.propertymanager.repository.TenancyRepository;
import com.propertymanager.repository.UnitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for tenancies — the "who lives where" relationship.
 *
 * All mutations run inside a transaction so that either:
 *   - the tenancy is created AND the room is marked OCCUPIED, or
 *   - neither happens
 * This keeps the database consistent even if the app crashes halfway through.
 */
public class TenancyService {

    private static final Logger log = LoggerFactory.getLogger(TenancyService.class);

    private final TenancyRepository tenancyRepo;
    private final UnitRepository unitRepo;
    private final UnitService unitService;
    private final TenantService tenantService;
    private final AuditService audit;

    public TenancyService() {
        this.tenancyRepo = new TenancyRepository();
        this.unitRepo = new UnitRepository();
        this.unitService = new UnitService();
        this.tenantService = new TenantService();
        this.audit = new AuditService();
    }

    /* =================================================================== */

    public List<Tenancy> listAll() {
        return tenancyRepo.findAll();
    }

    public List<Tenancy> listActive() {
        return tenancyRepo.findActive();
    }

    public Tenancy findById(long id) {
        return tenancyRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenancy not found."));
    }

    public Optional<Tenancy> findActiveByUnit(long unitId) {
        return tenancyRepo.findActiveByUnit(unitId);
    }

    public Optional<Tenancy> findActiveByTenant(long tenantId) {
        return tenancyRepo.findActiveByTenant(tenantId);
    }

    public List<Tenancy> historyForTenant(long tenantId) {
        return tenancyRepo.findByTenant(tenantId);
    }

    public long countActive() {
        return tenancyRepo.countActive();
    }

    /* =================================================================== */
    /*  ASSIGN A TENANT TO A ROOM                                           */
    /* =================================================================== */

    /**
     * Assigns a tenant to a room.
     *
     * Effect:
     *   1. Creates a Tenancy row with the given move-in date, rent, deposit
     *   2. Marks the room as OCCUPIED
     *   3. Writes an audit entry
     *
     * Runs in a transaction — the tenancy is only saved if the room
     * update also succeeds.
     *
     * @throws ValidationException if the tenant already lives somewhere,
     *                             the room is already taken, or the inputs are bad.
     */
    public Tenancy assign(long tenantId, long unitId, LocalDate moveInDate,
                          BigDecimal monthlyRent, BigDecimal depositAmount)
            throws ValidationException {

        // ---------- Validate inputs ----------
        if (tenantId <= 0) throw new ValidationException("Please select a tenant.");
        if (unitId <= 0)   throw new ValidationException("Please select a room.");
        if (moveInDate == null) moveInDate = LocalDate.now();
        if (monthlyRent == null || monthlyRent.signum() < 0)
            throw new ValidationException("Monthly rent must be a positive number.");
        if (depositAmount == null) depositAmount = BigDecimal.ZERO;
        if (depositAmount.signum() < 0)
            throw new ValidationException("Deposit cannot be negative.");

        // ---------- Business rules ----------
        if (tenancyRepo.findActiveByTenant(tenantId).isPresent()) {
            throw new ValidationException(
                "This tenant already has an active room. End the current tenancy first.");
        }
        if (tenancyRepo.findActiveByUnit(unitId).isPresent()) {
            throw new ValidationException(
                "This room is already occupied. Choose a vacant room.");
        }

        // Also make sure the unit actually exists and is not in maintenance
        Unit unit = unitRepo.findById(unitId)
                .orElseThrow(() -> new ValidationException("The selected room no longer exists."));
        if (Unit.STATUS_MAINTENANCE.equalsIgnoreCase(unit.getStatus())) {
            throw new ValidationException(
                "This room is under maintenance and cannot be assigned.");
        }

        // ---------- Build and persist inside a transaction ----------
        Tenancy t = new Tenancy();
        t.setTenantId(tenantId);
        t.setUnitId(unitId);
        t.setMonthlyRent(monthlyRent);
        t.setDepositAmount(depositAmount);
        t.setMoveInDate(moveInDate);
        t.setStatus(Tenancy.STATUS_ACTIVE);

        try (Connection c = Database.getConnection()) {
            boolean originalAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // 1) Insert the tenancy
                long tenancyId = tenancyRepo.insert(t);
                t.setId(tenancyId);

                // 2) Mark the unit as OCCUPIED
                unitRepo.updateStatus(unitId, Unit.STATUS_OCCUPIED);

                c.commit();

                // 3) Audit
                audit.log("TENANCY_ASSIGNED", "tenancies", String.valueOf(tenancyId),
                          "Tenant #" + tenantId + " -> Unit #" + unitId
                          + " (" + unit.getHouseNumber() + ")");

                log.info("Assigned tenant {} to unit {} (house {})",
                         tenantId, unitId, unit.getHouseNumber());
                return t;

            } catch (RuntimeException e) {
                c.rollback();
                log.error("Assignment failed — rolled back", e);
                throw new ValidationException(
                    "Unable to assign this tenant to the room. Please try again.");

            } finally {
                c.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            log.error("Transaction failed during assignment", e);
            throw new ValidationException("Database error while assigning tenant.");
        }
    }

    /* =================================================================== */
    /*  END A TENANCY                                                       */
    /* =================================================================== */

    /**
     * Ends a tenancy and frees the room.
     *
     * Effect:
     *   1. Tenancy -> status ENDED with move_out_date
     *   2. Room -> status VACANT
     *   3. Tenant -> status LEFT
     *   4. Audit entry
     *
     * Runs in a transaction.
     */
    public void endTenancy(long tenancyId, LocalDate moveOutDate) throws ValidationException {
        if (moveOutDate == null) moveOutDate = LocalDate.now();

        Tenancy t = tenancyRepo.findById(tenancyId)
                .orElseThrow(() -> new ValidationException("Tenancy not found."));
        if (!t.isActive()) {
            throw new ValidationException("This tenancy is already ended.");
        }
        if (moveOutDate.isBefore(t.getMoveInDate())) {
            throw new ValidationException("Move-out date cannot be before the move-in date.");
        }

        try (Connection c = Database.getConnection()) {
            boolean originalAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // 1) End the tenancy
                tenancyRepo.end(tenancyId, moveOutDate);

                // 2) Room becomes vacant
                unitRepo.updateStatus(t.getUnitId(), Unit.STATUS_VACANT);

                // 3) Tenant becomes LEFT
                tenantService.markAsLeft(t.getTenantId());

                c.commit();

                // 4) Audit
                audit.log("TENANCY_ENDED", "tenancies", String.valueOf(tenancyId),
                          "Ended on " + moveOutDate + " for unit #" + t.getUnitId());

                log.info("Ended tenancy {} (tenant {} left unit {}) on {}",
                         tenancyId, t.getTenantId(), t.getUnitId(), moveOutDate);

            } catch (RuntimeException e) {
                c.rollback();
                log.error("End tenancy failed — rolled back", e);
                throw new ValidationException("Unable to end the tenancy. Please try again.");

            } finally {
                c.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            log.error("Transaction failed while ending tenancy", e);
            throw new ValidationException("Database error while ending tenancy.");
        }
    }

    /* =================================================================== */

    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
}
