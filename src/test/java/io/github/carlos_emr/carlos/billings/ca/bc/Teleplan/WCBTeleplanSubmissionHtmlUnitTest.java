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
package io.github.carlos_emr.carlos.billings.ca.bc.Teleplan;

import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.entities.WCB;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #3950: the Teleplan submission report (TeleplanFileWriter) renders WCB rows through
 * {@link WCBTeleplanSubmission}; worker names and claim values must be encoded there too.
 */
@DisplayName("WCBTeleplanSubmission report HTML encoding")
@Tag("unit")
@Tag("billing")
@Tag("security")
class WCBTeleplanSubmissionHtmlUnitTest {

    @Test
    void shouldEncodeWorkerNameAndCodes_whenRenderingWcbRow() {
        String html = new WCBTeleplanSubmission().getHtmlLine("42", "1001", "<script>alert(1)</script>,Jane",
                "<b>phn</b>", "20260612", "<i>19950</i>", "10.00", "250", "", "");

        assertThat(html)
                .contains(SafeEncode.forHtmlContent("<script>alert(1)</script>,Jane"))
                .contains(SafeEncode.forHtmlContent("<b>phn</b>"))
                .contains(SafeEncode.forHtmlContent("<i>19950</i>"))
                .contains("openBrWindow('adjustBill.jsp?billingmaster_no=0000042'")
                .doesNotContain("<script>")
                .doesNotContain("<b>")
                .doesNotContain("<i>");
    }

    @Test
    void shouldReturnLinkedErrorRow_whenDxCodeIsNotNumeric() {
        WCB wcb = new WCB();
        wcb.setW_wcbno("123");
        Billingmaster bm = new Billingmaster();
        bm.setBillingmasterNo(42);
        bm.setDxCode1("<x>");

        String html = new WCBTeleplanSubmission().validate(wcb, bm);

        assertThat(html)
                .startsWith("<tr bgcolor='red'>")
                .contains("adjustBill.jsp?billingmaster_no=0000042")
                .contains("ICD9 may only contain Numbers");
    }

    @Test
    void shouldReturnEmpty_whenWcbClaimIsValid() {
        WCB wcb = new WCB();
        wcb.setW_wcbno("123");
        Billingmaster bm = new Billingmaster();
        bm.setBillingmasterNo(42);
        bm.setDxCode1("250");

        assertThat(new WCBTeleplanSubmission().validate(wcb, bm)).isEmpty();
    }
}
