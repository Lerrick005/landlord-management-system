package com.propertymanager.controller;

import com.propertymanager.model.Property;
import com.propertymanager.service.PropertyService;
import com.propertymanager.service.PropertyService.ValidationException;
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

import java.time.LocalDate;

/**
 * Controller for property-form.fxml.
 *
 * Used for both "New Property" and "Edit Property".
 * Call setProperty(null) for create mode, or setProperty(p) for edit mode.
 */
public class PropertyFormController {

    private static final Logger log = LoggerFactory.getLogger(PropertyFormController.class);

    /* ---- FXML-injected ---- */
    @FXML private Label headerLabel;
    @FXML private TextField nameField;
    @FXML private Label nameError;
    @FXML private TextField codeField;
    @FXML private ComboBox<String> statusCombo;
    @FXML private TextField locationField;
    @FXML private TextArea descriptionArea;
    @FXML private TextArea notesArea;
    @FXML private Label formError;
    @FXML private Button saveButton;

    /* ---- Services ---- */
    private final PropertyService propertyService = new PropertyService();

    /* ---- State ---- */
    private Property property;      // null = create mode
    private boolean saved = false;

    /* =================================================================== */

    @FXML
    private void initialize() {
        statusCombo.setItems(FXCollections.observableArrayList("ACTIVE", "INACTIVE"));
        statusCombo.getSelectionModel().select("ACTIVE");

        // Clear errors as soon as the user types
        nameField.textProperty().addListener((o, a, b) -> hideErrors());
    }

    /* =================================================================== */

    /**
     * Called by the list controller before showing the dialog.
     *
     * @param property null to create a new property, or the property to edit
     */
    public void setProperty(Property property) {
        this.property = property;

        if (property == null) {
            // Create mode
            headerLabel.setText("New Property");
            saveButton.setText("Create Property");
        } else {
            // Edit mode — populate fields
            headerLabel.setText("Edit Property");
            saveButton.setText("Save Changes");

            nameField.setText(property.getName() == null ? "" : property.getName());
            codeField.setText(property.getCode() == null ? "" : property.getCode());
            locationField.setText(property.getLocation() == null ? "" : property.getLocation());
            descriptionArea.setText(property.getDescription() == null ? "" : property.getDescription());
            notesArea.setText(property.getNotes() == null ? "" : property.getNotes());

            if (property.getStatus() != null) {
                statusCombo.getSelectionModel().select(property.getStatus());
            }
        }
    }

    /** True if the user saved successfully. The list controller uses this to refresh. */
    public boolean isSaved() {
        return saved;
    }

    /* =================================================================== */

    @FXML
    private void handleSave() {
        hideErrors();

        // ---------- Build the property object ----------
        Property p = (property == null) ? new Property() : property;

        p.setName(trimOrNull(nameField.getText()));
        p.setCode(trimOrNull(codeField.getText()));
        p.setLocation(trimOrNull(locationField.getText()));
        p.setDescription(trimOrNull(descriptionArea.getText()));
        p.setNotes(trimOrNull(notesArea.getText()));
        p.setStatus(statusCombo.getValue());

        if (p.getRegisteredAt() == null) {
            p.setRegisteredAt(LocalDate.now());
        }

        // ---------- Client-side validation ----------
        if (p.getName() == null || p.getName().isBlank()) {
            showFieldError(nameError, "Property name is required.");
            nameField.requestFocus();
            return;
        }

        // ---------- Persist ----------
        try {
            if (property == null) {
                propertyService.create(p);
                log.info("Created property '{}'", p.getName());
            } else {
                propertyService.update(p);
                log.info("Updated property id={}", p.getId());
            }
            saved = true;
            closeDialog();

        } catch (ValidationException ve) {
            // Business rule failure — show to user
            if (ve.getMessage() != null && ve.getMessage().toLowerCase().contains("code")) {
                showFormError(ve.getMessage());
            } else {
                showFormError(ve.getMessage());
            }
            log.info("Validation failed: {}", ve.getMessage());

        } catch (RuntimeException re) {
            // Technical failure — show generic message
            log.error("Failed to save property", re);
            showFormError("Unable to save property. Please try again.");
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
        nameError.setVisible(false);
        nameError.setManaged(false);
        formError.setVisible(false);
        formError.setManaged(false);
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
}
