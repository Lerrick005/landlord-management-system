package com.propertymanager.repository;

import com.propertymanager.database.Database;
import com.propertymanager.model.Payment;
import com.propertymanager.model.Tenant;
import com.propertymanager.model.Tenancy;
import com.propertymanager.model.Unit;
import com.propertymanager.model.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
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
 * Data access for the "payments" table.
 *
 * Provides:
 *   - insert / read by id, tenant, date range
 *   - sums for balance calculations (voided payments excluded)
 *   - search across tenants, receipt number, and reference
 *   - voiding a payment (never deleted, only reversed)
 */
public class PaymentRepository {

    private static final Logger log = LoggerFactory.getLogger(PaymentRepository.class);

    private static final String BASE_SELECT = """
        SELECT p.id, p.receipt_number, p.tenant_id, p.tenancy_id,
               p.payment_date, p.amount, p.period_year, p.period_month,
               p.method, p.reference_number, p.notes,
               p.recorded_by, p.created_at,
               p.voided, p.voided_at, p.voided_by, p.void_reason,

               t.id           AS t_id,
               t.tenant_number,
               t.full_name,
               t.national_id,
               t.phone,
               t.status       AS t_status,

               u.id           AS u_id,
               u.house_number,
               u.status       AS u_status,

               pr.id          AS pr_id,
               pr.name        AS pr_name,

               usr.username   AS usr_username,
               vusr.username  AS vusr_username
        FROM payments p
        JOIN tenants t    ON t.id = p.tenant_id
        LEFT JOIN tenancies te ON te.id = p.tenancy_id
        LEFT JOIN units u     ON u.id  = te.unit_id
        LEFT JOIN properties pr ON pr.id = u.property_id
        LEFT JOIN users usr   ON usr.id = p.recorded_by
        LEFT JOIN users vusr  ON vusr.id = p.voided_by
        """;

    /* =================================================================== */
    /*  READ                                                               */
    /* =================================================================== */

    public List<Payment> findAll() {
        String sql = BASE_SELECT + " ORDER BY p.payment_date DESC, p.id DESC";
        return queryList(sql, null);
    }

    public Optional<Payment> findById(long id) {
        String sql = BASE_SELECT + " WHERE p.id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findById({}) failed", id, e);
            throw new RuntimeException("Unable to look up payment", e);
        }
    }

    public Optional<Payment> findByReceiptNumber(String receiptNumber) {
        String sql = BASE_SELECT + " WHERE p.receipt_number = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, receiptNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findByReceiptNumber({}) failed", receiptNumber, e);
            throw new RuntimeException("Unable to look up payment", e);
        }
    }

    public List<Payment> findByTenant(long tenantId) {
        String sql = BASE_SELECT + " WHERE p.tenant_id = ? ORDER BY p.payment_date DESC";
        return queryList(sql, ps -> ps.setLong(1, tenantId));
    }

    public List<Payment> findByPeriod(int year, int month) {
        String sql = BASE_SELECT
            + " WHERE p.period_year = ? AND p.period_month = ?"
            + " ORDER BY p.payment_date DESC";
        return queryList(sql, ps -> {
            ps.setInt(1, year);
            ps.setInt(2, month);
        });
    }

    public List<Payment> findByDateRange(LocalDate from, LocalDate to) {
        String sql = BASE_SELECT
            + " WHERE p.payment_date BETWEEN ? AND ?"
            + " ORDER BY p.payment_date DESC";
        return queryList(sql, ps -> {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
        });
    }

    /** Search by tenant name, receipt number, or M-Pesa / bank reference. */
    public List<Payment> search(String query) {
        if (query == null || query.isBlank()) return findAll();

        String sql = BASE_SELECT + """
            WHERE LOWER(t.full_name)         LIKE LOWER(?)
               OR LOWER(t.tenant_number)     LIKE LOWER(?)
               OR LOWER(p.receipt_number)    LIKE LOWER(?)
               OR LOWER(COALESCE(p.reference_number, '')) LIKE LOWER(?)
            ORDER BY p.payment_date DESC, p.id DESC
            """;
        String pattern = "%" + query.trim() + "%";
        return queryList(sql, ps -> {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setString(4, pattern);
        });
    }

    /* =================================================================== */
    /*  SUMS / BALANCES                                                    */
    /* =================================================================== */

    /** Sum of all ACTIVE payments for a tenant for a specific month. */
    public BigDecimal sumForTenantAndPeriod(long tenantId, int year, int month) {
        String sql = """
            SELECT COALESCE(SUM(amount), 0)
            FROM payments
            WHERE tenant_id = ? AND period_year = ? AND period_month = ?
              AND voided = FALSE
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, tenantId);
            ps.setInt(2, year);
            ps.setInt(3, month);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            log.error("sumForTenantAndPeriod({}, {}, {}) failed", tenantId, year, month, e);
            throw new RuntimeException("Unable to compute payments", e);
        }
    }

    /** Total collected in a specific month (all tenants, active only). */
    public BigDecimal sumForPeriod(int year, int month) {
        String sql = """
            SELECT COALESCE(SUM(amount), 0)
            FROM payments
            WHERE period_year = ? AND period_month = ?
              AND voided = FALSE
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, year);
            ps.setInt(2, month);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            log.error("sumForPeriod({}, {}) failed", year, month, e);
            throw new RuntimeException("Unable to compute payments", e);
        }
    }

    /** Total collected in a date range (used by reports, active only). */
    public BigDecimal sumBetweenDates(LocalDate from, LocalDate to) {
        String sql = """
            SELECT COALESCE(SUM(amount), 0)
            FROM payments
            WHERE payment_date BETWEEN ? AND ?
              AND voided = FALSE
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            log.error("sumBetweenDates({}, {}) failed", from, to, e);
            throw new RuntimeException("Unable to compute payments", e);
        }
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM payments";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            log.error("count() failed", e);
            throw new RuntimeException("Unable to count payments", e);
        }
    }

    public boolean receiptNumberExists(String receiptNumber) {
        String sql = "SELECT 1 FROM payments WHERE receipt_number = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, receiptNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("receiptNumberExists({}) failed", receiptNumber, e);
            throw new RuntimeException("Unable to check receipt number", e);
        }
    }

    /* =================================================================== */
    /*  WRITE                                                              */
    /* =================================================================== */

    public long insert(Payment p) {
        String sql = """
            INSERT INTO payments (receipt_number, tenant_id, tenancy_id,
                                  payment_date, amount, period_year, period_month,
                                  method, reference_number, notes, recorded_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, p.getReceiptNumber());
            ps.setLong(2, p.getTenantId());
            if (p.getTenancyId() != null) ps.setLong(3, p.getTenancyId());
            else ps.setNull(3, java.sql.Types.BIGINT);

            ps.setDate(4, Date.valueOf(p.getPaymentDate() != null
                    ? p.getPaymentDate() : LocalDate.now()));
            ps.setBigDecimal(5, p.getAmount());
            ps.setInt(6, p.getPeriodYear());
            ps.setInt(7, p.getPeriodMonth());
            ps.setString(8, p.getMethod());
            ps.setString(9, p.getReferenceNumber());
            ps.setString(10, p.getNotes());

            if (p.getRecordedByUserId() != null) ps.setLong(11, p.getRecordedByUserId());
            else ps.setNull(11, java.sql.Types.BIGINT);

            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("Insert affected 0 rows");

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    p.setId(id);
                    log.info("Inserted payment id={} receipt={} amount={}",
                             id, p.getReceiptNumber(), p.getAmount());
                    return id;
                }
                throw new SQLException("No generated key returned");
            }
        } catch (SQLException e) {
            log.error("insert() failed for receipt={}", p.getReceiptNumber(), e);
            throw new RuntimeException("Unable to save payment", e);
        }
    }

    /**
     * Marks a payment as voided. This is the ONLY mutation we allow on a
     * payment. The row stays in the database — its void metadata captures
     * who reversed it, when, and why.
     */
    public void voidPayment(long paymentId, long voidedByUserId, String reason) {
        String sql = """
            UPDATE payments
               SET voided = TRUE,
                   voided_at = NOW(),
                   voided_by = ?,
                   void_reason = ?
             WHERE id = ? AND voided = FALSE
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, voidedByUserId);
            ps.setString(2, reason);
            ps.setLong(3, paymentId);

            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Payment id " + paymentId
                    + " does not exist or is already voided");
            }
            log.info("Voided payment id={} by user id={}", paymentId, voidedByUserId);
        } catch (SQLException e) {
            log.error("voidPayment({}) failed", paymentId, e);
            throw new RuntimeException(
                "Unable to void the payment. It may already be voided.", e);
        }
    }

    /* =================================================================== */
    /*  INTERNAL                                                           */
    /* =================================================================== */

    @FunctionalInterface
    private interface ParamSetter {
        void set(PreparedStatement ps) throws SQLException;
    }

    private List<Payment> queryList(String sql, ParamSetter setter) {
        List<Payment> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (setter != null) setter.set(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            log.error("Query failed: {}", sql, e);
            throw new RuntimeException("Unable to load payments", e);
        }
    }

    private Payment map(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setId(rs.getLong("id"));
        p.setReceiptNumber(rs.getString("receipt_number"));
        p.setTenantId(rs.getLong("tenant_id"));

        long tenancyIdRaw = rs.getLong("tenancy_id");
        if (!rs.wasNull()) p.setTenancyId(tenancyIdRaw);

        Date pd = rs.getDate("payment_date");
        if (pd != null) p.setPaymentDate(pd.toLocalDate());

        p.setAmount(rs.getBigDecimal("amount"));
        p.setPeriodYear(rs.getInt("period_year"));
        p.setPeriodMonth(rs.getInt("period_month"));
        p.setMethod(rs.getString("method"));
        p.setReferenceNumber(rs.getString("reference_number"));
        p.setNotes(rs.getString("notes"));

        long recBy = rs.getLong("recorded_by");
        if (!rs.wasNull()) p.setRecordedByUserId(recBy);

        Timestamp createdTs = rs.getTimestamp("created_at");
        if (createdTs != null) {
            p.setCreatedAt(createdTs.toInstant().atOffset(ZoneOffset.UTC));
        }

        // -------- tenant --------
        Tenant t = new Tenant();
        t.setId(rs.getLong("t_id"));
        t.setTenantNumber(rs.getString("tenant_number"));
        t.setFullName(rs.getString("full_name"));
        t.setNationalId(rs.getString("national_id"));
        t.setPhone(rs.getString("phone"));
        t.setStatus(rs.getString("t_status"));
        p.setTenant(t);

        // -------- unit + property (only if tenancy is present) --------
        long uId = rs.getLong("u_id");
        if (!rs.wasNull()) {
            Unit u = new Unit();
            u.setId(uId);
            u.setHouseNumber(rs.getString("house_number"));
            u.setStatus(rs.getString("u_status"));

            long prId = rs.getLong("pr_id");
            if (!rs.wasNull()) {
                Property prop = new Property();
                prop.setId(prId);
                prop.setName(rs.getString("pr_name"));
                u.setProperty(prop);
            }

            // Attach the unit to a minimal Tenancy so getUnitLabel() works
            Tenancy te = new Tenancy();
            if (p.getTenancyId() != null) te.setId(p.getTenancyId());
            te.setUnit(u);
            te.setTenant(t);
            p.setTenancy(te);
        }

        // -------- recorded by username --------
        p.setRecordedByUsername(rs.getString("usr_username"));

        // -------- void fields --------
        p.setVoided(rs.getBoolean("voided"));

        Timestamp voidedTs = rs.getTimestamp("voided_at");
        if (voidedTs != null) {
            p.setVoidedAt(voidedTs.toInstant().atOffset(ZoneOffset.UTC));
        }

        long voidedBy = rs.getLong("voided_by");
        if (!rs.wasNull()) p.setVoidedByUserId(voidedBy);

        p.setVoidedByUsername(rs.getString("vusr_username"));
        p.setVoidReason(rs.getString("void_reason"));

        return p;
    }
}
