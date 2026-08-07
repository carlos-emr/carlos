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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BillingResearchCodeUpdate2Action}. Pins
 * {@code _billing/w} privilege gate, POST-only contract, and the
 * code-extraction logic that pulls up to 3 research codes from
 * {@code code_*} request params.
 *
 * @since 2026-04-29
 */
@DisplayName("BillingResearchCodeUpdate2Action")
@Tag("unit")
@Tag("billing")
class BillingResearchCodeUpdate2ActionUnitTest extends CarlosUnitTestBase {

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
        mockRequest.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldExtractCodesAndReturnSuccess_whenPrivilegeGranted() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        mockRequest.setParameter("code_a01", "x");
        mockRequest.setParameter("code_b02", "x");

        BillingResearchCodeUpdate2Action action =
                new BillingResearchCodeUpdate2Action(mockSecurityInfoManager);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        // Suffix is uppercased; order is parameter-iteration-order which is
        // implementation-defined, so just assert the set.
        assertThat(mockRequest.getAttribute("researchCodeCount")).isEqualTo(2);
        verify(mockSecurityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull());
    }

    @Test
    void shouldThrowSecurityException_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);

        BillingResearchCodeUpdate2Action action =
                new BillingResearchCodeUpdate2Action(mockSecurityInfoManager);

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }

    @Test
    void shouldReturn405_whenNotPost() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        mockRequest.setMethod("GET");

        BillingResearchCodeUpdate2Action action =
                new BillingResearchCodeUpdate2Action(mockSecurityInfoManager);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(405);
    }

    @Test
    void shouldCapAtThreeCodes_whenMoreThanThreeProvided() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        mockRequest.setParameter("code_a01", "x");
        mockRequest.setParameter("code_b02", "x");
        mockRequest.setParameter("code_c03", "x");
        mockRequest.setParameter("code_d04", "x");

        BillingResearchCodeUpdate2Action action =
                new BillingResearchCodeUpdate2Action(mockSecurityInfoManager);

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        // Total count reflects all 4 matched params; only first 3 slots fill.
        assertThat(mockRequest.getAttribute("researchCodeCount")).isEqualTo(4);
        // researchCode0/1/2 must each be a non-empty 3-char uppercase string.
        for (int i = 0; i < 3; i++) {
            assertThat((String) mockRequest.getAttribute("researchCode" + i)).hasSize(3);
        }
    }
}
