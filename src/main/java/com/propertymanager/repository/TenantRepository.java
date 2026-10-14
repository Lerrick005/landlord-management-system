package com.propertymanager.repository;

import com.propertymanager.database.Database;
import com.propertymanager.model.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for the "tenants" table.
 */
public class TenantRepository {

    private static final Logger log = LoggerFactory.getLogger(TenantRepository.class);

    private static final String BASE_SELECT = """
        SELECT id, tenant_number, full_name, national_id, phone, alt_phone,
               email, next_of_kin, next_of_kin_phone, occupation,
               status, registered_at, created_at
        FROM tenants
        """;

    /* =================================================================== */

    public List<Tenant> findAll() {
        String sql = BASE_SELECT + " ORDER BY full_name";
        List<Tenant> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            log.error("findAll() failed", e);
            throw new RuntimeException("Unable to load tenants", e);
        }
    }

    /** Only ACTIVE tenants — current residents. */
    public List<Tenant> findActive() {
        String sql = BASE_SELECT + " WHERE status = 'ACTIVE' ORDER BY full_name";
        List<Tenant> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            log.error("findActive() failed", e);
            throw new RuntimeException("Unable to load active tenants", e);
        }
    }

    /** Tenants without a current tenancy — candidates for assignment. */
    public List<Tenant> findUnassigned() {
        String sql = BASE_SELECT + """
            WHERE status = 'ACTIVE'
              AND id NOT IN (SELECT tenant_id FROM tenancies WHERE status = 'ACTIVE')
            ORDER BY full_name
            """;
        List<Tenant> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            log.error("findUnassigned() failed", e);
            throw new RuntimeException("Unable to load unassigned tenants", e);
        }
    }

    public Optional<Tenant> findById(long id) {
        String sql = BASE_SELECT + " WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findById({}) failed", id, e);
            throw new RuntimeException("Unable to look up tenant", e);
        }
    }

    public Optional<Tenant> findByNumber(String tenantNumber) {
        String sql = BASE_SELECT + " WHERE tenant_number = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenantNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findByNumber({}) failed", tenantNumber, e);
            throw new RuntimeException("Unable to look up tenant", e);
        }
    }

    /**
     * Search across name, tenant number, national id and phone.
     * Empty query returns everyone.
     */
    public List<Tenant> search(String query) {
        if (query == null || query.isBlank()) return findAll();

        String sql = BASE_SELECT + """
            WHERE LOWER(full_name)     LIKE LOWER(?)
               OR LOWER(tenant_number) LIKE LOWER(?)
               OR LOWER(national_id)   LIKE LOWER(?)
               OR phone                LIKE ?
            ORDER BY full_name
            """;
        String pattern = "%" + query.trim() + "%";
        List<Tenant> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setString(4, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            log.error("search({}) failed", query, e);
            throw new RuntimeException("Unable to search tenants", e);
        }
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM tenants";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            log.error("count() failed", e);
            throw new RuntimeException("Unable to count tenants", e);
        }
    }

    /* =================================================================== */

    public long insert(Tenant t) {
        String sql = """
            INSERT INTO tenants (tenant_number, full_name, national_id, phone,
                                 alt_phone, email, next_of_kin, next_of_kin_phone,
                                 occupation, status, registered_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, t.getTenantNumber());
            ps.setString(2, t.getFullName());
            ps.setString(3, t.getNationalId());
            ps.setString(4, t.getPhone());
            ps.setString(5, t.getAltPhone());
            ps.setString(6, t.getEmail());
            ps.setString(7, t.getNextOfKin());
            ps.setString(8, t.getNextOfKinPhone());
            ps.setString(9, t.getOccupation());
            ps.setString(10, t.getStatus() != null ? t.getStatus() : Tenant.STATUS_ACTIVE);
            if (t.getRegisteredAt() != null) {
                ps.setDate(11, Date.valueOf(t.getRegisteredAt()));
            } else {
                ps.setDate(11, Date.valueOf(java.time.LocalDate.now()));
            }

            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("Insert affected 0 rows");

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    t.setId(id);
                    log.info("Inserted tenant id={} number={}", id, t.getTenantNumber());
                    return id;
                }
                throw new SQLException("No generated key returned");
            }
        } catch (SQLException e) {
            log.error("insert() failed for number={}", t.getTenantNumber(), e);
            throw new RuntimeException("Unable to save tenant", e);
        }
    }

    public void update(Tenant t) {
        String sql = """
            UPDATE tenants
               SET full_name = ?, national_id = ?, phone = ?, alt_phone = ?,
                   email = ?, next_of_kin = ?, next_of_kin_phone = ?,
                   occupation = ?, status = ?
             WHERE id = ?
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, t.getFullName());
            ps.setString(2, t.getNationalId());
            ps.setString(3, t.getPhone());
            ps.setString(4, t.getAltPhone());
            ps.setString(5, t.getEmail());
            ps.setString(6, t.getNextOfKin());
            ps.setString(7, t.getNextOfKinPhone());
            ps.setString(8, t.getOccupation());
            ps.setString(9, t.getStatus());
            ps.setLong(10, t.getId());

            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("No tenant with id " + t.getId());
            log.info("Updated tenant id={}", t.getId());
        } catch (SQLException e) {
            log.error("update() failed for id={}", t.getId(), e);
            throw new RuntimeException("Unable to update tenant", e);
        }
    }

    /**
     * Mark a tenant as LEFT. Historical record is preserved.
     */
    public void markAsLeft(long tenantId) {
        String sql = "UPDATE tenants SET status = 'LEFT' WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, tenantId);
            ps.executeUpdate();
            log.info("Tenant id={} marked LEFT", tenantId);
        } catch (SQLException e) {
            log.error("markAsLeft({}) failed", tenantId, e);
            throw new RuntimeException("Unable to mark tenant as left", e);
        }
    }

    public void setActive(long tenantId, boolean active) {
        String sql = "UPDATE tenants SET status = ? WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, active ? Tenant.STATUS_ACTIVE : Tenant.STATUS_LEFT);
            ps.setLong(2, tenantId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("setActive({}, {}) failed", tenantId, active, e);
            throw new RuntimeException("Unable to update tenant status", e);
        }
    }

    /* =================================================================== */

    /**
     * Checks whether a national ID already exists.
     * Pass excludeId to ignore the current row when editing.
     */
    public boolean nationalIdExists(String nationalId, long excludeId) {
        String sql = "SELECT 1 FROM tenants WHERE LOWER(national_id) = LOWER(?) AND id <> ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nationalId);
            ps.setLong(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("nationalIdExists({}) failed", nationalId, e);
            throw new RuntimeException("Unable to check national ID", e);
        }
    }

    public boolean tenantNumberExists(String tenantNumber) {
        String sql = "SELECT 1 FROM tenants WHERE tenant_number = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tenantNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("tenantNumberExists({}) failed", tenantNumber, e);
            throw new RuntimeException("Unable to check tenant number", e);
        }
    }

    /* =================================================================== */

    private Tenant map(ResultSet rs) throws SQLException {
        Tenant t = new Tenant();
        t.setId(rs.getLong("id"));
        t.setTenantNumber(rs.getString("tenant_number"));
        t.setFullName(rs.getString("full_name"));
        t.setNationalId(rs.getString("national_id"));
        t.setPhone(rs.getString("phone"));
        t.setAltPhone(rs.getString("alt_phone"));
        t.setEmail(rs.getString("email"));
        t.setNextOfKin(rs.getString("next_of_kin"));
        t.setNextOfKinPhone(rs.getString("next_of_kin_phone"));
        t.setOccupation(rs.getString("occupation"));
        t.setStatus(rs.getString("status"));

        Date regDate = rs.getDate("registered_at");
        if (regDate != null) t.setRegisteredAt(regDate.toLocalDate());

        Timestamp createdTs = rs.getTimestamp("created_at");
        if (createdTs != null) {
            t.setCreatedAt(createdTs.toInstant().atOffset(ZoneOffset.UTC));
        }
        return t;
    }
}
