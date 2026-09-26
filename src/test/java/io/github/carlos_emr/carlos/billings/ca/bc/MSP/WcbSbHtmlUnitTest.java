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

import io.github.carlos_emr.carlos.billing.CA.BC.model.Wcb;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #3950: WCB rows and WCB validation rows in the BC billing simulation report must encode
 * worker/claim values and the {@code billing_no} placed in the correction-popup {@code onClick} URL.
 */
@DisplayName("WcbSb report HTML encoding")
@Tag("unit")
@Tag("billing")
@Tag("security")
class WcbSbHtmlUnitTest extends CarlosUnitTestBase {

    @BeforeEach
    void registerBillingmasterDao() {
        // getBillingMasterNo falls back to the invoice number when no billingmaster row exists.
        createAndRegisterMock(BillingmasterDAO.class);
    }

    private static Wcb wcb(String lastName, String firstName, String wcbNo, String icd9) {
        Wcb w = new Wcb();
        w.setBillingNo(77);
        w.setlName(lastName);
        w.setfName(firstName);
        w.setPhn("<b>9876543210</b>");
        w.setWcbNo(wcbNo);
        w.setIcd9(icd9);
        w.setFeeItem("19950");
        return w;
    }

    @Test
    void shouldEncodeWorkerName_whenRenderingWcbRow() {
        String html = new WcbSb(wcb("<script>alert(1)</script>", "Jane", "123", "250")).getHtmlLine();

        assertThat(html)
                .contains(SafeEncode.forHtmlContent("<script>alert(1)</script>,Jane"))
                .contains(SafeEncode.forHtmlContent("<b>9876543210</b>"))
                .contains("openBrWindow('billingTeleplanCorrectionWCB.jsp?billing_no=0000077'")
                .doesNotContain("<script>")
                .doesNotContain("<b>");
    }

    @Test
    void shouldEncodeBillingNumber_whenRenderingWcbRowLink() {
        WcbSb sb = new WcbSb(wcb("Doe", "Jane", "123", "250"));

        String html = sb.getHtmlLine("1');alert(1);//", "INV", "Doe,Jane", "9876543210", "20260612",
                "19950", "10.00", "250", "", "");

        assertThat(html)
                .contains("billingTeleplanCorrectionWCB.jsp?billing_no=")
                .doesNotContain("');alert(1)");
    }

    @Test
    void shouldReturnEncodedErrorRow_whenWcbDataIsInvalid() {
        String html = new WcbSb(wcb("Doe", "Jane", "not-a-number", "x")).validate();

        assertThat(html)
                .startsWith("<tr bgcolor='red'>")
                .contains("billingTeleplanCorrectionWCB.jsp?billing_no=0000077")
                .contains("ICD9 may only contain Numbers")
                .contains("WCB claim # may only contain Numbers");
    }

    @Test
    void shouldReturnEmpty_whenWcbDataIsValid() {
        assertThat(new WcbSb(wcb("Doe", "Jane", "123", "250")).validate()).isEmpty();
    }
}
