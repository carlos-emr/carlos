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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import io.github.carlos_emr.carlos.billings.ca.on.service.OhipReportGenerationService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit coverage for {@code ViewGenSimulation2Action} simulation-page result setup. */
@DisplayName("ViewGenSimulation2Action")
@Tag("unit")
@Tag("billing")
class ViewGenSimulation2ActionUnitTest {

    @Test
    void shouldEncodeErrorMessage_beforeEmbeddingInSimulationHtml() {
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
