package com.propertymanager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads application configuration from application.properties
 * and allows environment variables to override file values.
 */
public final class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private static final Properties PROPS = new Properties();
    private static boolean loaded = false;

    private AppConfig() {
        // utility class — no instances
    }

    /** Loads the properties file if not already loaded. Idempotent. */
    public static synchronized void load() {
        if (loaded) return;

        try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in == null) {
                throw new IllegalStateException(
                    "application.properties not found on classpath. "
                    + "Copy application.properties.example to application.properties."
                );
            }
            PROPS.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load application.properties", e);
        }

        // Environment variable overrides — useful for keeping secrets out of the file
        override("db.host",     "DB_HOST");
        override("db.port",     "DB_PORT");
        override("db.name",     "DB_NAME");
        override("db.user",     "DB_USER");
        override("db.password", "DB_PASSWORD");

        loaded = true;
        log.info("Configuration loaded (db={}@{})",
                 get("db.name"), get("db.host"));
    }

    private static void override(String key, String envVar) {
        String value = System.getenv(envVar);
        if (value != null && !value.isBlank()) {
            PROPS.setProperty(key, value);
        }
    }

    /** Returns a raw string value, loading the file first if needed. */
    public static String get(String key) {
        if (!loaded) load();
        String v = PROPS.getProperty(key);
        if (v == null) {
            throw new IllegalArgumentException("Missing config key: " + key);
        }
        return v.trim();
    }

    /** Convenience for integer config values. */
    public static int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    /** Convenience for boolean config values. */
    public static boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }
}
