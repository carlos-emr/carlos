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

import jakarta.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("should reject malformed CSV when generating XLS")
    void shouldRejectMalformedCsv_whenGeneratingXls() throws Exception {
        request.setParameter("getXLS", "Export to XLS");
        request.setParameter("csv", "\"unterminated");

        String result = new GenerateOutFiles2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("should set server error status when XLS write fails")
    void shouldSetServerErrorStatus_whenXlsWriteFails() throws Exception {
        HttpServletResponse failingResponse = mock(HttpServletResponse.class);
        when(failingResponse.isCommitted()).thenReturn(false);
        when(failingResponse.getOutputStream()).thenThrow(new IOException("write failed"));
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(failingResponse);
        request.setParameter("getXLS", "Export to XLS");
        request.setParameter("csv", "alpha,beta\n1,2");

        String result = new GenerateOutFiles2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(failingResponse).reset();
        verify(failingResponse).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
}
