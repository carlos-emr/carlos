/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.DemographicManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code PatientEndYearStatementService} patient resolution, aggregation, and PDF contracts. */
@DisplayName("PatientEndYearStatementService")
@Tag("unit")
@Tag("billing")
class PatientEndYearStatementServiceUnitTest extends io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase {

    @Test
    void shouldUseOneDetachedSnapshot_forHtmlTotalsAndPdfRows() throws Exception {
        var dao = mock(io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao.class);
        var service = new PatientEndYearStatementService(dao, mock(DemographicManager.class));
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(123);
        demographic.setFirstName("Jane");
        demographic.setLastName("Fixture");
        var item = new io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao.ItemRow(101, "A001A", "10.1");
        var row = new io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao.InvoiceRow(101,
                java.sql.Date.valueOf("2025-01-01"), new BigDecimal("30.00"), new BigDecimal("-5.00"), List.of(item));
        when(dao.statement(123, null, null)).thenReturn(List.of(row));
        var result = service.aggregateInvoices(demographic, null, null);
        assertThat(result.reportRows()).containsExactly(row);
        assertThat(result.invoices().getFirst().services().getFirst().fee()).isEqualTo("10.10");
        assertThat(result.summary().getInvoiced()).isEqualTo("30.00");
        assertThat(result.summary().getPaid()).isEqualTo("-5.00");
        verify(dao).statement(123, null, null);
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (var jdbc = org.mockito.Mockito.mockStatic(io.github.carlos_emr.carlos.db.LegacyJdbcQuery.class,
                invocation -> { throw new AssertionError("JDBC attempted during detached PDF rendering"); })) {
            service.writePdfResponse(response, "statement", result, "2025-01-01", "2025-12-31");
            jdbc.verifyNoInteractions();
        }
        org.mockito.Mockito.verifyNoMoreInteractions(dao);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(response.getContentAsByteArray())) {
            assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf))
                    .contains("A001A", "30.00", "-5.00");
        }
    }

    @Test
    void shouldRenderEveryInvoiceAndService_acrossMultiplePages() throws Exception {
        var dao = mock(io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao.class);
        var service = new PatientEndYearStatementService(dao, mock(DemographicManager.class));
        var patient = new Demographic(); patient.setDemographicNo(123);
        patient.setFirstName("Multi"); patient.setLastName("Synthetic");
        var rows = java.util.stream.IntStream.rangeClosed(1, 200).mapToObj(id ->
                new io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao.InvoiceRow(id,
                        java.sql.Date.valueOf("2025-01-01"), new BigDecimal("3.00"), BigDecimal.ZERO,
                        List.of(new io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao.ItemRow(id, "A5692", "3.00"))))
                .toList();
        when(dao.statement(123, null, null)).thenReturn(rows);
        var result = service.aggregateInvoices(patient, null, null);
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        service.writePdfResponse(response, "multi", result, "2025-01-01", "2025-12-31");
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(response.getContentAsByteArray())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            assertThat(org.apache.commons.lang3.StringUtils.countMatches(text, "A5692")).isEqualTo(200);
            assertThat(text).contains("600.00");
        }
        assertThat(result.summary().getCount()).isEqualTo("200");
    }

    @Test
    void shouldLeaveResponseUntouched_whenRenderingFailsAfterWritingPartialBytes() {
        var service = new PatientEndYearStatementService(
                mock(io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao.class), mock(DemographicManager.class));
        var patient = new Demographic(); patient.setDemographicNo(123);
        var result = service.aggregateInvoices(patient, null, null);
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (var exporter = org.mockito.Mockito.mockStatic(net.sf.jasperreports.engine.JasperExportManager.class)) {
            exporter.when(() -> net.sf.jasperreports.engine.JasperExportManager.exportReportToPdfStream(
                    org.mockito.ArgumentMatchers.any(net.sf.jasperreports.engine.JasperPrint.class),
                    org.mockito.ArgumentMatchers.any(java.io.OutputStream.class))).thenAnswer(invocation -> {
                        ((java.io.OutputStream) invocation.getArgument(1)).write(
                                "%PDF-partial".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                        throw new net.sf.jasperreports.engine.JRException("Synthetic export failure");
                    });
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.writePdfResponse(response, "statement", result, null, null))
                    .isInstanceOf(PatientEndYearStatementService.Failure.class);
        }
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentType()).isNull();
        assertThat(response.getHeader("Content-Disposition")).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "<html>Error</html>", "%PDF-partial"})
    void shouldRejectInvalidRendererOutput_beforeSettingPdfHeaders(String rendered) {
        var service = new PatientEndYearStatementService(
                mock(io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao.class), mock(DemographicManager.class));
        var patient = new Demographic(); patient.setDemographicNo(123);
        var result = service.aggregateInvoices(patient, null, null);
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        try (var exporter = org.mockito.Mockito.mockStatic(net.sf.jasperreports.engine.JasperExportManager.class)) {
            exporter.when(() -> net.sf.jasperreports.engine.JasperExportManager.exportReportToPdfStream(
                    org.mockito.ArgumentMatchers.any(net.sf.jasperreports.engine.JasperPrint.class),
                    org.mockito.ArgumentMatchers.any(java.io.OutputStream.class))).thenAnswer(invocation -> {
                        ((java.io.OutputStream) invocation.getArgument(1)).write(rendered.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                        return null;
                    });
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.writePdfResponse(response, "statement", result, null, null))
                    .isInstanceOf(PatientEndYearStatementService.Failure.class);
        }
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentType()).isNull();
    }

}
