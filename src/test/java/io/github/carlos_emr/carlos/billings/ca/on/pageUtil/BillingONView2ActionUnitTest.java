/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONFormViewModel;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BillingONView2Action}.
 *
 * @since 2026-04-24
 */
@DisplayName("BillingONView2Action")
@Tag("unit")
@Tag("billing")
class BillingONView2ActionUnitTest extends CarlosUnitTestBase {

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
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    /** A pre-built stub DTO the mocked assembler returns for the happy path. */
    private static final BillingONFormViewModel STUB_MODEL =
            BillingONFormViewModel.builder().demographicNo("stub").build();

    @Test
    void shouldReturnSuccess_whenAuthorizedGetRequest() throws Exception {
        try (MockedConstruction<BillingONFormDataAssembler> ignored = mockConstruction(
                BillingONFormDataAssembler.class,
                (mock, ctx) -> when(mock.assemble(any(), any())).thenReturn(STUB_MODEL))) {
            BillingONView2Action action = new BillingONView2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(action.getModel()).isSameAs(STUB_MODEL);
        }
    }

    @Test
    void shouldReturnSuccess_whenAuthorizedPostRequest() throws Exception {
        mockRequest.setMethod("POST");

        try (MockedConstruction<BillingONFormDataAssembler> ignored = mockConstruction(
                BillingONFormDataAssembler.class,
                (mock, ctx) -> when(mock.assemble(any(), any())).thenReturn(STUB_MODEL))) {
            BillingONView2Action action = new BillingONView2Action();
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        }
    }

    @Test
    void shouldReturnSuccess_whenAuthorizedHeadRequest() throws Exception {
        mockRequest.setMethod("HEAD");

        try (MockedConstruction<BillingONFormDataAssembler> ignored = mockConstruction(
                BillingONFormDataAssembler.class,
                (mock, ctx) -> when(mock.assemble(any(), any())).thenReturn(STUB_MODEL))) {
            BillingONView2Action action = new BillingONView2Action();
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        }
    }

    @Test
    void shouldSendMethodNotAllowedAndReturnNone_whenDeleteRequest() throws Exception {
        mockRequest.setMethod("DELETE");

        try (MockedConstruction<BillingONFormDataAssembler> ignored =
                mockConstruction(BillingONFormDataAssembler.class)) {
            BillingONView2Action action = new BillingONView2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(mockResponse.getHeader("Allow")).isEqualTo("GET, HEAD, POST");
        }
    }

    @Test
    void shouldThrowSecurityException_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        try (MockedConstruction<BillingONFormDataAssembler> ignored =
                mockConstruction(BillingONFormDataAssembler.class)) {
            BillingONView2Action action = new BillingONView2Action();

            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_billing");
        }
    }

    @Test
    void shouldThrowSecurityException_whenLacksBillingReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(false);

        try (MockedConstruction<BillingONFormDataAssembler> ignored =
                mockConstruction(BillingONFormDataAssembler.class)) {
            BillingONView2Action action = new BillingONView2Action();

            assertThatThrownBy(action::execute)
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_billing");
        }
    }

    @Test
    void shouldDelegateToAssembler_andExposeItsModel() throws Exception {
        try (MockedConstruction<BillingONFormDataAssembler> construction = mockConstruction(
                BillingONFormDataAssembler.class,
                (mock, ctx) -> when(mock.assemble(any(), any())).thenReturn(STUB_MODEL))) {
            BillingONView2Action action = new BillingONView2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(construction.constructed()).hasSize(1);
            assertThat(action.getModel()).isSameAs(STUB_MODEL);
        }
    }

    @Test
    void shouldExposeModelAsRequestAttribute() throws Exception {
        try (MockedConstruction<BillingONFormDataAssembler> ignored = mockConstruction(
                BillingONFormDataAssembler.class,
                (mock, ctx) -> when(mock.assemble(any(), any())).thenReturn(STUB_MODEL))) {
            BillingONView2Action action = new BillingONView2Action();
            action.execute();

            Object attr = mockRequest.getAttribute("model");
            assertThat(attr).isInstanceOf(BillingONFormViewModel.class);
            assertThat(attr).isSameAs(action.getModel());
        }
    }
}
