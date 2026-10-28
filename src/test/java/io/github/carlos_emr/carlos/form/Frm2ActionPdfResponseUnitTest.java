/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.form;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.github.carlos_emr.carlos.form.util.JasperReportPdfPrint;
import io.github.carlos_emr.carlos.form.util.Rourke2020PdfReport.RenderingException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@Tag("unit")
@Tag("form")
class Frm2ActionPdfResponseUnitTest extends CarlosUnitTestBase {
    private String execute(MockHttpServletResponse response, JasperReportPdfPrint printer) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("form_class", "Rourke2020");
        request.setParameter("demographic_no", "1");
        request.setParameter("formId", "7");
        request.setParameter("submit", "printAllJasperReport");
        request.setParameter("__cfgfile", "Rourke2020printCfgPg1");
        LoggedInInfo info = mock(LoggedInInfo.class);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(eq(info), eq("_form"), eq("w"), isNull())).thenReturn(true);
        FrmRecord formRecord = mock(FrmRecord.class, withSettings().extraInterfaces(JasperReportPdfPrint.class));
        doAnswer(invocation -> {
            printer.PrintJasperPdf(invocation.getArgument(0), info, 1, 7, List.of(1, 2, 3, 4));
            return null;
        }).when((JasperReportPdfPrint) formRecord).PrintJasperPdf(any(), eq(info), eq(1), eq(7), any());
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class);
             MockedStatic<LoggedInInfo> login = mockStatic(LoggedInInfo.class);
             MockedConstruction<FrmRecordFactory> factory = mockConstruction(FrmRecordFactory.class,
                     (created, context) -> when(created.factory("Rourke2020")).thenReturn(formRecord))) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
            String result = new Frm2Action().execute();
            assertThat(factory.constructed()).hasSize(1);
            return result;
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldResetPdfHeadersAndBufferedBytes_whenPrintingFailsBeforeCommit(boolean buffered) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        if (buffered) response.getOutputStream().write("%PDF-partial".getBytes(StandardCharsets.UTF_8));
        String result = execute(response, (output, info, demographic, form, pages) -> {
            throw new IllegalStateException("Synthetic report failure");
        });
        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).isNull();
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getErrorMessage()).isEqualTo("Could not generate form PDF");
    }

    @ParameterizedTest
    @EnumSource(RenderingException.Reason.class)
    void shouldExplainRenderingFailure_withoutReturningAPdf(RenderingException.Reason reason) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RenderingException failure = new RenderingException(reason);
        String result = execute(response, (output, info, demographic, form, pages) -> { throw failure; });
        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(failure.getMessage()).contains("administrator");
        assertThat(response.getErrorMessage()).isNull();
    }

    @Test
    void shouldAvoidForwardingOrAppendingErrorContent_whenPdfResponseIsCommitted() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.getOutputStream().write("%PDF-partial".getBytes(StandardCharsets.UTF_8));
        String result = execute(response, (output, info, demographic, form, pages) -> {
            response.setCommitted(true);
            throw new IllegalStateException("Synthetic export failure");
        });
        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("%PDF-partial");
        assertThat(response.getErrorMessage()).isNull();
    }

    @Test
    void shouldKeepInlinePdfResponse_whenPrintingSucceeds() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(execute(response, (output, info, demographic, form, pages) -> { })).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("inline; filename=\"Rourke2020_7.pdf\"");
    }
}
