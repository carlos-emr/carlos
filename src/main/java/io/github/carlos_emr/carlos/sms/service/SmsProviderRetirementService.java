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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
import io.github.carlos_emr.carlos.sms.SmsProviderErrorCode;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.dao.SmsConfigDao;
import io.github.carlos_emr.carlos.sms.dao.SmsProviderRateLimitDao;
import io.github.carlos_emr.carlos.sms.dao.SmsProviderRetirementDao;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsConfig;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Serializes selection changes and queue ownership across servers. The existing STUB limiter key is
 * atomically materialized and locked before config, retirement preferences, or message rows. Merely
 * locking the missing config key allows compatible gap locks and is not a first-save mutex. This does
 * not acquire a permit, and no external send or nested limiter transaction runs while this lock is held.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class SmsProviderRetirementService {
    /** Closes a retired provider's waiting text that was queued again, as a new row, for the selected provider. */
    public static final String QUEUE_PROVIDER_MOVED_CODE = "QUEUE_PROVIDER_MOVED";
    /** The retry code a provider refusal was saved with before the fixed codes; rows saved then keep it. */
    static final String LEGACY_PROVIDER_FAILURE_RETRY_SCHEDULED_CODE = "QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED";
    /**
     * Retry codes whose attempt definitely sent nothing: the provider refused the text (a fixed code the queue
     * retries, or the legacy code), or CARLOS could not check consent and never called the provider. The queue
     * keeps a fixed code on a waiting row only after a definite FAILED answer; an unclear answer leaves the row
     * {@code SENDING}. A stale send the provider did not find ({@code QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED})
     * is not one of them: it may still have gone out.
     */
    private static final Set<String> DEFINITELY_UNSENT_RETRY_CODES = definitelyUnsentRetryCodes();

    private final SmsProviderRateLimitDao rateLimitDao;
    private final SmsConfigDao configDao;
    private final SmsProviderRetirementDao retirementDao;

    public SmsProviderRetirementService(SmsProviderRateLimitDao rateLimitDao, SmsConfigDao configDao,
                                        SmsProviderRetirementDao retirementDao) {
        this.rateLimitDao = rateLimitDao;
        this.configDao = configDao;
        this.retirementDao = retirementDao;
    }

    public Optional<SmsConfig> lockSelection() {
        rateLimitDao.ensureExists(SmsProviderType.STUB, new Date());
        if (rateLimitDao.findByProviderTypeForUpdate(SmsProviderType.STUB).isEmpty()) {
            throw new IllegalStateException("SMS provider selection could not be locked; sending is blocked.");
        }
        return configDao.findCurrentForUpdate();
    }

    public void recordSelection(SmsProviderType previous, SmsProviderType selected, boolean firstSave) {
        retirementDao.recordSelection(previous, selected, firstSave);
    }

    public long retiredThrough(SmsProviderType provider) {
        return retirementDao.retiredThrough(provider);
    }

    public boolean retired(SmsTransaction row) {
        return row.getId() != null && row.getId() <= retiredThrough(row.getProviderType());
    }

    /** A forced administrator STUB system test is permitted independently of the clinic's selection. */
    public boolean admissionAllowed(Optional<SmsConfig> config, SmsProviderType provider, SmsMessagePurpose purpose) {
        retirementDao.retiredThrough(provider); // Corrupt retirement state also refuses new admissions.
        if (purpose == SmsMessagePurpose.SYSTEM_TEST && provider == SmsProviderType.STUB) {
            return true;
        }
        return selectedProvider(config) == provider && config.map(SmsConfig::isEnabled).orElse(true);
    }

    /**
     * @param config the locked settings row, or empty while nothing is saved
     * @return the provider the clinic selects now: the saved one, or {@code sms.provider.default} while nothing
     *         is saved
     * @throws IllegalStateException when nothing is saved and {@code sms.provider.default} names no provider
     */
    public SmsProviderType selectedProvider(Optional<SmsConfig> config) {
        return config.map(SmsConfig::getProviderType).orElseGet(() -> {
            String value = CarlosProperties.getInstance().getProperty("sms.provider.default");
            if (value == null || value.isBlank()) {
                return SmsProviderType.STUB;
            }
            try {
                return SmsProviderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Invalid sms.provider.default; configure a supported SMS provider.");
            }
        });
    }

    /**
     * Whether a retired provider's waiting text may be queued again for the selected provider, as a new row
     * under a new client reference. Only a patient's text that certainly never went out qualifies: still
     * {@code QUEUED} with its body, never accepted by a provider or reported on by one, and either never tried or
     * rescheduled after an attempt that definitely sent nothing. Anything that may have been sent stays with
     * staff: a {@code SENDING} row (never offered here; stale recovery owns it), a row rescheduled after a stale
     * send the provider did not find, a row a provider callback has touched, and any retry this rule does not
     * recognize. A system test is never moved: the administrator sends a new one.
     */
    static boolean movable(SmsTransaction row) {
        if (row.getStatus() != SmsStatus.QUEUED || row.getMessagePurpose() == SmsMessagePurpose.SYSTEM_TEST
                || !row.hasStoredMessageBody() || row.getProviderEventAt() != null
                || (row.getProviderMessageId() != null && !row.getProviderMessageId().isBlank())) {
            return false;
        }
        if (SmsQueueProcessingService.QUEUE_STALE_STATUS_NOT_FOUND_RETRY_SCHEDULED_CODE.equals(row.getErrorCode())) {
            return false;
        }
        // Set.of rejects a null lookup, and a tried row with no code is not one this rule recognizes.
        return row.getAttemptCount() == 0
                || (row.getErrorCode() != null && DEFINITELY_UNSENT_RETRY_CODES.contains(row.getErrorCode()));
    }

    private static Set<String> definitelyUnsentRetryCodes() {
        Set<String> codes = new HashSet<>();
        codes.add(LEGACY_PROVIDER_FAILURE_RETRY_SCHEDULED_CODE);
        codes.add(SmsQueueProcessingService.QUEUE_CONSENT_CHECK_FAILED_RETRY_SCHEDULED_CODE);
        for (SmsProviderErrorCode code : SmsProviderErrorCode.values()) {
            // A permanent refusal is never retried, so it is never on a waiting row.
            if (!code.permanent()) {
                codes.add(code.name());
            }
        }
        return Set.copyOf(codes);
    }

    /** What the moved row is closed with: it names the provider and the new row that sends the text now. */
    static SmsProviderSendResultDto movedResult(SmsProviderType selected, Long movedToId) {
        return SmsProviderSendResultDto.failed(QUEUE_PROVIDER_MOVED_CODE,
                "Moved to " + selected.name() + " as text " + movedToId + ".");
    }

    static SmsProviderSendResultDto retirementFailure(SmsTransaction row) {
        boolean systemTest = row.getMessagePurpose() == SmsMessagePurpose.SYSTEM_TEST;
        return SmsProviderSendResultDto.failed(systemTest ? "QUEUE_SYSTEM_TEST_PROVIDER_CHANGED" : "QUEUE_PROVIDER_NOT_ACTIVE",
                systemTest ? "This queued system test was not sent. Use Send test again in Administration > SMS."
                        : "The SMS provider for this queued text was retired. Review it before sending it again.");
    }
}
