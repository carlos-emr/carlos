/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package carlostest5922;

import java.lang.reflect.InvocationTargetException;
import java.sql.*;

public class ReportBlobReaderProbe {
    public static void main(String[] args) {
        try (Connection connection = installedConnection()) {
            connection.setReadOnly(true);
            verifyFixtureDatabase(connection);
            if (args.length == 1 && "--verify-target".equals(args[0])) return;
            try {
                checkReaders(connection);
            } catch (Exception | AssertionError failure) {
                System.err.println("Native BLOB reader probe failed: " + failure.getClass().getName());
                System.exit(1);
            }
        } catch (Exception failure) {
            System.err.println("Native BLOB reader database setup failed: " + failure.getClass().getName());
            System.exit(2);
        }
    }

    private static Connection installedConnection() throws Exception {
        var settings = new java.util.Properties();
        try (var input = java.nio.file.Files.newInputStream(java.nio.file.Path.of("/etc/carlos-emr/carlos.properties"))) {
            settings.load(input);
        }
        return DriverManager.getConnection("jdbc:mariadb://127.0.0.1:3306/" + settings.getProperty("db_name"),
                settings.getProperty("db_username"), settings.getProperty("db_password"));
    }

    private static void verifyFixtureDatabase(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "SELECT CONCAT_WS(':', HEX(DATABASE()), HEX(@@hostname), @@port, @@server_id, HEX(@@datadir))")) {
            String expected = System.getenv("PROBE_BLOB_DB_ID");
            if (expected == null || !rows.next() || !expected.equals(rows.getString(1))) {
                throw new SQLException("Installed database differs from fixture database");
            }
        }
    }

    private static void checkReaders(Connection connection) throws Exception {
        String expected = "FAKE-report-\u03a9";
        int failures = 0;
        String form = System.getenv("PROBE_BLOB_FORM_ID");
        String marker = System.getenv("PROBE_BLOB_FORM_MARKER");
        if (form == null || !form.matches("[1-9][0-9]*") || marker == null || !marker.startsWith("FAKE-")) {
            throw new AssertionError("Invalid owned fixture identity");
        }
        for (String name : new String[]{"configured", "template"}) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT errorLog AS value FROM eform WHERE fid=? AND form_name=? AND subject=? AND form_creator=? AND status=0")) {
                statement.setInt(1, Integer.parseInt(form));
                statement.setString(2, marker);
                statement.setString(3, marker);
                statement.setString(4, marker);
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next() || !expected.equals(rows.getString(1))) throw new AssertionError("Legacy string control failed");
                    int type = rows.getMetaData().getColumnType(1);
                    try {
                        Object value;
                        if (name.equals("configured")) {
                            Class<?> reader = Class.forName("io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery");
                            var method = reader.getDeclaredMethod("readValue", ResultSet.class, int.class, long.class, int.class);
                            method.setAccessible(true);
                            value = method.invoke(null, rows, 1, 1024L, 1024);
                        } else {
                            Class<?> reader = Class.forName("io.github.carlos_emr.carlos.report.reportByTemplate.ReportResultBudget");
                            var constructor = reader.getDeclaredConstructor();
                            constructor.setAccessible(true);
                            var method = reader.getDeclaredMethod("readCell", ResultSet.class, int.class, int.class);
                            method.setAccessible(true);
                            value = method.invoke(constructor.newInstance(), rows, 1, type);
                        }
                        if (!expected.equals(value)) throw new AssertionError("Reader changed synthetic UTF-8 text");
                        System.out.println(name + ": PASS, JDBC type=" + type + ", legacy string control=true");
                    } catch (InvocationTargetException error) {
                        failures++;
                        Throwable cause = error.getCause();
                        System.out.println(name + ": FAIL, JDBC type=" + type + ", cause="
                                + cause.getClass().getName() + ", legacy string control=true");
                    }
                }
            }
        }
        if (failures != 0) System.exit(1);
    }
}
