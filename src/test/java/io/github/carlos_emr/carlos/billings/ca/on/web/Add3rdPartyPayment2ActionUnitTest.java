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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONCorrectionViewModel;
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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingONCorrectionDataAssembler;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionService;

/**
 * Unit tests for {@link Add3rdPartyPayment2Action} — the POST-only
 * 3rd-party payment endpoint extracted from the legacy
 * {@code BillingCorrection2Action} method-param dispatch.
 *
 * <p>Covers the gate contract: null-session guard, missing-privilege
 * guard, 405 on non-POST methods with {@code Allow: POST} header, and
 * delegation to {@link BillingCorrectionService#addThirdPartyPayment}.</p>
 *
 * @since 2026-04-25
 */
@DisplayName("Add3rdPartyPayment2Action")
@Tag("unit")
@Tag("billing")
class Add3rdPartyPayment2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mockitoCloseable;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private BillingONCorrectionDataAssembler mockAssembler;

    @Mock
    private BillingCorrectionService mockService;

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

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockAssembler.assemble(any(LoggedInInfo.class), any(HttpServletRequest.class)))
                .thenReturn(BillingONCorrectionViewModel.builder().build());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    private Add3rdPartyPayment2Action newAction() {
        return new Add3rdPartyPayment2Action(
                mockSecurityInfoManager, mockAssembler, mockService);
    }

    @Test
    void shouldDelegateToService_whenAuthorisedPost() {
        when(mockService.addThirdPartyPayment(any(LoggedInInfo.class), any(HttpServletRequest.class)))
                .thenReturn("success");

        String result = newAction().execute();

        assertThat(result).isEqualTo("success");
        verify(mockService).addThirdPartyPayment(eq(mockLoggedInInfo), eq(mockRequest));
        assertThat(mockRequest.getAttribute("correctionModel")).isNotNull();
    }

    @Test
    void shouldThrowSecurityException_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing session");

        verify(mockService, never()).addThirdPartyPayment(any(), any());
    }

    @Test
    void shouldThrowSecurityException_whenLackingBillingWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");

        verify(mockService, never()).addThirdPartyPayment(any(), any());
    }

    @Test
    void shouldReturn405WithAllowHeader_whenNotPost() {
        mockRequest.setMethod("GET");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verify(mockService, never()).addThirdPartyPayment(any(), any());
    }
}
