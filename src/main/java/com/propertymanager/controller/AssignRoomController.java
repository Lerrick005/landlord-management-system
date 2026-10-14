package com.propertymanager.controller;

import com.propertymanager.model.Property;
import com.propertymanager.model.Tenant;
import com.propertymanager.model.Unit;
import com.propertymanager.service.PropertyService;
import com.propertymanager.service.TenancyService;
import com.propertymanager.service.TenancyService.ValidationException;
import com.propertymanager.service.UnitService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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
 * Controller for assign-room.fxml — assigns a tenant to a vacant room.
 *
 * Flow:
 *   1. Caller sets the tenant via setTenant(t)
 *   2. Property dropdown loads all ACTIVE properties
 *   3. Selecting a property loads its VACANT rooms
 *   4. Selecting a room pre-fills the rent
 *   5. Save calls TenancyService.assign(...) which runs inside a transaction
 */
public class AssignRoomController {

    private static final Logger log = LoggerFactory.getLogger(AssignRoomController.class);

    /* ---- FXML-injected ---- */
    @FXML private Label subHeaderLabel;

    @FXML private ComboBox<Property> propertyCombo;
    @FXML private Label propertyError;

    @FXML private ComboBox<Unit> roomCombo;
    @FXML private Label roomError;
    @FXML private Label roomHintLabel;

    @FXML private DatePicker moveInDatePicker;
    @FXML private Label moveInError;

    @FXML private TextField rentField;
    @FXML private Label rentError;

    @FXML private TextField depositField;

    @FXML private Label formError;
    @FXML private Button saveButton;

    /* ---- Services ---- */
    private final PropertyService propertyService = new PropertyService();
    private final UnitService unitService = new UnitService();
    private final TenancyService tenancyService = new TenancyService();

    /* ---- State ---- */
    private Tenant tenant;
    private boolean saved = false;

    /* =================================================================== */

    @FXML
    private void initialize() {
        // Move-in date defaults to today
        moveInDatePicker.setValue(LocalDate.now());

        // Property combo displays the property name
        propertyCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Property p) { return p == null ? "" : p.getName(); }
            @Override public Property fromString(String s) { return null; }
        });

        // Room combo displays "K1 — Bedsitter — KSh 8,000"
        roomCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Unit u) {
                if (u == null) return "";
                return u.getHouseNumber() + " — " + u.getUnitTypeDisplay()
                    + " — " + formatMoney(u.getMonthlyRent());
            }
            @Override public Unit fromString(String s) { return null; }
        });

        // Load properties
        loadProperties();

        // When property changes -> reload rooms
        propertyCombo.valueProperty().addListener((o, a, b) -> loadRooms());

        // When room changes -> auto-fill rent and show hint
        roomCombo.valueProperty().addListener((o, a, b) -> onRoomSelected());

        // Clear errors on typing
        rentField.textProperty().addListener((o, a, b) -> hideErrors());
    }

    /* =================================================================== */

    /** Called by the caller BEFORE the dialog is shown. */
    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
        if (tenant != null) {
            subHeaderLabel.setText(
                tenant.getFullName() + "  (" + tenant.getTenantNumber() + ")");
        }
    }

    /** True if the user saved successfully. */
    public boolean isSaved() {
        return saved;
    }

    /* =================================================================== */

    private void loadProperties() {
        try {
            List<Property> properties = propertyService.listActive();
            propertyCombo.setItems(FXCollections.observableArrayList(properties));

            if (properties.isEmpty()) {
                showFormError("No active properties. Add a property first.");
                saveButton.setDisable(true);
            }
        } catch (RuntimeException e) {
            log.error("Failed to load properties", e);
            showFormError("Unable to load properties. Please try again.");
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
            List<Unit> allUnits = unitService.listByProperty(p.getId());
            List<Unit> vacant = allUnits.stream()
                .filter(u -> Unit.STATUS_VACANT.equalsIgnoreCase(u.getStatus()))
                .toList();

            if (vacant.isEmpty()) {
                roomHintLabel.setText("No vacant rooms in this property.");
                saveButton.setDisable(true);
                return;
            }

            roomCombo.setItems(FXCollections.observableArrayList(vacant));
            saveButton.setDisable(false);

        } catch (RuntimeException e) {
            log.error("Failed to load vacant rooms", e);
            showFormError("Unable to load rooms. Please try again.");
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

        // Auto-fill deposit as a suggestion (same as rent is common)
        if (u.getDepositAmount() != null
                && u.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
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

        if (tenant == null) {
            showFormError("No tenant selected.");
            return;
        }

        Property property = propertyCombo.getValue();
        Unit unit         = roomCombo.getValue();
        LocalDate moveIn  = moveInDatePicker.getValue();
        String rentText   = trimOrNull(rentField.getText());
        String depositTxt = trimOrNull(depositField.getText());

        // ---------- Validation ----------
        boolean hasError = false;

        if (property == null) {
            showFieldError(propertyError, "Please select a property.");
            hasError = true;
        }
        if (unit == null) {
            showFieldError(roomError, "Please select a room.");
            hasError = true;
        }
        if (moveIn == null) {
            showFieldError(moveInError, "Please choose a move-in date.");
            hasError = true;
        }
        if (rentText == null) {
            showFieldError(rentError, "Monthly rent is required.");
            hasError = true;
        }

        BigDecimal rent = null;
        if (rentText != null) {
            try {
                rent = new BigDecimal(rentText);
                if (rent.signum() < 0) {
                    showFieldError(rentError, "Rent cannot be negative.");
                    hasError = true;
                }
            } catch (NumberFormatException e) {
                showFieldError(rentError, "Rent must be a number (e.g. 8000).");
                hasError = true;
            }
        }

        BigDecimal deposit = BigDecimal.ZERO;
        if (depositTxt != null) {
            try {
                deposit = new BigDecimal(depositTxt);
                if (deposit.signum() < 0) {
                    // Negative deposit — treat as zero rather than erroring
                    deposit = BigDecimal.ZERO;
                }
            } catch (NumberFormatException e) {
                showFormError("Deposit must be a number (e.g. 8000).");
                hasError = true;
            }
        }

        if (hasError) return;

        // ---------- Delegate to the service ----------
        try {
            tenancyService.assign(
                tenant.getId(),
                unit.getId(),
                moveIn,
                rent,
                deposit);

            saved = true;
            closeDialog();

        } catch (ValidationException ve) {
            showFormError(ve.getMessage() != null
                ? ve.getMessage()
                : "Please check the entered details.");

        } catch (RuntimeException re) {
            log.error("Failed to assign room", re);
            showFormError("Unable to assign the room. Please try again.");
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
        hide(propertyError);
        hide(roomError);
        hide(moveInError);
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

    private String formatMoney(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMaximumFractionDigits(0);
        return "KSh " + nf.format(amount);
    }
}
