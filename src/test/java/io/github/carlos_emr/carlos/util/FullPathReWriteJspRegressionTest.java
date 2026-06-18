/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("security")
@DisplayName("FullPathReWrite JSP regressions")
class FullPathReWriteJspRegressionTest {

    private static final Path ADJUST_BILL_JSP = Path.of(
            "src/main/webapp/WEB-INF/jsp/billing/CA/BC/adjustBill.jsp");
    private static final Path BILLING_BC_JSP = Path.of(
            "src/main/webapp/WEB-INF/jsp/billing/CA/BC/billingBC.jsp");
    private static final Path BILLING_SVC_TRAY_ASSOC_JSP = Path.of(
            "src/main/webapp/WEB-INF/jsp/billing/CA/BC/billingSVCTrayAssoc.jsp");
    private static final Path DXCODE_SVCCODE_ASSOC_JSP = Path.of(
            "src/main/webapp/WEB-INF/jsp/billing/CA/BC/dxcode_svccode_assoc.jsp");
    private static final Path FORMWCB_JSP = Path.of(
            "src/main/webapp/WEB-INF/jsp/billing/CA/BC/formwcb.jsp");

    @Test
    @DisplayName("should encode runtime query values in rewrite popup URLs")
    void shouldEncodeRuntimeQueryValues_inRewritePopupUrls() throws IOException {
        String adjustBill = read(ADJUST_BILL_JSP);
        String billingBC = read(BILLING_BC_JSP);
        String billingSvcTrayAssoc = read(BILLING_SVC_TRAY_ASSOC_JSP);
        String dxcodeSvcCodeAssoc = read(DXCODE_SVCCODE_ASSOC_JSP);
        String formwcb = read(FORMWCB_JSP);

        assertThat(adjustBill)
                .contains("var t0 = encodeURIComponent(document.forms['reprocessBilling'].elements[d].value)")
                .contains("var t0 = encodeURIComponent(document.forms[form].elements[code].value)")
                .contains("var t0 = encodeURIComponent(document.forms['reprocessBilling'].service_code.value)")
                .contains("encodeURIComponent(form)")
                .contains("encodeURIComponent(field)")
                .contains("encodeURIComponent(str)")
                .contains("encodeURIComponent(serviceDate)")
                .contains("encodeURIComponent(providerNo)")
                .doesNotContain("&formElement=' + d")
                .doesNotContain("&formName=' + form")
                .doesNotContain("&formElementPrice=' + price")
                .doesNotContain("&searchStr=' + str")
                .doesNotContain("&serviceDate=' + serviceDate")
                .doesNotContain("&providerNo=' + providerNo");

        assertThat(billingBC)
                .contains("var t0 = encodeURIComponent(document.BillingCreateBillingForm.xml_other1.value)")
                .contains("var t0 = encodeURIComponent(document.BillingCreateBillingForm.elements[d].value)")
                .contains("var t0 = encodeURIComponent(document.serviceform.xml_referral1.value)")
                .contains("encodeURIComponent(d)")
                .doesNotContain("escape(document.BillingCreateBillingForm")
                .doesNotContain("escape(document.serviceform")
                .doesNotContain("&formElement=' + d");

        assertThat(billingSvcTrayAssoc)
                .contains("encodeURIComponent(form)")
                .contains("encodeURIComponent(field)")
                .contains("encodeURIComponent(str)")
                .doesNotContain("+ '?form=' + form + '&field=' + field + '&searchStr=' + str");

        assertThat(dxcodeSvcCodeAssoc)
                .contains("var t0 = encodeURIComponent(document.forms[0].xml_other1.value)")
                .doesNotContain("escape(document.forms[0].xml_other1.value)");

        assertThat(formwcb)
                .contains("context=\"javaScriptBlock\"")
                .contains("encodeURIComponent(form)")
                .contains("encodeURIComponent(field)")
                .contains("encodeURIComponent(str)")
                .doesNotContain("&searchStr=' + str");
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
