/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.service;

import java.sql.Connection;
import java.util.concurrent.Callable;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

/**
 * Serializes claim generation across both Teleplan entry points and application instances.
 * The dedicated connection owns the database advisory lock until the entire operation completes.
 * @since 2026-11-09
 */
@Service
public class TeleplanExportCoordinator {
    private final DataSource dataSource;
    public TeleplanExportCoordinator(DataSource dataSource) { this.dataSource = dataSource; }

    public <T> T generate(Callable<T> operation) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            if (catalog == null || catalog.isBlank()) throw new IllegalStateException("Teleplan database is unavailable");
            String lock = "carlos:teleplan:" + java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(catalog.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 32);
            try (var statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                statement.setString(1, lock);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next() || rows.getInt(1) != 1) {
                        throw new IllegalStateException("Another Teleplan export is running; retry when it finishes");
                    }
                }
            } catch (java.sql.SQLException acquisitionFailure) {
                try { connection.abort(Runnable::run); }
                catch (java.sql.SQLException abortFailure) { acquisitionFailure.addSuppressed(abortFailure); }
                throw acquisitionFailure;
            }
            try (var heldLock = new GenerationLock(connection, lock)) {
                return operation.call();
            }
        }
    }

    /** TWR preserves an operation failure and suppresses any lock-release failure on it. */
    private record GenerationLock(Connection connection, String name) implements AutoCloseable {
        @Override
        public void close() throws java.sql.SQLException {
            try (var statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, name);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next() || rows.getInt(1) != 1) throw new java.sql.SQLException("Teleplan lock release failed");
                }
            } catch (java.sql.SQLException releaseFailure) {
                // A pool must never receive a physical connection still owning this lock.
                try { connection.abort(Runnable::run); }
                catch (java.sql.SQLException abortFailure) { releaseFailure.addSuppressed(abortFailure); }
                throw releaseFailure;
            }
        }
    }
}
