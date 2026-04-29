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

import io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
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
 * Tests for {@link ManageCss2Action}'s default render path. Until this fix,
 * the action gated {@code save()} and {@code delete()} on {@code _admin/w}
 * but the bare {@code execute()} path forwarded to the form unauthenticated
 * — a privilege gap that the type-design review of PR #1967 surfaced.
 */
@DisplayName("ManageCss2Action")
@Tag("unit")
@Tag("billing")
class ManageCss2ActionUnitTest extends CarlosUnitTestBase {

    private MockHttpServletRequest mockRequest;
    private SecurityInfoManager mockSecurity;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private LoggedInInfo mockLoggedInInfo;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        mockSecurity = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, mockSecurity);
        registerMock(CSSStylesDAO.class, mock(CSSStylesDAO.class));

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse)
                .thenReturn(new MockHttpServletResponse());

        mockLoggedInInfo = mock(LoggedInInfo.class);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
    }

    @Test
    void shouldReturnInit_whenAuthorizedReadOfDefaultRender() throws Exception {
        when(mockSecurity.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("r"), isNull()))
                .thenReturn(true);

        assertThat(new ManageCss2Action().execute()).isEqualTo("init");
    }

    @Test
    void shouldThrowSecurityException_whenSessionMissing() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(null);

        assertThatThrownBy(() -> new ManageCss2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing session");
    }

    @Test
    void shouldThrowSecurityException_whenAdminReadPrivilegeMissing() {
        when(mockSecurity.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("r"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> new ManageCss2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin");
    }

    @Test
    void shouldRouteThroughSave_whenMethodIsSave() {
        // Pre-fix the action would still hit the save() gate; we just verify
        // the dispatcher routes by method param without touching the new
        // _admin/r gate (save() has its own _admin/w gate).
        mockRequest.setParameter("method", "save");
        when(mockSecurity.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("w"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> new ManageCss2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin");
    }
}
