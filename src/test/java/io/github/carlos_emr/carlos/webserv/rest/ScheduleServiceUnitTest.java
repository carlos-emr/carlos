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
package io.github.carlos_emr.carlos.webserv.rest;

import java.util.Date;
import java.util.List;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.appointment.search.FilterDefinition;
import io.github.carlos_emr.carlos.commn.dao.AppointmentSearchDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentSearch;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.AppointmentManager;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.NewAppointmentTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SearchConfigTo1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ScheduleService}.
 *
 * <p>Covers schedule validation, authorization, and error-handling paths.</p>
 *
 * @since 2026-04-20
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleService unit tests")
@Tag("unit")
@Tag("fast")
class ScheduleServiceUnitTest extends CarlosUnitTestBase {

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private AppointmentManager appointmentManager;

    @Mock
    private ScheduleManager scheduleManager;

    @Mock
    private AppointmentSearchDao appointmentSearchDao;

    private ScheduleService service;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        loggedInInfo = new LoggedInInfo();
        service = new ScheduleService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };

        injectDependency(service, "securityInfoManager", securityInfoManager);
        injectDependency(service, "appointmentManager", appointmentManager);
        injectDependency(service, "scheduleManager", scheduleManager);
        injectDependency(service, "appointmentSearchDao", appointmentSearchDao);

        // Lenient: some tests exercise paths that short-circuit before the privilege check
        // (e.g. findUnknownFilter) or override the stub. Strict-by-default would fail those.
        lenient().when(securityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("w"), any())).thenReturn(true);
    }

    @Test
    @DisplayName("should preserve the manager's HTTP response when appointment count dates are invalid")
    void shouldPreserveHttpResponse_whenAppointmentCountDatesAreInvalid() {
        WebApplicationException badRequest = new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                .entity("Path parameter has the wrong format").header("X-Validation", "date-format").build());
        when(scheduleManager.listProviderAppointmentCounts(loggedInInfo, "not-a-date", "2026-11-30"))
                .thenThrow(badRequest);

        // Use a separate logger so parallel endpoint tests cannot pollute this capture.
        service.logger = LogManager.getLogger(getClass().getName() + ".invalidAppointmentCountDates");
        try (LogCapture logs = LogCapture.forLogger(service.logger.getName())) {
            assertThatThrownBy(() -> service.listProviderAppointmentCounts("not-a-date", "2026-11-30"))
                    .isSameAs(badRequest);
            assertThat(logs.events()).noneMatch(event -> event.getLevel().isMoreSpecificThan(Level.ERROR));
        }
    }

    @Test
    @DisplayName("should return bad request when search configuration id is null or zero")
    void shouldReturnBadRequest_whenSearchConfigurationIdIsNullOrZero() {
        Response nullIdResponse = service.saveSearchConfig(null, new SearchConfigTo1());
        Response zeroIdResponse = service.saveSearchConfig(0, new SearchConfigTo1());

        assertThat(nullIdResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(nullIdResponse.getEntity()).isEqualTo("Invalid search configuration id");
        assertThat(zeroIdResponse.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(zeroIdResponse.getEntity()).isEqualTo("Invalid search configuration id");
        // Two saveSearchConfig invocations above → hasPrivilege is checked twice
        verify(securityInfoManager, times(2)).hasPrivilege(eq(loggedInInfo), eq("_appointment"), eq("w"), isNull());
        verify(appointmentSearchDao, never()).find(any());
    }

    @Test
    @DisplayName("should return forbidden when caller lacks appointment write privilege")
    void shouldReturnForbidden_whenCallerLacksAppointmentWritePrivilege() {
        when(securityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("w"), any())).thenReturn(false);

        Response response = service.saveSearchConfig(123, new SearchConfigTo1());

        assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
        verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq("_appointment"), eq("w"), isNull());
        verify(appointmentSearchDao, never()).find(any());
    }

    @Test
    @DisplayName("should return internal server error when persist fails")
    void shouldReturnInternalServerError_whenPersistFails() {
        AppointmentSearch currentSearch = new AppointmentSearch();
        currentSearch.setProviderNo("101");
        currentSearch.setSearchName("Test");
        currentSearch.setSearchType("APPOINTMENT");
        currentSearch.setUuid("uuid-1");

        when(appointmentSearchDao.find(123)).thenReturn(currentSearch);
        doThrow(new RuntimeException("boom")).when(appointmentSearchDao).persist(any(AppointmentSearch.class));

        Response response = service.saveSearchConfig(123, new SearchConfigTo1());

        assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
        assertThat(response.getEntity()).isEqualTo("Failed to save search configuration");
    }

    @Test
    @DisplayName("should return missing filter sentinel when filter class name is null")
    void shouldReturnMissingFilterSentinel_whenFilterClassNameIsNull() {
        FilterDefinition filterDefinition = new FilterDefinition();
        filterDefinition.setFilterClassName(null);

        String unknownFilter = service.findUnknownFilter(List.of(filterDefinition));

        assertThat(unknownFilter).isEqualTo("<missing filterClassName>");
    }

    /** Authorization rejection tests for appointment mutator endpoints. */
    @Nested
    @DisplayName("appointment mutator authorization")
    @Tag("security")
    class AppointmentMutatorAuthorizationTest {

        @BeforeEach
        void denyPrivilege() {
            lenient().when(securityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("w"), any())).thenReturn(false);
            lenient().when(securityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("d"), any())).thenReturn(false);
        }

        /** Confirms the mutator gated on the {@code _appointment} write privilege before rejecting. */
        private void verifyWritePrivilegeChecked() {
            verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq("_appointment"), eq("w"), isNull());
        }

        /** Confirms the mutator gated on the {@code _appointment} delete privilege before rejecting. */
        private void verifyDeletePrivilegeChecked() {
            verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq("_appointment"), eq("d"), isNull());
        }

        @Test
        @DisplayName("should throw 403 and not persist when caller lacks write privilege on addAppointment")
        void shouldThrow403_whenCallerLacksWritePrivilegeOnAddAppointment() {
            assertThatThrownBy(() -> service.addAppointment(new NewAppointmentTo1()))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus())
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
            verifyNoInteractions(appointmentManager);
            verifyWritePrivilegeChecked();
        }

        @Test
        @DisplayName("should return 403 and not delete when caller lacks delete privilege on deleteAppointment")
        void shouldReturn403_whenCallerLacksDeletePrivilegeOnDeleteAppointment() {
            AppointmentTo1 apptTo = new AppointmentTo1();
            apptTo.setId(42);

            Response response = service.deleteAppointment(apptTo);

            assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
            verifyNoInteractions(appointmentManager);
            verifyDeletePrivilegeChecked();
        }

        @Test
        @DisplayName("should throw 403 and not update when caller lacks write privilege on updateAppointment")
        void shouldThrow403_whenCallerLacksWritePrivilegeOnUpdateAppointment() {
            assertThatThrownBy(() -> service.updateAppointment(new AppointmentTo1()))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus())
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
            verifyNoInteractions(scheduleManager);
            verifyWritePrivilegeChecked();
        }

        @Test
        @DisplayName("should throw 403 and not update when caller lacks write privilege on updateAppointmentStatus")
        void shouldThrow403_whenCallerLacksWritePrivilegeOnUpdateAppointmentStatus() {
            assertThatThrownBy(() -> service.updateAppointmentStatus(99, new AppointmentTo1()))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus())
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
            verifyNoInteractions(appointmentManager);
            verifyWritePrivilegeChecked();
        }

        @Test
        @DisplayName("should throw 403 and not update when caller lacks write privilege on updateAppointmentType")
        void shouldThrow403_whenCallerLacksWritePrivilegeOnUpdateAppointmentType() {
            assertThatThrownBy(() -> service.updateAppointmentType(99, new AppointmentTo1()))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus())
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
            verifyNoInteractions(appointmentManager);
            verifyWritePrivilegeChecked();
        }

        @Test
        @DisplayName("should throw 403 and not update when caller lacks write privilege on updateAppointmentUrgency")
        void shouldThrow403_whenCallerLacksWritePrivilegeOnUpdateAppointmentUrgency() {
            assertThatThrownBy(() -> service.updateAppointmentUrgency(99, new AppointmentTo1()))
                    .isInstanceOf(WebApplicationException.class)
                    .satisfies(ex -> assertThat(((WebApplicationException) ex).getResponse().getStatus())
                            .isEqualTo(Response.Status.FORBIDDEN.getStatusCode()));
            verifyNoInteractions(appointmentManager);
            verifyWritePrivilegeChecked();
        }
    }

    @Test
    @DisplayName("should preserve audit fields and stamp the updating provider when updating an appointment")
    void shouldPreserveAuditFields_whenUpdatingAppointment() {
        // AppointmentConverter resolves these DAOs via SpringUtils in its field initializers.
        registerMock(DemographicDao.class, Mockito.mock(DemographicDao.class));
        registerMock(ProviderDao.class, Mockito.mock(ProviderDao.class));

        Provider loggedInProvider = new Provider();
        loggedInProvider.setProviderNo("101");
        loggedInInfo.setLoggedInProvider(loggedInProvider);

        Date originalCreate = new Date(1_000_000_000_000L);
        Appointment existing = new Appointment();
        existing.setId(42);
        existing.setCreateDateTime(originalCreate);
        existing.setCreator("origCreator");
        existing.setCreatorSecurityId(7);
        existing.setReason("old reason");
        when(scheduleManager.updateAppointment(eq(loggedInInfo), eq(42), eq("original-version"), any()))
                .thenAnswer(call -> {
                    java.util.function.Consumer<Appointment> edit = call.getArgument(3);
                    edit.accept(existing);
                    existing.setLastUpdateUser(loggedInInfo.getLoggedInProviderNo());
                    return existing;
                });

        AppointmentTo1 hostile = new AppointmentTo1();
        hostile.setId(42);
        hostile.setEditVersion("original-version");
        hostile.setReason("new reason");
        hostile.setCreator("hacker");
        hostile.setCreatorSecurityId(999);
        hostile.setLastUpdateUser("hacker");
        hostile.setCreateDateTime(new Date(0));

        service.updateAppointment(hostile);

        verify(scheduleManager).updateAppointment(eq(loggedInInfo), eq(42), eq("original-version"), any());
        verifyNoInteractions(appointmentManager);
        Appointment saved = existing;

        assertThat(saved.getReason()).isEqualTo("new reason");
        assertThat(saved.getCreateDateTime()).isEqualTo(originalCreate);
        assertThat(saved.getCreator()).isEqualTo("origCreator");
        assertThat(saved.getCreatorSecurityId()).isEqualTo(7);
        assertThat(saved.getLastUpdateUser()).isEqualTo("101");
    }

    @Test
    @DisplayName("should reject update with bad request when appointment id is missing")
    void shouldRejectUpdate_whenIdIsMissing() {
        AppointmentTo1 to = new AppointmentTo1();
        to.setId(null);

        assertThatThrownBy(() -> service.updateAppointment(to))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(e -> assertThat(((WebApplicationException) e).getResponse().getStatus())
                        .isEqualTo(Response.Status.BAD_REQUEST.getStatusCode()));

        verifyNoInteractions(scheduleManager, appointmentManager);
    }

    @Test
    @DisplayName("should respond not found when the appointment to update does not exist")
    void shouldRespondNotFound_whenAppointmentMissing() {
        registerMock(DemographicDao.class, Mockito.mock(DemographicDao.class));
        registerMock(ProviderDao.class, Mockito.mock(ProviderDao.class));
        when(scheduleManager.updateAppointment(any(), eq(404), eq("read-version"), any()))
                .thenThrow(new io.github.carlos_emr.carlos.managers.AppointmentUpdateException(
                        io.github.carlos_emr.carlos.managers.AppointmentUpdateException.Reason.NOT_FOUND));

        AppointmentTo1 to = new AppointmentTo1();
        to.setId(404);
        to.setEditVersion("read-version");

        assertThatThrownBy(() -> service.updateAppointment(to))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(e -> assertThat(((WebApplicationException) e).getResponse().getStatus())
                        .isEqualTo(Response.Status.NOT_FOUND.getStatusCode()));

        verify(scheduleManager).updateAppointment(eq(loggedInInfo), eq(404), eq("read-version"), any());
        verifyNoInteractions(appointmentManager);
    }

    @Test
    void shouldReturnStableConflict_whenSharedServiceRejectsStaleVersion() {
        AppointmentTo1 to = new AppointmentTo1();
        registerMock(DemographicDao.class, Mockito.mock(DemographicDao.class));
        registerMock(ProviderDao.class, Mockito.mock(ProviderDao.class));
        to.setId(42);
        to.setEditVersion("stale");
        when(scheduleManager.updateAppointment(any(), eq(42), eq("stale"), any()))
                .thenThrow(new io.github.carlos_emr.carlos.managers.AppointmentUpdateException(
                        io.github.carlos_emr.carlos.managers.AppointmentUpdateException.Reason.STALE_VERSION));
        assertThatThrownBy(() -> service.updateAppointment(to))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(e -> {
                    Response response = ((WebApplicationException) e).getResponse();
                    assertThat(response.getStatus()).isEqualTo(409);
                    assertThat(((java.util.Map<?, ?>) response.getEntity()).get("code"))
                            .isEqualTo("APPOINTMENT_STALE_VERSION");
                });
        verifyNoInteractions(appointmentManager);
    }

    @Test
    void shouldReturnBadRequest_whenEditVersionIsMissing() {
        registerMock(DemographicDao.class, Mockito.mock(DemographicDao.class));
        registerMock(ProviderDao.class, Mockito.mock(ProviderDao.class));
        AppointmentTo1 to = new AppointmentTo1();
        to.setId(42);
        when(scheduleManager.updateAppointment(any(), eq(42), isNull(), any()))
                .thenThrow(new io.github.carlos_emr.carlos.managers.AppointmentUpdateException(
                        io.github.carlos_emr.carlos.managers.AppointmentUpdateException.Reason.MISSING_VERSION));
        assertThatThrownBy(() -> service.updateAppointment(to))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(e -> assertThat(((WebApplicationException) e).getResponse().getStatus()).isEqualTo(400));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Status", "Type", "Urgency"})
    void shouldApplyOnlyNamedField_whenVersionedPartialEditSucceeds(String field) {
        registerMock(DemographicDao.class, Mockito.mock(DemographicDao.class));
        registerMock(ProviderDao.class, Mockito.mock(ProviderDao.class));
        Appointment current = new Appointment();
        current.setId(42);
        current.setStatus("old-status");
        current.setType("old-type");
        current.setUrgency("old-urgency");
        current.setReason("preserved");
        current.setCreator("server-creator");
        current.setLastUpdateUser("server-updater");
        current.setUpdateDateTime(new Date(123000));
        when(scheduleManager.updateAppointment(eq(loggedInInfo), eq(42), eq("123000"), any()))
                .thenAnswer(call -> {
                    java.util.function.Consumer<Appointment> edit = call.getArgument(3);
                    edit.accept(current);
                    return current;
                });
        AppointmentTo1 submitted = new AppointmentTo1();
        submitted.setId(999); // Path identity controls a partial edit.
        submitted.setEditVersion("123000");
        submitted.setStatus("new");
        submitted.setType("new");
        submitted.setUrgency("new");
        submitted.setReason("over-posted");
        submitted.setCreator("over-posted");
        submitted.setLastUpdateUser("over-posted");

        var response = switch (field) {
            case "Status" -> service.updateAppointmentStatus(42, submitted);
            case "Type" -> service.updateAppointmentType(42, submitted);
            case "Urgency" -> service.updateAppointmentUrgency(42, submitted);
            default -> throw new AssertionError(field);
        };

        assertThat(current.getId()).isEqualTo(42);
        assertThat(current.getStatus()).isEqualTo("Status".equals(field) ? "new" : "old-status");
        assertThat(current.getType()).isEqualTo("Type".equals(field) ? "new" : "old-type");
        assertThat(current.getUrgency()).isEqualTo("Urgency".equals(field) ? "new" : "old-urgency");
        assertThat(current.getReason()).isEqualTo("preserved");
        assertThat(current.getCreator()).isEqualTo("server-creator");
        assertThat(current.getLastUpdateUser()).isEqualTo("server-updater");
        assertThat(response.getAppointment().getEditVersion()).isEqualTo("123000");
        verifyNoInteractions(appointmentManager);
    }

    @Test
    @DisplayName("should reject updateUrgency with bad request when the request body is null")
    void shouldRejectUpdateUrgency_whenBodyIsNull() {
        assertThatThrownBy(() -> service.updateAppointmentUrgency(99, null))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(e -> assertThat(((WebApplicationException) e).getResponse().getStatus())
                        .isEqualTo(Response.Status.BAD_REQUEST.getStatusCode()));

        verifyNoInteractions(scheduleManager, appointmentManager);
    }
}
