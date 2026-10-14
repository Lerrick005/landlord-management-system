package com.propertymanager;

/**
 * Non-JavaFX launcher class.
 *
 * jpackage's launcher refuses to start a class that directly extends
 * javafx.application.Application when it's packaged as a non-modular jar.
 * This class does not extend Application, so the launcher accepts it.
 * It simply delegates to Main.main().
 */
public class Launcher {
    public static void main(String[] args) {
        Main.main(args);
    }
}
