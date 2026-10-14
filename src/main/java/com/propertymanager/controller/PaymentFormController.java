
package com.propertymanager.controller;

import com.propertymanager.model.Payment;
import com.propertymanager.model.Tenant;
import com.propertymanager.model.Tenancy;
import com.propertymanager.service.PaymentService;
import com.propertymanager.service.PaymentService.ValidationException;
import com.propertymanager.service.TenancyService;
import com.propertymanager.service.TenantService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Controller for payment-form.fxml.
 *
 * Flow:
 *   1. Load all active tenants
 *   2. On tenant selection, look up their active tenancy and pre-fill amount
 *   3. On save, delegate to PaymentService.record(...)
 *   4. Expose the saved Payment so the caller can show the receipt
 */
public class PaymentFormController {

    private static final Logger log = LoggerFactory.getLogger(PaymentFormController.class);

    /* ---- FXML-injected ---- */
    @FXML private ComboBox<Tenant> tenantCombo;
    @FXML private Label tenantError;
    @FXML private Label tenantHintLabel;

    @FXML private TextField amountField;
    @FXML private Label amountError;

    @FXML private DatePicker paymentDatePicker;
    @FXML private Label paymentDateError;

    @FXML private ComboBox<String> monthCombo;
    @FXML private ComboBox<Integer> yearCombo;

    @FXML private ComboBox<String> methodCombo;

    @FXML private TextField referenceField;
    @FXML private Label referenceError;

    @FXML private TextArea notesArea;

    @FXML private Label formError;
    @FXML private Button saveButton;

    /* ---- Services ---- */
    private final PaymentService paymentService = new PaymentService();
    private final TenantService tenantService = new TenantService();
    private final TenancyService tenancyService = new TenancyService();

    /* ---- State ---- */
    private Payment savedPayment;
    private boolean saved = false;

    /* =================================================================== */

    @FXML
    private void initialize() {
        // Tenant combo — show "TEN-000001 — John Kamau"
        tenantCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Tenant t) {
                if (t == null) return "";
                return t.getTenantNumber() + " — " + t.getFullName();
            }
            @Override public Tenant fromString(String s) { return null; }
        });

        // Payment date defaults to today
        paymentDatePicker.setValue(LocalDate.now());

        // Month combo — January..December
        monthCombo.setItems(FXCollections.observableArrayList(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"));
        monthCombo.getSelectionModel().select(LocalDate.now().getMonthValue() - 1);

        // Year combo — current year ± 1
        int currentYear = LocalDate.now().getYear();
        yearCombo.setItems(FXCollections.observableArrayList(
            currentYear - 1, currentYear, currentYear + 1));
        yearCombo.getSelectionModel().select(Integer.valueOf(currentYear));

        // Method combo
        methodCombo.setItems(FXCollections.observableArrayList(
            "Cash", "M-Pesa", "Bank", "Other"));
        methodCombo.getSelectionModel().select("Cash");

        // Tenant selection triggers auto-fill
        tenantCombo.valueProperty().addListener((o, a, b) -> onTenantSelected());

        // Clear errors on typing
        amountField.textProperty().addListener((o, a, b) -> hideErrors());
        referenceField.textProperty().addListener((o, a, b) -> hideErrors());

        // Load tenants
        loadTenants();
    }

    /* =================================================================== */

    private void loadTenants() {
        try {
            List<Tenant> tenants = tenantService.listActive();
            tenantCombo.setItems(FXCollections.observableArrayList(tenants));
            if (tenants.isEmpty()) {
                showFormError("No active tenants. Register a tenant first.");
                saveButton.setDisable(true);
            }
        } catch (RuntimeException e) {
            log.error("Failed to load tenants", e);
            showFormError("Unable to load tenants. Please try again.");
        }
    }

    /* =================================================================== */

    private void onTenantSelected() {
        Tenant t = tenantCombo.getValue();
        tenantHintLabel.setText("");
        if (t == null) return;

        try {
            var activeOpt = tenancyService.findActiveByTenant(t.getId());
            if (activeOpt.isPresent()) {
                Tenancy ten = activeOpt.get();
                String room = ten.getUnitLabel();
                String property = ten.getPropertyLabel();
                BigDecimal rent = ten.getMonthlyRent() != null
                        ? ten.getMonthlyRent() : BigDecimal.ZERO;

                tenantHintLabel.setText(
                    room + " · " + property + "  ·  Rent " + formatMoney(rent));

                // Auto-fill amount
                if (amountField.getText() == null || amountField.getText().isBlank()) {
                    amountField.setText(rent.stripTrailingZeros().toPlainString());
                }
            } else {
                tenantHintLabel.setText("This tenant has no active room.");
            }
        } catch (RuntimeException e) {
            log.error("Failed to look up tenancy for tenant {}", t.getId(), e);
        }
    }

    /* =================================================================== */

    @FXML
    private void handleSave() {
        hideErrors();

        Tenant tenant = tenantCombo.getValue();
        String amountText = trimOrNull(amountField.getText());
        LocalDate payDate = paymentDatePicker.getValue();
        String monthName  = monthCombo.getValue();
        Integer year      = yearCombo.getValue();
        String methodName = methodCombo.getValue();
        String reference  = trimOrNull(referenceField.getText());
        String notes      = trimOrNull(notesArea.getText());

        // ---------- Validation ----------
        boolean hasError = false;

        if (tenant == null) {
            showFieldError(tenantError, "Please select a tenant.");
            hasError = true;
        }
        if (amountText == null) {
            showFieldError(amountError, "Amount is required.");
            hasError = true;
        }
        if (payDate == null) {
            showFieldError(paymentDateError, "Payment date is required.");
            hasError = true;
        }
        if (monthName == null || year == null) {
            showFormError("Please choose the month and year this payment is for.");
            hasError = true;
        }

        BigDecimal amount = null;
        if (amountText != null) {
            try {
                amount = new BigDecimal(amountText);
                if (amount.signum() <= 0) {
                    showFieldError(amountError, "Amount must be greater than zero.");
                    hasError = true;
                }
            } catch (NumberFormatException e) {
                showFieldError(amountError, "Amount must be a number (e.g. 8000).");
                hasError = true;
            }
        }

        if (reference != null && reference.length() > 60) {
            showFieldError(referenceError, "Reference must be 60 characters or fewer.");
            hasError = true;
        }

        if (hasError) return;

        // ---------- Build the Payment ----------
        Payment p = new Payment();
        p.setTenantId(tenant.getId());
        p.setAmount(amount);
        p.setPaymentDate(payDate);
        p.setPeriodYear(year);
        p.setPeriodMonth(monthNameToNumber(monthName));
        p.setMethod(methodCodeFromDisplay(methodName));
        p.setReferenceNumber(reference);
        p.setNotes(notes);

        // ---------- Persist ----------
        try {
            paymentService.record(p);
            savedPayment = p;
            saved = true;
            closeDialog();

        } catch (ValidationException ve) {
            showFormError(ve.getMessage() != null
                ? ve.getMessage() : "Please check the entered details.");
        } catch (RuntimeException re) {
            log.error("Failed to record payment", re);
            showFormError("Unable to record the payment. Please try again.");
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

    /** True if the user saved successfully. */
    public boolean isSaved() {
        return saved;
    }

    /** The payment just recorded, or null if the user cancelled. */
    public Payment getSavedPayment() {
        return savedPayment;
    }

    /* =================================================================== */

    private void hideErrors() {
        hide(tenantError);
        hide(amountError);
        hide(paymentDateError);
        hide(referenceError);
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

    private int monthNameToNumber(String name) {
        for (Month m : Month.values()) {
            if (m.getDisplayName(TextStyle.FULL, Locale.ENGLISH).equalsIgnoreCase(name)) {
                return m.getValue();
            }
        }
        return LocalDate.now().getMonthValue();
    }

    private String methodCodeFromDisplay(String display) {
        if (display == null) return Payment.METHOD_CASH;
        return switch (display) {
            case "Cash"   -> Payment.METHOD_CASH;
            case "M-Pesa" -> Payment.METHOD_MPESA;
            case "Bank"   -> Payment.METHOD_BANK;
            default       -> Payment.METHOD_OTHER;
        };
    }
}
