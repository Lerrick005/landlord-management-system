package com.propertymanager.controller;

import com.propertymanager.reports.ReportService;
import com.propertymanager.reports.ReportService.ReportData;
import com.propertymanager.reports.ReportService.ReportParams;
import com.propertymanager.reports.ReportType;
import com.propertymanager.reports.ExcelExporter;
import com.propertymanager.reports.PdfExporter;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Controller for report-view.fxml.
 *
 * Renders any report generically: the table columns and rows
 * come from the ReportData object the ReportService returns.
 * Filters shown depend on the ReportType.
 */
public class ReportViewerController {

    private static final Logger log = LoggerFactory.getLogger(ReportViewerController.class);

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    /* ---- FXML-injected ---- */
    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;

    @FXML private HBox  filterBar;
    @FXML private Label filterLabel;

    @FXML private ComboBox<String> monthFilter;
    @FXML private ComboBox<Integer> yearFilter;
    @FXML private DatePicker fromDatePicker;
    @FXML private Label dateRangeSeparator;
    @FXML private DatePicker toDatePicker;

    @FXML private Button applyButton;
    @FXML private Label  rowCountLabel;

    @FXML private TableView<ObservableList<String>> reportTable;
    @FXML private VBox emptyState;

    @FXML private Label generatedOnLabel;
    @FXML private Button printButton;
    @FXML private Button exportExcelButton;
    @FXML private Button exportPdfButton;
    @FXML private Button closeButton;

    /* ---- Services ---- */
    private final ReportService reportService = new ReportService();

    /* ---- State ---- */
    private ReportType reportType;
    private ReportData lastData;

    /* =================================================================== */

    @FXML
    private void initialize() {
        // Wire action buttons
        applyButton.setOnAction(e -> reload());
        printButton.setOnAction(e -> handlePrint());
        exportExcelButton.setOnAction(e -> handleExportExcel());
        exportPdfButton.setOnAction(e -> handleExportPdf());

        reportTable.setPlaceholder(new Label(""));
    }

    /* =================================================================== */

    /** Called by the caller BEFORE the dialog is shown. */
    public void setReportType(ReportType type) {
        this.reportType = type;

        titleLabel.setText(type.getTitle());
        subtitleLabel.setText(type.getDescription());

        configureFiltersFor(type);
        reload();
    }

    /* =================================================================== */

    private void configureFiltersFor(ReportType type) {
        // Default: hide everything
        monthFilter.setVisible(false);       monthFilter.setManaged(false);
        yearFilter.setVisible(false);        yearFilter.setManaged(false);
        fromDatePicker.setVisible(false);    fromDatePicker.setManaged(false);
        toDatePicker.setVisible(false);      toDatePicker.setManaged(false);
        dateRangeSeparator.setVisible(false);dateRangeSeparator.setManaged(false);
        filterBar.setVisible(false);         filterBar.setManaged(false);
        applyButton.setVisible(false);       applyButton.setManaged(false);

        switch (type) {
            case RENT_COLLECTION, ARREARS -> {
                showPeriodFilters();
            }
            case PAYMENTS -> {
                showDateRangeFilters();
            }
            // TENANTS, PROPERTIES, OCCUPANCY have no filters
            default -> { /* nothing */ }
        }
    }

    private void showPeriodFilters() {
        // Populate month list
        monthFilter.setItems(FXCollections.observableArrayList(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"));
        monthFilter.getSelectionModel().select(LocalDate.now().getMonthValue() - 1);

        // Populate year list
        int now = LocalDate.now().getYear();
        yearFilter.setItems(FXCollections.observableArrayList(
            now - 1, now, now + 1));
        yearFilter.getSelectionModel().select(Integer.valueOf(now));

        monthFilter.setVisible(true);      monthFilter.setManaged(true);
        yearFilter.setVisible(true);       yearFilter.setManaged(true);
        filterBar.setVisible(true);        filterBar.setManaged(true);
        applyButton.setVisible(true);      applyButton.setManaged(true);
        filterLabel.setText("Period:");
    }

    private void showDateRangeFilters() {
        // Default: last 30 days
        fromDatePicker.setValue(LocalDate.now().minusMonths(1));
        toDatePicker.setValue(LocalDate.now());

        fromDatePicker.setVisible(true);   fromDatePicker.setManaged(true);
        toDatePicker.setVisible(true);     toDatePicker.setManaged(true);
        dateRangeSeparator.setVisible(true); dateRangeSeparator.setManaged(true);
        filterBar.setVisible(true);        filterBar.setManaged(true);
        applyButton.setVisible(true);      applyButton.setManaged(true);
        filterLabel.setText("Date range:");
    }

    /* =================================================================== */

    private void reload() {
        if (reportType == null) return;

        try {
            ReportParams params = new ReportParams();

            // Populate params based on which filters are visible
            if (monthFilter.isVisible() && monthFilter.getValue() != null
                    && yearFilter.getValue() != null) {
                int monthNum = monthFilter.getSelectionModel().getSelectedIndex() + 1;
                params.month = YearMonth.of(yearFilter.getValue(), monthNum);
            }

            if (fromDatePicker.isVisible()) {
                params.fromDate = fromDatePicker.getValue();
                params.toDate = toDatePicker.getValue();
            }

            ReportData data = reportService.build(reportType, params);
            this.lastData = data;

            buildTable(data);

            rowCountLabel.setText(
                data.rows.size() + (data.rows.size() == 1 ? " row" : " rows"));

            generatedOnLabel.setText(
                "Generated: " + LocalDate.now().format(DATE_FMT));

            boolean empty = data.rows.isEmpty();
            emptyState.setVisible(empty);
            emptyState.setManaged(empty);
            reportTable.setVisible(!empty);
            reportTable.setManaged(!empty);

        } catch (RuntimeException e) {
            log.error("Failed to build report {}", reportType, e);
            showError("Unable to build the report. Please try again.");
        }
    }

    /* =================================================================== */

    private void buildTable(ReportData data) {
        reportTable.getColumns().clear();
        reportTable.getItems().clear();

        if (data.columns.isEmpty()) return;

        // Build columns dynamically
        for (int colIdx = 0; colIdx < data.columns.size(); colIdx++) {
            final int ci = colIdx;
            TableColumn<ObservableList<String>, String> column =
                new TableColumn<>(data.columns.get(colIdx));

            column.setCellValueFactory(cellData -> {
                ObservableList<String> row = cellData.getValue();
                String value = (ci < row.size()) ? row.get(ci) : "";
                return new ReadOnlyStringWrapper(value);
            });

            column.setSortable(false);

            // Right-align numeric-looking columns for readability
            String header = data.columns.get(colIdx).toUpperCase();
            if (header.contains("(KSH)") || header.endsWith("UNITS")
                    || header.equals("OCCUPIED") || header.equals("VACANT")
                    || header.equals("TOTAL UNITS") || header.equals("OCCUPANCY %")
                    || header.equals("COLLECTION %") || header.endsWith("ROOM")) {
                column.setStyle("-fx-alignment: CENTER-RIGHT;");
            }

            reportTable.getColumns().add(column);
        }

        // Populate rows
        for (List<String> row : data.rows) {
            reportTable.getItems().add(FXCollections.observableArrayList(row));
        }
    }

    /* =================================================================== */

    @FXML
    private void handleApplyFilters() {
        reload();
    }

    /* =================================================================== */

    @FXML
    private void handleClose() {
        Stage s = (Stage) closeButton.getScene().getWindow();
        s.close();
    }

    /* =================================================================== */

    private void handlePrint() {
        if (lastData == null) return;
        try {
            javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
            if (job == null || !job.showPrintDialog(printButton.getScene().getWindow())) {
                return;
            }
            boolean ok = job.printPage(reportTable);
            if (ok) job.endJob();
            else showError("Printing failed.");
        } catch (Exception e) {
            log.error("Print failed", e);
            showError("Unable to print the report.");
        }
    }

    /* =================================================================== */

    private void handleExportExcel() {
        if (lastData == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Report as Excel");
        chooser.setInitialFileName(safeFileName(lastData.title) + ".xlsx");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));

        File file = chooser.showSaveDialog(exportExcelButton.getScene().getWindow());
        if (file == null) return;

        try {
            ExcelExporter.export(lastData, file);
            showInfo("Exported", "Report saved to:\n" + file.getAbsolutePath());
        } catch (Exception e) {
            log.error("Excel export failed", e);
            showError("Unable to export the report as Excel.");
        }
    }

    /* =================================================================== */

    private void handleExportPdf() {
        if (lastData == null) return;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Report as PDF");
        chooser.setInitialFileName(safeFileName(lastData.title) + ".pdf");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

        File file = chooser.showSaveDialog(exportPdfButton.getScene().getWindow());
        if (file == null) return;

        try {
            PdfExporter.export(lastData, file);
            showInfo("Exported", "Report saved to:\n" + file.getAbsolutePath());
        } catch (Exception e) {
            log.error("PDF export failed", e);
            showError("Unable to export the report as PDF.");
        }
    }

    /* =================================================================== */

    private String safeFileName(String title) {
        if (title == null) return "report";
        return title.replaceAll("[^a-zA-Z0-9-_ ]", "").replace(' ', '_');
    }

    private void showInfo(String header, String body) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(header);
        a.setHeaderText(header);
        a.setContentText(body);
        a.showAndWait();
    }

    private void showError(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error");
        a.setHeaderText("Something went wrong");
        a.setContentText(message);
        a.showAndWait();
    }
}
