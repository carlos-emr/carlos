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
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The BC Bill Status page's status choice (issue #4868). The Master Record's Invoice List opens
 * the page fixed to one patient ({@code filterPatient=true}), and the page used to force the status
 * to All there, so the status radios could never filter the patient's bills. The browser check
 * {@code billing-bc-bill-status-filters} drives the same page live.
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC Bill Status JSP status choice")
class BillingBcBillStatusJspRegressionTest {

    private static final Path BILL_STATUS_JSP = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/billStatus.jsp");
    private static final String STATUS_READ = "String billTypes = request.getParameter(\"billTypes\");";

    /** The scriptlet that settles which status the page searches and ticks, up to its closing tag. */
    private static String statusChoice() throws IOException {
        String page = Files.readString(BILL_STATUS_JSP, StandardCharsets.UTF_8);
        int start = page.indexOf(STATUS_READ);
        assertThat(start).as("the page reads the billTypes parameter").isNotNegative();
        int end = page.indexOf("%>", start);
        assertThat(end).as("the status scriptlet closes").isGreaterThan(start);
        assertThat(page.indexOf(STATUS_READ, end)).as("the page reads billTypes once").isNegative();
        return page.substring(start, end);
    }

    /**
     * In the patient view, only a missing or blank status means All: a status picked from the
     * radios reaches the search and stays ticked.
     */
    @Test
    @DisplayName("should keep All for a missing or blank status in the patient view, and use a chosen one")
    void shouldUseChosenStatus_whenPatientViewHasOne() throws IOException {
        String choice = statusChoice();

        assertThat(choice).containsPattern(Pattern.compile(
                "if \\(\"true\"\\.equals\\(readonly\\)\\) \\{"
                        + "(?:\\s*//[^\\n]*)*"
                        + "\\s*if \\(billTypes == null \\|\\| billTypes\\.isBlank\\(\\)\\) \\{"
                        + "\\s*billTypes = \"%\";"
                        + "\\s*\\}"));
        assertThat(Pattern.compile("billTypes = \"%\";").matcher(choice).results().count())
                .as("All is set only for a missing or blank status")
                .isEqualTo(1);
    }

    /**
     * The clinic-wide page still opens on Rejected when no status is given, and that default is
     * kept out of the patient view, which opens on All.
     */
    @Test
    @DisplayName("should default a missing status to Rejected on the clinic-wide page only")
    void shouldDefaultToRejected_forClinicWidePageOnly() throws IOException {
        String choice = statusChoice();

        assertThat(choice).containsPattern(Pattern.compile(
                "\\}\\s*else if \\(billTypes == null\\) \\{\\s*billTypes = MSPReconcile\\.REJECTED;\\s*\\}"));
        assertThat(Pattern.compile("billTypes = MSPReconcile\\.REJECTED;").matcher(choice).results().count())
                .as("Rejected is the default in one place")
                .isEqualTo(1);
        assertThat(choice.indexOf("if (\"true\".equals(readonly)) {"))
                .as("the patient view is settled before the clinic-wide default")
                .isLessThan(choice.indexOf("billTypes = MSPReconcile.REJECTED;"));
    }
}
