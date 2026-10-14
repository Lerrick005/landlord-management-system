package com.propertymanager.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

/**
 * Controller for placeholder.fxml.
 *
 * Used as a stand-in for every section that will be built in later phases:
 * Properties, Tenants, Payments, Reports, Users, Audit Logs, Settings.
 *
 * The shell (MainController) calls setTitle() / setSubtitle() after loading
 * so the same FXML file can serve any of those sections.
 */
public class PlaceholderController {

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;

    public void setTitle(String title) {
        if (titleLabel != null) titleLabel.setText(title);
    }

    public void setSubtitle(String subtitle) {
        if (subtitleLabel != null) subtitleLabel.setText(subtitle);
    }
}
