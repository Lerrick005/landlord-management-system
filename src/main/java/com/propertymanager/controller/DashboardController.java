package com.propertymanager.controller;

import com.propertymanager.model.Unit;
import com.propertymanager.service.DashboardService;
import com.propertymanager.service.DashboardService.DashboardData;
import com.propertymanager.service.DashboardService.OutstandingTenant;
import com.propertymanager.service.DashboardService.RecentPayment;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Controller for dashboard.fxml.
 *
 * Loads aggregated data via DashboardService and populates the stat cards,
 * occupancy donut, recent payments list, and the outstanding rent table.
 */
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    /* ---- Header ---- */
    @FXML private Label periodLabel;

    /* ---- Row 1 ---- */
    @FXML private Label totalPropertiesLabel;
    @FXML private Label totalUnitsLabel;
    @FXML private Label occupiedLabel;
    @FXML private Label vacantLabel;

    /* ---- Row 2 ---- */
    @FXML private Label expectedRentLabel;
    @FXML private Label collectedLabel;
    @FXML private Label outstandingLabel;
    @FXML private Label expectedRentCaption;
    @FXML private Label collectedCaption;
    @FXML private Label outstandingCaption;

    /* ---- Row 3 — Occupancy panel ---- */
    @FXML private StackPane occupancyChartContainer;
    @FXML private Label occupancyPctLabel;
    @FXML private Label occupancyDetailLabel;
    @FXML private Label occOccupiedLabel;
    @FXML private Label occVacantLabel;
    @FXML private Label occMaintenanceLabel;

    /* ---- Row 3 — Recent payments ---- */
    @FXML private VBox recentPaymentsList;
    @FXML private VBox recentPaymentsEmpty;

    /* ---- Row 4 — Outstanding table ---- */
    @FXML private Label outstandingSummaryLabel;
    @FXML private TableView<OutstandingTenant> outstandingTable;
    @FXML private TableColumn<OutstandingTenant, String> osTenantColumn;
    @FXML private TableColumn<OutstandingTenant, String> osRoomColumn;
    @FXML private TableColumn<OutstandingTenant, String> osPropertyColumn;
    @FXML private TableColumn<OutstandingTenant, String> osExpectedColumn;
    @FXML private TableColumn<OutstandingTenant, String> osPaidColumn;
    @FXML private TableColumn<OutstandingTenant, String> osBalanceColumn;
    @FXML private VBox outstandingEmpty;

    /* ---- Service ---- */
    private final DashboardService dashboardService = new DashboardService();

    /* ---- State ---- */
    private final ObservableList<OutstandingTenant> outstandingData =
        FXCollections.observableArrayList();

    /* =================================================================== */

    @FXML
    private void initialize() {
        setupOutstandingColumns();
        outstandingTable.setItems(outstandingData);
        outstandingTable.setPlaceholder(new Label(""));
        refresh();
    }

    /* =================================================================== */

    /** Reloads every element from the database. */
    public void refresh() {
        try {
            DashboardData d = dashboardService.load();

            // ---- Header ----
            periodLabel.setText("Overview for " + d.monthLabel);

            // ---- Row 1 — Primary cards ----
            totalPropertiesLabel.setText(String.valueOf(d.totalProperties));
            totalUnitsLabel.setText(String.valueOf(d.totalUnits));
            occupiedLabel.setText(String.valueOf(d.occupiedUnits));
            vacantLabel.setText(String.valueOf(d.vacantUnits));

            // ---- Row 2 — Financial cards ----
            expectedRentLabel.setText(formatMoney(d.expectedRent));
            collectedLabel.setText(formatMoney(d.collected));
            outstandingLabel.setText(formatMoney(d.outstanding));

            expectedRentCaption.setText("for " + d.monthLabel);
            collectedCaption.setText("for " + d.monthLabel);
            outstandingCaption.setText("for " + d.monthLabel);

            // ---- Row 3 — Occupancy panel ----
            occupancyPctLabel.setText(String.format("%.0f%%", d.occupancyPct));
            occupancyDetailLabel.setText(
                d.occupiedUnits + " of " + d.totalUnits + " rooms occupied");
            occOccupiedLabel.setText(String.valueOf(d.occupiedUnits));
            occVacantLabel.setText(String.valueOf(d.vacantUnits));
            occMaintenanceLabel.setText(String.valueOf(d.maintenanceUnits));

            // Draw the donut
            drawDonut(d.occupancyPct);

            // ---- Row 3 — Recent payments ----
            buildRecentPayments(d.recentPayments);

            // ---- Row 4 — Outstanding table ----
            outstandingData.setAll(d.outstandingTenants);
            boolean hasOutstanding = !d.outstandingTenants.isEmpty();
            outstandingTable.setVisible(hasOutstanding);
            outstandingTable.setManaged(hasOutstanding);
            outstandingEmpty.setVisible(!hasOutstanding);
            outstandingEmpty.setManaged(!hasOutstanding);

            if (hasOutstanding) {
                outstandingSummaryLabel.setText(
                    "Top " + d.outstandingTenants.size() + " tenants with unpaid rent");
            } else {
                outstandingSummaryLabel.setText("Everyone has paid");
            }

        } catch (RuntimeException e) {
            log.error("Failed to load dashboard data", e);
        }
    }

    /* =================================================================== */
    /*  OCCUPANCY DONUT                                                    */
    /* =================================================================== */

    private void drawDonut(double pct) {
        occupancyChartContainer.getChildren().clear();

        double size = 120;
        double stroke = 14;

        // Background ring (light grey)
        javafx.scene.shape.Circle bg = new javafx.scene.shape.Circle(size / 2 - stroke / 2);
        bg.setStroke(javafx.scene.paint.Color.web("#ecebe7"));
        bg.setStrokeWidth(stroke);
        bg.setFill(javafx.scene.paint.Color.TRANSPARENT);

        // Foreground arc
        javafx.scene.shape.Arc arc = new javafx.scene.shape.Arc();
        arc.setCenterX(0);
        arc.setCenterY(0);
        arc.setRadiusX(size / 2 - stroke / 2);
        arc.setRadiusY(size / 2 - stroke / 2);
        arc.setStartAngle(90);
        arc.setLength(-pct * 3.6);      // 100% = 360°
        arc.setType(javafx.scene.shape.ArcType.OPEN);
        arc.setStrokeWidth(stroke);
        arc.setStroke(javafx.scene.paint.Color.web(goldIfGood(pct)));
        arc.setFill(null);
        arc.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);

        // Center label
        Label center = new Label(String.format("%.0f%%", pct));
        center.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: -fx-charcoal;");

        StackPane ring = new StackPane(bg, arc);
        StackPane full = new StackPane(ring, center);

        occupancyChartContainer.getChildren().add(full);
    }

    /** Green if occupancy >= 80%, gold if 50–80%, red if <50%. */
    private String goldIfGood(double pct) {
        if (pct >= 80) return "#2e7d4f";   // success
        if (pct >= 50) return "#c9a24a";   // gold
        return "#a83232";                  // danger
    }

    /* =================================================================== */
    /*  RECENT PAYMENTS LIST                                               */
    /* =================================================================== */

    private void buildRecentPayments(List<RecentPayment> payments) {
        // Clear everything except the empty-state child
        recentPaymentsList.getChildren().removeIf(n -> n != recentPaymentsEmpty);

        if (payments.isEmpty()) {
            recentPaymentsEmpty.setVisible(true);
            recentPaymentsEmpty.setManaged(true);
            return;
        }

        recentPaymentsEmpty.setVisible(false);
        recentPaymentsEmpty.setManaged(false);

        for (RecentPayment p : payments) {
            recentPaymentsList.getChildren().add(buildRecentPaymentRow(p));
        }
    }

    private HBox buildRecentPaymentRow(RecentPayment p) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6, 0, 6, 0));

        // Left side: tenant name + room
        VBox left = new VBox(2);
        Label name = new Label(safe(p.tenantName));
        name.setStyle("-fx-font-weight: bold; -fx-text-fill: -fx-charcoal; -fx-font-size: 13px;");

        String roomText = (p.room == null || p.room.isBlank())
            ? "—"
            : "Room " + p.room;
        Label sub = new Label(roomText + "  ·  " + safe(p.method) + "  ·  " + p.getDateLabel());
        sub.getStyleClass().add("label-muted");

        left.getChildren().addAll(name, sub);

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        // Amount
        Label amount = new Label(formatMoney(p.amount));
        amount.setStyle(
            "-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: -fx-gold-deep;");

        row.getChildren().addAll(left, spacer, amount);
        return row;
    }

    /* =================================================================== */
    /*  OUTSTANDING TABLE COLUMNS                                          */
    /* =================================================================== */

    private void setupOutstandingColumns() {
        osTenantColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(safe(c.getValue().tenantName)));

        osRoomColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(safe(c.getValue().room)));

        osPropertyColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(safe(c.getValue().property)));

        osExpectedColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(formatMoney(c.getValue().expected)));
        osExpectedColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        osPaidColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(formatMoney(c.getValue().paid)));
        osPaidColumn.setStyle("-fx-alignment: CENTER-RIGHT;");

        osBalanceColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(formatMoney(c.getValue().outstanding)));
        osBalanceColumn.setStyle("-fx-alignment: CENTER-RIGHT; -fx-text-fill: -fx-danger; -fx-font-weight: bold;");
    }

    /* =================================================================== */

    private String formatMoney(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMaximumFractionDigits(0);
        return "KSh " + nf.format(amount);
    }

    private String safe(String s) { return (s == null || s.isBlank()) ? "—" : s; }
}
