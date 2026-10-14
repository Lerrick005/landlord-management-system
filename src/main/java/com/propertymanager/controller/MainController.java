package com.propertymanager.controller;

import com.propertymanager.model.User;
import com.propertymanager.session.SessionManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller for main.fxml — the application shell.
 *
 * Owns:
 *   - The top bar (user name, role, initials, change password, logout)
 *   - The sidebar (active-item highlighting, permission-based visibility)
 *   - The center content area (swapping between section views)
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    /* ---- Top bar ---- */
    @FXML private Label pageTitleLabel;
    @FXML private Label userNameLabel;
    @FXML private Label userRoleLabel;
    @FXML private Label avatarInitialsLabel;
    @FXML private Button changePasswordButton;

    /* ---- Center ---- */
    @FXML private StackPane contentArea;

    /* ---- Sidebar nav buttons ---- */
    @FXML private Button navDashboard;
    @FXML private Button navProperties;
    @FXML private Button navTenants;
    @FXML private Button navPayments;
    @FXML private Button navReports;
    @FXML private Button navUsers;
    @FXML private Button navAuditLogs;
    @FXML private Button navSettings;

    /* ---- State ---- */
    private final Map<Button, String> navRoutes = new LinkedHashMap<>();
    private Button activeNav;

    /* =================================================================== */

    @FXML
    private void initialize() {

        User user = SessionManager.getCurrentUser();
        if (user == null) {
            log.error("MainController initialized without a session user");
            return;
        }

        // Populate the top bar
        userNameLabel.setText(user.getFullName());
        userRoleLabel.setText(user.getRole() != null ? user.getRole().getName() : "");
        avatarInitialsLabel.setText(initials(user.getFullName()));

        // Register nav routes (button -> which content to show)
        navRoutes.put(navDashboard,  "dashboard");
        navRoutes.put(navProperties, "properties");
        navRoutes.put(navTenants,    "tenants");
        navRoutes.put(navPayments,   "payments");
        navRoutes.put(navReports,    "reports");
        navRoutes.put(navUsers,      "users");
        navRoutes.put(navAuditLogs,  "auditLogs");
        navRoutes.put(navSettings,   "settings");

        // Hide admin-only entries for non-admins
        applyPermissions();

        // Show the dashboard first
        setActive(navDashboard);
        loadContent("dashboard");
    }

    /* =================================================================== */

    @FXML
    private void navigate(javafx.event.ActionEvent event) {
        Button source = (Button) event.getSource();
        String route = navRoutes.get(source);
        if (route == null) return;

        setActive(source);
        loadContent(route);
    }

    /* =================================================================== */

    /** Loads the FXML for the given route into the center content area. */
    private void loadContent(String route) {
        String fxmlPath;
        String title;

        switch (route) {
            case "dashboard" -> { fxmlPath = "/fxml/dashboard.fxml";   title = "Dashboard"; }
            case "properties" -> { fxmlPath = "/fxml/properties.fxml"; title = "Properties"; }
            case "tenants"    -> { fxmlPath = "/fxml/tenants.fxml";    title = "Tenants"; }
            case "payments"   -> { fxmlPath = "/fxml/payments.fxml";   title = "Payments"; }
            case "reports"    -> { fxmlPath = "/fxml/reports.fxml";    title = "Reports"; }
            case "users"      -> { fxmlPath = "/fxml/users.fxml";      title = "Users"; }
            case "auditLogs"  -> { fxmlPath = "/fxml/audit-logs.fxml"; title = "Audit Logs"; }
            case "settings"   -> { fxmlPath = "/fxml/settings.fxml";   title = "Settings"; }
            default -> { fxmlPath = "/fxml/dashboard.fxml"; title = "Dashboard"; }
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();

            // For the placeholder view, set the title/subtitle dynamically
            Object ctrl = loader.getController();
            if (ctrl instanceof PlaceholderController pc && !"dashboard".equals(route)) {
                pc.setTitle(title);
                pc.setSubtitle(title + " management arrives in a later phase.");
            }

            contentArea.getChildren().setAll(view);
            pageTitleLabel.setText(title);

        } catch (IOException e) {
            log.error("Failed to load content for route '{}'", route, e);
            Label error = new Label("Unable to load this section.");
            error.getStyleClass().add("field-error-message");
            contentArea.getChildren().setAll(error);
        }
    }

    /* =================================================================== */

    /** Marks the given button as active in the sidebar, un-highlighting the rest. */
    private void setActive(Button nav) {
        if (activeNav != null) {
            activeNav.getStyleClass().remove("sidebar-item-active");
        }
        if (!nav.getStyleClass().contains("sidebar-item-active")) {
            nav.getStyleClass().add("sidebar-item-active");
        }
        activeNav = nav;
    }

    /* =================================================================== */

    /** Hides admin-only sidebar entries for non-admins. */
    private void applyPermissions() {
        boolean admin = SessionManager.isAdmin();

        Node[] adminOnly = { navUsers, navAuditLogs, navSettings };
        for (Node node : adminOnly) {
            node.setVisible(admin);
            node.setManaged(admin);
        }
    }

    /* =================================================================== */
    /*  CHANGE PASSWORD                                                    */
    /* =================================================================== */

    @FXML
    private void handleChangePassword() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/change-password.fxml"));
            Parent root = loader.load();

            ChangePasswordController controller = loader.getController();

            Scene scene = new Scene(root);
            scene.getStylesheets().add(
                getClass().getResource("/css/app.css").toExternalForm());

            Stage dialog = new Stage();
            dialog.setTitle("Change Password");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(changePasswordButton.getScene().getWindow());
            dialog.setScene(scene);
            dialog.setResizable(false);
            dialog.setOnShown(e -> dialog.centerOnScreen());
            dialog.showAndWait();

            if (controller.isSaved()) {
                Alert ok = new Alert(Alert.AlertType.INFORMATION);
                ok.setTitle("Password Changed");
                ok.setHeaderText("Your password has been updated");
                ok.setContentText("Use your new password the next time you log in.");
                ok.showAndWait();
            }

        } catch (IOException e) {
            log.error("Failed to open change-password dialog", e);
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Error");
            err.setHeaderText("Something went wrong");
            err.setContentText("Unable to open the change-password dialog.");
            err.showAndWait();
        }
    }

    /* =================================================================== */
    /*  WINDOW CONTROLS                                                    */
    /* =================================================================== */

    @FXML
    private void handleMinimize() {
        Stage stage = (Stage) changePasswordButton.getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void handleMaximize() {
        Stage stage = (Stage) changePasswordButton.getScene().getWindow();
        stage.setMaximized(!stage.isMaximized());
    }

    @FXML
    private void handleClose() {
        log.info("User closed the main window");
        SessionManager.logout();
        Stage stage = (Stage) changePasswordButton.getScene().getWindow();
        stage.close();
    }

    /* =================================================================== */

    @FXML
    private void handleLogout() {
        log.info("User requested logout");
        SessionManager.logout();

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Parent root = loader.load();

            Stage loginStage = new Stage();
            loginStage.setTitle("Property Management System");
            Scene scene = new Scene(root, 900, 600);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            loginStage.setScene(scene);
            loginStage.setMinWidth(900);
            loginStage.setMinHeight(600);
            loginStage.setResizable(false);
            loginStage.show();

            Stage currentStage = (Stage) userNameLabel.getScene().getWindow();
            currentStage.close();

        } catch (IOException e) {
            log.error("Failed to reopen login window after logout", e);
        }
    }

    /* =================================================================== */

    /** Extracts up to two initials from a full name. */
    private String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) return "U";
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
}
