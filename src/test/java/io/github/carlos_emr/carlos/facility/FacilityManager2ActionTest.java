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
package io.github.carlos_emr.carlos.facility;

import io.github.carlos_emr.carlos.commn.dao.FacilityDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Security regression coverage for facility management mutations.
 *
 * @since 2026-05-28
 */
@DisplayName("FacilityManager2Action")
@Tag("unit")
@Tag("facility")
@Tag("security")
class FacilityManager2ActionTest extends CarlosUnitTestBase {

    @Mock private FacilityDao facilityDao;
    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private LoggedInInfo loggedInInfo;

    private AutoCloseable mocks;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest("GET", "/FacilityManager");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("should deny access when _admin.facility write privilege is missing")
    void shouldDenyAccess_whenFacilityAdminWriteMissing() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.facility", "w", null))
                .thenReturn(false);

        FacilityManager2Action action = action();

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.facility");
        verifyNoInteractions(facilityDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"delete", "save", "add"})
    @DisplayName("should reject mutating methods when HTTP method is not POST")
    void shouldRejectMutation_whenHttpMethodIsNotPost(String method) throws Exception {
        allowFacilityAdminWrite();
        request.setMethod("GET");
        request.addParameter("method", method);

        String result = action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verify(facilityDao, never()).find(any());
        verify(facilityDao, never()).persist(any());
        verify(facilityDao, never()).merge(any());
    }

    @Test
    @DisplayName("should disable facility on authorized delete POST")
    void shouldDisableFacility_onAuthorizedDeletePost() throws Exception {
        allowFacilityAdminWrite();
        request.setMethod("POST");
        request.addParameter("method", "delete");
        request.addParameter("id", "7");
        Facility facility = new Facility();
        facility.setId(7);
        when(facilityDao.find(7)).thenReturn(facility);

        String result = action().execute();

        assertThat(result).isEqualTo("list");
        assertThat(facility.isDisabled()).isTrue();
        verify(facilityDao).merge(facility);
        verify(facilityDao).findAll(true);
    }

    private void allowFacilityAdminWrite() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin.facility", "w", null))
                .thenReturn(true);
    }

    private FacilityManager2Action action() {
        return new FacilityManager2Action(facilityDao, securityInfoManager);
    }
}
