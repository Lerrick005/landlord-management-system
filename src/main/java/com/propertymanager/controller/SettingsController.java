package com.propertymanager.controller;

import com.propertymanager.database.Database;
import com.propertymanager.service.AuditService;
import com.propertymanager.service.BackupService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for settings.fxml.
 *
 * Reads and writes the system_settings key/value table, and provides
 * database backup and restore actions.
 */
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    /* ---- FXML-injected ---- */
    @FXML private TextField orgNameField;
    @FXML private TextField orgAddressField;
    @FXML private TextField orgPhoneField;
    @FXML private TextField orgEmailField;

    @FXML private TextField currencyCodeField;
    @FXML private TextField currencySymbolField;

    @FXML private TextField backupFolderField;

    @FXML private Button browseButton;
    @FXML private Button saveButton;
    @FXML private Button saveButton2;
    @FXML private Button backupNowButton;
    @FXML private Button restoreButton;
    @FXML private Label  statusLabel;

    /* ---- Services ---- */
    private final AuditService audit = new AuditService();
    private final BackupService backupService = new BackupService();

    /* ---- Keys used in system_settings ---- */
    private static final String K_ORG_NAME     = "org.name";
    private static final String K_ORG_ADDRESS  = "org.address";
    private static final String K_ORG_PHONE    = "org.phone";
    private static final String K_ORG_EMAIL    = "org.email";
    private static final String K_CURRENCY     = "org.currency";
    private static final String K_CURRENCY_SYM = "org.currency_symbol";
    private static final String K_BACKUP_DIR   = "backup.location";

    /* =================================================================== */

    @FXML
    private void initialize() {
        loadSettings();
        statusLabel.setText("");
    }

    /* =================================================================== */

    private void loadSettings() {
        try {
            Map<String, String> settings = readAll();

            orgNameField.setText(nz(settings.get(K_ORG_NAME)));
            orgAddressField.setText(nz(settings.get(K_ORG_ADDRESS)));
            orgPhoneField.setText(nz(settings.get(K_ORG_PHONE)));
            orgEmailField.setText(nz(settings.get(K_ORG_EMAIL)));
            currencyCodeField.setText(nz(settings.get(K_CURRENCY)));
            currencySymbolField.setText(nz(settings.get(K_CURRENCY_SYM)));
            backupFolderField.setText(nz(settings.get(K_BACKUP_DIR)));

        } catch (RuntimeException e) {
            log.error("Failed to load settings", e);
            showError("Unable to load settings. Please try again.");
        }
    }

    /* =================================================================== */

    @FXML
    private void handleBrowse() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose Backup Folder");

        String current = backupFolderField.getText();
        if (current != null && !current.isBlank()) {
            File f = new File(current);
            if (f.isDirectory()) chooser.setInitialDirectory(f);
        }

        Stage owner = (Stage) browseButton.getScene().getWindow();
        File chosen = chooser.showDialog(owner);
        if (chosen != null) {
            backupFolderField.setText(chosen.getAbsolutePath());
        }
    }

    /* =================================================================== */

    @FXML
    private void handleSave() {
        statusLabel.setText("");
        statusLabel.setStyle("-fx-text-fill: -fx-grey-500;");

        String orgName    = trimOrEmpty(orgNameField.getText());
        String orgAddress = trimOrEmpty(orgAddressField.getText());
        String orgPhone   = trimOrEmpty(orgPhoneField.getText());
        String orgEmail   = trimOrEmpty(orgEmailField.getText());
        String currency   = trimOrEmpty(currencyCodeField.getText());
        String currSymbol = trimOrEmpty(currencySymbolField.getText());
        String backupDir  = trimOrEmpty(backupFolderField.getText());

        // Validation
        if (orgName.isBlank()) {
            showFormError("Organization name cannot be empty.");
            return;
        }
        if (currency.isBlank()) {
            showFormError("Currency code cannot be empty.");
            return;
        }
        if (currSymbol.isBlank()) {
            showFormError("Currency symbol cannot be empty.");
            return;
        }
        if (!orgEmail.isBlank() && !orgEmail.contains("@")) {
            showFormError("Email address looks invalid.");
            return;
        }

        try {
            Map<String, String> updates = new HashMap<>();
            updates.put(K_ORG_NAME,     orgName);
            updates.put(K_ORG_ADDRESS,  orgAddress);
            updates.put(K_ORG_PHONE,    orgPhone);
            updates.put(K_ORG_EMAIL,    orgEmail);
            updates.put(K_CURRENCY,     currency);
            updates.put(K_CURRENCY_SYM, currSymbol);
            updates.put(K_BACKUP_DIR,   backupDir);

            writeAll(updates);

            audit.log("SETTINGS_UPDATED", "system_settings", null,
                      "Organization settings updated");

            statusLabel.setText("Settings saved.");
            statusLabel.setStyle("-fx-text-fill: -fx-success;");

            new Thread(() -> {
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                Platform.runLater(() -> statusLabel.setText(""));
            }).start();

        } catch (RuntimeException e) {
            log.error("Failed to save settings", e);
            showFormError("Unable to save settings. Please try again.");
        }
    }

    /* =================================================================== */
    /*  BACKUP                                                             */
    /* =================================================================== */

    @FXML
    private void handleBackupNow() {
        String folderPath = trimOrEmpty(backupFolderField.getText());
        if (folderPath.isBlank()) {
            showFormError("Please choose a backup folder first.");
            return;
        }

        File folder = new File(folderPath);

        backupNowButton.setDisable(true);
        backupNowButton.setText("Backing up...");
        statusLabel.setText("");
        statusLabel.setStyle("-fx-text-fill: -fx-grey-500;");

        // Run in a background thread — pg_dump can take a few seconds
        Thread t = new Thread(() -> {
            BackupService.Result result = backupService.backup(folder);

            Platform.runLater(() -> {
                backupNowButton.setDisable(false);
                backupNowButton.setText("Backup Now");

                if (result.success) {
                    audit.log("BACKUP_CREATED", "database", null, result.message);

                    statusLabel.setText(result.message);
                    statusLabel.setStyle("-fx-text-fill: -fx-success;");

                    Alert a = new Alert(Alert.AlertType.INFORMATION);
                    a.setTitle("Backup Complete");
                    a.setHeaderText("Your data has been backed up");
                    a.setContentText(result.message + "\n\n"
                        + (result.file != null ? result.file.getAbsolutePath() : ""));
                    a.showAndWait();

                } else {
                    statusLabel.setText(result.message);
                    statusLabel.setStyle("-fx-text-fill: -fx-danger;");
                    showError(result.message);
                }
            });
        }, "backup-thread");
        t.setDaemon(true);
        t.start();
    }

    /* =================================================================== */
    /*  RESTORE                                                            */
    /* =================================================================== */

    @FXML
    private void handleRestore() {
        // ---- Pick backup file ----
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Backup File");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Backup Files", "*.dump", "*.sql", "*.backup"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        String current = backupFolderField.getText();
        if (current != null && !current.isBlank()) {
            File folder = new File(current);
            if (folder.isDirectory()) chooser.setInitialDirectory(folder);
        }

        File chosen = chooser.showOpenDialog(restoreButton.getScene().getWindow());
        if (chosen == null) return;

        // ---- Big red warning ----
        Alert warning = new Alert(Alert.AlertType.WARNING);
        warning.setTitle("Confirm Restore");
        warning.setHeaderText("This will REPLACE all current data");
        warning.setContentText(
            "Restoring from:\n  " + chosen.getName() + "\n\n"
            + "Every property, room, tenant, and payment currently in the system "
            + "will be DELETED and replaced with the contents of this backup.\n\n"
            + "This action cannot be undone.\n\n"
            + "Are you sure you want to continue?");

        ButtonType confirm = new ButtonType("Yes, Restore");
        ButtonType cancel  = new ButtonType("Cancel", ButtonType.CANCEL.getButtonData());
        warning.getButtonTypes().setAll(cancel, confirm);

        Optional<ButtonType> choice = warning.showAndWait();
        if (choice.isEmpty() || choice.get() != confirm) return;

        // ---- Second confirmation (extra safety) ----
        Alert reallySure = new Alert(Alert.AlertType.CONFIRMATION);
        reallySure.setTitle("Final Confirmation");
        reallySure.setHeaderText("Last chance");
        reallySure.setContentText(
            "Click \"Restore Now\" to permanently replace the current data. "
            + "You will be logged out automatically.");

        ButtonType go = new ButtonType("Restore Now");
        reallySure.getButtonTypes().setAll(cancel, go);

        Optional<ButtonType> finalChoice = reallySure.showAndWait();
        if (finalChoice.isEmpty() || finalChoice.get() != go) return;

        // ---- Run the restore ----
        restoreButton.setDisable(true);
        backupNowButton.setDisable(true);
        restoreButton.setText("Restoring...");

        Thread t = new Thread(() -> {
            BackupService.Result result = backupService.restore(chosen);

            Platform.runLater(() -> {
                if (result.success) {
                    audit.log("BACKUP_RESTORED", "database", null,
                              "Restored from " + chosen.getName());

                    Alert done = new Alert(Alert.AlertType.INFORMATION);
                    done.setTitle("Restore Complete");
                    done.setHeaderText("Data restored successfully");
                    done.setContentText(
                        "The system will now close. Please start it again "
                        + "and log in with your credentials.");
                    done.showAndWait();

                    // Shut down — user must relaunch to avoid stale connections
                    Database.shutdown();
                    Platform.exit();
                    System.exit(0);

                } else {
                    restoreButton.setDisable(false);
                    backupNowButton.setDisable(false);
                    restoreButton.setText("Restore from Backup...");

                    showError(result.message);
                }
            });
        }, "restore-thread");
        t.setDaemon(true);
        t.start();
    }

    /* =================================================================== */
    /*  DB ACCESS                                                          */
    /* =================================================================== */

    private Map<String, String> readAll() {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT key, value FROM system_settings";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            log.error("readAll() failed", e);
            throw new RuntimeException("Unable to read settings", e);
        }
        return map;
    }

    private void writeAll(Map<String, String> updates) {
        String sql = """
            UPDATE system_settings
               SET value = ?, updated_at = NOW()
             WHERE key = ?
            """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (Map.Entry<String, String> e : updates.entrySet()) {
                ps.setString(1, e.getValue());
                ps.setString(2, e.getKey());
                ps.addBatch();
            }
            ps.executeBatch();
            log.info("Saved {} setting(s)", updates.size());
        } catch (SQLException e) {
            log.error("writeAll() failed", e);
            throw new RuntimeException("Unable to save settings", e);
        }
    }

    /* =================================================================== */
    /*  HELPERS                                                            */
    /* =================================================================== */

    private String nz(String s) { return s == null ? "" : s; }

    private String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private void showFormError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: -fx-danger;");
    }

    private void showError(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error");
        a.setHeaderText("Something went wrong");
        a.setContentText(message);
        a.showAndWait();
    }
}
