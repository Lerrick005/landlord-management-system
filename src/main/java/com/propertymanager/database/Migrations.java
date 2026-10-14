package com.propertymanager.database;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs Flyway migrations on startup.
 *
 * Migration files live in: src/main/resources/db/migration/
 * Naming convention:        V1__init_schema.sql, V2__seed_roles.sql, etc.
 *
 * Flyway keeps a "flyway_schema_history" table to track what has been applied.
 */
public final class Migrations {

    private static final Logger log = LoggerFactory.getLogger(Migrations.class);

    private Migrations() {
        // utility class
    }

    /** Runs all pending migrations. */
    public static void run() {
        Flyway flyway = Flyway.configure()
                .dataSource(Database.dataSource())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();

        MigrateResult result = flyway.migrate();

        if (result.migrationsExecuted == 0) {
            log.info("Database schema is up to date. No migrations to apply.");
        } else {
            log.info("Migrations applied: {} (schema now at version {})",
                     result.migrationsExecuted, result.targetSchemaVersion);
        }
    }
}
