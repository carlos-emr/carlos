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

import io.github.carlos_emr.carlos.commn.dao.ConsentDao;
import io.github.carlos_emr.carlos.commn.model.Consent;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.sms.SmsConsentStatus;
import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
import io.github.carlos_emr.carlos.sms.SmsRecipientPhoneType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("service")
@ExtendWith(MockitoExtension.class)
class CarlosSmsConsentServiceUnitTest {
    private static final int DEMOGRAPHIC_NO = 123;
    private static final int CONSENT_TYPE_ID = 7;
    private static final int CONSENT_ID = 4321;
    private static final Instant EDITED_AT = Instant.parse("2026-09-01T14:30:00Z");

    @Mock
    private SmsConsentTypeResolver consentTypeResolver;

    @Mock
    private ConsentDao consentDao;

    @Test
    @DisplayName("evaluate blocks patient messages when no SMS consent type is configured")
    void shouldBlockAsNotConfigured_whenNoSmsConsentTypeIsConfigured() {
        when(consentTypeResolver.resolve()).thenReturn(Optional.empty());

        SmsConsentDecisionDto decision = service(false).evaluate(patientMessage());

        assertBlocked(decision, SmsStatus.CONSENT_BLOCKED, "SMS_CONSENT_NOT_CONFIGURED",
                SmsConsentStatus.NOT_CONFIGURED);
        assertThat(decision.consentId()).isNull();
        verifyNoInteractions(consentDao);
    }

    @Test
    @DisplayName("evaluate blocks patient messages when the patient has no SMS consent record")
    void shouldBlockAsUnknown_whenPatientHasNoConsentRecord() {
        configureConsentType();
        when(consentDao.findByDemographic(DEMOGRAPHIC_NO)).thenReturn(List.of());

        SmsConsentDecisionDto decision = service(false).evaluate(patientMessage());

        assertBlocked(decision, SmsStatus.CONSENT_BLOCKED, "SMS_CONSENT_UNKNOWN", SmsConsentStatus.UNKNOWN);
        assertThat(decision.consentId()).isNull();
        assertThat(decision.consentLastUpdateDate()).isNull();
    }

    @Test
    @DisplayName("evaluate treats a deleted SMS consent record as unknown consent")
    void shouldBlockAsUnknown_whenConsentRecordIsDeleted() {
        configureConsentType();
        Consent deleted = consent(false);
        deleted.setDeleted(true);
        when(consentDao.findByDemographic(DEMOGRAPHIC_NO)).thenReturn(List.of(deleted));

        SmsConsentDecisionDto decision = service(false).evaluate(patientMessage());

        assertBlocked(decision, SmsStatus.CONSENT_BLOCKED, "SMS_CONSENT_UNKNOWN", SmsConsentStatus.UNKNOWN);
    }

    @Test
    @DisplayName("evaluate blocks patient messages as opted out and records the consent row relied on")
    void shouldBlockAsOptedOut_whenPatientOptedOut() {
        configureConsentType();
        Consent optedOut = consent(true);
        when(consentDao.findByDemographic(DEMOGRAPHIC_NO)).thenReturn(List.of(optedOut));

        SmsConsentDecisionDto decision = service(false).evaluate(patientMessage());

        assertBlocked(decision, SmsStatus.OPTOUT_BLOCKED, "SMS_CONSENT_OPTED_OUT", SmsConsentStatus.OPT_OUT);
        assertThat(decision.consentId()).isEqualTo(CONSENT_ID);
        assertThat(decision.consentLastUpdateDate()).isEqualTo(EDITED_AT);
    }

    @Test
    @DisplayName("evaluate permits patient messages and records the consent row relied on")
    void shouldPermitWithConsentSnapshot_whenPatientConsented() {
        configureConsentType();
        Consent consented = consent(false);
        when(consentDao.findByDemographic(DEMOGRAPHIC_NO)).thenReturn(List.of(consented));

        SmsConsentDecisionDto decision = service(false).evaluate(patientMessage());

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.blockedStatus()).isNull();
        assertThat(decision.reasonCode()).isNull();
        assertThat(decision.consentStatus()).isEqualTo(SmsConsentStatus.OPT_IN);
        assertThat(decision.consentId()).isEqualTo(CONSENT_ID);
        assertThat(decision.consentLastUpdateDate()).isEqualTo(EDITED_AT);
    }

    @Test
    @DisplayName("evaluate fails safe to opted out when duplicate SMS consent records conflict")
    void shouldBlockAsOptedOut_whenDuplicateConsentRecordsConflict() {
        configureConsentType();
        Consent newerOptIn = consent(5000, CONSENT_TYPE_ID, false, EDITED_AT.plusSeconds(3600));
        Consent olderOptOut = consent(4000, CONSENT_TYPE_ID, true, EDITED_AT);
        when(consentDao.findByDemographic(DEMOGRAPHIC_NO)).thenReturn(List.of(newerOptIn, olderOptOut));

        SmsConsentDecisionDto decision = service(false).evaluate(patientMessage());

        assertBlocked(decision, SmsStatus.OPTOUT_BLOCKED, "SMS_CONSENT_OPTED_OUT", SmsConsentStatus.OPT_OUT);
        assertThat(decision.consentId()).isEqualTo(4000);
    }

    @Test
    @DisplayName("evaluate records the most recently edited record when duplicate opt-ins exist")
    void shouldRecordMostRecentlyEditedConsent_whenDuplicateOptInsExist() {
        configureConsentType();
        Consent undated = consent(3000, CONSENT_TYPE_ID, false, null);
        Consent older = consent(4000, CONSENT_TYPE_ID, false, EDITED_AT);
        Consent newer = consent(5000, CONSENT_TYPE_ID, false, EDITED_AT.plusSeconds(3600));
        when(consentDao.findByDemographic(DEMOGRAPHIC_NO)).thenReturn(List.of(undated, older, newer));

        SmsConsentDecisionDto decision = service(false).evaluate(patientMessage());

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.consentId()).isEqualTo(5000);
        assertThat(decision.consentLastUpdateDate()).isEqualTo(EDITED_AT.plusSeconds(3600));
    }

    @Test
    @DisplayName("evaluate ignores the patient's records for other consent types")
    void shouldBlockAsUnknown_whenOnlyOtherConsentTypesAreRecorded() {
        configureConsentType();
        Consent otherTypeOptIn = consent(6000, CONSENT_TYPE_ID + 1, false, EDITED_AT);
        when(consentDao.findByDemographic(DEMOGRAPHIC_NO)).thenReturn(List.of(otherTypeOptIn));

        SmsConsentDecisionDto decision = service(false).evaluate(patientMessage());

        assertBlocked(decision, SmsStatus.CONSENT_BLOCKED, "SMS_CONSENT_UNKNOWN", SmsConsentStatus.UNKNOWN);
    }

    @Test
    @DisplayName("evaluate applies the same patient consent check to appointment reminders")
    void shouldBlockAppointmentReminder_whenPatientHasNoConsentRecord() {
        configureConsentType();
        when(consentDao.findByDemographic(DEMOGRAPHIC_NO)).thenReturn(List.of());

        SmsConsentDecisionDto decision = service(true)
                .evaluate(command(DEMOGRAPHIC_NO, SmsMessagePurpose.APPOINTMENT_REMINDER));

        assertBlocked(decision, SmsStatus.CONSENT_BLOCKED, "SMS_CONSENT_UNKNOWN", SmsConsentStatus.UNKNOWN);
    }

    @Test
    @DisplayName("evaluate blocks on an implied opt-out even when an explicit opt-in exists")
    void shouldBlockAsOptedOut_whenOptOutIsImpliedAndOptInIsExplicit() {
        configureConsentType();
        when(consentDao.findByDemographic(DEMOGRAPHIC_NO)).thenReturn(List.of(
                consent(4000, CONSENT_TYPE_ID, true, EDITED_AT.plusSeconds(3600), false),
                consent(5000, CONSENT_TYPE_ID, false, EDITED_AT, true)));

        SmsConsentDecisionDto decision = service(false).evaluate(patientMessage());

        assertBlocked(decision, SmsStatus.OPTOUT_BLOCKED, "SMS_CONSENT_OPTED_OUT", SmsConsentStatus.OPT_OUT);
        assertThat(decision.consentId()).isEqualTo(4000);
    }

    @Test
    @DisplayName("evaluate reports a lone implied opt-out as an opt-out, not as missing explicit consent")
    void shouldBlockAsOptedOut_whenOnlyRecordIsImpliedOptOut() {
        configureConsentType();
        when(consentDao.findByDemographic(DEMOGRAPHIC_NO))
                .thenReturn(List.of(consent(CONSENT_ID, CONSENT_TYPE_ID, true, EDITED_AT, false)));

        SmsConsentDecisionDto decision = service(false).evaluate(patientMessage());

        assertBlocked(decision, SmsStatus.OPTOUT_BLOCKED, "SMS_CONSENT_OPTED_OUT", SmsConsentStatus.OPT_OUT);
    }

    @Test
    @DisplayName("evaluate permits on an opt-in whose edit date was never stored")
    void shouldPermitWithoutEditDate_whenOnlyOptInIsUndated() {
        configureConsentType();
        when(consentDao.findByDemographic(DEMOGRAPHIC_NO))
                .thenReturn(List.of(consent(CONSENT_ID, CONSENT_TYPE_ID, false, null)));

        SmsConsentDecisionDto decision = service(false).evaluate(patientMessage());

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.consentId()).isEqualTo(CONSENT_ID);
        assertThat(decision.consentLastUpdateDate()).isNull();
    }

    @Test
    @DisplayName("evaluate blocks when the only opt-in on record was implied rather than given by the patient")
    void shouldBlockAsNotExplicit_whenOnlyImpliedConsentIsRecorded() {
        configureConsentType();
        when(consentDao.findByDemographic(DEMOGRAPHIC_NO))
                .thenReturn(List.of(consent(CONSENT_ID, CONSENT_TYPE_ID, false, EDITED_AT, false)));

        SmsConsentDecisionDto decision = service(false).evaluate(patientMessage());

        assertBlocked(decision, SmsStatus.CONSENT_BLOCKED, "SMS_CONSENT_NOT_EXPLICIT", SmsConsentStatus.NOT_EXPLICIT);
        assertThat(decision.consentId()).isEqualTo(CONSENT_ID);
    }

    @Test
    @DisplayName("evaluate relies on the explicit opt-in when a newer implied duplicate exists")
    void shouldRecordExplicitOptIn_whenNewerImpliedDuplicateExists() {
        configureConsentType();
        when(consentDao.findByDemographic(DEMOGRAPHIC_NO)).thenReturn(List.of(
                consent(CONSENT_ID, CONSENT_TYPE_ID, false, EDITED_AT, true),
                consent(CONSENT_ID + 1, CONSENT_TYPE_ID, false, EDITED_AT.plusSeconds(3600), false)));

        SmsConsentDecisionDto decision = service(false).evaluate(patientMessage());

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.consentId()).isEqualTo(CONSENT_ID);
    }

    @Test
    @DisplayName("evaluate decides a system-test message with no patient by the switch alone")
    void shouldPermitSystemTest_whenCommandHasNoPatient() {
        SmsConsentDecisionDto decision = service(true)
                .evaluate(command(null, SmsMessagePurpose.SYSTEM_TEST));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.consentStatus()).isEqualTo(SmsConsentStatus.SYSTEM_TEST);
        verifyNoInteractions(consentTypeResolver, consentDao);
    }

    @Test
    @DisplayName("evaluate blocks a missing command without consulting consent records")
    void shouldBlockAsUnknown_whenCommandIsMissing() {
        SmsConsentDecisionDto decision = service(true).evaluate(null);

        assertBlocked(decision, SmsStatus.CONSENT_BLOCKED, "SMS_CONSENT_UNKNOWN", SmsConsentStatus.UNKNOWN);
        verifyNoInteractions(consentTypeResolver, consentDao);
    }

    @Test
    @DisplayName("evaluate blocks a command with no patient without consulting consent records")
    void shouldBlockAsUnknown_whenDemographicNoIsMissing() {
        SmsConsentDecisionDto decision = service(false)
                .evaluate(command(null, SmsMessagePurpose.PATIENT_MESSAGE));

        assertBlocked(decision, SmsStatus.CONSENT_BLOCKED, "SMS_CONSENT_UNKNOWN", SmsConsentStatus.UNKNOWN);
        verifyNoInteractions(consentTypeResolver, consentDao);
    }

    @Test
    @DisplayName("evaluate permits system-test messages without patient consent when the switch is enabled")
    void shouldPermitSystemTest_whenSystemTestSwitchIsEnabled() {
        SmsConsentDecisionDto decision = service(true)
                .evaluate(command(DEMOGRAPHIC_NO, SmsMessagePurpose.SYSTEM_TEST));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.consentStatus()).isEqualTo(SmsConsentStatus.SYSTEM_TEST);
        assertThat(decision.consentId()).isNull();
        verifyNoInteractions(consentTypeResolver, consentDao);
    }

    @Test
    @DisplayName("evaluate blocks system-test messages when the switch is disabled")
    void shouldBlockSystemTest_whenSystemTestSwitchIsDisabled() {
        SmsConsentDecisionDto decision = service(false)
                .evaluate(command(DEMOGRAPHIC_NO, SmsMessagePurpose.SYSTEM_TEST));

        assertBlocked(decision, SmsStatus.CONSENT_BLOCKED, "SMS_SYSTEM_TEST_DISABLED",
                SmsConsentStatus.SYSTEM_TEST);
        verifyNoInteractions(consentTypeResolver, consentDao);
    }

    private CarlosSmsConsentService service(boolean systemTestEnabled) {
        return new CarlosSmsConsentService(consentTypeResolver, consentDao, () -> systemTestEnabled);
    }

    private void configureConsentType() {
        ConsentType consentType = new ConsentType();
        consentType.setId(CONSENT_TYPE_ID);
        consentType.setType("sms_communication_consent");
        consentType.setActive(true);
        when(consentTypeResolver.resolve()).thenReturn(Optional.of(consentType));
    }

    private static Consent consent(boolean optedOut) {
        return consent(CONSENT_ID, CONSENT_TYPE_ID, optedOut, EDITED_AT);
    }

    /** {@link Consent} exposes no id setter, so a subclass supplies the persisted id the audit snapshot records. */
    private static Consent consent(int id, int consentTypeId, boolean optedOut, Instant editedAt) {
        return consent(id, consentTypeId, optedOut, editedAt, true);
    }

    private static Consent consent(int id, int consentTypeId, boolean optedOut, Instant editedAt, boolean explicit) {
        Consent consent = new Consent() {
            @Override
            public Integer getId() {
                return id;
            }
        };
        consent.setDemographicNo(DEMOGRAPHIC_NO);
        consent.setConsentTypeId(consentTypeId);
        consent.setOptout(optedOut);
        consent.setExplicit(explicit);
        consent.setEditDate(editedAt == null ? null : Date.from(editedAt));
        return consent;
    }

    private static SmsSendCommand patientMessage() {
        return command(DEMOGRAPHIC_NO, SmsMessagePurpose.PATIENT_MESSAGE);
    }

    private static SmsSendCommand command(Integer demographicNo, SmsMessagePurpose purpose) {
        return new SmsSendCommand(
                demographicNo,
                "416-555-1212",
                SmsRecipientPhoneType.CELL,
                "Synthetic test message",
                purpose,
                "999998",
                1001,
                null
        );
    }

    private static void assertBlocked(
            SmsConsentDecisionDto decision,
            SmsStatus blockedStatus,
            String reasonCode,
            SmsConsentStatus consentStatus
    ) {
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.blockedStatus()).isEqualTo(blockedStatus);
        assertThat(decision.reasonCode()).isEqualTo(reasonCode);
        assertThat(decision.consentStatus()).isEqualTo(consentStatus);
        assertThat(decision.operatorMessage()).isNotBlank();
    }
}
