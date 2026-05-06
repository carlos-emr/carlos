/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for report-by-template CSV state handling.
 */
@DisplayName("SQLReporter CSV session storage")
@Tag("unit")
@Tag("report")
class SQLReporterSessionStorageTest {

    @Test
    @DisplayName("should avoid HttpSession CSV storage for report by template")
    void shouldAvoidHttpSessionCsvStorage_forReportByTemplate() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/io/github/carlos_emr/carlos/report/reportByTemplate/SQLReporter.java"),
                StandardCharsets.UTF_8);

        assertThat(source).doesNotContainPattern(
                "getSession\\s*\\(\\s*(?:false\\s*)?\\)\\s*\\.\\s*setAttribute\\s*\\(\\s*\"csv\"");
        assertThat(source).doesNotContainPattern(
                "HttpSession\\s+\\w+\\s*=\\s*\\w+\\s*\\.\\s*getSession\\s*\\(\\s*(?:false\\s*)?\\)");
        assertThat(source).doesNotContainPattern(
                "\\b\\w+\\s*\\.\\s*setAttribute\\s*\\(\\s*\"csv\"");
        assertThat(source).contains("MAX_CSV_EXPORT_LENGTH");
    }
}
