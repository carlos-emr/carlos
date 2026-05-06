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

import io.github.carlos_emr.carlos.services.security.SecurityManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for report-by-template CSV and spreadsheet export handling.
 */
@DisplayName("GenerateOutFiles2Action")
@Tag("unit")
@Tag("report")
class GenerateOutFiles2ActionTest extends CarlosUnitTestBase {

    private static final int MAX_CSV_EXPORT_LENGTH = 5 * 1024 * 1024;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<SecurityManager> securityManagerMock;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", "999998");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        securityManagerMock = mockStatic(SecurityManager.class);
        securityManagerMock.when(() -> SecurityManager.hasPrivilege(eq("_admin"), anyString())).thenReturn(false);
        securityManagerMock.when(() -> SecurityManager.hasPrivilege(eq("_report"), anyString())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (securityManagerMock != null) {
            securityManagerMock.close();
        }
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
        assertThat(response.getContentAsString()).isEqualTo("from-request");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"oscarReport.csv\"");
    }

    @Test
    @DisplayName("should reject missing CSV when only session CSV exists")
    void shouldRejectMissingCsv_whenOnlySessionCsvExists() {
        request.setParameter("getCSV", "Export to CSV");
        request.getSession().setAttribute("csv", "from-session");

        String result = new GenerateOutFiles2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("should reject oversized CSV when posted CSV exceeds limit")
    void shouldRejectOversizedCsv_whenPostedCsvExceedsLimit() {
        request.setParameter("getCSV", "Export to CSV");
        request.setParameter("csv", "a".repeat(MAX_CSV_EXPORT_LENGTH + 1));

        String result = new GenerateOutFiles2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        assertThat(response.getContentAsString()).isEmpty();
    }
}
