package com.propertymanager.repository;

import com.propertymanager.database.Database;
import com.propertymanager.model.Property;
import com.propertymanager.model.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
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
 * Data access for the "units" table.
 */
public class UnitRepository {

    private static final Logger log = LoggerFactory.getLogger(UnitRepository.class);

    /** Joins the parent property so callers get property name without a second query. */
    private static final String BASE_SELECT = """
        SELECT u.id, u.property_id, u.house_number, u.unit_type,
               u.monthly_rent, u.deposit_amount, u.status, u.notes,
               u.created_at,
               p.id AS p_id, p.code AS p_code, p.name AS p_name,
               p.location AS p_location, p.status AS p_status
        FROM units u
        JOIN properties p ON p.id = u.property_id
        """;

    /* =================================================================== */

    public List<Unit> findAll() {
        String sql = BASE_SELECT + " ORDER BY p.name, u.house_number";
        List<Unit> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            log.error("findAll() failed", e);
            throw new RuntimeException("Unable to load units", e);
        }
    }

    /** All units belonging to a specific property. */
    public List<Unit> findByProperty(long propertyId) {
        String sql = BASE_SELECT + " WHERE u.property_id = ? ORDER BY u.house_number";
        List<Unit> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, propertyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            log.error("findByProperty({}) failed", propertyId, e);
            throw new RuntimeException("Unable to load units for property", e);
        }
    }

    /** Vacant units — used by the dedicated vacant-units screen (spec §19). */
    public List<Unit> findVacant() {
        String sql = BASE_SELECT + " WHERE u.status = 'VACANT' ORDER BY p.name, u.house_number";
        List<Unit> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            log.error("findVacant() failed", e);
            throw new RuntimeException("Unable to load vacant units", e);
        }
    }

    public Optional<Unit> findById(long id) {
        String sql = BASE_SELECT + " WHERE u.id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findById({}) failed", id, e);
            throw new RuntimeException("Unable to look up unit", e);
        }
    }

    /** Case-insensitive search by house number, filtered optionally by property. */
    public List<Unit> search(String query, Long propertyId) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        List<Object> params = new ArrayList<>();

        boolean hasQuery = query != null && !query.isBlank();
        boolean hasProperty = propertyId != null;

        if (hasQuery || hasProperty) {
            sql.append(" WHERE ");
            if (hasQuery) {
                sql.append("LOWER(u.house_number) LIKE LOWER(?)");
                params.add("%" + query.trim() + "%");
                if (hasProperty) sql.append(" AND ");
            }
            if (hasProperty) {
                sql.append("u.property_id = ?");
                params.add(propertyId);
            }
        }
        sql.append(" ORDER BY p.name, u.house_number");

        List<Unit> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            log.error("search({}, {}) failed", query, propertyId, e);
            throw new RuntimeException("Unable to search units", e);
        }
    }

    /** Count units by status — for dashboard stat cards. */
    public long countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM units WHERE status = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.error("countByStatus({}) failed", status, e);
            throw new RuntimeException("Unable to count units", e);
        }
    }

    /** Total units across all properties. */
    public long count() {
        String sql = "SELECT COUNT(*) FROM units";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            log.error("count() failed", e);
            throw new RuntimeException("Unable to count units", e);
        }
    }

    /** Sum of monthly_rent for all units in a given status. Used by the dashboard. */
    public BigDecimal sumMonthlyRentByStatus(String status) {
        String sql = "SELECT COALESCE(SUM(monthly_rent), 0) FROM units WHERE status = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        } catch (SQLException e) {
            log.error("sumMonthlyRentByStatus({}) failed", status, e);
            throw new RuntimeException("Unable to sum rent", e);
        }
    }

    /* =================================================================== */

    public long insert(Unit u) {
        String sql = """
            INSERT INTO units (property_id, house_number, unit_type,
                               monthly_rent, deposit_amount, status, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, u.getPropertyId());
            ps.setString(2, u.getHouseNumber());
            ps.setString(3, u.getUnitType());
            ps.setBigDecimal(4, u.getMonthlyRent());
            ps.setBigDecimal(5, u.getDepositAmount());
            ps.setString(6, u.getStatus() != null ? u.getStatus() : Unit.STATUS_VACANT);
            ps.setString(7, u.getNotes());

            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("Insert affected 0 rows");

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    u.setId(id);
                    log.info("Inserted unit id={} property={} house={}",
                             id, u.getPropertyId(), u.getHouseNumber());
                    return id;
                }
                throw new SQLException("No generated key returned");
            }
        } catch (SQLException e) {
            log.error("insert() failed for house={}", u.getHouseNumber(), e);
            throw new RuntimeException("Unable to save unit", e);
        }
    }

    public void update(Unit u) {
        String sql = """
            UPDATE units
               SET property_id = ?, house_number = ?, unit_type = ?,
                   monthly_rent = ?, deposit_amount = ?, status = ?, notes = ?
             WHERE id = ?
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, u.getPropertyId());
            ps.setString(2, u.getHouseNumber());
            ps.setString(3, u.getUnitType());
            ps.setBigDecimal(4, u.getMonthlyRent());
            ps.setBigDecimal(5, u.getDepositAmount());
            ps.setString(6, u.getStatus());
            ps.setString(7, u.getNotes());
            ps.setLong(8, u.getId());

            int rows = ps.executeUpdate();
            if (rows == 0) throw new SQLException("No unit with id " + u.getId());
            log.info("Updated unit id={}", u.getId());
        } catch (SQLException e) {
            log.error("update() failed for id={}", u.getId(), e);
            throw new RuntimeException("Unable to update unit", e);
        }
    }

    public void updateStatus(long unitId, String status) {
        String sql = "UPDATE units SET status = ? WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, unitId);
            ps.executeUpdate();
            log.info("Unit id={} status -> {}", unitId, status);
        } catch (SQLException e) {
            log.error("updateStatus({}, {}) failed", unitId, status, e);
            throw new RuntimeException("Unable to update unit status", e);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM units WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
            log.info("Deleted unit id={}", id);
        } catch (SQLException e) {
            log.error("delete() failed for id={}", id, e);
            throw new RuntimeException(
                "Unable to delete unit. It may still have a tenancy attached.", e);
        }
    }

    /**
     * Checks whether a house number already exists in a property.
     * Pass excludeId to ignore the current row when editing.
     */
    public boolean houseNumberExists(long propertyId, String houseNumber, long excludeId) {
        String sql = """
            SELECT 1 FROM units
            WHERE property_id = ? AND LOWER(house_number) = LOWER(?) AND id <> ?
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, propertyId);
            ps.setString(2, houseNumber);
            ps.setLong(3, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("houseNumberExists({}, {}) failed", propertyId, houseNumber, e);
            throw new RuntimeException("Unable to check house number", e);
        }
    }

    /* =================================================================== */

    private Unit map(ResultSet rs) throws SQLException {
        Unit u = new Unit();
        u.setId(rs.getLong("id"));
        u.setPropertyId(rs.getLong("property_id"));
        u.setHouseNumber(rs.getString("house_number"));
        u.setUnitType(rs.getString("unit_type"));
        u.setMonthlyRent(rs.getBigDecimal("monthly_rent"));
        u.setDepositAmount(rs.getBigDecimal("deposit_amount"));
        u.setStatus(rs.getString("status"));
        u.setNotes(rs.getString("notes"));

        Timestamp createdTs = rs.getTimestamp("created_at");
        if (createdTs != null) {
            u.setCreatedAt(createdTs.toInstant().atOffset(ZoneOffset.UTC));
        }

        Property p = new Property();
        p.setId(rs.getLong("p_id"));
        p.setCode(rs.getString("p_code"));
        p.setName(rs.getString("p_name"));
        p.setLocation(rs.getString("p_location"));
        p.setStatus(rs.getString("p_status"));
        u.setProperty(p);

        return u;
    }
}
