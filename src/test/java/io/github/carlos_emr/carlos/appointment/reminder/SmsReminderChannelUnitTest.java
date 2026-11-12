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
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
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
import java.time.Clock;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
    /** The job hands the text over 24 hours before the start. */
    private static final Clock HANDOVER = Clock.fixed(START.minusHours(24).atZone(ZONE).toInstant(), ZONE);
    private static final ClaimedReminder REMINDER = new ClaimedReminder(7L, 101, PATIENT, START, "999998");
    private static final String LONGEST_NAME = "FAKE Maple Creek Family Clinic";

    private final SmsQueueService queue = mock(SmsQueueService.class);
    private final SmsTransactionDao texts = mock(SmsTransactionDao.class);
    private final DemographicManager demographics = mock(DemographicManager.class);
    private final DemographicExtDao extras = mock(DemographicExtDao.class);
    private final ClinicDAO clinics = mock(ClinicDAO.class);
    private final ProviderDao providers = mock(ProviderDao.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
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
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq(SecurityInfoManager.READ),
                eq(PATIENT))).thenReturn(true);
        channel = channelAt(HANDOVER);
    }

    private SmsReminderChannel channelAt(Clock clock) {
        return new SmsReminderChannel(queue, texts, demographics, extras, clinics, providers, security,
                () -> switchedOn, () -> null, ReminderWindow.forSmsHoursBefore("24"), clock);
    }

    private SmsSendCommand handedOver() {
        ArgumentCaptor<SmsSendCommand> command = ArgumentCaptor.forClass(SmsSendCommand.class);
        verify(queue).enqueue(command.capture());
        return command.getValue();
    }

    @Test
    @DisplayName("should hand one reminder text to the SMS queue, for the cell number, never sent after 21:00 that night")
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
        // Handed over at 12:30 the day before: a text the queue holds up must not go out after 21:00 (#884 S1).
        assertThat(command.expiresAt()).isEqualTo(START.minusDays(1).withHour(21).withMinute(0).atZone(ZONE).toInstant());
        // No STOP invitation until STOP replies are handled.
        assertThat(command.body()).isEqualTo("Reminder: appointment at FAKE Maple Clinic on Wed Nov 18 at 12:30 PM. "
                + "To change or cancel, call (613) 555-0100.");
    }

    @Test
    @DisplayName("should keep the start minus 2 h as the limit for a text handed over the same morning")
    void shouldLimitTheText_toStartMinusTheCutoffOnTheSameMorning() {
        when(queue.enqueue(any())).thenReturn(SmsSendResultDto.queued(55L));

        channelAt(Clock.fixed(START.withHour(8).withMinute(30).atZone(ZONE).toInstant(), ZONE)).send(REMINDER);

        assertThat(handedOver().expiresAt()).isEqualTo(START.minusHours(2).atZone(ZONE).toInstant());
    }

    @Test
    @DisplayName("should invite a STOP reply only once STOP replies are handled")
    void shouldInviteAStopReply_onlyOnceStopRepliesAreHandled() {
        assertThat(SmsReminderChannel.STOP_REPLIES_HANDLED).isFalse();
        assertThat(SmsReminderChannel.text(Locale.ENGLISH, "FAKE Clinic", "555-0100", START, true))
                .endsWith("call 555-0100. Reply STOP to stop texts.");
        assertThat(SmsReminderChannel.text(Locale.FRENCH, "FAKE Clinic", "", START, true))
                .endsWith("joignez la clinique. Répondez STOP pour ne plus recevoir de textos.");
        assertThat(SmsReminderChannel.text(Locale.ENGLISH, "FAKE Clinic", "555-0100", START, false))
                .doesNotContain("STOP");
    }

    @Test
    @DisplayName("should write the text in French for a French-speaking patient")
    void shouldWriteTheText_inFrenchForAFrenchSpeakingPatient() {
        patient.setOfficialLanguage("French");
        when(queue.enqueue(any())).thenReturn(SmsSendResultDto.queued(55L));

        channel.send(REMINDER);

        assertThat(handedOver().body()).isEqualTo("Rappel : RDV à FAKE Maple Clinic le mer. 18/11 à 12 h 30. "
                + "Modifier ou annuler : (613) 555-0100.");
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
        // With the STOP sentence too, so the text still fits once STOP replies are handled.
        for (boolean stopLine : new boolean[]{false, true}) {
            for (Locale locale : List.of(Locale.ENGLISH, Locale.FRENCH)) {
                for (String phone : List.of("(613) 555-0100", "")) {
                    for (int day = 0; day < 365; day += 1) {
                        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0).plusDays(day)
                                .with(day % 2 == 0 ? LocalTime.of(12, 45) : LocalTime.of(23, 59));
                        String text = SmsReminderChannel.text(locale, LONGEST_NAME, phone, start, stopLine);
                        SmsSegments.Count count = SmsSegments.count(text);
                        assertThat(count.encoding()).as(text).isEqualTo(SmsSegments.Encoding.GSM_7);
                        assertThat(count.segments()).as(text).isEqualTo(1);
                    }
                }
            }
        }
        assertThat(LONGEST_NAME).hasSize(SmsReminderChannel.CLINIC_NAME_MAX);
    }

    @Test
    @DisplayName("should refuse, never cut, split or retry, a text the clinic's details make too long")
    void shouldFailATextTooLong_neverCutOrSplitIt() {
        patient.setOfficialLanguage("French");
        clinic.setClinicName(LONGEST_NAME);
        // Over 160 characters with the longest name: five numbers where one was expected.
        clinic.setClinicPhone("(613) 555-0100 ".repeat(5).trim());

        assertThat(channel.send(REMINDER)).isEqualTo(ReminderOutcome.because(AppointmentReminderStatus.TOO_LONG, "tooLong"));
        verify(queue, never()).enqueue(any());
    }

    /**
     * #884 (review): a database or lookup error before anything reaches the queue sent nothing. It is a failure, so its
     * one retry runs; never unconfirmed, which would say it may have gone, never retry it, and cover other bookings.
     */
    @Test
    @DisplayName("should record a failure, not an unconfirmed send, for an error before the queue")
    void shouldRecordAFailure_whenALookupFailsBeforeTheQueue() {
        when(extras.getValueForDemoKey(PATIENT, SmsReminderChannel.CELL_PHONE_KEY))
                .thenThrow(new IllegalStateException("FAKE database failure"));

        assertThat(channel.send(REMINDER)).isEqualTo(ReminderOutcome.because(AppointmentReminderStatus.FAILED,
                new IllegalStateException("FAKE database failure")));

        org.mockito.Mockito.reset(extras);
        when(extras.getValueForDemoKey(PATIENT, SmsReminderChannel.CELL_PHONE_KEY)).thenReturn("416-555-0142");
        when(clinics.getClinic()).thenThrow(new IllegalStateException("FAKE database failure"));

        assertThat(channel.send(REMINDER).status()).isEqualTo(AppointmentReminderStatus.FAILED);
        verify(queue, never()).enqueue(any());
    }

    @Test
    @DisplayName("should send other languages in English, since they would not fit one segment")
    void shouldSendOtherLanguages_inEnglish() {
        assertThat(SmsReminderChannel.text(Locale.of("es"), "FAKE Clinic", "555-0100", START, false)).startsWith("Reminder:");
        assertThat(SmsReminderChannel.text(Locale.of("pl"), "FAKE Clinic", "555-0100", START, false)).startsWith("Reminder:");
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
    @DisplayName("should record no access, not a failure, when the manager refuses the patient")
    void shouldRecordNoAccess_whenTheManagerRefusesThePatient() {
        // As DemographicManagerImpl.getDemographic does: the same privilege question, refused with a plain
        // RuntimeException, which on its own would read as a failure and be retried.
        when(security.hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq(SecurityInfoManager.READ),
                eq(PATIENT))).thenReturn(false);
        when(demographics.getDemographic(any(LoggedInInfo.class), eq(Integer.valueOf(PATIENT))))
                .thenThrow(new RuntimeException("missing required sec object (_demographic)"));

        assertThat(channel.send(REMINDER)).isEqualTo(
                ReminderOutcome.because(AppointmentReminderStatus.NOT_PERMITTED, "noPatientAccess"));
        verify(demographics, never()).getDemographic(any(LoggedInInfo.class), anyInt());
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
    @DisplayName("should try a text that never reached the queue once more, run only while on, and keep quiet hours")
    void shouldRetryOnceBeforeTheQueue_runOnlyWhileOn_andKeepQuietHours() {
        assertThat(channel.channel()).isEqualTo(ReminderChannel.SMS);
        assertThat(channel.maxAttempts()).isEqualTo(2);
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

    /** #884 S3: Group Update moves the reminder to a new number; its text keeps the number it was sent under. */
    @Test
    @DisplayName("should follow the text it handed over, after Group Update moved the reminder to a new number")
    void shouldFollowTheHandedOverText_afterGroupUpdateMovedTheReminder() {
        AppointmentReminder reminder = AppointmentReminder.requested(101, ReminderChannel.SMS, "999998", new Date());
        reminder.startSending(new Date(), new Date());
        reminder.finish(AppointmentReminderStatus.QUEUED, null, 55L, new Date());
        reminder.moveTo(201, "999998", new Date());
        SmsTransaction handedOver = text(SmsStatus.QUEUED, null);
        ReflectionTestUtils.setField(handedOver, "appointmentNo", 101);
        when(texts.find((Object) 55L)).thenReturn(handedOver);

        assertThat(statusFor(reminder, text(SmsStatus.SENT, null))).isEqualTo(AppointmentReminderStatus.SENT);
        verify(texts, never()).findLatestOutboundByAppointment(201, SmsMessagePurpose.APPOINTMENT_REMINDER);

        // A text row without a number, or one no longer found, falls back to the reminder's own number.
        ReflectionTestUtils.setField(handedOver, "appointmentNo", null);
        when(texts.findLatestOutboundByAppointment(201, SmsMessagePurpose.APPOINTMENT_REMINDER))
                .thenReturn(Optional.of(text(SmsStatus.FAILED, "QUEUE_EXPIRED")));
        assertThat(channel.currentStatus(reminder)).isEqualTo(AppointmentReminderStatus.EXPIRED);
        when(texts.find((Object) 55L)).thenReturn(null);
        assertThat(channel.currentStatus(reminder)).isEqualTo(AppointmentReminderStatus.EXPIRED);
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
