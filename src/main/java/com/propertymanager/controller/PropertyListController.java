package com.propertymanager.controller;

import com.propertymanager.model.Property;
import com.propertymanager.service.PropertyService;
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
import java.util.List;
import java.util.Optional;

/**
 * Controller for properties.fxml — the properties list screen.
 */
public class PropertyListController {

    private static final Logger log = LoggerFactory.getLogger(PropertyListController.class);

    /* ---- FXML-injected ---- */
    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Label countLabel;

    @FXML private TableView<Property> propertiesTable;
    @FXML private TableColumn<Property, String> codeColumn;
    @FXML private TableColumn<Property, String> nameColumn;
    @FXML private TableColumn<Property, String> locationColumn;
    @FXML private TableColumn<Property, String> unitsColumn;
    @FXML private TableColumn<Property, String> statusColumn;
    @FXML private TableColumn<Property, Void>   actionsColumn;

    @FXML private VBox emptyState;

    /* ---- Services ---- */
    private final PropertyService propertyService = new PropertyService();
    private final UnitService unitService = new UnitService();

    /* ---- State ---- */
    private final ObservableList<Property> data = FXCollections.observableArrayList();
    private PauseTransition searchDebounce;

    /* =================================================================== */

    @FXML
    private void initialize() {
        setupFilters();
        setupColumns();
        setupSearch();

        propertiesTable.setItems(data);
        propertiesTable.setPlaceholder(new Label(""));

        refresh();
    }

    /* =================================================================== */

    private void setupFilters() {
        statusFilter.setItems(FXCollections.observableArrayList(
            "All", "Active", "Inactive"));
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
        codeColumn.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getCode()));

        nameColumn.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getName()));

        locationColumn.setCellValueFactory(c ->
            new SimpleStringProperty(
                c.getValue().getLocation() != null ? c.getValue().getLocation() : "—"));

        unitsColumn.setCellValueFactory(c -> {
            Property p = c.getValue();
            long count = unitService.listByProperty(p.getId()).size();
            return new SimpleStringProperty(String.valueOf(count));
        });

        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Property p = getTableRow().getItem();
                Label badge = new Label(p.getStatus());
                badge.getStyleClass().add("badge");
                badge.getStyleClass().add(
                    p.isActive() ? "badge-success" : "badge-neutral");
                setGraphic(badge);
                setText(null);
            }
        });

        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button unitsBtn = new Button("Units");
            private final Button deleteBtn = new Button("Delete");
            private final HBox box = new HBox(6, editBtn, unitsBtn, deleteBtn);

            {
                editBtn.getStyleClass().add("button");
                unitsBtn.getStyleClass().add("button");
                deleteBtn.getStyleClass().add("button-danger");

                editBtn.setGraphic(new FontIcon("fas-pen"));
                unitsBtn.setGraphic(new FontIcon("fas-building"));
                deleteBtn.setGraphic(new FontIcon("fas-trash"));

                editBtn.setGraphicTextGap(6);
                unitsBtn.setGraphicTextGap(6);
                deleteBtn.setGraphicTextGap(6);

                editBtn.setMinWidth(Region.USE_PREF_SIZE);
                unitsBtn.setMinWidth(Region.USE_PREF_SIZE);
                deleteBtn.setMinWidth(Region.USE_PREF_SIZE);

                editBtn.setOnAction(e -> {
                    Property p = getTableRow().getItem();
                    if (p != null) openPropertyForm(p);
                });

                unitsBtn.setOnAction(e -> {
                    Property p = getTableRow().getItem();
                    if (p != null) openUnitsFor(p);
                });

                deleteBtn.setOnAction(e -> {
                    Property p = getTableRow().getItem();
                    if (p != null) confirmDelete(p);
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
        try {
            String query = searchField.getText();
            String filter = statusFilter.getValue();

            List<Property> list = propertyService.search(query);

            if ("Active".equals(filter)) {
                list = list.stream().filter(Property::isActive).toList();
            } else if ("Inactive".equals(filter)) {
                list = list.stream().filter(p -> !p.isActive()).toList();
            }

            data.setAll(list);
            countLabel.setText(list.size() + (list.size() == 1 ? " property" : " properties"));

            boolean empty = list.isEmpty();
            emptyState.setVisible(empty);
            emptyState.setManaged(empty);
            propertiesTable.setVisible(!empty);
            propertiesTable.setManaged(!empty);

        } catch (RuntimeException e) {
            log.error("Failed to load properties", e);
            showError("Unable to load properties. Please try again.");
        }
    }

    /* =================================================================== */

    @FXML
    private void handleNewProperty() {
        openPropertyForm(null);
    }

    /* =================================================================== */

    private void openPropertyForm(Property property) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/property-form.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(
                getClass().getResource("/css/app.css").toExternalForm());

            PropertyFormController controller = loader.getController();
            controller.setProperty(property);

            Stage dialog = new Stage();
            dialog.setTitle(property == null ? "New Property" : "Edit Property");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(propertiesTable.getScene().getWindow());
            dialog.setScene(scene);
            dialog.setResizable(false);
            dialog.showAndWait();

            if (controller.isSaved()) {
                refresh();
            }
        } catch (IOException e) {
            log.error("Failed to open property form", e);
            showError("Unable to open property form.");
        }
    }

    /* =================================================================== */

    /**
     * Opens the Rooms screen for the selected property by swapping the
     * center content area of the shell.
     */
    private void openUnitsFor(Property p) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/units.fxml"));
            Parent view = loader.load();

            UnitListController controller = loader.getController();
            controller.setProperty(p);

            Scene scene = propertiesTable.getScene();
            if (scene == null) {
                log.warn("Properties table has no scene — cannot swap content area");
                showError("Unable to open the rooms screen.");
                return;
            }

            StackPane contentArea = (StackPane) scene.lookup("#contentArea");
            if (contentArea == null) {
                log.warn("#contentArea not found — cannot show rooms screen");
                showError("Unable to open the rooms screen.");
                return;
            }

            contentArea.getChildren().setAll(view);

            Label titleLabel = (Label) scene.lookup("#pageTitleLabel");
            if (titleLabel != null) {
                titleLabel.setText(p.getName() + " — Rooms");
            }

        } catch (IOException e) {
            log.error("Failed to open rooms screen", e);
            showError("Unable to open the rooms screen.");
        }
    }

    /* =================================================================== */

    private void confirmDelete(Property p) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Delete Property");
        a.setHeaderText("Delete \"" + p.getName() + "\"?");
        a.setContentText(
            "This will permanently remove the property.\n\n"
            + "If the property has units attached, the delete will be blocked.");

        ButtonType deleteBtn = new ButtonType("Delete");
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonType.CANCEL.getButtonData());
        a.getButtonTypes().setAll(cancelBtn, deleteBtn);

        Optional<ButtonType> result = a.showAndWait();
        if (result.isPresent() && result.get() == deleteBtn) {
            try {
                propertyService.delete(p.getId());
                refresh();
            } catch (RuntimeException e) {
                showError(e.getMessage() != null ? e.getMessage()
                                                 : "Unable to delete property.");
            }
        }
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
