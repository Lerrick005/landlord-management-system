package com.propertymanager.security;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Password hashing and verification using BCrypt.
 *
 * BCrypt automatically:
 *   - salts each password (so identical passwords produce different hashes)
 *   - is intentionally slow (defeats brute-force attacks)
 *   - encodes the salt + cost inside the hash string (so no extra columns needed)
 *
 * The output is a 60-character string like:
 *   $2a$12$XyzAbCdEf... (never store the plain password)
 */
public final class PasswordUtil {

    /** Cost factor — 12 is a good balance of security and speed on modern hardware. */
    private static final int COST = 12;

    private PasswordUtil() {
        // utility class
    }

    /** Hashes a plain password. Store the result in users.password_hash. */
    public static String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(COST));
    }

    /**
     * Verifies a plain password against a stored BCrypt hash.
     * Returns false if anything goes wrong — never throws for bad input.
     */
    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, storedHash);
        } catch (IllegalArgumentException e) {
            // Malformed hash — treat as mismatch, don't crash the app.
            return false;
        }
    }

    /**
     * Returns true if a password meets minimum policy:
     *   - at least 8 characters
     *   - contains at least one letter
     *   - contains at least one digit
     */
    public static boolean meetsPolicy(String plainPassword) {
        if (plainPassword == null || plainPassword.length() < 8) return false;
        boolean hasLetter = plainPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit  = plainPassword.chars().anyMatch(Character::isDigit);
        return hasLetter && hasDigit;
    }
}
