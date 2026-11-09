/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.db;

import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Keep develop's original TEXT column out of a frozen release's VARCHAR capacity statement. */
final class FlywayPropertyCapacityPreservation implements Callback {
    static final String SCRIPT = "V1.0.59.27__preserve_oscar19_text_capacity.sql";
    private static final ThreadLocal<Boolean> MIGRATING = new ThreadLocal<>();
    private final boolean historicalArrival;

    FlywayPropertyCapacityPreservation(boolean historicalArrival) {
        this.historicalArrival = historicalArrival;
    }

    @Override
    public boolean supports(Event event, Context context) {
        if (event == Event.BEFORE_VALIDATE || event == Event.BEFORE_EACH_MIGRATE) return true;
        if (!historicalArrival) return false;
        return event == Event.BEFORE_MIGRATE
                || ((event == Event.AFTER_EACH_MIGRATE || event == Event.AFTER_EACH_MIGRATE_ERROR)
                        && context.getMigrationInfo() != null
                        && SCRIPT.equals(context.getMigrationInfo().getScript()));
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return false;
    }

    @Override
    public String getCallbackName() {
        return "reviewed-develop-property-capacity-preservation";
    }

    @Override
    public void handle(Event event, Context context) {
        try {
            Connection connection = context.getConnection();
            if (!mariaDb(connection)) return;
            if (event == Event.BEFORE_VALIDATE) {
                if (staged(connection)) {
                    verifyStaged(connection);
                    if (!Boolean.TRUE.equals(MIGRATING.get()))
                        throw new IllegalStateException(
                                "FLYWAY_RELEASED_PROPERTY_RECOVERY_REQUIRED");
                }
            } else if (event == Event.BEFORE_MIGRATE) {
                if (staged(connection)) verifyStaged(connection);
                else verifyOriginal(connection);
            } else if (event == Event.BEFORE_EACH_MIGRATE) {
                // Flyway holds its schema-history lock here. Recovery cannot alter an active
                // migration's staged column, even if an outer coordination connection is lost.
                restore(connection);
                if (historicalArrival
                        && context.getMigrationInfo() != null
                        && SCRIPT.equals(context.getMigrationInfo().getScript()))
                    preserve(connection);
            } else restore(connection);
        } catch (SQLException failure) {
            throw refused(failure);
        }
    }

    static <T> T duringMigrate(Supplier<T> operation) {
        require(MIGRATING.get() == null);
        MIGRATING.set(Boolean.TRUE);
        try {
            return operation.get();
        } finally {
            MIGRATING.remove();
        }
    }

    /** Repair may inspect a recoverable stage but never changes its data or column shape. */
    static void verifyPendingRecovery(Connection connection) {
        try {
            if (mariaDb(connection) && staged(connection)) verifyStaged(connection);
        } catch (SQLException failure) {
            throw refused(failure);
        }
    }

    private static void preserve(Connection connection) throws SQLException {
        verifyOriginal(connection);
        try (var sql = connection.createStatement()) {
            sql.executeUpdate(
                    "ALTER TABLE property RENAME COLUMN `value` TO carlos_forward_59_27_value, ADD"
                        + " COLUMN `value` VARCHAR(4000) CHARACTER SET utf8mb4 COLLATE"
                        + " utf8mb4_general_ci NULL DEFAULT NULL AFTER carlos_forward_59_27_value");
        }
    }

    private static void restore(Connection connection) throws SQLException {
        if (!staged(connection)) return;
        verifyStaged(connection);
        try (var sql = connection.createStatement()) {
            sql.executeUpdate(
                    "ALTER TABLE property DROP COLUMN `value`, RENAME COLUMN"
                            + " carlos_forward_59_27_value TO `value`");
        }
        verifyOriginal(connection);
    }

    private static void verifyStaged(Connection connection) throws SQLException {
        require(
                names(connection)
                        .equals(
                                List.of(
                                        "name",
                                        "carlos_forward_59_27_value",
                                        "value",
                                        "id",
                                        "provider_no")));
        verifyValue(connection, "carlos_forward_59_27_value", "text");
        verifyValue(connection, "value", "varchar(4000)");
        verifyDependencies(connection);
        try (var query =
                        connection.prepareStatement(
                                "select count(*) from property where `value` IS NOT NULL");
                var rows = query.executeQuery()) {
            require(rows.next() && rows.getLong(1) == 0);
        }
    }

    private static void verifyOriginal(Connection connection) throws SQLException {
        require(names(connection).equals(List.of("name", "value", "id", "provider_no")));
        verifyValue(connection, "value", "text");
        verifyDependencies(connection);
    }

    private static void verifyDependencies(Connection connection) throws SQLException {
        try (var query =
                        connection.prepareStatement(
                                "select ENGINE from information_schema.TABLES where"
                                    + " TABLE_SCHEMA=DATABASE() and lower(TABLE_NAME)='property'"
                                    + " and TABLE_TYPE='BASE TABLE'");
                var rows = query.executeQuery()) {
            require(rows.next() && "InnoDB".equals(rows.getString(1)) && !rows.next());
        }
        try (var query =
                        connection.prepareStatement(
                                "select count(*) from information_schema.TRIGGERS where"
                                        + " EVENT_OBJECT_SCHEMA=DATABASE() and"
                                        + " lower(EVENT_OBJECT_TABLE)='property'");
                var rows = query.executeQuery()) {
            require(rows.next() && rows.getLong(1) == 0);
        }
        try (var query =
                        connection.prepareStatement(
                                "select count(*) from information_schema.STATISTICS where"
                                    + " TABLE_SCHEMA=DATABASE() and lower(TABLE_NAME)='property'"
                                    + " and COLUMN_NAME IN ('value','carlos_forward_59_27_value')");
                var rows = query.executeQuery()) {
            require(rows.next() && rows.getLong(1) == 0);
        }
    }

    private static void verifyValue(Connection connection, String column, String type)
            throws SQLException {
        try (var query =
                connection.prepareStatement(
                        "select"
                            + " COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,CHARACTER_SET_NAME,COLLATION_NAME"
                            + " from information_schema.COLUMNS where TABLE_SCHEMA=DATABASE() and"
                            + " lower(TABLE_NAME)='property' and COLUMN_NAME=?")) {
            query.setString(1, column);
            try (var rows = query.executeQuery()) {
                require(
                        rows.next()
                                && type.equals(rows.getString(1))
                                && "YES".equals(rows.getString(2))
                                && "NULL".equals(rows.getString(3))
                                && "".equals(rows.getString(4))
                                && "utf8mb4".equals(rows.getString(5))
                                && "utf8mb4_general_ci".equals(rows.getString(6))
                                && !rows.next());
            }
        }
    }

    private static List<String> names(Connection connection) throws SQLException {
        var names = new ArrayList<String>();
        try (var query =
                        connection.prepareStatement(
                                "select COLUMN_NAME from information_schema.COLUMNS where"
                                    + " TABLE_SCHEMA=DATABASE() and lower(TABLE_NAME)='property'"
                                    + " order by ORDINAL_POSITION");
                var rows = query.executeQuery()) {
            while (rows.next()) names.add(rows.getString(1));
        }
        return names;
    }

    private static boolean staged(Connection connection) throws SQLException {
        try (var query =
                        connection.prepareStatement(
                                "select count(*) from information_schema.COLUMNS where"
                                    + " TABLE_SCHEMA=DATABASE() and lower(TABLE_NAME)='property'"
                                    + " and COLUMN_NAME='carlos_forward_59_27_value'");
                var rows = query.executeQuery()) {
            require(rows.next());
            long count = rows.getLong(1);
            require(count <= 1);
            return count == 1;
        }
    }

    private static boolean mariaDb(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        return "MariaDB".equals(product) || "MySQL".equals(product);
    }

    private static void require(boolean condition) {
        if (!condition) throw refused(null);
    }

    private static IllegalStateException refused(SQLException cause) {
        return new IllegalStateException("FLYWAY_RELEASED_PROPERTY_PREPARATION_REFUSED", cause);
    }
}
