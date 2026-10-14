package com.propertymanager.database;

import com.propertymanager.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Owns the HikariCP connection pool. Initialized once at startup
 * and reused for the lifetime of the application.
 */
public final class Database {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private static HikariDataSource dataSource;

    private Database() {
        // utility class
    }

    /** Opens the pool. Safe to call multiple times — subsequent calls are no-ops. */
    public static synchronized void initialize() {
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }

        String url = String.format(
            "jdbc:postgresql://%s:%s/%s",
            AppConfig.get("db.host"),
            AppConfig.get("db.port"),
            AppConfig.get("db.name")
        );

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(AppConfig.get("db.user"));
        cfg.setPassword(AppConfig.get("db.password"));

        cfg.setMaximumPoolSize(AppConfig.getInt("db.pool.maxSize"));
        cfg.setMinimumIdle(AppConfig.getInt("db.pool.minIdle"));
        cfg.setPoolName("pms-pool");

        cfg.setConnectionTimeout(10_000);   // wait up to 10s for a connection
        cfg.setValidationTimeout(5_000);    // 5s for a validation query
        cfg.setIdleTimeout(600_000);        // 10 min before idle connections close
        cfg.setMaxLifetime(1_800_000);      // 30 min max lifetime per connection

        dataSource = new HikariDataSource(cfg);
        log.info("Database pool initialized: {}", url);
    }

    /** Returns the underlying DataSource. Use sparingly — prefer getConnection(). */
    public static DataSource dataSource() {
        if (dataSource == null) {
            throw new IllegalStateException("Database not initialized. Call Database.initialize() first.");
        }
        return dataSource;
    }

    /** Borrows a connection from the pool. Caller must close it (try-with-resources). */
    public static Connection getConnection() throws SQLException {
        return dataSource().getConnection();
    }

    /** Quick connectivity check used at startup and in settings screens. */
    public static boolean testConnection() {
        try (Connection c = getConnection()) {
            return c.isValid(3);
        } catch (SQLException e) {
            log.warn("Database connectivity check failed: {}", e.getMessage());
            return false;
        }
    }

    /** Closes the pool. Called on application shutdown. */
    public static synchronized void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("Database pool closed.");
        }
    }
}
