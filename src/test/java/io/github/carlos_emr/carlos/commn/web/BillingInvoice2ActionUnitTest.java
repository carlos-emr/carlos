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
package io.github.carlos_emr.carlos.commn.web;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.service.PdfRecordPrinter;
import io.github.carlos_emr.carlos.managers.BillingONManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@DisplayName("BillingInvoice2Action")
@Tag("unit")
@Tag("billing")
class BillingInvoice2ActionUnitTest extends CarlosUnitTestBase {

    private static final byte[] PDF_BYTES = "%PDF-1.4\n%CARLOS test PDF\n".getBytes(StandardCharsets.US_ASCII);

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private BillingONManager billingManager;
    private LoggedInInfo loggedInInfo;
    private String previousInvoiceDir;

    @TempDir
    private Path tempDir;

    @BeforeEach
    void registerActionDependencies() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.addPreferredLocale(Locale.CANADA);

        securityInfoManager = mock(SecurityInfoManager.class);
        billingManager = mock(BillingONManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(BillingONManager.class, billingManager);

        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull()))
                .thenReturn(true);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        previousInvoiceDir = CarlosProperties.getInstance().getProperty("INVOICE_DIR");
        CarlosProperties.getInstance().setProperty("INVOICE_DIR", tempDir.toString());
    }

    @AfterEach
    void closeStaticMocks() {
        if (previousInvoiceDir == null) {
            CarlosProperties.getInstance().remove("INVOICE_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("INVOICE_DIR", previousInvoiceDir);
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    void shouldReturnNoResult_whenSingleInvoicePdfIsStreamed() throws Exception {
        request.setParameter("invoiceNo", "1");

        try (MockedConstruction<PdfRecordPrinter> printers = mockPdfPrinterWriting(PDF_BYTES)) {
            String result = new BillingInvoice2Action().getPrintPDF();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getContentType()).isEqualTo("application/pdf");
            assertThat(response.getHeader("Content-Disposition"))
                    .startsWith("attachment; filename=\"BillingInvoice1_")
                    .endsWith(".pdf\"");
            assertThat(response.getContentAsByteArray()).startsWith(PDF_BYTES);
            assertThat(printers.constructed()).hasSize(1);
            verify(printers.constructed().get(0)).printBillingInvoice(eq(1), eq(Locale.CANADA));
            verify(billingManager).addPrintedBillingComment(eq(1), eq(Locale.CANADA));
        }
    }

    @Test
    void shouldReturnNoResult_whenDedicatedSingleInvoicePrintActionIsSubmitted() throws Exception {
        request.setParameter("invoiceNo", "1");

        try (MockedConstruction<PdfRecordPrinter> printers = mockPdfPrinterWriting(PDF_BYTES)) {
            String result = new BillingInvoicePrint2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getContentType()).isEqualTo("application/pdf");
            assertThat(response.getContentAsByteArray()).startsWith(PDF_BYTES);
            assertThat(printers.constructed()).hasSize(1);
            verify(printers.constructed().get(0)).printBillingInvoice(eq(1), eq(Locale.CANADA));
        }
    }

    @Test
    void shouldReturnNoResultWithHttpError_whenSingleInvoicePrinterWritesNoPdfBytes() throws Exception {
        request.setParameter("invoiceNo", "1");

        try (MockedConstruction<PdfRecordPrinter> printers = mockConstruction(PdfRecordPrinter.class)) {
            String result = new BillingInvoice2Action().getPrintPDF();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getErrorMessage()).isEqualTo("Unable to generate billing invoice PDF");
            assertThat(response.getContentType()).isNull();
            assertThat(response.getHeader("Content-Disposition")).isNull();
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertThat(printers.constructed()).hasSize(1);
            verify(printers.constructed().get(0)).printBillingInvoice(eq(1), eq(Locale.CANADA));
            verify(billingManager, never()).addPrintedBillingComment(eq(1), eq(Locale.CANADA));
        }
    }

    @Test
    void shouldReturnNoResultWithHttpError_whenSingleInvoicePrinterThrowsBeforeWritingPdf() {
        request.setParameter("invoiceNo", "1");

        try (MockedConstruction<PdfRecordPrinter> printers = mockConstruction(PdfRecordPrinter.class, (mock, context) ->
                doThrow(new IllegalStateException("printer failed"))
                        .when(mock).printBillingInvoice(eq(1), eq(Locale.CANADA)))) {

            assertThatCode(() -> {
                String result = new BillingInvoice2Action().getPrintPDF();
                assertThat(result).isEqualTo(ActionSupport.NONE);
            }).doesNotThrowAnyException();

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getErrorMessage()).isEqualTo("Unable to generate billing invoice PDF");
            assertThat(response.getContentType()).isNull();
            assertThat(response.getHeader("Content-Disposition")).isNull();
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertThat(printers.constructed()).hasSize(1);
            verify(billingManager, never()).addPrintedBillingComment(eq(1), eq(Locale.CANADA));
        }
    }

    @Test
    void shouldReturnNoResultWithHttpError_whenSingleInvoicePdfExporterDependencyIsMissing() {
        request.setParameter("invoiceNo", "1");

        try (MockedConstruction<PdfRecordPrinter> printers = mockConstruction(PdfRecordPrinter.class, (mock, context) ->
                doThrow(new NoClassDefFoundError("com/lowagie/text/SplitCharacter"))
                        .when(mock).printBillingInvoice(eq(1), eq(Locale.CANADA)))) {

            assertThatCode(() -> {
                String result = new BillingInvoice2Action().getPrintPDF();
                assertThat(result).isEqualTo(ActionSupport.NONE);
            }).doesNotThrowAnyException();

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getErrorMessage()).isEqualTo("Unable to generate billing invoice PDF");
            assertThat(response.getContentType()).isNull();
            assertThat(response.getHeader("Content-Disposition")).isNull();
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertThat(printers.constructed()).hasSize(1);
            verify(billingManager, never()).addPrintedBillingComment(eq(1), eq(Locale.CANADA));
        }
    }

    @Test
    void shouldReturnNoResultWithHttpError_whenSingleInvoiceNumberHasNoDigits() throws Exception {
        request.setParameter("invoiceNo", "abc");

        try (MockedConstruction<PdfRecordPrinter> printers = mockConstruction(PdfRecordPrinter.class)) {
            String result = new BillingInvoice2Action().getPrintPDF();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getErrorMessage()).isEqualTo("Unable to generate billing invoice PDF");
            assertThat(response.getContentType()).isNull();
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertThat(printers.constructed()).isEmpty();
            verify(billingManager, never()).addPrintedBillingComment(any(Integer.class), any(Locale.class));
        }
    }

    @Test
    void shouldReturnNoResult_whenSingleInvoicePrintedCommentCannotBeRecorded() throws Exception {
        request.setParameter("invoiceNo", "1");
        doThrow(new RuntimeException("comment failed"))
                .when(billingManager).addPrintedBillingComment(eq(1), eq(Locale.CANADA));

        try (MockedConstruction<PdfRecordPrinter> printers = mockPdfPrinterWriting(PDF_BYTES)) {
            String result = new BillingInvoice2Action().getPrintPDF();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getContentType()).isEqualTo("application/pdf");
            assertThat(response.getHeader("Content-Disposition"))
                    .startsWith("attachment; filename=\"BillingInvoice1_")
                    .endsWith(".pdf\"");
            assertThat(response.getContentAsByteArray()).startsWith(PDF_BYTES);
            assertThat(printers.constructed()).hasSize(1);
            verify(printers.constructed().get(0)).printBillingInvoice(eq(1), eq(Locale.CANADA));
            verify(billingManager).addPrintedBillingComment(eq(1), eq(Locale.CANADA));
        }
    }

    @Test
    void shouldReturnNoResultWithHttpError_whenSingleInvoiceResponseStreamCannotBeOpened() throws Exception {
        request.setParameter("invoiceNo", "1");
        HttpServletResponse failingResponse = mock(HttpServletResponse.class);
        when(failingResponse.isCommitted()).thenReturn(false);
        doThrow(new IllegalStateException("writer already used")).when(failingResponse).getOutputStream();
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(failingResponse);

        try (MockedConstruction<PdfRecordPrinter> printers = mockPdfPrinterWriting(PDF_BYTES)) {
            assertThatCode(() -> {
                String result = new BillingInvoice2Action().getPrintPDF();

                assertThat(result).isEqualTo(ActionSupport.NONE);
            }).doesNotThrowAnyException();

            verify(failingResponse).sendError(500, "Unable to generate billing invoice PDF");
            assertThat(printers.constructed()).hasSize(1);
            verify(printers.constructed().get(0)).printBillingInvoice(eq(1), eq(Locale.CANADA));
            verify(billingManager, never()).addPrintedBillingComment(eq(1), eq(Locale.CANADA));
        }
    }

    @Test
    void shouldReturnNoResult_whenInvoiceListPdfIsStreamed() throws Exception {
        request.setParameter("invoiceAction", "1", "8");

        try (MockedConstruction<PdfRecordPrinter> printers = mockPdfPrinterWriting(PDF_BYTES);
             MockedStatic<ConcatPDF> concatPdfMock = mockStatic(ConcatPDF.class)) {
            concatPdfMock.when(() -> ConcatPDF.concat(any(ArrayList.class), any(OutputStream.class)))
                    .thenAnswer(invocation -> {
                        ((OutputStream) invocation.getArgument(1)).write(PDF_BYTES);
                        return null;
                    });

            String result = new BillingInvoice2Action().getListPrintPDF();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getContentType()).isEqualTo("application/pdf");
            assertThat(response.getHeader("Content-Disposition"))
                    .startsWith("attachment; filename=\"BillingInvoices_")
                    .endsWith(".pdf\"");
            assertThat(response.getContentAsByteArray()).startsWith(PDF_BYTES);
            assertThat(printers.constructed()).hasSize(2);
            verify(printers.constructed().get(0)).printBillingInvoice(eq(1), eq(Locale.CANADA));
            verify(printers.constructed().get(1)).printBillingInvoice(eq(8), eq(Locale.CANADA));
            verify(billingManager).addPrintedBillingComment(eq(1), eq(Locale.CANADA));
            verify(billingManager).addPrintedBillingComment(eq(8), eq(Locale.CANADA));
            concatPdfMock.verify(() -> ConcatPDF.concat(any(ArrayList.class), any(OutputStream.class)));
        }
    }

    @Test
    void shouldReturnNoResult_whenDedicatedInvoiceListPrintActionIsSubmitted() throws Exception {
        request.setParameter("invoiceAction", "1", "8");

        try (MockedConstruction<PdfRecordPrinter> printers = mockPdfPrinterWriting(PDF_BYTES);
             MockedStatic<ConcatPDF> concatPdfMock = mockStatic(ConcatPDF.class)) {
            concatPdfMock.when(() -> ConcatPDF.concat(any(ArrayList.class), any(OutputStream.class)))
                    .thenAnswer(invocation -> {
                        ((OutputStream) invocation.getArgument(1)).write(PDF_BYTES);
                        return null;
                    });

            String result = new BillingInvoiceListPrint2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getContentType()).isEqualTo("application/pdf");
            assertThat(response.getContentAsByteArray()).startsWith(PDF_BYTES);
            assertThat(printers.constructed()).hasSize(2);
            verify(printers.constructed().get(0)).printBillingInvoice(eq(1), eq(Locale.CANADA));
            verify(printers.constructed().get(1)).printBillingInvoice(eq(8), eq(Locale.CANADA));
        }
    }

    @Test
    void shouldReturnNoResultWithHttpError_whenInvoiceListMergeWritesNoPdfBytes() throws Exception {
        request.setParameter("invoiceAction", "1", "8");

        try (MockedConstruction<PdfRecordPrinter> printers = mockPdfPrinterWriting(PDF_BYTES);
             MockedStatic<ConcatPDF> concatPdfMock = mockStatic(ConcatPDF.class)) {

            String result = new BillingInvoice2Action().getListPrintPDF();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(response.getErrorMessage()).isEqualTo("Unable to generate billing invoice PDF");
            assertThat(response.getContentType()).isNull();
            assertThat(response.getHeader("Content-Disposition")).isNull();
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertThat(printers.constructed()).hasSize(2);
            concatPdfMock.verify(() -> ConcatPDF.concat(any(ArrayList.class), any(OutputStream.class)));
            verify(billingManager, never()).addPrintedBillingComment(eq(1), eq(Locale.CANADA));
            verify(billingManager, never()).addPrintedBillingComment(eq(8), eq(Locale.CANADA));
        }
    }

    private MockedConstruction<PdfRecordPrinter> mockPdfPrinterWriting(byte[] bytes) {
        return mockConstruction(PdfRecordPrinter.class, (mock, context) ->
                doAnswer(invocation -> {
                    ((OutputStream) context.arguments().get(0)).write(bytes);
                    return null;
                }).when(mock).printBillingInvoice(any(Integer.class), any(Locale.class)));
    }
}
