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

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The clinic's SMS settings, saved from Administration &gt; SMS ({@code sms_config}, V1.0.78).
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
 * Every provider keeps its own login and sender number, so a clinic that goes back to a provider finds them
 * as it left them. The {@code credentials} column holds one entry per provider, keyed by its
 * {@link SmsProviderType} name: each credential field name maps to its encrypted value, and
 * {@link #SENDER_NUMBER_KEY} to that provider's sender number (E.164, not encrypted, as in the
 * {@code sender_number} column). For example
 * {@code {"VOIPMS": {"api_user": "{ENC}...", "@senderNumber": "+16135550100"}}}. The {@code sender_number}
 * column always holds the selected provider's number. A row saved before entries existed holds one flat map of
 * field name to encrypted value; it is read as the selected provider's entry, and the next write stores entries.
 *
 * @since 2026-09-24
 */
@Entity
@Table(name = "sms_config")
public class SmsConfig extends AbstractModel<Integer> {
    /**
     * The entry key of a provider's sender number. A credential field name starts with a letter
     * ({@code SmsCredentialField}), so it can never be this.
     */
    public static final String SENDER_NUMBER_KEY = "@senderNumber";

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The only row's id; V1.0.78 refuses any other. */
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

    /** The selected provider's sender number; its entry in {@link #credentialsJson} holds the same number. */
    @Column(name = "sender_number", length = 32)
    private String senderNumber;

    /** Encrypted ({@code {ENC}...}); see {@link #getWebhookSecret()}. */
    @Column(name = "webhook_secret", length = 512)
    private String webhookSecret;

    /** JSON object of provider name to that provider's entry; see the class description. */
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

    /**
     * @return the row's version, which each save raises; null until first saved. The settings page sends
     *         back the version it showed, so a save made from an out-of-date page can be refused.
     */
    public Integer getVersion() {
        return version;
    }

    public SmsProviderType getProviderType() {
        return providerType;
    }

    /**
     * Selects the provider. Choosing another keeps the one being left as it is: its credentials and its sender
     * number stay stored in its entry, ready for when it is chosen again. The {@code sender_number} column is not
     * changed here; the caller sets the new provider's number.
     */
    public void setProviderType(SmsProviderType providerType) {
        SmsProviderType selected = providerType == null ? SmsProviderType.STUB : providerType;
        if (selected != this.providerType && credentialsReadable()) {
            // Also stores a row saved before entries existed in the entry format, under the provider it belongs to.
            TreeMap<String, TreeMap<String, String>> entries = entries();
            putOrRemove(entries.computeIfAbsent(this.providerType.name(), name -> new TreeMap<>()),
                    SENDER_NUMBER_KEY, senderNumber);
            writeEntries(entries);
        }
        this.providerType = selected;
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

    /** @return the selected provider's E.164 sender number, or {@code null} when none is set */
    public String getSenderNumber() {
        return senderNumber;
    }

    /**
     * Sets the selected provider's sender number, in the {@code sender_number} column and in its entry. While the
     * stored credentials cannot be read, only the column is written; the save that repairs them writes the entry.
     */
    public void setSenderNumber(String senderNumber) {
        this.senderNumber = isBlank(senderNumber) ? null : senderNumber;
        if (credentialsReadable()) {
            TreeMap<String, TreeMap<String, String>> entries = entries();
            putOrRemove(entries.computeIfAbsent(providerType.name(), name -> new TreeMap<>()),
                    SENDER_NUMBER_KEY, this.senderNumber);
            writeEntries(entries);
        }
    }

    /**
     * @param provider a provider
     * @return the sender number stored for it: the {@code sender_number} column for the selected provider, its
     *         entry's number for any other; empty when none is stored or the stored credentials cannot be read
     */
    public Optional<String> storedSenderNumber(SmsProviderType provider) {
        if (provider == providerType) {
            return Optional.ofNullable(senderNumber);
        }
        return Optional.ofNullable(readableEntries().getOrDefault(provider.name(), new TreeMap<>())
                .get(SENDER_NUMBER_KEY)).filter(number -> !number.isBlank());
    }

    /**
     * @return each provider's stored sender number by provider name, the selected one's from the
     *         {@code sender_number} column; only the column's when the stored credentials cannot be read. For
     *         comparing two saves.
     */
    public Map<String, String> storedSenderNumbers() {
        Map<String, String> numbers = new TreeMap<>();
        readableEntries().forEach((provider, entry) -> {
            String number = entry.get(SENDER_NUMBER_KEY);
            if (!isBlank(number)) {
                numbers.put(provider, number);
            }
        });
        numbers.remove(providerType.name());
        if (senderNumber != null) {
            numbers.put(providerType.name(), senderNumber);
        }
        return numbers;
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

    /** @return the selected provider's decrypted credential value, or empty when none is stored */
    public String getCredential(String name) {
        return getCredential(providerType, name);
    }

    /** @return the provider's decrypted credential value, or empty when none is stored */
    public String getCredential(SmsProviderType provider, String name) {
        return isCredentialName(name) ? decrypt(entry(provider).get(name)) : "";
    }

    /** Stores the value encrypted under {@code name} for the selected provider; a blank value removes it. */
    public void setCredential(String name, String value) {
        setCredential(providerType, name, value);
    }

    /**
     * Stores the value encrypted under {@code name} in the provider's entry; a blank value removes it. Other
     * providers' entries are left as they are.
     *
     * @throws IllegalArgumentException when {@code name} is the reserved {@link #SENDER_NUMBER_KEY}
     */
    public void setCredential(SmsProviderType provider, String name, String value) {
        if (!isCredentialName(name)) {
            throw new IllegalArgumentException("not an SMS credential field name");
        }
        // Unreadable stored credentials are replaced rather than blocking the save that repairs them.
        TreeMap<String, TreeMap<String, String>> entries = readableEntries();
        TreeMap<String, String> entry = entries.computeIfAbsent(provider.name(), key -> new TreeMap<>());
        if (isBlank(value)) {
            entry.remove(name);
        } else {
            entry.put(name, encrypt(value));
        }
        writeEntries(entries);
    }

    /** Whether the selected provider has a value stored under {@code name} that decrypts with the current key. */
    public boolean hasReadableCredential(String name) {
        return hasReadableCredential(providerType, name);
    }

    /**
     * @return whether a value is stored under {@code name} for the provider and decrypts with the current key;
     *         {@code false} when none is stored or it cannot be read, for example after the encryption key changed
     */
    public boolean hasReadableCredential(SmsProviderType provider, String name) {
        if (!hasCredential(provider, name)) {
            return false;
        }
        try {
            getCredential(provider, name);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * Removes every provider's stored credentials and the sender numbers kept in their entries, readable or not;
     * used to repair stored credentials that cannot be read. The {@code sender_number} column is kept.
     */
    public void clearCredentials() {
        credentialsJson = null;
    }

    /** Whether the selected provider has {@code name} stored; see {@link #hasCredential(SmsProviderType, String)}. */
    public boolean hasCredential(String name) {
        return hasCredential(providerType, name);
    }

    /** @return whether {@code name} is stored for the provider; {@code false} when the credentials cannot be read */
    public boolean hasCredential(SmsProviderType provider, String name) {
        return isCredentialName(name) && readableEntries().getOrDefault(provider.name(), new TreeMap<>())
                .containsKey(name);
    }

    /** The names of the selected provider's stored credentials; see {@link #credentialNames(SmsProviderType)}. */
    public Set<String> credentialNames() {
        return credentialNames(providerType);
    }

    /**
     * @return the names of the provider's stored credentials (not their values); empty when the stored
     *         credentials cannot be read, so the settings page can still be opened and saved to replace them
     */
    public Set<String> credentialNames(SmsProviderType provider) {
        TreeMap<String, String> entry = readableEntries().getOrDefault(provider.name(), new TreeMap<>());
        entry.remove(SENDER_NUMBER_KEY);
        return Set.copyOf(entry.keySet());
    }

    /** @return {@code false} when credentials are stored but cannot be read and must be entered again */
    public boolean credentialsReadable() {
        try {
            entries();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** @return the encrypted webhook secret as stored, for comparing two saves; never the secret itself */
    public String storedWebhookSecret() {
        return webhookSecret;
    }

    /** @return the credentials column as stored, for comparing two saves; never a credential itself */
    public String storedCredentials() {
        return credentialsJson;
    }

    /**
     * @return each provider's encrypted credentials as stored, by provider name, without sender numbers; for
     *         comparing two saves, never a credential itself
     * @throws IllegalStateException when the stored credentials cannot be read
     */
    public Map<String, String> storedCredentialsByProvider() {
        Map<String, String> stored = new TreeMap<>();
        entries().forEach((provider, entry) -> {
            TreeMap<String, String> credentials = new TreeMap<>(entry);
            credentials.remove(SENDER_NUMBER_KEY);
            if (!credentials.isEmpty()) {
                stored.put(provider, credentials.toString());
            }
        });
        return stored;
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

    private TreeMap<String, String> entry(SmsProviderType provider) {
        return entries().getOrDefault(provider.name(), new TreeMap<>());
    }

    private TreeMap<String, TreeMap<String, String>> readableEntries() {
        try {
            return entries();
        } catch (IllegalStateException e) {
            return new TreeMap<>();
        }
    }

    /**
     * Reads the stored entries. A JSON object whose values are all objects of text holds entries; one whose values
     * are all text is the flat map saved before entries existed, the selected provider's. Anything else, like
     * JSON that does not parse, cannot be read, and the credentials must be entered again.
     */
    private TreeMap<String, TreeMap<String, String>> entries() {
        TreeMap<String, TreeMap<String, String>> entries = new TreeMap<>();
        if (isBlank(credentialsJson)) {
            return entries;
        }
        JsonNode root;
        try {
            root = JSON.readTree(credentialsJson);
        } catch (JsonProcessingException e) {
            throw unreadable();
        }
        if (root == null || !root.isObject()) {
            throw unreadable();
        }
        boolean flat = false;
        boolean nested = false;
        for (Map.Entry<String, JsonNode> field : root.properties()) {
            if (field.getValue().isTextual()) {
                flat = true;
                entries.computeIfAbsent(providerType.name(), name -> new TreeMap<>())
                        .put(field.getKey(), field.getValue().textValue());
            } else if (field.getValue().isObject()) {
                nested = true;
                entries.put(field.getKey(), textEntry(field.getValue()));
            } else {
                throw unreadable();
            }
        }
        if (flat && nested) {
            throw unreadable();
        }
        return entries;
    }

    private static TreeMap<String, String> textEntry(JsonNode node) {
        TreeMap<String, String> entry = new TreeMap<>();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if (!field.getValue().isTextual()) {
                throw unreadable();
            }
            entry.put(field.getKey(), field.getValue().textValue());
        }
        return entry;
    }

    private void writeEntries(TreeMap<String, TreeMap<String, String>> entries) {
        entries.values().removeIf(Map::isEmpty);
        try {
            credentialsJson = entries.isEmpty() ? null : JSON.writeValueAsString(entries);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("SMS credentials could not be stored.");
        }
    }

    private static void putOrRemove(TreeMap<String, String> entry, String key, String value) {
        if (isBlank(value)) {
            entry.remove(key);
        } else {
            entry.put(key, value);
        }
    }

    private static boolean isCredentialName(String name) {
        return name != null && !SENDER_NUMBER_KEY.equals(name);
    }

    private static IllegalStateException unreadable() {
        // Deliberately generic: the stored JSON holds encrypted credentials.
        return new IllegalStateException("Stored SMS credentials are unreadable; re-enter them in Administration > SMS.");
    }

    private static String encrypt(String plainText) {
        try {
            return EncryptionUtils.encrypt(plainText);
        } catch (Exception e) {
            // No cause or value in the message: it would carry the secret into logs.
            throw new SmsSecretEncryptionException();
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
