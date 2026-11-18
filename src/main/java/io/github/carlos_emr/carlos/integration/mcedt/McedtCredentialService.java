/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.integration.mcedt;

import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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

    private void lockCredential() {
        entityManager.createNativeQuery("SELECT id FROM mcedt_credential WHERE id = 1 FOR UPDATE", Integer.class)
                .getSingleResult();
    }

    private List<UserProperty> legacyPasswords(boolean lock) {
        var query = entityManager.createQuery(
                "SELECT p FROM UserProperty p WHERE p.name = :name ORDER BY p.id", UserProperty.class)
                .setParameter("name", UserProperty.MCEDT_ACCOUNT_PASSWORD);
        if (lock) {
            query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }
        return query.getResultList();
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

    private List<UserProperty> lockLegacyPasswordsAfterProbe() {
        if (isMariaDb()) {
            // Promote the nonlocking existence check to a current read, including commits
            // after the probe established a snapshot. Keep the caller's settings intact.
            return entityManager.unwrap(org.hibernate.Session.class).createNativeQuery(
                            "SET STATEMENT innodb_snapshot_isolation=OFF FOR "
                                    + "SELECT * FROM property WHERE name = :name ORDER BY id FOR UPDATE",
                            UserProperty.class)
                    .addSynchronizedEntityClass(UserProperty.class)
                    .setParameter("name", UserProperty.MCEDT_ACCOUNT_PASSWORD).getResultList();
        }
        return legacyPasswords(true);
    }

    private void store(String encrypted, String actor) {
        entityManager.createNativeQuery("UPDATE mcedt_credential SET encrypted_password = :password, "
                        + "lastUpdateUser = :actor, lastUpdateDate = CURRENT_TIMESTAMP WHERE id = 1")
                .setParameter("password", encrypted).setParameter("actor", actor).executeUpdate();
    }

    private void removeLegacy(List<UserProperty> rows) {
        if (rows.isEmpty()) {
            return;
        }
        // MariaDB snapshot isolation otherwise rejects cleanup when a legacy writer commits
        // after the nonlocking read. Scope current-row deletion to this statement, preserving
        // the caller's isolation and avoiding a second connection. The portable DELETE also
        // guards the name so a row renamed into an ordinary preference cannot be erased.
        String delete = (isMariaDb() ? "SET STATEMENT innodb_snapshot_isolation=OFF FOR " : "")
                + "DELETE FROM property WHERE id = :id AND name = :name";
        for (UserProperty row : rows) {
            entityManager.createNativeQuery(delete)
                    .setParameter("id", row.getId())
                    .setParameter("name", UserProperty.MCEDT_ACCOUNT_PASSWORD).executeUpdate();
            entityManager.detach(row);
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
            List<UserProperty> legacy = legacyPasswords(true);
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
                    "SELECT encrypted_password FROM mcedt_credential WHERE id = 1 FOR UPDATE", String.class).getSingleResult();
            // A null singleton is normal with a configured or cleared password. Check for
            // migration without taking an index gap lock on the unrelated preference table.
            if (encrypted == null && !hasLegacyPassword()) {
                return fallback;
            }
            List<UserProperty> legacy = encrypted == null ? lockLegacyPasswordsAfterProbe() : legacyPasswords(false);
            if (encrypted == null && !legacy.isEmpty()) {
                encrypted = EncryptionUtils.encrypt(legacy.get(0).getValue());
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
