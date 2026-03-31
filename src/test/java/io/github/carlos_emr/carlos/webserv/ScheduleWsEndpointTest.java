/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentType;
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.AppointmentTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.AppointmentTypeTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ScheduleTemplateCodeTransfer;

/**
 * SOAP-level endpoint tests for {@link ScheduleWs} using CXF local transport.
 *
 * <p>These tests verify the full CXF JAX-WS pipeline for schedule operations:
 * SOAP envelope marshalling/unmarshalling, WSDL processing, and response
 * serialization of {@link AppointmentTransfer} arrays. Only the most
 * representative methods are tested from the 19-method service.</p>
 *
 * @since 2026-03-31
 * @see CarlosSoapTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("soap")
@DisplayName("ScheduleWs SOAP endpoint tests")
class ScheduleWsEndpointTest extends CarlosSoapTestBase {

    @Mock
    private ScheduleManager scheduleManager;

    private ScheduleWs ws;

    @Override
    protected Object getServiceBean() {
        ws = new ScheduleWs();
        return ws;
    }

    @Override
    protected Class<?> getServiceInterface() {
        return ScheduleWs.class;
    }

    @BeforeEach
    void setUpMocks() {
        registerMock(ScheduleManager.class, scheduleManager);
        injectDependency(ws, "scheduleManager", scheduleManager);
    }

    /** Tests for the getScheduleTemplateCodes SOAP operation. */
    @Nested
    @DisplayName("getScheduleTemplateCodes operation")
    class GetScheduleTemplateCodes {

        @Test
        @DisplayName("should return template codes when codes exist")
        void shouldReturnTemplateCodes_whenCodesExist() {
            List<ScheduleTemplateCode> codes = new ArrayList<>();
            ScheduleTemplateCode code = new ScheduleTemplateCode();
            code.setCode('A');
            code.setDescription("Available");
            codes.add(code);
            when(scheduleManager.getScheduleTemplateCodes()).thenReturn(codes);

            ScheduleWs proxy = createClient(ScheduleWs.class);
            ScheduleTemplateCodeTransfer[] result = proxy.getScheduleTemplateCodes();

            assertThat(result).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should return null or empty array when no template codes (JAXB empty array serialization)")
        void shouldReturnEmptyArray_whenNoTemplateCodes() {
            when(scheduleManager.getScheduleTemplateCodes()).thenReturn(new ArrayList<>());

            ScheduleWs proxy = createClient(ScheduleWs.class);
            ScheduleTemplateCodeTransfer[] result = proxy.getScheduleTemplateCodes();

            assertThat(result).isNullOrEmpty();
        }
    }

    /** Tests for the getAppointment2 SOAP operation. */
    @Nested
    @DisplayName("getAppointment2 operation")
    class GetAppointment2 {

        @Test
        @DisplayName("should return appointment transfer when valid ID provided")
        void shouldReturnAppointmentTransfer_whenValidIdProvided() {
            Appointment appointment = new Appointment();
            appointment.setId(42);
            appointment.setProviderNo("001");
            appointment.setAppointmentDate(new Date());
            appointment.setStartTime(new Date());
            appointment.setEndTime(new Date());
            when(scheduleManager.getAppointment(any(LoggedInInfo.class), eq(42)))
                .thenReturn(appointment);

            ScheduleWs proxy = createClient(ScheduleWs.class);
            AppointmentTransfer result = proxy.getAppointment2(42, false);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null when appointment not found")
        void shouldReturnNull_whenAppointmentNotFound() {
            when(scheduleManager.getAppointment(any(LoggedInInfo.class), eq(999)))
                .thenReturn(null);

            ScheduleWs proxy = createClient(ScheduleWs.class);
            AppointmentTransfer result = proxy.getAppointment2(999, false);

            assertThat(result).isNull();
        }
    }

    /** Tests for the getAppointmentTypes SOAP operation. */
    @Nested
    @DisplayName("getAppointmentTypes operation")
    class GetAppointmentTypes {

        @Test
        @DisplayName("should return appointment types when types exist")
        void shouldReturnAppointmentTypes_whenTypesExist() {
            List<AppointmentType> types = new ArrayList<>();
            AppointmentType type = new AppointmentType();
            type.setName("General");
            types.add(type);
            when(scheduleManager.getAppointmentTypes()).thenReturn(types);

            ScheduleWs proxy = createClient(ScheduleWs.class);
            AppointmentTypeTransfer[] result = proxy.getAppointmentTypes();

            assertThat(result).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should return null or empty array when no appointment types (JAXB empty array serialization)")
        void shouldReturnEmptyArray_whenNoAppointmentTypes() {
            when(scheduleManager.getAppointmentTypes()).thenReturn(new ArrayList<>());

            ScheduleWs proxy = createClient(ScheduleWs.class);
            AppointmentTypeTransfer[] result = proxy.getAppointmentTypes();

            assertThat(result).isNullOrEmpty();
        }
    }

    /** Tests for the getAppointmentsUpdatedAfterDate SOAP operation. */
    @Nested
    @DisplayName("getAppointmentsUpdatedAfterDate operation")
    class GetAppointmentsUpdatedAfterDate {

        @Test
        @DisplayName("should return appointments updated after date")
        void shouldReturnAppointments_whenUpdatedAfterDate() {
            List<Appointment> appointments = new ArrayList<>();
            Appointment apt = new Appointment();
            apt.setId(1);
            apt.setAppointmentDate(new Date());
            apt.setStartTime(new Date());
            apt.setEndTime(new Date());
            appointments.add(apt);
            when(scheduleManager.getAppointmentUpdatedAfterDate(any(LoggedInInfo.class), any(Date.class), anyInt()))
                .thenReturn(appointments);

            ScheduleWs proxy = createClient(ScheduleWs.class);
            AppointmentTransfer[] result = proxy.getAppointmentsUpdatedAfterDate(new Date(), 10, false);

            assertThat(result).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should return null or empty array when no appointments after date (JAXB empty array serialization)")
        void shouldReturnEmptyArray_whenNoAppointmentsAfterDate() {
            when(scheduleManager.getAppointmentUpdatedAfterDate(any(LoggedInInfo.class), any(Date.class), anyInt()))
                .thenReturn(new ArrayList<>());

            ScheduleWs proxy = createClient(ScheduleWs.class);
            AppointmentTransfer[] result = proxy.getAppointmentsUpdatedAfterDate(new Date(), 10, false);

            assertThat(result).isNullOrEmpty();
        }
    }
}
