package com.propertymanager.repository;

import com.propertymanager.database.Database;
import com.propertymanager.model.Property;
import com.propertymanager.model.Tenant;
import com.propertymanager.model.Tenancy;
import com.propertymanager.model.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for the "tenancies" table.
 *
 * A tenancy links a Tenant to a Unit — it's the "who lives where" record.
 * Historical rows are never deleted; ended tenancies stay with status=ENDED
 * and a move_out_date.
 */
public class TenancyRepository {

    private static final Logger log = LoggerFactory.getLogger(TenancyRepository.class);

    /** Full join so tenant, unit, and property are all populated in one query. */
    private static final String BASE_SELECT = """
        SELECT t.id, t.tenant_id, t.unit_id, t.monthly_rent, t.deposit_amount,
               t.move_in_date, t.move_out_date, t.status, t.created_at,

               tn.id         AS tn_id,
               tn.tenant_number,
               tn.full_name,
               tn.national_id,
               tn.phone,
               tn.alt_phone,
               tn.email,
               tn.next_of_kin,
               tn.next_of_kin_phone,
               tn.occupation,
               tn.status     AS tn_status,
               tn.registered_at,
               tn.created_at AS tn_created_at,

               u.id             AS u_id,
               u.property_id,
               u.house_number,
               u.unit_type,
               u.monthly_rent   AS u_monthly_rent,
               u.deposit_amount AS u_deposit_amount,
               u.status         AS u_status,
               u.notes          AS u_notes,
               u.created_at     AS u_created_at,

               p.id             AS p_id,
               p.code           AS p_code,
               p.name           AS p_name,
               p.location       AS p_location,
               p.status         AS p_status
        FROM tenancies t
        JOIN tenants tn ON tn.id = t.tenant_id
        JOIN units    u  ON u.id  = t.unit_id
        JOIN properties p ON p.id = u.property_id
        """;

    /* =================================================================== */

    public List<Tenancy> findAll() {
        String sql = BASE_SELECT + " ORDER BY t.move_in_date DESC";
        List<Tenancy> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            log.error("findAll() failed", e);
            throw new RuntimeException("Unable to load tenancies", e);
        }
    }

    /** All tenancies that are currently ACTIVE (tenant is living there now). */
    public List<Tenancy> findActive() {
        String sql = BASE_SELECT + " WHERE t.status = 'ACTIVE' ORDER BY t.move_in_date DESC";
        List<Tenancy> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            log.error("findActive() failed", e);
            throw new RuntimeException("Unable to load active tenancies", e);
        }
    }

    public Optional<Tenancy> findById(long id) {
        String sql = BASE_SELECT + " WHERE t.id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findById({}) failed", id, e);
            throw new RuntimeException("Unable to look up tenancy", e);
        }
    }

    /** The ACTIVE tenancy for a given unit, if any. */
    public Optional<Tenancy> findActiveByUnit(long unitId) {
        String sql = BASE_SELECT + " WHERE t.unit_id = ? AND t.status = 'ACTIVE'";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, unitId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findActiveByUnit({}) failed", unitId, e);
            throw new RuntimeException("Unable to look up tenancy for unit", e);
        }
    }

    /** The ACTIVE tenancy for a given tenant, if any. */
    public Optional<Tenancy> findActiveByTenant(long tenantId) {
        String sql = BASE_SELECT + " WHERE t.tenant_id = ? AND t.status = 'ACTIVE'";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findActiveByTenant({}) failed", tenantId, e);
            throw new RuntimeException("Unable to look up tenancy for tenant", e);
        }
    }

    /** All tenancies (active + ended) for a tenant — their rental history. */
    public List<Tenancy> findByTenant(long tenantId) {
        String sql = BASE_SELECT + " WHERE t.tenant_id = ? ORDER BY t.move_in_date DESC";
        List<Tenancy> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            log.error("findByTenant({}) failed", tenantId, e);
            throw new RuntimeException("Unable to load tenant history", e);
        }
    }

    public long countActive() {
        String sql = "SELECT COUNT(*) FROM tenancies WHERE status = 'ACTIVE'";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            log.error("countActive() failed", e);
            throw new RuntimeException("Unable to count active tenancies", e);
        }
    }

    /* =================================================================== */

    public long insert(Tenancy t) {
        String sql = """
            INSERT INTO tenancies (tenant_id, unit_id, monthly_rent, deposit_amount,
                                   move_in_date, status)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, t.getTenantId());
            ps.setLong(2, t.getUnitId());
            ps.setBigDecimal(3, t.getMonthlyRent());
            ps.setBigDecimal(4, t.getDepositAmount());
            ps.setDate(5, Date.valueOf(
                t.getMoveInDate() != null ? t.getMoveInDate() : LocalDate.now()));
            ps.setString(6, t.getStatus() != null ? t.getStatus() : Tenancy.STATUS_ACTIVE);

            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("Insert affected 0 rows");

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    t.setId(id);
                    log.info("Inserted tenancy id={} tenant={} unit={}",
                             id, t.getTenantId(), t.getUnitId());
                    return id;
                }
                throw new SQLException("No generated key returned");
            }
        } catch (SQLException e) {
            log.error("insert() failed for tenant={} unit={}",
                      t.getTenantId(), t.getUnitId(), e);
            throw new RuntimeException(
                "Unable to assign this tenant to a room. "
                + "They may already have an active tenancy, or the room may be taken.",
                e);
        }
    }

    /**
     * Ends a tenancy: sets status=ENDED and records the move-out date.
     */
    public void end(long tenancyId, LocalDate moveOutDate) {
        String sql = """
            UPDATE tenancies
               SET status = 'ENDED', move_out_date = ?
             WHERE id = ? AND status = 'ACTIVE'
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(moveOutDate != null ? moveOutDate : LocalDate.now()));
            ps.setLong(2, tenancyId);

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("No active tenancy with id " + tenancyId);
            }
            log.info("Ended tenancy id={} on {}", tenancyId, moveOutDate);
        } catch (SQLException e) {
            log.error("end({}, {}) failed", tenancyId, moveOutDate, e);
            throw new RuntimeException("Unable to end this tenancy", e);
        }
    }

    /* =================================================================== */

    private Tenancy map(ResultSet rs) throws SQLException {
        Tenancy t = new Tenancy();
        t.setId(rs.getLong("id"));
        t.setTenantId(rs.getLong("tenant_id"));
        t.setUnitId(rs.getLong("unit_id"));
        t.setMonthlyRent(rs.getBigDecimal("monthly_rent"));
        t.setDepositAmount(rs.getBigDecimal("deposit_amount"));

        Date moveIn = rs.getDate("move_in_date");
        if (moveIn != null) t.setMoveInDate(moveIn.toLocalDate());
        Date moveOut = rs.getDate("move_out_date");
        if (moveOut != null) t.setMoveOutDate(moveOut.toLocalDate());

        t.setStatus(rs.getString("status"));

        Timestamp createdTs = rs.getTimestamp("created_at");
        if (createdTs != null) {
            t.setCreatedAt(createdTs.toInstant().atOffset(ZoneOffset.UTC));
        }

        // ---- tenant ----
        Tenant tn = new Tenant();
        tn.setId(rs.getLong("tn_id"));
        tn.setTenantNumber(rs.getString("tenant_number"));
        tn.setFullName(rs.getString("full_name"));
        tn.setNationalId(rs.getString("national_id"));
        tn.setPhone(rs.getString("phone"));
        tn.setAltPhone(rs.getString("alt_phone"));
        tn.setEmail(rs.getString("email"));
        tn.setNextOfKin(rs.getString("next_of_kin"));
        tn.setNextOfKinPhone(rs.getString("next_of_kin_phone"));
        tn.setOccupation(rs.getString("occupation"));
        tn.setStatus(rs.getString("tn_status"));
        Date tnReg = rs.getDate("registered_at");
        if (tnReg != null) tn.setRegisteredAt(tnReg.toLocalDate());
        Timestamp tnCreated = rs.getTimestamp("tn_created_at");
        if (tnCreated != null) {
            tn.setCreatedAt(tnCreated.toInstant().atOffset(ZoneOffset.UTC));
        }
        t.setTenant(tn);

        // ---- unit ----
        Unit u = new Unit();
        u.setId(rs.getLong("u_id"));
        u.setPropertyId(rs.getLong("property_id"));
        u.setHouseNumber(rs.getString("house_number"));
        u.setUnitType(rs.getString("unit_type"));
        u.setMonthlyRent(rs.getBigDecimal("u_monthly_rent"));
        u.setDepositAmount(rs.getBigDecimal("u_deposit_amount"));
        u.setStatus(rs.getString("u_status"));
        u.setNotes(rs.getString("u_notes"));
        Timestamp uCreated = rs.getTimestamp("u_created_at");
        if (uCreated != null) {
            u.setCreatedAt(uCreated.toInstant().atOffset(ZoneOffset.UTC));
        }

        // ---- property (inside the unit) ----
        Property p = new Property();
        p.setId(rs.getLong("p_id"));
        p.setCode(rs.getString("p_code"));
        p.setName(rs.getString("p_name"));
        p.setLocation(rs.getString("p_location"));
        p.setStatus(rs.getString("p_status"));
        u.setProperty(p);

        t.setUnit(u);

        return t;
    }
}
