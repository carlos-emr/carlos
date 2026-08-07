/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards the schema and fail-visible behavior required by the PHCP encounter report. */
@DisplayName("PHCP report migration regressions")
@Tag("unit")
@Tag("report")
class PhcpReportMigrationRegressionTest {

    private static final Path MIGRATION = Path.of("database", "mysql", "migration", "common",
            "V1.0.7__restore_phcp_diagnosis_groups.sql");
    private static final Path REPORT = Path.of("src", "main", "webapp", "WEB-INF", "jsp",
            "report", "reportonbilledphcp.jsp");

    @Test
    @DisplayName("should create and seed the PHCP diagnosis grouping lookup")
    void shouldCreateAndSeedPhcpDiagnosisGroups() throws IOException {
        String migration = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS dxphcpgroup")
                .contains("lastUpdateUser varchar(100) NOT NULL")
                .contains("lastUpdateDate timestamp NOT NULL")
                .contains("FROM diagnosticcode")
                .contains("WHERE diagnostic_code REGEXP '^[0-9]{1,5}$'")
                .contains("WHEN diagnostic_code = CAST(CAST(diagnostic_code AS UNSIGNED) AS CHAR)")
                .contains("MIN(CAST(LEFT(diagnostic_code, 3) AS UNSIGNED))")
                .contains("WHEN codes.category_code <= 279 THEN '03 Endocrine")
                .contains("WHERE NOT EXISTS (")
                .contains("ICD-9 001-139", "ICD-9 800-999");
    }

    @Test
    @DisplayName("should fail visibly, tolerate alphanumeric codes, and encode category labels")
    void shouldRenderDiagnosisSearchesSafelyAndFailVisibly() throws IOException {
        String report = Files.readString(REPORT, StandardCharsets.UTF_8);
        String normalizedReport = report.replaceAll("\\s+", " ").trim();

        assertThat(normalizedReport)
                .contains("<%@ page errorPage=\"/WEB-INF/jsp/error/errorpage.jsp\" buffer=\"64kb\" %>")
                .contains("if (bDx) { sql = \"select dxcode, level1, level2 from dxphcpgroup")
                .contains("serviceCode.matches(\"[0-9]{1,5}\")")
                .contains("<carlos:encode value='<%= curCatName %>' context=\"html\"/>")
                .contains("getProperty(\"providerNo\", \"\") %>' context=\"htmlAttribute\"/>")
                .contains("getProperty(\"lastName\", \"\") %>' context=\"html\"/>")
                .doesNotContain("<td colspan=\"24\"><%= curCatName %>")
                .doesNotContain("<option value=\"<%=((Properties)VEC_PROVIDER")
                .doesNotContain("catch (Exception e)")
                .doesNotContain("request.getRequestDispatcher(\"/WEB-INF/jsp/error/errorpage.jsp\")");
    }
}
