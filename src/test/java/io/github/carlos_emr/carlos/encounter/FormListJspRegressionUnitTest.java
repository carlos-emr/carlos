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
package io.github.carlos_emr.carlos.encounter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks how the chart Forms list ({@code encounter/formlist.jsp}) applies its page window
 * (issue #5684). {@code FormListPageCalculatorUnitTest} covers the window arithmetic; this test
 * pins the page's use of it alongside the installed browser check
 * {@code echart-forms-list-paging}: rows are counted across every form type, a row skipped for
 * being off the page still advances the count and the "(current)" highlight, and one
 * Previous/Next row follows the table instead of one per form type.
 *
 * @since 2026-11-05
 */
@DisplayName("Chart Forms list JSP paging regressions")
@Tag("unit")
@Tag("encounter")
class FormListJspRegressionUnitTest {

    private static final Path FORM_LIST_JSP = Path.of("src/main/webapp/WEB-INF/jsp/encounter/formlist.jsp");

    private String jsp;

    @BeforeEach
    void readJsp() throws IOException {
        jsp = Files.readString(FORM_LIST_JSP, StandardCharsets.UTF_8).replaceAll("\\s+", " ");
    }

    @Test
    @DisplayName("should page over the whole list's row count with the request's limits")
    void shouldCalculatePageFromRequestLimits_overEveryFormType() {
        // The total is summed over every form type before any row is rendered; parsing stays in
        // the calculator, so a nonnumeric limit no longer fails the page.
        Matcher calculation = matching("FormListPageCalculator\\.calculate\\(\\s*"
                + "request\\.getParameter\\(\"limit1\"\\),\\s*"
                + "request\\.getParameter\\(\"limit2\"\\),\\s*(\\w+)\\s*\\)");
        Matcher total = matching("\\b" + Pattern.quote(calculation.group(1))
                + "\\s*\\+=\\s*\\w+\\.length\\s*;");
        assertThat(total.end()).isLessThan(calculation.start());
        assertThat(jsp).doesNotContainPattern(
                "Integer\\.parseInt\\(\\s*request\\.getParameter\\(\"limit[12]\"\\)");
    }

    @Test
    @DisplayName("should advance the row count for rows skipped as off the page")
    void shouldAdvanceRowCount_forRowsOffThePage() {
        // The count lives in the loop header, so the `continue` that skips an off-page row
        // still advances it, and is never reset between form types.
        Matcher loop = matching("for \\(int (\\w+) = 0; \\1 < \\w+\\.length; \\1\\+\\+, (\\w+)\\+\\+\\) \\{");
        String rowIndex = loop.group(2);
        matching("if \\(!\\w+\\.contains\\(" + Pattern.quote(rowIndex) + "\\)\\) \\{ continue;");
        String initialization = "int " + rowIndex + " = 0;";
        assertThat(occurrences(initialization)).isEqualTo(1);
        Matcher types = matching("for \\(int (\\w+) = 0; \\1 < \\w+\\.length; \\1\\+\\+\\) \\{");
        assertThat(jsp.indexOf(initialization)).isLessThan(types.start());
    }

    @Test
    @DisplayName("should work out the current highlight before skipping an off-page row")
    void shouldTrackCurrentHighlight_beforeSkippingOffPageRows() {
        // The highlight of a row depends on the row before it in its form type, which may sit on
        // the previous page; tracking it after the skip would mislabel a page's first row.
        Matcher highlight = matching("boolean \\w+ = !Objects.equals\\((\\w+), (\\w+)\\);");
        Matcher track = matching("\\b" + Pattern.quote(highlight.group(1)) + " = "
                + Pattern.quote(highlight.group(2)) + ";");
        Matcher skip = matching("if \\(!\\w+\\.contains\\(\\w+\\)\\) \\{");
        assertThat(track.start()).isGreaterThan(highlight.start());
        assertThat(skip.start()).isGreaterThan(track.start());
    }

    @Test
    @DisplayName("should render one encoded Previous/Next row after every form type")
    void shouldRenderOnePagingRow_afterEveryFormType() {
        Matcher skip = matching("if \\(!\\w+\\.contains\\(\\w+\\)\\) \\{");
        Matcher paging = matching("if \\((\\w+)\\.hasPrevious\\(\\) \\|\\| \\1\\.hasNext\\(\\)\\) \\{");
        assertThat(paging.start()).isGreaterThan(skip.start());
        assertThat(occurrences("encounter.formlist.formLastpage")).isEqualTo(1);
        assertThat(occurrences("encounter.formlist.formNextPage")).isEqualTo(1);
        assertThat(Pattern.compile("<a href=\"<carlos:encode value='<%= \\w+ %>' context=\"htmlAttribute\"/>\">")
                .matcher(jsp).results().count()).isEqualTo(2L);
        assertThat(jsp)
                .contains("+ SafeEncode.forUriComponent(")
                .contains("\"&provider_no=\" + SafeEncode.forUriComponent(")
                .containsPattern("\"&limit1=\" \\+ \\w+\\.previousOffset\\(\\)")
                .containsPattern("\"&limit1=\" \\+ \\w+\\.nextOffset\\(\\)");
    }

    private Matcher matching(String expression) {
        Matcher matcher = Pattern.compile(expression).matcher(jsp);
        assertThat(matcher.find()).as("JSP structural guard: %s", expression).isTrue();
        return matcher;
    }

    private int occurrences(String needle) {
        int count = 0;
        for (int at = jsp.indexOf(needle); at >= 0; at = jsp.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}
