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
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.exception.PatientDirectiveException;
import io.github.carlos_emr.carlos.commn.model.AppointmentReminder;
import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.sms.SmsMessagePurpose;
import io.github.carlos_emr.carlos.sms.SmsRecipientPhoneType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.service.SmsQueueService;
import io.github.carlos_emr.carlos.sms.service.SmsSendService;
import io.github.carlos_emr.carlos.sms.support.SmsSegments;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #884: the SMS reminder channel hands one text, in the patient's language and one GSM-7 segment, to the SMS queue.
 * FAKE patients, numbers and clinic only.
 */
@Tag("unit")
@Tag("appointment")
@DisplayName("SMS appointment reminder channel")
class SmsReminderChannelUnitTest {

    private static final int PATIENT = 42;
    private static final ZoneId ZONE = ZoneId.of("America/Toronto");
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 18, 12, 30);
    private static final ClaimedReminder REMINDER = new ClaimedReminder(7L, 101, PATIENT, START, "999998");
    private static final String LONGEST_NAME = "FAKE Maple Creek Family Clinic";

    private final SmsQueueService queue = mock(SmsQueueService.class);
    private final SmsTransactionDao texts = mock(SmsTransactionDao.class);
    private final DemographicManager demographics = mock(DemographicManager.class);
    private final DemographicExtDao extras = mock(DemographicExtDao.class);
    private final ClinicDAO clinics = mock(ClinicDAO.class);
    private final ProviderDao providers = mock(ProviderDao.class);
    private final Demographic patient = new Demographic();
    private final Clinic clinic = new Clinic();
    private boolean switchedOn = true;
    private SmsReminderChannel channel;

    @BeforeEach
    void setUp() {
        Provider system = new Provider();
        system.setProviderNo(Provider.SYSTEM_PROVIDER_NO);
        when(providers.getProvider(Provider.SYSTEM_PROVIDER_NO)).thenReturn(system);
        patient.setOfficialLanguage("English");
        when(demographics.getDemographic(any(LoggedInInfo.class), eq(Integer.valueOf(PATIENT)))).thenReturn(patient);
        when(extras.getValueForDemoKey(PATIENT, SmsReminderChannel.CELL_PHONE_KEY)).thenReturn("416-555-0142*");
        clinic.setClinicName("FAKE Maple Clinic");
        clinic.setClinicPhone("(613) 555-0100");
        when(clinics.getClinic()).thenReturn(clinic);
        channel = new SmsReminderChannel(queue, texts, demographics, extras, clinics, providers, () -> switchedOn,
                () -> null, ReminderWindow.forSmsHoursBefore("24"), ZONE);
    }

    private SmsSendCommand handedOver() {
        ArgumentCaptor<SmsSendCommand> command = ArgumentCaptor.forClass(SmsSendCommand.class);
        verify(queue).enqueue(command.capture());
        return command.getValue();
    }

    @Test
    @DisplayName("should hand one reminder text to the SMS queue, for the cell number, never after start minus 2 h")
    void shouldQueueOneReminderText_forTheCellNumberWithItsTimeLimit() {
        when(queue.enqueue(any())).thenReturn(SmsSendResultDto.queued(55L));

        ReminderOutcome outcome = channel.send(REMINDER);

        assertThat(outcome).isEqualTo(ReminderOutcome.sms(AppointmentReminderStatus.QUEUED, 55L, null));
        SmsSendCommand command = handedOver();
        assertThat(command.messagePurpose()).isEqualTo(SmsMessagePurpose.APPOINTMENT_REMINDER);
        assertThat(command.recipientPhoneType()).isEqualTo(SmsRecipientPhoneType.CELL);
        assertThat(command.recipientPhoneNumber()).as("the preferred-number star is dropped").isEqualTo("416-555-0142");
        assertThat(command.demographicNo()).isEqualTo(PATIENT);
        assertThat(command.appointmentNo()).isEqualTo(101);
        assertThat(command.requestedByHealthcareProviderNo()).isEqualTo("999998");
        assertThat(command.expiresAt()).isEqualTo(START.minusHours(2).atZone(ZONE).toInstant());
        assertThat(command.body()).isEqualTo("Reminder: appointment at FAKE Maple Clinic on Wed Nov 18 at 12:30 PM. "
                + "To change or cancel, call (613) 555-0100. Reply STOP to stop texts.");
    }

    @Test
    @DisplayName("should write the text in French for a French-speaking patient")
    void shouldWriteTheText_inFrenchForAFrenchSpeakingPatient() {
        patient.setOfficialLanguage("French");
        when(queue.enqueue(any())).thenReturn(SmsSendResultDto.queued(55L));

        channel.send(REMINDER);

        assertThat(handedOver().body()).isEqualTo("Rappel : RDV à FAKE Maple Clinic le mer. 18/11 à 12 h 30. "
                + "Modifier ou annuler : (613) 555-0100. Répondez STOP pour ne plus recevoir de textos.");
    }

    @Test
    @DisplayName("should say to contact the clinic when it has no phone number")
    void shouldSayToContactTheClinic_whenItHasNoPhoneNumber() {
        clinic.setClinicPhone(" ");
        when(queue.enqueue(any())).thenReturn(SmsSendResultDto.queued(55L));

        channel.send(REMINDER);

        assertThat(handedOver().body()).contains("To change or cancel, contact the clinic.").doesNotContain("call");
    }

    @Test
    @DisplayName("should fit one GSM-7 segment in English and French, for every weekday, month and time")
    void shouldFitOneGsmSegment_forEveryWeekdayMonthAndTime() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.FRENCH)) {
            for (String phone : List.of("(613) 555-0100", "")) {
                for (int day = 0; day < 365; day += 1) {
                    LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0).plusDays(day)
                            .with(day % 2 == 0 ? LocalTime.of(12, 45) : LocalTime.of(23, 59));
                    String text = SmsReminderChannel.text(locale, LONGEST_NAME, phone, start);
                    SmsSegments.Count count = SmsSegments.count(text);
                    assertThat(count.encoding()).as(text).isEqualTo(SmsSegments.Encoding.GSM_7);
                    assertThat(count.segments()).as(text).isEqualTo(1);
                }
            }
        }
        assertThat(LONGEST_NAME).hasSize(SmsReminderChannel.CLINIC_NAME_MAX);
    }

    @Test
    @DisplayName("should fail, never cut or split, a text the clinic's details make too long")
    void shouldFailATextTooLong_neverCutOrSplitIt() {
        patient.setOfficialLanguage("French");
        clinic.setClinicName(LONGEST_NAME);
        clinic.setClinicPhone("(613) 555-0100 poste 12345");

        assertThat(channel.send(REMINDER)).isEqualTo(ReminderOutcome.because(AppointmentReminderStatus.FAILED, "tooLong"));
        verify(queue, never()).enqueue(any());
    }

    @Test
    @DisplayName("should send other languages in English, since they would not fit one segment")
    void shouldSendOtherLanguages_inEnglish() {
        assertThat(SmsReminderChannel.text(Locale.of("es"), "FAKE Clinic", "555-0100", START)).startsWith("Reminder:");
        assertThat(SmsReminderChannel.text(Locale.of("pl"), "FAKE Clinic", "555-0100", START)).startsWith("Reminder:");
    }

    @Test
    @DisplayName("should use the clinic's short name setting, fold accents, and cut at a word")
    void shouldMakeTheClinicName_shortAndGsm7() {
        assertThat(SmsReminderChannel.clinicName("FAKE Short", "FAKE Very Long Official Name")).isEqualTo("FAKE Short");
        assertThat(SmsReminderChannel.clinicName(null, "Clinique FAKE de la Côte")).isEqualTo("Clinique FAKE de la Cote");
        assertThat(SmsReminderChannel.clinicName(null, "FAKE Clinic ✚")).isEqualTo("FAKE Clinic");
        assertThat(SmsReminderChannel.clinicName(null, "FAKE Maple Creek Family Medicine Clinic"))
                .isEqualTo("FAKE Maple Creek Family");
        assertThat(SmsReminderChannel.clinicName("  ", null)).isEmpty();
    }

    @Test
    @DisplayName("should drop OSCAR's preferred-number stars from a cell number")
    void shouldDropPreferredNumberStars_fromACellNumber() {
        assertThat(SmsReminderChannel.cellNumber(" 416-555-0142 * ")).isEqualTo("416-555-0142");
        assertThat(SmsReminderChannel.cellNumber(null)).isEmpty();
    }

    @Test
    @DisplayName("should refuse without a cell number, or with one that is not a phone number")
    void shouldRefuse_withoutAUsableCellNumber() {
        when(extras.getValueForDemoKey(PATIENT, SmsReminderChannel.CELL_PHONE_KEY)).thenReturn(null);
        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.NO_ADDRESS);

        when(extras.getValueForDemoKey(PATIENT, SmsReminderChannel.CELL_PHONE_KEY)).thenReturn("FAKE-not-a-number");
        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.INVALID_ADDRESS);

        verify(queue, never()).enqueue(any());
    }

    @Test
    @DisplayName("should refuse when the system account may not read the patient, or is missing")
    void shouldRefuse_whenTheSystemAccountMayNotReadThePatient() {
        when(demographics.getDemographic(any(LoggedInInfo.class), eq(Integer.valueOf(PATIENT))))
                .thenThrow(new PatientDirectiveException("FAKE directive"));
        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.NOT_PERMITTED);

        when(providers.getProvider(Provider.SYSTEM_PROVIDER_NO)).thenReturn(null);
        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.NOT_PERMITTED);
        verify(queue, never()).enqueue(any());
    }

    @Test
    @DisplayName("should record the queue's refusals as what they are")
    void shouldRecordTheQueuesRefusals_asWhatTheyAre() {
        when(queue.enqueue(any())).thenReturn(SmsSendResultDto.consentBlocked(SmsConsentDecisionDto.blocked(
                SmsStatus.OPTOUT_BLOCKED, "SMS_CONSENT_OPTED_OUT", "FAKE opted out"), 56L));
        assertThat(channel.send(REMINDER))
                .isEqualTo(ReminderOutcome.sms(AppointmentReminderStatus.BLOCKED_CONSENT, 56L, "OPTOUT_BLOCKED"));

        when(queue.enqueue(any())).thenReturn(SmsSendResultDto.validationFailed(List.of(SmsSendService.SMS_TURNED_OFF_MESSAGE)));
        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.NO_SENDER);

        when(queue.enqueue(any())).thenReturn(SmsSendResultDto.validationFailed(
                List.of(SmsSendService.SMS_PROVIDER_NOT_READY_MESSAGE)));
        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.NO_SENDER);

        when(queue.enqueue(any())).thenReturn(SmsSendResultDto.validationFailed(List.of(SmsSendService.SMS_EXPIRED_MESSAGE)));
        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.EXPIRED);

        when(queue.enqueue(any())).thenReturn(SmsSendResultDto.validationFailed(List.of("FAKE other refusal")));
        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.FAILED);

        when(queue.enqueue(any())).thenThrow(new IllegalStateException("FAKE consent tables down"));
        assertThat(channel.send(REMINDER))
                .isEqualTo(ReminderOutcome.because(AppointmentReminderStatus.FAILED, "IllegalStateException"));
    }

    @Test
    @DisplayName("should never retry, run only while switched on, and keep quiet hours")
    void shouldNeverRetry_runOnlyWhileOn_andKeepQuietHours() {
        assertThat(channel.channel()).isEqualTo(ReminderChannel.SMS);
        assertThat(channel.maxAttempts()).isEqualTo(1);
        assertThat(channel.enabled()).isTrue();
        switchedOn = false;
        assertThat(channel.enabled()).isFalse();
        assertThat(channel.window()).get().satisfies(window -> {
            assertThat(window.isQuiet(LocalTime.of(22, 0))).isTrue();
            assertThat(window.isQuiet(LocalTime.of(9, 0))).isFalse();
            assertThat(window.cutoff()).isEqualTo(Duration.ofHours(2));
        });
    }

    @Test
    @DisplayName("should read where a queued text got to, for the status line")
    void shouldReadWhereAQueuedTextGotTo_forTheStatusLine() {
        AppointmentReminder reminder = AppointmentReminder.requested(101, ReminderChannel.SMS, "999998", new Date());
        reminder.startSending(new Date(), new Date());
        reminder.finish(AppointmentReminderStatus.QUEUED, null, 55L, new Date());

        assertThat(statusFor(reminder, null)).isEqualTo(AppointmentReminderStatus.QUEUED);
        assertThat(statusFor(reminder, text(SmsStatus.QUEUED, null))).isEqualTo(AppointmentReminderStatus.QUEUED);
        assertThat(statusFor(reminder, text(SmsStatus.SENT, null))).isEqualTo(AppointmentReminderStatus.SENT);
        assertThat(statusFor(reminder, text(SmsStatus.DELIVERED, null))).isEqualTo(AppointmentReminderStatus.SENT);
        assertThat(statusFor(reminder, text(SmsStatus.OPTOUT_BLOCKED, null))).isEqualTo(AppointmentReminderStatus.BLOCKED_CONSENT);
        assertThat(statusFor(reminder, text(SmsStatus.FAILED, "QUEUE_EXPIRED"))).isEqualTo(AppointmentReminderStatus.EXPIRED);
        assertThat(statusFor(reminder, text(SmsStatus.FAILED, "INVALID_RECIPIENT"))).isEqualTo(AppointmentReminderStatus.FAILED);

        // A reminder that never reached the queue keeps its own outcome.
        reminder.finish(AppointmentReminderStatus.NO_ADDRESS, null, null, new Date());
        assertThat(channel.currentStatus(reminder)).isEqualTo(AppointmentReminderStatus.NO_ADDRESS);
    }

    private AppointmentReminderStatus statusFor(AppointmentReminder reminder, SmsTransaction text) {
        when(texts.findLatestOutboundByAppointment(101, SmsMessagePurpose.APPOINTMENT_REMINDER))
                .thenReturn(Optional.ofNullable(text));
        return channel.currentStatus(reminder);
    }

    private static SmsTransaction text(SmsStatus status, String errorCode) {
        SmsTransaction text = new SmsTransaction();
        ReflectionTestUtils.setField(text, "status", status);
        ReflectionTestUtils.setField(text, "errorCode", errorCode);
        return text;
    }
}
