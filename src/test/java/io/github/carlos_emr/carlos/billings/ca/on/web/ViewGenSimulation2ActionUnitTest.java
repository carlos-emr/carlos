/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.service.OhipReportGenerationService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ViewGenSimulation2Action")
@Tag("unit")
@Tag("billing")
class ViewGenSimulation2ActionUnitTest {

    @Test
    void shouldEncodeErrorMessageBeforeEmbeddingInSimulationHtml() {
        OhipReportGenerationService.SimulationResult result =
                new OhipReportGenerationService.SimulationResult(
                        "<table><tr><td>preview</td></tr></table>",
                        "bad <script>alert(1)</script><br>",
                        "2026-04-01",
                        "2026-04-30");

        String html = ViewGenSimulation2Action.formatSimulationHtml(result);

        assertThat(html).contains("bad &lt;script&gt;alert(1)&lt;/script&gt;<br>");
        assertThat(html).doesNotContain("<script>");
        assertThat(html).endsWith("<table><tr><td>preview</td></tr></table>");
    }
}
