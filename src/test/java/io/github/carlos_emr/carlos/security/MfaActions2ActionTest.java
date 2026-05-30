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
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.SecurityManager;
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
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("MfaActions2Action Tests")
@Tag("unit")
@Tag("security")
class MfaActions2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo loggedInInfo;
    private SecurityManager securityManager;
    private MfaManager mfaManager;
    private SecurityInfoManager securityInfoManager;
    private MfaActions2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        loggedInInfo = mock(LoggedInInfo.class);
        securityManager = mock(SecurityManager.class);
        mfaManager = mock(MfaManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);

        registerMock(SecurityManager.class, securityManager);
        registerMock(MfaManager.class, mfaManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        request.setMethod("POST");
        request.setParameter("method", MfaActions2Action.METHOD_RESET_MFA);
        request.setParameter("securityId", "123");
        when(securityInfoManager.hasPrivilege(
                eq(loggedInInfo), eq("_admin.userSecurity"), eq("w"), isNull()))
                .thenReturn(true);

        action = new MfaActions2Action();
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
    void shouldRejectGet_forResetMfaRequest() throws Exception {
        request.setMethod("GET");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityManager, mfaManager);
    }

    @Test
    void shouldRejectRequest_forNonResetMfaMethod() throws Exception {
        request.setParameter("method", "view");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityManager, mfaManager);
    }

    @Test
    void shouldThrowSecurityException_forDeniedAdminPrivilege() {
        when(securityInfoManager.hasPrivilege(
                eq(loggedInInfo), eq("_admin.userSecurity"), eq("w"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.userSecurity");
        verifyNoInteractions(securityManager, mfaManager);
    }

    @Test
    void shouldRejectRequest_forInvalidSecurityId() throws Exception {
        request.setParameter("securityId", "not-a-number");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(securityManager, mfaManager);
    }

    @Test
    void shouldRejectRequest_forNonexistentSecurityRecord() throws Exception {
        when(securityManager.find(loggedInInfo, 123)).thenReturn(null);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verifyNoInteractions(mfaManager);
    }

    @Test
    void shouldResetMfaSecret_forAuthorizedValidPost() throws Exception {
        Security security = mock(Security.class);
        when(securityManager.find(loggedInInfo, 123)).thenReturn(security);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verify(securityManager).find(loggedInInfo, 123);
        verify(mfaManager).resetMfaSecret(loggedInInfo, security);
    }
}
