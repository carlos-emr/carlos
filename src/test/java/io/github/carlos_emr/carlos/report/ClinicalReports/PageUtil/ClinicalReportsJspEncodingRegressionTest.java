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

package io.github.carlos_emr.carlos.report.ClinicalReports.PageUtil;

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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how the Clinical Reports page prints the values it gets back from a run.
 *
 * <p>{@code RunClinicalReport2Action} copies the posted criteria (each numerator's value, start and
 * end date) and the chosen {@code showfields} into request attributes, and the page printed them
 * back raw: in {@code value="..."} attributes and in the result table's headings. It also printed
 * stored text raw: measurement values in the result cells, the CSV box, and the numerator,
 * denominator, provider and patient-set names. Each of these now goes through
 * {@code <carlos:encode>} in the context it lands in.</p>
 *
 * <p>Any other {@code <%= %>} output on the page must be a value the server builds itself (a loop
 * index, the context path, a {@code selected}/{@code checked} flag, a count) and is listed in
 * {@link #REVIEWED_RAW}; a new raw output fails here until it is encoded or reviewed.</p>
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("fast")
@Tag("security")
class ClinicalReportsJspEncodingRegressionTest extends io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase {

    private static final Path JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "report", "ClinicalReports.jsp");

    /** A {@code <carlos:encode value='<%= ... %>' .../>} tag, whose scriptlet is encoded output. */
    private static final Pattern ENCODE_TAG = Pattern.compile("<carlos:encode\\s+value='<%=.*?%>'[^>]*/>", Pattern.DOTALL);
    private static final Pattern RAW_OUTPUT = Pattern.compile("<%=(.*?)%>", Pattern.DOTALL);

    /** Raw outputs reviewed as server-built (whitespace removed). */
    private static final Set<String> REVIEWED_RAW = Set.of(
            "x", "i", "rm", "key", "true", "colour", "roleName$", "max_numerator", "includeNonPositiveResultsCheck",
            // The As Of Date box starts empty.
            "\"\"",
            "request.getContextPath()",
            "request.getScheme()+\"://\"+request.getServerName()+\":\"+request.getServerPort()+request.getContextPath()+\"/\"",
            "re.getNumeratorCount()", "re.getDenominatorCount()",
            // Set by RunClinicalReport2Action with Integer.toString.
            "request.getAttribute(\"numerator\")", "request.getAttribute(\"denominator\")",
            "request.getAttribute(\"percentage\")",
            // Keys from the shipped numerator/denominator definitions, used in script variable names.
            "repValues[i]", "repNumValues[i]",
            "listName.equals(defaultDenominatorPatientSet)?\"checked=\\\"checked\\\"\":\"\"",
            "(h.get(\"providerNo\").equals(defaultDenominatorProviderNo)?\"selected\":\"\")");

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"numer_val[0]", "numer_startDate[0]", "numer_endDate[0]",
            "numer_val[i]", "numer_startDate[i]", "numer_endDate[i]"})
    @DisplayName("should encode each posted criterion it puts back into its input box")
    void shouldEncodePostedCriterion_whenEchoedIntoItsInputValue(String field) throws Exception {
        String jsp = read();

        assertThat(jsp).contains("value=\"<carlos:encode value='<%= String.valueOf(" + field + ") %>' context=\"htmlAttribute\"/>\"");
        assertThat(jsp).doesNotContain("value=\"<%=" + field + "%>\"");
    }

    @Test
    @DisplayName("should encode the result headings, cells, CSV box and stored names")
    void shouldEncodeResultsAndStoredNames_forTheirContext() throws Exception {
        String jsp = read();

        assertThat(jsp)
                // showfields is posted, and head() returns an unknown field as it came
                .contains("<th><carlos:encode value='<%= head(heading) %>'/>")
                .contains("<th><carlos:encode value='<%= replaceHeading(outputfields[i], forView, measurementTitles) %>'/>")
                .contains("<td><carlos:encode value='<%= String.valueOf(display(h.get(outputfield))) %>'/>")
                .contains("value=\"<carlos:encode value='<%= (String) request.getAttribute(\"csv\") %>' context=\"htmlAttribute\"/>\"")
                .contains("<carlos:encode value='<%= (String) request.getAttribute(\"name\") %>'/>")
                .contains("title=\"<carlos:encode value='<%= re.getName() %>' context=\"htmlAttribute\"/>\"")
                .contains("value=\"<carlos:encode value='<%= n.getId() %>' context=\"htmlAttribute\"/>\"")
                .contains("<carlos:encode value='<%= n.getNumeratorName() %>'/>")
                .contains("<carlos:encode value='<%= d.getDenominatorName() %>'/>")
                .contains("<carlos:encode value='<%= h.get(\"lastName\") %>'/>")
                .contains("value=\"<carlos:encode value='<%= listName %>' context=\"htmlAttribute\"/>\"");
    }

    @Test
    @DisplayName("should print raw only values the server builds itself")
    void shouldPrintOnlyReviewedServerValues_withoutEncoding() throws Exception {
        String rest = ENCODE_TAG.matcher(read()).replaceAll("");
        List<String> unreviewed = new ArrayList<>();
        Matcher m = RAW_OUTPUT.matcher(rest);
        while (m.find()) {
            String expr = m.group(1).replaceAll("\\s+", "");
            boolean flag = expr.startsWith("sel(") || expr.startsWith("dchecked(");
            boolean encoded = expr.startsWith("SafeEncode.");
            if (!flag && !encoded && !REVIEWED_RAW.contains(expr)) {
                unreviewed.add(expr);
            }
        }

        assertThat(unreviewed).as("raw <%%= %%> output that is neither encoded nor reviewed").isEmpty();
    }

    private static String read() throws Exception {
        return Files.readString(JSP, StandardCharsets.UTF_8);
    }
}
