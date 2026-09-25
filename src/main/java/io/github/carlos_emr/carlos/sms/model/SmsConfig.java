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
package io.github.carlos_emr.carlos.sms.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The clinic's SMS settings, saved from Administration &gt; SMS ({@code sms_config}, V1.0.34).
 * <p>
 * One row is used (the lowest id); when there is none, the {@code sms.*} properties still apply. The
 * webhook secret and every provider credential value are encrypted at rest with {@link EncryptionUtils}
 * (key {@code encryption.util.secret.key}, kept outside the database), the same way {@code FaxConfig}
 * stores its passwords. Getters decrypt; nothing here is ever logged or rendered, and the admin page
 * only learns whether a secret is set.
 *
 * @since 2026-09-24
 */
@Entity
@Table(name = "sms_config")
public class SmsConfig extends AbstractModel<Integer> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<TreeMap<String, String>> CREDENTIAL_MAP = new TypeReference<>() {
    };

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 16)
    private SmsProviderType providerType = SmsProviderType.STUB;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "scheduler_enabled", nullable = false)
    private boolean schedulerEnabled;

    @Column(name = "sender_number", length = 32)
    private String senderNumber;

    /** Encrypted ({@code {ENC}...}); see {@link #getWebhookSecret()}. */
    @Column(name = "webhook_secret", length = 512)
    private String webhookSecret;

    /** JSON object of credential field name to encrypted value. */
    @Column(name = "credentials", columnDefinition = "TEXT")
    private String credentialsJson;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Date updatedAt;

    @Column(name = "updated_by", length = 16)
    private String updatedBy;

    @Override
    public Integer getId() {
        return id;
    }

    public SmsProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(SmsProviderType providerType) {
        this.providerType = providerType == null ? SmsProviderType.STUB : providerType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public void setSchedulerEnabled(boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }

    /** @return the E.164 sender number, or {@code null} when none is set */
    public String getSenderNumber() {
        return senderNumber;
    }

    public void setSenderNumber(String senderNumber) {
        this.senderNumber = isBlank(senderNumber) ? null : senderNumber;
    }

    /** @return the decrypted webhook secret, or empty when none is stored */
    public String getWebhookSecret() {
        return decrypt(webhookSecret);
    }

    /** Stores the secret encrypted; a blank value removes it. */
    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = isBlank(webhookSecret) ? null : encrypt(webhookSecret);
    }

    public boolean hasWebhookSecret() {
        return !isBlank(webhookSecret);
    }

    /** @return the decrypted credential value, or empty when none is stored */
    public String getCredential(String name) {
        return decrypt(credentials().get(name));
    }

    /** Stores the value encrypted under {@code name}; a blank value removes the credential. */
    public void setCredential(String name, String value) {
        Map<String, String> credentials = credentials();
        if (isBlank(value)) {
            credentials.remove(name);
        } else {
            credentials.put(name, encrypt(value));
        }
        writeCredentials(credentials);
    }

    public boolean hasCredential(String name) {
        return credentials().containsKey(name);
    }

    /** @return the names of the stored credentials (not their values) */
    public Set<String> credentialNames() {
        return Set.copyOf(credentials().keySet());
    }

    public Date getUpdatedAt() {
        return updatedAt == null ? null : new Date(updatedAt.getTime());
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    /** Records who saved the settings and when. */
    public void markUpdated(String providerNo) {
        this.updatedAt = new Date();
        this.updatedBy = providerNo;
    }

    private TreeMap<String, String> credentials() {
        if (isBlank(credentialsJson)) {
            return new TreeMap<>();
        }
        try {
            return JSON.readValue(credentialsJson, CREDENTIAL_MAP);
        } catch (JsonProcessingException e) {
            // Deliberately generic: the stored JSON holds encrypted credentials.
            throw new IllegalStateException(
                    "Stored SMS credentials are unreadable; re-enter them in Administration > SMS.");
        }
    }

    private void writeCredentials(Map<String, String> credentials) {
        try {
            credentialsJson = credentials.isEmpty() ? null : JSON.writeValueAsString(credentials);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("SMS credentials could not be stored.");
        }
    }

    private static String encrypt(String plainText) {
        try {
            return EncryptionUtils.encrypt(plainText);
        } catch (Exception e) {
            // No cause or value in the message: it would carry the secret into logs.
            throw new IllegalStateException("SMS secret could not be encrypted; check encryption.util.secret.key.");
        }
    }

    private static String decrypt(String stored) {
        if (isBlank(stored)) {
            return "";
        }
        try {
            return EncryptionUtils.decrypt(stored);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "SMS secret could not be decrypted (key changed?); re-enter it in Administration > SMS.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
