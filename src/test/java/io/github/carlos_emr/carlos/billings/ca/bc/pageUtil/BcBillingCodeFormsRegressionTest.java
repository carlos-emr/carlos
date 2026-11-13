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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BC billing code pages (Add Code, Add Private Code, a code's Edit) that the container renders: the browser checks
 * behind them only run once the pages are reachable, so these pin the markup their scripts rely on.
 *
 * @since 2026-10-17
 */
@DisplayName("BC billing code forms")
@Tag("unit")
@Tag("billing")
class BcBillingCodeFormsRegressionTest {

    private static final Path BC_JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "billing", "CA", "BC");

    private static String read(String page) throws IOException {
        return Files.readString(BC_JSP.resolve(page), StandardCharsets.UTF_8);
    }

    @ParameterizedTest
    @ValueSource(strings = {"billingAddCode.jsp", "billingAddPrivateCode.jsp"})
    @DisplayName("should name the Add form the way its price check looks it up")
    void shouldNameAddForm_forItsPriceCheck(String page) throws IOException {
        String jsp = read(page);

        // checkUnits() reads document.BillingAddCodeForm; without the name it throws and the form posts unchecked.
        assertThat(jsp)
                .contains("document.BillingAddCodeForm.value.value")
                .contains("<form name=\"BillingAddCodeForm\" action=\"${pageContext.request.contextPath}/billing/CA/BC/billingAddCode\" method=\"post\" onsubmit=\"return checkUnits();\">");
    }

    @ParameterizedTest
    @ValueSource(strings = {"billingAddCode.jsp", "billingCodeAdjust.jsp"})
    @DisplayName("should not call a setValues the page never defines when it opens")
    void shouldNotCallUndefinedSetValues_whenPageOpens(String page) throws IOException {
        String jsp = read(page);

        // Only billingEditCode.jsp defines setValues; these two raised a script error on every open.
        assertThat(jsp).doesNotContain("setValues");
    }

    @Test
    @DisplayName("should take Add Code's service code in a text box")
    void shouldTakeServiceCode_inTextBox() throws IOException {
        String jsp = read("billingAddCode.jsp");

        // As a checkbox the field could only ever send "on", never the code typed for it.
        assertThat(jsp)
                .contains("<input type=\"text\" name=\"code\" maxlength=\"5\" />")
                .doesNotContain("<input type=\"checkbox\" name=\"code\"");
    }

    @Test
    @DisplayName("should show and post a missing fee row date as empty on a code's Edit page")
    void shouldShowMissingDate_asEmptyOnEditPage() throws IOException {
        String jsp = read("billingEditCode.jsp");

        // String.valueOf(null) put the word "null" in the date box, which the save then stored as no date.
        assertThat(jsp)
                .contains("String serviceDate = ConversionUtils.toDateString(bs.getBillingserviceDate());")
                .contains("String terminationDate = ConversionUtils.toDateString(bs.getTerminationDate());")
                .doesNotContain("String.valueOf(bs.getBillingserviceDate())")
                .doesNotContain("String.valueOf(bs.getTerminationDate())");
    }

    @Test
    @DisplayName("should show the saved fee row dates whatever shape the save answers them in")
    void shouldShowSavedDates_whateverTheirShape() throws IOException {
        String jsp = read("billingEditCode.jsp");

        // The save answers dates as epoch milliseconds, and an open-ended row's end date can be null; reading
        // .time off either left the row stuck in edit mode or showed NaN.
        assertThat(jsp)
                .contains("function shownDate(value) {")
                .contains("if (value === null || value === undefined || value === '') {")
                .contains("var d = new Date(typeof value === 'object' ? value.time : value);")
                // In UTC, so a browser west of a UTC server does not show the day before.
                .contains("d.getUTCFullYear() + '-' + String(d.getUTCMonth() + 1).padStart(2, '0') + '-' + String(d.getUTCDate()).padStart(2, '0')")
                .contains("textContent = shownDate(data.billingserviceDate);")
                .contains("textContent = shownDate(data.terminationDate);")
                .doesNotContain("data.billingserviceDate.time")
                .doesNotContain("data.terminationDate.time");
    }
}
