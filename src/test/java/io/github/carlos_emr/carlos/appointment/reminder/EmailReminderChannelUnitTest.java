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
package io.github.carlos_emr.carlos.appointment.reminder;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.EmailConfigDao;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.email.core.EmailSendResult;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EmailComposeManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #5887: the email reminder goes through EmailManager as the system provider, in Ben's wording and the patient's
 * language, and a refusal sends nothing. FAKE patient, clinic and addresses only.
 */
@Tag("unit")
@Tag("appointment")
@Tag("email")
@DisplayName("Email appointment reminder channel")
class EmailReminderChannelUnitTest {

    private static final int PATIENT = 42;
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 13, 9, 0);
    private static final ClaimedReminder REMINDER = new ClaimedReminder(7L, 101, PATIENT, START);
    private static final String SENDER = "fake-reminders@clinic.example.invalid";
    private static final String ADDRESS = "fake.patient@example.invalid";

    private final EmailManager emails = mock(EmailManager.class);
    private final EmailComposeManager compose = mock(EmailComposeManager.class);
    private final EmailConfigDao senders = mock(EmailConfigDao.class);
    private final DemographicManager demographics = mock(DemographicManager.class);
    private final ClinicDAO clinics = mock(ClinicDAO.class);
    private final ProviderDao providers = mock(ProviderDao.class);
    private final Demographic patient = new Demographic();
    private final Clinic clinic = new Clinic();
    private String configuredSender = SENDER;
    private EmailReminderChannel channel;

    @BeforeEach
    void setUp() {
        Provider system = new Provider();
        system.setProviderNo(Provider.SYSTEM_PROVIDER_NO);
        when(providers.getProvider(Provider.SYSTEM_PROVIDER_NO)).thenReturn(system);
        EmailConfig sender = mock(EmailConfig.class);
        when(sender.getId()).thenReturn(3);
        when(senders.findActiveEmailConfig(SENDER)).thenReturn(sender);
        when(emails.clinicFooterMissing()).thenReturn(false);
        patient.setOfficialLanguage("English");
        when(demographics.getDemographic(any(LoggedInInfo.class), eq(Integer.valueOf(PATIENT)))).thenReturn(patient);
        when(compose.getRecipients(any(LoggedInInfo.class), eq(Integer.valueOf(PATIENT))))
                .thenReturn(new List<?>[]{List.of(ADDRESS, "second@example.invalid"), List.of()});
        clinic.setClinicName("FAKE Maple Clinic");
        clinic.setClinicPhone("555-0100");
        when(clinics.getClinic()).thenReturn(clinic);
        channel = new EmailReminderChannel(emails, compose, senders, demographics, clinics, providers, () -> configuredSender);
    }

    private static EmailLog log(int id, EmailLog.EmailStatus status) {
        EmailLog log = new EmailLog();
        ReflectionTestUtils.setField(log, "id", id);
        log.setStatus(status);
        return log;
    }

    private EmailData sent() {
        ArgumentCaptor<EmailData> email = ArgumentCaptor.forClass(EmailData.class);
        verify(emails).sendEmailWithResult(any(LoggedInInfo.class), email.capture());
        return email.getValue();
    }

    @Test
    void shouldSendAsTheSystemProvider_toTheFirstValidAddressWithoutAChartNote() {
        when(emails.sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class)))
                .thenReturn(EmailSendResult.accepted(log(900, EmailLog.EmailStatus.SUCCESS), true));

        ReminderOutcome outcome = channel.send(REMINDER);

        assertThat(outcome).isEqualTo(new ReminderOutcome(AppointmentReminderStatus.SENT, 900));
        ArgumentCaptor<LoggedInInfo> user = ArgumentCaptor.forClass(LoggedInInfo.class);
        verify(emails).sendEmailWithResult(user.capture(), any(EmailData.class));
        assertThat(user.getValue().getLoggedInProviderNo()).isEqualTo(Provider.SYSTEM_PROVIDER_NO);
        EmailData email = sent();
        assertThat(email.getRecipients()).containsExactly(ADDRESS);
        assertThat(email.getSenderConfigId()).isEqualTo(3);
        assertThat(email.getTransactionType()).isEqualTo(EmailLog.TransactionType.APPOINTMENT_REMINDER);
        assertThat(email.getChartDisplayOption()).isEqualTo(EmailLog.ChartDisplayOption.WITHOUT_NOTE);
        assertThat(email.getIsEncrypted()).isFalse();
        assertThat(email.getDemographicNo()).isEqualTo(PATIENT);
        assertThat(email.getProviderNo()).isEqualTo(Provider.SYSTEM_PROVIDER_NO);
    }

    /** Ben's wording (D275), with only the clinic, the date and time, and the clinic phone filled in. */
    @Test
    void shouldWriteBensWording_withOnlyTheClinicDateTimeAndPhone() {
        when(emails.sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class)))
                .thenReturn(EmailSendResult.accepted(log(900, EmailLog.EmailStatus.SUCCESS), true));
        String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.ENGLISH).format(START);
        String time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.ENGLISH).format(START);

        channel.send(REMINDER);

        EmailData email = sent();
        assertThat(email.getSubject()).isEqualTo("Appointment reminder: FAKE Maple Clinic");
        assertThat(email.getBody()).isEqualTo("This is a reminder of your appointment at FAKE Maple Clinic on " + date
                + " at " + time + ". If you need to change or cancel it, please call 555-0100. Please do not reply to"
                + " this email.");
        assertThat(date).isEqualTo("November 13, 2026");
    }

    @Test
    void shouldWriteInFrench_forAFrenchSpeakingPatient() {
        patient.setOfficialLanguage("French");
        when(emails.sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class)))
                .thenReturn(EmailSendResult.accepted(log(900, EmailLog.EmailStatus.SUCCESS), true));

        channel.send(REMINDER);

        EmailData email = sent();
        assertThat(email.getSubject()).isEqualTo("Rappel de rendez-vous : FAKE Maple Clinic");
        assertThat(email.getBody()).startsWith("Ceci est un rappel de votre rendez-vous à FAKE Maple Clinic le 13 novembre 2026 à ")
                .contains("ou l'annuler, veuillez appeler le 555-0100.");
    }

    @Test
    void shouldSayContactTheClinic_whenTheClinicHasNoPhone() {
        clinic.setClinicPhone("  ");
        when(emails.sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class)))
                .thenReturn(EmailSendResult.accepted(log(900, EmailLog.EmailStatus.SUCCESS), true));

        channel.send(REMINDER);

        assertThat(sent().getBody()).contains("please contact the clinic.").doesNotContain("call");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt_BR"})
    void shouldHaveTheReminderTextInEveryBundle(String language) {
        Locale locale = Locale.forLanguageTag(language.replace('_', '-'));
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", locale);

        String[] withPhone = EmailReminderChannel.text(locale, "FAKE Maple Clinic", "555-0100", START);
        String[] withoutPhone = EmailReminderChannel.text(locale, "FAKE Maple Clinic", "", START);

        assertThat(bundle.getString(EmailReminderChannel.SUBJECT_KEY)).contains("{0}");
        assertThat(withPhone[0]).contains("FAKE Maple Clinic");
        assertThat(withPhone[1]).contains("FAKE Maple Clinic", "555-0100").doesNotContain("{", "}");
        assertThat(withoutPhone[1]).contains("FAKE Maple Clinic").doesNotContain("555-0100", "{", "}");
        if (!"en".equals(language)) {
            assertThat(withPhone[1]).as("a real translation").doesNotStartWith("This is a reminder");
        }
    }

    @Test
    void shouldRefuseWithoutSending_whenNoActiveSenderIsSetUp() {
        configuredSender = null;
        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.NO_SENDER);
        configuredSender = "fake-inactive@clinic.example.invalid";
        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.NO_SENDER);

        verify(emails, never()).sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class));
    }

    @Test
    void shouldRefuseWithoutSending_whenTheClinicFooterIsMissing() {
        when(emails.clinicFooterMissing()).thenReturn(true);

        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.NO_FOOTER);
        verify(emails, never()).sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class));
    }

    @Test
    void shouldRefuseWithoutSending_whenThePatientHasNoValidAddress() {
        when(compose.getRecipients(any(LoggedInInfo.class), eq(Integer.valueOf(PATIENT))))
                .thenReturn(new List<?>[]{List.of(), List.of()});
        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.NO_ADDRESS);

        when(compose.getRecipients(any(LoggedInInfo.class), eq(Integer.valueOf(PATIENT))))
                .thenReturn(new List<?>[]{List.of(), List.of("not-an-address")});
        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.INVALID_ADDRESS);

        verify(emails, never()).sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class));
    }

    /** Ben: explicit email opt-in only. The check runs before anything is logged or sent. */
    @Test
    void shouldRefuseWithoutSending_whenThePatientHasNotOptedIn() {
        when(emails.consentBlockMessage(any(LoggedInInfo.class), any(EmailData.class))).thenReturn("FAKE not opted in");

        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.BLOCKED_CONSENT);
        verify(emails, never()).sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class));
    }

    @Test
    void shouldRefuseWithoutSending_whenTheSystemProviderMayNotReadThePatient() {
        when(demographics.getDemographic(any(LoggedInInfo.class), eq(Integer.valueOf(PATIENT))))
                .thenThrow(new SecurityException("missing required sec object (_demographic)"));

        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.NOT_PERMITTED);
        verify(emails, never()).sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class));
    }

    @Test
    void shouldRefuseWithoutSending_whenTheClinicHasNoSystemProvider() {
        when(providers.getProvider(anyString())).thenReturn(null);

        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.NOT_PERMITTED);
        verify(emails, never()).sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class));
    }

    @Test
    void shouldMapEachSendResult_toTheReminderStatus() {
        when(emails.sendEmailWithResult(any(LoggedInInfo.class), any(EmailData.class)))
                .thenReturn(EmailSendResult.unconfirmed(log(901, EmailLog.EmailStatus.PENDING)))
                .thenReturn(EmailSendResult.failed(log(902, EmailLog.EmailStatus.BLOCKED), true))
                .thenReturn(EmailSendResult.failed(log(903, EmailLog.EmailStatus.FAILED), true));

        assertThat(channel.send(REMINDER)).isEqualTo(new ReminderOutcome(AppointmentReminderStatus.UNCONFIRMED, 901));
        assertThat(channel.send(REMINDER)).isEqualTo(new ReminderOutcome(AppointmentReminderStatus.BLOCKED_CONSENT, 902));
        assertThat(channel.send(REMINDER)).isEqualTo(new ReminderOutcome(AppointmentReminderStatus.FAILED, 903));
    }
}
