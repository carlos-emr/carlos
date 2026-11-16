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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.reportByTemplate.ReportCsvDownloads;
import io.github.carlos_emr.carlos.report.reportByTemplate.ReportObject;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Report by Template CSV and XLSX downloads, built on the server from the result the report kept for the
 * session ({@link ReportCsvDownloads}); the request carries only the result's token.
 */
@DisplayName("GenerateOutFiles2Action")
@Tag("unit")
@Tag("report")
class GenerateOutFiles2ActionTest extends CarlosUnitTestBase {

    private static final String PROVIDER = "999998";

    /**
     * CSVs as the reporters produce them, and the SHA-256 of the files the previous action built when the
     * result page posted exactly this CSV back: the CSV bytes, and, for the XLSX, the sorted map of each ZIP
     * entry to the SHA-256 of its contents (entry contents are deterministic; ZIP timestamps are not). The
     * digests were taken from the posted-CSV action before it changed, so a match means the download is
     * unchanged.
     */
    private static final String[][] PREVIOUS_GENERATION = {
        {"name,amount\r\nsynthetic,12.50\r\n",
            "426b06dc76a78a7461a5287f6e968fa341885d22c50fdeaa752e972ae76fc705",
            "8975d6cf3f15f1fc16e7e3737e02da573645d8b7b833ddaeb2b4dfbdc878abc3"},
        {"chart,reference,precision,formula,negativeId,exponent,negativeZero,sixteenDigits\r\n"
            + "00123,12345678901234567,1.23456789012345678,=1+1,-001,1e20,-0,1234567890123456\r\n",
            "cba822dae55cbdb01efc0a10373c444b9fea7785f79d363e19bff06c38c9a507",
            "9361bfa114927ae5f1c1da5436f3e757fcec49fea9228a831ef3043695049bd3"},
        {"\"\t=header\",multiline,amount\r\n\"\t=1+1\",\"Zo\u00eb,\"\"text\"\"\r\nnext\",-12.50\r\n",
            "8831d434cc046ba4af718dd1aa95295ff2a136836a98de18da515ebc17c39cbd",
            "bff3c4ef23752a915b9f583ff40f1106bdd2f60d05a16f559800790fdfb6c8f4"},
        {"arabic,fullwidth,2026,count\r\n\u0661\u0662,\uff11\uff12,42,999999999999999\r\n",
            "e6b0859d01564448d9f31034d7f45b4a134036af439e483df8862b5d4b2fbe54",
            "57c4ee0621ea63f1c8eebbeece3694581edb192c737788cfd4e1764d0a94b090"},
    };

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;

    private MockHttpSession session;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER);

        session = new MockHttpSession();
        request = new MockHttpServletRequest("POST", "/oscarReport/reportByTemplate/generateOutFilesAction");
        request.setSession(session);
        response = new MockHttpServletResponse();
        LoggedInInfo.setLoggedInInfoIntoSession(session, loggedInInfo);

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

    /** Runs a report as GenerateReport2Action does: the reporter's CSVs are kept for this session. */
    private String runReport(String... csvs) {
        MockHttpServletRequest run = new MockHttpServletRequest("POST", "/oscarReport/reportByTemplate/GenerateReportAction");
        run.setSession(session);
        ReportObject report = mock(ReportObject.class);
        when(report.isSequence()).thenReturn(csvs.length > 1);
        run.setAttribute("reportobject", report);
        if (csvs.length > 1) {
            run.setAttribute("sequenceLength", csvs.length);
            for (int query = 0; query < csvs.length; query++) {
                run.setAttribute("csv-" + query, csvs[query]);
            }
        } else {
            run.setAttribute("csv", csvs[0]);
        }
        ReportCsvDownloads.offer(run, PROVIDER);
        return (String) run.getAttribute(ReportCsvDownloads.TOKEN_ATTRIBUTE);
    }

    private void requestDownload(String format, String token, String index) {
        request.setParameter(format, "Export");
        if (token != null) {
            request.setParameter("resultToken", token);
        }
        if (index != null) {
            request.setParameter("resultIndex", index);
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** SHA-256 of the sorted map of ZIP entry name to the SHA-256 of its contents. */
    private static String xlsxContentDigest(byte[] xlsx) throws Exception {
        TreeMap<String, String> entries = new TreeMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), sha256(zip.readAllBytes()));
            }
        }
        return sha256(entries.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void assertRunAgainMessage(String result) {
        assertThat(result).isEqualTo(GenerateOutFiles2Action.UNAVAILABLE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_GONE);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Nested
    @DisplayName("downloads built from the kept result")
    class Downloads {

        @ParameterizedTest(name = "corpus {0}")
        @ValueSource(ints = {0, 1, 2, 3})
        @DisplayName("should send the same CSV bytes the posted-CSV export produced for the same data")
        void shouldSendTheSameCsvBytes_asThePreviousGeneration(int corpus) throws Exception {
            requestDownload("getCSV", runReport(PREVIOUS_GENERATION[corpus][0]), "0");

            assertThat(new GenerateOutFiles2Action().execute()).isEqualTo(ActionSupport.NONE);

            assertThat(sha256(response.getContentAsByteArray())).isEqualTo(PREVIOUS_GENERATION[corpus][1]);
        }

        @ParameterizedTest(name = "corpus {0}")
        @ValueSource(ints = {0, 1, 2, 3})
        @DisplayName("should send the same XLSX workbook the posted-CSV export produced for the same data")
        void shouldSendTheSameXlsxWorkbook_asThePreviousGeneration(int corpus) throws Exception {
            requestDownload("getXLS", runReport(PREVIOUS_GENERATION[corpus][0]), "0");

            assertThat(new GenerateOutFiles2Action().execute()).isEqualTo(ActionSupport.NONE);

            assertThat(xlsxContentDigest(response.getContentAsByteArray())).isEqualTo(PREVIOUS_GENERATION[corpus][2]);
        }

        @Test
        @DisplayName("should send a UTF-8 CSV attachment with nosniff")
        void shouldSendCsvAttachmentHeaders_forCsvDownload() throws Exception {
            requestDownload("getCSV", runReport("name\r\nsynthetic\r\n"), "0");

            assertThat(new GenerateOutFiles2Action().execute()).isEqualTo(ActionSupport.NONE);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("name\r\nsynthetic\r\n");
            assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
            assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name());
            assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"oscarReport.csv\"");
            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        }

        @Test
        @DisplayName("should send an XLSX attachment with nosniff when exporting a spreadsheet")
        void shouldSendXlsxAttachmentHeaders_whenExportingSpreadsheet() throws Exception {
            requestDownload("getXLS", runReport("name,amount\nsynthetic,12.50\n"), "0");

            String result = new GenerateOutFiles2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentType())
                    .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"oscarReport.xlsx\"");
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
            byte[] bytes = response.getContentAsByteArray();
            assertThat(bytes).startsWith(new byte[] {'P', 'K', 3, 4});
            try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                assertThat(workbook.getSheetName(0)).isEqualTo("OSCAR_Report");
                assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("synthetic");
                assertThat(workbook.getSheetAt(0).getRow(1).getCell(1).getNumericCellValue()).isEqualTo(12.5);
            }
        }

        @Test
        @DisplayName("should send the CSV of the query the request names in a sequence")
        void shouldSendTheNamedQuery_forSequencedReport() throws Exception {
            requestDownload("getCSV", runReport("first\r\n", "second\r\n"), "1");

            assertThat(new GenerateOutFiles2Action().execute()).isEqualTo(ActionSupport.NONE);

            assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("second\r\n");
        }

        @Test
        @DisplayName("should send the CSV rather than the XLSX when a request names both")
        void shouldPreferCsv_whenBothFormatsAreNamed() throws Exception {
            requestDownload("getCSV", runReport("name\r\n"), "0");
            request.setParameter("getXLS", "Export");

            assertThat(new GenerateOutFiles2Action().execute()).isEqualTo(ActionSupport.NONE);

            assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
        }

        @Test
        void shouldPreserveTextExactly_whenExportingNumericLookingIdentifiers() throws Exception {
            requestDownload("getXLS", runReport("chart,reference,precision,formula,negativeId,exponent,negativeZero,sixteenDigits\n00123,12345678901234567,1.23456789012345678,=1+1,-001,1e20,-0,1234567890123456\n"), "0");
            assertThat(new GenerateOutFiles2Action().execute()).isEqualTo(ActionSupport.NONE);
            try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                    new ByteArrayInputStream(response.getContentAsByteArray()))) {
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
            requestDownload("getXLS", runReport("arabic,fullwidth,mixed,decimal\n\u0661\u0662,\uff11\uff12,1\u0661,1.\u0661\n"), "0");
            assertThat(new GenerateOutFiles2Action().execute()).isEqualTo(ActionSupport.NONE);
            try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                    new ByteArrayInputStream(response.getContentAsByteArray()))) {
                var row = workbook.getSheetAt(0).getRow(1);
                String[] expected = {"\u0661\u0662", "\uff11\uff12", "1\u0661", "1.\u0661"};
                for (int column = 0; column < expected.length; column++) {
                    assertThat(row.getCell(column).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
                    assertThat(row.getCell(column).getStringCellValue()).isEqualTo(expected[column]);
                }
            }
        }

        @Test
        void shouldKeepSafeNumbersNumeric_whenExportingCountsAndAmounts() throws Exception {
            requestDownload("getXLS", runReport("2026,amount,zero,maximum\n42,-12.50,0,999999999999999\n"), "0");
            assertThat(new GenerateOutFiles2Action().execute()).isEqualTo(ActionSupport.NONE);
            try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                    new ByteArrayInputStream(response.getContentAsByteArray()))) {
                assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("2026");
                double[] expected = {42, -12.5, 0, 999999999999999d};
                var row = workbook.getSheetAt(0).getRow(1);
                for (int column = 0; column < expected.length; column++) {
                    assertThat(row.getCell(column).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.NUMERIC);
                    assertThat(row.getCell(column).getNumericCellValue()).isEqualTo(expected[column]);
                }
            }
        }
    }

    @Nested
    @DisplayName("a result this session no longer holds")
    class Unavailable {

        @Test
        @DisplayName("should ignore a posted CSV and ask to run the report again, as an open pre-upgrade page posts")
        void shouldIgnorePostedCsv_fromAnOldResultPage() throws Exception {
            request.setParameter("getCSV", "Export to CSV");
            request.setParameter("csv", "FAKE-posted,=HYPERLINK(\"x\")\r\n");

            assertRunAgainMessage(new GenerateOutFiles2Action().execute());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"getCSV", "getXLS"})
        @DisplayName("should ask to run the report again for a token this session does not hold")
        void shouldAskToRunAgain_forUnknownToken(String format) throws Exception {
            runReport("name\r\n");
            requestDownload(format, "0".repeat(64), "0");

            assertRunAgainMessage(new GenerateOutFiles2Action().execute());
        }

        @Test
        @DisplayName("should ask to run the report again when another session's token is posted")
        void shouldAskToRunAgain_forAnotherSessionsToken() throws Exception {
            String token = runReport("name\r\n");
            MockHttpSession other = new MockHttpSession();
            LoggedInInfo.setLoggedInInfoIntoSession(other, loggedInInfo);
            request.setSession(other);
            requestDownload("getCSV", token, "0");

            assertRunAgainMessage(new GenerateOutFiles2Action().execute());
        }

        @Test
        @DisplayName("should ask to run the report again when another provider is signed in to the session")
        void shouldAskToRunAgain_forAnotherProvider() throws Exception {
            String token = runReport("name\r\n");
            when(loggedInInfo.getLoggedInProviderNo()).thenReturn("100");
            requestDownload("getCSV", token, "0");

            assertRunAgainMessage(new GenerateOutFiles2Action().execute());
        }

        @Test
        @DisplayName("should ask to run the report again for a query that offers no download")
        void shouldAskToRunAgain_forQueryWithoutCsv() throws Exception {
            requestDownload("getCSV", runReport("first\r\n", ""), "1");

            assertRunAgainMessage(new GenerateOutFiles2Action().execute());
        }

        @ParameterizedTest(name = "templateid={0}")
        @CsvSource(value = {"42,42", "0,", "-3,", "abc,", "'',"})
        @DisplayName("should pass only a positive template id to the message, for its run-again link")
        void shouldPassOnlyPositiveTemplateId_toTheMessage(String templateId, Integer expected) throws Exception {
            request.setParameter("getCSV", "Export to CSV");
            request.setParameter("templateid", templateId);

            assertRunAgainMessage(new GenerateOutFiles2Action().execute());
            assertThat(request.getAttribute("templateid")).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("request checks")
    class RequestChecks {

        @Test
        @DisplayName("should allow a GET: a download reads a kept result and changes nothing")
        void shouldSendDownload_forGetRequest() throws Exception {
            request.setMethod("GET");
            requestDownload("getCSV", runReport("name\r\n"), "0");

            assertThat(new GenerateOutFiles2Action().execute()).isEqualTo(ActionSupport.NONE);

            assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("name\r\n");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"})
        @DisplayName("should answer 405 to any method but GET and POST, without a download")
        void shouldRefuseOtherMethods_withoutDownload(String method) throws Exception {
            request.setMethod(method);
            requestDownload("getCSV", runReport("name\r\n"), "0");

            assertThat(new GenerateOutFiles2Action().execute()).isEqualTo(ActionSupport.NONE);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("GET, POST");
            assertThat(response.getHeader("Content-Disposition")).isNull();
        }

        @Test
        @DisplayName("should answer 400 to a request that names no format")
        void shouldAnswerBadRequest_whenNoFormatIsNamed() throws Exception {
            request.setParameter("resultToken", runReport("name\r\n"));

            assertThat(new GenerateOutFiles2Action().execute()).isEqualTo(ActionSupport.NONE);

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(response.getHeader("Content-Disposition")).isNull();
        }

        @Test
        @DisplayName("should refuse a provider without admin or report read, before reading the result")
        void shouldRefuse_whenProviderLacksReportRead() {
            requestDownload("getCSV", runReport("name\r\n"), "0");
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.READ, null)).thenReturn(false);

            assertThatThrownBy(() -> new GenerateOutFiles2Action().execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_admin or _report)");
            assertThat(response.getContentAsByteArray()).isEmpty();
        }

        @Test
        @DisplayName("should refuse a request without a signed-in provider")
        void shouldRefuse_whenNoProviderIsSignedIn() {
            request.setSession(new MockHttpSession());
            requestDownload("getCSV", "0".repeat(64), "0");

            assertThatThrownBy(() -> new GenerateOutFiles2Action().execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_admin or _report)");
        }
    }

    @Nested
    @DisplayName("generation failures")
    class Failures {

        @Test
        @DisplayName("should answer 400 with no attachment when a kept CSV cannot be parsed for the XLSX")
        void shouldRejectMalformedCsv_whenGeneratingXlsx() throws Exception {
            requestDownload("getXLS", runReport("\"unterminated"), "0");

            String result = new GenerateOutFiles2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertThat(response.getHeader("Content-Disposition")).isNull();
            assertThat(response.getContentAsString()).isEmpty();
        }

        @Test
        @DisplayName("should answer 500 before writing anything when a cell is too long for a workbook")
        void shouldRefuseBeforeWriting_whenACellIsTooLongForAWorkbook() throws Exception {
            requestDownload("getXLS", runReport("name,note\nsynthetic," + "x".repeat(32_768) + "\n"), "0");

            String result = new GenerateOutFiles2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            assertThat(response.getHeader("Content-Disposition")).isNull();
            assertThat(response.getContentAsByteArray()).isEmpty();
        }

        @Test
        @DisplayName("should end the request without a page when the CSV cannot be written to the client")
        void shouldEndWithoutPage_whenCsvWriteFails() throws Exception {
            requestDownload("getCSV", runReport("name\r\n"), "0");
            HttpServletResponse failingResponse = mock(HttpServletResponse.class);
            when(failingResponse.getOutputStream()).thenThrow(new IOException("synthetic client disconnect"));
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(failingResponse);

            // NONE: Struts must not render a page into a download whose headers are already set.
            assertThat(new GenerateOutFiles2Action().execute()).isEqualTo(ActionSupport.NONE);
            verify(failingResponse).setHeader("Content-Disposition", "attachment; filename=\"oscarReport.csv\"");
        }

        @Test
        @DisplayName("should set server error status when XLSX output stream is unavailable")
        void shouldSetServerErrorStatus_whenXlsxOutputStreamIsUnavailable() throws Exception {
            requestDownload("getXLS", runReport("alpha,beta\n1,2"), "0");
            HttpServletResponse failingResponse = mock(HttpServletResponse.class);
            when(failingResponse.isCommitted()).thenReturn(false);
            when(failingResponse.getOutputStream()).thenThrow(new IOException("write failed"));
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(failingResponse);

            String result = new GenerateOutFiles2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            verify(failingResponse).reset();
            verify(failingResponse).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("should leave the response uncommitted and report a server error when XLSX output fails mid-write")
        void shouldReportServerError_whenXlsxOutputFailsMidWrite() throws Exception {
            StringBuilder csv = new StringBuilder("name,amount\n");
            for (int i = 0; i < 2_000; i++) csv.append("synthetic row ").append(i).append(',').append(i).append(".25\n");
            requestDownload("getXLS", runReport(csv.toString()), "0");
            HttpServletResponse failingResponse = mock(HttpServletResponse.class);
            FailingServletOutputStream output = new FailingServletOutputStream(4_096);
            when(failingResponse.isCommitted()).thenAnswer(invocation -> output.flushes > 0);
            when(failingResponse.getOutputStream()).thenReturn(output);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(failingResponse);

            String result = new GenerateOutFiles2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(output.failed).isTrue();
            // A flush would commit a real response and turn the 500 into a 200 with a truncated sheet.
            assertThat(output.flushes).isZero();
            assertThat(output.closes).isZero();
            verify(failingResponse).reset();
            verify(failingResponse).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
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
