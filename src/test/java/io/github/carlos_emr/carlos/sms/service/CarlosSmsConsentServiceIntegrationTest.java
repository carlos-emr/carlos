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
import io.github.carlos_emr.carlos.commn.dao.ConsentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Consent;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.sms.SmsConsentStatus;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Runs the SMS consent check against the real {@code property}, {@code consentType} and {@code Consent}
 * tables (H2, generated from the entities), and verifies the consent snapshot round-trips through
 * {@code sms_transaction}. The mocked unit test cannot prove the DAO queries select the right rows.
 */
@Tag("integration")
@Tag("service")
@DisplayName("CarlosSmsConsentService integration")
class CarlosSmsConsentServiceIntegrationTest extends CarlosTestBase {
    private static final int DEMOGRAPHIC_NO = 987654;
    private static final String SMS_CONSENT_TYPE = "sms_communication_consent";

    @Autowired
    private UserPropertyDAO userPropertyDao;

    @Autowired
    private ConsentTypeDao consentTypeDao;

    @Autowired
    private ConsentDao consentDao;

    @Autowired
    private SmsTransactionDao smsTransactionDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private CarlosSmsConsentService consentService;

    @BeforeEach
    void setUp() {
        consentService = new CarlosSmsConsentService(
                new SmsConsentTypeResolver(userPropertyDao, consentTypeDao), consentDao, () -> false);
    }

    @Test
    @DisplayName("blocks as not configured when the sms_communication property is absent")
    void shouldBlockAsNotConfigured_whenPropertyIsAbsent() {
        SmsConsentDecisionDto decision = consentService.evaluate(patientMessage());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("SMS_CONSENT_NOT_CONFIGURED");
    }

    @Test
    @DisplayName("blocks as unknown when the patient only consented to a different consent type")
    void shouldBlockAsUnknown_whenPatientConsentedToDifferentType() {
        configureSmsConsentType();
        ConsentType otherType = persistConsentType("electronic_communication_consent");
        persistConsent(otherType, false);

        SmsConsentDecisionDto decision = consentService.evaluate(patientMessage());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.blockedStatus()).isEqualTo(SmsStatus.CONSENT_BLOCKED);
        assertThat(decision.consentStatus()).isEqualTo(SmsConsentStatus.UNKNOWN);
    }

    @Test
    @DisplayName("blocks as opted out when the patient's SMS consent record is opted out")
    void shouldBlockAsOptedOut_whenSmsConsentRecordIsOptedOut() {
        Consent optedOut = persistConsent(configureSmsConsentType(), true);

        SmsConsentDecisionDto decision = consentService.evaluate(patientMessage());

        assertThat(decision.blockedStatus()).isEqualTo(SmsStatus.OPTOUT_BLOCKED);
        assertThat(decision.consentId()).isEqualTo(optedOut.getId());
    }

    @Test
    @DisplayName("blocks as opted out when duplicate SMS consent rows disagree")
    void shouldBlockAsOptedOut_whenDuplicateConsentRowsDisagree() {
        ConsentType smsType = configureSmsConsentType();
        persistConsent(smsType, false);
        Consent optedOut = persistConsent(smsType, true);

        SmsConsentDecisionDto decision = consentService.evaluate(patientMessage());

        assertThat(decision.blockedStatus()).isEqualTo(SmsStatus.OPTOUT_BLOCKED);
        assertThat(decision.consentId()).isEqualTo(optedOut.getId());
    }

    @Test
    @DisplayName("permits and persists the consent snapshot on the sms_transaction row")
    void shouldPersistConsentSnapshot_whenPatientConsented() {
        Consent consented = persistConsent(configureSmsConsentType(), false);
        JpaSmsTransactionService recorder =
                new JpaSmsTransactionService(smsTransactionDao, mock(ApplicationEventPublisher.class));

        SmsConsentDecisionDto decision = consentService.evaluate(patientMessage());
        SmsTransaction recorded = recorder.recordOutboundAttempt(patientMessage(), SmsProviderType.STUB, decision);
        entityManager.flush();
        entityManager.clear();

        assertThat(decision.allowed()).isTrue();
        SmsTransaction reloaded = entityManager.find(SmsTransaction.class, recorded.getId());
        assertThat(reloaded.getStatus()).isEqualTo(SmsStatus.QUEUED);
        assertThat(reloaded.getConsentStatus()).isEqualTo(SmsConsentStatus.OPT_IN);
        assertThat(reloaded.getConsentId()).isEqualTo(consented.getId());
        // The reloaded row and the reloaded Consent must agree, or every dispatch would rewrite the snapshot.
        assertThat(reloaded.hasConsentSnapshot(consentService.evaluate(patientMessage()))).isTrue();
    }

    @Test
    @DisplayName("blocks as not explicit and persists that snapshot when the stored opt-in was implied")
    void shouldPersistNotExplicitSnapshot_whenStoredOptInIsImplied() {
        Consent implied = persistConsent(configureSmsConsentType(), false, false, false);
        JpaSmsTransactionService recorder =
                new JpaSmsTransactionService(smsTransactionDao, mock(ApplicationEventPublisher.class));

        SmsConsentDecisionDto decision = consentService.evaluate(patientMessage());
        SmsTransaction recorded = recorder.recordOutboundAttempt(patientMessage(), SmsProviderType.STUB, decision);
        entityManager.flush();
        entityManager.clear();

        assertThat(decision.allowed()).isFalse();
        SmsTransaction reloaded = entityManager.find(SmsTransaction.class, recorded.getId());
        assertThat(reloaded.getStatus()).isEqualTo(SmsStatus.CONSENT_BLOCKED);
        assertThat(reloaded.getConsentStatus()).isEqualTo(SmsConsentStatus.NOT_EXPLICIT);
        assertThat(reloaded.getConsentReasonCode()).isEqualTo("SMS_CONSENT_NOT_EXPLICIT");
        assertThat(reloaded.getConsentId()).isEqualTo(implied.getId());
    }

    @Test
    @DisplayName("blocks as unknown when the patient's stored SMS opt-in was withdrawn by soft delete")
    void shouldBlockAsUnknown_whenStoredOptInIsSoftDeleted() {
        persistConsent(configureSmsConsentType(), false, true, true);

        SmsConsentDecisionDto decision = consentService.evaluate(patientMessage());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.consentStatus()).isEqualTo(SmsConsentStatus.UNKNOWN);
    }

    private ConsentType configureSmsConsentType() {
        ConsentType consentType = persistConsentType(SMS_CONSENT_TYPE);
        UserProperty property = new UserProperty();
        property.setName(UserProperty.SMS_COMMUNICATION);
        property.setValue(SMS_CONSENT_TYPE);
        entityManager.persist(property);
        entityManager.flush();
        return consentType;
    }

    private ConsentType persistConsentType(String type) {
        ConsentType consentType = new ConsentType();
        consentType.setType(type);
        consentType.setName("Synthetic " + type);
        consentType.setDescription("Synthetic consent type for tests");
        consentType.setActive(true);
        entityManager.persist(consentType);
        entityManager.flush();
        return consentType;
    }

    private Consent persistConsent(ConsentType consentType, boolean optedOut) {
        return persistConsent(consentType, optedOut, true, false);
    }

    private Consent persistConsent(ConsentType consentType, boolean optedOut, boolean explicit, boolean deleted) {
        Consent consent = new Consent();
        consent.setDemographicNo(DEMOGRAPHIC_NO);
        consent.setConsentTypeId(consentType.getId());
        consent.setExplicit(explicit);
        consent.setOptout(optedOut);
        consent.setDeleted(deleted);
        consent.setEditDate(new Date());
        entityManager.persist(consent);
        entityManager.flush();
        // Detach so the service reads the row back from the table, as it does in production.
        entityManager.clear();
        return consent;
    }

    private static SmsSendCommand patientMessage() {
        return SmsSendCommand.patientMessage(DEMOGRAPHIC_NO, "416-555-1212", "Synthetic test message", "999998");
    }
}
