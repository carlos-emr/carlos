/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.integration.mcedt;

import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the clinic MCEDT credential separately from opaque legacy plaintext.
 * The seeded singleton row serializes reads, migration and first-time saves.
 *
 * @since 2026-11-17
 */
@Service
@Transactional
public class McedtCredentialService {
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private record LegacyPassword(int id, String value) {}

    private void lockCredential() {
        entityManager.createNativeQuery(currentRows("SELECT id FROM mcedt_credential WHERE id = 1 FOR UPDATE"), Integer.class)
                .getSingleResult();
    }

    private List<LegacyPassword> legacyPasswords(boolean lock) {
        String sql = "SELECT p.id, p.value FROM property p WHERE p.name = :name ORDER BY p.id";
        if (lock) {
            sql = currentRows(sql + " FOR UPDATE");
        }
        // Scalar values come from the current row, even if Hibernate already manages an
        // earlier version of the corresponding preference in the caller's transaction.
        return entityManager.unwrap(org.hibernate.Session.class).createNativeQuery(sql, Object[].class)
                .addSynchronizedEntityClass(UserProperty.class)
                .setParameter("name", UserProperty.MCEDT_ACCOUNT_PASSWORD)
                .getResultList().stream()
                .map(row -> new LegacyPassword(((Number) row[0]).intValue(), (String) row[1]))
                .toList();
    }

    private boolean hasLegacyPassword() {
        return !entityManager.createQuery("SELECT p.id FROM UserProperty p WHERE p.name = :name", Integer.class)
                .setParameter("name", UserProperty.MCEDT_ACCOUNT_PASSWORD)
                .setMaxResults(1).getResultList().isEmpty();
    }

    private boolean isMariaDb() {
        return entityManager.unwrap(org.hibernate.Session.class).doReturningWork(connection ->
                connection.getMetaData().getDatabaseProductName().equals("MariaDB"));
    }

    /** Use a current row without changing the caller's transaction isolation settings. */
    private String currentRows(String sql) {
        return isMariaDb() ? "SET STATEMENT innodb_snapshot_isolation=OFF FOR " + sql : sql;
    }

    private void store(String encrypted, String actor) {
        entityManager.createNativeQuery(currentRows("UPDATE mcedt_credential SET encrypted_password = :password, "
                        + "lastUpdateUser = :actor, lastUpdateDate = CURRENT_TIMESTAMP WHERE id = 1"))
                .setParameter("password", encrypted).setParameter("actor", actor).executeUpdate();
    }

    private void removeLegacy(List<LegacyPassword> rows) {
        if (rows.isEmpty()) {
            return;
        }
        // Guard the name so a row renamed into an ordinary preference cannot be erased.
        String delete = currentRows("DELETE FROM property WHERE id = :id AND name = :name");
        for (LegacyPassword row : rows) {
            // Remove any managed copy without reloading the value or flushing it after the delete.
            entityManager.detach(entityManager.getReference(UserProperty.class, row.id()));
            entityManager.createNativeQuery(delete)
                    .setParameter("id", row.id())
                    .setParameter("name", UserProperty.MCEDT_ACCOUNT_PASSWORD).executeUpdate();
        }
        entityManager.flush();
    }

    /**
     * Encrypts the replacement before changing storage and removes all legacy duplicates.
     *
     * @param password replacement plaintext under the existing null/blank and 4000-character contract
     * @throws IllegalStateException if validation, encryption or persistence fails, without a credential-bearing cause
     */
    public void savePassword(String password) {
        savePassword(password, "mcedt-service");
    }

    /**
     * Records the authenticated provider for an explicit replacement without auditing secret values.
     *
     * @param password String replacement plaintext, with a limit of 4000 Unicode code points.
     *                 Null or a value empty after trimming uses the configured fallback.
     * @param updatedBy String authenticated provider identifier, with a limit of 100 Unicode code points.
     *                  Null or blank uses the service actor {@code mcedt-service}.
     * @throws IllegalStateException if validation, encryption or persistence fails
     */
    public void savePassword(String password, String updatedBy) {
        try {
            String actor = updatedBy == null || updatedBy.isBlank() ? "mcedt-service" : updatedBy;
            if (actor.codePointCount(0, actor.length()) > 100) {
                throw new IllegalArgumentException("MCEDT actor exceeds storage contract");
            }
            if (password != null && password.codePointCount(0, password.length()) > 4000) {
                throw new IllegalArgumentException("MCEDT password exceeds storage contract");
            }
            String encrypted = EncryptionUtils.encrypt(password);
            lockCredential();
            List<LegacyPassword> legacy = legacyPasswords(true);
            store(encrypted, actor);
            removeLegacy(legacy);
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to securely save the MCEDT password");
        }
    }

    /**
     * Migrates the first legacy value and removes all legacy rows in the same locked transaction.
     * Legacy values are always plaintext, including literal encryption-marker prefixes.
     * Corrupt data in the dedicated encrypted record fails closed.
     *
     * @param fallback configured credential used when the stored password is missing or blank
     * @return decrypted stored password, or the configured fallback
     * @throws IllegalStateException if encryption, decryption or persistence fails, without a credential-bearing cause
     */
    public String getPassword(String fallback) {
        try {
            // The singleton is a current locking read, serializing saves and first-read migration.
            String encrypted = (String) entityManager.createNativeQuery(
                    currentRows("SELECT encrypted_password FROM mcedt_credential WHERE id = 1 FOR UPDATE"),
                    String.class).getSingleResult();
            // A null singleton is normal with a configured or cleared password. Check for
            // migration without taking an index gap lock on the unrelated preference table.
            if (encrypted == null && !hasLegacyPassword()) {
                return fallback;
            }
            List<LegacyPassword> legacy = legacyPasswords(encrypted == null);
            if (encrypted == null) {
                if (legacy.isEmpty()) {
                    return fallback;
                }
                encrypted = EncryptionUtils.encrypt(legacy.getFirst().value());
                store(encrypted, "mcedt-migration");
            }
            if (!EncryptionUtils.isEncrypted(encrypted)) {
                throw new IllegalStateException("Invalid MCEDT credential format");
            }
            String password = EncryptionUtils.decrypt(encrypted);
            removeLegacy(legacy);
            return password == null || password.trim().isEmpty() ? fallback : password;
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to securely read the MCEDT password");
        }
    }
}
