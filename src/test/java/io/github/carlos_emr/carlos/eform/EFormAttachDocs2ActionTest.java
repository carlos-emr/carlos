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
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EFormAttachDocs2Action Tests")
@Tag("unit")
@Tag("eform")
class EFormAttachDocs2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;
    @Mock
    private DocumentAttachmentManager mockDocumentAttachmentManager;
    @Mock
    private LoggedInInfo mockLoggedInInfo;
    @Mock
    private HttpServletRequest mockRequest;
    @Mock
    private HttpServletResponse mockResponse;

    private EFormAttachDocs2Action action;
    private StringWriter responseBuffer;

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(DocumentAttachmentManager.class, mockDocumentAttachmentManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("u"), isNull()))
                .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("r"), isNull()))
                .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_lab"), eq("r"), isNull()))
                .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_hrm"), eq("r"), isNull()))
                .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("r"), isNull()))
                .thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("r"), isNull()))
                .thenReturn(true);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        responseBuffer = new StringWriter();
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(responseBuffer));

        action = new EFormAttachDocs2Action();
        action.setRequestId("123");
        action.setDemoNo("456");
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
    void shouldPreserveHiddenLabAttachments_whenUserLacksLabRead() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_lab"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockDocumentAttachmentManager.getEFormAttachments(mockLoggedInInfo, 123, DocumentType.LAB, 456))
                .thenReturn(List.of("11", "12"));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockDocumentAttachmentManager).getEFormAttachments(mockLoggedInInfo, 123, DocumentType.LAB, 456);
        verify(mockDocumentAttachmentManager).attachToEForm(
                eq(mockLoggedInInfo),
                eq(DocumentType.LAB),
                argThat(values -> values != null && values.length == 2 && values[0].equals("11") && values[1].equals("12")),
                eq("999998"),
                eq(123),
                eq(456));
        assertThat(responseBuffer.toString()).isEqualTo("ok");
    }

    @Test
    void shouldAllowClearingVisibleFormAttachments_whenUserCanReadForms() throws Exception {
        when(mockRequest.getParameterValues("formNo")).thenReturn(null);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockDocumentAttachmentManager, never()).getEFormAttachments(mockLoggedInInfo, 123, DocumentType.FORM, 456);
        verify(mockDocumentAttachmentManager).attachToEForm(
                eq(mockLoggedInInfo),
                eq(DocumentType.FORM),
                argThat(values -> values != null && values.length == 0),
                eq("999998"),
                eq(123),
                eq(456));
    }

    @Test
    void shouldPreserveHiddenFormAttachments_whenUserLacksFormRead() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_form"), eq("r"), isNull()))
                .thenReturn(false);
        when(mockRequest.getParameterValues("formNo")).thenReturn(null);
        when(mockDocumentAttachmentManager.getEFormAttachments(mockLoggedInInfo, 123, DocumentType.FORM, 456))
                .thenReturn(List.of("77", "88"));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockDocumentAttachmentManager).getEFormAttachments(mockLoggedInInfo, 123, DocumentType.FORM, 456);
        verify(mockDocumentAttachmentManager).attachToEForm(
                eq(mockLoggedInInfo),
                eq(DocumentType.FORM),
                argThat(values -> values != null && values.length == 2 && values[0].equals("77") && values[1].equals("88")),
                eq("999998"),
                eq(123),
                eq(456));
    }
}
