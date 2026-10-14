package com.propertymanager.controller;

import com.propertymanager.model.Property;
import com.propertymanager.model.Unit;
import com.propertymanager.service.UnitService;
import com.propertymanager.service.UnitService.ValidationException;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Controller for unit-form.fxml.
 *
 * Used for both "New Room" and "Edit Room".
 * setProperty(...) is required — it tells us which property the room belongs to.
 * setUnit(null) -> create mode. setUnit(u) -> edit mode.
 */
public class UnitFormController {

    private static final Logger log = LoggerFactory.getLogger(UnitFormController.class);

    /* ---- FXML-injected ---- */
    @FXML private Label headerLabel;
    @FXML private Label subHeaderLabel;

    @FXML private TextField houseNumberField;
    @FXML private Label houseNumberError;

    @FXML private ComboBox<String> typeCombo;
    @FXML private Label typeError;

    @FXML private TextField rentField;
    @FXML private Label rentError;

    @FXML private TextField depositField;
    @FXML private Label depositError;

    @FXML private ComboBox<String> statusCombo;
    @FXML private TextArea notesArea;
    @FXML private Label formError;

    @FXML private Button saveButton;

    /* ---- Services ---- */
    private final UnitService unitService = new UnitService();

    /* ---- State ---- */
    private Property property;
    private Unit unit;            // null = create mode
    private boolean saved = false;

    /* =================================================================== */

    @FXML
    private void initialize() {
        // Populate type dropdown
        typeCombo.setItems(FXCollections.observableArrayList(
            "Bedsitter",
            "Single",
            "Studio",
            "One Bedroom",
            "Two Bedroom",
            "Three Bedroom",
            "Other"
        ));

        // Populate status dropdown
        statusCombo.setItems(FXCollections.observableArrayList(
            "VACANT", "OCCUPIED", "MAINTENANCE"
        ));
        statusCombo.getSelectionModel().select("VACANT");

        // Clear errors on typing
        houseNumberField.textProperty().addListener((o, a, b) -> hideErrors());
        rentField.textProperty().addListener((o, a, b) -> hideErrors());
        depositField.textProperty().addListener((o, a, b) -> hideErrors());
    }

    /* =================================================================== */

    /** Called by the caller BEFORE the dialog is shown. */
    public void setProperty(Property property) {
        this.property = property;
        if (property != null) {
            String sub = property.getName();
            if (property.getLocation() != null && !property.getLocation().isBlank()) {
                sub += " · " + property.getLocation();
            }
            subHeaderLabel.setText(sub);
        }
    }

    /** Called by the caller BEFORE the dialog is shown. null = create mode. */
    public void setUnit(Unit unit) {
        this.unit = unit;

        if (unit == null) {
            headerLabel.setText("New Room");
            saveButton.setText("Create Room");
            return;
        }

        // Edit mode — populate fields
        headerLabel.setText("Edit Room");
        saveButton.setText("Save Changes");

        houseNumberField.setText(unit.getHouseNumber() == null ? "" : unit.getHouseNumber());
        notesArea.setText(unit.getNotes() == null ? "" : unit.getNotes());

        if (unit.getMonthlyRent() != null) {
            rentField.setText(unit.getMonthlyRent().stripTrailingZeros().toPlainString());
        }
        if (unit.getDepositAmount() != null
                && unit.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
            depositField.setText(unit.getDepositAmount().stripTrailingZeros().toPlainString());
        }

        typeCombo.getSelectionModel().select(displayTypeFromStored(unit.getUnitType()));

        if (unit.getStatus() != null) {
            statusCombo.getSelectionModel().select(unit.getStatus());
        }
    }

    /** True if the user saved successfully. The caller uses this to refresh. */
    public boolean isSaved() {
        return saved;
    }

    /* =================================================================== */

    @FXML
    private void handleSave() {
        hideErrors();

        if (property == null) {
            showFormError("No property selected.");
            return;
        }

        // ---------- Read raw inputs ----------
        String houseNumber = trimOrNull(houseNumberField.getText());
        String typeDisplay = typeCombo.getValue();
        String status      = statusCombo.getValue();
        String rentText    = trimOrNull(rentField.getText());
        String depositText = trimOrNull(depositField.getText());
        String notes       = trimOrNull(notesArea.getText());

        // ---------- Field validation ----------
        boolean hasError = false;

        if (houseNumber == null) {
            showFieldError(houseNumberError, "Room number is required.");
            hasError = true;
        }
        if (typeDisplay == null) {
            showFieldError(typeError, "Please choose a room type.");
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
        if (depositText != null) {
            try {
                deposit = new BigDecimal(depositText);
                if (deposit.signum() < 0) {
                    showFieldError(depositError, "Deposit cannot be negative.");
                    hasError = true;
                }
            } catch (NumberFormatException e) {
                showFieldError(depositError, "Deposit must be a number.");
                hasError = true;
            }
        }

        if (hasError) return;

        // ---------- Build the Unit ----------
        Unit u = (unit == null) ? new Unit() : unit;
        u.setPropertyId(property.getId());
        u.setHouseNumber(houseNumber);
        u.setUnitType(storedTypeFromDisplay(typeDisplay));
        u.setMonthlyRent(rent);
        u.setDepositAmount(deposit);
        u.setStatus(status != null ? status : Unit.STATUS_VACANT);
        u.setNotes(notes);

        // ---------- Persist ----------
        try {
            if (unit == null) {
                unitService.create(u);
                log.info("Created room {} at property {}", u.getHouseNumber(), property.getName());
            } else {
                unitService.update(u);
                log.info("Updated room id={}", u.getId());
            }
            saved = true;
            closeDialog();

        } catch (ValidationException ve) {
            showFormError(ve.getMessage() != null
                ? ve.getMessage()
                : "Please check the entered details.");

        } catch (RuntimeException re) {
            log.error("Failed to save room", re);
            showFormError("Unable to save the room. Please try again.");
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
        hide(houseNumberError);
        hide(typeError);
        hide(rentError);
        hide(depositError);
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

    /* =================================================================== */

    /** "One Bedroom" -> "ONE_BEDROOM", "Bedsitter" -> "BEDSITTER", etc. */
    private String storedTypeFromDisplay(String display) {
        if (display == null) return null;
        return switch (display) {
            case "Bedsitter"     -> Unit.TYPE_BEDSITTER;
            case "Single"        -> Unit.TYPE_SINGLE;
            case "Studio"        -> Unit.TYPE_STUDIO;
            case "One Bedroom"   -> Unit.TYPE_ONE_BEDROOM;
            case "Two Bedroom"   -> Unit.TYPE_TWO_BEDROOM;
            case "Three Bedroom" -> Unit.TYPE_THREE_BEDROOM;
            default              -> Unit.TYPE_OTHER;
        };
    }

    /** "ONE_BEDROOM" -> "One Bedroom", "BEDSITTER" -> "Bedsitter", etc. */
    private String displayTypeFromStored(String stored) {
        if (stored == null) return "Other";
        return switch (stored) {
            case Unit.TYPE_BEDSITTER     -> "Bedsitter";
            case Unit.TYPE_SINGLE        -> "Single";
            case Unit.TYPE_STUDIO        -> "Studio";
            case Unit.TYPE_ONE_BEDROOM   -> "One Bedroom";
            case Unit.TYPE_TWO_BEDROOM   -> "Two Bedroom";
            case Unit.TYPE_THREE_BEDROOM -> "Three Bedroom";
            default                      -> "Other";
        };
    }
}
