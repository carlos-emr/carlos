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
import io.github.carlos_emr.carlos.commn.dao.ConsentDao;
import io.github.carlos_emr.carlos.commn.model.Consent;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.sms.SmsConsentStatus;
import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Gates outbound SMS on the patient's current record for the configured SMS consent type.
 * <p>
 * Anything other than a current opt-in blocks the send: SMS consent is never implied. The consent
 * record is read through {@link ConsentDao} rather than {@code PatientConsentManager} because the
 * manager's lookup requires a {@code LoggedInInfo} for its privilege check and access log, and the queue
 * worker rechecks consent on a scheduler thread that has no session. Authorizing the sender belongs to
 * the send entry point, not to this check.
 * <p>
 * Decisions carry reason codes and generic operator messages only; they never include patient identifiers.
 *
 * @since 2026-09-18
 */
@Service
public class CarlosSmsConsentService implements SmsConsentService {
    static final String SYSTEM_TEST_ENABLED_PROPERTY = "sms.systemTest.enabled";

    private static final String NOT_CONFIGURED_CODE = "SMS_CONSENT_NOT_CONFIGURED";
    private static final String NOT_CONFIGURED_MESSAGE =
            "SMS consent is not configured; set the sms_communication property to an active consent type.";
    private static final String UNKNOWN_CODE = "SMS_CONSENT_UNKNOWN";
    private static final String UNKNOWN_MESSAGE = "No SMS consent is recorded for this patient.";
    private static final String OPTED_OUT_CODE = "SMS_CONSENT_OPTED_OUT";
    private static final String OPTED_OUT_MESSAGE = "This patient has opted out of SMS.";
    private static final String SYSTEM_TEST_DISABLED_CODE = "SMS_SYSTEM_TEST_DISABLED";
    private static final String SYSTEM_TEST_DISABLED_MESSAGE =
            "SMS system-test messages are disabled (sms.systemTest.enabled).";

    private final SmsConsentTypeResolver consentTypeResolver;
    private final ConsentDao consentDao;
    private final BooleanSupplier systemTestEnabled;

    @Autowired
    public CarlosSmsConsentService(SmsConsentTypeResolver consentTypeResolver, ConsentDao consentDao) {
        this(consentTypeResolver, consentDao,
                () -> CarlosProperties.getInstance().isPropertyActive(SYSTEM_TEST_ENABLED_PROPERTY));
    }

    CarlosSmsConsentService(
            SmsConsentTypeResolver consentTypeResolver,
            ConsentDao consentDao,
            BooleanSupplier systemTestEnabled
    ) {
        this.consentTypeResolver = consentTypeResolver;
        this.consentDao = consentDao;
        this.systemTestEnabled = systemTestEnabled;
    }

    @Override
    public SmsConsentDecisionDto evaluate(SmsSendCommand command) {
        if (command == null || command.demographicNo() == null) {
            return blockedWithoutRecord(SmsStatus.CONSENT_BLOCKED, UNKNOWN_CODE, UNKNOWN_MESSAGE,
                    SmsConsentStatus.UNKNOWN);
        }
        if (command.messagePurpose() == SmsMessagePurpose.SYSTEM_TEST) {
            // System tests are synthetic and never fall through to a patient's consent record, so the
            // switch alone decides them and cannot widen what real patient messages are allowed to do.
            return systemTestEnabled.getAsBoolean()
                    ? SmsConsentDecisionDto.permitted(SmsConsentStatus.SYSTEM_TEST, null, null)
                    : blockedWithoutRecord(SmsStatus.CONSENT_BLOCKED, SYSTEM_TEST_DISABLED_CODE,
                            SYSTEM_TEST_DISABLED_MESSAGE, SmsConsentStatus.SYSTEM_TEST);
        }

        Optional<ConsentType> consentType = consentTypeResolver.resolve();
        if (consentType.isEmpty()) {
            return blockedWithoutRecord(SmsStatus.CONSENT_BLOCKED, NOT_CONFIGURED_CODE, NOT_CONFIGURED_MESSAGE,
                    SmsConsentStatus.NOT_CONFIGURED);
        }

        Consent consent = consentDao.findByDemographicAndConsentTypeId(
                command.demographicNo(), consentType.get().getId());
        if (consent == null || consent.isDeleted()) {
            return blockedWithoutRecord(SmsStatus.CONSENT_BLOCKED, UNKNOWN_CODE, UNKNOWN_MESSAGE,
                    SmsConsentStatus.UNKNOWN);
        }

        Instant lastUpdate = consent.getEditDate() == null
                ? null
                : Instant.ofEpochMilli(consent.getEditDate().getTime());
        if (consent.isOptout()) {
            return SmsConsentDecisionDto.blocked(SmsStatus.OPTOUT_BLOCKED, OPTED_OUT_CODE, OPTED_OUT_MESSAGE,
                    SmsConsentStatus.OPT_OUT, consent.getId(), lastUpdate);
        }
        return SmsConsentDecisionDto.permitted(SmsConsentStatus.OPT_IN, consent.getId(), lastUpdate);
    }

    private static SmsConsentDecisionDto blockedWithoutRecord(
            SmsStatus blockedStatus,
            String reasonCode,
            String operatorMessage,
            SmsConsentStatus consentStatus
    ) {
        return SmsConsentDecisionDto.blocked(blockedStatus, reasonCode, operatorMessage, consentStatus, null, null);
    }
}
