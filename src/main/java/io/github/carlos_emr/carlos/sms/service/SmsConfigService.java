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

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.dao.SmsConfigDao;
import io.github.carlos_emr.carlos.sms.dto.SmsConfigUpdateDto;
import io.github.carlos_emr.carlos.sms.event.SmsConfigChangedEvent;
import io.github.carlos_emr.carlos.sms.model.SmsConfig;
import io.github.carlos_emr.carlos.sms.support.SmsPhoneNumbers;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleStateException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
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
 * credential fields the chosen provider declares are kept; others (for example from a previously
 * chosen provider) are removed so no stale secret lingers. Saving publishes
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

    public SmsConfigService(SmsConfigDao smsConfigDao, SmsProviderClientResolver providerClients,
                            ApplicationEventPublisher eventPublisher, SmsConfigAuditRecorder auditRecorder) {
        this.smsConfigDao = smsConfigDao;
        this.providerClients = providerClients;
        this.eventPublisher = eventPublisher;
        this.auditRecorder = auditRecorder;
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
    public List<String> credentialFields(SmsProviderType providerType) {
        if (providerType == null || !providerClients.registeredProviderTypes().contains(providerType)) {
            return List.of();
        }
        return List.copyOf(providerClients.resolve(providerType).credentialFields());
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
        Optional<SmsConfig> existing = smsConfigDao.findCurrent();
        if (!Objects.equals(existing.map(SmsConfig::getVersion).orElse(null), update.expectedVersion())) {
            throw new SmsConfigConflictException();
        }
        SmsConfig config = existing.orElseGet(SmsConfig::new);
        Snapshot before = Snapshot.of(config, existing.isPresent());
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
        Set<String> declared = new HashSet<>(credentialFields(update.providerType()));
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

    private void applyCredentials(SmsConfig config, SmsConfigUpdateDto update) {
        Set<String> declared = new HashSet<>(credentialFields(update.providerType()));
        for (String stored : config.credentialNames()) {
            if (!declared.contains(stored)) {
                config.setCredential(stored, null);
            }
        }
        for (String field : declared) {
            String value = update.credentials().get(field);
            if (!isBlank(value)) {
                config.setCredential(field, value);
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
