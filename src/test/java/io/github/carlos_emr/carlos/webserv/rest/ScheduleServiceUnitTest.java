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

import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.carlos_emr.carlos.appointment.search.FilterDefinition;
import io.github.carlos_emr.carlos.commn.dao.AppointmentSearchDao;
import io.github.carlos_emr.carlos.commn.model.AppointmentSearch;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.SearchConfigTo1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

    private ScheduleService service;

    @BeforeEach
    void setUp() {
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        service = new ScheduleService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };

        injectDependency(service, "securityInfoManager", securityInfoManager);
        injectDependency(service, "appointmentSearchDao", appointmentSearchDao);

        when(securityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("w"), any())).thenReturn(true);
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
        verify(appointmentSearchDao, never()).find(any());
    }

    @Test
    @DisplayName("should return forbidden when caller lacks appointment write privilege")
    void shouldReturnForbidden_whenCallerLacksAppointmentWritePrivilege() {
        when(securityInfoManager.hasPrivilege(any(), eq("_appointment"), eq("w"), any())).thenReturn(false);

        Response response = service.saveSearchConfig(123, new SearchConfigTo1());

        assertThat(response.getStatus()).isEqualTo(Response.Status.FORBIDDEN.getStatusCode());
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
}
