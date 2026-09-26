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
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the request guards in {@link GeneratePatientLetters2Action} (issue #3963):
 * POST-only, the empty-selection notice, and numeric demographic validation. Letter rendering
 * itself is covered by the browser check in {@code scripts/patient-letters-envelopes-playwright-checks.js}.
 */
@DisplayName("GeneratePatientLetters2Action")
@Tag("unit")
@Tag("report")
class GeneratePatientLetters2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    private SecurityInfoManager securityInfoManager;
    private ReportLettersDao reportLettersDao;
    private LogLettersDao logLettersDao;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        reportLettersDao = mock(ReportLettersDao.class);
        logLettersDao = mock(LogLettersDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ReportLettersDao.class, reportLettersDao);
        registerMock(LogLettersDao.class, logLettersDao);

        request = new MockHttpServletRequest("POST", "/carlos/report/GenerateLetters");
        request.getSession().setAttribute("user", "999998");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_report"), eq("r"), isNull()))
                .thenReturn(true);
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

    @Test
    @DisplayName("should redisplay the letters page before loading the template when no patient is selected")
    void shouldReturnInput_whenNoPatientsSelected() {
        request.addParameter("reportLetter", "1");

        String result = new GeneratePatientLetters2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.INPUT);
        assertThat(request.getAttribute(GenerateEnvelopes2Action.NO_PATIENTS_SELECTED_ATTRIBUTE))
                .isEqualTo(Boolean.TRUE);
        assertThat(response.getContentAsByteArray()).isEmpty();
        verifyNoInteractions(reportLettersDao, logLettersDao);
    }

    @Test
    @DisplayName("should reject GET with 405 before any letter work")
    void shouldReturn405_whenMethodIsGet() {
        request.setMethod("GET");
        request.addParameter("demos", "1");
        request.addParameter("reportLetter", "1");

        String result = new GeneratePatientLetters2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verifyNoInteractions(reportLettersDao, logLettersDao);
    }

    @Test
    @DisplayName("should reject a non-numeric demographic number before loading the template")
    void shouldThrowSecurityException_forNonNumericDemographic() {
        request.addParameter("demos", "1", "../../etc");
        request.addParameter("reportLetter", "1");

        assertThatThrownBy(() -> new GeneratePatientLetters2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("Invalid demographic number");
        verifyNoInteractions(reportLettersDao, logLettersDao);
    }

    @Test
    @DisplayName("should throw SecurityException without _report read")
    void shouldThrowSecurityException_whenReportPrivilegeMissing() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_report"), eq("r"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> new GeneratePatientLetters2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_report)");
    }
}
