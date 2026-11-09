/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.db;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Metadata-only preparation for the exact frozen consent migration under Flyway's lock. */
final class FlywayConsentAuditPreparation implements Callback {
    static final String SCRIPT = "V1.0.59.30__one_live_consent_per_type.sql";
    private static final List<String> COLUMN_ORDER =
            List.of(
                    "consent_id",
                    "migration",
                    "reason",
                    "prior_explicit",
                    "prior_optout",
                    "prior_deleted",
                    "recorded_at");
    private static final Map<String, List<String>> TYPES =
            Map.of(
                    "consent_id",
                    List.of("int(10)", "int(11)"),
                    "migration",
                    List.of("varchar(64)"),
                    "reason",
                    List.of("varchar(32)"),
                    "prior_explicit",
                    List.of("tinyint(1)"),
                    "prior_optout",
                    List.of("tinyint(1)"),
                    "prior_deleted",
                    List.of("tinyint(1)"),
                    "recorded_at",
                    List.of("datetime"));

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_MIGRATE
                || (event == Event.BEFORE_EACH_MIGRATE
                        && context.getMigrationInfo() != null
                        && SCRIPT.equals(context.getMigrationInfo().getScript()));
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return false;
    }

    @Override
    public String getCallbackName() {
        return "reviewed-develop-consent-audit-preparation";
    }

    @Override
    public void handle(Event event, Context context) {
        try {
            Connection connection = context.getConnection();
            verifyColumns(connection);
            verifyIndexes(connection);
            verifyTableAndDependencies(connection);
            // Develop 77 omitted this default. MariaDB rejects release 59.30's INSERT even
            // when SELECT returns no rows. Existing timestamps and audit rows are untouched.
            // Migration 86 subsequently reconciles the complete physical audit structure.
            if (event == Event.BEFORE_EACH_MIGRATE) {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate(
                            "ALTER TABLE Consent_migration_audit MODIFY COLUMN recorded_at DATETIME"
                                + " NOT NULL DEFAULT CURRENT_TIMESTAMP");
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException(
                    "FLYWAY_RELEASED_CONSENT_AUDIT_PREPARATION_REFUSED", failure);
        }
    }

    private static void verifyColumns(Connection connection) throws SQLException {
        var names = new ArrayList<String>();
        try (var query =
                        connection.prepareStatement(
                                "select"
                                    + " COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,CHARACTER_SET_NAME,COLLATION_NAME"
                                    + " from information_schema.COLUMNS where"
                                    + " TABLE_SCHEMA=DATABASE() and"
                                    + " lower(TABLE_NAME)='consent_migration_audit' order by"
                                    + " ORDINAL_POSITION");
                var rows = query.executeQuery()) {
            while (rows.next()) {
                names.add(rows.getString(1));
                verifyColumn(rows);
            }
        }
        require(names.equals(COLUMN_ORDER));
    }

    private static void verifyColumn(ResultSet row) throws SQLException {
        String name = row.getString(1), type = row.getString(2), defaultValue = row.getString(4);
        require(TYPES.containsKey(name) && TYPES.get(name).contains(type));
        require((name.startsWith("prior_") ? "YES" : "NO").equals(row.getString(3)));
        require("".equals(row.getString(5)));
        if (name.equals("recorded_at")) {
            require(defaultValue == null || defaultValue.equals("current_timestamp()"));
        } else
            require(
                    defaultValue == null
                            || (name.startsWith("prior_") && defaultValue.equals("NULL")));
        if (type.startsWith("varchar")) {
            require(
                    "utf8mb4".equals(row.getString(6))
                            && "utf8mb4_general_ci".equals(row.getString(7)));
        } else require(row.getString(6) == null && row.getString(7) == null);
    }

    private static void verifyIndexes(Connection connection) throws SQLException {
        var keys = new ArrayList<String>();
        try (var query =
                        connection.prepareStatement(
                                "select INDEX_NAME,COLUMN_NAME,NON_UNIQUE from"
                                    + " information_schema.STATISTICS where TABLE_SCHEMA=DATABASE()"
                                    + " and lower(TABLE_NAME)='consent_migration_audit' order by"
                                    + " INDEX_NAME,SEQ_IN_INDEX");
                var rows = query.executeQuery()) {
            while (rows.next()) {
                require("PRIMARY".equals(rows.getString(1)) && rows.getInt(3) == 0);
                keys.add(rows.getString(2));
            }
        }
        require(keys.equals(List.of("consent_id", "migration", "reason")));
    }

    private static void verifyTableAndDependencies(Connection connection) throws SQLException {
        try (var query =
                        connection.prepareStatement(
                                "select ENGINE from information_schema.TABLES where"
                                    + " TABLE_SCHEMA=DATABASE() and"
                                    + " lower(TABLE_NAME)='consent_migration_audit' and"
                                    + " TABLE_TYPE='BASE TABLE'");
                var rows = query.executeQuery()) {
            require(rows.next() && "InnoDB".equals(rows.getString(1)) && !rows.next());
        }
        try (var query =
                        connection.prepareStatement(
                                "select count(*) from information_schema.TRIGGERS where"
                                    + " EVENT_OBJECT_SCHEMA=DATABASE() and"
                                    + " lower(EVENT_OBJECT_TABLE)='consent_migration_audit'");
                var rows = query.executeQuery()) {
            require(rows.next() && rows.getInt(1) == 0);
        }
        try (var query =
                        connection.prepareStatement(
                                "select count(*) from information_schema.KEY_COLUMN_USAGE where"
                                    + " REFERENCED_TABLE_SCHEMA=DATABASE() and"
                                    + " lower(REFERENCED_TABLE_NAME)='consent_migration_audit'");
                var rows = query.executeQuery()) {
            require(rows.next() && rows.getInt(1) == 0);
        }
    }

    private static void require(boolean condition) {
        if (!condition)
            throw new IllegalStateException("FLYWAY_RELEASED_CONSENT_AUDIT_PREPARATION_REFUSED");
    }
}
