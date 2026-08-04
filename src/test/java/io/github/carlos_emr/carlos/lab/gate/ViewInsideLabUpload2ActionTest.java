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
package io.github.carlos_emr.carlos.lab.gate;

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
import org.mockito.MockedStatic;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViewInsideLabUpload2Action}, the GET-only view gate
 * for the HL7 lab upload page (admin &gt; Labs &gt; HL7 Lab upload). Verifies
 * privilege enforcement and HTTP method gating mirroring the pattern used by
 * other lab/share gate actions.
 *
 * @since 2026-05-03
 */
@DisplayName("ViewInsideLabUpload2Action Unit Tests")
@Tag("unit")
@Tag("gate")
@Tag("lab")
class ViewInsideLabUpload2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager mockSecurityInfoManager;
    private LoggedInInfo mockLoggedInInfo;
    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private ViewInsideLabUpload2Action action;

    @BeforeEach
    void setUp() {
        mockSecurityInfoManager = mock(SecurityInfoManager.class);
        mockLoggedInInfo = mock(LoggedInInfo.class);
        mockRequest = new MockHttpServletRequest();
        mockRequest.setMethod("GET");
        mockResponse = new MockHttpServletResponse();
        stubServletActionContext();
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        stubLoggedInInfo(mockLoggedInInfo);
        stubLabWritePrivilege(false);
        action = new ViewInsideLabUpload2Action(mockSecurityInfoManager);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldReturnSuccess_whenDisplayMethodRequest(String method) throws Exception {
        stubLabWritePrivilege(true);
        mockRequest.setMethod(method);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldThrow_whenSessionMissing() {
        stubLoggedInInfo(null);

        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class)
                .hasMessageContaining(ViewInsideLabUpload2Action.VIEW_ROUTE);
    }

    @Test
    void shouldThrow_whenPrivilegeMissing() {
        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class)
                .hasMessageContaining(ViewInsideLabUpload2Action.VIEW_ROUTE);
    }

    @Test
    void shouldSend405_onPost() throws Exception {
        stubLabWritePrivilege(true);
        mockRequest.setMethod("POST");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("GET, HEAD");
    }

    private void stubServletActionContext() {
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);
    }

    private void stubLoggedInInfo(LoggedInInfo loggedInInfo) {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    private void stubLabWritePrivilege(boolean allowed) {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_lab"), eq("w"), isNull()))
                .thenReturn(allowed);
    }
}
