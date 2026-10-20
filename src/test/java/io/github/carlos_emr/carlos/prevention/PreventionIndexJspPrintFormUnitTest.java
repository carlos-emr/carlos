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
package io.github.carlos_emr.carlos.prevention;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins the Preventions page's print form contract. The behaviour itself is driven in a browser by
 * {@code scripts/prevention-print-playwright-checks.js}; these source checks fail fast in CI when
 * an edit brings back one of the shapes behind the alpha report that Enable Print did not work.
 */
@Tag("unit")
@Tag("fast")
@Tag("prevention")
@DisplayName("prevention/index.jsp print form")
class PreventionIndexJspPrintFormUnitTest {

    private static final Path INDEX_JSP = Path.of("src/main/webapp/WEB-INF/jsp/prevention/index.jsp");
    private static String jsp;

    @BeforeAll
    static void readPage() throws Exception {
        jsp = Files.readString(INDEX_JSP);
    }

    @Test
    @DisplayName("should post only the selection, never the chart's comments, reasons, vaccines or lots")
    void shouldPostSelectionOnly_withoutChartFields() {
        assertThat(jsp)
                .doesNotContain("preventProcedure")
                .doesNotContain("name=\"preventionHeader")
                .doesNotContain("name=\"hin\"")
                .doesNotContain("name=\"mrp\"")
                .doesNotContain("name=\"lotNumberToAdd2\"")
                .contains("name=\"demographicNo\"")
                .contains("name=\"immunizationOnly\"");
    }

    @Test
    @DisplayName("should value every print box by its prevention type name")
    void shouldValuePrintBoxes_byTypeName() {
        Matcher boxes = Pattern.compile("name=\"printHP\"\\s+value=\"(.*?)\" checked").matcher(jsp);
        int count = 0;
        while (boxes.find()) {
            count++;
            assertThat(boxes.group(1)).isEqualTo("<carlos:encode value='<%= prevName %>' context=\"htmlAttribute\"/>");
        }
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("should refuse implicit submission so Enter in a vaccine box cannot print")
    void shouldRefuseImplicitSubmission_ofPrintForm() {
        // Each search box is labelled, so its Enter handling is reachable by assistive technology too.
        assertThat(jsp).contains("<label for=\"lotNumberToAdd2\" class=\"visually-hidden\">",
                "<label for=\"immunization\" class=\"visually-hidden\">");
        assertThat(jsp).contains("<form name=\"printFrm\" method=\"post\" onsubmit=\"return false;\"");
        assertThat(jsp.split("if \\(e\\.key === 'Enter'\\) \\{", -1)).hasSize(3);
        // An IME's Enter confirms composition and must not pick a suggestion either.
        assertThat(jsp.split("if \\(e\\.isComposing \\|\\| e\\.keyCode === 229\\) return;", -1)).hasSize(3);
        assertThat(jsp).doesNotContain("e.key === 'Enter' && activeIdx >= 0");
    }

    @Test
    @DisplayName("should check the selection before either print button sends the form")
    void shouldCheckSelection_beforeEitherPrint() {
        assertThat(jsp)
                .contains("function printImmOnly() {\n                submitPrint(true);\n            }")
                .contains("submitPrint(false);")
                .containsOnlyOnce("form.submit();")
                .doesNotContain("document.printFrm.submit()");
        int guard = jsp.indexOf("function submitPrint(immunizationsOnly) {");
        assertThat(jsp.indexOf("if (!onPrint()) {", guard)).isBetween(guard, jsp.indexOf("form.submit();"));
    }

    @Test
    @DisplayName("should show the print error banner whether or not there are recommendations")
    void shouldShowPrintError_outsideRecommendations() {
        int banner = jsp.indexOf("<% if (printError) { %>");
        assertThat(banner).isPositive()
                .isLessThan(jsp.indexOf("if (warnings.size() > 0 || recomendations.size() > 0 || dsProblems)"));
        assertThat(jsp).containsOnlyOnce("An error occurred while trying to print");
    }
}
