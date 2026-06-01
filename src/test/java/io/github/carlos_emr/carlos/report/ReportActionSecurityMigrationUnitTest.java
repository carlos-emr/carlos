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
package io.github.carlos_emr.carlos.report;

import io.github.carlos_emr.carlos.commn.dao.ReportByExamplesDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.pageUtil.RptByExample2Action;
import io.github.carlos_emr.carlos.report.reportByTemplate.actions.ExportTemplate2Action;
import io.github.carlos_emr.carlos.report.reportByTemplate.actions.GenerateOutFiles2Action;
import io.github.carlos_emr.carlos.report.reportByTemplate.actions.GenerateReport2Action;
import io.github.carlos_emr.carlos.report.reportByTemplate.actions.UploadTemplates2Action;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Report action security migration")
@Tag("unit")
@Tag("report")
class ReportActionSecurityMigrationUnitTest extends CarlosUnitTestBase {
    private static final String MISSING_ADMIN_OR_REPORT = "missing required sec object (_admin or _report)";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(ReportByExamplesDao.class, mock(ReportByExamplesDao.class));

        request = new MockHttpServletRequest();
        request.setContextPath("/carlos");
        response = new MockHttpServletResponse();

        servletActionContextMock = org.mockito.Mockito.mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfo = mock(LoggedInInfo.class);
    }

    @AfterEach
    void tearDown() {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("RptByExample redirects to logout when LoggedInInfo is missing")
    void shouldRedirectToLogout_whenRptByExampleHasNoLoggedInInfo() throws Exception {
        String result = new RptByExample2Action().execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/logout.htm");
        verifyNoInteractions(securityInfoManager);
    }

    @Test
    @DisplayName("report-template actions fail closed when LoggedInInfo is missing")
    void shouldFailClosed_whenReportTemplateActionsHaveNoLoggedInInfo() {
        assertMissingLoggedInInfoFails(new ExportTemplate2Action());
        assertMissingLoggedInInfoFails(new GenerateOutFiles2Action());
        assertMissingLoggedInInfoFails(new GenerateReport2Action());
        assertMissingLoggedInInfoFails(new UploadTemplates2Action());
    }

    @Test
    @DisplayName("migrated actions require admin or report read privilege")
    void shouldRequireAdminOrReportReadPrivilege_forMigratedActions() {
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), loggedInInfo);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null)).thenReturn(false);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.READ, null)).thenReturn(false);

        assertMissingPrivilegeFails(new RptByExample2Action());
        assertMissingPrivilegeFails(new ExportTemplate2Action());
        assertMissingPrivilegeFails(new GenerateOutFiles2Action());
        assertMissingPrivilegeFails(new GenerateReport2Action());
        assertMissingPrivilegeFails(new UploadTemplates2Action());

        verify(securityInfoManager, times(5))
                .hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null);
        verify(securityInfoManager, times(5))
                .hasPrivilege(loggedInInfo, "_report", SecurityInfoManager.READ, null);
    }

    private void assertMissingLoggedInInfoFails(ActionSupport action) {
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage(MISSING_ADMIN_OR_REPORT);
    }

    private void assertMissingPrivilegeFails(ActionSupport action) {
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage(MISSING_ADMIN_OR_REPORT);
    }
}
