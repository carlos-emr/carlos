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
package io.github.carlos_emr.carlos.security;

import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.MfaManager;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MfaActions2Action resetMfa hardening")
@Tag("unit")
@Tag("security")
class MfaActions2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityManager securityManager;
    private MfaManager mfaManager;
    private CarlosMethodSecurity methodSecurity;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityManager = mock(SecurityManager.class);
        mfaManager = mock(MfaManager.class);
        methodSecurity = mock(CarlosMethodSecurity.class);
        loggedInInfo = mock(LoggedInInfo.class);

        request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("method", MfaActions2Action.METHOD_RESET_MFA);
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
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

    private MfaActions2Action newAction() {
        return new MfaActions2Action(securityManager, mfaManager, methodSecurity);
    }

    @Test
    @DisplayName("should reject reset when caller lacks admin write privilege")
    void shouldRejectReset_whenCallerLacksAdminWrite() {
        when(methodSecurity.hasAdminWrite()).thenReturn(false);
        request.setParameter("securityId", "42");

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class);

        verify(mfaManager, never()).resetMfaSecret(any(), any());
    }

    @Test
    @DisplayName("should reject reset when request method is not POST")
    void shouldRejectReset_whenMethodIsNotPost() throws Exception {
        when(methodSecurity.hasAdminWrite()).thenReturn(true);
        request.setMethod("GET");
        request.setParameter("securityId", "42");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        verify(mfaManager, never()).resetMfaSecret(any(), any());
    }

    @Test
    @DisplayName("should return bad request when securityId is missing")
    void shouldReturnBadRequest_whenSecurityIdIsMissing() throws Exception {
        when(methodSecurity.hasAdminWrite()).thenReturn(true);

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verify(mfaManager, never()).resetMfaSecret(any(), any());
    }

    @Test
    @DisplayName("should return bad request when securityId is not numeric")
    void shouldReturnBadRequest_whenSecurityIdIsNotNumeric() throws Exception {
        when(methodSecurity.hasAdminWrite()).thenReturn(true);
        request.setParameter("securityId", "abc");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verify(mfaManager, never()).resetMfaSecret(any(), any());
    }

    @Test
    @DisplayName("should return not found when security record does not exist")
    void shouldReturnNotFound_whenSecurityRecordDoesNotExist() throws Exception {
        when(methodSecurity.hasAdminWrite()).thenReturn(true);
        when(securityManager.find(eq(loggedInInfo), anyInt())).thenReturn(null);
        request.setParameter("securityId", "42");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(404);
        verify(mfaManager, never()).resetMfaSecret(any(), any());
    }

    @Test
    @DisplayName("should reset MFA secret and write JSON when authorized and record exists")
    void shouldResetMfaSecret_whenAuthorizedAndRecordExists() throws Exception {
        when(methodSecurity.hasAdminWrite()).thenReturn(true);
        Security security = mock(Security.class);
        when(securityManager.find(eq(loggedInInfo), eq(42))).thenReturn(security);
        request.setParameter("securityId", "42");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).isEqualTo("{\"success\":true}");
        verify(mfaManager).resetMfaSecret(loggedInInfo, security);
    }
}
