package com.propertymanager.controller;

import com.propertymanager.model.AuditLogEntry;
import com.propertymanager.service.AuditLogService;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Controller for audit-logs.fxml.
 *
 * Read-only viewer. Users can search and filter, but not modify.
 */
public class AuditLogController {

    private static final Logger log = LoggerFactory.getLogger(AuditLogController.class);

    /* ---- FXML-injected ---- */
    @FXML private Button refreshButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> actionFilter;
    @FXML private Label countLabel;

    @FXML private TableView<AuditLogEntry> logsTable;
    @FXML private TableColumn<AuditLogEntry, String> whenColumn;
    @FXML private TableColumn<AuditLogEntry, String> userColumn;
    @FXML private TableColumn<AuditLogEntry, String> actionColumn;
    @FXML private TableColumn<AuditLogEntry, String> entityColumn;
    @FXML private TableColumn<AuditLogEntry, String> descriptionColumn;

    @FXML private VBox emptyState;

    /* ---- Service ---- */
    private final AuditLogService auditService = new AuditLogService();

    /* ---- State ---- */
    private final ObservableList<AuditLogEntry> data = FXCollections.observableArrayList();
    private PauseTransition searchDebounce;

    /** Currently-loaded full list — search/filter narrows this in memory. */
    private List<AuditLogEntry> allLogs = List.of();

    /* =================================================================== */

    @FXML
    private void initialize() {
        setupFilters();
        setupSearch();
        setupColumns();

        logsTable.setItems(data);
        logsTable.setPlaceholder(new Label(""));

        refresh();
    }

    /* =================================================================== */

    private void setupFilters() {
        actionFilter.setItems(FXCollections.observableArrayList(
            "All actions",
            "Payments",
            "Tenants",
            "Properties & Rooms",
            "Users",
            "System"));
        actionFilter.getSelectionModel().select("All actions");
        actionFilter.valueProperty().addListener((o, a, b) -> applyFilters());
    }

    /* =================================================================== */

    private void setupSearch() {
        searchDebounce = new PauseTransition(Duration.millis(200));
        searchDebounce.setOnFinished(e -> applyFilters());

        searchField.textProperty().addListener((obs, oldVal, newVal) ->
            searchDebounce.playFromStart());
    }

    /* =================================================================== */

    private void setupColumns() {
        whenColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(c.getValue().getWhenLabel()));

        userColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(nz(c.getValue().getUsername())));

        actionColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(prettyAction(c.getValue().getAction())));

        entityColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(nz(c.getValue().getEntity())));

        descriptionColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(nz(c.getValue().getDescription())));
    }

    /* =================================================================== */

    @FXML
    private void handleRefresh() {
        refresh();
    }

    private void refresh() {
        try {
            allLogs = auditService.findRecent(500);   // hard cap for performance
            applyFilters();
        } catch (RuntimeException e) {
            log.error("Failed to load audit logs", e);
        }
    }

    /* =================================================================== */

    private void applyFilters() {
        String query = searchField.getText();
        String category = actionFilter.getValue();

        List<AuditLogEntry> filtered = allLogs;

        // ---- Category filter ----
        if (category != null && !"All actions".equals(category)) {
            filtered = filtered.stream()
                .filter(l -> categoryMatches(category, l.getAction()))
                .toList();
        }

        // ---- Search filter ----
        if (query != null && !query.isBlank()) {
            String q = query.trim().toLowerCase();
            filtered = filtered.stream()
                .filter(l ->
                    contains(l.getUsername(), q) ||
                    contains(l.getAction(), q) ||
                    contains(l.getEntity(), q) ||
                    contains(l.getDescription(), q))
                .toList();
        }

        data.setAll(filtered);
        countLabel.setText(filtered.size()
            + (filtered.size() == 1 ? " event" : " events"));

        boolean empty = filtered.isEmpty();
        emptyState.setVisible(empty);
        emptyState.setManaged(empty);
        logsTable.setVisible(!empty);
        logsTable.setManaged(!empty);
    }

    /* =================================================================== */

    private boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle);
    }

    /** True if the given raw action code belongs to the selected category. */
    private boolean categoryMatches(String category, String action) {
        if (action == null) return false;
        return switch (category) {
            case "Payments" -> action.startsWith("PAYMENT_");
            case "Tenants" -> action.startsWith("TENANT_") || action.startsWith("TENANCY_");
            case "Properties & Rooms" ->
                action.startsWith("PROPERTY_") || action.startsWith("UNIT_");
            case "Users" -> action.startsWith("USER_") || action.startsWith("PASSWORD_");
            case "System" -> action.startsWith("SETTINGS_")
                          || action.startsWith("BACKUP_")
                          || action.startsWith("LOGIN_")
                          || action.startsWith("LOGOUT");
            default -> true;
        };
    }

    /** Turns "PAYMENT_RECORDED" into "Payment recorded" for display. */
    private String prettyAction(String action) {
        if (action == null) return "—";
        String[] parts = action.split("_");
        if (parts.length == 0) return action;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].toLowerCase();
            if (i == 0) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            } else {
                sb.append(' ').append(p);
            }
        }
        return sb.toString();
    }

    private String nz(String s) { return (s == null || s.isBlank()) ? "—" : s; }
}
