package com.propertymanager.controller;

import com.propertymanager.model.Role;
import com.propertymanager.model.User;
import com.propertymanager.repository.RoleRepository;
import com.propertymanager.repository.UserRepository;
import com.propertymanager.security.PasswordUtil;
import com.propertymanager.service.AuditService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Controller for user-form.fxml.
 *
 * Two modes:
 *   setUser(null) -> create mode (password fields visible)
 *   setUser(u)    -> edit mode   (password fields hidden; reset via list action)
 */
public class UserFormController {

    private static final Logger log = LoggerFactory.getLogger(UserFormController.class);

    /* ---- FXML-injected ---- */
    @FXML private Label headerLabel;
    @FXML private Label subHeaderLabel;

    @FXML private TextField usernameField;
    @FXML private Label usernameError;

    @FXML private TextField fullNameField;
    @FXML private Label fullNameError;

    @FXML private ComboBox<Role> roleCombo;
    @FXML private Label roleError;

    @FXML private VBox passwordSection;
    @FXML private PasswordField passwordField;
    @FXML private Label passwordError;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label confirmPasswordError;

    @FXML private Label formError;
    @FXML private Button saveButton;

    /* ---- Services ---- */
    private final UserRepository userRepo = new UserRepository();
    private final RoleRepository roleRepo = new RoleRepository();
    private final AuditService audit = new AuditService();

    /* ---- State ---- */
    private User user;      // null = create mode
    private boolean saved = false;

    /* =================================================================== */

    @FXML
    private void initialize() {
        // Role combo shows the full role name
        roleCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Role r) { return r == null ? "" : r.getName(); }
            @Override public Role fromString(String s) { return null; }
        });

        // Load roles
        try {
            List<Role> roles = roleRepo.findAll();
            roleCombo.setItems(FXCollections.observableArrayList(roles));
        } catch (RuntimeException e) {
            log.error("Failed to load roles", e);
            showFormError("Unable to load roles. Please try again.");
        }

        // Clear errors on typing
        usernameField.textProperty().addListener((o, a, b) -> hideErrors());
        fullNameField.textProperty().addListener((o, a, b) -> hideErrors());
        passwordField.textProperty().addListener((o, a, b) -> hideErrors());
        confirmPasswordField.textProperty().addListener((o, a, b) -> hideErrors());
    }

    /* =================================================================== */

    /** Called by the caller BEFORE the dialog is shown. null = create mode. */
    public void setUser(User user) {
        this.user = user;

        if (user == null) {
            // -------- Create mode --------
            headerLabel.setText("Add User");
            subHeaderLabel.setText("Create a new account for this system");
            saveButton.setText("Add User");

            passwordSection.setVisible(true);
            passwordSection.setManaged(true);

            // Default to Property Manager — the safest non-admin role
            selectDefaultRole();
            return;
        }

        // -------- Edit mode --------
        headerLabel.setText("Edit User");
        subHeaderLabel.setText(user.getUsername() + "  ·  "
            + (user.getFullName() != null ? user.getFullName() : ""));
        saveButton.setText("Save Changes");

        // Password section hidden — reset happens from the list
        passwordSection.setVisible(false);
        passwordSection.setManaged(false);

        usernameField.setText(nz(user.getUsername()));
        fullNameField.setText(nz(user.getFullName()));

        // Select this user's role
        if (user.getRole() != null) {
            for (Role r : roleCombo.getItems()) {
                if (r.getId() == user.getRole().getId()) {
                    roleCombo.getSelectionModel().select(r);
                    break;
                }
            }
        }
    }

    /** True if the user saved successfully. */
    public boolean isSaved() {
        return saved;
    }

    /* =================================================================== */

    private void selectDefaultRole() {
        for (Role r : roleCombo.getItems()) {
            if ("MANAGER".equalsIgnoreCase(r.getCode())) {
                roleCombo.getSelectionModel().select(r);
                return;
            }
        }
        // fallback
        if (!roleCombo.getItems().isEmpty()) {
            roleCombo.getSelectionModel().selectFirst();
        }
    }

    /* =================================================================== */

    @FXML
    private void handleSave() {
        hideErrors();

        String username = trimOrNull(usernameField.getText());
        String fullName = trimOrNull(fullNameField.getText());
        Role   role     = roleCombo.getValue();

        boolean isCreate = (user == null);

        // ---------- Field validation ----------
        boolean hasError = false;

        if (username == null) {
            showFieldError(usernameError, "Username is required.");
            hasError = true;
        } else if (username.length() < 3) {
            showFieldError(usernameError, "Username must be at least 3 characters.");
            hasError = true;
        } else if (username.contains(" ")) {
            showFieldError(usernameError, "Username cannot contain spaces.");
            hasError = true;
        }

        if (fullName == null) {
            showFieldError(fullNameError, "Full name is required.");
            hasError = true;
        }

        if (role == null) {
            showFieldError(roleError, "Please select a role.");
            hasError = true;
        }

        String password = null;
        if (isCreate) {
            password = passwordField.getText();
            String confirm = confirmPasswordField.getText();

            if (password == null || password.isEmpty()) {
                showFieldError(passwordError, "Password is required.");
                hasError = true;
            } else if (!password.equals(confirm)) {
                showFieldError(confirmPasswordError, "Passwords do not match.");
                hasError = true;
            } else if (!PasswordUtil.meetsPolicy(password)) {
                showFieldError(passwordError,
                    "Password must be 8+ characters with a letter and a number.");
                hasError = true;
            }
        }

        if (hasError) return;

        // ---------- Build User ----------
        User u = isCreate ? new User() : user;
        u.setUsername(username);
        u.setFullName(fullName);
        u.setRoleId(role.getId());
        u.setRole(role);
        if (isCreate) {
            u.setActive(true);
            u.setPasswordHash(PasswordUtil.hash(password));
        }

        // ---------- Persist ----------
        try {
            if (isCreate) {
                userRepo.insert(u);
                audit.log("USER_CREATED", "users", u.getUsername(),
                          u.getFullName() + " (" + role.getCode() + ")");
                log.info("Created user '{}' with role {}", u.getUsername(), role.getCode());
            } else {
                userRepo.update(u);
                audit.log("USER_UPDATED", "users", u.getUsername(), u.getFullName());
                log.info("Updated user '{}'", u.getUsername());
            }
            saved = true;
            closeDialog();

        } catch (RuntimeException re) {
            log.error("Failed to save user", re);
            String msg = re.getMessage() != null ? re.getMessage() : "";
            if (msg.toLowerCase().contains("unique") || msg.toLowerCase().contains("duplicate")) {
                showFormError("That username is already taken. Please choose a different one.");
            } else {
                showFormError("Unable to save the user. Please try again.");
            }
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
        hide(usernameError);
        hide(fullNameError);
        hide(roleError);
        hide(passwordError);
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

    private String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String nz(String s) { return s == null ? "" : s; }
}
