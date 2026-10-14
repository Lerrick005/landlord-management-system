package com.propertymanager.controller;

import com.propertymanager.model.Tenant;
import com.propertymanager.model.Tenancy;
import com.propertymanager.service.TenancyService;
import com.propertymanager.service.TenantService;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for tenants.fxml — the tenant list screen.
 */
public class TenantListController {

    private static final Logger log = LoggerFactory.getLogger(TenantListController.class);

    /* ---- FXML-injected ---- */
    @FXML private Button registerTenantButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Label countLabel;

    @FXML private TableView<Tenant> tenantsTable;
    @FXML private TableColumn<Tenant, String> tenantNumberColumn;
    @FXML private TableColumn<Tenant, String> fullNameColumn;
    @FXML private TableColumn<Tenant, String> phoneColumn;
    @FXML private TableColumn<Tenant, String> nationalIdColumn;
    @FXML private TableColumn<Tenant, String> roomColumn;
    @FXML private TableColumn<Tenant, String> statusColumn;
    @FXML private TableColumn<Tenant, Void>   actionsColumn;

    @FXML private VBox emptyState;
    @FXML private Button emptyRegisterButton;

    /* ---- Services ---- */
    private final TenantService tenantService = new TenantService();
    private final TenancyService tenancyService = new TenancyService();

    /* ---- State ---- */
    private final ObservableList<Tenant> data = FXCollections.observableArrayList();
    private PauseTransition searchDebounce;

    /** Cache of tenant id -> current room label (e.g. "K1"). */
    private final Map<Long, String> currentRoomByTenant = new HashMap<>();

    /** Cache of tenant id -> active tenancy (used by end/assign actions). */
    private final Map<Long, Tenancy> activeTenancyByTenant = new HashMap<>();

    /* =================================================================== */

    @FXML
    private void initialize() {
        applyIcons();
        setupFilters();
        setupSearch();
        setupColumns();

        tenantsTable.setItems(data);
        tenantsTable.setPlaceholder(new Label(""));

        refresh();
    }

    /* =================================================================== */

    private void applyIcons() {
        registerTenantButton.setGraphic(new FontIcon("fas-user-plus"));
        registerTenantButton.setGraphicTextGap(6);

        emptyRegisterButton.setGraphic(new FontIcon("fas-user-plus"));
        emptyRegisterButton.setGraphicTextGap(6);
    }

    /* =================================================================== */

    private void setupFilters() {
        statusFilter.setItems(FXCollections.observableArrayList(
            "All", "Active", "Left"));
        statusFilter.getSelectionModel().select("All");
        statusFilter.valueProperty().addListener((o, a, b) -> refresh());
    }

    /* =================================================================== */

    private void setupSearch() {
        searchDebounce = new PauseTransition(Duration.millis(250));
        searchDebounce.setOnFinished(e -> refresh());

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            searchDebounce.playFromStart();
        });
    }

    /* =================================================================== */

    private void setupColumns() {
        tenantNumberColumn.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getTenantNumber()));

        fullNameColumn.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getFullName()));

        phoneColumn.setCellValueFactory(c ->
            new SimpleStringProperty(
                c.getValue().getPhone() != null ? c.getValue().getPhone() : "—"));

        nationalIdColumn.setCellValueFactory(c ->
            new SimpleStringProperty(
                c.getValue().getNationalId() != null ? c.getValue().getNationalId() : "—"));

        // Room column — reads from the cache populated during refresh()
        roomColumn.setCellValueFactory(c -> {
            String room = currentRoomByTenant.get(c.getValue().getId());
            return new SimpleStringProperty(room != null ? room : "—");
        });

        // Status badge
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Tenant t = getTableRow().getItem();
                Label badge = new Label(t.getStatus());
                badge.getStyleClass().add("badge");
                badge.getStyleClass().add(
                    t.isActive() ? "badge-success" : "badge-neutral");
                setGraphic(badge);
                setText(null);
            }
        });

        // Actions — compact View / Edit / Assign / End buttons
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button viewBtn   = compactButton("View",   "fas-eye");
            private final Button editBtn   = compactButton("Edit",   "fas-pen");
            private final Button assignBtn = compactButton("Assign", "fas-door-open");
            private final Button endBtn    = compactButton("End",    "fas-sign-out-alt");
            private final HBox box = new HBox(4, viewBtn, editBtn, assignBtn, endBtn);

            {
                viewBtn.getStyleClass().add("button");
                editBtn.getStyleClass().add("button");
                assignBtn.getStyleClass().add("button");
                endBtn.getStyleClass().add("button-danger");

                box.setAlignment(Pos.CENTER_LEFT);

                viewBtn.setOnAction(e -> {
                    Tenant t = rowTenant();
                    if (t != null) showProfile(t);
                });
                editBtn.setOnAction(e -> {
                    Tenant t = rowTenant();
                    if (t != null) openTenantForm(t);
                });
                assignBtn.setOnAction(e -> {
                    Tenant t = rowTenant();
                    if (t != null) openAssignDialog(t);
                });
                endBtn.setOnAction(e -> {
                    Tenant t = rowTenant();
                    if (t != null) confirmEndTenancy(t);
                });
            }

            private Tenant rowTenant() {
                return getTableRow() == null ? null : getTableRow().getItem();
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || rowTenant() == null) {
                    setGraphic(null);
                    return;
                }
                Tenant t = rowTenant();
                boolean hasRoom = currentRoomByTenant.get(t.getId()) != null;

                // Assign visible only when no room; End visible only when has room
                assignBtn.setVisible(!hasRoom);
                assignBtn.setManaged(!hasRoom);
                endBtn.setVisible(hasRoom);
                endBtn.setManaged(hasRoom);

                setGraphic(box);
            }
        });
    }

    /* =================================================================== */

    /** Creates a small, compact action button with an icon + short label. */
    private Button compactButton(String label, String iconCode) {
        Button b = new Button(label);
        b.setGraphic(new FontIcon(iconCode));
        b.setGraphicTextGap(5);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setMaxWidth(Region.USE_PREF_SIZE);
        b.setPrefHeight(26);
        b.setStyle("-fx-padding: 3 9 3 9; -fx-font-size: 11.5px; -fx-background-radius: 5;");
        return b;
    }

    /* =================================================================== */

    private void refresh() {
        try {
            String query = searchField.getText();
            String filter = statusFilter.getValue();

            List<Tenant> list = tenantService.search(query);

            if ("Active".equals(filter)) {
                list = list.stream().filter(Tenant::isActive).toList();
            } else if ("Left".equals(filter)) {
                list = list.stream().filter(Tenant::hasLeft).toList();
            }

            // Rebuild the room lookup map for the visible tenants
            currentRoomByTenant.clear();
            activeTenancyByTenant.clear();
            for (Tenant t : list) {
                Optional<Tenancy> active = tenancyService.findActiveByTenant(t.getId());
                if (active.isPresent()) {
                    Tenancy ten = active.get();
                    currentRoomByTenant.put(t.getId(), ten.getUnitLabel());
                    activeTenancyByTenant.put(t.getId(), ten);
                }
            }

            data.setAll(list);
            countLabel.setText(list.size() + (list.size() == 1 ? " tenant" : " tenants"));

            boolean empty = list.isEmpty();
            emptyState.setVisible(empty);
            emptyState.setManaged(empty);
            tenantsTable.setVisible(!empty);
            tenantsTable.setManaged(!empty);

        } catch (RuntimeException e) {
            log.error("Failed to load tenants", e);
            showError("Unable to load tenants. Please try again.");
        }
    }

    /* =================================================================== */

    @FXML
    private void handleRegisterTenant() {
        openTenantForm(null);
    }

    /* =================================================================== */

    private void openTenantForm(Tenant tenant) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/tenant-form.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(
                getClass().getResource("/css/app.css").toExternalForm());

            TenantFormController controller = loader.getController();
            controller.setTenant(tenant);

            Stage dialog = new Stage();
            dialog.setTitle(tenant == null ? "Register Tenant" : "Edit Tenant");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(tenantsTable.getScene().getWindow());
            dialog.setScene(scene);
            dialog.setResizable(false);
            dialog.setOnShown(e -> dialog.centerOnScreen());
            dialog.showAndWait();

            if (controller.isSaved()) {
                refresh();
            }
        } catch (IOException e) {
            log.error("Failed to open tenant form", e);
            showError("Unable to open the tenant form.");
        }
    }

    /* =================================================================== */

    /** Opens the Assign Room dialog for a tenant. */
    private void openAssignDialog(Tenant t) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/assign-room.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(
                getClass().getResource("/css/app.css").toExternalForm());

            AssignRoomController controller = loader.getController();
            controller.setTenant(t);

            Stage dialog = new Stage();
            dialog.setTitle("Assign Room to " + t.getFullName());
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(tenantsTable.getScene().getWindow());
            dialog.setScene(scene);
            dialog.setResizable(false);
            dialog.setOnShown(e -> dialog.centerOnScreen());
            dialog.showAndWait();

            if (controller.isSaved()) {
                refresh();
            }
        } catch (IOException e) {
            log.error("Failed to open assign-room dialog", e);
            showError("Unable to open the room assignment dialog.");
        }
    }

    /* =================================================================== */

    /** Ends the tenant's active tenancy (moves them out, frees the room). */
    private void confirmEndTenancy(Tenant t) {
        Tenancy active = activeTenancyByTenant.get(t.getId());
        if (active == null) {
            showError("This tenant has no active room.");
            return;
        }

        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("End Tenancy");
        a.setHeaderText("End tenancy for " + t.getFullName() + "?");
        a.setContentText(
            "Room: " + active.getUnitLabel() + "\n"
            + "Move-in: " + active.getMoveInDate() + "\n\n"
            + "The room will become VACANT and the tenant will be marked LEFT.");

        ButtonType endBtn = new ButtonType("End Tenancy");
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonType.CANCEL.getButtonData());
        a.getButtonTypes().setAll(cancelBtn, endBtn);

        Optional<ButtonType> result = a.showAndWait();
        if (result.isPresent() && result.get() == endBtn) {
            try {
                tenancyService.endTenancy(active.getId(), LocalDate.now());
                refresh();
            } catch (TenancyService.ValidationException ve) {
                showError(ve.getMessage() != null ? ve.getMessage()
                                                   : "Unable to end tenancy.");
            } catch (RuntimeException re) {
                log.error("Failed to end tenancy", re);
                showError("Unable to end the tenancy. Please try again.");
            }
        }
    }

    /* =================================================================== */

    /**
     * Simple profile view — Phase 6 will expand this with payment history.
     * For now: shows the tenant's key info and current room.
     */
    private void showProfile(Tenant t) {
        Tenancy active = activeTenancyByTenant.get(t.getId());
        String room = active != null ? active.getUnitLabel() : "—";
        String propertyName = active != null ? active.getPropertyLabel() : "—";
        String moveIn = active != null && active.getMoveInDate() != null
            ? active.getMoveInDate().toString() : "—";
        String rent = active != null
            ? "KSh " + active.getMonthlyRent().stripTrailingZeros().toPlainString()
            : "—";

        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Tenant");
        a.setHeaderText(t.getFullName() + " (" + t.getTenantNumber() + ")");
        a.setContentText(
            "National ID: " + safe(t.getNationalId()) + "\n"
            + "Phone: " + safe(t.getPhone()) + "\n"
            + "Status: " + safe(t.getStatus()) + "\n\n"
            + "Property: " + propertyName + "\n"
            + "Room: " + room + "\n"
            + "Monthly Rent: " + rent + "\n"
            + "Move-in Date: " + moveIn);
        a.showAndWait();
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    /* =================================================================== */

    private void showError(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error");
        a.setHeaderText("Something went wrong");
        a.setContentText(message);
        a.showAndWait();
    }
}
