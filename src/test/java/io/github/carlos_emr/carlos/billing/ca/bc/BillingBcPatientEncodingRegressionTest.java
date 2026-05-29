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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BC billing patient encoding")
@Tag("unit")
@Tag("billing")
class BillingBcPatientEncodingRegressionTest {

    @Test
    void shouldEncodePatientFields_inBillingCorrectionReviewJsp() throws Exception {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/billingCorrectionReview.jsp"));

        assertThat(jsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("<carlos:encode value='<%= _p0_10 %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= _p0_2 %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= _p0_15 %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= _p0_6 %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= _p0_11 %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= _p0_13 %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= _p0_12 %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= _p0_14 %>' context=\"html\"/>")
                .doesNotContain("<%=_p0_10%>")
                .doesNotContain("<%=_p0_2%>")
                .doesNotContain("<%=_p0_15%>")
                .doesNotContain("<%=_p0_6%>")
                .doesNotContain("<%=_p0_11%>")
                .doesNotContain("<%=_p0_13%>")
                .doesNotContain("<%=_p0_12%>")
                .doesNotContain("<%=_p0_14%>");
    }

    @Test
    void shouldEncodePatientFields_inBillReceiptJsp() throws Exception {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/billReceipt.jsp"));

        assertThat(jsp)
                .contains("<carlos:encode value='<%= demo.getProvince() %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= demo.getSex() %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= DemographicData.getDob(demo, \"-\") %>' context=\"html\"/>")
                .doesNotContain("<%=demo.getProvince()%>")
                .doesNotContain("<%=demo.getSex()%>")
                .doesNotContain("<%=DemographicData.getDob(demo, \"-\")%>");
    }
}
