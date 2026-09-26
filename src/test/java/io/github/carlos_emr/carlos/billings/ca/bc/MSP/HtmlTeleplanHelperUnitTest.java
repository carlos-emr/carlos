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
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import io.github.carlos_emr.carlos.utility.SafeEncode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HtmlTeleplanHelper")
@Tag("unit")
class HtmlTeleplanHelperUnitTest {

    @Test
    void shouldEncodeBillingMasterNumber_whenRenderingAdjustmentLink() {
        String html = HtmlTeleplanHelper.htmlLine(
                "1&2=3#4",
                "INV-1",
                "<b>Patient</b>",
                "123&456",
                "2026-06-12",
                "A001A",
                "10.00",
                "250",
                "",
                "");

        assertThat(html).contains("adjustBill.jsp?billingmaster_no=1%262%3D3%234");
        assertThat(html).contains(SafeEncode.forHtmlContent("<b>Patient</b>"));
        assertThat(html).contains(SafeEncode.forHtmlContent("123&456"));
        assertThat(html).doesNotContain("adjustBill.jsp?billingmaster_no=1&2=3#4");
        assertThat(html).doesNotContain("<b>Patient</b>");
        assertThat(html).doesNotContain(">123&456<");
    }

    @Test
    void shouldEncodeValidationRows_whenRenderingTeleplanErrors() {
        CheckBillingData checkData = new CheckBillingData();

        String errorHtml = checkData.printErrorMsg("1&2=3#4", "bad <message> & more");
        String warningHtml = checkData.printWarningMsg("warn <b>");

        assertThat(errorHtml)
                .contains(SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent("1&2=3#4")))
                .contains(SafeEncode.forHtmlContent("bad <message> & more"))
                .doesNotContain("billingmaster_no=1&2=3#4")
                .doesNotContain("bad <message>");
        assertThat(warningHtml)
                .contains(SafeEncode.forHtmlContent("warn <b>"))
                .doesNotContain("warn <b>");

        String headerHtml = HtmlTeleplanHelper.htmlHeaderGen(errorHtml + warningHtml);
        assertThat(headerHtml)
                .contains(errorHtml)
                .contains(warningHtml)
                .doesNotContain("<td colspan='11' class='bodytext'><tr");
    }

    @Test
    void shouldEncodeWcbRowAndLink_whenRenderingWcbClaim() {
        String html = HtmlTeleplanHelper.wcbHtmlLine("1');alert(1);//", "INV", "<img src=x>", "<b>1</b>",
                "20260612", "19950", "10.00", "250", "", "");

        assertThat(html)
                .contains("openBrWindow('billingTeleplanCorrectionWCB.jsp?billing_no=")
                .contains(SafeEncode.forHtmlContent("<img src=x>"))
                .doesNotContain("');alert(1)")
                .doesNotContain("<img")
                .doesNotContain("<b>");
    }

    @Test
    void shouldReturnEmpty_whenErrorRowHasNoMessage() {
        assertThat(HtmlTeleplanHelper.adjustBillErrorRow("42", "")).isEmpty();
        assertThat(HtmlTeleplanHelper.wcbCorrectionErrorRow("42", null)).isEmpty();
    }

    @Test
    void shouldEncodeMessageAndId_whenRenderingCorrectionErrorRows() {
        String adjust = HtmlTeleplanHelper.adjustBillErrorRow("1\"onmouseover=\"x", ": bad <b>");
        String wcb = HtmlTeleplanHelper.wcbCorrectionErrorRow("7", ": bad <b>");

        assertThat(adjust)
                .contains("adjustBill.jsp?billingmaster_no=")
                .contains(SafeEncode.forHtmlContent(": bad <b>"))
                .doesNotContain("\"onmouseover")
                .doesNotContain("<b>");
        assertThat(wcb)
                .contains("billingTeleplanCorrectionWCB.jsp?billing_no=0000007")
                .doesNotContain("<b>");
    }

    @Test
    void shouldEncodeProviderAndCount_whenRenderingFooter() {
        String footer = HtmlTeleplanHelper.htmlFooter("<b>P</b>", "<i>3</i>", java.math.BigDecimal.TEN);

        assertThat(footer)
                .contains(SafeEncode.forHtmlContent("<b>P</b>"))
                .contains(SafeEncode.forHtmlContent("<i>3</i>"))
                .contains("TOTAL: 10")
                .doesNotContain("<b>")
                .doesNotContain("<i>");
        assertThat(HtmlTeleplanHelper.htmlFooter("P", 3, java.math.BigDecimal.ONE)).contains("Billing No: P: 3 RECORDS");
    }
}
