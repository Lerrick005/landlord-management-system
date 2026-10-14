package com.propertymanager.controller;

import com.propertymanager.model.Property;
import com.propertymanager.model.Tenant;
import com.propertymanager.model.Unit;
import com.propertymanager.service.PropertyService;
import com.propertymanager.service.TenancyService;
import com.propertymanager.service.TenantService;
import com.propertymanager.service.TenantService.ValidationException;
import com.propertymanager.service.UnitService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Controller for tenant-form.fxml.
 *
 * Used for both "Register Tenant" and "Edit Tenant".
 * setTenant(null) -> create mode (with optional room assignment).
 * setTenant(t)    -> edit mode (assignment section hidden).
 */
public class TenantFormController {

    private static final Logger log = LoggerFactory.getLogger(TenantFormController.class);

    /* ---- FXML-injected: tenant details ---- */
    @FXML private Label headerLabel;
    @FXML private Label subHeaderLabel;

    @FXML private TextField fullNameField;
    @FXML private Label fullNameError;

    @FXML private TextField nationalIdField;
    @FXML private Label nationalIdError;

    @FXML private TextField phoneField;
    @FXML private Label phoneError;

    @FXML private TextField altPhoneField;

    @FXML private TextField emailField;
    @FXML private Label emailError;

    @FXML private TextField nextOfKinField;
    @FXML private TextField nextOfKinPhoneField;

    @FXML private TextField occupationField;

    @FXML private ComboBox<String> statusCombo;

    /* ---- FXML-injected: assign room section ---- */
    @FXML private VBox assignRoomSection;
    @FXML private ComboBox<Property> propertyCombo;
    @FXML private ComboBox<Unit> roomCombo;
    @FXML private Label roomHintLabel;
    @FXML private DatePicker moveInDatePicker;
    @FXML private TextField rentField;
    @FXML private Label rentError;
    @FXML private TextField depositField;

    @FXML private Label formError;
    @FXML private Button saveButton;

    /* ---- Services ---- */
    private final TenantService tenantService = new TenantService();
    private final PropertyService propertyService = new PropertyService();
    private final UnitService unitService = new UnitService();
    private final TenancyService tenancyService = new TenancyService();

    /* ---- State ---- */
    private Tenant tenant;         // null = create mode
    private boolean saved = false;

    /* =================================================================== */

    @FXML
    private void initialize() {
        statusCombo.setItems(FXCollections.observableArrayList(
            Tenant.STATUS_ACTIVE, Tenant.STATUS_LEFT));
        statusCombo.getSelectionModel().select(Tenant.STATUS_ACTIVE);

        // Property combo display
        propertyCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Property p) { return p == null ? "" : p.getName(); }
            @Override public Property fromString(String s) { return null; }
        });

        // Room combo display
        roomCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Unit u) {
                if (u == null) return "";
                return u.getHouseNumber() + " — " + u.getUnitTypeDisplay()
                    + " — " + formatMoney(u.getMonthlyRent());
            }
            @Override public Unit fromString(String s) { return null; }
        });

        // Move-in date default
        moveInDatePicker.setValue(LocalDate.now());

        // When property changes, load vacant rooms
        propertyCombo.valueProperty().addListener((o, a, b) -> loadRooms());

        // When room changes, auto-fill rent & deposit
        roomCombo.valueProperty().addListener((o, a, b) -> onRoomSelected());

        // Clear errors on typing
        fullNameField.textProperty().addListener((o, a, b) -> hideErrors());
        nationalIdField.textProperty().addListener((o, a, b) -> hideErrors());
        phoneField.textProperty().addListener((o, a, b) -> hideErrors());
        emailField.textProperty().addListener((o, a, b) -> hideErrors());
        rentField.textProperty().addListener((o, a, b) -> hideErrors());
    }

    /* =================================================================== */

    /** Called by the caller BEFORE the dialog is shown. null = create mode. */
    public void setTenant(Tenant tenant) {
        this.tenant = tenant;

        if (tenant == null) {
            // -------- Create mode --------
            headerLabel.setText("Register Tenant");
            subHeaderLabel.setText("Enter the tenant's details below. "
                + "Optionally assign a room at the same time.");
            saveButton.setText("Register Tenant");

            // Load properties for the assign section
            loadProperties();
            return;
        }

        // -------- Edit mode --------
        headerLabel.setText("Edit Tenant");
        subHeaderLabel.setText(tenant.getTenantNumber() + " · "
            + (tenant.getFullName() != null ? tenant.getFullName() : ""));
        saveButton.setText("Save Changes");

        // Hide the assign-room section — assignments are made from the list
        assignRoomSection.setVisible(false);
        assignRoomSection.setManaged(false);

        fullNameField.setText(nz(tenant.getFullName()));
        nationalIdField.setText(nz(tenant.getNationalId()));
        phoneField.setText(nz(tenant.getPhone()));
        altPhoneField.setText(nz(tenant.getAltPhone()));
        emailField.setText(nz(tenant.getEmail()));
        nextOfKinField.setText(nz(tenant.getNextOfKin()));
        nextOfKinPhoneField.setText(nz(tenant.getNextOfKinPhone()));
        occupationField.setText(nz(tenant.getOccupation()));

        if (tenant.getStatus() != null) {
            statusCombo.getSelectionModel().select(tenant.getStatus());
        }
    }

    /** True if the user saved successfully. The caller uses this to refresh. */
    public boolean isSaved() {
        return saved;
    }

    /* =================================================================== */

    private void loadProperties() {
        try {
            List<Property> properties = propertyService.listActive();
            propertyCombo.setItems(FXCollections.observableArrayList(properties));
            if (properties.isEmpty()) {
                roomHintLabel.setText("No active properties. Register a property first.");
            }
        } catch (RuntimeException e) {
            log.error("Failed to load properties", e);
        }
    }

    /* =================================================================== */

    private void loadRooms() {
        Property p = propertyCombo.getValue();
        roomCombo.getItems().clear();
        rentField.clear();
        depositField.clear();
        roomHintLabel.setText("");

        if (p == null) return;

        try {
            List<Unit> vacant = unitService.listByProperty(p.getId()).stream()
                .filter(u -> Unit.STATUS_VACANT.equalsIgnoreCase(u.getStatus()))
                .toList();

            if (vacant.isEmpty()) {
                roomHintLabel.setText("No vacant rooms in this property.");
                return;
            }
            roomCombo.setItems(FXCollections.observableArrayList(vacant));
        } catch (RuntimeException e) {
            log.error("Failed to load rooms", e);
        }
    }

    /* =================================================================== */

    private void onRoomSelected() {
        Unit u = roomCombo.getValue();
        if (u == null) {
            roomHintLabel.setText("");
            return;
        }

        // Auto-fill rent
        if (u.getMonthlyRent() != null) {
            rentField.setText(u.getMonthlyRent().stripTrailingZeros().toPlainString());
        }
        // Auto-fill deposit
        if (u.getDepositAmount() != null && u.getDepositAmount().signum() > 0) {
            depositField.setText(u.getDepositAmount().stripTrailingZeros().toPlainString());
        }

        // Hint
        String hint = "Currently: " + formatMoney(u.getMonthlyRent()) + "/month";
        if (u.getDepositAmount() != null && u.getDepositAmount().signum() > 0) {
            hint += "  ·  Deposit " + formatMoney(u.getDepositAmount());
        }
        roomHintLabel.setText(hint);
    }

    /* =================================================================== */

    @FXML
    private void handleSave() {
        hideErrors();

        // ---------- Read raw tenant inputs ----------
        String fullName   = trimOrNull(fullNameField.getText());
        String nationalId = trimOrNull(nationalIdField.getText());
        String phone      = trimOrNull(phoneField.getText());
        String altPhone   = trimOrNull(altPhoneField.getText());
        String email      = trimOrNull(emailField.getText());
        String nextOfKin  = trimOrNull(nextOfKinField.getText());
        String nokPhone   = trimOrNull(nextOfKinPhoneField.getText());
        String occupation = trimOrNull(occupationField.getText());
        String status     = statusCombo.getValue();

        // ---------- Tenant field validation ----------
        boolean hasError = false;

        if (fullName == null) {
            showFieldError(fullNameError, "Full name is required.");
            hasError = true;
        }
        if (nationalId == null) {
            showFieldError(nationalIdError, "National ID or passport number is required.");
            hasError = true;
        }
        if (phone == null) {
            showFieldError(phoneError, "Phone number is required.");
            hasError = true;
        }
        if (email != null && !email.contains("@")) {
            showFieldError(emailError, "Email address looks invalid.");
            hasError = true;
        }

        // ---------- Assign-room validation (only in create mode) ----------
        Property assignProperty = null;
        Unit assignUnit = null;
        LocalDate moveInDate = null;
        BigDecimal assignRent = null;
        BigDecimal assignDeposit = BigDecimal.ZERO;

        boolean wantAssign = assignRoomSection.isVisible()
                && (propertyCombo.getValue() != null || roomCombo.getValue() != null);

        if (wantAssign) {
            assignProperty = propertyCombo.getValue();
            assignUnit = roomCombo.getValue();

            if (assignProperty == null) {
                showFormError("Please choose a property, or clear the Room field.");
                hasError = true;
            }
            if (assignUnit == null) {
                showFormError("Please choose a room, or clear the Property field.");
                hasError = true;
            }

            moveInDate = moveInDatePicker.getValue();
            if (moveInDate == null) moveInDate = LocalDate.now();

            String rentText = trimOrNull(rentField.getText());
            if (rentText == null) {
                showFieldError(rentError, "Rent is required when assigning a room.");
                hasError = true;
            } else {
                try {
                    assignRent = new BigDecimal(rentText);
                    if (assignRent.signum() < 0) {
                        showFieldError(rentError, "Rent cannot be negative.");
                        hasError = true;
                    }
                } catch (NumberFormatException e) {
                    showFieldError(rentError, "Rent must be a number (e.g. 8000).");
                    hasError = true;
                }
            }

            String depText = trimOrNull(depositField.getText());
            if (depText != null) {
                try {
                    assignDeposit = new BigDecimal(depText);
                    if (assignDeposit.signum() < 0) assignDeposit = BigDecimal.ZERO;
                } catch (NumberFormatException e) {
                    assignDeposit = BigDecimal.ZERO;
                }
            }
        }

        if (hasError) return;

        // ---------- Build Tenant ----------
        Tenant t = (tenant == null) ? new Tenant() : tenant;
        t.setFullName(fullName);
        t.setNationalId(nationalId);
        t.setPhone(phone);
        t.setAltPhone(altPhone);
        t.setEmail(email);
        t.setNextOfKin(nextOfKin);
        t.setNextOfKinPhone(nokPhone);
        t.setOccupation(occupation);
        t.setStatus(status != null ? status : Tenant.STATUS_ACTIVE);
        if (t.getRegisteredAt() == null) t.setRegisteredAt(LocalDate.now());

        // ---------- Save Tenant ----------
        try {
            if (tenant == null) {
                tenantService.register(t);
                log.info("Registered tenant '{}' number={}", t.getFullName(), t.getTenantNumber());
            } else {
                tenantService.update(t);
                log.info("Updated tenant id={}", t.getId());
            }
        } catch (ValidationException ve) {
            showFormError(ve.getMessage() != null
                ? ve.getMessage() : "Please check the entered details.");
            return;
        } catch (RuntimeException re) {
            log.error("Failed to save tenant", re);
            showFormError("Unable to save tenant. Please try again.");
            return;
        }

        // ---------- Optionally assign room ----------
        if (wantAssign && assignProperty != null && assignUnit != null && assignRent != null) {
            try {
                tenancyService.assign(
                    t.getId(),
                    assignUnit.getId(),
                    moveInDate,
                    assignRent,
                    assignDeposit);
                log.info("Assigned room {} to tenant {}", assignUnit.getHouseNumber(), t.getTenantNumber());
            } catch (TenancyService.ValidationException ve) {
                // Tenant is already saved — just tell the user the assignment failed
                showFormError("Tenant saved, but room assignment failed: "
                    + (ve.getMessage() != null ? ve.getMessage() : ""));
                saved = true;   // still mark saved so the list refreshes
                saveButton.setDisable(true);
                return;
            } catch (RuntimeException re) {
                log.error("Assignment failed after registration", re);
                showFormError("Tenant saved, but room assignment failed. "
                    + "Assign the room from the Tenants list.");
                saved = true;
                saveButton.setDisable(true);
                return;
            }
        }

        saved = true;
        closeDialog();
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
        hide(fullNameError);
        hide(nationalIdError);
        hide(phoneError);
        hide(emailError);
        hide(rentError);
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

    private String formatMoney(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMaximumFractionDigits(0);
        return "KSh " + nf.format(amount);
    }
}
