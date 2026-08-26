/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Consent;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@Tag("unit")
@Tag("fast")
@Tag("email")
@DisplayName("EmailConsentResolver")
class EmailConsentResolverUnitTest extends CarlosUnitTestBase {
    private EmailConsentResolver emailConsentResolver;
    private UserPropertyDAO userPropertyDAO;
    private PatientConsentManager patientConsentManager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        userPropertyDAO = mock(UserPropertyDAO.class);
        patientConsentManager = mock(PatientConsentManager.class);
        loggedInInfo = new LoggedInInfo();
        emailConsentResolver = new EmailConsentResolver(userPropertyDAO, patientConsentManager);
    }

    @Test
    @DisplayName("should parse configured consent type when value has leading delimiters")
    void shouldParseConfiguredConsentType_whenValueHasLeadingDelimiters() {
        ConsentType consentType = consentType("EmailConsent");
        when(userPropertyDAO.getProp(UserProperty.EMAIL_COMMUNICATION)).thenReturn(property("  ,; ( EmailConsent )"));
        when(patientConsentManager.getConsentType("EmailConsent")).thenReturn(consentType);

        EmailConsentResult result = emailConsentResolver.resolve(loggedInInfo, 123);

        assertThat(result.getStatus()).isEqualTo(EmailConsentStatus.UNKNOWN);
        assertThat(result.getConsentName()).isEqualTo("EmailConsent");
        verify(patientConsentManager).getConsentType("EmailConsent");
    }

    @Test
    @DisplayName("should resolve opt in when patient consent record is active and not opted out")
    void shouldResolveOptIn_whenPatientConsentRecordIsActiveAndNotOptedOut() {
        ConsentType consentType = consentType("EmailConsent");
        Date editDate = new Date(1_000L);
        Consent consent = consent(false, editDate);
        when(userPropertyDAO.getProp(UserProperty.EMAIL_COMMUNICATION)).thenReturn(property("EmailConsent"));
        when(patientConsentManager.getConsentType("EmailConsent")).thenReturn(consentType);
        when(patientConsentManager.getConsentByDemographicAndConsentType(loggedInInfo, 123, consentType)).thenReturn(consent);

        EmailConsentResult result = emailConsentResolver.resolve(loggedInInfo, 123);

        assertThat(result.getStatus()).isEqualTo(EmailConsentStatus.OPT_IN);
        assertThat(result.getConsentId()).isEqualTo(77);
        assertThat(result.getConsentLastUpdateDate()).isEqualTo(editDate);
    }

    @Test
    @DisplayName("should resolve opt out when patient consent record is opted out")
    void shouldResolveOptOut_whenPatientConsentRecordIsOptedOut() {
        ConsentType consentType = consentType("EmailConsent");
        Consent consent = consent(true, new Date(1_000L));
        when(userPropertyDAO.getProp(UserProperty.EMAIL_COMMUNICATION)).thenReturn(property("EmailConsent"));
        when(patientConsentManager.getConsentType("EmailConsent")).thenReturn(consentType);
        when(patientConsentManager.getConsentByDemographicAndConsentType(loggedInInfo, 123, consentType)).thenReturn(consent);

        EmailConsentResult result = emailConsentResolver.resolve(loggedInInfo, 123);

        assertThat(result.getStatus()).isEqualTo(EmailConsentStatus.OPT_OUT);
        assertThat(result.getConsentId()).isEqualTo(77);
    }

    @Test
    @DisplayName("should resolve not configured when property is missing")
    void shouldResolveNotConfigured_whenPropertyIsMissing() {
        when(userPropertyDAO.getProp(UserProperty.EMAIL_COMMUNICATION)).thenReturn(null);

        EmailConsentResult result = emailConsentResolver.resolve(loggedInInfo, 123);

        assertThat(result.getStatus()).isEqualTo(EmailConsentStatus.NOT_CONFIGURED);
    }

    @Test
    @DisplayName("should resolve not configured when consent type active flag is null")
    void shouldResolveNotConfigured_whenConsentTypeActiveFlagIsNull() {
        ConsentType consentType = new ConsentType();
        consentType.setName("EmailConsent");
        when(userPropertyDAO.getProp(UserProperty.EMAIL_COMMUNICATION)).thenReturn(property("EmailConsent"));
        when(patientConsentManager.getConsentType("EmailConsent")).thenReturn(consentType);

        EmailConsentResult result = emailConsentResolver.resolve(loggedInInfo, 123);

        assertThat(result.getStatus()).isEqualTo(EmailConsentStatus.NOT_CONFIGURED);
    }

    private UserProperty property(String value) {
        UserProperty property = new UserProperty();
        property.setValue(value);
        return property;
    }

    private ConsentType consentType(String name) {
        ConsentType consentType = new ConsentType();
        consentType.setName(name);
        consentType.setActive(true);
        return consentType;
    }

    private Consent consent(boolean optout, Date editDate) {
        Consent consent = new Consent();
        ReflectionTestUtils.setField(consent, "id", 77);
        consent.setOptout(optout);
        consent.setEditDate(editDate);
        return consent;
    }
}
