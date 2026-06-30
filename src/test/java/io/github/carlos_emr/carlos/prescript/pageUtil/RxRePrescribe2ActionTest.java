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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.managers.PrescriptionManager;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RxRePrescribe2Action prescription signature tests")
@Tag("unit")
@Tag("prescript")
class RxRePrescribe2ActionTest extends CarlosUnitTestBase {

    private static final int SCRIPT_ID = 1234;
    private static final int SIGNATURE_ID = 5678;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private PrescriptionManager mockPrescriptionManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private RxRePrescribe2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setRemoteAddr("127.0.0.1");

        RxSessionBean rxSessionBean = new RxSessionBean();
        rxSessionBean.setDemographicNo(1);
        rxSessionBean.setProviderNo("999998");
        request.getSession().setAttribute("RxSessionBean", rxSessionBean);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(PrescriptionManager.class, mockPrescriptionManager);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        action = new RxRePrescribe2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("should associate a saved digital signature with a prescription")
    void shouldAssociateSavedDigitalSignature_withPrescription() throws Exception {
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));

        String result = action.saveDigitalSignature();

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verify(mockSecurityInfoManager).hasPrivilege(mockLoggedInInfo, "_rx", "w", null);
        verify(mockPrescriptionManager).setPrescriptionSignature(mockLoggedInInfo, SCRIPT_ID, SIGNATURE_ID);
    }

    @Test
    @DisplayName("should clear the prescription signature when signature id is absent")
    void shouldClearPrescriptionSignature_whenSignatureIdIsAbsent() throws Exception {
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));

        String result = action.saveDigitalSignature();

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verify(mockPrescriptionManager).setPrescriptionSignature(mockLoggedInInfo, SCRIPT_ID, null);
    }

    @Test
    @DisplayName("should reject malformed digital signature ids")
    void shouldRejectMalformedDigitalSignature_whenIdIsMalformed() throws Exception {
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", "7<script>");

        String result = action.saveDigitalSignature();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(mockPrescriptionManager, never()).setPrescriptionSignature(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("should reject malformed prescription script ids")
    void shouldRejectMalformedScriptId_whenIdIsMalformed() throws Exception {
        request.setParameter("scriptId", "../123");
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));

        String result = action.saveDigitalSignature();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verify(mockPrescriptionManager, never()).setPrescriptionSignature(any(), any(Integer.class), any());
    }

    @Test
    @DisplayName("should redirect when prescription session is missing")
    void shouldRedirect_whenPrescriptionSessionIsMissing() throws Exception {
        request.getSession().removeAttribute("RxSessionBean");
        request.setParameter("scriptId", String.valueOf(SCRIPT_ID));
        request.setParameter("digitalSignatureId", String.valueOf(SIGNATURE_ID));

        String result = action.saveDigitalSignature();

        assertThat(result).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("error.html");
        verify(mockPrescriptionManager, never()).setPrescriptionSignature(any(), any(Integer.class), any());
    }
}
