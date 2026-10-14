package com.propertymanager.service;

import com.propertymanager.model.User;
import com.propertymanager.repository.UserRepository;
import com.propertymanager.security.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Authentication business logic.
 *
 * Responsibilities:
 *   - Look up a user by username
 *   - Verify the password against the BCrypt hash
 *   - Reject inactive accounts
 *   - Return a clean result the UI can act on
 *
 * Never exposes technical errors to the UI — those go to logs.
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;

    public AuthService() {
        this.userRepository = new UserRepository();
    }

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Attempt to log in.
     *
     * @param username the entered username (may be null/blank)
     * @param plainPassword the entered plain password (may be null/blank)
     * @return LoginResult — either success(User) or failure(message)
     */
    public LoginResult login(String username, String plainPassword) {

        // ---------- Basic input validation ----------
        if (username == null || username.isBlank()) {
            return LoginResult.fail("Please enter your username.");
        }
        if (plainPassword == null || plainPassword.isEmpty()) {
            return LoginResult.fail("Please enter your password.");
        }

        String trimmedUsername = username.trim();

        // ---------- Find the user ----------
        Optional<User> found;
        try {
            found = userRepository.findByUsername(trimmedUsername);
        } catch (RuntimeException e) {
            log.error("Login failed due to repository error for username={}", trimmedUsername, e);
            return LoginResult.fail("Unable to sign you in right now. Please try again.");
        }

        if (found.isEmpty()) {
            // Same generic message whether the username exists or not — don't leak which.
            log.info("Login attempt for unknown username='{}'", trimmedUsername);
            return LoginResult.fail("Incorrect username or password.");
        }

        User user = found.get();

        // ---------- Check account status ----------
        if (!user.isActive()) {
            log.info("Login denied — inactive account username='{}'", trimmedUsername);
            return LoginResult.fail("This account is disabled. Please contact an administrator.");
        }

        // ---------- Verify password ----------
        if (!PasswordUtil.verify(plainPassword, user.getPasswordHash())) {
            log.info("Login denied — wrong password username='{}'", trimmedUsername);
            return LoginResult.fail("Incorrect username or password.");
        }

        // ---------- Success ----------
        try {
            userRepository.touchLastLogin(user.getId());
        } catch (RuntimeException ignored) {
            // non-fatal — already logged inside the repository
        }

        log.info("Login success username='{}' role='{}'", user.getUsername(), user.getRoleCode());
        return LoginResult.success(user);
    }

    /* ====================================================================== */
    /*  Result type                                                           */
    /* ====================================================================== */

    /**
     * Outcome of a login attempt.
     * Either holds a User (success) or a message (failure).
     */
    public static final class LoginResult {

        private final boolean success;
        private final User user;
        private final String message;

        private LoginResult(boolean success, User user, String message) {
            this.success = success;
            this.user = user;
            this.message = message;
        }

        public static LoginResult success(User user) {
            return new LoginResult(true, user, null);
        }

        public static LoginResult fail(String message) {
            return new LoginResult(false, null, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public User getUser() {
            return user;
        }

        public String getMessage() {
            return message;
        }
    }
}
