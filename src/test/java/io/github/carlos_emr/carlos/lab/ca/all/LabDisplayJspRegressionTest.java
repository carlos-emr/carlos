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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern ACK_LAB_FUNC_ENCODE_TAG = Pattern.compile(
            "<carlos:encode\\s+value\\s*=\\s*(['\"])<%=\\s*ackLabFunc\\s*%>\\1"
                    + "\\s+context\\s*=\\s*(['\"])([^'\"]+)\\2\\s*/\\s*>");
    private static final int CONTEXT_GROUP = 3;

    @Test
    @DisplayName("should render acknowledgement handler with htmlAttribute encoding in lab display")
    void shouldRenderAcknowledgementHandler_withHtmlAttributeEncodingInLabDisplay() throws IOException {
        assertAcknowledgementHandlerUsesHtmlAttributeEncoding(
                Files.readString(LAB_DISPLAY_JSP, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("should render acknowledgement handler with htmlAttribute encoding in ajax lab display")
    void shouldRenderAcknowledgementHandler_withHtmlAttributeEncodingInAjaxLabDisplay() throws IOException {
        assertAcknowledgementHandlerUsesHtmlAttributeEncoding(
                Files.readString(LAB_DISPLAY_AJAX_JSP, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("should route embedded document observation links to the download action in lab display")
    void shouldRouteEmbeddedDocumentObservationLinks_toDownloadActionInLabDisplay() throws IOException {
        assertEmbeddedDocumentObservationLinksUseDownloadAction(
                Files.readString(LAB_DISPLAY_JSP, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("should route embedded document observation links to the download action in ajax lab display")
    void shouldRouteEmbeddedDocumentObservationLinks_toDownloadActionInAjaxLabDisplay() throws IOException {
        assertEmbeddedDocumentObservationLinksUseDownloadAction(
                Files.readString(LAB_DISPLAY_AJAX_JSP, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("should close inboxhub iframe after successful lab macro")
    void shouldCloseInboxhubIframe_afterSuccessfulLabMacro() throws IOException {
        String jsp = Files.readString(LAB_DISPLAY_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("return response.json();")
                .contains("if (json && json.success)")
                .contains("closeLabAfterMacro(formid);")
                .contains("if (window.frameElement)")
                .contains("window.frameElement.closest('.document-card.card')")
                .contains("new BroadcastChannel('inboxhub-refresh')");
    }

    private void assertAcknowledgementHandlerUsesHtmlAttributeEncoding(String jsp) {
        // ackLabFunc is already executable JavaScript built server-side with dynamic values
        // pre-encoded via SafeEncode.forJavaScriptAttribute. The enclosing onclick attribute
        // therefore only needs HTML-attribute encoding -- re-applying javaScriptAttribute
        // encoding produces top-level \x escapes that break onclick parsing.
        assertThat(ackLabFuncEncodeContexts(jsp))
                .as("every ackLabFunc onclick handler must use htmlAttribute encoding")
                .isNotEmpty()
                .containsOnly("htmlAttribute");
    }

    private void assertEmbeddedDocumentObservationLinksUseDownloadAction(String jsp) {
        assertThat(jsp)
                .contains("String embeddedDocumentHref = request.getContextPath() + \"/lab/DownloadEmbeddedDocumentFromLab?labNo=\"")
                .contains("String observationHref = isEmbeddedDocumentResult ? embeddedDocumentHref : labValuesHref;")
                .contains("href=\"<%= SafeEncode.forHtmlAttribute(observationHref) %>\"")
                .contains("href=\"<%= SafeEncode.forHtmlAttribute(embeddedDocumentHref) %>\"");
    }

    private static List<String> ackLabFuncEncodeContexts(String jsp) {
        Matcher matcher = ACK_LAB_FUNC_ENCODE_TAG.matcher(jsp);
        List<String> contexts = new ArrayList<>();
        while (matcher.find()) {
            contexts.add(matcher.group(CONTEXT_GROUP));
        }
        return contexts;
    }
}
