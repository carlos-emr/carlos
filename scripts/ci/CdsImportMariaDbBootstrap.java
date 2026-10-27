/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;
import org.flywaydb.core.Flyway;

/** Reproducible disposable MariaDB bootstrap using the actual complete shipped Flyway chain. */
public final class CdsImportMariaDbBootstrap {
    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !(args[2].equals("on") || args[2].equals("bc"))) {
            throw new IllegalArgumentException("Usage: protected-jdbc-properties migration-root on|bc");
        }
        Properties jdbc = new Properties();
        try (InputStream input = Files.newInputStream(Path.of(args[0]))) { jdbc.load(input); }
        String configuredUrl = jdbc.getProperty("jdbcUrl", "");
        // Exercise the same MySQL Connector/J shipped in the WAR against the MariaDB server.
        String url = configuredUrl.startsWith("jdbc:mariadb:")
                ? "jdbc:mysql:" + configuredUrl.substring("jdbc:mariadb:".length()) : configuredUrl;
        if (!url.matches("jdbc:mysql://[^/]+/(?:carlos_cds_test|cds_full_test|cds_ledger_test)(?:\\?.*)?")) {
            throw new IllegalArgumentException("Only explicitly named disposable CDS test databases are supported");
        }
        try (var connection = DriverManager.getConnection(url, jdbc.getProperty("jdbcUser"), jdbc.getProperty("jdbcPassword"));
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT VERSION(),@@innodb_page_size,@@innodb_snapshot_isolation,@@collation_server")) {
            rows.next();
            if (!rows.getString(1).contains("MariaDB") || rows.getInt(2)!=32768 || !rows.getBoolean(3)
                    || !rows.getString(4).equals("utf8mb4_general_ci")) {
                throw new IllegalStateException("MariaDB requires repository 32K/general_ci configuration and snapshot isolation");
            }
        }
        var flyway = Flyway.configure()
                .dataSource(url, jdbc.getProperty("jdbcUser"), jdbc.getProperty("jdbcPassword"))
                .locations("filesystem:" + Path.of(args[1], "common"), "filesystem:" + Path.of(args[1], args[2]))
                .baselineOnMigrate(false).validateOnMigrate(true).load();
        var migrated = flyway.migrate();
        flyway.validate();
        if (flyway.info().pending().length != 0) throw new IllegalStateException("Pending migrations remain");
        try (var connection = DriverManager.getConnection(url, jdbc.getProperty("jdbcUser"), jdbc.getProperty("jdbcPassword"));
                var statement = connection.createStatement()) {
            long tables;
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'")) {
                rows.next(); tables = rows.getLong(1);
            }
            if (tables < 300) throw new IllegalStateException("The full baseline was not installed");
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('drugs','preventions','allergies','cds_import_admission','cds_import_patient','cds_import_checkpoint') AND ENGINE='InnoDB'")) {
                rows.next(); if (rows.getInt(1) != 6) throw new IllegalStateException("Clinical/admission tables must be InnoDB");
            }
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM cds_import_admission WHERE id IN ('global','clinical-maintenance')")) {
                rows.next(); if (rows.getInt(1) != 2) throw new IllegalStateException("Admission seeds are missing");
            }
            System.out.println("Full " + args[2].toUpperCase(java.util.Locale.ROOT) + " Flyway chain validated: "
                    + tables + " tables; " + migrated.migrationsExecuted + " migrations applied in this invocation.");
        }
    }
}
