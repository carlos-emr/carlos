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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.dao.AppointmentSearchDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.AppointmentManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.managers.ScheduleManagerImpl;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * CXF local-transport endpoint tests for {@link ScheduleService} using CXF local transport.
 *
 * <p>These tests verify path routing, JSON serialization, and HTTP status codes
 * for the schedule/appointment REST API. Persistence and authorization dependencies are mocked.</p>
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

    private ScheduleService service;

    @Override
    protected Object getServiceBean() {
        service = new ScheduleService();
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

    /** Exercises legacy JSON payloads through CXF and the real update manager. */
    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("legacyAppointmentEdits")
    void shouldAcceptLegacyPayload_whenEditVersionIsAbsentOrBlank(String path, String versionField) {
        registerMock(io.github.carlos_emr.carlos.commn.dao.DemographicDao.class,
                mock(io.github.carlos_emr.carlos.commn.dao.DemographicDao.class));
        registerMock(io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao.class,
                mock(io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao.class));
        var appointments = mock(OscarAppointmentDao.class);
        var archives = mock(io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao.class);
        var manager = new ScheduleManagerImpl();
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(manager, "oscarAppointmentDao", appointments);
        injectDependency(manager, "appointmentArchiveDao", archives);
        injectDependency(service, "scheduleManager", manager);
        var current = new io.github.carlos_emr.carlos.commn.model.Appointment();
        current.setId(42);
        current.setUpdateDateTime(new Date(1000));
        current.setCreator("FAKE-creator");
        current.setStatus("old-status");
        current.setType("old-type");
        current.setUrgency("old-urgency");
        current.setReason("FAKE-original");
        when(appointments.findForUpdate(42)).thenReturn(current);
        org.mockito.Mockito.doAnswer(call -> {
            current.setUpdateDateTime(new Date(2000));
            return null;
        }).when(appointments).flush();

        try (var audit = org.mockito.Mockito.mockStatic(io.github.carlos_emr.carlos.log.LogAction.class);
                Response response = request().path(path).post("{\"id\":42," + versionField
                        + "\"status\":\"new\",\"type\":\"new\",\"urgency\":\"new\","
                        + "\"reason\":\"FAKE-edit\",\"creator\":\"over-posted\"}")) {
            assertThat(response.getStatus()).isEqualTo(200);
            var body = response.readEntity(io.github.carlos_emr.carlos.webserv.rest.to.SchedulingResponse.class);
            assertThat(body.getAppointment().getEditVersion()).isEqualTo("2000");
        }
        boolean fullEdit = path.endsWith("updateAppointment");
        assertThat(current.getStatus()).isEqualTo(fullEdit || path.endsWith("updateStatus") ? "new" : "old-status");
        assertThat(current.getType()).isEqualTo(fullEdit || path.endsWith("updateType") ? "new" : "old-type");
        assertThat(current.getUrgency()).isEqualTo(fullEdit || path.endsWith("updateUrgency") ? "new" : "old-urgency");
        assertThat(current.getReason()).isEqualTo(fullEdit ? "FAKE-edit" : "FAKE-original");
        assertThat(current.getCreator()).isEqualTo("FAKE-creator");
        var order = org.mockito.Mockito.inOrder(appointments, archives);
        order.verify(appointments).findForUpdate(42);
        order.verify(archives).archiveAppointment(current);
        order.verify(appointments).merge(current);
        order.verify(appointments).flush();
        verifyNoInteractions(mockAppointmentManager);
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> legacyAppointmentEdits() {
        return java.util.stream.Stream.of("/schedule/updateAppointment", "/schedule/appointment/42/updateStatus",
                "/schedule/appointment/42/updateType", "/schedule/appointment/42/updateUrgency").flatMap(path ->
                java.util.stream.Stream.of("", "\"editVersion\":null,", "\"editVersion\":\"\",",
                        "\"editVersion\":\"   \",").map(version ->
                        org.junit.jupiter.params.provider.Arguments.of(path, version)));
    }

    /** Verifies that version failures survive CXF routing and JSON serialization. */
    @ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("versionedEditFailures")
    void shouldReturnStableJsonError_whenVersionedAppointmentEditIsRefused(
            String path, io.github.carlos_emr.carlos.managers.AppointmentUpdateException.Reason reason,
            int status, String code) {
        registerMock(io.github.carlos_emr.carlos.commn.dao.DemographicDao.class,
                mock(io.github.carlos_emr.carlos.commn.dao.DemographicDao.class));
        registerMock(io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao.class,
                mock(io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao.class));
        when(mockScheduleManager.updateAppointment(any(), eq(42), eq("1000"), any()))
                .thenThrow(new io.github.carlos_emr.carlos.managers.AppointmentUpdateException(reason));
        try (Response response = request().path(path)
                .post("{\"id\":42,\"editVersion\":\"1000\",\"reason\":\"FAKE-edit\"}")) {
            assertThat(response.getStatus()).isEqualTo(status);
            java.util.Map<?, ?> error = response.readEntity(java.util.Map.class);
            assertThat(error.get("code")).isEqualTo(code);
            assertThat(response.getMediaType().toString()).startsWith("application/json");
        }
        verifyNoInteractions(mockAppointmentManager);
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> versionedEditFailures() {
        return java.util.stream.Stream.of("/schedule/updateAppointment", "/schedule/appointment/42/updateStatus",
                "/schedule/appointment/42/updateType", "/schedule/appointment/42/updateUrgency").flatMap(path ->
                java.util.stream.Stream.of(
                    org.junit.jupiter.params.provider.Arguments.of(path,
                        io.github.carlos_emr.carlos.managers.AppointmentUpdateException.Reason.STALE_VERSION,
                        409, "APPOINTMENT_STALE_VERSION"),
                    org.junit.jupiter.params.provider.Arguments.of(path,
                        io.github.carlos_emr.carlos.managers.AppointmentUpdateException.Reason.NOT_FOUND,
                        404, "APPOINTMENT_NOT_FOUND")));
    }

    /** Tests the appointment-count endpoint's HTTP success and error responses. */
    @Nested
    @DisplayName("GET /schedule/fetchProvidersApptsCount/{sDate}/{eDate}")
    class GetProviderAppointmentCounts {

        @ParameterizedTest
        @CsvSource({
            "not-a-date, 2026-11-30",
            "2026-11-01, not-a-date",
            "not-a-date, not-a-date"
        })
        @DisplayName("should return the manager's 400 and message when either date is malformed")
        void shouldReturnBadRequest_whenEitherDateIsMalformed(String startDate, String endDate) {
            ScheduleManagerImpl manager = new ScheduleManagerImpl();
            OscarAppointmentDao appointmentDao = mock(OscarAppointmentDao.class);
            injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);
            injectDependency(manager, "oscarAppointmentDao", appointmentDao);
            injectDependency(service, "scheduleManager", manager);

            try (Response response = request().path("/schedule/fetchProvidersApptsCount/" + startDate + "/" + endDate).get()) {
                assertThat(response.getStatus()).isEqualTo(400);
                assertThat(response.readEntity(String.class)).isEqualTo("Path parameter has the wrong format");
            }
            verifyNoInteractions(appointmentDao);
        }

        @Test
        @DisplayName("should keep unexpected failures as a generic 500")
        void shouldReturnInternalServerError_whenManagerFailsUnexpectedly() {
            when(mockScheduleManager.listProviderAppointmentCounts(any(LoggedInInfo.class), eq("2026-11-01"), eq("2026-11-30")))
                    .thenThrow(new IllegalStateException("Internal database details"));

            try (Response response = request().path("/schedule/fetchProvidersApptsCount/2026-11-01/2026-11-30").get()) {
                assertThat(response.getStatus()).isEqualTo(500);
                assertThat(response.readEntity(String.class)).isEqualTo("Internal server error");
            }
        }

        @Test
        @DisplayName("should return appointment counts when the date range is valid")
        void shouldReturnAppointmentCounts_withValidDates() {
            when(mockScheduleManager.listProviderAppointmentCounts(any(LoggedInInfo.class), eq("2026-11-01"), eq("2026-11-30")))
                    .thenReturn(Collections.singletonList(new Object[]{"999001", "Test", "Provider", 3L}));

            try (Response response = request().path("/schedule/fetchProvidersApptsCount/2026-11-01/2026-11-30").get()) {
                assertThat(response.getStatus()).isEqualTo(200);
                var json = responseJson(response);
                assertThat(json.at("/ProviderApptsCount")).hasSize(1);
                assertThat(json.at("/ProviderApptsCount/0/providerNo").textValue()).isEqualTo("999001");
                assertThat(json.at("/ProviderApptsCount/0/appointmentCount").longValue()).isEqualTo(3L);
            }
        }
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
        @DisplayName("should preserve names, blank slots, missing patients and ordering with bulk names")
        void shouldPreserveResponse_whenNamesAreBulkLoaded() {
            var appointments = new java.util.ArrayList<io.github.carlos_emr.carlos.commn.model.Appointment>();
            int[] patientIds = {42, 0, 99, 42};
            for (int i = 0; i < patientIds.length; i++) {
                var appointment = new io.github.carlos_emr.carlos.commn.model.Appointment();
                appointment.setId(20 - i);
                appointment.setDemographicNo(patientIds[i]);
                appointment.setName("Reserved clinic time");
                appointment.setAppointmentDate(java.sql.Date.valueOf("2026-10-31"));
                appointment.setStartTime(java.sql.Time.valueOf("09:00:00"));
                appointment.setEndTime(java.sql.Time.valueOf("09:30:00"));
                appointment.setReason("Follow-up");
                appointment.setStatus("t");
                appointment.setNotes("Synthetic notes");
                appointment.setType("Follow-up");
                appointments.add(appointment);
            }
            when(mockScheduleManager.getDayAppointments(any(LoggedInInfo.class), anyString(), any(Date.class)))
                .thenReturn(appointments);
            when(mockDemographicManager.getDemographicFormattedNames(any(LoggedInInfo.class), any()))
                .thenReturn(java.util.Map.of(42, "O'Neil, Zoë"));

            Response response = request().path("/schedule/999001/day/2026-10-31").get();

            assertThat(response.getStatus()).isEqualTo(200);
            var json = responseJson(response);
            assertThat(json).hasSize(4);
            assertThat(json.at("/0/name").textValue()).isEqualTo("O'Neil, Zoë");
            assertThat(json.at("/1/name").textValue()).isEqualTo("Reserved clinic time");
            assertThat(json.at("/2/name").isMissingNode() || json.at("/2/name").isNull()).isTrue();
            assertThat(json.at("/3/name").textValue()).isEqualTo("O'Neil, Zoë");
            for (int i = 0; i < patientIds.length; i++) {
                assertThat(json.at("/" + i + "/appointmentNo").intValue()).isEqualTo(20 - i);
                assertThat(json.at("/" + i + "/demographicNo").intValue()).isEqualTo(patientIds[i]);
                assertThat(json.at("/" + i + "/reason").textValue()).isEqualTo("Follow-up");
                assertThat(json.at("/" + i + "/notes").textValue()).isEqualTo("Synthetic notes");
                assertThat(json.at("/" + i + "/duration").textValue()).isEqualTo("30m ");
            }
            verify(mockDemographicManager)
                .getDemographicFormattedNames(any(LoggedInInfo.class), eq(List.of(42, 99, 42)));
            verify(mockDemographicManager, never())
                .getDemographicFormattedName(any(), any());
        }

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
