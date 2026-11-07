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
        jsp = Files.readString(FORM_LIST_JSP, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should page over the whole list's row count with the request's limits")
    void shouldCalculatePageFromRequestLimits_overEveryFormType() {
        // The total is summed over every form type before any row is rendered; parsing stays in
        // the calculator, so a nonnumeric limit no longer fails the page.
        assertThat(jsp)
                .contains("totalFormRows += pforms.length;")
                .contains("FormListPageCalculator.calculate(\n"
                        + "            request.getParameter(\"limit1\"), request.getParameter(\"limit2\"), totalFormRows);")
                .doesNotContain("Integer.parseInt")
                .doesNotContain("strLimit");
        assertThat(jsp.indexOf("totalFormRows += pforms.length;"))
                .isLessThan(jsp.indexOf("FormListPageCalculator.calculate("));
    }

    @Test
    @DisplayName("should advance the row count for rows skipped as off the page")
    void shouldAdvanceRowCount_forRowsOffThePage() {
        // The count lives in the loop header, so the `continue` that skips an off-page row
        // still advances it, and is never reset between form types.
        assertThat(jsp)
                .contains("for (int i = 0; i < pforms.length; i++, formRowIndex++) {")
                .contains("if (!formListPage.contains(formRowIndex)) {\n                            continue;");
        assertThat(occurrences("int formRowIndex = 0;")).isEqualTo(1);
        assertThat(jsp.indexOf("int formRowIndex = 0;"))
                .isLessThan(jsp.indexOf("for (int j = 0; j < forms.length; j++) {"));
    }

    @Test
    @DisplayName("should work out the current highlight before skipping an off-page row")
    void shouldTrackCurrentHighlight_beforeSkippingOffPageRows() {
        // The highlight of a row depends on the row before it in its form type, which may sit on
        // the previous page; tracking it after the skip would mislabel a page's first row.
        int highlight = jsp.indexOf("boolean yellow = !Objects.equals(current, created);");
        int track = jsp.indexOf("current = created;");
        int skip = jsp.indexOf("if (!formListPage.contains(formRowIndex)) {");

        assertThat(highlight).isPositive();
        assertThat(track).isGreaterThan(highlight);
        assertThat(skip).isGreaterThan(track);
    }

    @Test
    @DisplayName("should render one encoded Previous/Next row after every form type")
    void shouldRenderOnePagingRow_afterEveryFormType() {
        int lastRowSkip = jsp.lastIndexOf("formListPage.contains(formRowIndex)");
        int pagingRow = jsp.indexOf("if (formListPage.hasPrevious() || formListPage.hasNext()) {");

        assertThat(pagingRow).isGreaterThan(lastRowSkip);
        assertThat(occurrences("encounter.formlist.formLastpage")).isEqualTo(1);
        assertThat(occurrences("encounter.formlist.formNextPage")).isEqualTo(1);
        assertThat(jsp)
                .contains("<a href=\"<carlos:encode value='<%= formListPreviousUrl %>' context=\"htmlAttribute\"/>\">")
                .contains("<a href=\"<carlos:encode value='<%= formListNextUrl %>' context=\"htmlAttribute\"/>\">")
                .contains("+ SafeEncode.forUriComponent(demoNo)")
                .contains("\"&provider_no=\" + SafeEncode.forUriComponent(provNo)")
                .contains("\"&limit1=\" + formListPage.previousOffset()")
                .contains("\"&limit1=\" + formListPage.nextOffset()");
    }

    private int occurrences(String needle) {
        int count = 0;
        for (int at = jsp.indexOf(needle); at >= 0; at = jsp.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}
