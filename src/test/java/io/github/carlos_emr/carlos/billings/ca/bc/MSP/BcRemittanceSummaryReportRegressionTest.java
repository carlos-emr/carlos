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
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The BC MSP remittance summary report (REP_MSPREMSUM, issue #4895): every sub-report
 * {@code CreateBillingReport2Action} loads for it is shipped and allow-listed, and is a parameter the
 * report templates declare. It used to load an "orphan adjustments" sub-report that was never committed,
 * and the report answered HTTP 500.
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC MSP remittance summary report")
class BcRemittanceSummaryReportRegressionTest {

    private static final Path ACTION = Path.of(
            "src/main/java/io/github/carlos_emr/carlos/billings/ca/bc/MSP/CreateBillingReport2Action.java");
    private static final Path DOCUMENT_CREATOR = Path.of("src/main/java/io/github/carlos_emr/OscarDocumentCreator.java");
    private static final Path REPORTS = Path.of("src/main/resources/oscar/oscarBilling/ca/bc/reports");
    private static final Pattern CONFIG = Pattern.compile("reportCfg\\.setProperty\\((?:MSPReconcile\\.)?\"?(\\w+)\"?,\\s*\"([^\"]+)\"\\)");
    private static final Pattern SUB_REPORT = Pattern.compile(
            "getDocumentStream\\(REPORTS_PATH \\+ this\\.reportCfg\\.getProperty\\(\"(\\w+)\"\\)\\);\\s*try \\{\\s*reportParams\\.put\\(\"(\\w+)\", osc\\.getJasperReport\\(");
    private static final Pattern JASPER_PARAMETER = Pattern.compile(
            "<parameter name=\"(\\w+)\" class=\"net\\.sf\\.jasperreports\\.engine\\.JasperReport\"");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** The REP_MSPREMSUM branch of the action, up to the next report type. */
    private static String remittanceSummaryBranch() throws IOException {
        String action = read(ACTION);
        int start = action.indexOf("repType.equals(MSPReconcile.REP_MSPREMSUM)");
        assertThat(start).as("the action has a remittance summary branch").isNotNegative();
        int end = action.indexOf("} else if (repType.equals(", start);
        return action.substring(start, end);
    }

    /** Sub-report parameter name to the file its config key names. */
    private static Map<String, String> subReports() throws IOException {
        Map<String, String> files = new HashMap<>();
        Matcher config = CONFIG.matcher(read(ACTION));
        while (config.find()) {
            files.put(config.group(1), config.group(2));
        }
        Map<String, String> byParameter = new HashMap<>();
        Matcher subReport = SUB_REPORT.matcher(remittanceSummaryBranch());
        while (subReport.find()) {
            assertThat(files).as("no report file is configured for %s", subReport.group(1)).containsKey(subReport.group(1));
            byParameter.put(subReport.group(2), files.get(subReport.group(1)));
        }
        assertThat(byParameter).as("the branch loads its sub-reports").isNotEmpty();
        return byParameter;
    }

    @Test
    @DisplayName("should ship and allow-list every sub-report the remittance summary loads")
    void shouldShipEverySubReport_theRemittanceSummaryLoads() throws IOException {
        String allowList = read(DOCUMENT_CREATOR);
        for (Map.Entry<String, String> subReport : subReports().entrySet()) {
            assertThat(REPORTS.resolve(subReport.getValue())).as("sub-report %s", subReport.getKey()).exists();
            assertThat(allowList).as("allow-list entry for %s", subReport.getValue())
                    .contains("bc + \"" + subReport.getValue() + "\"");
        }
        assertThat(allowList).doesNotContain("s23_orphan");
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf_rep_mspremsum.jrxml", "csv_rep_mspremsum.jrxml"})
    @DisplayName("should load exactly the sub-reports the remittance summary template declares")
    void shouldLoadDeclaredSubReports_forRemittanceSummaryTemplate(String template) throws IOException {
        Set<String> declared = new TreeSet<>();
        Matcher parameter = JASPER_PARAMETER.matcher(read(REPORTS.resolve(template)));
        while (parameter.find()) {
            declared.add(parameter.group(1));
        }

        assertThat(new TreeSet<>(subReports().keySet())).isEqualTo(declared);
    }
}
