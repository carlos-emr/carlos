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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingCodeData;
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
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeletePrivateCode2Action}.
 *
 * @since 2026-04-20
 */
@DisplayName("DeletePrivateCode2Action")
@Tag("unit")
@Tag("billing")
class DeletePrivateCode2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    void shouldReturn400_whenCodeMissing() throws Exception {
        try (MockedConstruction<BillingCodeData> ignored = mockConstruction(BillingCodeData.class)) {
            DeletePrivateCode2Action action = new DeletePrivateCode2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(mockResponse.getErrorMessage()).isEqualTo("code is required");
            assertThat(ignored.constructed()).isEmpty();
        }
    }

    @Test
    void shouldReturn400_whenCodeIsBlank() throws Exception {
        mockRequest.setParameter("code", "   ");

        try (MockedConstruction<BillingCodeData> ignored = mockConstruction(BillingCodeData.class)) {
            DeletePrivateCode2Action action = new DeletePrivateCode2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(mockResponse.getErrorMessage()).isEqualTo("code is required");
            assertThat(ignored.constructed()).isEmpty();
        }
    }

    @Test
    void shouldReturn400_whenCodeIsNonNumeric() throws Exception {
        mockRequest.setParameter("code", "abc");

        try (MockedConstruction<BillingCodeData> ignored = mockConstruction(BillingCodeData.class)) {
            DeletePrivateCode2Action action = new DeletePrivateCode2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(mockResponse.getErrorMessage()).isEqualTo("code must be numeric");
            assertThat(ignored.constructed()).isEmpty();
        }
    }

    @Test
    void shouldReturnSuccess_whenBillingCodeDoesNotExist() throws Exception {
        mockRequest.setParameter("code", "123");

        try (MockedConstruction<BillingCodeData> mockedConstruction = mockConstruction(BillingCodeData.class,
                (mock, context) -> when(mock.deleteBillingCode("123")).thenReturn(false))) {
            DeletePrivateCode2Action action = new DeletePrivateCode2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(mockedConstruction.constructed()).hasSize(1);
            verify(mockedConstruction.constructed().get(0)).deleteBillingCode("123");
        }
    }

    @Test
    void shouldReturnSuccess_whenBillingCodeExists() throws Exception {
        mockRequest.setParameter("code", "123");

        try (MockedConstruction<BillingCodeData> mockedConstruction = mockConstruction(BillingCodeData.class,
                (mock, context) -> when(mock.deleteBillingCode("123")).thenReturn(true))) {
            DeletePrivateCode2Action action = new DeletePrivateCode2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(mockedConstruction.constructed()).hasSize(1);
            verify(mockedConstruction.constructed().get(0)).deleteBillingCode("123");
        }
    }
}
