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

import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONFormViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingONFormViewModel;
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
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViewBillingON2Action}.
 *
 * @since 2026-04-24
 */
@DisplayName("ViewBillingON2Action")
@Tag("unit")
@Tag("billing")
class ViewBillingON2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private BillingONFormViewModelAssembler mockAssembler;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    private static final BillingONFormViewModel STUB_MODEL =
            BillingONFormViewModel.builder().demographicNo("stub").build();

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("GET");

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);
        when(mockAssembler.assemble(any(), any())).thenReturn(STUB_MODEL);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    private ViewBillingON2Action newAction() {
        return new ViewBillingON2Action(mockSecurityInfoManager, mockAssembler);
    }

    @Test
    void shouldReturnSuccess_whenAuthorizedGetRequest() throws Exception {
        ViewBillingON2Action action = newAction();
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(action.getModel()).isSameAs(STUB_MODEL);
    }

    @Test
    void shouldReturnSuccess_whenAuthorizedPostRequest() throws Exception {
        mockRequest.setMethod("POST");
        ViewBillingON2Action action = newAction();
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldReturnSuccess_whenAuthorizedHeadRequest() throws Exception {
        mockRequest.setMethod("HEAD");
        ViewBillingON2Action action = newAction();
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldSendMethodNotAllowedAndReturnNone_whenDeleteRequest() throws Exception {
        mockRequest.setMethod("DELETE");
        ViewBillingON2Action action =
                new ViewBillingON2Action(mockSecurityInfoManager, mock(BillingONFormViewModelAssembler.class));

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("GET, HEAD, POST");
    }

    @Test
    void shouldThrowSecurityException_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        ViewBillingON2Action action =
                new ViewBillingON2Action(mockSecurityInfoManager, mock(BillingONFormViewModelAssembler.class));
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
        verify(mockSecurityInfoManager, never()).hasPrivilege(any(), eq("_billing"), eq("r"), isNull());
    }

    @Test
    void shouldThrowSecurityException_whenLacksBillingReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(false);

        ViewBillingON2Action action =
                new ViewBillingON2Action(mockSecurityInfoManager, mock(BillingONFormViewModelAssembler.class));
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }

    @Test
    void shouldDelegateToAssembler_andExposeItsModel() throws Exception {
        ViewBillingON2Action action = newAction();

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(mockAssembler).assemble(any(), any());
        assertThat(action.getModel()).isSameAs(STUB_MODEL);
    }

    @Test
    void shouldExposeModel_asRequestAttribute() throws Exception {
        ViewBillingON2Action action = newAction();
        action.execute();

        Object attr = mockRequest.getAttribute("formModel");
        assertThat(attr).isInstanceOf(BillingONFormViewModel.class);
        assertThat(attr).isSameAs(action.getModel());
    }
}
