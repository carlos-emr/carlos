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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Gates outbound SMS on the patient's current record for the configured SMS consent type.
 * <p>
 * Anything other than a current explicit opt-in blocks the send: SMS consent is never implied, so a record
 * with {@code explicit=false} does not permit one. When a patient has several live records for the consent
 * type, any opt-out among them blocks the send. The consent record is read through {@link ConsentDao}
 * rather than {@code PatientConsentManager} because the manager's lookup requires a {@code LoggedInInfo}
 * for its privilege check and access log, and the queue worker rechecks consent on a scheduler thread that
 * has no session. Authorizing the sender belongs to the send entry point, not to this check.
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
            "SMS consent is not configured; the sms_communication property must name an active consent type.";
    private static final String UNKNOWN_CODE = "SMS_CONSENT_UNKNOWN";
    private static final String UNKNOWN_MESSAGE = "No SMS consent is recorded for this patient.";
    private static final String NOT_EXPLICIT_CODE = "SMS_CONSENT_NOT_EXPLICIT";
    private static final String NOT_EXPLICIT_MESSAGE =
            "Only implied SMS consent is recorded for this patient; delete that record and record the "
                    + "patient's consent again.";
    private static final String OPTED_OUT_CODE = "SMS_CONSENT_OPTED_OUT";
    private static final String OPTED_OUT_MESSAGE = "This patient has opted out of SMS.";
    private static final String SYSTEM_TEST_DISABLED_CODE = "SMS_SYSTEM_TEST_DISABLED";
    private static final String SYSTEM_TEST_DISABLED_MESSAGE =
            "SMS system-test messages are disabled (sms.systemTest.enabled).";

    /** Orders consent records by edit date, treating an undated record as the oldest. */
    private static final Comparator<Consent> BY_EDIT_DATE =
            Comparator.comparing(Consent::getEditDate, Comparator.nullsFirst(Comparator.naturalOrder()));

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
        if (command == null) {
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
        if (command.demographicNo() == null) {
            return blockedWithoutRecord(SmsStatus.CONSENT_BLOCKED, UNKNOWN_CODE, UNKNOWN_MESSAGE,
                    SmsConsentStatus.UNKNOWN);
        }

        Optional<ConsentType> consentType = consentTypeResolver.resolve();
        if (consentType.isEmpty()) {
            return blockedWithoutRecord(SmsStatus.CONSENT_BLOCKED, NOT_CONFIGURED_CODE, NOT_CONFIGURED_MESSAGE,
                    SmsConsentStatus.NOT_CONFIGURED);
        }

        List<Consent> records = currentRecords(command.demographicNo(), consentType.get());
        if (records.isEmpty()) {
            return blockedWithoutRecord(SmsStatus.CONSENT_BLOCKED, UNKNOWN_CODE, UNKNOWN_MESSAGE,
                    SmsConsentStatus.UNKNOWN);
        }

        // Nothing stops the Consent table holding more than one live row per patient and consent type, and
        // a single-row lookup would pick one arbitrarily. Fail safe: any live opt-out blocks the send.
        Optional<Consent> optOut = records.stream().filter(Consent::isOptout).max(BY_EDIT_DATE);
        if (optOut.isPresent()) {
            return SmsConsentDecisionDto.blocked(SmsStatus.OPTOUT_BLOCKED, OPTED_OUT_CODE, OPTED_OUT_MESSAGE,
                    SmsConsentStatus.OPT_OUT, optOut.get().getId(), lastUpdate(optOut.get()));
        }
        // Every remaining record is an opt-in, but only one the patient gave directly counts. The patient
        // record stores explicit consent on every row it creates and never changes the flag on an existing
        // row, so an implied row can only come from an import or API caller and has to be replaced.
        Optional<Consent> explicitOptIn = records.stream().filter(Consent::isExplicit).max(BY_EDIT_DATE);
        if (explicitOptIn.isEmpty()) {
            Consent implied = records.stream().max(BY_EDIT_DATE).orElseThrow();
            return SmsConsentDecisionDto.blocked(SmsStatus.CONSENT_BLOCKED, NOT_EXPLICIT_CODE, NOT_EXPLICIT_MESSAGE,
                    SmsConsentStatus.NOT_EXPLICIT, implied.getId(), lastUpdate(implied));
        }
        return SmsConsentDecisionDto.permitted(
                SmsConsentStatus.OPT_IN, explicitOptIn.get().getId(), lastUpdate(explicitOptIn.get()));
    }

    private List<Consent> currentRecords(int demographicNo, ConsentType consentType) {
        List<Consent> all = consentDao.findByDemographic(demographicNo);
        if (all == null) {
            return List.of();
        }
        return all.stream()
                .filter(consent -> !consent.isDeleted())
                .filter(consent -> consentType.getId().equals(consent.getConsentTypeId()))
                .toList();
    }

    private static Instant lastUpdate(Consent consent) {
        // java.sql.Date subclasses throw from toInstant(); epoch millis is safe for every Date.
        return consent.getEditDate() == null ? null : Instant.ofEpochMilli(consent.getEditDate().getTime());
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
