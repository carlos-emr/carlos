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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BillingOnNewReportViewModelAssembler")
@Tag("unit")
@Tag("billing")
class BillingOnNewReportViewModelAssemblerUnitTest {

    @Test
    void shouldEncodePlainReportCellsBeforeRawJspRendering() {
        String cell = BillingOnNewReportViewModelAssembler.htmlCell("<img src=x onerror=alert(1)>");

        assertThat(cell).isEqualTo("&lt;img src=x onerror=alert(1)&gt;");
    }

    @Test
    void shouldEncodeBillingNoLinkTextAndUrlParameter() {
        String html = BillingOnNewReportViewModelAssembler.buildBillingNoLinkWithTitle(
                "/ctx",
                "123\" onclick=\"alert(1)",
                "Bill <OHIP>");

        assertThat(html).contains("billing_no=123%22+onclick%3D%22alert%281%29");
        assertThat(html).contains(">123&#34; onclick=&#34;alert(1)</a>");
        assertThat(html).contains("title='Bill &lt;OHIP>");
        assertThat(html).doesNotContain("123\" onclick=\"alert(1)");
    }
}
