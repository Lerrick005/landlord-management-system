
package com.propertymanager;

import com.propertymanager.config.AppConfig;
import com.propertymanager.database.Database;
import com.propertymanager.database.Migrations;
import com.propertymanager.repository.UserRepository;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application entry point.
 *
 * Startup sequence:
 *   1. Load configuration
 *   2. Open DB pool
 *   3. Test DB connectivity
 *   4. Run Flyway migrations
 *   5. First-run check:
 *        - zero users  -> show Setup Wizard
 *        - users exist -> show Login window
 */
public class Main extends Application {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    @Override
    public void start(Stage primaryStage) {
        try {
            AppConfig.load();
            Database.initialize();

            if (!Database.testConnection()) {
                showFatal(
                    "Cannot reach the database.",
                    "Please check that PostgreSQL is running and that the "
                        + "credentials in application.properties are correct."
                );
                Platform.exit();
                return;
            }

            Migrations.run();

            // ---- First-run detection ----
            boolean firstRun = false;
            try {
                firstRun = new UserRepository().count() == 0;
            } catch (RuntimeException e) {
                log.warn("Could not check user count — assuming setup complete", e);
            }

            if (firstRun) {
                log.info("First run detected — showing setup wizard.");
                showSetupWizard(primaryStage);
            } else {
                showLogin(primaryStage);
            }

            log.info("Application started successfully.");

        } catch (Exception e) {
            log.error("Startup failed", e);
            showFatal(
                "The application failed to start.",
                "Please check the logs at ~/.propertymanager/logs/app.log"
            );
            Platform.exit();
        }
    }

    /* =================================================================== */

    private void showLogin(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            Main.class.getResource("/fxml/login.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 900, 600);
        scene.getStylesheets().add(
            Main.class.getResource("/css/app.css").toExternalForm());

        stage.setTitle("Property Management System");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setResizable(false);
        stage.show();
    }

    /* =================================================================== */

    private void showSetupWizard(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
            Main.class.getResource("/fxml/setup-wizard.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 820, 640);
        scene.getStylesheets().add(
            Main.class.getResource("/css/app.css").toExternalForm());

        stage.setTitle("Welcome — Property Management System Setup");
        stage.setScene(scene);
        stage.setMinWidth(760);
        stage.setMinHeight(560);
        stage.setResizable(false);

        // Prevent the app from quitting when the wizard closes
        Platform.setImplicitExit(false);

        // When the wizard closes, open the login window
        stage.setOnHidden(e -> {
            try {
                Stage loginStage = new Stage();
                showLogin(loginStage);
            } catch (Exception ex) {
                log.error("Failed to show login window after setup", ex);
            } finally {
                // Restore normal exit behavior once login is on screen
                Platform.setImplicitExit(true);
            }
        });

        stage.show();
    }

    /* =================================================================== */

    @Override
    public void stop() {
        Database.shutdown();
    }

    private void showFatal(String header, String body) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Startup Error");
        a.setHeaderText(header);
        a.setContentText(body);
        a.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
