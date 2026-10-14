package com.propertymanager.controller;

import com.propertymanager.model.User;
import com.propertymanager.repository.UserRepository;
import com.propertymanager.security.PasswordUtil;
import com.propertymanager.service.AuditService;
import com.propertymanager.session.SessionManager;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyStringWrapper;
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
import javafx.scene.control.PasswordField;
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
import java.util.List;
import java.util.Optional;

/**
 * Controller for users.fxml.
 *
 * Simplified user management: list users, add new, reset password,
 * activate/deactivate. No delete — accounts are disabled, not removed.
 */
public class UserListController {

    private static final Logger log = LoggerFactory.getLogger(UserListController.class);

    /* ---- FXML-injected ---- */
    @FXML private Button newUserButton;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> roleFilter;
    @FXML private Label countLabel;

    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, String> usernameColumn;
    @FXML private TableColumn<User, String> nameColumn;
    @FXML private TableColumn<User, String> roleColumn;
    @FXML private TableColumn<User, String> statusColumn;
    @FXML private TableColumn<User, Void>   actionsColumn;

    @FXML private VBox emptyState;
    @FXML private Button emptyNewUserButton;

    /* ---- Services ---- */
    private final UserRepository userRepo = new UserRepository();
    private final AuditService audit = new AuditService();

    /* ---- State ---- */
    private final ObservableList<User> data = FXCollections.observableArrayList();
    private PauseTransition searchDebounce;
    private List<User> allUsers = List.of();

    /* =================================================================== */

    @FXML
    private void initialize() {
        applyIcons();
        setupFilters();
        setupSearch();
        setupColumns();

        usersTable.setItems(data);
        usersTable.setPlaceholder(new Label(""));

        refresh();
    }

    /* =================================================================== */

    private void applyIcons() {
        newUserButton.setGraphic(new FontIcon("fas-user-plus"));
        newUserButton.setGraphicTextGap(6);

        emptyNewUserButton.setGraphic(new FontIcon("fas-user-plus"));
        emptyNewUserButton.setGraphicTextGap(6);
    }

    /* =================================================================== */

    private void setupFilters() {
        roleFilter.setItems(FXCollections.observableArrayList(
            "All roles", "Administrator", "Property Manager", "Accountant"));
        roleFilter.getSelectionModel().select("All roles");
        roleFilter.valueProperty().addListener((o, a, b) -> applyFilters());
    }

    /* =================================================================== */

    private void setupSearch() {
        searchDebounce = new PauseTransition(Duration.millis(250));
        searchDebounce.setOnFinished(e -> applyFilters());

        searchField.textProperty().addListener((obs, oldVal, newVal) ->
            searchDebounce.playFromStart());
    }

    /* =================================================================== */

    private void setupColumns() {
        usernameColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(nz(c.getValue().getUsername())));

        nameColumn.setCellValueFactory(c ->
            new ReadOnlyStringWrapper(nz(c.getValue().getFullName())));

        // Role — badge-styled column
        roleColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                User u = getTableRow().getItem();
                String roleName = (u.getRole() != null ? u.getRole().getName() : "—");
                Label badge = new Label(roleName);
                badge.getStyleClass().add("badge");
                String code = u.getRoleCode();
                if ("ADMIN".equalsIgnoreCase(code)) {
                    badge.getStyleClass().add("badge-success");
                } else if ("ACCOUNTANT".equalsIgnoreCase(code)) {
                    badge.getStyleClass().add("badge-warning");
                } else {
                    badge.getStyleClass().add("badge-neutral");
                }
                setGraphic(badge);
                setText(null);
            }
        });

        // Status — badge
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                User u = getTableRow().getItem();
                Label badge = new Label(u.isActive() ? "ACTIVE" : "DISABLED");
                badge.getStyleClass().add("badge");
                badge.getStyleClass().add(
                    u.isActive() ? "badge-success" : "badge-danger");
                setGraphic(badge);
                setText(null);
            }
        });

        // Actions — Reset Password + Activate/Deactivate
        actionsColumn.setCellFactory(col -> new TableCell<>() {
            private final Button resetBtn   = compactButton("Reset Password", "fas-key");
            private final Button toggleBtn  = compactButton("Deactivate",     "fas-ban");
            private final HBox box = new HBox(6, resetBtn, toggleBtn);

            {
                resetBtn.getStyleClass().add("button");
                toggleBtn.getStyleClass().add("button-danger");

                box.setAlignment(Pos.CENTER_LEFT);

                resetBtn.setOnAction(e -> {
                    User u = rowUser();
                    if (u != null) handleResetPassword(u);
                });

                toggleBtn.setOnAction(e -> {
                    User u = rowUser();
                    if (u != null) handleToggleActive(u);
                });
            }

            private User rowUser() {
                return getTableRow() == null ? null : getTableRow().getItem();
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                User u = rowUser();
                if (empty || u == null) {
                    setGraphic(null);
                    return;
                }

                boolean isSelf = SessionManager.isLoggedIn()
                        && SessionManager.getCurrentUser().getId() == u.getId();

                // Disable both buttons when looking at your own account
                resetBtn.setDisable(isSelf);
                toggleBtn.setDisable(isSelf);

                // Toggle button label depends on current state
                if (u.isActive()) {
                    toggleBtn.setText("Deactivate");
                    toggleBtn.setGraphic(new FontIcon("fas-ban"));
                    toggleBtn.getStyleClass().removeAll("button", "button-primary");
                    toggleBtn.getStyleClass().add("button-danger");
                } else {
                    toggleBtn.setText("Activate");
                    toggleBtn.setGraphic(new FontIcon("fas-check"));
                    toggleBtn.getStyleClass().removeAll("button-danger", "button");
                    toggleBtn.getStyleClass().add("button-primary");
                }

                setGraphic(box);
            }
        });
    }

    /* =================================================================== */

    /** Small action button used in the Actions column. */
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
            allUsers = userRepo.findAll();
            applyFilters();
        } catch (RuntimeException e) {
            log.error("Failed to load users", e);
            showError("Unable to load users. Please try again.");
        }
    }

    private void applyFilters() {
        String query = searchField.getText();
        String role  = roleFilter.getValue();

        List<User> filtered = allUsers;

        if (query != null && !query.isBlank()) {
            String q = query.trim().toLowerCase();
            filtered = filtered.stream()
                .filter(u ->
                    (u.getUsername() != null && u.getUsername().toLowerCase().contains(q))
                    || (u.getFullName() != null && u.getFullName().toLowerCase().contains(q)))
                .toList();
        }

        if (role != null && !"All roles".equals(role)) {
            filtered = filtered.stream()
                .filter(u -> u.getRole() != null && role.equalsIgnoreCase(u.getRole().getName()))
                .toList();
        }

        data.setAll(filtered);
        countLabel.setText(filtered.size() + (filtered.size() == 1 ? " user" : " users"));

        boolean empty = filtered.isEmpty();
        emptyState.setVisible(empty);
        emptyState.setManaged(empty);
        usersTable.setVisible(!empty);
        usersTable.setManaged(!empty);
    }

    /* =================================================================== */
    /*  NEW USER                                                           */
    /* =================================================================== */

    @FXML
    private void handleNewUser() {
        openUserForm(null);
    }

    /* =================================================================== */

    private void openUserForm(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/user-form.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(
                getClass().getResource("/css/app.css").toExternalForm());

            UserFormController controller = loader.getController();
            controller.setUser(user);

            Stage dialog = new Stage();
            dialog.setTitle(user == null ? "Add User" : "Edit User");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(usersTable.getScene().getWindow());
            dialog.setScene(scene);
            dialog.setResizable(false);
            dialog.setOnShown(e -> dialog.centerOnScreen());
            dialog.showAndWait();

            if (controller.isSaved()) {
                refresh();
            }
        } catch (IOException e) {
            log.error("Failed to open user form", e);
            showError("Unable to open the user form.");
        }
    }

    /* =================================================================== */
    /*  RESET PASSWORD                                                     */
    /* =================================================================== */

    private void handleResetPassword(User u) {
        // Small inline dialog for password entry
        javafx.scene.control.Dialog<ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Reset Password");
        dialog.setHeaderText("Reset password for " + u.getFullName()
                             + " (" + u.getUsername() + ")");

        ButtonType resetBtn = new ButtonType("Reset Password",
                                             ButtonType.OK.getButtonData());
        ButtonType cancelBtn = new ButtonType("Cancel",
                                              ButtonType.CANCEL.getButtonData());
        dialog.getDialogPane().getButtonTypes().setAll(cancelBtn, resetBtn);

        VBox content = new VBox(10);
        content.setPadding(new javafx.geometry.Insets(10));

        Label lbl = new Label("New Password");
        lbl.getStyleClass().add("field-label");

        PasswordField pass1 = new PasswordField();
        pass1.setPromptText("Enter new password");
        pass1.setPrefWidth(260);

        Label lbl2 = new Label("Confirm Password");
        lbl2.getStyleClass().add("field-label");

        PasswordField pass2 = new PasswordField();
        pass2.setPromptText("Re-enter password");
        pass2.setPrefWidth(260);

        Label error = new Label();
        error.setStyle("-fx-text-fill: -fx-danger; -fx-font-size: 11px;");
        error.setVisible(false);
        error.setManaged(false);

        Label hint = new Label("Minimum 8 characters, at least one letter and one number.");
        hint.getStyleClass().add("label-muted");
        hint.setWrapText(true);
        hint.setMaxWidth(260);

        content.getChildren().addAll(lbl, pass1, lbl2, pass2, hint, error);
        dialog.getDialogPane().setContent(content);

        // Validate on OK
        final Button okButton = (Button) dialog.getDialogPane().lookupButton(resetBtn);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            String p1 = pass1.getText();
            String p2 = pass2.getText();
            error.setVisible(false);
            error.setManaged(false);

            if (p1 == null || p1.isEmpty()) {
                error.setText("Password cannot be empty.");
                error.setVisible(true);
                error.setManaged(true);
                evt.consume();
                return;
            }
            if (!p1.equals(p2)) {
                error.setText("Passwords do not match.");
                error.setVisible(true);
                error.setManaged(true);
                evt.consume();
                return;
            }
            if (!PasswordUtil.meetsPolicy(p1)) {
                error.setText("Password must be at least 8 characters "
                              + "with a letter and a number.");
                error.setVisible(true);
                error.setManaged(true);
                evt.consume();
            }
        });

        Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isPresent() && choice.get() == resetBtn) {
            String newPassword = pass1.getText();
            try {
                String hash = PasswordUtil.hash(newPassword);
                userRepo.updatePassword(u.getId(), hash);

                audit.log("PASSWORD_RESET", "users", u.getUsername(),
                          "Password reset for user " + u.getUsername());

                Alert ok = new Alert(Alert.AlertType.INFORMATION);
                ok.setTitle("Password Reset");
                ok.setHeaderText("Password updated");
                ok.setContentText("The password for " + u.getUsername()
                                  + " has been changed.");
                ok.showAndWait();

            } catch (RuntimeException e) {
                log.error("Failed to reset password for {}", u.getUsername(), e);
                showError("Unable to reset the password. Please try again.");
            }
        }
    }

    /* =================================================================== */
    /*  ACTIVATE / DEACTIVATE                                              */
    /* =================================================================== */

    private void handleToggleActive(User u) {
        boolean deactivating = u.isActive();

        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(deactivating ? "Deactivate User" : "Activate User");
        a.setHeaderText((deactivating ? "Deactivate " : "Activate ")
                        + u.getFullName() + "?");
        a.setContentText(deactivating
            ? "This user will not be able to log in until reactivated."
            : "This user will be able to log in again.");

        ButtonType confirm = new ButtonType(deactivating ? "Deactivate" : "Activate");
        ButtonType cancel  = new ButtonType("Cancel", ButtonType.CANCEL.getButtonData());
        a.getButtonTypes().setAll(cancel, confirm);

        Optional<ButtonType> result = a.showAndWait();
        if (result.isPresent() && result.get() == confirm) {
            try {
                userRepo.setActive(u.getId(), !deactivating);
                audit.log(deactivating ? "USER_DEACTIVATED" : "USER_ACTIVATED",
                          "users", u.getUsername(), u.getFullName());
                refresh();
            } catch (RuntimeException e) {
                log.error("Failed to toggle active for {}", u.getUsername(), e);
                showError("Unable to update the user.");
            }
        }
    }

    /* =================================================================== */

    private String nz(String s) { return (s == null || s.isBlank()) ? "—" : s; }

    private void showError(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error");
        a.setHeaderText("Something went wrong");
        a.setContentText(message);
        a.showAndWait();
    }
}
