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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.reportByTemplate.actions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.reportByTemplate.SQLReporter;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for report-by-template CSV and spreadsheet export handling.
 */
@DisplayName("GenerateOutFiles2Action")
@Tag("unit")
@Tag("report")
class GenerateOutFiles2ActionTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        loggedInInfo = mock(LoggedInInfo.class);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)).thenReturn(false);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.READ, null)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should write request CSV when session contains different CSV")
    void shouldWriteRequestCsv_whenSessionContainsDifferentCsv() throws Exception {
        request.setParameter("getCSV", "Export to CSV");
        request.setParameter("csv", "from-request");
        request.getSession().setAttribute("csv", "from-session");

        String result = new GenerateOutFiles2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("from-request");
        assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"oscarReport.csv\"");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    @DisplayName("should reject missing CSV when only session CSV exists")
    void shouldRejectMissingCsv_whenOnlySessionCsvExists() throws Exception {
        request.setParameter("getCSV", "Export to CSV");
        request.getSession().setAttribute("csv", "from-session");

        String result = new GenerateOutFiles2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("should reject oversized CSV when posted CSV exceeds limit")
    void shouldRejectOversizedCsv_whenPostedCsvExceedsLimit() throws Exception {
        request.setParameter("getCSV", "Export to CSV");
        request.setParameter("csv", "a".repeat(SQLReporter.MAX_CSV_EXPORT_LENGTH + 1));

        String result = new GenerateOutFiles2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("should reject oversized CSV when posted CSV contains multi-byte characters that exceed byte limit")
    void shouldRejectOversizedCsv_whenPostedCsvContainsMultiByteChars() throws Exception {
        request.setParameter("getCSV", "Export to CSV");
        // create a string shorter than char limit but larger in bytes (UTF-8 3 bytes per '€')
        int repeat = SQLReporter.MAX_CSV_EXPORT_LENGTH / 3 + 1;
        String multi = "\u20ac".repeat(repeat);
        request.setParameter("csv", multi);

        String result = new GenerateOutFiles2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("should reject malformed CSV when generating XLSX")
    void shouldRejectMalformedCsv_whenGeneratingXlsx() throws Exception {
        request.setParameter("getXLS", "Export to XLSX");
        request.setParameter("csv", "\"unterminated");

        String result = new GenerateOutFiles2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("should set server error status when XLSX output stream is unavailable")
    void shouldSetServerErrorStatus_whenXlsxOutputStreamIsUnavailable() throws Exception {
        HttpServletResponse failingResponse = mock(HttpServletResponse.class);
        when(failingResponse.isCommitted()).thenReturn(false);
        when(failingResponse.getOutputStream()).thenThrow(new IOException("write failed"));
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(failingResponse);
        request.setParameter("getXLS", "Export to XLSX");
        request.setParameter("csv", "alpha,beta\n1,2");

        String result = new GenerateOutFiles2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(failingResponse).reset();
        verify(failingResponse).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("should leave the response uncommitted and report a server error when XLSX output fails mid-write")
    void shouldReportServerError_whenXlsxOutputFailsMidWrite() throws Exception {
        HttpServletResponse failingResponse = mock(HttpServletResponse.class);
        FailingServletOutputStream output = new FailingServletOutputStream(4_096);
        when(failingResponse.isCommitted()).thenAnswer(invocation -> output.flushes > 0);
        when(failingResponse.getOutputStream()).thenReturn(output);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(failingResponse);
        StringBuilder csv = new StringBuilder("name,amount\n");
        for (int i = 0; i < 2_000; i++) csv.append("synthetic row ").append(i).append(',').append(i).append(".25\n");
        request.setParameter("getXLS", "Export to XLSX");
        request.setParameter("csv", csv.toString());

        String result = new GenerateOutFiles2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(output.failed).isTrue();
        // A flush would commit a real response and turn the 500 into a 200 with a truncated sheet.
        assertThat(output.flushes).isZero();
        assertThat(output.closes).isZero();
        verify(failingResponse).reset();
        verify(failingResponse).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("should send an XLSX attachment with nosniff when exporting a spreadsheet")
    void shouldSendXlsxAttachmentHeaders_whenExportingSpreadsheet() throws Exception {
        request.setParameter("getXLS", "Export to XLSX");
        request.setParameter("csv", "name,amount\nsynthetic,12.50\n");

        String result = new GenerateOutFiles2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(response.getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"oscarReport.xlsx\"");
        byte[] bytes = response.getContentAsByteArray();
        assertThat(bytes).startsWith(new byte[] {'P', 'K', 3, 4});
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("OSCAR_Report");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("synthetic");
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getNumericCellValue()).isEqualTo(12.5);
        }
    }

    @Test
    void shouldPreserveTextExactly_whenExportingNumericLookingIdentifiers() throws Exception {
        request.setParameter("getXLS", "Export to XLSX");
        request.setParameter("csv", "chart,reference,precision,formula,negativeId,exponent,negativeZero,sixteenDigits\n00123,12345678901234567,1.23456789012345678,=1+1,-001,1e20,-0,1234567890123456\n");
        assertThat(new GenerateOutFiles2Action().execute()).isEqualTo(ActionSupport.NONE);
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
            var row = workbook.getSheetAt(0).getRow(1);
            String[] expected = {"00123", "12345678901234567", "1.23456789012345678", "=1+1", "-001", "1e20", "-0", "1234567890123456"};
            for (int column = 0; column < expected.length; column++) {
                assertThat(row.getCell(column).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
                assertThat(row.getCell(column).getStringCellValue()).isEqualTo(expected[column]);
            }
        }
    }

    @Test
    void shouldKeepNonAsciiDigitsAsText_whenExportingNumericLookingValues() throws Exception {
        request.setParameter("getXLS", "Export to XLSX");
        request.setParameter("csv", "arabic,fullwidth,mixed,decimal\n١٢,１２,1١,1.١\n");
        assertThat(new GenerateOutFiles2Action().execute()).isEqualTo(ActionSupport.NONE);
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
            var row = workbook.getSheetAt(0).getRow(1);
            String[] expected = {"١٢", "１２", "1١", "1.١"};
            for (int column = 0; column < expected.length; column++) {
                assertThat(row.getCell(column).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
                assertThat(row.getCell(column).getStringCellValue()).isEqualTo(expected[column]);
            }
        }
    }

    @Test
    void shouldKeepSafeNumbersNumeric_whenExportingCountsAndAmounts() throws Exception {
        request.setParameter("getXLS", "Export to XLSX");
        request.setParameter("csv", "2026,amount,zero,maximum\n42,-12.50,0,999999999999999\n");
        assertThat(new GenerateOutFiles2Action().execute()).isEqualTo(ActionSupport.NONE);
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("2026");
            double[] expected = {42, -12.5, 0, 999999999999999d};
            var row = workbook.getSheetAt(0).getRow(1);
            for (int column = 0; column < expected.length; column++) {
                assertThat(row.getCell(column).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.NUMERIC);
                assertThat(row.getCell(column).getNumericCellValue()).isEqualTo(expected[column]);
            }
        }
    }

    /** A response stream that fails after {@code failAfter} bytes and records commit-causing calls. */
    private static final class FailingServletOutputStream extends ServletOutputStream {
        private final int failAfter;
        private int written;
        private int flushes;
        private int closes;
        private boolean failed;

        FailingServletOutputStream(int failAfter) {
            this.failAfter = failAfter;
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[] {(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (failed || written + length > failAfter) {
                failed = true;
                throw new IOException("synthetic client disconnect");
            }
            written += length;
        }

        @Override
        public void flush() {
            flushes++;
        }

        @Override
        public void close() {
            closes++;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
