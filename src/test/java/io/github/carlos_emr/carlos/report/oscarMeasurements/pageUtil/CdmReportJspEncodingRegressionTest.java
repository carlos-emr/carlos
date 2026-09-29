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

package io.github.carlos_emr.carlos.report.oscarMeasurements.pageUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how the CDM report pages and the other measurement pages render measuring instructions.
 *
 * <p>An instruction is stored per reading from the entry form's {@code inputMInstrc-*} field, so
 * it is user-controlled text. Since the CDM reports list every instruction still stored on
 * readings (issue #3893, AACP), an unencoded {@code value="${mInstrc.measuringInstrc}"} would be a
 * stored XSS sink. The reports also have to read each row's instructions from the handler that
 * {@code RptMeasurementTypesBeanHandler} builds for it, which the pages previously did not do.</p>
 *
 * @since 2026-09-24
 */
@Tag("unit")
@Tag("fast")
@Tag("security")
class CdmReportJspEncodingRegressionTest {

    private static final Path CDM = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "oscarReport", "oscarMeasurements");
    private static final Path MEASUREMENTS = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "encounter", "oscarMeasurements");
    private static final Pattern EL = Pattern.compile("\\$\\{([^}]*)}");

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"InitializePatientsMetGuidelineCDMReport.jsp", "InitializePatientsInAbnormalRangeCDMReport.jsp",
            "InitializeFrequencyOfRelevantTestsCDMReport.jsp"})
    @DisplayName("should encode every measuring instruction and read it from the row's own handler")
    void shouldEncodeInstructions_onEveryCdmScreen(String page) throws Exception {
        String jsp = Files.readString(CDM.resolve(page), StandardCharsets.UTF_8);

        assertThat(rawElMentioning(jsp, "measuringInstrc")).as("unencoded instruction output in %s", page).isEmpty();
        assertThat(jsp).contains("measurementTypes.measuringInstrcBeanVector[ctr.index].measuringInstrcVector")
                .doesNotContain("${mInstrcs[")
                .doesNotContain("measurementType.measuringInstrcVector")
                .doesNotContain("/img/calendar.gif");
    }

    @Test
    @DisplayName("should encode the CDM group names and the met-guideline hidden fields")
    void shouldEncodeGroupAndHiddenFields_forCdmEntryPages() throws Exception {
        String select = Files.readString(CDM.resolve("SelectCDMReport.jsp"), StandardCharsets.UTF_8);
        String metGuideline = Files.readString(CDM.resolve("InitializePatientsMetGuidelineCDMReport.jsp"), StandardCharsets.UTF_8);

        assertThat(rawElMentioning(select, "groupName")).isEmpty();
        assertThat(select).contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>");
        assertThat(rawElMentioning(metGuideline, "measurementType.type")).isEmpty();
        assertThat(rawElMentioning(metGuideline, "lastYear")).isEmpty();
        assertThat(rawElMentioning(metGuideline, "today")).isEmpty();
    }

    @Test
    @DisplayName("should encode the stored instruction on the measurement history page")
    void shouldEncodeInstruction_onDisplayHistory() throws Exception {
        String jsp = Files.readString(MEASUREMENTS.resolve("DisplayHistory.jsp"), StandardCharsets.UTF_8);

        assertThat(rawElMentioning(jsp, "measuringInstrc")).isEmpty();
        assertThat(rawElMentioning(jsp, "providerFirstName")).isEmpty();
        assertThat(rawElMentioning(jsp, "typeDescription")).isEmpty();
    }

    @Test
    @DisplayName("should encode measurement type names and instructions in the clinical reports selects")
    void shouldEncodeInstruction_inClinicalReports() throws Exception {
        String jsp = Files.readString(Path.of("src", "main", "webapp", "WEB-INF", "jsp", "report", "ClinicalReports.jsp"),
                StandardCharsets.UTF_8);

        assertThat(jsp).doesNotContain("<%=measurementTypes.getMeasuringInstrc() %>")
                .doesNotContain("(<%=measInst %>)")
                .contains("SafeEncode.forHtmlContent(measurementTypes.getMeasuringInstrc())");
    }

    /** EL expressions that mention {@code name} but are not wrapped in a CARLOS encoder. */
    private static List<String> rawElMentioning(String jsp, String name) {
        List<String> raw = new ArrayList<>();
        Matcher matcher = EL.matcher(jsp);
        while (matcher.find()) {
            String expression = matcher.group(1);
            if (!expression.contains(name)) {
                continue;
            }
            String trimmed = expression.trim();
            boolean encoded = trimmed.startsWith("carlos:for");
            // Structural uses (c:forEach items, c:set, tests, sizes) are not output.
            int start = matcher.start();
            String before = jsp.substring(Math.max(0, start - 12), start);
            boolean structural = before.endsWith("items=\"") || before.endsWith("value=\"") && jsp.substring(Math.max(0, start - 40), start).contains("<c:set")
                    || before.endsWith("test=\"") || trimmed.startsWith("empty ") || trimmed.startsWith("not empty")
                    || trimmed.contains(" == ");
            if (!encoded && !structural) {
                raw.add(expression);
            }
        }
        return raw;
    }
}
