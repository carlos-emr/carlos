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
package io.github.carlos_emr.carlos.admin.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for security action facility guards.
 *
 * @since 2026-05-31
 */
@DisplayName("Security action facility guard")
@Tag("unit")
@Tag("admin")
@Tag("security")
class SecurityActionFacilityGuardTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock
    private SecurityInfoManager securityInfoManager;
    @Mock
    private LoggedInInfo loggedInInfo;
    @Mock
    private SecurityRecordAccessGuard securityRecordAccessGuard;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        request = new MockHttpServletRequest();
        request.setMethod("POST");
        response = new MockHttpServletResponse();

        registerMock(SecurityInfoManager.class, securityInfoManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should reject update when security record is outside current facility")
    void shouldRejectUpdate_whenSecurityRecordIsOutsideCurrentFacility() throws Exception {
        request.setParameter("security_no", "42");

        Security security = new Security();
        when(securityRecordAccessGuard.parseSecurityId("42")).thenReturn(42);
        when(securityRecordAccessGuard.findSecurity(42)).thenReturn(security);
        when(securityRecordAccessGuard.hasCurrentFacilityAccess(loggedInInfo, security)).thenReturn(false);

        SecurityUpdate2Action action = new SecurityUpdate2Action();
        injectDependency(action, "securityRecordAccessGuard", securityRecordAccessGuard);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getErrorMessage()).isEqualTo("Cross-facility access denied");
    }

    @Test
    @DisplayName("should reject security add when provider is outside current facility")
    void shouldRejectSecurityAdd_whenProviderIsOutsideCurrentFacility() throws Exception {
        request.setParameter("provider_no", "prov-outside");
        when(securityRecordAccessGuard.hasCurrentFacilityAccess(loggedInInfo, "prov-outside")).thenReturn(false);

        SecurityAddSecurity2Action action = new SecurityAddSecurity2Action();
        injectDependency(action, "securityRecordAccessGuard", securityRecordAccessGuard);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getErrorMessage()).isEqualTo("Cross-facility access denied");
    }

    @Test
    @DisplayName("should accept update when security record belongs to current facility")
    void shouldAcceptUpdate_whenSecurityRecordBelongsToCurrentFacility() throws Exception {
        request.setParameter("security_no", "42");

        Security security = new Security();
        when(securityRecordAccessGuard.parseSecurityId("42")).thenReturn(42);
        when(securityRecordAccessGuard.findSecurity(42)).thenReturn(security);
        when(securityRecordAccessGuard.hasCurrentFacilityAccess(loggedInInfo, security)).thenReturn(true);

        SecurityUpdate2Action action = new SecurityUpdate2Action();
        injectDependency(action, "securityRecordAccessGuard", securityRecordAccessGuard);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }
}
