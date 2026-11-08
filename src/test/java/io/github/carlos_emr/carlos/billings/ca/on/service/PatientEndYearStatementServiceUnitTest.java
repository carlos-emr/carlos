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
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code PatientEndYearStatementService} patient resolution, aggregation, and PDF contracts. */
@DisplayName("PatientEndYearStatementService")
@Tag("unit")
@Tag("billing")
class PatientEndYearStatementServiceUnitTest extends io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase {

    @Test
    void shouldUseOneDetachedSnapshot_forHtmlTotalsAndPdfRows() {
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
    }

    @Test
    void shouldLeaveResponseUntouched_whenRenderingFailsAfterWritingPartialBytes() {
        var service = spy(new PatientEndYearStatementService(
                mock(io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao.class), mock(DemographicManager.class)));
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        doAnswer(invocation -> {
            ((java.io.OutputStream) invocation.getArgument(0)).write("%PDF-partial".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            throw new PatientEndYearStatementService.Failure(PatientEndYearStatementService.Reason.PDF_ERROR);
        }).when(service).writePdfTo(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.writePdfResponse(response, "statement", null, null, null))
                .isInstanceOf(PatientEndYearStatementService.Failure.class);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentType()).isNull();
        assertThat(response.getHeader("Content-Disposition")).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "<html>Error</html>"})
    void shouldRejectInvalidRendererOutput_beforeSettingPdfHeaders(String rendered) {
        var service = spy(new PatientEndYearStatementService(
                mock(io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao.class), mock(DemographicManager.class)));
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        doAnswer(invocation -> {
            ((java.io.OutputStream) invocation.getArgument(0)).write(rendered.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            return null;
        }).when(service).writePdfTo(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.writePdfResponse(response, "statement", null, null, null))
                .isInstanceOf(PatientEndYearStatementService.Failure.class);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentType()).isNull();
    }

}
