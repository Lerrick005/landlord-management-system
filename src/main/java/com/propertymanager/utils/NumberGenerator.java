package com.propertymanager.utils;

import com.propertymanager.database.Database;
import com.propertymanager.repository.PropertyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Year;

/**
 * Generates human-readable identifiers for records.
 *
 *   Property code:  SUN-01, GRN-02 ...
 *   Tenant number:  TEN-000124
 *   Receipt number: RCT-2026-001248
 *
 * Tenant and receipt sequences are stored in the system_settings table
 * so they survive application restarts and can be reset by an admin.
 */
public final class NumberGenerator {

    private static final Logger log = LoggerFactory.getLogger(NumberGenerator.class);

    private NumberGenerator() {}

    /* =================================================================== */
    /*  PROPERTY CODE                                                       */
    /* =================================================================== */

    /**
     * Builds a 3-letter prefix from a name and appends a 2-digit sequence.
     * Example: "Sunrise Apartments" -> "SUN-01", next -> "SUN-02".
     */
    public static String generatePropertyCode(String propertyName) {
        if (propertyName == null || propertyName.isBlank()) {
            throw new IllegalArgumentException("Property name required for code generation");
        }

        String prefix = extractPrefix(propertyName);

        // Count how many properties already exist with this prefix
        String sql = "SELECT COUNT(*) FROM properties WHERE code LIKE ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, prefix + "-%");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                int existing = rs.getInt(1);
                return String.format("%s-%02d", prefix, existing + 1);
            }
        } catch (SQLException e) {
            log.error("generatePropertyCode({}) failed", propertyName, e);
            throw new RuntimeException("Unable to generate property code", e);
        }
    }

    /** Extracts up to 3 alphabetic characters from a name, uppercased. */
    private static String extractPrefix(String name) {
        StringBuilder sb = new StringBuilder();
        for (char ch : name.toUpperCase().toCharArray()) {
            if (Character.isLetter(ch)) {
                sb.append(ch);
                if (sb.length() == 3) break;
            }
        }
        // Pad with 'X' if fewer than 3 letters were found
        while (sb.length() < 3) sb.append('X');
        return sb.toString();
    }

    /* =================================================================== */
    /*  TENANT NUMBER                                                       */
    /* =================================================================== */

    /**
     * Produces the next tenant number, e.g. TEN-000125.
     * The counter is stored under "tenant.next_sequence" in system_settings.
     * This method is atomic — safe under concurrent calls.
     */
    public static String nextTenantNumber() {
        String prefix = readSetting("tenant.prefix", "TEN");
        long seq = incrementSequence("tenant.next_sequence");
        return String.format("%s-%06d", prefix, seq);
    }

    /* =================================================================== */
    /*  RECEIPT NUMBER                                                      */
    /* =================================================================== */

    /**
     * Produces the next receipt number, e.g. RCT-2026-000125.
     * The counter is stored under "receipt.next_sequence" in system_settings.
     * The year is embedded in the middle.
     */
    public static String nextReceiptNumber() {
        String prefix = readSetting("receipt.prefix", "RCT");
        long seq = incrementSequence("receipt.next_sequence");
        int year = Year.now().getValue();
        return String.format("%s-%d-%06d", prefix, year, seq);
    }

    /* =================================================================== */
    /*  INTERNALS                                                           */
    /* =================================================================== */

    /**
     * Atomically increments a numeric setting and returns the new value.
     * Uses UPDATE ... RETURNING so concurrent callers never receive the
     * same sequence number.
     */
    private static long incrementSequence(String settingKey) {
        String sql = """
            UPDATE system_settings
               SET value = CAST(CAST(value AS BIGINT) + 1 AS VARCHAR),
                   updated_at = NOW()
             WHERE key = ?
            RETURNING CAST(value AS BIGINT)
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, settingKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
                throw new SQLException("Setting not found: " + settingKey);
            }
        } catch (SQLException e) {
            log.error("incrementSequence({}) failed", settingKey, e);
            throw new RuntimeException("Unable to allocate sequence number", e);
        }
    }

    private static String readSetting(String key, String defaultValue) {
        String sql = "SELECT value FROM system_settings WHERE key = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    return (v != null && !v.isBlank()) ? v : defaultValue;
                }
                return defaultValue;
            }
        } catch (SQLException e) {
            log.warn("readSetting({}) failed — using default '{}'", key, defaultValue, e);
            return defaultValue;
        }
    }

    /* =================================================================== */
    /*  VALIDATION HELPER (used by services to ensure uniqueness)          */
    /* =================================================================== */

    /** True if the given property code is available. */
    public static boolean isPropertyCodeAvailable(String code) {
        return !new PropertyRepository().codeExists(code, 0L);
    }
}
