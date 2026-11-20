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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DeletePatientLetters2Action}: Manage Letters' Delete archives a letter
 * template only on a POST from a user who may write reports, and says so when nothing was archived.
 */
@DisplayName("DeletePatientLetters2Action")
@Tag("unit")
@Tag("report")
class DeletePatientLetters2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager securityInfoManager;
    private ReportLettersDao reportLettersDao;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        reportLettersDao = mock(ReportLettersDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ReportLettersDao.class, reportLettersDao);
        registerMock(LogLettersDao.class, mock(LogLettersDao.class));

        request = new MockHttpServletRequest("POST", "/carlos/report/DeleteLetter");
        request.addParameter("reportID", "7");
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_report"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_report"), eq("w"), isNull())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    private static ReportLetters template(String archive) {
        ReportLetters letter = new ReportLetters();
        letter.setArchive(archive);
        return letter;
    }

    @Test
    @DisplayName("should archive the template and list the page again on POST")
    void shouldArchiveTemplate_whenPostedByReportWriter() {
        ReportLetters letter = template("0");
        when(reportLettersDao.find(7)).thenReturn(letter);

        assertThat(new DeletePatientLetters2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(letter.getArchive()).isEqualTo("1");
        verify(reportLettersDao).merge(letter);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(request.getAttribute("letterDeleteFailed")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should refuse a non-POST with 405 before reading or archiving anything")
    void shouldReturn405_whenMethodIsNotPost(String method) {
        request.setMethod(method);

        assertThat(new DeletePatientLetters2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(reportLettersDao);
    }

    @Test
    @DisplayName("should refuse a user who can only read reports")
    void shouldThrowSecurityException_whenUserCanOnlyReadReports() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_report"), eq("w"), isNull())).thenReturn(false);

        assertThatThrownBy(() -> new DeletePatientLetters2Action().execute())
                .isInstanceOf(SecurityException.class);

        verifyNoInteractions(reportLettersDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "0", "-3", "7 OR 1=1", "99999999999"})
    @DisplayName("should show the failure message with 400 for a missing or malformed template id")
    void shouldShowFailure_whenTemplateIdIsMalformed(String reportId) {
        request.removeParameter("reportID");
        request.addParameter("reportID", reportId);

        assertThat(new DeletePatientLetters2Action().execute()).isEqualTo(ActionSupport.INPUT);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(request.getAttribute("letterDeleteFailed")).isEqualTo(Boolean.TRUE);
        verifyNoInteractions(reportLettersDao);
    }

    @Test
    @DisplayName("should show the failure message with 400 when no template id is sent")
    void shouldShowFailure_whenTemplateIdIsAbsent() {
        request.removeParameter("reportID");

        assertThat(new DeletePatientLetters2Action().execute()).isEqualTo(ActionSupport.INPUT);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(request.getAttribute("letterDeleteFailed")).isEqualTo(Boolean.TRUE);
        verifyNoInteractions(reportLettersDao);
    }

    @Test
    @DisplayName("should show the failure message with 404 when the template does not exist")
    void shouldShowFailure_whenTemplateIsUnknown() {
        when(reportLettersDao.find(7)).thenReturn(null);

        assertThat(new DeletePatientLetters2Action().execute()).isEqualTo(ActionSupport.INPUT);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        assertThat(request.getAttribute("letterDeleteFailed")).isEqualTo(Boolean.TRUE);
        verify(reportLettersDao, never()).merge(any());
    }

    @Test
    @DisplayName("should show the failure message with 500 when the archive is not saved")
    void shouldShowFailure_whenArchiveIsNotSaved() {
        ReportLetters letter = template("0");
        when(reportLettersDao.find(7)).thenReturn(letter);
        doThrow(new IllegalStateException("FAKE database failure")).when(reportLettersDao).merge(letter);

        assertThat(new DeletePatientLetters2Action().execute()).isEqualTo(ActionSupport.INPUT);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        assertThat(request.getAttribute("letterDeleteFailed")).isEqualTo(Boolean.TRUE);
    }
}
