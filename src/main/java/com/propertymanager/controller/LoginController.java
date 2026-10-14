package com.propertymanager.controller;

import com.propertymanager.model.User;
import com.propertymanager.service.AuthService;
import com.propertymanager.service.AuthService.LoginResult;
import com.propertymanager.session.SessionManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.prefs.Preferences;

/**
 * Controller for login.fxml.
 *
 * Flow:
 *   1. User enters username + password
 *   2. handleLogin() validates input locally
 *   3. AuthService.login() runs on a background thread
 *   4. On success: open main window, close this one
 *   5. On failure: display friendly message
 */
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    /* ---- FXML-injected fields (names must match fx:id in login.fxml) ---- */

    @FXML private TextField       usernameField;
    @FXML private PasswordField   passwordField;
    @FXML private TextField       passwordVisibleField;
    @FXML private Button          togglePasswordButton;
    @FXML private CheckBox        rememberUsernameCheck;
    @FXML private Label           errorLabel;
    @FXML private Button          loginButton;
    @FXML private ProgressIndicator loginProgress;

    /* ---- Services ---- */

    private final AuthService authService = new AuthService();
    private final Preferences prefs = Preferences.userNodeForPackage(LoginController.class);

    private static final String PREF_REMEMBER   = "rememberUsername";
    private static final String PREF_USERNAME   = "savedUsername";

    /* =================================================================== */

    @FXML
    private void initialize() {
        hideError();

        // Load remembered username
        boolean remember = prefs.getBoolean(PREF_REMEMBER, false);
        rememberUsernameCheck.setSelected(remember);
        if (remember) {
            String saved = prefs.get(PREF_USERNAME, "");
            if (!saved.isBlank()) {
                usernameField.setText(saved);
                Platform.runLater(() -> passwordField.requestFocus());
            }
        } else {
            Platform.runLater(() -> usernameField.requestFocus());
        }

        // Toggle password visibility
        togglePasswordButton.setOnAction(e -> togglePasswordVisibility());

        // Enter key in either field triggers login
        usernameField.setOnAction(e -> handleLogin());
        passwordField.setOnAction(e -> handleLogin());
        passwordVisibleField.setOnAction(e -> handleLogin());

        // Clear error as soon as the user starts typing again
        usernameField.textProperty().addListener((o, a, b) -> hideError());
        passwordField.textProperty().addListener((o, a, b) -> hideError());
        passwordVisibleField.textProperty().addListener((o, a, b) -> hideError());
    }

    /* =================================================================== */

    @FXML
    private void handleLogin() {
        hideError();

        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.isVisible()
                ? passwordField.getText()
                : passwordVisibleField.getText();

        // ---------- Local validation ----------
        if (username.isBlank()) {
            showError("Please enter your username.");
            usernameField.requestFocus();
            return;
        }
        if (password == null || password.isEmpty()) {
            showError("Please enter your password.");
            passwordField.requestFocus();
            return;
        }

        setLoading(true);

        // ---------- Background login task ----------
        Task<LoginResult> task = new Task<>() {
            @Override
            protected LoginResult call() {
                return authService.login(username, password);
            }
        };

        task.setOnSucceeded(e -> {
            setLoading(false);
            LoginResult result = task.getValue();
            if (result.isSuccess()) {
                onLoginSuccess(result.getUser());
            } else {
                showError(result.getMessage());
                passwordField.clear();
                passwordVisibleField.clear();
                passwordField.requestFocus();
            }
        });

        task.setOnFailed(e -> {
            setLoading(false);
            Throwable ex = task.getException();
            log.error("Login task failed unexpectedly", ex);
            showError("Unable to sign you in right now. Please try again.");
        });

        Thread t = new Thread(task, "login-task");
        t.setDaemon(true);
        t.start();
    }

    /* =================================================================== */

    private void onLoginSuccess(User user) {
        // Save remembered username preference
        if (rememberUsernameCheck.isSelected()) {
            prefs.putBoolean(PREF_REMEMBER, true);
            prefs.put(PREF_USERNAME, user.getUsername());
        } else {
            prefs.putBoolean(PREF_REMEMBER, false);
            prefs.remove(PREF_USERNAME);
        }

        // Start session
        SessionManager.login(user);

        // Open main window, close login window
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/main.fxml"));
            Parent root = loader.load();

            Stage mainStage = new Stage();
            mainStage.setTitle("Property Management System");
            Scene scene = new Scene(root, 1200, 760);
            scene.getStylesheets().add(
                getClass().getResource("/css/app.css").toExternalForm());
            mainStage.setScene(scene);
            mainStage.setMinWidth(1000);
            mainStage.setMinHeight(640);
            mainStage.show();

            // Close login window
            Stage loginStage = (Stage) loginButton.getScene().getWindow();
            loginStage.close();

        } catch (IOException e) {
            log.error("Failed to open main window", e);
            showError("Signed in, but the main window could not be opened.");
            SessionManager.logout();
        }
    }

    /* =================================================================== */

    private void togglePasswordVisibility() {
        if (passwordField.isVisible()) {
            // Show
            passwordVisibleField.setText(passwordField.getText());
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            passwordVisibleField.setVisible(true);
            passwordVisibleField.setManaged(true);
            togglePasswordButton.setText("Hide");
            passwordVisibleField.requestFocus();
            passwordVisibleField.positionCaret(passwordVisibleField.getText().length());
        } else {
            // Hide
            passwordField.setText(passwordVisibleField.getText());
            passwordVisibleField.setVisible(false);
            passwordVisibleField.setManaged(false);
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            togglePasswordButton.setText("Show");
            passwordField.requestFocus();
            passwordField.positionCaret(passwordField.getText().length());
        }
    }

    /* =================================================================== */

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void setLoading(boolean loading) {
        loginButton.setDisable(loading);
        usernameField.setDisable(loading);
        passwordField.setDisable(loading);
        passwordVisibleField.setDisable(loading);
        rememberUsernameCheck.setDisable(loading);
        togglePasswordButton.setDisable(loading);

        loginProgress.setVisible(loading);
        loginProgress.setManaged(loading);

        loginButton.setText(loading ? "Signing in…" : "Sign In");
    }
}
