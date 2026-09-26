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
package io.github.carlos_emr.carlos.report.pageUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.LogLettersDao;
import io.github.carlos_emr.carlos.commn.dao.ReportLettersDao;
import io.github.carlos_emr.carlos.commn.model.ReportLetters;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.dispatcher.multipart.StrutsUploadedFile;
import org.apache.struts2.dispatcher.multipart.UploadedFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ManagePatientLetters2Action}: the stored template name is the user's
 * original file name (sanitized), not the Struts {@code upload_*.tmp} name (issue #3963).
 */
@DisplayName("ManagePatientLetters2Action")
@Tag("unit")
@Tag("report")
class ManagePatientLetters2ActionUnitTest extends CarlosUnitTestBase {

    /** Smallest JasperReports 7 template that compiles: one user parameter, no bands. */
    private static final String MINIMAL_JRXML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<jasperReport name=\"FakeLetter\" pageWidth=\"595\" pageHeight=\"842\" columnWidth=\"555\""
            + " leftMargin=\"20\" rightMargin=\"20\" topMargin=\"20\" bottomMargin=\"20\">\n"
            + "  <parameter name=\"first_name\" class=\"java.lang.String\"/>\n"
            + "</jasperReport>\n";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    private SecurityInfoManager securityInfoManager;
    private ReportLettersDao reportLettersDao;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private File uploadTemp;

    @BeforeEach
    void setUp() throws Exception {
        securityInfoManager = mock(SecurityInfoManager.class);
        reportLettersDao = mock(ReportLettersDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ReportLettersDao.class, reportLettersDao);
        registerMock(LogLettersDao.class, mock(LogLettersDao.class));

        request = new MockHttpServletRequest("POST", "/carlos/report/ManageLetters");
        request.getSession().setAttribute("user", "999998");
        request.addParameter("reportName", "FAKE reminder letter");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_report"), eq("r"), isNull()))
                .thenReturn(true);

        // Stand-in for the Struts multipart temp file: same upload_*.tmp shape, in java.io.tmpdir.
        uploadTemp = File.createTempFile("upload_", ".tmp");
        Files.write(uploadTemp.toPath(), MINIMAL_JRXML.getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (uploadTemp != null) {
            Files.deleteIfExists(uploadTemp.toPath());
        }
    }

    private UploadedFile upload(String inputName, String originalName) {
        return StrutsUploadedFile.Builder.create(uploadTemp)
                .withInputName(inputName)
                .withOriginalName(originalName)
                .withContentType("text/xml")
                .build();
    }

    @Nested
    @DisplayName("upload")
    class Upload {

        @Test
        @DisplayName("should store the original file name instead of the Struts temp name")
        void shouldSaveOriginalFileName_whenTemplateUploaded() {
            ManagePatientLetters2Action action = new ManagePatientLetters2Action();
            action.withUploadedFiles(List.of(upload("reportFile", "Flu Reminder.jrxml")));

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            ArgumentCaptor<ReportLetters> saved = ArgumentCaptor.forClass(ReportLetters.class);
            verify(reportLettersDao).persist(saved.capture());
            assertThat(saved.getValue().getFileName()).isEqualTo("Flu_Reminder.jrxml");
            assertThat(saved.getValue().getFileName()).doesNotStartWith("upload_");
            assertThat(saved.getValue().getReportName()).isEqualTo("FAKE reminder letter");
            assertThat(saved.getValue().getProviderNo()).isEqualTo("999998");
        }

        @Test
        @DisplayName("should bind only the reportFile input")
        void shouldIgnoreOtherInputs_whenBindingUploads() {
            ManagePatientLetters2Action action = new ManagePatientLetters2Action();
            action.withUploadedFiles(List.of(upload("somethingElse", "other.jrxml")));

            assertThat(action.getReportFile()).isNull();
            assertThat(action.getReportFileFileName()).isNull();
            assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
            verifyNoInteractions(reportLettersDao);
        }

        @Test
        @DisplayName("should reject GET with 405 before saving anything")
        void shouldReturn405_whenMethodIsGet() {
            request.setMethod("GET");
            ManagePatientLetters2Action action = new ManagePatientLetters2Action();
            action.withUploadedFiles(List.of(upload("reportFile", "letter.jrxml")));

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            verifyNoInteractions(reportLettersDao);
        }

        @Test
        @DisplayName("should keep the prevention-report return target")
        void shouldReturnPreventionResult_whenGotoRequested() {
            request.addParameter("goto", "success_manage_from_prevention");
            ManagePatientLetters2Action action = new ManagePatientLetters2Action();
            action.withUploadedFiles(List.of(upload("reportFile", "letter.jrxml")));

            assertThat(action.execute()).isEqualTo("success_manage_from_prevention");
        }
    }

    @Nested
    @DisplayName("stored file name")
    class StoredFileName {

        @ParameterizedTest(name = "[{index}] {0} -> {1}")
        @CsvSource(delimiter = '|', value = {
                "Flu Reminder.jrxml|Flu_Reminder.jrxml",
                "C:\\fakepath\\letter.jrxml|letter.jrxml",
                "../../etc/passwd|passwd",
                "shell.jsp|letter-template.jrxml",
                ".hidden|letter-template.jrxml",
                "'  '|letter-template.jrxml"
        })
        @DisplayName("should reduce the client name to a safe basename")
        void shouldSanitizeFileName_forUntrustedInput(String original, String expected) {
            ManagePatientLetters2Action action = new ManagePatientLetters2Action();
            action.setReportFileFileName(original);

            assertThat(action.resolveStoredFileName()).isEqualTo(expected);
        }

        @Test
        @DisplayName("should drop quotes and line breaks that could split the download header")
        void shouldStripHeaderBreakingCharacters_fromFileName() {
            ManagePatientLetters2Action action = new ManagePatientLetters2Action();
            action.setReportFileFileName("a\"b\r\nSet-Cookie: x.jrxml");

            String stored = action.resolveStoredFileName();

            assertThat(stored).doesNotContain("\"", "\r", "\n", ":", " ").endsWith(".jrxml");
        }

        @Test
        @DisplayName("should fall back when the browser sent no file name")
        void shouldUseFallback_whenFileNameMissing() {
            assertThat(new ManagePatientLetters2Action().resolveStoredFileName())
                    .isEqualTo(ManagePatientLetters2Action.FALLBACK_FILE_NAME);
        }

        @Test
        @DisplayName("should cap the name at the column width and keep the extension")
        void shouldTruncateFileName_toColumnWidth() {
            ManagePatientLetters2Action action = new ManagePatientLetters2Action();
            action.setReportFileFileName("a".repeat(300) + ".jrxml");

            String stored = action.resolveStoredFileName();

            assertThat(stored).hasSize(255).endsWith(".jrxml");
        }
    }
}
