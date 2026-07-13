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
package io.github.carlos_emr.carlos.dxresearch.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.DxDao;
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
import static org.mockito.Mockito.when;

@DisplayName("DxResearchLoadAssociations2Action Unit Tests")
@Tag("unit")
@Tag("dxresearch")
class DxResearchLoadAssociations2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private LoggedInInfo mockLoggedInInfo;
    @Mock private DxDao mockDxDao;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private dxResearchLoadAssociations2Action action;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(DxDao.class, mockDxDao);

        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("GET");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_dxresearch"), eq("r"), isNull()))
                .thenReturn(true);

        action = new dxResearchLoadAssociations2Action();
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should require read privilege for initial associations view load")
    void shouldRequireReadPrivilege_forInitialAssociationsViewLoad() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_dxresearch"), eq("r"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_dxresearch r");
    }

    @Test
    @DisplayName("should send 405 when mutation is attempted with GET")
    void shouldSend405_whenMutationAttemptedWithGet() throws Exception {
        mockRequest.setParameter("method", "clearAssociations");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}
