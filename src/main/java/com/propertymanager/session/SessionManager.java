package com.propertymanager.session;

import com.propertymanager.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the currently logged-in user for the lifetime of the application.
 *
 * This is an in-memory, single-instance session. When the app exits,
 * the session is cleared. It is never persisted to disk.
 *
 * Used by:
 *   - The main window to display "Welcome, John" in the header
 *   - Role-based authorization checks (e.g. canOpenUsersScreen())
 *   - The audit logger, to record which user performed an action
 */
public final class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    private static User currentUser;

    private SessionManager() {
        // utility class
    }

    /** Call immediately after a successful login. */
    public static synchronized void login(User user) {
        if (user == null) {
            throw new IllegalArgumentException("Cannot start a session with a null user.");
        }
        currentUser = user;
        log.info("Session started for user '{}' (role={})",
                 user.getUsername(), user.getRoleCode());
    }

    /** Call when the user chooses Logout, or on application shutdown. */
    public static synchronized void logout() {
        if (currentUser != null) {
            log.info("Session ended for user '{}'", currentUser.getUsername());
        }
        currentUser = null;
    }

    /** Returns the current user, or null if nobody is logged in. */
    public static synchronized User getCurrentUser() {
        return currentUser;
    }

    /** True if someone is logged in. */
    public static synchronized boolean isLoggedIn() {
        return currentUser != null;
    }

    /* ====================================================================== */
    /*  Convenience permission checks — used by the main shell and menus      */
    /* ====================================================================== */

    public static boolean isAdmin() {
        return currentUser != null && currentUser.hasRole("ADMIN");
    }

    public static boolean isManager() {
        return currentUser != null && currentUser.hasRole("MANAGER");
    }

    public static boolean isAccountant() {
        return currentUser != null && currentUser.hasRole("ACCOUNTANT");
    }

    /** Admin or Manager — can manage properties, units, tenants. */
    public static boolean canManageProperties() {
        return isAdmin() || isManager();
    }

    /** Anyone logged in can record payments (spec §3). */
    public static boolean canRecordPayments() {
        return isLoggedIn();
    }

    /** Only admins manage users, backups, and system configuration. */
    public static boolean canManageUsers() {
        return isAdmin();
    }

    public static boolean canManageBackups() {
        return isAdmin();
    }

    public static boolean canViewAuditLogs() {
        return isAdmin();
    }

    public static boolean canConfigureSystem() {
        return isAdmin();
    }
}
