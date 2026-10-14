package com.propertymanager.controller;

import com.propertymanager.model.User;
import com.propertymanager.repository.UserRepository;
import com.propertymanager.security.PasswordUtil;
import com.propertymanager.service.AuditService;
import com.propertymanager.session.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for change-password.fxml.
 *
 * Changes the CURRENTLY LOGGED-IN user's password.
 * Requires the current password to be entered correctly.
 */
public class ChangePasswordController {

    private static final Logger log = LoggerFactory.getLogger(ChangePasswordController.class);

    /* ---- FXML-injected ---- */
    @FXML private Label subHeaderLabel;

    @FXML private PasswordField currentPasswordField;
    @FXML private Label currentPasswordError;

    @FXML private PasswordField newPasswordField;
    @FXML private Label newPasswordError;

    @FXML private PasswordField confirmPasswordField;
    @FXML private Label confirmPasswordError;

    @FXML private Label formError;
    @FXML private Button saveButton;

    /* ---- Services ---- */
    private final UserRepository userRepo = new UserRepository();
    private final AuditService audit = new AuditService();

    /* ---- State ---- */
    private boolean saved = false;

    /* =================================================================== */

    @FXML
    private void initialize() {
        User current = SessionManager.getCurrentUser();
        if (current != null) {
            subHeaderLabel.setText("Change password for " + current.getUsername());
        }

        // Clear errors on typing
        currentPasswordField.textProperty().addListener((o, a, b) -> hideErrors());
        newPasswordField.textProperty().addListener((o, a, b) -> hideErrors());
        confirmPasswordField.textProperty().addListener((o, a, b) -> hideErrors());
    }

    /* =================================================================== */

    /** True if the password was changed successfully. */
    public boolean isSaved() {
        return saved;
    }

    /* =================================================================== */

    @FXML
    private void handleSave() {
        hideErrors();

        User current = SessionManager.getCurrentUser();
        if (current == null) {
            showFormError("No active session. Please log in again.");
            return;
        }

        String currentPw = currentPasswordField.getText();
        String newPw     = newPasswordField.getText();
        String confirmPw = confirmPasswordField.getText();

        // ---------- Field validation ----------
        boolean hasError = false;

        if (currentPw == null || currentPw.isEmpty()) {
            showFieldError(currentPasswordError, "Please enter your current password.");
            hasError = true;
        }

        if (newPw == null || newPw.isEmpty()) {
            showFieldError(newPasswordError, "New password is required.");
            hasError = true;
        } else if (!PasswordUtil.meetsPolicy(newPw)) {
            showFieldError(newPasswordError,
                "Password must be 8+ characters with a letter and a number.");
            hasError = true;
        } else if (newPw.equals(currentPw)) {
            showFieldError(newPasswordError,
                "New password must be different from the current one.");
            hasError = true;
        }

        if (confirmPw == null || !confirmPw.equals(newPw)) {
            showFieldError(confirmPasswordError, "Passwords do not match.");
            hasError = true;
        }

        if (hasError) return;

        // ---------- Verify current password ----------
        if (!PasswordUtil.verify(currentPw, current.getPasswordHash())) {
            showFieldError(currentPasswordError, "Current password is incorrect.");
            return;
        }

        // ---------- Save new password ----------
        try {
            String newHash = PasswordUtil.hash(newPw);
            userRepo.updatePassword(current.getId(), newHash);

            // Update the in-memory user so the session stays consistent
            current.setPasswordHash(newHash);

            audit.log("PASSWORD_CHANGED", "users", current.getUsername(),
                      "User changed their own password");

            log.info("User '{}' changed their password", current.getUsername());

            saved = true;
            closeDialog();

        } catch (RuntimeException e) {
            log.error("Failed to change password", e);
            showFormError("Unable to change the password. Please try again.");
        }
    }

    /* =================================================================== */

    @FXML
    private void handleCancel() {
        saved = false;
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }

    /* =================================================================== */

    private void hideErrors() {
        hide(currentPasswordError);
        hide(newPasswordError);
        hide(confirmPasswordError);
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
}
