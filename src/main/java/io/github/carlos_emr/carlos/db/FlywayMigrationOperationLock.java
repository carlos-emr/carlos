/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.db;

import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/** Fence recovery together with the migration/repair, before Flyway performs its own validation. */
final class FlywayMigrationOperationLock {
    private static final String ACQUIRE =
            "SELECT GET_LOCK(CONCAT('carlos-release:',LEFT(SHA2(DATABASE(),256),40)),55)";
    private static final String RELEASE =
            "SELECT RELEASE_LOCK(CONCAT('carlos-release:',LEFT(SHA2(DATABASE(),256),40)))";

    private FlywayMigrationOperationLock() {}

    static <T> T execute(Flyway flyway, Function<Connection, T> operation) {
        try (var connection = flyway.getConfiguration().getDataSource().getConnection();
                var lock = acquire(connection)) {
            return operation.apply(connection);
        } catch (SQLException failure) {
            throw new IllegalStateException("FLYWAY_RELEASED_MIGRATION_LOCK_FAILED", failure);
        }
    }

    private static Lease acquire(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        boolean supported = "MySQL".equals(product) || "MariaDB".equals(product);
        if (supported) {
            try (var query = connection.prepareStatement(ACQUIRE)) {
                query.setQueryTimeout(60);
                try (var rows = query.executeQuery()) {
                    if (!rows.next() || rows.getInt(1) != 1)
                        throw new IllegalStateException("FLYWAY_RELEASED_MIGRATION_BUSY");
                }
            }
        }
        return new Lease(connection, supported);
    }

    private record Lease(Connection connection, boolean held) implements AutoCloseable {
        @Override
        public void close() throws SQLException {
            if (!held) return;
            try (var query = connection.prepareStatement(RELEASE);
                    var rows = query.executeQuery()) {
                if (!rows.next() || rows.getInt(1) != 1)
                    throw new IllegalStateException("FLYWAY_RELEASED_MIGRATION_LOCK_LOST");
            }
        }

        @Override
        public String toString() {
            return "Lease[protected]";
        }
    }
}
