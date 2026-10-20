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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Ontario Billing Report Center's Flu fragment lists flu-shot claims as one list with a count and a
 * total, and each row opens its claim read-only (#5093).
 *
 * @since 2026-10-20
 */
@DisplayName("billingReport_flu.jspf (Ontario)")
@Tag("unit")
@Tag("billing")
class BillingReportFluFragmentJspRegressionTest {

    private static final int MAX_PARENT_SEARCH_DEPTH = 8;
    private static final Path FRAGMENT = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "billing", "CA", "ON",
            "billingReport_flu.jspf");

    @Test
    @DisplayName("should list the flu-shot claims as one list with a count and a total")
    void shouldListFluClaimsAsOneList_withACountAndATotal() throws Exception {
        String jsp = Files.readString(resolveProjectPath(FRAGMENT));

        assertThat(jsp)
                .contains("<fmt:setBundle basename=\"oscarResources\"/>")
                .contains("items=\"${billingReportFragmentModel.fluRows}\"")
                .contains("<fmt:message key=\"billing.flu.count\"><fmt:param value=\"${billingReportFragmentModel.fluCount}\"/></fmt:message>")
                .contains("${billingReportFragmentModel.fluTotal}")
                .doesNotContain("<TH align")
                .doesNotContain("fluWalkinRows")
                .doesNotContain("fluClinicRows")
                .doesNotContain("WALK-IN");
    }

    @Test
    @DisplayName("should open each claim read-only")
    void shouldOpenEachClaimReadOnly_fromItsRow() throws Exception {
        String jsp = Files.readString(resolveProjectPath(FRAGMENT));

        assertThat(jsp)
                .contains("/billing/CA/ON/ViewBillingONDisplay?billing_no=<carlos:encode value='${row.billingId}' context='uriComponent'/>")
                .doesNotContain("ViewBillingOB2")
                .doesNotContain("BillingDeleteNoAppt");
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth <= MAX_PARENT_SEARCH_DEPTH && current != null; depth++) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate project file: " + relativePath);
    }
}
