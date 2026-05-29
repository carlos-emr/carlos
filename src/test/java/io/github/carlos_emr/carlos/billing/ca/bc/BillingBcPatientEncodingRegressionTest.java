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
package io.github.carlos_emr.carlos.billing.ca.bc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BC billing patient encoding")
@Tag("unit")
@Tag("billing")
class BillingBcPatientEncodingRegressionTest {
    private static final List<String> BILLING_CORRECTION_REVIEW_PATIENT_FIELDS = List.of(
            "_p0_10", // patient name
            "_p0_2", // health number
            "_p0_15", // sex
            "_p0_6", // date of birth
            "_p0_11", // address
            "_p0_13", // city
            "_p0_12", // province
            "_p0_14"); // postal code
    private static final String ENCODE_TAG_PREFIX = "<carlos:encode\\s+value\\s*=\\s*(['\"])<%=\\s*";
    private static final String ENCODE_TAG_SUFFIX = "\\s*%>\\1\\s+context\\s*=\\s*(['\"])html\\2\\s*/\\s*>";

    @Test
    void shouldEncodeDemographicFields_inBillingCorrectionReviewJsp() throws Exception {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/billingCorrectionReview.jsp"));

        assertThat(jsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>");

        for (String patientField : BILLING_CORRECTION_REVIEW_PATIENT_FIELDS) {
            assertUsesHtmlEncodingForScriptlet(jsp, patientField);
            assertThat(jsp).doesNotContainPattern("<%=\\s*" + patientField + "\\s*%>");
        }
    }

    @Test
    void shouldEncodeDemographicFields_inBillReceiptJsp() throws Exception {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/billReceipt.jsp"));

        assertUsesHtmlEncodingForScriptlet(jsp, "demo\\.getProvince\\(\\)");
        assertUsesHtmlEncodingForScriptlet(jsp, "demo\\.getSex\\(\\)");
        assertUsesHtmlEncodingForScriptlet(jsp, "DemographicData\\.getDob\\(demo,\\s*\"-\"\\)");

        assertThat(jsp)
                .doesNotContainPattern("<%=\\s*demo\\.getProvince\\(\\)\\s*%>")
                .doesNotContainPattern("<%=\\s*demo\\.getSex\\(\\)\\s*%>")
                .doesNotContainPattern("<%=\\s*DemographicData\\.getDob\\(demo,\\s*\"-\"\\)\\s*%>");
    }

    private void assertUsesHtmlEncodingForScriptlet(String jsp, String scriptletExpressionPattern) {
        assertThat(jsp).containsPattern(ENCODE_TAG_PREFIX + scriptletExpressionPattern + ENCODE_TAG_SUFFIX);
    }
}
