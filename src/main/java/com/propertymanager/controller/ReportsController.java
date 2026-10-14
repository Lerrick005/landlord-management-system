package com.propertymanager.controller;

import com.propertymanager.reports.ReportType;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Controller for reports.fxml.
 *
 * Builds a card per ReportType, styled with our design tokens,
 * and opens the report viewer dialog on click.
 */
public class ReportsController {

    private static final Logger log = LoggerFactory.getLogger(ReportsController.class);

    @FXML private TilePane reportGrid;

    /* =================================================================== */

    @FXML
    private void initialize() {
        buildCards();
    }

    /* =================================================================== */

    private void buildCards() {
        reportGrid.getChildren().clear();
        for (ReportType type : ReportType.values()) {
            reportGrid.getChildren().add(buildCard(type));
        }
    }

    /* =================================================================== */

    /** Builds one clickable report card. */
    private VBox buildCard(ReportType type) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(20));
        card.setPrefWidth(260);
        card.setMinHeight(150);
        card.getStyleClass().addAll("card", "report-card");
        card.setCursor(Cursor.HAND);

        // Icon circle
        StackPane iconWrap = new StackPane();
        iconWrap.setMinSize(44, 44);
        iconWrap.setMaxSize(44, 44);
        iconWrap.setStyle(
            "-fx-background-color: rgba(201,162,74,0.15);" +
            "-fx-background-radius: 10;");

        FontIcon icon = new FontIcon(type.getIconCode());
        icon.setIconSize(22);
        icon.setIconColor(javafx.scene.paint.Color.web("#a8842f"));
        iconWrap.getChildren().add(icon);

        // Title
        Label title = new Label(type.getTitle());
        title.getStyleClass().add("section-title");

        // Description
        Label desc = new Label(type.getDescription());
        desc.getStyleClass().add("label-muted");
        desc.setWrapText(true);

        card.getChildren().addAll(iconWrap, title, desc);

        // Click handler
        card.setOnMouseClicked(e -> openReport(type));

        return card;
    }

    /* =================================================================== */

    /** Opens the report viewer dialog for the given report type. */
    private void openReport(ReportType type) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/report-view.fxml"));
            Parent root = loader.load();

            ReportViewerController controller = loader.getController();
            controller.setReportType(type);

            // Size the dialog to fit within the screen, leaving room for the taskbar
            var screen = Screen.getPrimary().getVisualBounds();
            double w = Math.min(1100, screen.getWidth()  - 60);
            double h = Math.min(720,  screen.getHeight() - 80);

            Stage dialog = new Stage();
            dialog.setTitle("Report — " + type.getTitle());
            Scene scene = new Scene(root, w, h);
            scene.getStylesheets().add(
                getClass().getResource("/css/app.css").toExternalForm());
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(reportGrid.getScene().getWindow());
            dialog.setScene(scene);
            dialog.setMinWidth(760);
            dialog.setMinHeight(500);
            dialog.setMaxHeight(screen.getHeight() - 40);
            dialog.setOnShown(e -> dialog.centerOnScreen());
            dialog.showAndWait();

        } catch (IOException e) {
            log.error("Failed to open report viewer for {}", type, e);
            showError("Unable to open the report.");
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
