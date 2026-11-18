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

    private List<UserProperty> lockedLegacyPasswords() {
        return entityManager.createQuery("SELECT p FROM UserProperty p WHERE p.name = :name ORDER BY p.id", UserProperty.class)
                .setParameter("name", UserProperty.MCEDT_ACCOUNT_PASSWORD)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    private void store(String encrypted) {
        entityManager.createNativeQuery("UPDATE mcedt_credential SET encrypted_password = :password WHERE id = 1")
                .setParameter("password", encrypted).executeUpdate();
    }

    private void removeLegacy(List<UserProperty> rows) {
        for (UserProperty row : rows) {
            entityManager.remove(row);
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
        try {
            if (password != null && password.codePointCount(0, password.length()) > 4000) {
                throw new IllegalArgumentException("MCEDT password exceeds storage contract");
            }
            String encrypted = EncryptionUtils.encrypt(password);
            lockCredential();
            List<UserProperty> legacy = lockedLegacyPasswords();
            store(encrypted);
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
            // Both reads must be current locking reads. An ordinary ciphertext
            // SELECT would open a repeatable-read snapshot before a legacy writer
            // commits and can produce MariaDB error1020 when its row is then locked.
            String encrypted = (String) entityManager.createNativeQuery(
                    "SELECT encrypted_password FROM mcedt_credential WHERE id = 1 FOR UPDATE", String.class).getSingleResult();
            List<UserProperty> legacy = lockedLegacyPasswords();
            if (encrypted == null && !legacy.isEmpty()) {
                encrypted = EncryptionUtils.encrypt(legacy.get(0).getValue());
                store(encrypted);
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
