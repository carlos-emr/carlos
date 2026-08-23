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
package io.github.carlos_emr.carlos.ui.servlet;

import io.github.carlos_emr.carlos.casemgmt.dao.ClientImageDAO;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SessionConstants;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ImageRenderingServlet Unit Tests")
@Tag("unit")
@Tag("servlet")
class ImageRenderingServletTest extends CarlosUnitTestBase {

    private DigitalSignatureManager digitalSignatureManager;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private ImageRenderingServlet servlet;

    @BeforeEach
    void setUp() {
        digitalSignatureManager = mock(DigitalSignatureManager.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);

        registerMock(ClientImageDAO.class, mock(ClientImageDAO.class));
        registerMock(DigitalSignatureManager.class, digitalSignatureManager);
        registerMock(SecurityInfoManager.class, securityInfoManager);

        request = new MockHttpServletRequest("GET", "/imageRenderingServlet");
        response = new MockHttpServletResponse();
        request.getSession().setAttribute(SessionConstants.LOGGED_IN_PROVIDER, new Provider("999998"));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        request.setParameter("source", ImageRenderingServlet.Source.signature_stored.name());
        request.setParameter("digitalSignatureId", "42");

        servlet = new ImageRenderingServlet();
    }

    @Test
    @DisplayName("should render stored consultation signature when consult read is granted for the demographic")
    void shouldRenderConsultSignature_whenConsultReadGrantedForDemographic() throws Exception {
        DigitalSignature metadata = metadata(ModuleType.CONSULTATION, 123);
        DigitalSignature signature = signature();
        when(digitalSignatureManager.getDigitalSignatureMetadata(42)).thenReturn(metadata);
        when(digitalSignatureManager.getDigitalSignature(42)).thenReturn(signature);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, "123"))
                .thenReturn(true);

        servlet.doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getContentType()).isEqualTo("image/jpeg");
        assertThat(response.getContentAsByteArray()).isEqualTo(signature.getSignatureImage());
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, "123");
    }

    @Test
    @DisplayName("should forbid stored consultation signature when consult read is denied for the demographic")
    void shouldForbidConsultSignature_whenConsultReadDeniedForDemographic() throws Exception {
        when(digitalSignatureManager.getDigitalSignatureMetadata(42)).thenReturn(metadata(ModuleType.CONSULTATION, 123));
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, "123"))
                .thenReturn(false);

        servlet.doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, "123");
        verify(digitalSignatureManager, never()).getDigitalSignature(42);
    }

    @Test
    @DisplayName("should forbid stored consultation signature when demographic context is missing")
    void shouldForbidConsultSignature_whenDemographicContextMissing() throws Exception {
        when(digitalSignatureManager.getDigitalSignatureMetadata(42)).thenReturn(metadata(ModuleType.CONSULTATION, null));

        servlet.doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        verifyNoInteractions(securityInfoManager);
        verify(digitalSignatureManager, never()).getDigitalSignature(42);
    }

    @Test
    @DisplayName("should render stored prescription signature when rx read is granted for the demographic")
    void shouldRenderPrescriptionSignature_whenRxReadGrantedForDemographic() throws Exception {
        DigitalSignature signature = signature();
        when(digitalSignatureManager.getDigitalSignatureMetadata(42)).thenReturn(metadata(ModuleType.PRESCRIPTION, 123));
        when(digitalSignatureManager.getDigitalSignature(42)).thenReturn(signature);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.READ, "123"))
                .thenReturn(true);

        servlet.doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getContentAsByteArray()).isEqualTo(signature.getSignatureImage());
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_rx", SecurityInfoManager.READ, "123");
    }

    @Test
    @DisplayName("should forbid stored eForm signature when eForm read is denied for the demographic")
    void shouldForbidEformSignature_whenEformReadDeniedForDemographic() throws Exception {
        when(digitalSignatureManager.getDigitalSignatureMetadata(42)).thenReturn(metadata(ModuleType.E_FORM, 123));
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, "123"))
                .thenReturn(false);

        servlet.doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_eform", SecurityInfoManager.READ, "123");
        verify(digitalSignatureManager, never()).getDigitalSignature(42);
    }

    @Test
    @DisplayName("should forbid stored signature when module type is missing")
    void shouldForbidStoredSignature_whenModuleTypeMissing() throws Exception {
        when(digitalSignatureManager.getDigitalSignatureMetadata(42)).thenReturn(metadata(null, 123));

        servlet.doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        verifyNoInteractions(securityInfoManager);
        verify(digitalSignatureManager, never()).getDigitalSignature(42);
    }

    @Test
    @DisplayName("should not load stored signature when provider session is missing")
    void shouldForbidStoredSignature_whenProviderSessionMissing() throws Exception {
        request.getSession().removeAttribute(SessionConstants.LOGGED_IN_PROVIDER);

        servlet.doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        verify(digitalSignatureManager, never()).getDigitalSignatureMetadata(42);
        verify(digitalSignatureManager, never()).getDigitalSignature(42);
    }

    @Test
    @DisplayName("should forbid stored signature when logged in info is missing")
    void shouldForbidStoredSignature_whenLoggedInInfoMissing() throws Exception {
        request.getSession().removeAttribute(new LoggedInInfo().LOGGED_IN_INFO_KEY);
        when(digitalSignatureManager.getDigitalSignatureMetadata(42))
                .thenReturn(metadata(ModuleType.CONSULTATION, 123));

        servlet.doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        verifyNoInteractions(securityInfoManager);
        verify(digitalSignatureManager, never()).getDigitalSignature(42);
    }

    @Test
    @DisplayName("should return not found when digital signature id is missing")
    void shouldReturnNotFound_whenDigitalSignatureIdMissing() throws Exception {
        request.removeParameter("digitalSignatureId");

        servlet.doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verifyNoInteractions(digitalSignatureManager, securityInfoManager);
    }

    @Test
    @DisplayName("should return not found when digital signature id is empty")
    void shouldReturnNotFound_whenDigitalSignatureIdEmpty() throws Exception {
        request.setParameter("digitalSignatureId", "");

        servlet.doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verifyNoInteractions(digitalSignatureManager, securityInfoManager);
    }

    @Test
    @DisplayName("should return not found when digital signature id is non-numeric")
    void shouldReturnNotFound_whenDigitalSignatureIdNonNumeric() throws Exception {
        request.setParameter("digitalSignatureId", "not-a-number");

        servlet.doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verifyNoInteractions(digitalSignatureManager, securityInfoManager);
    }

    @Test
    @DisplayName("should return not found when signature metadata is missing")
    void shouldReturnNotFound_whenSignatureMetadataMissing() throws Exception {
        when(digitalSignatureManager.getDigitalSignatureMetadata(42)).thenReturn(null);

        servlet.doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verifyNoInteractions(securityInfoManager);
        verify(digitalSignatureManager, never()).getDigitalSignature(42);
    }

    @Test
    @DisplayName("should return not found when full signature is missing after authorization")
    void shouldReturnNotFound_whenFullSignatureMissingAfterAuthorization() throws Exception {
        when(digitalSignatureManager.getDigitalSignatureMetadata(42))
                .thenReturn(metadata(ModuleType.CONSULTATION, 123));
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, "123"))
                .thenReturn(true);
        when(digitalSignatureManager.getDigitalSignature(42)).thenReturn(null);

        servlet.doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, "123");
    }

    @Test
    @DisplayName("should return not found when signature image is missing after authorization")
    void shouldReturnNotFound_whenSignatureImageMissingAfterAuthorization() throws Exception {
        when(digitalSignatureManager.getDigitalSignatureMetadata(42))
                .thenReturn(metadata(ModuleType.CONSULTATION, 123));
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, "123"))
                .thenReturn(true);
        when(digitalSignatureManager.getDigitalSignature(42)).thenReturn(new DigitalSignature());

        servlet.doGet(request, response);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, "123");
    }

    private static DigitalSignature metadata(ModuleType moduleType, Integer demographicId) {
        DigitalSignature signature = new DigitalSignature();
        signature.setModuleType(moduleType);
        signature.setDemographicId(demographicId);
        return signature;
    }

    private static DigitalSignature signature() {
        DigitalSignature signature = new DigitalSignature();
        signature.setSignatureImage("signature-image".getBytes(StandardCharsets.UTF_8));
        return signature;
    }
}
