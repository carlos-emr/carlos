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
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("DxResearchLoadAssociations2Action Unit Tests")
@Tag("unit")
@Tag("dxresearch")
class DxResearchLoadAssociations2ActionUnitTest extends CarlosUnitTestBase {

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

    @ParameterizedTest
    @ValueSource(strings = {"clearAssociations", "addAssociation", "uploadFile", "autoPopulateAssociations"})
    @DisplayName("should send 405 when mutation is attempted with GET")
    void shouldSend405_whenMutationAttemptedWithGet(String method) throws Exception {
        mockRequest.setParameter("method", method);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verifyNoInteractions(mockDxDao);
    }

    @Test
    void shouldReturnOnlyJson_whenListingAssociations() throws Exception {
        mockRequest.setParameter("method", "getAllAssociations");
        when(mockDxDao.findAllAssociations()).thenReturn(List.of());

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getContentAsString()).isEqualTo("[]");
        assertThat(mockResponse.getContentType()).startsWith("application/json");
    }

    @Test
    void shouldReturnOnlyCsv_whenExportingAssociations() throws Exception {
        mockRequest.setParameter("method", "export");
        when(mockDxDao.findAllAssociations()).thenReturn(List.of());

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getContentAsString()).startsWith("Issue List Code Type,Issue List Code,");
        assertThat(mockResponse.getContentType()).isEqualTo("application/octet-stream");
    }

    @Test
    void shouldReturnOnlyJson_whenClearingAssociationsWithPost() throws Exception {
        mockRequest.setMethod("POST");
        mockRequest.setParameter("method", "clearAssociations");
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_dxresearch"), eq("u"), isNull())).thenReturn(true);
        when(mockDxDao.removeAssociations()).thenReturn(3);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getContentAsString()).isEqualTo("{\"recordsUpdated\":3}");
    }

    @Test
    void shouldReturnErrorWithoutWritingResponse_whenUploadIsMissing() throws Exception {
        mockRequest.setMethod("POST");
        mockRequest.setParameter("method", "uploadFile");
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_dxresearch"), eq("w"), isNull())).thenReturn(true);

        assertThat(action.execute()).isEqualTo(ActionSupport.ERROR);
        assertThat(action.getActionErrors()).contains("File not uploaded.");
        assertThat(mockResponse.getContentAsString()).isEmpty();
        verifyNoInteractions(mockDxDao);
    }

    @Test
    void shouldRenderPageWithoutJsonPrefix_whenAppendingCsvUpload() throws Exception {
        mockRequest.setMethod("POST");
        mockRequest.setParameter("method", "uploadFile");
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_dxresearch"), eq("w"), isNull())).thenReturn(true);
        Path file = Files.createTempFile("dx-association-", ".csv");
        try {
            Files.writeString(file, "Issue List Code Type,Issue List Code,Disease Registry Code Type,Disease Registry Code\nicd10,ABC,icd9,250\n");
            UploadedFile upload = mock(UploadedFile.class);
            when(upload.getInputName()).thenReturn("file");
            when(upload.getContent()).thenReturn(file.toFile());
            action.withUploadedFiles(List.of(upload));
            action.setReplace(false);

            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            assertThat(mockResponse.getContentAsString()).isEmpty();
            verify(mockDxDao).persist(any());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void shouldRequireReadPrivilege_whenUnknownMethodFallsBackToPage() {
        mockRequest.setParameter("method", "unknown");
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_dxresearch"), eq("r"), isNull())).thenReturn(false);

        assertThatThrownBy(() -> action.execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(mockDxDao);
    }
}
