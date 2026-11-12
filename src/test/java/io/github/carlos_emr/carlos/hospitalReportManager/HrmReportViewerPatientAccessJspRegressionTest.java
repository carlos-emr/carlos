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
package io.github.carlos_emr.carlos.hospitalReportManager;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The HRM report viewer page reads the report itself, and the inbox includes it without going
 * through the viewer's action. The page must therefore make the patient decision on its own,
 * before the first read of the report, and the list page must keep reading through
 * {@code HRMUtil}, which makes it for a patient's list.
 */
@Tag("unit")
@Tag("hrm")
@Tag("security")
@DisplayName("HRM viewer pages decide patient access before reading a report")
class HrmReportViewerPatientAccessJspRegressionTest {

    private static final Path VIEWER = Path.of("src/main/webapp/WEB-INF/jsp/hospitalReportManager/displayHRMReport.jsp");
    private static final Path LIST = Path.of("src/main/webapp/WEB-INF/jsp/hospitalReportManager/displayHRMDocList.jsp");
    private static final Path INBOX = Path.of("src/main/webapp/WEB-INF/jsp/oscarMDS/Page.jsp");

    @Test
    @DisplayName("should decide patient access in the viewer before the report row, its file or its links are read")
    void shouldDecideAccess_beforeViewerReadsReport() throws IOException {
        String jsp = Files.readString(VIEWER, StandardCharsets.UTF_8);

        int decision = jsp.indexOf("HRMReportAccess.mayReadReport(");
        assertThat(decision).as("the viewer asks HRMReportAccess").isPositive();
        for (String read : new String[] {"hrmDocumentDao.findById(hrmReportId)", "HRMReportParser.parseReport(",
                "hrmDocumentToDemographicDao.findByHrmDocumentId(", "hrmDocumentCommentDao.getCommentsForDocument(",
                "hrmDocumentToProviderDao.findByHrmDocumentIdNoSystemUser(", "LogAction.addLog("}) {
            assertThat(jsp.indexOf(read)).as("%s comes after the decision", read).isGreaterThan(decision);
        }
        // The decision uses the id the page then reads, parsed once.
        assertThat(jsp.indexOf("Integer hrmReportId = Integer.parseInt(request.getParameter(\"id\"));")).isBetween(0, decision);
    }

    @Test
    @DisplayName("should answer a refused direct request with 403 and a refused inbox include with text only")
    void shouldRefuseWithStatusOrText_whenViewerDeniesAccess() throws IOException {
        String jsp = Files.readString(VIEWER, StandardCharsets.UTF_8);
        int decision = jsp.indexOf("HRMReportAccess.mayReadReport(");
        int firstRead = jsp.indexOf("hrmDocumentDao.findById(hrmReportId)");
        String refusal = jsp.substring(decision, firstRead);

        assertThat(refusal)
                .contains("RequestDispatcher.INCLUDE_REQUEST_URI")
                .contains("response.sendError(HttpServletResponse.SC_FORBIDDEN)")
                .contains("<fmt:message key=\"demographic.demographiceditdemographic.accessDenied\"/>")
                .contains("return;");
    }

    @Test
    @DisplayName("should keep the inbox including the viewer page, which is why the page decides for itself")
    void shouldIncludeViewer_fromInboxPage() throws IOException {
        assertThat(Files.readString(INBOX, StandardCharsets.UTF_8))
                .contains("<jsp:include page=\"/WEB-INF/jsp/hospitalReportManager/displayHRMReport.jsp\"");
    }

    @Test
    @DisplayName("should list a patient's reports only through HRMUtil, which applies the patient's access rules")
    void shouldListThroughHrmUtil_inListPage() throws IOException {
        String jsp = Files.readString(LIST, StandardCharsets.UTF_8);

        assertThat(jsp).contains("HRMUtil.listHRMDocuments(loggedInInfo, orderBy, asc, demographic_no, true)")
                .doesNotContain("HRMDocumentToDemographicDao")
                .doesNotContain("HRMReportParser");
    }
}
