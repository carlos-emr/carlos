/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BillingONNewReportViewModelAssembler")
@Tag("unit")
@Tag("billing")
class BillingONNewReportViewModelAssemblerUnitTest {

    @Test
    void shouldEncodePlainReportCellsBeforeRawJspRendering() {
        String cell = BillingONNewReportViewModelAssembler.htmlCell("<img src=x onerror=alert(1)>");

        assertThat(cell).isEqualTo("&lt;img src=x onerror=alert(1)&gt;");
    }

    @Test
    void shouldEncodeBillingNoLinkTextAndUrlParameter() {
        String html = BillingONNewReportViewModelAssembler.buildBillingNoLinkWithTitle(
                "/ctx",
                "123\" onclick=\"alert(1)",
                "Bill <OHIP>");

        assertThat(html).contains("billing_no=123%22+onclick%3D%22alert%281%29");
        assertThat(html).contains(">123&#34; onclick=&#34;alert(1)</a>");
        assertThat(html).contains("title='Bill &lt;OHIP>");
        assertThat(html).doesNotContain("123\" onclick=\"alert(1)");
    }
}
