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
package io.github.carlos_emr.carlos.email.core;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;

/**
 * At-rest protection for the transport secrets held in {@code emailConfig.configDetails}.
 *
 * <p>SMTP passwords and provider API keys are stored inside the {@code configDetails} JSON blob.
 * Historically they lived there in plaintext, so any database-level read (backup, SQL injection
 * elsewhere, DB operator access) yielded the clinic's outbound mail credentials. This utility
 * encrypts those secret fields at rest and decrypts them only when a sender needs them.</p>
 *
 * <p>Encryption reuses {@link EncryptionUtils} (AES-256-GCM, key sourced from
 * {@code encryption.util.secret.key} outside the database). The {@code {ENC}} prefix that
 * {@link EncryptionUtils} writes is the versioned format marker: values carrying it are already
 * encrypted, values without it are legacy plaintext. Because {@link EncryptionUtils#decrypt(String)}
 * passes non-prefixed values through unchanged, senders tolerate a mixed plaintext/encrypted
 * population during the migration window.</p>
 *
 * <p><strong>Security:</strong> callers must never log or surface the plaintext secret, nor the
 * raw {@code configDetails} JSON. The exception messages here are deliberately generic and never
 * echo the credential or JSON.</p>
 *
 * @see EncryptionUtils
 * @since 2026-07-03
 */
public final class EmailConfigSecrets {

    /** {@code configDetails} JSON keys that hold transport secrets and must be encrypted at rest. */
    private static final List<String> SECRET_FIELDS = List.of("password", "api_key");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private EmailConfigSecrets() {
    }

    /**
     * Returns the given {@code configDetails} JSON with every known secret field encrypted at rest.
     *
     * <p>The operation is idempotent: fields that already carry the {@code {ENC}} marker are left
     * untouched, so re-running it over an already-migrated (or partially-migrated) row is a no-op
     * for those fields. When nothing needs encrypting — the input is blank, is not a JSON object,
     * carries no secret field, or all secrets are already encrypted — the original string is
     * returned unchanged (same reference), which lets callers cheaply skip a needless database
     * write.</p>
     *
     * @param configDetailsJson the raw {@code configDetails} value, may be null/blank
     * @return the JSON with secret fields encrypted, or the original value when no change is needed
     * @throws EmailSendingException if the encryption key is unavailable or encryption fails; the
     *                               message never contains the secret or the JSON
     */
    public static String encryptSecrets(String configDetailsJson) throws EmailSendingException {
        if (configDetailsJson == null || configDetailsJson.isBlank()) {
            return configDetailsJson;
        }

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(configDetailsJson);
        } catch (Exception e) {
            // Not parseable as JSON: leave the stored value untouched rather than risk corrupting it.
            return configDetailsJson;
        }
        if (root == null || !root.isObject()) {
            return configDetailsJson;
        }

        ObjectNode configObject = (ObjectNode) root;
        boolean changed = false;
        for (String field : SECRET_FIELDS) {
            JsonNode value = configObject.get(field);
            if (value == null || !value.isTextual()) {
                continue;
            }
            String plaintext = value.asText();
            // isEncrypted() also treats null/empty as "already handled", so empty secrets are skipped.
            if (EncryptionUtils.isEncrypted(plaintext)) {
                continue;
            }
            try {
                configObject.put(field, EncryptionUtils.encrypt(plaintext));
            } catch (Exception e) {
                throw new EmailSendingException("Unable to encrypt email transport credentials at rest");
            }
            changed = true;
        }

        return changed ? configObject.toString() : configDetailsJson;
    }

    /**
     * Decrypts a single at-rest secret value read from {@code configDetails}.
     *
     * <p>Legacy plaintext values (those without the {@code {ENC}} marker) are returned unchanged so
     * that sends keep working while the population is still being migrated. Null and empty values
     * are returned as-is.</p>
     *
     * @param storedValue the value read from the {@code configDetails} JSON
     * @return the decrypted secret, or the original value when it is plaintext/blank
     * @throws EmailSendingException if a marked-encrypted value cannot be decrypted (missing key,
     *                               rotated key, tampering); the message never contains the value
     */
    public static String decryptSecret(String storedValue) throws EmailSendingException {
        if (storedValue == null || storedValue.isEmpty()) {
            return storedValue;
        }
        try {
            return EncryptionUtils.decrypt(storedValue);
        } catch (Exception e) {
            throw new EmailSendingException("Unable to decrypt email transport credentials");
        }
    }
}
