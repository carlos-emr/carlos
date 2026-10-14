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
package io.github.carlos_emr.carlos.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for the Manage Job Types DataTables lifecycle.
 *
 * @since 2026-08-11
 */
@DisplayName("Manage Job Types JSP regressions")
@Tag("unit")
@Tag("regression")
class JobTypesJspRegressionTest {
    private static final Path JOB_TYPES_JSP = projectRoot()
            .resolve("src/main/webapp/WEB-INF/jsp/admin/jobTypes.jsp");

    @Test
    void shouldRefreshRows_throughExistingDataTable() throws IOException {
        String jsp = Files.readString(JOB_TYPES_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("let jobTypeTable;")
                .contains("if (!jobTypeTable)")
                .contains("table.clear();")
                .contains("table.rows.add(rows);")
                .contains("table.draw();")
                .doesNotContain("$(\"#jobTypeTable tbody tr\").remove();");
        assertThat(countOccurrences(jsp, "$('#jobTypeTable').DataTable({"))
                .as("the JSP should contain one DataTables initialization path")
                .isEqualTo(1);
    }

    @Test
    void shouldKeepDialogOpen_whenSaveFails() throws IOException {
        String jsp = Files.readString(JOB_TYPES_JSP, StandardCharsets.UTF_8);
        int saveRequestStart = jsp.indexOf("$.post('${pageContext.request.contextPath}/ws/rs/jobs/saveJobType'");
        int cancelButtonStart = jsp.indexOf("cancel:", saveRequestStart);

        assertThat(saveRequestStart).isGreaterThanOrEqualTo(0);
        assertThat(cancelButtonStart).isGreaterThan(saveRequestStart);
        assertThat(jsp.substring(saveRequestStart, cancelButtonStart))
                .contains(".done(function () {")
                .contains("listJobs();")
                .contains("$dialog.dialog(\"close\");")
                .contains(".fail(function () {")
                .contains("window.alert(jobTypesSaveErrorLabel);");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty(
                "maven.multiModuleProjectDirectory",
                System.getProperty("user.dir")));
    }
}
