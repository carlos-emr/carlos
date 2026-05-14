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

import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnMriViewModelAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingDataLoadException;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnMriViewModel;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViewBillingOnMri2Action}.
 *
 * <p>The missing-session case intentionally returns 401 before privilege checks run. That keeps
 * login/session regressions separate from billing privilege failures when this view is reached
 * through Struts, while avoiding a noisy server-side security exception for simple session loss.</p>
 *
 * @since 2026-05-10
 */
@DisplayName("ViewBillingOnMri2Action")
@Tag("unit")
@Tag("billing")
class ViewBillingOnMri2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private BillingOnMriViewModelAssembler mockAssembler;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    private static final BillingOnMriViewModel STUB_MODEL =
            BillingOnMriViewModel.builder().selectedYear("2026").build();

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("GET");

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(BillingOnMriViewModelAssembler.class, mockAssembler);

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

    private ViewBillingOnMri2Action newAction() {
        return new ViewBillingOnMri2Action(mockSecurityInfoManager, mockAssembler);
    }

    @Test
    void shouldReturnSuccess_whenAssemblerSucceeds() throws Exception {
        assertThat(newAction().execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldExposeModel_asRequestAttribute() throws Exception {
        newAction().execute();
        assertThat(mockRequest.getAttribute("mriModel")).isSameAs(STUB_MODEL);
    }

    @Test
    void shouldReturnUnauthorized_whenSessionMissing() throws Exception {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        assertThat(newAction().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(401);
        verify(mockSecurityInfoManager, never()).hasPrivilege(any(), any(), any(), any());
    }

    @Test
    void shouldThrowSecurityException_whenLacksBillingReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(newAction()::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }

    @Test
    void shouldWrapRuntimeException_asBillingDataLoadException() {
        RuntimeException cause = new NullPointerException("null bill center");
        when(mockAssembler.assemble(any(), any())).thenThrow(cause);

        assertThatThrownBy(newAction()::execute)
                .isInstanceOf(BillingDataLoadException.class)
                .hasMessageContaining("OHIP report view model")
                .hasCause(cause);
    }

    @Test
    void shouldRethrowBillingDataLoadException_unchanged() {
        BillingDataLoadException original = new BillingDataLoadException("disk error");
        when(mockAssembler.assemble(any(), any())).thenThrow(original);

        assertThatThrownBy(newAction()::execute)
                .isSameAs(original);
    }
}
