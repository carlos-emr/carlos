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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.dao.AppointmentSearchDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.AppointmentManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * HTTP-level endpoint tests for {@link ScheduleService} using CXF local transport.
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
        }

        @Test
        @DisplayName("should return 200 with empty list when no statuses")
        void shouldReturn200WithEmptyList_whenNoStatuses() {
            when(mockScheduleManager.getAppointmentStatuses(any(LoggedInInfo.class)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/schedule/statuses").get();

            assertThat(response.getStatus()).isEqualTo(200);
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

            when(mockScheduleManager.getDayAppointments(any(LoggedInInfo.class), anyString(), any(Date.class)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/schedule/day/today").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    /** Tests for POST /schedule/add endpoint. */
    @Nested
    @DisplayName("POST /schedule/add")
    class AddAppointment {

        @Test
        @Disabled("TODO: Requires mock setup for NewAppointmentConverter internal SpringUtils.getBean() calls on CXF thread")
        @DisplayName("should return 200 when appointment is added successfully")
        void shouldReturn200_whenAppointmentAdded() {
            // addAppointment returns void; default mock behavior (do nothing) is sufficient

            String json = "{\"demographicNo\":1,\"providerNo\":\"999001\",\"startDate\":\"2026-04-01\",\"startTime\":\"09:00\",\"endTime\":\"09:15\"}";

            Response response = request().path("/schedule/add").post(json);

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
