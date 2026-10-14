package com.propertymanager.controller;

import com.propertymanager.config.AppConfig;
import com.propertymanager.database.Database;
import com.propertymanager.model.Payment;
import com.propertymanager.model.Tenant;
import com.propertymanager.model.Tenancy;
import com.propertymanager.session.SessionManager;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Transform;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.embed.swing.SwingFXUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Controller for receipt.fxml.
 *
 * Displays a professional payment receipt and provides:
 *   - Print (via JavaFX printer job)
 *   - Export PDF (via Apache PDFBox — image-based for simplicity)
 *
 * If the payment is voided, a VOIDED watermark is applied and the
 * print/export buttons are hidden.
 */
public class ReceiptController {

    private static final Logger log = LoggerFactory.getLogger(ReceiptController.class);

    private static final DateTimeFormatter PRINT_DATE_FMT =
        DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH);

    /* ---- FXML-injected ---- */
    @FXML private Label orgNameLabel;
    @FXML private Label orgAddressLabel;
    @FXML private Label orgContactLabel;

    @FXML private Label receiptNumberLabel;
    @FXML private Label amountBannerLabel;

    @FXML private Label tenantLabel;
    @FXML private Label tenantNumberLabel;
    @FXML private Label propertyLabel;
    @FXML private Label roomLabel;
    @FXML private Label periodLabel;
    @FXML private Label paymentDateLabel;
    @FXML private Label methodLabel;
    @FXML private HBox  referenceRow;
    @FXML private Label referenceLabel;

    @FXML private Label monthlyRentLabel;
    @FXML private Label amountPaidLabel;
    @FXML private Label balanceLabel;

    @FXML private Label recordedByLabel;
    @FXML private Label printedOnLabel;

    @FXML private Button printButton;
    @FXML private Button exportButton;
    @FXML private Button closeButton;

    /* ---- State ---- */
    private Payment payment;

    /* =================================================================== */

    @FXML
    private void initialize() {
        loadOrganizationInfo();

        printButton.setOnAction(e -> handlePrint());
        exportButton.setOnAction(e -> handleExportPdf());

        printedOnLabel.setText(LocalDateTime.now().format(PRINT_DATE_FMT));
    }

    /* =================================================================== */

    /** Called by the caller BEFORE the dialog is shown. */
    public void setPayment(Payment payment) {
        this.payment = payment;
        if (payment == null) return;

        populateReceipt(payment);
    }

    /* =================================================================== */

    private void populateReceipt(Payment p) {
        receiptNumberLabel.setText(p.getReceiptNumber() != null
                ? p.getReceiptNumber() : "—");

        // ---- Void handling: mark the receipt clearly and disable actions ----
        if (p.isVoided()) {
            markAsVoided(p);
        }

        BigDecimal amount = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
        amountBannerLabel.setText(formatMoney(amount));
        amountPaidLabel.setText(formatMoney(amount));

        // -------- Tenant --------
        Tenant t = p.getTenant();
        if (t != null) {
            tenantLabel.setText(safe(t.getFullName()));
            tenantNumberLabel.setText(safe(t.getTenantNumber()));
        } else {
            tenantLabel.setText("—");
            tenantNumberLabel.setText("—");
        }

        // -------- Tenancy snapshot: property + room + rent --------
        Tenancy te = p.getTenancy();
        BigDecimal rent = BigDecimal.ZERO;

        if (te != null) {
            if (te.getUnit() != null) {
                roomLabel.setText(safe(te.getUnit().getHouseNumber()));
                if (te.getUnit().getProperty() != null) {
                    propertyLabel.setText(safe(te.getUnit().getProperty().getName()));
                } else {
                    propertyLabel.setText("—");
                }
            } else {
                roomLabel.setText("—");
                propertyLabel.setText("—");
            }
            rent = te.getMonthlyRent() != null ? te.getMonthlyRent() : BigDecimal.ZERO;
        } else {
            propertyLabel.setText("—");
            roomLabel.setText("—");
        }

        monthlyRentLabel.setText(formatMoney(rent));

        // -------- Period --------
        periodLabel.setText(p.getPeriodLabel());

        // -------- Payment date --------
        LocalDate pd = p.getPaymentDate();
        paymentDateLabel.setText(pd != null
                ? pd.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
                : "—");

        // -------- Method --------
        methodLabel.setText(safe(p.getMethodDisplay()));

        // -------- Reference --------
        String ref = p.getReferenceNumber();
        if (ref == null || ref.isBlank()) {
            referenceRow.setVisible(false);
            referenceRow.setManaged(false);
        } else {
            referenceRow.setVisible(true);
            referenceRow.setManaged(true);
            referenceLabel.setText(ref);
        }

        // -------- Balance outstanding for this period --------
        BigDecimal outstanding = rent.subtract(amount);
        if (outstanding.signum() < 0) outstanding = BigDecimal.ZERO;
        balanceLabel.setText(formatMoney(outstanding));

        // -------- Recorded by --------
        String recorder = p.getRecordedByUsername();
        if (recorder == null || recorder.isBlank()) {
            recorder = SessionManager.isLoggedIn()
                ? SessionManager.getCurrentUser().getUsername()
                : "system";
        }
        recordedByLabel.setText(recorder);
    }

    /* =================================================================== */
    /*  VOIDED MARKING                                                     */
    /* =================================================================== */

    /**
     * Shows a big red "VOIDED" watermark on the receipt and hides the
     * Print and Export buttons — a voided receipt is a historical record
     * but should not be handed to the tenant as proof of payment.
     */
    private void markAsVoided(Payment p) {
        try {
            // Hide the print + export buttons
            printButton.setVisible(false);
            printButton.setManaged(false);
            exportButton.setVisible(false);
            exportButton.setManaged(false);

            // Overlay a VOIDED banner across the receipt
            Node rootNode = closeButton.getScene().getRoot();
            if (!(rootNode instanceof StackPane root)) {
                log.warn("Receipt root is not a StackPane — cannot add VOIDED banner");
                return;
            }

            Label voidedBanner = new Label("VOIDED");
            voidedBanner.setStyle(
                "-fx-font-size: 72px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: rgba(168,50,50,0.25);" +
                "-fx-rotate: -20;");
            voidedBanner.setMouseTransparent(true);

            StackPane bannerWrap = new StackPane(voidedBanner);
            bannerWrap.setMouseTransparent(true);
            bannerWrap.setPickOnBounds(false);
            bannerWrap.setAlignment(Pos.CENTER);

            root.getChildren().add(bannerWrap);

            // Add void metadata to the receipt footer
            String voidedBy = p.getVoidedByUsername() != null
                ? p.getVoidedByUsername() : "unknown";
            String voidedAt = p.getVoidedAt() != null
                ? p.getVoidedAt().toString() : "—";
            String reason = p.getVoidReason() != null ? p.getVoidReason() : "—";

            Label voidInfo = new Label(
                "VOIDED on " + voidedAt + " by " + voidedBy + " — " + reason);
            voidInfo.setWrapText(true);
            voidInfo.setMaxWidth(440);
            voidInfo.setStyle(
                "-fx-text-fill: -fx-danger;" +
                "-fx-font-size: 11px;" +
                "-fx-font-style: italic;" +
                "-fx-background-color: rgba(168,50,50,0.08);" +
                "-fx-padding: 8 10 8 10;" +
                "-fx-background-radius: 6;");

            VBox rootVBox = findFirstVBox(root);
            if (rootVBox != null) {
                VBox.setMargin(voidInfo, new Insets(12, 0, 0, 0));
                rootVBox.getChildren().add(voidInfo);
            }

        } catch (Exception e) {
            log.warn("Could not apply VOIDED styling to receipt", e);
        }
    }

    /** Finds the first VBox inside a container — used to append the void note. */
    private VBox findFirstVBox(Parent parent) {
        if (parent == null) return null;
        if (parent instanceof VBox v) return v;
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Parent p) {
                VBox found = findFirstVBox(p);
                if (found != null) return found;
            }
        }
        return null;
    }

    /* =================================================================== */

    /** Reads org name/address/contact from system_settings. */
    private void loadOrganizationInfo() {
        String name = readSetting("org.name", "Property Management");
        String address = readSetting("org.address", "");
        String phone = readSetting("org.phone", "");
        String email = readSetting("org.email", "");

        orgNameLabel.setText(name);
        orgAddressLabel.setText(address);
        orgContactLabel.setText(
            (phone.isBlank() ? "" : phone) + (email.isBlank() ? "" :
                (phone.isBlank() ? "" : "  ·  ") + email));
    }

    private String readSetting(String key, String fallback) {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT value FROM system_settings WHERE key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String v = rs.getString(1);
                    return v != null ? v : fallback;
                }
            }
        } catch (SQLException e) {
            log.warn("readSetting({}) failed", key, e);
        }
        return fallback;
    }

    /* =================================================================== */

    private void handlePrint() {
        try {
            javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
            if (job == null) {
                showError("No printer available.");
                return;
            }
            if (!job.showPrintDialog(printButton.getScene().getWindow())) return;

            Node root = printButton.getScene().getRoot();
            boolean ok = job.printPage(root);
            if (ok) {
                job.endJob();
            } else {
                showError("Printing failed.");
            }
        } catch (Exception e) {
            log.error("Print failed", e);
            showError("Unable to print the receipt.");
        }
    }

    /* =================================================================== */

    private void handleExportPdf() {
        if (payment == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Receipt as PDF");
        chooser.setInitialFileName(payment.getReceiptNumber() + ".pdf");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

        File file = chooser.showSaveDialog(exportButton.getScene().getWindow());
        if (file == null) return;

        try {
            Node root = printButton.getScene().getRoot();
            SnapshotParameters params = new SnapshotParameters();
            params.setTransform(Transform.scale(2, 2));
            WritableImage fxImage = root.snapshot(params, null);

            BufferedImage awtImage = SwingFXUtils.fromFXImage(fxImage, null);
            if (awtImage == null) {
                showError("Unable to render the receipt.");
                return;
            }

            try (PDDocument doc = new PDDocument()) {
                PDPage page = new PDPage(new PDRectangle(
                    awtImage.getWidth() / 2f,
                    awtImage.getHeight() / 2f));
                doc.addPage(page);

                PDImageXObject img = PDImageXObject.createFromByteArray(
                    doc, toPngBytes(awtImage), "receipt");

                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.drawImage(img, 0, 0, page.getMediaBox().getWidth(),
                                 page.getMediaBox().getHeight());
                }

                doc.save(file);
            }

            log.info("Exported receipt {} to {}",
                     payment.getReceiptNumber(), file.getAbsolutePath());

            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Receipt Exported");
            a.setHeaderText("Saved successfully");
            a.setContentText(file.getAbsolutePath());
            a.showAndWait();

        } catch (Exception e) {
            log.error("PDF export failed", e);
            showError("Unable to export the receipt as PDF.");
        }
    }

    private byte[] toPngBytes(BufferedImage img) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    /* =================================================================== */

    @FXML
    private void handleClose() {
        Stage s = (Stage) closeButton.getScene().getWindow();
        s.close();
    }

    private void showError(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error");
        a.setHeaderText("Something went wrong");
        a.setContentText(message);
        a.showAndWait();
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMaximumFractionDigits(0);
        return "Ksh " + nf.format(amount);
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }
}
