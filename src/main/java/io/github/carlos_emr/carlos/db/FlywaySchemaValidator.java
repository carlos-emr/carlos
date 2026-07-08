/**
 * CARLOS EMR - Clinical Assisting Recording Ledger Open Source
 *
 * Copyright (c) 2026 CARLOS EMR contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package io.github.carlos_emr.carlos.db;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.beans.factory.InitializingBean;

/**
 * Application-boot schema-version gate backed by Flyway.
 *
 * <p>CARLOS manages its MariaDB/MySQL schema with Flyway migrations (a consolidated
 * {@code V1} baseline plus dated {@code VYYYY.MM.DD} deltas). In production the migrations are
 * applied by an explicit, operator-gated step ({@code carlos-ctl db migrate}, run after a
 * pre-migration backup) — <strong>never</strong> silently on application boot, because a
 * multi-node deployment would otherwise have every instance racing to migrate a PHI schema on a
 * pod restart, and regulators expect schema changes to be a reviewed, logged operation.</p>
 *
 * <p>This bean therefore runs at context init in one of three modes, selected by the
 * {@code carlos.flyway.onBoot} property:</p>
 * <ul>
 *   <li>{@code off} (default) — do nothing. Schema is assumed to be managed entirely out of band
 *       (the legacy shell-script build, or an operator-run {@code migrate}). This preserves the
 *       historical behaviour where the app performs no schema management.</li>
 *   <li>{@code validate} — verify the database is at (or ahead of) the schema the deployed WAR
 *       expects and <strong>fail fast</strong> if it is behind. This is the recommended production
 *       posture once the baseline has been adopted: the app refuses to start against a schema it
 *       was not built for, rather than failing later with obscure column-not-found errors.</li>
 *   <li>{@code migrate} — apply pending migrations on boot. Intended for the disposable
 *       devcontainer / single-node dev database only; do not use in multi-node production.</li>
 * </ul>
 *
 * <p>The bean reads migrations from the classpath location(s) in {@code carlos.flyway.locations}
 * (the WAR ships them at {@code db/migration}). It is deliberately import-light and holds no
 * Hibernate/JPA dependency so it can run before the {@code EntityManagerFactory} is touched.</p>
 */
public class FlywaySchemaValidator implements InitializingBean {

    private static final Logger logger = LogManager.getLogger(FlywaySchemaValidator.class);

    /** Do-nothing default: schema managed out of band. */
    static final String MODE_OFF = "off";
    /** Verify-and-fail-fast: refuse to start if the schema is behind the deployed WAR. */
    static final String MODE_VALIDATE = "validate";
    /** Apply pending migrations on boot (dev/single-node only). */
    static final String MODE_MIGRATE = "migrate";

    private final DataSource dataSource;
    private final String mode;
    private final String[] locations;

    /**
     * @param dataSource the application {@code DataSource} (same pool the app uses); Flyway opens
     *                   its own short-lived connections from it and never holds one.
     * @param mode       one of {@code off} / {@code validate} / {@code migrate}; unknown or blank
     *                   values are treated as {@code off} (fail-safe: never block boot on a typo).
     * @param locations  comma-separated Flyway locations (e.g. {@code classpath:db/migration/common,
     *                   classpath:db/migration/on}); blank disables the gate.
     */
    public FlywaySchemaValidator(DataSource dataSource, String mode, String locations) {
        this.dataSource = dataSource;
        this.mode = normalizeMode(mode);
        this.locations = splitLocations(locations);
    }

    private static String normalizeMode(String raw) {
        if (raw == null) {
            return MODE_OFF;
        }
        String m = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (MODE_VALIDATE.equals(m) || MODE_MIGRATE.equals(m)) {
            return m;
        }
        return MODE_OFF;
    }

    private static String[] splitLocations(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void afterPropertiesSet() {
        if (MODE_OFF.equals(mode)) {
            logger.info("Flyway boot gate disabled (carlos.flyway.onBoot=off); schema managed out of band");
            return;
        }
        if (locations.length == 0) {
            logger.warn("carlos.flyway.onBoot={} but carlos.flyway.locations is empty — skipping schema gate", mode);
            return;
        }
        // baselineOnMigrate lets an existing (pre-Flyway) datadir adopt the baseline in place: the
        // first migrate stamps flyway_schema_history at baselineVersion instead of erroring on a
        // non-empty schema. Harmless for a fresh DB that starts empty.
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        if (MODE_MIGRATE.equals(mode)) {
            MigrateResult result = flyway.migrate();
            logger.info("Flyway boot migrate complete: {} migration(s) applied, schema now at {}",
                    result.migrationsExecuted, result.targetSchemaVersion);
            return;
        }

        // MODE_VALIDATE: fail fast when the running schema is behind the deployed WAR.
        flyway.validate();
        MigrationInfo current = flyway.info().current();
        logger.info("Flyway schema validation passed; database at version {}",
                current != null ? current.getVersion() : "(none)");
    }
}
