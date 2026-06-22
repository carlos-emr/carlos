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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SearchConfigTo1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ScheduleService}.
 *
 * <p>Focuses on the `saveSearchConfig` error-handling paths added for the
 * appointment-search hardening change.</p>
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
    private AppointmentSearchDao appointmentSearchDao;

    @Mock
    private AppointmentManager appointmentManager;

    @Mock
    private ScheduleManager scheduleManager;

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
        injectDependency(service, "appointmentSearchDao", appointmentSearchDao);
        injectDependency(service, "appointmentManager", appointmentManager);
        injectDependency(service, "scheduleManager", scheduleManager);

        // Lenient: some tests exercise paths that short-circuit before the privilege check
        // (e.g. findUnknownFilter) or override the stub. Strict-by-default would fail those.
        lenient().when(securityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("w"), any())).thenReturn(true);
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
        when(appointmentManager.getAppointment(any(), eq(42))).thenReturn(existing);

        AppointmentTo1 hostile = new AppointmentTo1();
        hostile.setId(42);
        hostile.setReason("new reason");
        hostile.setCreator("hacker");
        hostile.setCreatorSecurityId(999);
        hostile.setLastUpdateUser("hacker");
        hostile.setCreateDateTime(new Date(0));

        service.updateAppointment(hostile);

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(scheduleManager).updateAppointment(eq(loggedInInfo), captor.capture());
        Appointment saved = captor.getValue();

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

        verify(scheduleManager, never()).updateAppointment(any(), any());
    }

    @Test
    @DisplayName("should respond not found when the appointment to update does not exist")
    void shouldRespondNotFound_whenAppointmentMissing() {
        when(appointmentManager.getAppointment(any(), eq(404))).thenReturn(null);

        AppointmentTo1 to = new AppointmentTo1();
        to.setId(404);

        assertThatThrownBy(() -> service.updateAppointment(to))
                .isInstanceOf(WebApplicationException.class)
                .satisfies(e -> assertThat(((WebApplicationException) e).getResponse().getStatus())
                        .isEqualTo(Response.Status.NOT_FOUND.getStatusCode()));

        verify(scheduleManager, never()).updateAppointment(any(), any());
    }
}
