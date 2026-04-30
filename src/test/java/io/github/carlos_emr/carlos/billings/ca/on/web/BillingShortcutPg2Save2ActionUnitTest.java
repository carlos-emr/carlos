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

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingShortcutPg2Service;
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
 * Pins the privilege gate, missing-session guard, and POST-only contract on
 * {@link BillingShortcutPg2Save2Action}.
 *
 * <p>The action delegates persistence to {@link BillingShortcutPg2Service}.
 * Without a POST gate a forged GET URL with the form params would drive the
 * persist path, sidestepping CSRFGuard's body-token validation (the default
 * config only fires on non-GET request bodies).
 *
 * @since 2026-04-30
 */
@DisplayName("BillingShortcutPg2Save2Action")
@Tag("unit")
@Tag("billing")
class BillingShortcutPg2Save2ActionUnitTest extends CarlosUnitTestBase {

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private SecurityInfoManager mockSecurityInfoManager;
    private BillingShortcutPg2Service mockAssembler;
    private LoggedInInfo mockLoggedInInfo;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");

        mockSecurityInfoManager = mock(SecurityInfoManager.class);
        mockAssembler = mock(BillingShortcutPg2Service.class);
        mockLoggedInInfo = mock(LoggedInInfo.class);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    private BillingShortcutPg2Save2Action newAction() {
        return new BillingShortcutPg2Save2Action(mockSecurityInfoManager, mockAssembler);
    }

    @Test
    void shouldThrowSecurityException_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing session");
    }

    @Test
    void shouldThrowSecurityException_whenLackingBillingWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("w"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_billing");
    }

    @Test
    void shouldReturn405WithAllowHeader_whenInvokedViaGet() throws Exception {
        // The whole point of this test class — a forged GET must not drive
        // the persist path. The HttpMethodGuardFilter blocks at the filter
        // tier for canonical mutator names, but BillingShortcutPg2Save isn't
        // in that list so the action must self-gate.
        mockRequest.setMethod("GET");

        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    void shouldReturnBackToEdit_whenButtonIsBackToEdit() throws Exception {
        mockRequest.setParameter("button", "Back to Edit");

        assertThat(newAction().execute()).isEqualTo("backToEdit");
    }

    @Test
    void shouldReturnSuccess_whenAuthorizedPostWithoutBackButton() throws Exception {
        // Drives the assembler — happy-path persist + render.
        String result = newAction().execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
    }
}
