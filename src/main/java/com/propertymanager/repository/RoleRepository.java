package com.propertymanager.repository;

import com.propertymanager.database.Database;
import com.propertymanager.model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data access for the "roles" table.
 * Roles are seeded by Flyway migration V2 and rarely change.
 */
public class RoleRepository {

    private static final Logger log = LoggerFactory.getLogger(RoleRepository.class);

    private static final String BASE_SELECT =
        "SELECT id, code, name, description FROM roles";

    /** All roles, ordered by id. */
    public List<Role> findAll() {
        List<Role> list = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(BASE_SELECT + " ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
            return list;
        } catch (SQLException e) {
            log.error("findAll() failed", e);
            throw new RuntimeException("Unable to load roles", e);
        }
    }

    /** Look up a role by its code, e.g. "ADMIN". Case-insensitive. */
    public Optional<Role> findByCode(String code) {
        String sql = BASE_SELECT + " WHERE UPPER(code) = UPPER(?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findByCode({}) failed", code, e);
            throw new RuntimeException("Unable to look up role", e);
        }
    }

    /** Look up a role by its numeric id. */
    public Optional<Role> findById(int id) {
        String sql = BASE_SELECT + " WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
                return Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findById({}) failed", id, e);
            throw new RuntimeException("Unable to look up role", e);
        }
    }

    private Role map(ResultSet rs) throws SQLException {
        return new Role(
            rs.getInt("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("description")
        );
    }
}
