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
package io.github.carlos_emr.carlos.hospitalReportManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("PrintHRMReport2Action")
@Tag("unit")
@Tag("hrm")
class PrintHRMReport2ActionUnitTest extends CarlosUnitTestBase {

    private static final byte[] PDF_BYTES = "%PDF-1.4\n%CARLOS test HRM PDF\n".getBytes(StandardCharsets.US_ASCII);
    private static final Path STRUTS_DOCUMENT_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-document.xml");

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private String previousDocumentDir;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void registerActionDependencies() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(DemographicDao.class, mock(DemographicDao.class));
        registerMock(HRMDocumentToDemographicDao.class, mock(HRMDocumentToDemographicDao.class));

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_hrm"), eq("r"), isNull()))
                .thenReturn(true);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", tempDir.toString());
    }

    @AfterEach
    void closeStaticMocks() {
        if (previousDocumentDir == null) {
            CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", previousDocumentDir);
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    void shouldReturnNoResult_whenHrmPdfIsStreamed() throws Exception {
        try (MockedStatic<ConcatPDF> concatPdfMock = mockStatic(ConcatPDF.class)) {
            concatPdfMock.when(() -> ConcatPDF.concat(any(ArrayList.class), any(OutputStream.class)))
                    .thenAnswer(invocation -> {
                        ((OutputStream) invocation.getArgument(1)).write(PDF_BYTES);
                        return null;
                    });

            String result = new PrintHRMReport2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getContentType()).isEqualTo("application/pdf");
            assertThat(response.getContentAsByteArray()).startsWith(PDF_BYTES);
        }
    }

    @Test
    void shouldReturnNoResultWithHttpError_whenHrmResponseStreamCannotBeOpened() throws Exception {
        HttpServletResponse failingResponse = mock(HttpServletResponse.class);
        when(failingResponse.isCommitted()).thenReturn(false);
        doThrow(new IOException("stream unavailable")).when(failingResponse).getOutputStream();
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(failingResponse);

        String result = new PrintHRMReport2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(failingResponse).reset();
        verify(failingResponse).sendError(500, "Unable to generate HRM PDF");
    }

    @Test
    void shouldKeepHrmPrintRoute_withoutNamedResults() throws Exception {
        String strutsDocument = Files.readString(STRUTS_DOCUMENT_XML);

        assertThat(strutsDocument)
                .contains("<action name=\"hospitalReportManager/PrintHRMReport\" "
                        + "class=\"io.github.carlos_emr.carlos.hospitalReportManager.PrintHRMReport2Action\"/>")
                .doesNotContain("<action name=\"hospitalReportManager/PrintHRMReport\" "
                        + "class=\"io.github.carlos_emr.carlos.hospitalReportManager.PrintHRMReport2Action\">\n"
                        + "            <result");
    }
}
