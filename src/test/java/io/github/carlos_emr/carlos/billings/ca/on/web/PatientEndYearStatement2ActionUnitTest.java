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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the initial-render gate {@link PatientEndYearStatement2Action}.
 *
 * @since 2026-04-27
 */
@DisplayName("PatientEndYearStatement2Action")
@Tag("unit")
@Tag("billing")
class PatientEndYearStatement2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("GET");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldReturnSuccessClearStaleSummaryAndEchoNames() {
        // Pre-populate the session with a stale summary; the initial render
        // should clear it so the JSP doesn't show a leftover invoice table.
        mockRequest.getSession(true).setAttribute("summary", "stale");
        mockRequest.setParameter("firstNameParam", "Jane");
        mockRequest.setParameter("lastNameParam", "Doe");

        PatientEndYearStatement2Action action =
                new PatientEndYearStatement2Action(mockSecurityInfoManager);
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(mockRequest.getSession().getAttribute("summary")).isNull();
        assertThat(mockRequest.getAttribute("firstNameParamEcho")).isEqualTo("Jane");
        assertThat(mockRequest.getAttribute("lastNameParamEcho")).isEqualTo("Doe");
        assertThat(mockRequest.getAttribute("patientNameDisplay")).isEqualTo("Jane Doe");
    }

    @Test
    void shouldEchoEmptyDisplayName_whenOnlyOneNameSupplied() {
        mockRequest.setParameter("firstNameParam", "Jane");
        // lastNameParam missing -> displayName is empty (not "Jane null")
        PatientEndYearStatement2Action action =
                new PatientEndYearStatement2Action(mockSecurityInfoManager);
        action.execute();

        assertThat(mockRequest.getAttribute("patientNameDisplay")).isEqualTo("");
    }

    @Test
    void shouldThrowSecurityException_whenLackingBillingReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(false);

        PatientEndYearStatement2Action action =
                new PatientEndYearStatement2Action(mockSecurityInfoManager);
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }
}
