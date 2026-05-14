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

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the CARLOS method-security expression helper.
 *
 * @since 2026-05-06
 */
@DisplayName("CarlosMethodSecurity")
@Tag("unit")
@Tag("security")
class CarlosMethodSecurityTest extends CarlosUnitTestBase {

    private AutoCloseable mocks;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private LoggedInInfo loggedInInfo;

    private CarlosMethodSecurity methodSecurity;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        methodSecurity = new CarlosMethodSecurity(securityInfoManager);
        servletActionContextMock = mockStatic(ServletActionContext.class);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mocks != null) mocks.close();
    }

    @Test
    @DisplayName("should return false when privilege request has blank input")
    void shouldReturnFalse_whenPrivilegeRequestHasBlankInput() {
        assertThat(methodSecurity.hasPrivilege(null, "w")).isFalse();
        assertThat(methodSecurity.hasPrivilege("", "w")).isFalse();
        assertThat(methodSecurity.hasPrivilege("_admin", null)).isFalse();
        assertThat(methodSecurity.hasPrivilege("_admin", " ")).isFalse();
        verifyNoInteractions(securityInfoManager);
        servletActionContextMock.verifyNoInteractions();
        loggedInInfoMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should return false when no Struts request exists")
    void shouldReturnFalse_whenNoStrutsRequestExists() {
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(null);

        boolean allowed = methodSecurity.hasPrivilege("_admin", "w");

        assertThat(allowed).isFalse();
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    @DisplayName("should return false when session has no LoggedInInfo")
    void shouldReturnFalse_whenSessionHasNoLoggedInInfo() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(null);

        boolean allowed = methodSecurity.hasPrivilege("_admin", "w");

        assertThat(allowed).isFalse();
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    @DisplayName("should delegate to SecurityInfoManager when session context exists")
    void shouldDelegate_whenSessionContextExists() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(loggedInInfo);
        when(securityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull())).thenReturn(true);

        boolean allowed = methodSecurity.hasPrivilege("_admin", "w");

        assertThat(allowed).isTrue();
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin", "w", null);
    }

    @Test
    @DisplayName("should return true when admin write privilege exists")
    void shouldReturnTrue_whenAdminWritePrivilegeExists() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(loggedInInfo);
        when(securityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull())).thenReturn(true);

        boolean allowed = methodSecurity.hasAdminWrite();

        assertThat(allowed).isTrue();
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin", "w", null);
    }

    @Test
    @DisplayName("should return true when user-admin write privilege exists")
    void shouldReturnTrue_whenUserAdminWritePrivilegeExists() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(loggedInInfo);
        when(securityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull())).thenReturn(false);
        when(securityInfoManager.hasPrivilege(any(), eq("_admin.userAdmin"), eq("w"), isNull())).thenReturn(true);

        boolean allowed = methodSecurity.hasAdminWrite();

        assertThat(allowed).isTrue();
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin", "w", null);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin.userAdmin", "w", null);
    }

    @Test
    @DisplayName("should return false when admin write privileges are denied")
    void shouldReturnFalse_whenAdminWritePrivilegesAreDenied() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(loggedInInfo);
        when(securityInfoManager.hasPrivilege(any(), eq("_admin"), eq("w"), isNull())).thenReturn(false);
        when(securityInfoManager.hasPrivilege(any(), eq("_admin.userAdmin"), eq("w"), isNull())).thenReturn(false);

        boolean allowed = methodSecurity.hasAdminWrite();

        assertThat(allowed).isFalse();
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin", "w", null);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_admin.userAdmin", "w", null);
    }
}
