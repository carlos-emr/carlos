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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.AbstractModel;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Version;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The clinic's SMS settings, saved from Administration &gt; SMS ({@code sms_config}, V1.0.34).
 * <p>
 * There is at most one row, {@link #SINGLETON_ID}; when there is none, the {@code sms.*} properties still
 * apply. A save that races another fails instead of adding a second row or overwriting the other: the
 * database refuses it (on MariaDB, "Record has changed since last read"), or a second first save hits
 * the fixed id, or a second update fails the {@link Version} check. The
 * webhook secret and every provider credential value are encrypted at rest with {@link EncryptionUtils}
 * (key {@code encryption.util.secret.key}, kept outside the database), the same way {@code FaxConfig}
 * stores its passwords. Getters decrypt; nothing here is ever logged or rendered, and the admin page
 * only learns whether a secret is set.
 * <p>
 * Credentials are kept per provider, so choosing another provider never removes the ones already
 * entered for the first: switching back finds them again.
 *
 * @since 2026-09-24
 */
@Entity
@Table(name = "sms_config")
public class SmsConfig extends AbstractModel<Integer> {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** The only row's id; V1.0.34 refuses any other. */
    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id = SINGLETON_ID;

    /** Null until first saved, which tells Hibernate a new row from a stored one. */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

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

    /**
     * JSON object of provider name to that provider's credentials, each a field name to encrypted value:
     * {@code {"VOIPMS": {"api_username": "{ENC}..."}}}. Rows saved before credentials were kept per
     * provider hold one flat object of field name to encrypted value; see {@code credentials()}.
     */
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

    /**
     * Chooses the provider. Credentials stored in the earlier flat shape belong to the provider the row
     * was saved with, so they are first grouped under that provider; otherwise the next provider would
     * appear to own them.
     */
    public void setProviderType(SmsProviderType providerType) {
        SmsProviderType chosen = providerType == null ? SmsProviderType.STUB : providerType;
        if (chosen != this.providerType && storesFlatCredentials()) {
            writeCredentials(credentials());
        }
        this.providerType = chosen;
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

    /** @return the provider's decrypted credential value, or empty when none is stored */
    public String getCredential(SmsProviderType provider, String name) {
        return decrypt(credentials().getOrDefault(provider.name(), new TreeMap<>()).get(name));
    }

    /**
     * Stores the value encrypted under {@code name} for the provider; a blank value removes that one
     * credential. Other providers' credentials are left as they are, and a provider left with none is
     * dropped.
     */
    public void setCredential(SmsProviderType provider, String name, String value) {
        // Unreadable stored credentials are replaced rather than blocking the save that repairs them.
        TreeMap<String, TreeMap<String, String>> credentials = readableCredentials();
        if (isBlank(value)) {
            TreeMap<String, String> providerCredentials = credentials.get(provider.name());
            if (providerCredentials == null || providerCredentials.remove(name) == null) {
                return;
            }
            if (providerCredentials.isEmpty()) {
                credentials.remove(provider.name());
            }
        } else {
            credentials.computeIfAbsent(provider.name(), unused -> new TreeMap<>()).put(name, encrypt(value));
        }
        writeCredentials(credentials);
    }

    /**
     * @return whether {@code name} is stored for the provider; {@code false} when the stored credentials
     *         cannot be read
     */
    public boolean hasCredential(SmsProviderType provider, String name) {
        TreeMap<String, String> providerCredentials = readableCredentials().get(provider.name());
        return providerCredentials != null && providerCredentials.containsKey(name);
    }

    /**
     * @return the names of the provider's stored credentials (not their values); empty when the stored
     *         credentials cannot be read, so the settings page can still be opened and saved to replace them
     */
    public Set<String> credentialNames(SmsProviderType provider) {
        TreeMap<String, String> providerCredentials = readableCredentials().get(provider.name());
        return providerCredentials == null ? Set.of() : Set.copyOf(providerCredentials.keySet());
    }

    /**
     * Removes every credential stored for the provider; other providers' credentials are left as they
     * are. Does nothing when the provider has none, or when the stored credentials cannot be read.
     */
    public void removeCredentials(SmsProviderType provider) {
        TreeMap<String, TreeMap<String, String>> credentials = readableCredentials();
        if (credentials.remove(provider.name()) != null) {
            writeCredentials(credentials);
        }
    }

    /** @return {@code false} when credentials are stored but cannot be read and must be entered again */
    public boolean credentialsReadable() {
        try {
            credentials();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** @return the encrypted webhook secret as stored, for comparing two saves; never the secret itself */
    public String storedWebhookSecret() {
        return webhookSecret;
    }

    /** @return the encrypted credentials as stored, for comparing two saves; never a credential itself */
    public String storedCredentials() {
        return credentialsJson;
    }

    /**
     * @return each provider's encrypted credentials as stored (provider name to field name to encrypted
     *         value), for telling which provider's credentials a save changed; never a credential itself.
     *         Empty when the stored credentials cannot be read.
     */
    public Map<String, Map<String, String>> storedCredentialsByProvider() {
        Map<String, Map<String, String>> byProvider = new LinkedHashMap<>();
        readableCredentials().forEach((provider, values) ->
                byProvider.put(provider, Collections.unmodifiableMap(values)));
        return Collections.unmodifiableMap(byProvider);
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

    /** Redacted: the inherited reflection toString would print the encrypted secret and credentials. */
    @Override
    public String toString() {
        return "SmsConfig[redacted]";
    }

    private TreeMap<String, TreeMap<String, String>> readableCredentials() {
        try {
            return credentials();
        } catch (IllegalStateException e) {
            return new TreeMap<>();
        }
    }

    /**
     * Reads the stored credentials as provider name to field name to encrypted value.
     * <p>
     * Two shapes are read. The current one groups by provider: every value of the outer object is an
     * object of strings. The earlier flat shape, written before credentials were kept per provider, is
     * one object whose every value is a string; it belongs to the provider the row names, and the next
     * write stores it grouped. Anything else (not JSON, not an object, a mix of the two shapes, or a
     * value that is neither) is unreadable.
     *
     * @throws IllegalStateException when the stored credentials cannot be read
     */
    private TreeMap<String, TreeMap<String, String>> credentials() {
        JsonNode stored = storedCredentialTree();
        TreeMap<String, TreeMap<String, String>> byProvider = new TreeMap<>();
        if (stored == null) {
            return byProvider;
        }
        if (isFlat(stored)) {
            byProvider.put(providerType.name(), stringValues(stored));
            return byProvider;
        }
        for (Map.Entry<String, JsonNode> provider : stored.properties()) {
            TreeMap<String, String> values = stringValues(provider.getValue());
            if (!values.isEmpty()) {
                byProvider.put(provider.getKey(), values);
            }
        }
        return byProvider;
    }

    /** @return whether the stored credentials are readable and still in the earlier flat shape */
    private boolean storesFlatCredentials() {
        try {
            JsonNode stored = storedCredentialTree();
            return stored != null && isFlat(stored);
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** @return the stored credentials as a JSON object, or {@code null} when none are stored */
    private JsonNode storedCredentialTree() {
        if (isBlank(credentialsJson)) {
            return null;
        }
        JsonNode stored;
        try {
            stored = JSON.readTree(credentialsJson);
        } catch (JsonProcessingException e) {
            throw unreadableCredentials();
        }
        if (stored == null || !stored.isObject()) {
            throw unreadableCredentials();
        }
        return stored;
    }

    /** The earlier flat shape: a non-empty object whose every value is a string (an encrypted credential). */
    private static boolean isFlat(JsonNode stored) {
        if (stored.isEmpty()) {
            return false;
        }
        for (JsonNode value : stored) {
            if (!value.isTextual()) {
                return false;
            }
        }
        return true;
    }

    private static TreeMap<String, String> stringValues(JsonNode node) {
        if (!node.isObject()) {
            throw unreadableCredentials();
        }
        TreeMap<String, String> values = new TreeMap<>();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if (!field.getValue().isTextual()) {
                throw unreadableCredentials();
            }
            values.put(field.getKey(), field.getValue().textValue());
        }
        return values;
    }

    private static IllegalStateException unreadableCredentials() {
        // Deliberately generic: the stored JSON holds encrypted credentials.
        return new IllegalStateException(
                "Stored SMS credentials are unreadable; re-enter them in Administration > SMS.");
    }

    private void writeCredentials(Map<String, TreeMap<String, String>> credentials) {
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
