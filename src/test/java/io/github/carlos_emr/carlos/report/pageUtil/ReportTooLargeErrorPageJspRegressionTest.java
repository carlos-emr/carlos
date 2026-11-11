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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * #5850: a report CSV download refused by its limits answers 413, and the container shows the
 * error page. That page shows the status message only in its developer block, so it says what to
 * do from fixed bundle text, and only for the 413s the report download marked.
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("report")
@DisplayName("Error page for a report too large to download")
class ReportTooLargeErrorPageJspRegressionTest {

    private static final Path PAGE = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "error", "errorpage.jsp");

    private static String page() throws IOException {
        // Comments do not count: a check left inside <%-- --%> would not run.
        return Files.readString(PAGE, StandardCharsets.UTF_8).replaceAll("(?s)<%--.*?--%>", "");
    }

    @Test
    void shouldSayWhatToDo_whenTheReportDownloadMarkedItsRefusal() throws IOException {
        assertThat(page()).containsSubsequence(
                "CARLOS Error: ${carlos:forHtml(_responseStatus)}",
                "<c:if test=\"${_responseStatus == 413 and requestScope."
                        + RptDownloadCSVServlet.REPORT_TOO_LARGE_ATTRIBUTE + " == true}\">",
                "<fmt:message key=\"report.limitRefused\" var=\"_reportLimitRefused\"/>",
                "<p id=\"report-limit-refused\" role=\"alert\"><carlos:encode value=\"${_reportLimitRefused}\"/></p>",
                "</c:if>",
                // Before, and outside, the developer block that alone shows the status message.
                "<c:if test=\"${_displayError}\">");
    }

    @Test
    void shouldShowTheReportLine_onlyForAMarkedRefusal() throws IOException {
        // Uploads and attachments answer 413 too; they are not reports and set no marker.
        assertThat(page()).doesNotContain("<c:if test=\"${_responseStatus == 413}\">");
        assertThat(page().split("report\\.limitRefused", -1)).hasSize(2);
    }
}
