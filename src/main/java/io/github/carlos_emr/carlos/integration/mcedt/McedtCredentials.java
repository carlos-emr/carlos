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

/** Stores the clinic's MCEDT credential with the existing application encryption key. */
@Service
@Transactional
public class McedtCredentials {
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private List<UserProperty> lockedPasswords() {
        return entityManager.createQuery("SELECT p FROM UserProperty p WHERE p.name = :name ORDER BY p.id", UserProperty.class)
                .setParameter("name", UserProperty.MCEDT_ACCOUNT_PASSWORD)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
    }

    /** Encrypt before mutating a row; a failed key or write never reports success. */
    public void savePassword(String password) {
        try {
            String encrypted = EncryptionUtils.encrypt(password);
            List<UserProperty> rows = lockedPasswords();
            UserProperty property;
            if (rows.isEmpty()) {
                property = new UserProperty();
                property.setName(UserProperty.MCEDT_ACCOUNT_PASSWORD);
                property.setValue(encrypted);
                entityManager.persist(property);
            } else {
                property = rows.get(0);
                property.setValue(encrypted);
            }
            entityManager.flush();
        } catch (Exception failure) {
            // Do not retain a cause: persistence/crypto diagnostics can contain a credential.
            throw new IllegalStateException("Unable to securely save the MCEDT password");
        }
    }

    /**
     * Migrate legacy plaintext rows on first use, in the same locked transaction.
     * A concurrent password change cannot be overwritten by a stale migration.
     * The configured fallback retains the existing missing/blank-row behavior.
     */
    public String getPassword(String fallback) {
        try {
            List<UserProperty> rows = lockedPasswords();
            for (UserProperty property : rows) {
                if (!EncryptionUtils.isEncrypted(property.getValue())) {
                    property.setValue(EncryptionUtils.encrypt(property.getValue()));
                }
            }
            entityManager.flush();
            String stored = rows.isEmpty() ? null : rows.get(0).getValue();
            String password = EncryptionUtils.decrypt(stored);
            return password == null || password.trim().isEmpty() ? fallback : password;
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to securely read the MCEDT password");
        }
    }
}
