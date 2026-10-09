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
package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.dao.SmsConfigDao;
import io.github.carlos_emr.carlos.sms.dto.SmsConfigUpdateDto;
import io.github.carlos_emr.carlos.sms.event.SmsConfigChangedEvent;
import io.github.carlos_emr.carlos.sms.model.SmsConfig;
import io.github.carlos_emr.carlos.sms.support.SmsPhoneNumbers;
import io.github.carlos_emr.carlos.sms.validator.SmsConfigValidator;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleStateException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reads and saves the SMS settings from Administration &gt; SMS ({@link SmsConfig}).
 * <p>
 * While nothing is saved, callers fall back to the {@code sms.*} properties: sending stays on, the
 * provider comes from {@code sms.provider.default} and the scheduler from
 * {@code sms.queue.scheduler.enabled}. Once saved, the stored values win.
 * <p>
 * Secrets are write-only: a blank webhook secret or credential keeps what is stored. Only the
 * credential fields the chosen provider declares are kept, and choosing another provider removes every
 * stored credential, so one provider's login is never handed to another that happens to use the same
 * field name. The webhook secret is CARLOS's own and is kept. Saving publishes
 * {@link SmsConfigChangedEvent} so the queue scheduler can start or stop without a restart.
 *
 * @since 2026-09-24
 */
@Service
public class SmsConfigService {
    /** MariaDB ER_DUP_ENTRY: the row already exists. */
    private static final int MARIADB_DUPLICATE_KEY = 1062;
    /** MariaDB ER_CHECKREAD, "Record has changed since last read": snapshot isolation refused the update. */
    private static final int MARIADB_RECORD_CHANGED = 1020;
    /** The standard SQLSTATE for a unique or primary key violation (what H2 reports). */
    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";
    /**
     * How recent the same administrator's save must be for a refused save to count as a second click on Save.
     * A real double-click lands within seconds; the limit also stops the check being used at leisure to test
     * guesses at a stored secret.
     */
    private static final long DOUBLE_CLICK_WINDOW_MILLIS = 60_000L;

    private final SmsConfigDao smsConfigDao;
    private final SmsProviderClientResolver providerClients;
    private final ApplicationEventPublisher eventPublisher;
    private final SmsConfigAuditRecorder auditRecorder;
    private final SmsProviderRetirementService retirementService;

    public SmsConfigService(SmsConfigDao smsConfigDao, SmsProviderClientResolver providerClients,
                            ApplicationEventPublisher eventPublisher, SmsConfigAuditRecorder auditRecorder) {
        this(smsConfigDao, providerClients, eventPublisher, auditRecorder, null);
    }

    @Autowired
    public SmsConfigService(SmsConfigDao smsConfigDao, SmsProviderClientResolver providerClients,
                            ApplicationEventPublisher eventPublisher, SmsConfigAuditRecorder auditRecorder,
                            SmsProviderRetirementService retirementService) {
        this.smsConfigDao = smsConfigDao;
        this.providerClients = providerClients;
        this.eventPublisher = eventPublisher;
        this.auditRecorder = auditRecorder;
        this.retirementService = retirementService;
    }

    boolean retirementTrackingEnabled() {
        return retirementService != null;
    }

    /** @return the saved settings, or empty while nothing has been saved */
    @Transactional(readOnly = true)
    public Optional<SmsConfig> current() {
        return smsConfigDao.findCurrent();
    }

    /** @return whether CARLOS may send SMS: on while nothing is saved, otherwise the saved switch */
    @Transactional(readOnly = true)
    public boolean sendingEnabled() {
        return current().map(SmsConfig::isEnabled).orElse(true);
    }

    @Transactional(readOnly = true)
    public Optional<SmsProviderType> storedProvider() {
        return current().map(SmsConfig::getProviderType);
    }

    @Transactional(readOnly = true)
    public Optional<Boolean> storedSchedulerEnabled() {
        return current().map(SmsConfig::isSchedulerEnabled);
    }

    /**
     * The saved scheduler setting as the database holds it now, read in a transaction of its own.
     * <p>
     * For callers that run after a commit, such as the scheduler's settings listener: there the saving
     * transaction's session is still bound, and {@link #storedSchedulerEnabled()} would answer from it
     * with the value that was just saved, not with a later save by someone else.
     *
     * @return the committed scheduler setting, or empty while nothing has been saved
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Boolean> committedSchedulerEnabled() {
        return smsConfigDao.findCurrent().map(SmsConfig::isSchedulerEnabled);
    }

    /** @return the providers that have an installed client; only these can be chosen */
    public Set<SmsProviderType> installedProviders() {
        return Set.copyOf(providerClients.registeredProviderTypes());
    }

    /** @return the credential fields the provider's client declares; empty when no client is installed */
    public List<SmsCredentialField> credentialFields(SmsProviderType providerType) {
        if (providerType == null || !providerClients.registeredProviderTypes().contains(providerType)) {
            return List.of();
        }
        return List.copyOf(providerClients.resolve(providerType).credentialFields());
    }

    /**
     * What saving {@code update} needs before sending can be switched on: the provider's required credential
     * fields, whether it needs a sender number, and which credentials are already stored for it. Nothing
     * counts as stored when the update chooses another provider, because saving clears the old provider's
     * credentials, or when a stored credential cannot be read.
     *
     * @param update the submitted settings
     * @return the provider's needs, for {@code SmsConfigValidator}
     */
    @Transactional(readOnly = true)
    public SmsConfigValidator.ProviderNeeds providerNeeds(SmsConfigUpdateDto update) {
        SmsProviderType providerType = update.providerType();
        if (providerType == null || !providerClients.registeredProviderTypes().contains(providerType)) {
            return SmsConfigValidator.ProviderNeeds.NONE;
        }
        SmsProviderClient client = providerClients.resolve(providerType);
        Set<String> required = new HashSet<>();
        client.credentialFields().stream().filter(SmsCredentialField::required)
                .forEach(field -> required.add(field.name()));
        Set<String> stored = new HashSet<>();
        current().filter(config -> config.getProviderType() == providerType).ifPresent(config ->
                client.credentialFields().stream().map(SmsCredentialField::name)
                        .filter(config::hasReadableCredential).forEach(stored::add));
        return new SmsConfigValidator.ProviderNeeds(required, client.requiresSenderNumber(), stored);
    }

    /**
     * @param providerType the provider about to be used
     * @return whether it can send now ({@link #readyProviderSettings(SmsProviderType)}), so a text that could
     *         never be sent is refused before it is recorded
     */
    @Transactional(readOnly = true)
    public boolean providerReady(SmsProviderType providerType) {
        try {
            readyProviderSettings(providerType);
            return true;
        } catch (SmsProviderNotReadyException e) {
            return false;
        }
    }

    /**
     * The settings a provider sends with, checked against what it declares it needs. A provider with no
     * installed client is not checked: resolving it fails anyway.
     *
     * @param providerType the provider about to send
     * @return its settings, with every required credential and, if it needs one, the sender number
     * @throws SmsProviderNotReadyException when a stored credential cannot be read, or a required credential or
     *                                      the sender number is missing, as while only {@code sms.provider.default}
     *                                      names a provider that needs credentials
     */
    @Transactional(readOnly = true)
    public SmsProviderSettings readyProviderSettings(SmsProviderType providerType) {
        SmsProviderSettings settings = providerSettings(providerType);
        if (!providerClients.registeredProviderTypes().contains(providerType)) {
            return settings;
        }
        SmsProviderClient client = providerClients.resolve(providerType);
        boolean credentialMissing = client.credentialFields().stream()
                .anyMatch(field -> field.required() && settings.credential(field.name()).isEmpty());
        if (credentialMissing) {
            throw new SmsProviderNotReadyException("a required SMS provider credential is not saved");
        }
        if (client.requiresSenderNumber() && settings.senderNumber().isEmpty()) {
            throw new SmsProviderNotReadyException("the SMS sender number the provider needs is not saved");
        }
        return settings;
    }

    /**
     * The settings a provider gets when it sends or looks up a text: the saved sender number and its declared
     * credentials while it is the saved provider, otherwise none. While nothing is saved there is nothing to
     * hand over, so every provider gets none.
     *
     * @param providerType the provider about to send or look up a text
     * @return its settings
     * @throws SmsProviderNotReadyException when a stored credential cannot be read (for example after the
     *                                      encryption key changed); the administrator must enter it again
     */
    @Transactional(readOnly = true)
    public SmsProviderSettings providerSettings(SmsProviderType providerType) {
        Optional<SmsConfig> stored = current().filter(config -> config.getProviderType() == providerType);
        if (stored.isEmpty()) {
            return SmsProviderSettings.none(providerType);
        }
        SmsConfig config = stored.get();
        List<SmsCredentialField> fields = credentialFields(providerType);
        if (!fields.isEmpty() && !config.credentialsReadable()) {
            throw new SmsProviderNotReadyException("the stored SMS provider credentials cannot be parsed");
        }
        Map<String, String> credentials = new HashMap<>();
        for (SmsCredentialField field : fields) {
            if (config.hasCredential(field.name())) {
                try {
                    credentials.put(field.name(), config.getCredential(field.name()));
                } catch (IllegalStateException e) {
                    throw new SmsProviderNotReadyException("a stored SMS provider credential cannot be decrypted", e);
                }
            }
        }
        return SmsProviderSettings.of(providerType, config.getSenderNumber(), credentials);
    }

    /**
     * Reloads status-lookup settings for each message. A retired account's outcome is unknown: a new
     * account cannot establish that it never received the old send, even after a switch away and back.
     * Empty therefore requires manual reconciliation, without making an external lookup.
     */
    @Transactional
    public Optional<SmsProviderSettings> statusLookupSettings(
            SmsTransaction transaction) {
        if (retirementService != null) {
            retirementService.lockSelection();
            if (retirementService.retired(transaction)) {
                return Optional.empty();
            }
        }
        return Optional.of(providerSettings(transaction.getProviderType()));
    }

    /** A coherent final dispatch decision; its transaction commits before the external provider call. */
    @Transactional
    public Optional<SmsProviderSettings> dispatchSettings(
            SmsTransaction transaction) {
        if (retirementService == null) {
            return sendingEnabled() ? Optional.of(readyProviderSettings(transaction.getProviderType()))
                    : Optional.empty();
        }
        Optional<SmsConfig> selected = retirementService.lockSelection();
        if (!retirementService.admissionAllowed(selected, transaction.getProviderType(),
                SmsMessagePurpose.PATIENT_MESSAGE)
                || retirementService.retired(transaction)) {
            return Optional.empty();
        }
        return Optional.of(readyProviderSettings(transaction.getProviderType()));
    }

    /**
     * Saves validated settings (see {@code SmsConfigValidator}), creating the row the first time.
     * <p>
     * The save is refused unless the stored version is the one the page showed
     * ({@link SmsConfigUpdateDto#expectedVersion()}). Without that check a page left open in another tab
     * would silently put back every setting it showed, such as turning sending on again after another
     * administrator turned it off.
     *
     * @param update              the submitted settings
     * @param updatedByProviderNo the admin saving them, recorded on the row
     * @return the saved settings
     * @throws SmsConfigConflictException when another save got there first, after the page was loaded or
     *                                    racing this one; nothing from this one is stored
     */
    @Transactional
    public SmsConfig save(SmsConfigUpdateDto update, String updatedByProviderNo) {
        Optional<SmsConfig> existing = retirementService == null ? smsConfigDao.findCurrent()
                : retirementService.lockSelection();
        if (!Objects.equals(existing.map(SmsConfig::getVersion).orElse(null), update.expectedVersion())) {
            throw new SmsConfigConflictException();
        }
        SmsConfig config = existing.orElseGet(SmsConfig::new);
        Snapshot before = Snapshot.of(config, existing.isPresent());
        if (retirementService != null) {
            retirementService.recordSelection(config.getProviderType(), update.providerType(), existing.isEmpty());
        }
        if (existing.isPresent() && config.getProviderType() != update.providerType()) {
            config.clearCredentials();
        }
        config.setProviderType(update.providerType());
        config.setEnabled(update.enabled());
        config.setSchedulerEnabled(update.schedulerEnabled());
        config.setSenderNumber(SmsPhoneNumbers.normalizeToE164(update.senderNumber()).orElse(null));
        if (update.clearWebhookSecret()) {
            config.setWebhookSecret(null);
        } else if (!isBlank(update.webhookSecret())) {
            config.setWebhookSecret(update.webhookSecret());
        }
        applyCredentials(config, update);
        config.markUpdated(updatedByProviderNo);

        if (existing.isPresent()) {
            smsConfigDao.merge(config);
        } else {
            smsConfigDao.persist(config);
        }
        // Flushed first, so a save that lost a race is neither audited nor announced.
        flushOrReportConflict();
        auditRecorder.recordSaved(config, updatedByProviderNo, before.changedFields(Snapshot.of(config, true)));
        eventPublisher.publishEvent(new SmsConfigChangedEvent(config.isSchedulerEnabled()));
        return config;
    }

    /**
     * Whether the stored settings are already exactly what {@code update} asks for, last saved by the same
     * administrator within the last minute: the case of a Save button clicked twice, where the second request
     * finds the first one's save. The settings page then reports the save as done instead of blaming another
     * administrator. Typed secrets are compared with the stored ones; a blank secret field asks to keep what
     * is stored, so it always matches.
     *
     * @param update     the submitted settings that met a conflict
     * @param providerNo the administrator who submitted them
     * @return {@code true} when nothing would change and the last save was by {@code providerNo}, moments ago
     */
    @Transactional(readOnly = true)
    public boolean alreadySaved(SmsConfigUpdateDto update, String providerNo) {
        Optional<SmsConfig> stored = smsConfigDao.findCurrent();
        if (stored.isEmpty() || providerNo == null || !providerNo.equals(stored.get().getUpdatedBy())) {
            return false;
        }
        SmsConfig config = stored.get();
        Date updatedAt = config.getUpdatedAt();
        // Either direction: a time in the future (another server's clock, or the hour repeated when clocks go
        // back, since updated_at is local time) is not "moments ago" either.
        if (updatedAt == null
                || Math.abs(System.currentTimeMillis() - updatedAt.getTime()) > DOUBLE_CLICK_WINDOW_MILLIS) {
            return false;
        }
        try {
            return config.getProviderType() == update.providerType()
                    && config.isEnabled() == update.enabled()
                    && config.isSchedulerEnabled() == update.schedulerEnabled()
                    && Objects.equals(config.getSenderNumber(),
                            SmsPhoneNumbers.normalizeToE164(update.senderNumber()).orElse(null))
                    && webhookSecretMatches(config, update)
                    && credentialsMatch(config, update);
        } catch (IllegalStateException unreadable) {
            // A stored secret that cannot be decrypted cannot be shown to match.
            return false;
        }
    }

    private static boolean webhookSecretMatches(SmsConfig config, SmsConfigUpdateDto update) {
        if (update.clearWebhookSecret()) {
            return !config.hasWebhookSecret();
        }
        return isBlank(update.webhookSecret()) || sameSecret(update.webhookSecret(), config.getWebhookSecret());
    }

    private boolean credentialsMatch(SmsConfig config, SmsConfigUpdateDto update) {
        Set<String> declared = declaredNames(update.providerType());
        if (!config.credentialsReadable() || !declared.containsAll(config.credentialNames())) {
            return false;
        }
        for (String field : declared) {
            String value = update.credentials().get(field);
            if (!isBlank(value) && !sameSecret(value, config.getCredential(field))) {
                return false;
            }
        }
        return true;
    }

    /** Compares in constant time, so the time taken says nothing about how much of a guess was right. */
    private static boolean sameSecret(String typed, String stored) {
        return MessageDigest.isEqual(typed.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes the row now, inside this transaction, so a save that raced another administrator's fails
     * here. On MariaDB with snapshot isolation (the default since 11.6) both races fail with error 1020,
     * "Record has changed since last read". Where that is off, and on H2, a second first save hits the
     * fixed id and a second update fails the version check. The exception rolls the transaction back. Any other database failure passes through unchanged, so a
     * real defect is never reported as "someone else saved".
     */
    private void flushOrReportConflict() {
        try {
            smsConfigDao.flush();
        } catch (RuntimeException e) {
            if (isSaveConflict(e)) {
                throw new SmsConfigConflictException(e);
            }
            throw e;
        }
    }

    /**
     * Whether the failure, or anything that caused it, says another save got there first: a failed
     * version check, a duplicate key, or MariaDB refusing to change a row that another transaction
     * changed after this one read it. A constraint failure of any other kind (a missing value, a failed
     * check) is not a conflict.
     */
    private static boolean isSaveConflict(Throwable failure) {
        Set<Throwable> seen = new HashSet<>();
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException || cause instanceof StaleStateException
                    || cause instanceof EntityExistsException || cause instanceof OptimisticLockingFailureException
                    || cause instanceof DuplicateKeyException) {
                return true;
            }
            if (cause instanceof SQLException sql && (sql.getErrorCode() == MARIADB_DUPLICATE_KEY
                    || sql.getErrorCode() == MARIADB_RECORD_CHANGED
                    || SQLSTATE_UNIQUE_VIOLATION.equals(sql.getSQLState()))) {
                return true;
            }
        }
        return false;
    }

    private Set<String> declaredNames(SmsProviderType providerType) {
        Set<String> names = new HashSet<>();
        credentialFields(providerType).forEach(field -> names.add(field.name()));
        return names;
    }

    private void applyCredentials(SmsConfig config, SmsConfigUpdateDto update) {
        if (!config.credentialsReadable()) {
            // Values that cannot be parsed are lost already; dropping them lets any save repair the row, even
            // for a provider that declares no credentials and so would never rewrite them.
            config.clearCredentials();
        }
        Set<String> declared = declaredNames(update.providerType());
        for (String stored : config.credentialNames()) {
            if (!declared.contains(stored)) {
                config.setCredential(stored, null);
            }
        }
        for (SmsCredentialField field : credentialFields(update.providerType())) {
            String value = update.credentials().get(field.name());
            if (!isBlank(value)) {
                config.setCredential(field.name(), value);
            } else if (!field.required() && config.hasCredential(field.name())
                    && !config.hasReadableCredential(field.name())) {
                // An optional value that no longer decrypts, left blank because the page showed it as not stored,
                // would block every send until something was typed into its field, so it is dropped. A required
                // one is kept, in case the encryption key is put right: sending stays blocked until it is entered
                // again either way.
                config.setCredential(field.name(), null);
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * What the audit record compares: values for the plain settings, and only fingerprints of the stored
     * (encrypted) secret and credentials, so a change can be named without reading any secret.
     */
    private record Snapshot(boolean stored, SmsProviderType providerType, boolean enabled, boolean schedulerEnabled,
                            String senderNumber, String webhookSecretStored, String credentialsStored) {
        static Snapshot of(SmsConfig config, boolean stored) {
            return new Snapshot(stored, config.getProviderType(), config.isEnabled(), config.isSchedulerEnabled(),
                    config.getSenderNumber(), config.storedWebhookSecret(), config.storedCredentials());
        }

        List<String> changedFields(Snapshot after) {
            List<String> changed = new ArrayList<>();
            if (!stored) {
                changed.add("firstSave");
            }
            if (providerType != after.providerType) {
                changed.add("providerType");
            }
            if (enabled != after.enabled) {
                changed.add("enabled");
            }
            if (schedulerEnabled != after.schedulerEnabled) {
                changed.add("schedulerEnabled");
            }
            if (!Objects.equals(senderNumber, after.senderNumber)) {
                changed.add("senderNumber");
            }
            if (!Objects.equals(webhookSecretStored, after.webhookSecretStored)) {
                changed.add("webhookSecret");
            }
            if (!Objects.equals(credentialsStored, after.credentialsStored)) {
                changed.add("credentials");
            }
            return changed;
        }
    }
}
