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

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * #5887: the Email Reminder box on Add/Edit Appointment. A form that did not show the box never changes the
 * stored choice, so an unticked box can be told from a missing one.
 */
@Tag("unit")
@Tag("appointment")
@DisplayName("Email Reminder box on Add/Edit Appointment")
class AppointmentReminderChoiceUnitTest extends CarlosUnitTestBase {

    private final LoggedInInfo user = mock(LoggedInInfo.class);

    private static MockHttpServletRequest form(boolean shown, boolean ticked) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/appointment/UpdateRecord");
        if (shown) {
            request.setParameter(AppointmentReminderChoice.SHOWN_PARAMETER, "1");
        }
        if (ticked) {
            request.setParameter(AppointmentReminderChoice.PARAMETER, "email reminder");
        }
        return request;
    }

    private static Appointment appointment(Integer id) {
        Appointment appointment = new Appointment();
        appointment.setId(id);
        appointment.setDemographicNo(42);
        return appointment;
    }

    @Test
    void shouldReadTheBox_onlyFromAFormThatShowedIt() {
        assertThat(AppointmentReminderChoice.isOnForm(form(true, false))).isTrue();
        assertThat(AppointmentReminderChoice.isOnForm(form(false, true))).isFalse();
        assertThat(AppointmentReminderChoice.isOnForm(null)).isFalse();
        assertThat(AppointmentReminderChoice.isTicked(form(true, true))).isTrue();
        assertThat(AppointmentReminderChoice.isTicked(form(true, false))).isFalse();
    }

    @Test
    void shouldRecordTickedAndUnticked_whenTheFormShowedTheBox() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);

        AppointmentReminderChoice.record(form(true, true), user, appointment(101));
        AppointmentReminderChoice.record(form(true, false), user, appointment(102));

        verify(reminders).recordRequest(user, 101, 42, ReminderChannel.EMAIL, true);
        verify(reminders).recordRequest(user, 102, 42, ReminderChannel.EMAIL, false);
    }

    @Test
    void shouldLeaveTheStoredChoiceAlone_whenTheFormHadNoBox() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);

        AppointmentReminderChoice.record(form(false, false), user, appointment(101));
        AppointmentReminderChoice.record(form(false, true), user, appointment(101));

        verifyNoInteractions(reminders);
    }

    @Test
    void shouldNotLookUpTheService_forAFormWithoutTheBox() {
        // Nothing is registered: a lookup would fail, so other callers of the actions are unaffected.
        assertThatCode(() -> AppointmentReminderChoice.record(form(false, true), user, appointment(101)))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRecordNothing_beforeTheAppointmentHasANumber() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);

        AppointmentReminderChoice.record(form(true, true), user, appointment(null));
        AppointmentReminderChoice.record(form(true, true), user, null);

        verifyNoInteractions(reminders);
    }

    @Test
    void shouldReadTheBox_fromAFormCopiedIntoAMap() {
        assertThat(AppointmentReminderChoice.isOnCopiedForm(java.util.Map.of(AppointmentReminderChoice.SHOWN_PARAMETER, "1"))).isTrue();
        assertThat(AppointmentReminderChoice.isOnCopiedForm(java.util.Map.of(AppointmentReminderChoice.PARAMETER, "on"))).isFalse();
        assertThat(AppointmentReminderChoice.isOnCopiedForm(null)).isFalse();
        assertThat(AppointmentReminderChoice.isTickedOnCopiedForm(java.util.Map.of(AppointmentReminderChoice.PARAMETER, "on"))).isTrue();
        assertThat(AppointmentReminderChoice.isTickedOnCopiedForm(java.util.Map.of(AppointmentReminderChoice.SHOWN_PARAMETER, "1"))).isFalse();
    }

    private static Appointment booking(int id, String providerNo, int patient) {
        Appointment booking = appointment(id);
        booking.setProviderNo(providerNo);
        booking.setDemographicNo(patient);
        return booking;
    }

    /** #5887: Group Appt books the patient with several providers at once; one booking gets the reminder. */
    @Test
    void shouldRecordTheGroupsBoxOnce_onTheFormProvidersBookingWithThePatient() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);
        var bookings = java.util.List.of(booking(201, "100001", 42), booking(202, "999998", 42), booking(203, "100002", 0));

        AppointmentReminderChoice.recordForGroup(form(true, true), user, bookings, "999998");

        verify(reminders).recordRequest(user, 202, 42, ReminderChannel.EMAIL, true);
        verify(reminders, org.mockito.Mockito.times(1)).recordRequest(any(), anyInt(), anyInt(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void shouldPickTheFirstBookingWithThePatient_whenTheFormProviderHasNone() {
        var bookings = java.util.List.of(booking(201, "999998", 0), booking(202, "100001", 42), booking(203, "100002", 42));

        assertThat(AppointmentReminderChoice.patientBooking(bookings, "999998").getId()).isEqualTo(202);
        assertThat(AppointmentReminderChoice.patientBooking(java.util.List.of(booking(201, "999998", 0)), "999998")).isNull();
        assertThat(AppointmentReminderChoice.patientBooking(java.util.List.of(), null)).isNull();
    }

    @Test
    void shouldLeaveGroupBookingsAlone_whenTheFormHadNoBox() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);

        AppointmentReminderChoice.recordForGroup(form(false, true), user, java.util.List.of(booking(201, "999998", 42)), "999998");
        AppointmentReminderChoice.moveReminders(form(false, true), user, 101, appointment(201));

        verifyNoInteractions(reminders);
    }

    /** Group Update books the appointment again under a new number; its reminder goes with it. */
    @Test
    void shouldMoveTheReminders_toTheRebookedAppointment() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);

        AppointmentReminderChoice.moveReminders(form(true, false), user, 101, appointment(201));
        AppointmentReminderChoice.moveReminders(form(true, false), user, null, appointment(202));
        AppointmentReminderChoice.moveReminders(form(true, false), user, 103, appointment(null));

        verify(reminders).moveReminders(user, 101, 201);
        verify(reminders, org.mockito.Mockito.times(1)).moveReminders(any(), anyInt(), anyInt());
    }

    /**
     * The group page has no transaction around its bookings: each one is already saved when the reminder is
     * recorded. A reminder that cannot be stored must not stop the page halfway or show an error page that invites
     * a second submit (and a duplicate booking); Edit then shows the box unticked, so staff can tick it again.
     */
    @Test
    void shouldNotStopTheGroupPage_whenTheReminderCannotBeStored() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);
        when(reminders.recordRequest(any(), anyInt(), anyInt(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenThrow(new IllegalStateException("FAKE"));
        when(reminders.moveReminders(any(), anyInt(), anyInt())).thenThrow(new IllegalStateException("FAKE"));

        assertThatCode(() -> AppointmentReminderChoice.recordForGroup(form(true, true), user,
                java.util.List.of(booking(201, "999998", 42)), "999998")).doesNotThrowAnyException();
        assertThatCode(() -> AppointmentReminderChoice.moveReminders(form(true, true), user, 101, appointment(201)))
                .doesNotThrowAnyException();
        assertThatCode(() -> AppointmentReminderChoice.recordAfterGroupUpdate(form(true, true), user, appointment(202)))
                .doesNotThrowAnyException();

        verify(reminders).recordRequest(user, 202, 42, ReminderChannel.EMAIL, true);
    }

    /** The failure is not silent: the group page's result says the reminder was not recorded. */
    @Test
    void shouldMarkTheRequestForAWarning_whenAReminderCallOnTheGroupPageFails() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);
        MockHttpServletRequest stored = form(true, true);
        MockHttpServletRequest failed = form(true, true);

        AppointmentReminderChoice.recordAfterGroupUpdate(stored, user, appointment(202));
        assertThat(AppointmentReminderChoice.notRecorded(stored)).as("stored: no warning").isFalse();

        when(reminders.moveReminders(any(), anyInt(), anyInt())).thenThrow(new IllegalStateException("FAKE"));
        AppointmentReminderChoice.moveReminders(failed, user, 101, appointment(201));
        assertThat(AppointmentReminderChoice.notRecorded(failed)).isTrue();
        assertThat(AppointmentReminderChoice.notRecorded(null)).isFalse();
    }

    @Test
    void shouldAskTheStoredChoice_forTheEditPage() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);
        when(reminders.isRequested(101, ReminderChannel.EMAIL)).thenReturn(true);

        assertThat(AppointmentReminderChoice.isRequested(101)).isTrue();
        assertThat(AppointmentReminderChoice.isRequested(102)).isFalse();
        assertThat(AppointmentReminderChoice.isRequested(null)).isFalse();
        assertThat(AppointmentReminderChoice.isRequested(0)).isFalse();
    }

    @Test
    void shouldShowTheStatusLine_toWhoeverMayOpenThisPatientsAppointment() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        ReminderStatusLine line = new ReminderStatusLine("appointment.reminder.status.due", "");
        when(reminders.statusLine(any(Appointment.class), org.mockito.ArgumentMatchers.eq(ReminderChannel.EMAIL),
                any(Locale.class))).thenReturn(Optional.of(line));
        // A front-desk role: the appointment and the patient's record, but no right on their email.
        when(security.isAllowedAccessToPatientRecord(user, 42)).thenReturn(true);
        when(security.hasPrivilege(user, "_demographic", SecurityInfoManager.READ, 42)).thenReturn(true);
        when(security.hasPrivilege(user, "_appointment", SecurityInfoManager.WRITE, 42)).thenReturn(true);
        when(security.hasPrivilege(user, "_email", SecurityInfoManager.READ, 42)).thenReturn(false);

        assertThat(AppointmentReminderChoice.statusLine(user, appointment(101), Locale.ENGLISH)).isEqualTo(line);
    }

    @Test
    void shouldHideTheStatusLine_fromAUserWhoMayNotOpenThisPatientsAppointment() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(reminders.statusLine(any(Appointment.class), any(ReminderChannel.class), any(Locale.class)))
                .thenReturn(Optional.of(new ReminderStatusLine("appointment.reminder.status.due", "")));
        when(security.isAllowedAccessToPatientRecord(user, 42)).thenReturn(true);
        when(security.hasPrivilege(user, "_demographic", SecurityInfoManager.READ, 42)).thenReturn(true);
        // Email or text rights do not stand in for the appointment's.
        when(security.hasPrivilege(user, "_email", SecurityInfoManager.READ, 42)).thenReturn(true);
        when(security.hasPrivilege(user, "_sms", SecurityInfoManager.READ, 42)).thenReturn(true);

        assertThat(AppointmentReminderChoice.statusLine(user, appointment(101), Locale.ENGLISH))
                .as("no _appointment w for this patient").isNull();
        assertThat(AppointmentReminderChoice.statusLine(user, appointment(101), ReminderChannel.SMS, Locale.ENGLISH))
                .as("no _appointment w for this patient: no text line either").isNull();

        when(security.hasPrivilege(user, "_appointment", SecurityInfoManager.WRITE, 42)).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 42)).thenReturn(false);
        assertThat(AppointmentReminderChoice.statusLine(user, appointment(101), Locale.ENGLISH))
                .as("the patient's record is locked to this user").isNull();

        when(security.isAllowedAccessToPatientRecord(user, 42)).thenReturn(true);
        when(security.hasPrivilege(user, "_demographic", SecurityInfoManager.READ, 42)).thenReturn(false);
        assertThat(AppointmentReminderChoice.statusLine(user, appointment(101), Locale.ENGLISH)).isNull();

        assertThat(AppointmentReminderChoice.statusLine(null, appointment(101), Locale.ENGLISH)).isNull();
    }

    @Test
    void shouldNotLookUpTheStatus_forAnAppointmentWithoutANumberOrAPatient() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        Appointment noPatient = appointment(101);
        noPatient.setDemographicNo(0);

        assertThat(AppointmentReminderChoice.statusLine(user, appointment(null), Locale.ENGLISH)).isNull();
        assertThat(AppointmentReminderChoice.statusLine(user, noPatient, Locale.ENGLISH)).isNull();
        assertThat(AppointmentReminderChoice.statusLine(user, null, Locale.ENGLISH)).isNull();

        verify(security, never()).hasPrivilege(any(), anyString(), anyString(), anyInt());
        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
        verify(reminders, never()).statusLine(any(), any());
        verify(reminders, never()).statusLine(any(), any(), any());
    }

    // #884: the Text Reminder box sits beside the email one and is read, stored and shown on its own.

    private static MockHttpServletRequest boxes(boolean emailShown, boolean emailTicked, boolean smsShown,
                                                boolean smsTicked) {
        MockHttpServletRequest request = form(emailShown, emailTicked);
        if (smsShown) {
            request.setParameter(AppointmentReminderChoice.SMS_SHOWN_PARAMETER, "1");
        }
        if (smsTicked) {
            request.setParameter(AppointmentReminderChoice.SMS_PARAMETER, "text reminder");
        }
        return request;
    }

    @Test
    void shouldReadEachBox_onlyUnderItsOwnName() {
        MockHttpServletRequest emailOnly = boxes(true, true, false, false);
        MockHttpServletRequest smsOnly = boxes(false, false, true, true);

        assertThat(AppointmentReminderChoice.isOnForm(smsOnly, ReminderChannel.SMS)).isTrue();
        assertThat(AppointmentReminderChoice.isTicked(smsOnly, ReminderChannel.SMS)).isTrue();
        assertThat(AppointmentReminderChoice.isOnForm(smsOnly)).isFalse();
        assertThat(AppointmentReminderChoice.isTicked(smsOnly)).isFalse();
        assertThat(AppointmentReminderChoice.isOnForm(emailOnly, ReminderChannel.SMS)).isFalse();
        assertThat(AppointmentReminderChoice.isTicked(emailOnly, ReminderChannel.SMS)).isFalse();
        assertThat(AppointmentReminderChoice.isOnForm(null, ReminderChannel.SMS)).isFalse();
        assertThat(AppointmentReminderChoice.isTicked(null, ReminderChannel.SMS)).isFalse();
        assertThat(AppointmentReminderChoice.isAnyOnForm(smsOnly)).isTrue();
        assertThat(AppointmentReminderChoice.isAnyOnForm(boxes(false, true, false, true))).isFalse();
    }

    @Test
    void shouldRecordEachBoxOnItsOwnChannel_whenTheFormShowedBoth() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);

        AppointmentReminderChoice.record(boxes(true, false, true, true), user, appointment(101));

        verify(reminders).recordRequest(user, 101, 42, ReminderChannel.EMAIL, false);
        verify(reminders).recordRequest(user, 101, 42, ReminderChannel.SMS, true);
    }

    @Test
    void shouldLeaveTheEmailChoiceAlone_whenOnlyTheTextBoxWasShown() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);

        AppointmentReminderChoice.record(boxes(false, true, true, false), user, appointment(101));

        verify(reminders).recordRequest(user, 101, 42, ReminderChannel.SMS, false);
        verify(reminders, never()).recordRequest(any(), anyInt(), anyInt(),
                org.mockito.ArgumentMatchers.eq(ReminderChannel.EMAIL), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void shouldLeaveTheTextChoiceAlone_whenOnlyTheEmailBoxWasShown() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);

        AppointmentReminderChoice.record(boxes(true, true, false, true), user, appointment(101));

        verify(reminders).recordRequest(user, 101, 42, ReminderChannel.EMAIL, true);
        verify(reminders, never()).recordRequest(any(), anyInt(), anyInt(),
                org.mockito.ArgumentMatchers.eq(ReminderChannel.SMS), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void shouldReadTheTextBox_fromAFormCopiedIntoAMap() {
        var copied = java.util.Map.of(AppointmentReminderChoice.SMS_SHOWN_PARAMETER, "1",
                AppointmentReminderChoice.SMS_PARAMETER, "on", AppointmentReminderChoice.SHOWN_PARAMETER, "1");

        assertThat(AppointmentReminderChoice.isOnCopiedForm(copied, ReminderChannel.SMS)).isTrue();
        assertThat(AppointmentReminderChoice.isTickedOnCopiedForm(copied, ReminderChannel.SMS)).isTrue();
        assertThat(AppointmentReminderChoice.isOnCopiedForm(copied, ReminderChannel.EMAIL)).isTrue();
        assertThat(AppointmentReminderChoice.isTickedOnCopiedForm(copied, ReminderChannel.EMAIL)).isFalse();
        assertThat(AppointmentReminderChoice.isOnCopiedForm(java.util.Map.of(AppointmentReminderChoice.SHOWN_PARAMETER, "1"),
                ReminderChannel.SMS)).isFalse();
        assertThat(AppointmentReminderChoice.isOnCopiedForm(null, ReminderChannel.SMS)).isFalse();
        assertThat(AppointmentReminderChoice.isTickedOnCopiedForm(null, ReminderChannel.SMS)).isFalse();
    }

    @Test
    void shouldRecordAndMoveGroupReminders_whenOnlyTheTextBoxWasShown() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);
        MockHttpServletRequest smsOnly = boxes(false, false, true, true);

        AppointmentReminderChoice.recordForGroup(smsOnly, user, java.util.List.of(booking(201, "999998", 42)), "999998");
        AppointmentReminderChoice.moveReminders(smsOnly, user, 101, appointment(202));

        verify(reminders).recordRequest(user, 201, 42, ReminderChannel.SMS, true);
        verify(reminders).moveReminders(user, 101, 202);
    }

    @Test
    void shouldAskTheStoredTextChoice_forTheEditPage() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);
        when(reminders.isRequested(101, ReminderChannel.SMS)).thenReturn(true);

        assertThat(AppointmentReminderChoice.isRequested(101, ReminderChannel.SMS)).isTrue();
        assertThat(AppointmentReminderChoice.isRequested(101)).isFalse();
        assertThat(AppointmentReminderChoice.isRequested(null, ReminderChannel.SMS)).isFalse();
        assertThat(AppointmentReminderChoice.isRequested(0, ReminderChannel.SMS)).isFalse();
    }

    /** #884 (the owner's decision): the text line, too, shows to whoever may open the appointment, without _sms. */
    @Test
    void shouldShowTheTextStatusLine_toWhoeverMayOpenThisPatientsAppointment() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        ReminderStatusLine line = new ReminderStatusLine("appointment.reminder.sms.status.due", "");
        when(reminders.statusLine(any(Appointment.class), org.mockito.ArgumentMatchers.eq(ReminderChannel.SMS),
                any(Locale.class))).thenReturn(Optional.of(line));
        // A front-desk role: the appointment and the patient's record, but no right on their texts or email.
        when(security.isAllowedAccessToPatientRecord(user, 42)).thenReturn(true);
        when(security.hasPrivilege(user, "_demographic", SecurityInfoManager.READ, 42)).thenReturn(true);
        when(security.hasPrivilege(user, "_appointment", SecurityInfoManager.WRITE, 42)).thenReturn(true);
        when(security.hasPrivilege(user, "_sms", SecurityInfoManager.READ, 42)).thenReturn(false);
        when(security.hasPrivilege(user, "_email", SecurityInfoManager.READ, 42)).thenReturn(false);

        assertThat(AppointmentReminderChoice.statusLine(user, appointment(101), ReminderChannel.SMS, Locale.ENGLISH))
                .isEqualTo(line);
    }
}
