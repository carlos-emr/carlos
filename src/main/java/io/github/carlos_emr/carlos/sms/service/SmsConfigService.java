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
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
     *
     * @param update              the submitted settings
     * @param updatedByProviderNo the admin saving them, recorded on the row
     * @return the saved settings
     * @throws SmsConfigConflictException when another save raced this one; nothing from this one is stored
     */
    @Transactional
    public SmsConfig save(SmsConfigUpdateDto update, String updatedByProviderNo) {
        Optional<SmsConfig> existing = smsConfigDao.findCurrent();
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
     * Writes the row now, inside this transaction, so a save that raced another administrator's fails
     * here: a second first save hits the fixed id, and a second update fails the version check. The
     * exception rolls the transaction back. The JPA and Hibernate types are what the flush throws; the
     * Spring types cover a DAO whose exceptions are translated.
     */
    private void flushOrReportConflict() {
        try {
            smsConfigDao.flush();
        } catch (OptimisticLockException | StaleStateException | EntityExistsException
                 | ConstraintViolationException | OptimisticLockingFailureException
                 | DataIntegrityViolationException e) {
            throw new SmsConfigConflictException(e);
        }
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
