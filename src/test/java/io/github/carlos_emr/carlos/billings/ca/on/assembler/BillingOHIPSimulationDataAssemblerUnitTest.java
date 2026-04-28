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

@DisplayName("BillingOHIPSimulationDataAssembler")
@Tag("unit")
@Tag("billing")
class BillingOHIPSimulationDataAssemblerUnitTest {

    @Test
    void shouldEncodeProviderErrorLinesBeforeEmbeddingInPreviewHtml() {
        String html = BillingOHIPSimulationDataAssembler.formatErrorLine(
                "The billing code (<script>alert(1)</script>) for providers (999998) is not correct!");

        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(html).doesNotContain("<script>");
        assertThat(html).endsWith("<br>");
    }
}
