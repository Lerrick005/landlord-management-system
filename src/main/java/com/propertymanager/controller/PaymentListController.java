package com.propertymanager.controller;

import com.propertymanager.model.Payment;
import com.propertymanager.service.PaymentService;
import com.propertymanager.service.PaymentService.ValidationException;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Controller for payments.fxml — the payments list screen.
 *
 * Supports viewing, filtering, recording, and voiding payments.
 * Voided payments stay in the table but are marked with a red VOIDED badge.
 */
public class PaymentListController {

    private static final Logger log = LoggerFactory.getLogger(PaymentListController.class);

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    /* ---- FXML-injected ---- */
    @FXML private Button recordPaymentButton;

    @FXML private Label monthCollectedLabel;
    @FXML private Label monthCountLabel;
    @FXML private Label totalCountLabel;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> periodFilter;
    @FXML private Label countLabel;

    @FXML private TableView<Payment> paymentsTable;
    @FXML private TableColumn<Payment, String> receiptColumn;
    @FXML private TableColumn<Payment, String> dateColumn;
    @FXML private TableColumn<Payment, String> tenantColumn;
    @FXML private TableColumn<Payment, String> roomColumn;
    @FXML private TableColumn<Payment, String> periodColumn;
    @FXML private TableColumn<Payment, String> amountColumn;
    @FXML private TableColumn<Payment, String> methodColumn;
    @FXML private TableColumn<Payment, String> statusColumn;
    @FXML private TableColumn<Payment, Void>   actionsColumn;

    @FXML private Button emptyRecordButton;

    /* ---- Service ---- */
    private final PaymentService paymentService = new PaymentService();

    /* ---- State ---- */
    private final ObservableList<Payment> data = FXCollections.observableArrayList();
    private PauseTransition searchDebounce;

    /* =================================================================== */

    @FXML
    private void initialize() {
        applyIcons();
        setupPeriodFilter();
        setupSearch();
        setupColumns();

        paymentsTable.setItems(data);
        paymentsTable.setPlaceholder(new Label(""));

        refresh();
    }

    /* =================================================================== */

    private void applyIcons() {
        recordPaymentButton.setGraphic(new FontIcon("fas-money-bill-wave"));
        recordPaymentButton.setGraphicTextGap(6);

        emptyRecordButton.setGraphic(new FontIcon("fas-money-bill-wave"));
        emptyRecordButton.setGraphicTextGap(6);
    }

    /* =================================================================== */

    private void setupPeriodFilter() {
        periodFilter.setItems(FXCollections.observableArrayList(
            "All time",
            "This month",
            "Last month",
            "Last 3 months",
            "Last 6 months",
            "This year"));
        periodFilter.getSelectionModel().select("All time");
        periodFilter.valueProperty().addListener((o, a, b) -> refresh());
    }

    /* =================================================================== */

    private void setupSearch() {
        searchDebounce = new PauseTransition(Duration.millis(250));
        searchDebounce.setOnFinished(e -> refresh());

        searchField.textProperty().addListener((obs, oldVal, newVal) ->
            searchDebounce.playFromStart());
    }

    /* =================================================================== */

    private void setupColumns() {
        receiptColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(nz(c.getValue().getReceiptNumber())));

        dateColumn.setCellValueFactory(c -> {
            var d = c.getValue().getPaymentDate();
            return new ReadOnlyStringWrapper(d != null ? d.format(DATE_FMT) : "—");
        });

        tenantColumn.setCellValueFactory(c -> {
            var t = c.getValue().getTenant();
            return new ReadOnlyStringWrapper(t != null ? nz(t.getFullName()) : "—");
        });

        roomColumn.setCellValueFactory(c -> {
            var te = c.getValue().getTenancy();
            if (te != null && te.getUnit() != null && te.getUnit().getHouseNumber() != null) {
                return new ReadOnlyStringWrapper(te.getUnit().getHouseNumber());
            }
            return new ReadOnlyStringWrapper("—");
        });

        periodColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(c.getValue().getPeriodLabel()));

        amountColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(formatMoney(c.getValue().getAmount())));
        amountColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        methodColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(c.getValue().getMethodDisplay()));

        // Status column — ACTIVE / VOIDED badge
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Payment p = getTableRow().getItem();
                Label badge = new Label(p.getStatusLabel());
                badge.getStyleClass().add("badge");
                badge.getStyleClass().add(
                    p.isVoided() ? "badge-danger" : "badge-success");
                setGraphic(badge);
                setText(null);
            }
        });

        // Actions — Receipt + Void
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button receiptBtn = compactButton("Receipt", "fas-receipt");
            private final Button voidBtn    = compactButton("Void",    "fas-ban");
            private final HBox box = new HBox(6, receiptBtn, voidBtn);

            {
                receiptBtn.getStyleClass().add("button");
                voidBtn.getStyleClass().add("button-danger");
                box.setAlignment(Pos.CENTER_LEFT);

                receiptBtn.setOnAction(e -> {
                    Payment p = rowPayment();
                    if (p != null) openReceipt(p);
                });

                voidBtn.setOnAction(e -> {
                    Payment p = rowPayment();
                    if (p != null) handleVoidPayment(p);
                });
            }

            private Payment rowPayment() {
                return getTableRow() == null ? null : getTableRow().getItem();
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                Payment p = rowPayment();
                if (empty || p == null) {
                    setGraphic(null);
                    return;
                }

                // Void hidden for already-voided payments
                voidBtn.setVisible(!p.isVoided());
                voidBtn.setManaged(!p.isVoided());

                setGraphic(box);
            }
        });
    }

    /* =================================================================== */

    /** Compact action button used in the Actions column. */
    private Button compactButton(String label, String iconCode) {
        Button b = new Button(label);
        b.setGraphic(new FontIcon(iconCode));
        b.setGraphicTextGap(5);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setMaxWidth(Region.USE_PREF_SIZE);
        b.setPrefHeight(28);
        b.setStyle("-fx-padding: 4 10 4 10; -fx-font-size: 12px; -fx-background-radius: 5;");
        return b;
    }

    /* =================================================================== */

    private void refresh() {
        try {
            String query = searchField.getText();
            String period = periodFilter.getValue();

            List<Payment> list = paymentService.search(query);
            list = applyPeriodFilter(list, period);

            data.setAll(list);
            countLabel.setText(list.size() + (list.size() == 1 ? " payment" : " payments"));

            // ---- Summary strip ----
            YearMonth now = YearMonth.now();
            BigDecimal monthTotal = paymentService.totalCollectedFor(
                now.getYear(), now.getMonthValue());
            monthCollectedLabel.setText(formatMoney(monthTotal));

            long monthCount = paymentService.listByPeriod(
                now.getYear(), now.getMonthValue()).stream()
                .filter(p -> !p.isVoided())
                .count();
            monthCountLabel.setText(String.valueOf(monthCount));

            totalCountLabel.setText(String.valueOf(paymentService.count()));

            // ---- Empty state ----
            boolean empty = list.isEmpty();
            var emptyState = paymentsTable.getParent().lookup("#emptyState");
            if (emptyState != null) {
                emptyState.setVisible(empty);
                emptyState.setManaged(empty);
            }
            paymentsTable.setVisible(!empty);
            paymentsTable.setManaged(!empty);

        } catch (RuntimeException e) {
            log.error("Failed to load payments", e);
            showError("Unable to load payments. Please try again.");
        }
    }

    /* =================================================================== */

    private List<Payment> applyPeriodFilter(List<Payment> list, String period) {
        if (period == null || "All time".equals(period)) return list;

        YearMonth now = YearMonth.now();
        YearMonth from;
        YearMonth to = now;

        switch (period) {
            case "This month"    -> { from = now; }
            case "Last month"    -> { from = now.minusMonths(1); to = now.minusMonths(1); }
            case "Last 3 months" -> { from = now.minusMonths(2); }
            case "Last 6 months" -> { from = now.minusMonths(5); }
            case "This year"     -> { from = YearMonth.of(now.getYear(), 1); }
            default -> { return list; }
        }

        final YearMonth f = from;
        final YearMonth t = to;
        return list.stream().filter(p -> {
            YearMonth ym = YearMonth.of(p.getPeriodYear(), p.getPeriodMonth());
            return !ym.isBefore(f) && !ym.isAfter(t);
        }).toList();
    }

    /* =================================================================== */

    @FXML
    private void handleRecordPayment() {
        openPaymentForm();
    }

    /* =================================================================== */

    private void openPaymentForm() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/payment-form.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(
                getClass().getResource("/css/app.css").toExternalForm());

            PaymentFormController controller = loader.getController();

            Stage dialog = new Stage();
            dialog.setTitle("Record Payment");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(paymentsTable.getScene().getWindow());
            dialog.setScene(scene);
            dialog.setResizable(false);
            dialog.setOnShown(e -> dialog.centerOnScreen());
            dialog.showAndWait();

            if (controller.isSaved()) {
                refresh();
                if (controller.getSavedPayment() != null) {
                    openReceipt(controller.getSavedPayment());
                }
            }
        } catch (IOException e) {
            log.error("Failed to open payment form", e);
            showError("Unable to open the payment form.");
        }
    }

    /* =================================================================== */

    private void openReceipt(Payment p) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/receipt.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(
                getClass().getResource("/css/app.css").toExternalForm());

            ReceiptController controller = loader.getController();
            controller.setPayment(p);

            Stage dialog = new Stage();
            dialog.setTitle("Receipt " + p.getReceiptNumber());
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(paymentsTable.getScene().getWindow());
            dialog.setScene(scene);
            dialog.setResizable(false);
            dialog.setOnShown(e -> dialog.centerOnScreen());
            dialog.showAndWait();
        } catch (IOException e) {
            log.error("Failed to open receipt", e);
            showError("Unable to open the receipt.");
        }
    }

    /* =================================================================== */
    /*  VOID PAYMENT                                                       */
    /* =================================================================== */

    private void handleVoidPayment(Payment p) {
        if (p.isVoided()) {
            showError("This payment is already voided.");
            return;
        }

        // ---- Step 1: Confirm ----
        Alert confirm = new Alert(Alert.AlertType.WARNING);
        confirm.setTitle("Void Payment");
        confirm.setHeaderText("Void payment " + p.getReceiptNumber() + "?");
        confirm.setContentText(
            "Amount: " + formatMoney(p.getAmount()) + "\n"
            + "Tenant: " + (p.getTenant() != null ? p.getTenant().getFullName() : "—") + "\n"
            + "For: " + p.getPeriodLabel() + "\n\n"
            + "This payment will stop counting toward the tenant's balance.\n"
            + "The record itself is preserved for the audit trail.\n\n"
            + "This action cannot be undone.");

        ButtonType confirmBtn = new ButtonType("Continue");
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonType.CANCEL.getButtonData());
        confirm.getButtonTypes().setAll(cancelBtn, confirmBtn);

        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != confirmBtn) return;

        // ---- Step 2: Ask for reason ----
        String reason = promptForReason(p);
        if (reason == null) return;

        // ---- Step 3: Void ----
        try {
            paymentService.voidPayment(p.getId(), reason);
            refresh();

            Alert done = new Alert(Alert.AlertType.INFORMATION);
            done.setTitle("Payment Voided");
            done.setHeaderText("Payment voided successfully");
            done.setContentText(
                "Receipt: " + p.getReceiptNumber() + "\n"
                + "Amount: " + formatMoney(p.getAmount()) + "\n"
                + "Reason: " + reason);
            done.showAndWait();

        } catch (ValidationException ve) {
            showError(ve.getMessage() != null
                ? ve.getMessage()
                : "Unable to void the payment.");
        } catch (RuntimeException re) {
            log.error("Failed to void payment", re);
            showError("Unable to void the payment. Please try again.");
        }
    }

    /**
     * Shows a dialog asking why the payment is being voided.
     * Returns the reason string, or null if the user cancelled.
     */
    private String promptForReason(Payment p) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reason for Void");
        dialog.setHeaderText("Why are you voiding receipt " + p.getReceiptNumber() + "?");

        ButtonType voidBtn = new ButtonType("Void Payment",
                                            ButtonType.OK.getButtonData());
        ButtonType cancelBtn = new ButtonType("Cancel",
                                              ButtonType.CANCEL.getButtonData());
        dialog.getDialogPane().getButtonTypes().setAll(cancelBtn, voidBtn);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        Label lbl = new Label("Reason *");
        lbl.getStyleClass().add("field-label");

        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText(
            "e.g. Wrong amount entered, duplicate payment, refund issued...");
        reasonArea.setPrefRowCount(3);
        reasonArea.setPrefWidth(360);
        reasonArea.setWrapText(true);

        Label error = new Label();
        error.setStyle("-fx-text-fill: -fx-danger; -fx-font-size: 11px;");
        error.setVisible(false);
        error.setManaged(false);

        Label hint = new Label("Minimum 5 characters. This will be saved in the audit log.");
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        hint.setMaxWidth(360);

        content.getChildren().addAll(lbl, reasonArea, hint, error);
        dialog.getDialogPane().setContent(content);

        // Validate on OK
        final Button okButton = (Button) dialog.getDialogPane().lookupButton(voidBtn);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            String text = reasonArea.getText() == null ? "" : reasonArea.getText().trim();
            error.setVisible(false);
            error.setManaged(false);

            if (text.isEmpty()) {
                error.setText("Please enter a reason.");
                error.setVisible(true);
                error.setManaged(true);
                evt.consume();
                return;
            }
            if (text.length() < 5) {
                error.setText("Reason must be at least 5 characters.");
                error.setVisible(true);
                error.setManaged(true);
                evt.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != voidBtn) return null;

        String text = reasonArea.getText();
        return text != null ? text.trim() : null;
    }

    /* =================================================================== */

    private String formatMoney(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMaximumFractionDigits(0);
        return "KSh " + nf.format(amount);
    }

    private String nz(String s) { return (s == null || s.isBlank()) ? "—" : s; }

    private void showError(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error");
        a.setHeaderText("Something went wrong");
        a.setContentText(message);
        a.showAndWait();
    }
}
