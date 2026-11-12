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
    void shouldAskTheStoredChoice_forTheEditPage() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);
        when(reminders.isRequested(101, ReminderChannel.EMAIL)).thenReturn(true);

        assertThat(AppointmentReminderChoice.isRequested(101)).isTrue();
        assertThat(AppointmentReminderChoice.isRequested(102)).isFalse();
        assertThat(AppointmentReminderChoice.isRequested(null)).isFalse();
        assertThat(AppointmentReminderChoice.isRequested(0)).isFalse();
    }

    @Test
    void shouldShowTheStatusLine_onlyToAUserWhoMayReadThisPatientsRecordAndEmail() {
        AppointmentReminderService reminders = createAndRegisterMock(AppointmentReminderService.class);
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        ReminderStatusLine line = new ReminderStatusLine("appointment.reminder.status.due", "");
        when(reminders.statusLine(any(Appointment.class), any(Locale.class))).thenReturn(Optional.of(line));
        when(security.isAllowedAccessToPatientRecord(user, 42)).thenReturn(true);
        when(security.hasPrivilege(user, "_demographic", SecurityInfoManager.READ, 42)).thenReturn(true);
        when(security.hasPrivilege(user, "_email", SecurityInfoManager.READ, 42)).thenReturn(true);

        assertThat(AppointmentReminderChoice.statusLine(user, appointment(101), Locale.ENGLISH)).isEqualTo(line);

        when(security.isAllowedAccessToPatientRecord(user, 42)).thenReturn(false);
        assertThat(AppointmentReminderChoice.statusLine(user, appointment(101), Locale.ENGLISH)).isNull();

        when(security.isAllowedAccessToPatientRecord(user, 42)).thenReturn(true);

        when(security.hasPrivilege(user, "_email", SecurityInfoManager.READ, 42)).thenReturn(false);
        assertThat(AppointmentReminderChoice.statusLine(user, appointment(101), Locale.ENGLISH)).isNull();

        when(security.hasPrivilege(user, "_email", SecurityInfoManager.READ, 42)).thenReturn(true);
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
    }
}
