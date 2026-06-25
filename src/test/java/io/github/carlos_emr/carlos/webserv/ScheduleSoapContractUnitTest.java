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
package io.github.carlos_emr.carlos.webserv;

import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentType;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.DayWorkSchedule;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.AppointmentTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.AppointmentTypeTransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contract tests for the native CARLOS {@code ScheduleService} SOAP operations behind the
 * appointment rows of {@code docs/api/cortico-carlos-compatibility.md}.
 *
 * <p>Each test exercises a representative successful call and proves the operation delegates to
 * {@link ScheduleManager} with the native CARLOS contract arguments. This is native CARLOS
 * compatibility behavior; mapping literal Cortico/Juno {@code .ws} operation labels onto these
 * calls is an adapter/proxy responsibility and is out of scope here.</p>
 *
 * <p>Fixtures are synthetic; no PHI, credentials, or live calls are used.</p>
 *
 * @since 2026-06-25
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Native ScheduleService SOAP contract")
@Tag("unit")
@Tag("webservice")
class ScheduleSoapContractUnitTest {

    private static final String PROVIDER_NO = "999998";
    private static final Integer DEMOGRAPHIC_ID = 321;
    private static final Integer APPOINTMENT_ID = 4242;

    @Mock
    private ScheduleManager scheduleManager;

    private LoggedInInfo loggedInInfo;
    private ScheduleWs service;

    @BeforeEach
    void setUp() {
        loggedInInfo = new LoggedInInfo();
        service = new ScheduleWs() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }

            @Override
            protected Security getLoggedInSecurity() {
                return new Security();
            }
        };
        ReflectionTestUtils.setField(service, "scheduleManager", scheduleManager);
    }

    @Test
    @DisplayName("should add appointment and return the new id when post_appointment_data maps to addAppointment")
    void shouldAddAppointment_whenPostingAppointmentData() {
        AppointmentTransfer transfer = new AppointmentTransfer();
        transfer.setProviderNo(PROVIDER_NO);
        transfer.setDemographicNo(DEMOGRAPHIC_ID);
        doAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(2);
            appointment.setId(APPOINTMENT_ID);
            return null;
        }).when(scheduleManager).addAppointment(eq(loggedInInfo), any(Security.class), any(Appointment.class));

        Integer createdId = service.addAppointment(transfer);

        assertThat(createdId).isEqualTo(APPOINTMENT_ID);
        verify(scheduleManager).addAppointment(eq(loggedInInfo), any(Security.class), any(Appointment.class));
    }

    @Test
    @DisplayName("should read then persist editable fields when update_appointment maps to updateAppointment")
    void shouldUpdateAppointment_whenUpdatingAppointment() {
        AppointmentTransfer transfer = new AppointmentTransfer();
        transfer.setId(APPOINTMENT_ID);
        transfer.setStatus("C");
        when(scheduleManager.getAppointment(loggedInInfo, APPOINTMENT_ID)).thenReturn(new Appointment());

        service.updateAppointment(transfer);

        // The contract first reads the current appointment so unrelated fields are preserved,
        // then writes the merged appointment back.
        verify(scheduleManager).getAppointment(loggedInInfo, APPOINTMENT_ID);
        verify(scheduleManager).updateAppointment(eq(loggedInInfo), any(Appointment.class));
    }

    @Test
    @DisplayName("should read provider day schedule when get_day_work_schedule maps to getDayWorkSchedule")
    void shouldReadDayWorkSchedule_whenReadingProviderSchedule() {
        GregorianCalendar date = new GregorianCalendar(2026, GregorianCalendar.JUNE, 25);
        DayWorkSchedule daySchedule = new DayWorkSchedule();
        when(scheduleManager.getDayWorkSchedule(PROVIDER_NO, date)).thenReturn(daySchedule);

        assertThat(service.getDayWorkSchedule(PROVIDER_NO, date)).isNotNull();
        verify(scheduleManager).getDayWorkSchedule(PROVIDER_NO, date);
    }

    @Test
    @DisplayName("should return null day schedule when the provider has none")
    void shouldReturnNullDaySchedule_whenProviderHasNone() {
        GregorianCalendar date = new GregorianCalendar(2026, GregorianCalendar.JUNE, 25);
        when(scheduleManager.getDayWorkSchedule(PROVIDER_NO, date)).thenReturn(null);

        assertThat(service.getDayWorkSchedule(PROVIDER_NO, date)).isNull();
    }

    @Test
    @DisplayName("should read provider appointments when get_providers_appointments maps to getAppointmentsForProvider")
    void shouldReadProviderAppointments_whenReadingForProvider() {
        GregorianCalendar date = new GregorianCalendar(2026, GregorianCalendar.JUNE, 25);
        when(scheduleManager.getDayAppointments(loggedInInfo, PROVIDER_NO, date)).thenReturn(Collections.emptyList());

        AppointmentTransfer[] result = service.getAppointmentsForProvider(PROVIDER_NO, date);

        assertThat(result).isEmpty();
        verify(scheduleManager).getDayAppointments(loggedInInfo, PROVIDER_NO, date);
    }

    @Test
    @DisplayName("should read patient appointments when get_appointments_for_patient maps to getAppointmentsForPatient")
    void shouldReadPatientAppointments_whenReadingForPatient() {
        when(scheduleManager.getAppointmentsForPatient(loggedInInfo, DEMOGRAPHIC_ID, 0, 50))
                .thenReturn(Collections.emptyList());

        AppointmentTransfer[] result = service.getAppointmentsForPatient(DEMOGRAPHIC_ID, 0, 50);

        assertThat(result).isEmpty();
        verify(scheduleManager).getAppointmentsForPatient(loggedInInfo, DEMOGRAPHIC_ID, 0, 50);
    }

    @Test
    @DisplayName("should read appointment by id when get_appointment_by_id maps to getAppointment")
    void shouldReadAppointmentById_whenReadingSingleAppointment() {
        Appointment appointment = new Appointment();
        appointment.setId(APPOINTMENT_ID);
        when(scheduleManager.getAppointment(loggedInInfo, APPOINTMENT_ID)).thenReturn(appointment);

        AppointmentTransfer result = service.getAppointment(APPOINTMENT_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(APPOINTMENT_ID);
        verify(scheduleManager).getAppointment(loggedInInfo, APPOINTMENT_ID);
    }

    @Test
    @DisplayName("should read appointment types when get_appointment_types maps to getAppointmentTypes")
    void shouldReadAppointmentTypes_whenReadingTypes() {
        List<AppointmentType> types = Collections.singletonList(new AppointmentType());
        when(scheduleManager.getAppointmentTypes()).thenReturn(types);

        AppointmentTypeTransfer[] result = service.getAppointmentTypes();

        assertThat(result).hasSize(1);
        verify(scheduleManager).getAppointmentTypes();
    }
}
