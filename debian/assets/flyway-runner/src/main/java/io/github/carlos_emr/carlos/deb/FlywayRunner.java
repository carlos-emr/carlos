/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.deb;

import java.util.Arrays;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * Minimal Flyway front end for the Debian package's {@code carlos-ctl db-*} verbs.
 *
 * <p>The package deliberately does <em>not</em> ship the Flyway command-line
 * distribution. Flyway core, the MySQL/MariaDB module and the JDBC driver are
 * already inside the deployed CARLOS WAR (the application uses them for its
 * boot-time {@code carlos.flyway.onBoot} gate), and the migration set is on
 * that same WAR classpath at {@code db/migration}. Running against the WAR's
 * own jars and its own migration resources is what makes the history this
 * writes byte-for-byte acceptable to the validator the application runs at
 * startup — a separately downloaded CLI could drift from the deployed WAR and
 * turn every boot into a checksum failure.</p>
 *
 * <p>Credentials arrive in the environment ({@code FLYWAY_URL},
 * {@code FLYWAY_USER}, {@code FLYWAY_PASSWORD}), never on the command line:
 * any local user can read {@code /proc/&lt;pid&gt;/cmdline} while the process
 * runs, and this one connects to a database holding PHI.</p>
 *
 * <p>Usage: {@code FlywayRunner (info|validate|migrate|baseline|repair) locations}</p>
 *
 * @since 2026-08-20
 */
public final class FlywayRunner {

    /**
     * The genesis baseline of the CARLOS schema: common {@code V1} plus the
     * province {@code V1.0.1}/{@code V1.0.2} files. Adopting a pre-Flyway
     * OSCAR/OpenO datadir at plain {@code 1} would let Flyway re-run the
     * province files, and {@code V1.0.1} DROPs and recreates province tables —
     * data loss. Keep in lockstep with database/mysql/migration/flyway.conf.
     */
    private static final String BASELINE_VERSION = "1.0.2";
    private static final String BASELINE_DESCRIPTION = "CARLOS full genesis (V1 + province V1.0.1/V1.0.2)";

    private FlywayRunner() {
    }

    /**
     * Entry point invoked by {@code carlos-ctl db-migrate}.
     *
     * @param args {@code args[0]} is the Flyway command (info, validate,
     *             migrate, baseline or repair); {@code args[1]} is the
     *             comma-separated migration location list, restricted to the
     *             classpath locations this package ships.
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: FlywayRunner <info|validate|migrate|baseline|repair> <locations>");
            System.exit(2);
        }
        final String command = args[0];
        final String[] locations = args[1].split(",");
        // Only the migration sets this package ships. carlos-ctl passes
        // exactly these; refusing anything else (a filesystem: location above
        // all) means a compromised or confused caller cannot point a
        // root-credentialed migration run at arbitrary SQL on disk.
        for (String location : locations) {
            switch (location) {
                case "classpath:db/migration/common":
                case "classpath:db/migration/on":
                case "classpath:db/migration/bc":
                    break;
                default:
                    System.err.println("refusing migration location outside the packaged set: "
                            + location.replaceAll("[^\\x20-\\x7e]", "?"));
                    System.exit(2);
            }
        }

        final String url = requireEnv("FLYWAY_URL");
        final String user = requireEnv("FLYWAY_USER");
        final String password = System.getenv("FLYWAY_PASSWORD");

        final Flyway flyway = Flyway.configure(FlywayRunner.class.getClassLoader())
                .dataSource(url, user, password == null ? "" : password)
                .locations(locations)
                .baselineVersion(BASELINE_VERSION)
                .baselineDescription(BASELINE_DESCRIPTION)
                // Never auto-baseline: stamping a non-empty pre-Flyway schema
                // during migrate is the data-loss trap described above. The
                // `baseline` verb is the explicit, operator-chosen path.
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load();

        try {
            switch (command) {
                case "info":
                    printInfo(flyway.info().all());
                    break;
                case "validate":
                    flyway.validate();
                    System.out.println("schema validates against the migrations shipped in this WAR");
                    break;
                case "migrate":
                    final MigrateResult result = flyway.migrate();
                    System.out.printf("applied %d migration(s); schema is at %s%n",
                            result.migrationsExecuted,
                            result.targetSchemaVersion == null ? "(unchanged)" : result.targetSchemaVersion);
                    break;
                case "baseline":
                    flyway.baseline();
                    System.out.println("stamped flyway_schema_history at " + BASELINE_VERSION);
                    break;
                case "repair":
                    flyway.repair();
                    System.out.println("repaired flyway_schema_history");
                    break;
                default:
                    // The raw argument is process input; strip anything
                    // non-printable rather than echoing it verbatim into a
                    // log a terminal will render.
                    System.err.println("unknown command: "
                            + command.replaceAll("[^\\x20-\\x7e]", "?"));
                    System.exit(2);
            }
        } catch (RuntimeException e) {
            // Flyway's own message is the useful part; a stack trace here only
            // buries it in the postinst output an operator has to read.
            System.err.println("flyway " + command + " failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printInfo(MigrationInfo[] all) {
        final List<MigrationInfo> infos = Arrays.asList(all);
        System.out.printf("%-12s %-10s %-60s %s%n", "VERSION", "STATE", "DESCRIPTION", "TYPE");
        for (MigrationInfo info : infos) {
            System.out.printf("%-12s %-10s %-60s %s%n",
                    info.getVersion() == null ? "" : info.getVersion().toString(),
                    info.getState().getDisplayName(),
                    info.getDescription(),
                    info.getType());
        }
    }

    private static String requireEnv(String name) {
        final String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            System.err.println(name + " must be set in the environment");
            System.exit(2);
        }
        return value;
    }
}
