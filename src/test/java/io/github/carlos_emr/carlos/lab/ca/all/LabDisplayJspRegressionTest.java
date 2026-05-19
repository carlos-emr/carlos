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
package io.github.carlos_emr.carlos.lab.ca.all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the lab display acknowledgement controls.
 *
 * @since 2026-05-19
 */
@DisplayName("Lab display JSP regression tests")
@Tag("unit")
@Tag("lab")
class LabDisplayJspRegressionTest {

    private static final Path LAB_DISPLAY_JSP = Path.of(
            "src", "main", "webapp", "WEB-INF", "jsp", "lab", "CA", "ALL", "labDisplay.jsp");
    private static final Path LAB_DISPLAY_AJAX_JSP = Path.of(
            "src", "main", "webapp", "WEB-INF", "jsp", "lab", "CA", "ALL", "labDisplayAjax.jsp");

    @Test
    @DisplayName("should render acknowledgement handler as executable JavaScript in lab display")
    void shouldRenderAcknowledgementHandler_asExecutableJavaScriptInLabDisplay() throws IOException {
        assertAcknowledgementHandlerUsesHtmlAttributeEncoding(Files.readString(LAB_DISPLAY_JSP, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("should render acknowledgement handler as executable JavaScript in ajax lab display")
    void shouldRenderAcknowledgementHandler_asExecutableJavaScriptInAjaxLabDisplay() throws IOException {
        assertAcknowledgementHandlerUsesHtmlAttributeEncoding(Files.readString(LAB_DISPLAY_AJAX_JSP, StandardCharsets.UTF_8));
    }

    private void assertAcknowledgementHandlerUsesHtmlAttributeEncoding(String jsp) {
        assertThat(jsp)
                .as("ackLabFunc is already executable JavaScript; only the enclosing HTML attribute needs encoding")
                .contains("onclick=\"<carlos:encode value='<%= ackLabFunc %>' context=\"htmlAttribute\"/>")
                .doesNotContain("onclick=\"<carlos:encode value='<%= ackLabFunc %>' context=\"javaScriptAttribute\"/>");
    }
}
