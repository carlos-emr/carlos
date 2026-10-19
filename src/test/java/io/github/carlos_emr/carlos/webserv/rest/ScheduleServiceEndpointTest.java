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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.dao.AppointmentSearchDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.AppointmentManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * CXF local-transport endpoint tests for {@link ScheduleService} using CXF local transport.
 *
 * <p>These tests verify path routing, JSON serialization, and HTTP status codes
 * for the schedule/appointment REST API. All dependencies are mocked.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("ScheduleService REST endpoint tests")
class ScheduleServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private ScheduleManager mockScheduleManager;
    @Mock
    private AppointmentManager mockAppointmentManager;
    @Mock
    private DemographicManager mockDemographicManager;
    @Mock
    private SecurityInfoManager mockSecurityInfoManager;
    @Mock
    private AppointmentSearchDao mockAppointmentSearchDao;
    @Mock
    private BillingONCHeader1Dao mockBillingONCHeader1Dao;

    @Override
    protected Object getServiceBean() {
        ScheduleService service = new ScheduleService();
        injectDependency(service, "scheduleManager", mockScheduleManager);
        injectDependency(service, "appointmentManager", mockAppointmentManager);
        injectDependency(service, "demographicManager", mockDemographicManager);
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(service, "appointmentSearchDao", mockAppointmentSearchDao);
        injectDependency(service, "billingONCHeader1Dao", mockBillingONCHeader1Dao);
        return service;
    }

    @BeforeEach
    void setUpSecurityDefaults() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), any(), any(), any()))
            .thenReturn(true);
    }

    /** Tests for GET /schedule/statuses endpoint. */
    @Nested
    @DisplayName("GET /schedule/statuses")
    class GetAppointmentStatuses {

        @Test
        @DisplayName("should return 200 with appointment statuses")
        void shouldReturn200WithStatuses_whenStatusesExist() {
            AppointmentStatus status = new AppointmentStatus();
            status.setStatus("T");
            status.setDescription("To Do");
            when(mockScheduleManager.getAppointmentStatuses(any(LoggedInInfo.class)))
                .thenReturn(List.of(status));

            Response response = request().path("/schedule/statuses").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/total").intValue()).isEqualTo(1);
            assertThat(wireJson.at("/content").isArray()).isTrue();
            assertThat(wireJson.at("/content")).hasSize(1);
            assertThat(wireJson.at("/content/0/status").textValue()).isEqualTo("T");
            assertThat(wireJson.at("/content/0/description").textValue()).isEqualTo("To Do");
        }

        @Test
        @DisplayName("should return 200 with empty list when no statuses")
        void shouldReturn200WithEmptyList_whenNoStatuses() {
            when(mockScheduleManager.getAppointmentStatuses(any(LoggedInInfo.class)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/schedule/statuses").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/total").intValue()).isEqualTo(0);
            assertThat(wireJson.at("/content").isArray()).isTrue();
            assertThat(wireJson.at("/content")).hasSize(0);
        }
    }

    /** Tests for GET /schedule/day/{date} endpoint. */
    @Nested
    @DisplayName("GET /schedule/day/{date}")
    class GetAppointmentsForDay {

        @Test
        @DisplayName("should return 200 with appointments for today")
        void shouldReturn200WithAppointments_whenTodayRequested() {
            Provider mockProvider = new Provider();
            mockProvider.setProviderNo("999001");
            when(mockLoggedInInfo.getLoggedInProvider()).thenReturn(mockProvider);

            var appointment = new io.github.carlos_emr.carlos.commn.model.Appointment();
            appointment.setId(17);
            appointment.setDemographicNo(0);
            appointment.setName("Reserved clinic time");
            appointment.setAppointmentDate(java.sql.Date.valueOf("2026-08-12"));
            appointment.setStartTime(java.sql.Time.valueOf("09:00:00"));
            appointment.setEndTime(java.sql.Time.valueOf("09:30:00"));
            when(mockScheduleManager.getDayAppointments(any(LoggedInInfo.class), anyString(), any(Date.class)))
                .thenReturn(List.of(appointment));

            Response response = request().path("/schedule/day/today").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.isArray()).isTrue();
            assertThat(wireJson).hasSize(1);
            assertThat(wireJson.at("/0/appointmentNo").intValue()).isEqualTo(17);
            assertThat(wireJson.at("/0/name").textValue()).isEqualTo("Reserved clinic time");
        }
    }

    /** Tests for POST /schedule/{demographicNo}/appointmentHistory. */
    @Nested
    @DisplayName("POST /schedule/{demographicNo}/appointmentHistory")
    class AppointmentHistory {

        @Test
        @DisplayName("should write midnight create/update timestamps as epoch milliseconds and keep slot shapes")
        void shouldWriteTimestampsAsEpochMillis_whenTheyFallOnMidnight() {
            // Issue #3877: createDateTime/updateDateTime are DATETIME columns, so a value at local
            // midnight used to leave /ws/services as "yyyy-MM-dd". The appointment's own date and
            // times are DATE/TIME columns and keep their string shapes (#2957 round-trips them).
            Calendar calendar = Calendar.getInstance();
            calendar.clear();
            calendar.set(2026, Calendar.MARCH, 11);
            Date midnight = calendar.getTime();
            // AppointmentConverter looks these up through SpringUtils; neither is reached here
            // (no demographic/provider include), but the converter needs them to construct.
            registerMock(io.github.carlos_emr.carlos.commn.dao.DemographicDao.class,
                    mock(io.github.carlos_emr.carlos.commn.dao.DemographicDao.class));
            registerMock(io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao.class,
                    mock(io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao.class));
            var appointment = new io.github.carlos_emr.carlos.commn.model.Appointment();
            appointment.setId(23);
            appointment.setDemographicNo(42);
            appointment.setProviderNo("999998");
            appointment.setAppointmentDate(java.sql.Date.valueOf("2026-03-11"));
            appointment.setStartTime(java.sql.Time.valueOf("09:30:00"));
            appointment.setEndTime(java.sql.Time.valueOf("09:45:00"));
            appointment.setCreateDateTime(midnight);
            appointment.setUpdateDateTime(midnight);
            when(mockAppointmentManager.getAppointmentHistoryWithoutDeleted(any(LoggedInInfo.class), eq(42), anyInt(),
                    anyInt())).thenReturn(List.of(appointment));
            when(mockBillingONCHeader1Dao.findByDemoNoWithItems(eq(42), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/schedule/42/appointmentHistory").post("{}");

            assertThat(response.getStatus()).isEqualTo(200);
            var wireJson = responseJson(response);
            assertThat(wireJson.at("/appointments/0/id").intValue()).isEqualTo(23);
            for (String field : List.of("createDateTime", "updateDateTime")) {
                var value = wireJson.at("/appointments/0/" + field);
                assertThat(value.isIntegralNumber()).as("%s on the wire: %s", field, value).isTrue();
                assertThat(value.longValue()).as(field).isEqualTo(midnight.getTime());
            }
            assertThat(wireJson.at("/appointments/0/appointmentDate").textValue()).isEqualTo("2026-03-11");
            assertThat(wireJson.at("/appointments/0/startTime").textValue()).isEqualTo("09:30:00");
            assertThat(wireJson.at("/appointments/0/endTime").textValue()).isEqualTo("09:45:00");
        }
    }
}
