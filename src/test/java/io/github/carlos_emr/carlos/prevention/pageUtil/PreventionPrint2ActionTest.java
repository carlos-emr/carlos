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
package io.github.carlos_emr.carlos.prevention.pageUtil;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prevention.pageUtil.PreventionPrintLoader.Entry;
import io.github.carlos_emr.carlos.prevention.pageUtil.PreventionPrintLoader.Printout;
import io.github.carlos_emr.carlos.prevention.pageUtil.PreventionPrintLoader.Section;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.openpdf.text.DocumentException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("PreventionPrint2Action Unit Tests")
@Tag("unit")
@Tag("prevention")
class PreventionPrint2ActionTest extends CarlosUnitTestBase {

    private static final byte[] PDF = "%PDF-1.7 printed preventions".getBytes(StandardCharsets.ISO_8859_1);

    private AutoCloseable mocks;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private LoggedInInfo loggedInInfo;

    @Mock
    private PreventionPrintLoader printLoader;

    @Mock
    private PreventionPrintPdf pdf;

    private final AtomicInteger pdfsCreated = new AtomicInteger();
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private PreventionPrint2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        request = new MockHttpServletRequest("POST", "/prevention/printPrevention");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_prevention", "r", null)).thenReturn(true);
        action = new PreventionPrint2Action(securityInfoManager, printLoader, () -> {
            pdfsCreated.incrementAndGet();
            return pdf;
        });
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
    @DisplayName("should load the ticked types for the patient and stream the PDF only after it is complete")
    void shouldStreamPdf_whenGenerationSucceeds() throws Exception {
        selection("42", "true", "Inf", "PAP");
        Printout printout = printout(true);
        when(printLoader.load(loggedInInfo, "42", new String[]{"Inf", "PAP"}, true)).thenReturn(printout);
        doAnswer(invocation -> {
            invocation.<OutputStream>getArgument(1).write(PDF);
            return null;
        }).when(pdf).printPdf(eq(printout), any(OutputStream.class));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"Prevention.pdf\"");
        assertThat(response.getContentLength()).isEqualTo(PDF.length);
        assertThat(response.getContentAsByteArray()).isEqualTo(PDF);
        assertThat(request.getAttribute("printError")).isNull();
    }

    @Test
    @DisplayName("should ignore every chart field a request posts and pass only the selection to the loader")
    void shouldPassOnlySelection_whenRequestCarriesChartFields() throws Exception {
        selection("42", "false", "Inf");
        request.addParameter("preventionHeader0", "Td");
        request.addParameter("preventProcedureComments0-0", "forged comment");
        request.addParameter("mrp", "999998");
        request.addParameter("hin", "9999999999");
        when(printLoader.load(any(), anyString(), any(), anyBoolean())).thenReturn(printout(false));
        doAnswer(invocation -> {
            invocation.<OutputStream>getArgument(1).write(PDF);
            return null;
        }).when(pdf).printPdf(any(Printout.class), any(OutputStream.class));

        action.execute();

        verify(printLoader).load(loggedInInfo, "42", new String[]{"Inf"}, false);
    }

    @Test
    @DisplayName("should show the page's print error, not a download, when nothing printable was selected")
    void shouldRenderPrintError_whenNothingPrintable() throws Exception {
        selection("42", "false");
        when(printLoader.load(loggedInInfo, "42", null, false))
                .thenReturn(new Printout(new Demographic(), null, false, List.of()));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.ERROR);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(request.getAttribute("printError")).isEqualTo(Boolean.TRUE);
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(response.getContentType()).isNull();
        assertThat(pdfsCreated).hasValue(0);
    }

    @Test
    @DisplayName("should discard a half-written PDF and show the print error when generation fails")
    void shouldRenderPrintError_whenGenerationFails() throws Exception {
        selection("42", "false", "Inf");
        when(printLoader.load(any(), anyString(), any(), anyBoolean())).thenReturn(printout(false));
        doAnswer(invocation -> {
            invocation.<OutputStream>getArgument(1).write(PDF, 0, 8);
            throw new DocumentException("layout failed");
        }).when(pdf).printPdf(any(Printout.class), any(OutputStream.class));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.ERROR);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(request.getAttribute("printError")).isEqualTo(Boolean.TRUE);
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should show the print error when the generator answers something other than a PDF")
    void shouldRenderPrintError_whenOutputIsNotPdf() throws Exception {
        selection("42", "false", "Inf");
        when(printLoader.load(any(), anyString(), any(), anyBoolean())).thenReturn(printout(false));
        doAnswer(invocation -> {
            invocation.<OutputStream>getArgument(1).write("<html>".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(pdf).printPdf(any(Printout.class), any(OutputStream.class));

        assertThat(action.execute()).isEqualTo(ActionSupport.ERROR);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should show the print error when the PDF library fails at run time")
    void shouldRenderPrintError_whenGenerationThrowsRuntimeException() throws Exception {
        selection("42", "false", "Inf");
        when(printLoader.load(any(), anyString(), any(), anyBoolean())).thenReturn(printout(false));
        doThrow(new IllegalStateException("font missing")).when(pdf).printPdf(any(Printout.class), any(OutputStream.class));

        assertThat(action.execute()).isEqualTo(ActionSupport.ERROR);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "-1", "42 OR 1=1"})
    @DisplayName("should refuse a malformed patient number before reading the chart")
    void shouldRejectMalformedPatient_withBadRequest(String demographicNo) throws Exception {
        selection(demographicNo, "false", "Inf");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(printLoader);
        assertThat(pdfsCreated).hasValue(0);
    }

    @Test
    @DisplayName("should refuse a patient number with no patient")
    void shouldRejectMissingPatient_withBadRequest() throws Exception {
        selection("42", "false", "Inf");
        when(printLoader.load(any(), anyString(), any(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Patient not found"));

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getHeader("Content-Disposition")).isNull();
    }

    @Test
    @DisplayName("should refuse a user without prevention read access before reading anything")
    void shouldThrowSecurityException_whenPreventionReadMissing() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_prevention", "r", null)).thenReturn(false);
        selection("42", "false", "Inf");

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_prevention)");
        verifyNoInteractions(printLoader);
    }

    private void selection(String demographicNo, String immunizationOnly, String... types) {
        request.addParameter("demographicNo", demographicNo);
        request.addParameter("demographic_no", demographicNo);
        request.addParameter("immunizationOnly", immunizationOnly);
        if (types.length > 0) {
            request.addParameter("printHP", types);
        }
    }

    private static Printout printout(boolean immunizationsOnly) {
        Entry entry = new Entry("2025-10-01", "45 years", "0", null, null, null, null, null, null, null, null, "Dr C");
        return new Printout(mock(Demographic.class), null, immunizationsOnly,
                List.of(new Section("Inf", false, false, List.of(entry))));
    }
}
