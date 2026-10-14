package com.propertymanager.controller;

import com.propertymanager.database.Database;
import com.propertymanager.model.Role;
import com.propertymanager.model.User;
import com.propertymanager.repository.RoleRepository;
import com.propertymanager.repository.UserRepository;
import com.propertymanager.security.PasswordUtil;
import com.propertymanager.service.AuditService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for setup-wizard.fxml.
 *
 * Runs only on first launch (when there are zero users in the database).
 * Creates the first ADMIN account and saves initial organization settings.
 */
public class SetupWizardController {

    private static final Logger log = LoggerFactory.getLogger(SetupWizardController.class);

    /* ---- FXML-injected ---- */
    @FXML private TextField adminFullNameField;
    @FXML private Label adminFullNameError;
    @FXML private TextField adminUsernameField;
    @FXML private Label adminUsernameError;
    @FXML private PasswordField adminPasswordField;
    @FXML private Label adminPasswordError;
    @FXML private PasswordField adminConfirmField;
    @FXML private Label adminConfirmError;

    @FXML private TextField orgNameField;
    @FXML private Label orgNameError;
    @FXML private TextField orgAddressField;
    @FXML private TextField orgPhoneField;
    @FXML private TextField orgEmailField;
    @FXML private TextField currencyCodeField;
    @FXML private TextField currencySymbolField;

    @FXML private Label formError;
    @FXML private Button completeButton;

    /* ---- Services ---- */
    private final UserRepository userRepo = new UserRepository();
    private final RoleRepository roleRepo = new RoleRepository();
    private final AuditService audit = new AuditService();

    /* ---- State ---- */
    private boolean completed = false;

    /* =================================================================== */

    @FXML
    private void initialize() {
        // Pre-fill sensible defaults
        currencyCodeField.setText("KES");
        currencySymbolField.setText("KSh");
        orgNameField.setPromptText("e.g. Green Eden Hostel");

        // Clear errors as the user types
        adminFullNameField.textProperty().addListener((o, a, b) -> hideErrors());
        adminUsernameField.textProperty().addListener((o, a, b) -> hideErrors());
        adminPasswordField.textProperty().addListener((o, a, b) -> hideErrors());
        adminConfirmField.textProperty().addListener((o, a, b) -> hideErrors());
        orgNameField.textProperty().addListener((o, a, b) -> hideErrors());
    }

    /* =================================================================== */

    /** True if setup completed successfully. Main.java reads this. */
    public boolean isCompleted() {
        return completed;
    }

    /* =================================================================== */

    @FXML
    private void handleComplete() {
        hideErrors();

        // ---------- Read inputs ----------
        String adminName  = trimOrNull(adminFullNameField.getText());
        String adminUser  = trimOrNull(adminUsernameField.getText());
        String adminPass  = adminPasswordField.getText();
        String adminConf  = adminConfirmField.getText();

        String orgName    = trimOrNull(orgNameField.getText());
        String orgAddr    = trimOrEmpty(orgAddressField.getText());
        String orgPhone   = trimOrEmpty(orgPhoneField.getText());
        String orgEmail   = trimOrEmpty(orgEmailField.getText());
        String currCode   = trimOrNull(currencyCodeField.getText());
        String currSymbol = trimOrNull(currencySymbolField.getText());

        // ---------- Validate ----------
        boolean hasError = false;

        if (adminName == null) {
            showFieldError(adminFullNameError, "Full name is required.");
            hasError = true;
        }

        if (adminUser == null) {
            showFieldError(adminUsernameError, "Username is required.");
            hasError = true;
        } else if (adminUser.length() < 3) {
            showFieldError(adminUsernameError, "Username must be at least 3 characters.");
            hasError = true;
        } else if (adminUser.contains(" ")) {
            showFieldError(adminUsernameError, "Username cannot contain spaces.");
            hasError = true;
        }

        if (adminPass == null || adminPass.isEmpty()) {
            showFieldError(adminPasswordError, "Password is required.");
            hasError = true;
        } else if (!adminPass.equals(adminConf)) {
            showFieldError(adminConfirmError, "Passwords do not match.");
            hasError = true;
        } else if (!PasswordUtil.meetsPolicy(adminPass)) {
            showFieldError(adminPasswordError,
                "Password must be 8+ characters with a letter and a number.");
            hasError = true;
        }

        if (orgName == null) {
            showFieldError(orgNameError, "Organization name is required.");
            hasError = true;
        }

        if (currCode == null) {
            showFormError("Currency code is required.");
            hasError = true;
        }
        if (currSymbol == null) {
            showFormError("Currency symbol is required.");
            hasError = true;
        }

        if (!orgEmail.isEmpty() && !orgEmail.contains("@")) {
            showFormError("Email address looks invalid.");
            hasError = true;
        }

        if (hasError) return;

        // ---------- Persist admin + settings ----------
        try {
            // Find the ADMIN role id
            Role adminRole = roleRepo.findByCode("ADMIN")
                .orElseThrow(() -> new IllegalStateException(
                    "ADMIN role not found — was the database seeded?"));

            // Create the admin user
            User admin = new User();
            admin.setUsername(adminUser);
            admin.setFullName(adminName);
            admin.setPasswordHash(PasswordUtil.hash(adminPass));
            admin.setRoleId(adminRole.getId());
            admin.setRole(adminRole);
            admin.setActive(true);

            userRepo.insert(admin);
            log.info("First-run setup: created admin user '{}'", adminUser);

            // Save organization settings (only set keys that already exist)
            Map<String, String> settings = new HashMap<>();
            settings.put("org.name",            orgName);
            settings.put("org.address",         orgAddr);
            settings.put("org.phone",           orgPhone);
            settings.put("org.email",           orgEmail);
            settings.put("org.currency",        currCode);
            settings.put("org.currency_symbol", currSymbol);
            settings.put("setup.complete",      "true");
            writeSettings(settings);

            // Audit entry
            audit.log("SETUP_COMPLETED", "system_settings", null,
                      "First-run setup completed by " + adminUser);

            completed = true;
            closeDialog();

        } catch (RuntimeException e) {
            log.error("Setup failed", e);
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.toLowerCase().contains("unique") || msg.toLowerCase().contains("duplicate")) {
                showFormError("That username is already taken. Please choose another.");
            } else {
                showFormError("Setup failed. Please check the details and try again.");
            }
        }
    }

    /* =================================================================== */

    private void writeSettings(Map<String, String> updates) {
        // UPDATE only — the keys already exist (seeded by V2 migration)
        String sql = """
            UPDATE system_settings
               SET value = ?, updated_at = NOW()
             WHERE key = ?
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (Map.Entry<String, String> e : updates.entrySet()) {
                ps.setString(1, e.getValue());
                ps.setString(2, e.getKey());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            log.error("writeSettings failed", e);
            throw new RuntimeException("Unable to save settings", e);
        }
    }

    /* =================================================================== */

    private void closeDialog() {
        Stage s = (Stage) completeButton.getScene().getWindow();
        s.close();
    }

    /* =================================================================== */

    private void hideErrors() {
        hide(adminFullNameError);
        hide(adminUsernameError);
        hide(adminPasswordError);
        hide(adminConfirmError);
        hide(orgNameError);
        hide(formError);
    }

    private void hide(Label l) {
        l.setVisible(false);
        l.setManaged(false);
    }

    private void showFieldError(Label label, String message) {
        label.setText(message);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void showFormError(String message) {
        formError.setText(message);
        formError.setVisible(true);
        formError.setManaged(true);
    }

    private String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
