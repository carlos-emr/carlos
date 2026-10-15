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
package io.github.carlos_emr.carlos.documentManager.gate;

import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.documentManager.IncomingDocUtil;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

@DisplayName("ViewIncomingDocuments2Action Tests")
@Tag("unit")
@Tag("documentManager")
class ViewIncomingDocuments2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;
    @Mock
    private LoggedInInfo mockLoggedInInfo;
    @Mock
    private HttpServletRequest mockRequest;
    @Mock
    private HttpServletResponse mockResponse;

    private ViewIncomingDocuments2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), eq("r"), isNull()))
                .thenReturn(true);

        action = new ViewIncomingDocuments2Action();
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

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST"})
    void shouldAllowReadNavigation_whenNoPdfMutationRequested(String method) throws Exception {
        when(mockRequest.getMethod()).thenReturn(method);
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_edoc", "w", null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "PATCH", "DELETE"})
    void shouldRefuseMutation_whenRequestIsNotPost(String method) throws Exception {
        when(mockRequest.getMethod()).thenReturn(method);
        when(mockRequest.getParameter("pdfAction")).thenReturn("DeletePDF");
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).setHeader("Allow", "POST");
        verify(mockResponse).sendError(eq(405), any(String.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Rotate90", "Rotate180", "RotateM90", "RotateAll90", "RotateAll180", "RotateAllM90", "DeletePage", "DeletePDF", "ExtractPagePDF"})
    void shouldRefuseEveryMutation_whenCallerHasOnlyReadAccess(String operation) throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getParameter("pdfAction")).thenReturn(operation);
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(eq(403), any(String.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Rotate90", "Rotate180", "RotateM90", "RotateAll90", "RotateAll180", "RotateAllM90", "DeletePage", "DeletePDF", "ExtractPagePDF"})
    void shouldAllowEverySupportedMutation_whenPostAndWriteAuthorized(String operation) throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getParameter("pdfAction")).thenReturn(operation);
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_edoc", "w", null)).thenReturn(true);
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    void shouldRejectUnknownAction_whenWriteAuthorized() throws Exception {
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getParameter("pdfAction")).thenReturn("Unknown");
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_edoc", "w", null)).thenReturn(true);
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        verify(mockResponse).sendError(eq(400), any(String.class));
    }

    @Test
    void shouldKeepReadGate_whenCallerCannotReadDocuments() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(mockLoggedInInfo, "_edoc", "r", null)).thenReturn(false);
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verify(mockSecurityInfoManager, never()).hasPrivilege(mockLoggedInInfo, "_edoc", "w", null);
    }

    @Test
    void shouldUseDedicatedGate_whenResolvingIncomingDocumentRoute() throws Exception {
        String mapping = Files.readString(resolveProjectPath(Path.of("src/main/webapp/WEB-INF/classes/struts-document.xml")));
        assertThat(mapping).contains("<action name=\"documentManager/ViewIncomingDocs\" class=\"io.github.carlos_emr.carlos.documentManager.gate.ViewIncomingDocuments2Action\">");
    }

    /**
     * ManageDocument's nextIncomingDoc result renders incomingDocs.jsp without this gate, so the
     * JSP must repeat the POST, write-privilege and supported-action checks before dispatching.
     */
    @Test
    void shouldRepeatMutationChecks_inIncomingDocsJspForUngatedResult() throws Exception {
        String jsp = Files.readString(resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp/documentManager/incomingDocs.jsp")));
        int dispatch = jsp.indexOf("IncomingDocUtil.doPagesAction(");
        assertThat(dispatch).isPositive();
        String beforeDispatch = jsp.substring(0, dispatch);
        assertThat(beforeDispatch)
                .contains("!\"POST\".equals(request.getMethod())")
                .contains("\"_edoc\", \"w\", null")
                .contains("!IncomingDocUtil.isSupportedPageAction(pdfAction)")
                .contains("SC_BAD_REQUEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Rotate90", "Rotate180", "RotateM90", "RotateAll90", "RotateAll180", "RotateAllM90",
            "DeletePage", "DeletePDF", "ExtractPagePDF"})
    void shouldRecognizeSupportedPageAction_forEveryDispatchedVerb(String pdfAction) {
        assertThat(IncomingDocUtil.isSupportedPageAction(pdfAction)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "rotate90", "Rotate45", "DeleteAll"})
    void shouldRejectUnknownPageAction_forUnsupportedNames(String pdfAction) {
        assertThat(IncomingDocUtil.isSupportedPageAction(pdfAction)).isFalse();
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty("basedir", System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not resolve " + relativePath + " from the project directory");
    }
}
