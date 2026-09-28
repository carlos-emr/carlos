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
package io.github.carlos_emr.carlos.form.gate;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ViewFormXmlUpload2Action tests")
@Tag("unit")
@Tag("form")
@Tag("security")
class ViewFormXmlUpload2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager securityInfoManager;
    @Mock
    private LoggedInInfo loggedInInfo;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private RequestDispatcher dispatcher;

    private ViewFormXmlUpload2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestDispatcher("/WEB-INF/jsp/form/formXmlUpload.jsp")).thenReturn(dispatcher);
        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_admin.eform"), eq("w"), isNull())).thenReturn(true);

        action = new ViewFormXmlUpload2Action(securityInfoManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("should forward the import page when the caller has _admin eForm write")
    void shouldForwardImportPage_whenAdminEformWriter() throws Exception {
        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(dispatcher).forward(request, response);
        verify(securityInfoManager, never()).hasPrivilege(
                any(LoggedInInfo.class), eq("_admin"), eq("w"), isNull());
    }

    @Test
    @DisplayName("should forward the import page when the caller has full admin write")
    void shouldForwardImportPage_whenAdminWriter() throws Exception {
        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_admin.eform"), eq("w"), isNull())).thenReturn(false);
        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_admin"), eq("w"), isNull())).thenReturn(true);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("should reject the import page when the caller has neither admin privilege")
    void shouldRejectImportPage_whenNoAdminPrivilege() {
        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_admin.eform"), eq("w"), isNull())).thenReturn(false);
        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_admin"), eq("w"), isNull())).thenReturn(false);

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.eform or _admin");
    }

    @Test
    @DisplayName("should reject the import page without a logged-in session")
    void shouldRejectImportPage_whenUnauthenticated() {
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(null);

        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin.eform or _admin");
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    @DisplayName("should forward the import page when the caller uses HEAD")
    void shouldForwardImportPage_whenHead() throws Exception {
        when(request.getMethod()).thenReturn("HEAD");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("should return method not allowed when the page route receives POST")
    void shouldReturnMethodNotAllowed_whenPost() throws Exception {
        when(request.getMethod()).thenReturn("POST");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(response).setHeader("Allow", "GET, HEAD");
        verify(response).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(securityInfoManager, never()).hasPrivilege(
                any(LoggedInInfo.class), any(String.class), any(String.class), isNull());
    }

    @Test
    @DisplayName("should not serialize the Spring security manager")
    void shouldNotSerializeSecurityInfoManager_whenActionIsSerialized() throws NoSuchFieldException {
        Field securityInfoManagerField = ViewFormXmlUpload2Action.class.getDeclaredField("securityInfoManager");

        assertThat(Modifier.isTransient(securityInfoManagerField.getModifiers())).isTrue();
    }
}
