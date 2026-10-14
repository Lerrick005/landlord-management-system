package com.propertymanager.controller;

import com.propertymanager.model.Property;
import com.propertymanager.model.Unit;
import com.propertymanager.service.UnitService;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
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
import javafx.scene.layout.StackPane;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Controller for units.fxml — the Rooms screen for a specific property.
 *
 * Lifecycle:
 *   1. FXMLLoader.load() -> initialize() runs (wires columns, filters, search)
 *   2. Caller invokes setProperty(property) -> we finish setup + first refresh
 */
public class UnitListController {

    private static final Logger log = LoggerFactory.getLogger(UnitListController.class);

    /* ---- FXML-injected ---- */
    @FXML private Button backButton;
    @FXML private Label pageTitleLabel;
    @FXML private Label pageSubtitleLabel;
    @FXML private Button newRoomButton;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Label countLabel;

    @FXML private TableView<Unit> unitsTable;
    @FXML private TableColumn<Unit, String> houseNumberColumn;
    @FXML private TableColumn<Unit, String> typeColumn;
    @FXML private TableColumn<Unit, String> rentColumn;
    @FXML private TableColumn<Unit, String> depositColumn;
    @FXML private TableColumn<Unit, String> statusColumn;
    @FXML private TableColumn<Unit, Void>   actionsColumn;

    @FXML private VBox emptyState;
    @FXML private Button emptyNewRoomButton;

    /* ---- Services ---- */
    private final UnitService unitService = new UnitService();

    /* ---- State ---- */
    private Property property;
    private final ObservableList<Unit> data = FXCollections.observableArrayList();
    private PauseTransition searchDebounce;
    private boolean ready = false;

    /* =================================================================== */

    @FXML
    private void initialize() {
        applyIcons();
        setupFilters();
        setupSearch();
        setupColumns();

        unitsTable.setItems(data);
        unitsTable.setPlaceholder(new Label(""));
    }

    /* =================================================================== */

    /**
     * Called by the caller after FXMLLoader.load() returns.
     * Triggers the full setup and first data load.
     */
    public void setProperty(Property property) {
        this.property = property;
        if (property == null) {
            log.error("setProperty(null) called on UnitListController");
            return;
        }

        pageTitleLabel.setText(property.getName() + " — Rooms");
        updateSubtitle(0, 0, 0);

        ready = true;
        refresh();
    }

    /* =================================================================== */

    private void applyIcons() {
        backButton.setGraphic(new FontIcon("fas-arrow-left"));
        backButton.setGraphicTextGap(6);

        newRoomButton.setGraphic(new FontIcon("fas-plus"));
        newRoomButton.setGraphicTextGap(6);

        emptyNewRoomButton.setGraphic(new FontIcon("fas-plus"));
        emptyNewRoomButton.setGraphicTextGap(6);
    }

    /* =================================================================== */

    private void setupFilters() {
        statusFilter.setItems(FXCollections.observableArrayList(
            "All", "Vacant", "Occupied", "Maintenance"));
        statusFilter.getSelectionModel().select("All");
        statusFilter.valueProperty().addListener((o, a, b) -> {
            if (ready) refresh();
        });
    }

    /* =================================================================== */

    private void setupSearch() {
        searchDebounce = new PauseTransition(Duration.millis(250));
        searchDebounce.setOnFinished(e -> {
            if (ready) refresh();
        });

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (ready) searchDebounce.playFromStart();
        });
    }

    /* =================================================================== */

    private void setupColumns() {
        houseNumberColumn.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getHouseNumber()));

        typeColumn.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getUnitTypeDisplay()));

        rentColumn.setCellValueFactory(c ->
            new SimpleStringProperty(formatMoney(c.getValue().getMonthlyRent())));

        depositColumn.setCellValueFactory(c ->
            new SimpleStringProperty(formatMoney(c.getValue().getDepositAmount())));

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
                Unit u = getTableRow().getItem();
                Label badge = new Label(u.getStatus());
                badge.getStyleClass().add("badge");
                badge.getStyleClass().add(switch (u.getStatus()) {
                    case Unit.STATUS_OCCUPIED    -> "badge-warning";
                    case Unit.STATUS_VACANT      -> "badge-success";
                    case Unit.STATUS_MAINTENANCE -> "badge-danger";
                    default -> "badge-neutral";
                });
                setGraphic(badge);
                setText(null);
            }
        });

        // Actions — Edit / Delete with real FontAwesome icons
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn   = new Button("Edit");
            private final Button deleteBtn = new Button("Delete");
            private final HBox box = new HBox(6, editBtn, deleteBtn);

            {
                editBtn.getStyleClass().add("button");
                deleteBtn.getStyleClass().add("button-danger");

                editBtn.setGraphic(new FontIcon("fas-pen"));
                deleteBtn.setGraphic(new FontIcon("fas-trash"));
                editBtn.setGraphicTextGap(6);
                deleteBtn.setGraphicTextGap(6);

                editBtn.setMinWidth(Region.USE_PREF_SIZE);
                deleteBtn.setMinWidth(Region.USE_PREF_SIZE);

                editBtn.setOnAction(e -> {
                    Unit u = getTableRow().getItem();
                    if (u != null) openUnitForm(u);
                });

                deleteBtn.setOnAction(e -> {
                    Unit u = getTableRow().getItem();
                    if (u != null) confirmDelete(u);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    /* =================================================================== */

    private void refresh() {
        if (property == null) return;

        try {
            String query = searchField.getText();
            List<Unit> list = unitService.search(query, property.getId());

            String filter = statusFilter.getValue();
            if (filter != null && !"All".equals(filter)) {
                String status = switch (filter) {
                    case "Vacant"      -> Unit.STATUS_VACANT;
                    case "Occupied"    -> Unit.STATUS_OCCUPIED;
                    case "Maintenance" -> Unit.STATUS_MAINTENANCE;
                    default            -> null;
                };
                if (status != null) {
                    list = list.stream().filter(u -> status.equals(u.getStatus())).toList();
                }
            }

            data.setAll(list);
            countLabel.setText(list.size() + (list.size() == 1 ? " room" : " rooms"));

            long total    = list.size();
            long vacant   = list.stream().filter(u -> Unit.STATUS_VACANT.equals(u.getStatus())).count();
            long occupied = list.stream().filter(u -> Unit.STATUS_OCCUPIED.equals(u.getStatus())).count();
            updateSubtitle(total, occupied, vacant);

            boolean empty = list.isEmpty();
            emptyState.setVisible(empty);
            emptyState.setManaged(empty);
            unitsTable.setVisible(!empty);
            unitsTable.setManaged(!empty);

        } catch (RuntimeException e) {
            log.error("Failed to load rooms", e);
            showError("Unable to load rooms. Please try again.");
        }
    }

    private void updateSubtitle(long total, long occupied, long vacant) {
        if (property == null) {
            pageSubtitleLabel.setText("");
            return;
        }
        String location = property.getLocation() == null || property.getLocation().isBlank()
            ? ""
            : property.getLocation() + " · ";
        pageSubtitleLabel.setText(
            location + total + (total == 1 ? " room" : " rooms") + " · "
            + occupied + " occupied · " + vacant + " vacant");
    }

    /* =================================================================== */

    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/properties.fxml"));
            Parent view = loader.load();

            Parent contentArea = (Parent) backButton.getScene().lookup("#contentArea");
            if (contentArea instanceof StackPane sp) {
                sp.getChildren().setAll(view);
                Label titleLabel = (Label) backButton.getScene().lookup("#pageTitleLabel");
                if (titleLabel != null) titleLabel.setText("Properties");
            }
        } catch (IOException e) {
            log.error("Failed to return to Properties", e);
            showError("Unable to return to Properties.");
        }
    }

    /* =================================================================== */

    @FXML
    private void handleNewRoom() {
        openUnitForm(null);
    }

    /* =================================================================== */

    private void openUnitForm(Unit unit) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/unit-form.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(
                getClass().getResource("/css/app.css").toExternalForm());

            UnitFormController controller = loader.getController();
            controller.setProperty(property);
            controller.setUnit(unit);

            Stage dialog = new Stage();
            dialog.setTitle(unit == null
                ? "New Room — " + property.getName()
                : "Edit Room — " + unit.getHouseNumber());
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(unitsTable.getScene().getWindow());
            dialog.setScene(scene);
            dialog.setResizable(false);
            dialog.showAndWait();

            if (controller.isSaved()) {
                refresh();
            }
        } catch (IOException e) {
            log.error("Failed to open room form", e);
            showError("Unable to open the room form.");
        }
    }

    /* =================================================================== */

    private void confirmDelete(Unit u) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Delete Room");
        a.setHeaderText("Delete room " + u.getHouseNumber() + "?");
        a.setContentText(
            "This will permanently remove the room.\n\n"
            + "If the room has a tenancy attached, the delete will be blocked.");

        ButtonType deleteBtn = new ButtonType("Delete");
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonType.CANCEL.getButtonData());
        a.getButtonTypes().setAll(cancelBtn, deleteBtn);

        Optional<ButtonType> result = a.showAndWait();
        if (result.isPresent() && result.get() == deleteBtn) {
            try {
                unitService.delete(u.getId());
                refresh();
            } catch (RuntimeException e) {
                showError(e.getMessage() != null ? e.getMessage()
                                                 : "Unable to delete room.");
            }
        }
    }

    /* =================================================================== */

    private String formatMoney(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMaximumFractionDigits(0);
        return "KSh " + nf.format(amount);
    }

    private void showError(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error");
        a.setHeaderText("Something went wrong");
        a.setContentText(message);
        a.showAndWait();
    }
}
