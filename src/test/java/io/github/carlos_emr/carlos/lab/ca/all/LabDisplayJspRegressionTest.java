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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "src/main/webapp/WEB-INF/jsp/lab/CA/ALL/labDisplay.jsp",
            "src/main/webapp/WEB-INF/jsp/lab/CA/ALL/labDisplayAjax.jsp",
            "src/main/webapp/WEB-INF/jspf/lab-embedded-pdf-preview.jspf",
            "src/main/webapp/WEB-INF/jsp/admin/labDisplaySettings.jsp"})
    @DisplayName("should set the negotiated locale immediately before every oscarResources bundle")
    void shouldSetNegotiatedLocale_beforeEveryBundle(String file) throws IOException {
        String jsp = Files.readString(Path.of(file), StandardCharsets.UTF_8);
        Matcher bundles = Pattern.compile("<fmt:setBundle basename=\"oscarResources\"/>").matcher(jsp);
        int count = 0;
        while (bundles.find()) {
            count++;
            String before = jsp.substring(0, bundles.start()).stripTrailing();
            assertThat(before).as("%s: the line before setBundle", file)
                    .endsWith("LocaleUtils.resolveBundleLocale(request) %>\"/>");
        }
        assertThat(count).as("%s declares the oscarResources bundle", file).isPositive();
    }

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
    @DisplayName("should render the inline embedded PDF preview in lab display")
    void shouldRenderEmbeddedPdfPreview_inLabDisplay() throws IOException {
        assertEmbeddedPdfPreviewIsRendered(Files.readString(LAB_DISPLAY_JSP, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("should render the inline embedded PDF preview in ajax lab display")
    void shouldRenderEmbeddedPdfPreview_inAjaxLabDisplay() throws IOException {
        assertEmbeddedPdfPreviewIsRendered(Files.readString(LAB_DISPLAY_AJAX_JSP, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("should render Epsilon ED rows with the download link, note and preview in lab display")
    void shouldRenderEpsilonEmbeddedDocuments_inLabDisplay() throws IOException {
        assertEpsilonBranchRendersEmbeddedDocuments(
                Files.readString(LAB_DISPLAY_JSP, StandardCharsets.UTF_8), "HHSEMR");
    }

    @Test
    @DisplayName("should render Epsilon ED rows with the download link, note and preview in ajax lab display")
    void shouldRenderEpsilonEmbeddedDocuments_inAjaxLabDisplay() throws IOException {
        assertEpsilonBranchRendersEmbeddedDocuments(
                Files.readString(LAB_DISPLAY_AJAX_JSP, StandardCharsets.UTF_8), "HHSEMR");
    }

    @Test
    @DisplayName("should match the viewer's acknowledgement by CARLOS provider number in ajax lab display")
    void shouldMatchAcknowledgement_byCarlosProviderNumberInAjaxLabDisplay() throws IOException {
        String jsp = Files.readString(LAB_DISPLAY_AJAX_JSP, StandardCharsets.UTF_8);

        // ReportStatus.getProviderNo() is the routed provider's practitioner number, null for a
        // provider without one; dereferencing it made the AJAX view answer 500 for such labs (#4124).
        assertThat(jsp)
                // providerNo is a request parameter this page never defaults, so the guard matters too.
                .contains("if (providerNo != null && providerNo.equals(reportStatus.getOscarProviderNo()))")
                .doesNotContain("reportStatus.getProviderNo().equals(");
    }

    @Test
    @DisplayName("should frame the preview lazily, encoded, and without inline script")
    void shouldFramePreviewLazily_withoutInlineScript() throws IOException {
        String fragment = Files.readString(Path.of("src/main/webapp/WEB-INF/jspf/lab-embedded-pdf-preview.jspf"),
                StandardCharsets.UTF_8);

        // labDisplayAjax.jsp is inserted with innerHTML, where an inline script never runs.
        assertThat(fragment)
                .contains("src=\"<%= SafeEncode.forHtmlAttribute(embeddedDocumentViewHref) %>\"")
                .contains("loading=\"lazy\"")
                .contains("<details class=\"lab-embedded-pdf\"")
                .contains("labPdfPreviewSettings.inlinePreviewEnabled()")
                .contains("EmbeddedLabDocumentLoader.Status.TOO_LARGE")
                .doesNotContain("<script");
    }

    @Test
    @DisplayName("should close inboxhub iframe after successful lab macro")
    void shouldCloseInboxhubIframe_afterSuccessfulLabMacro() throws IOException {
        String jsp = Files.readString(LAB_DISPLAY_JSP, StandardCharsets.UTF_8);

        // closeLabAfterMacro now also takes whether the macro acknowledged: closing this window
        // is what closeOnSuccess asks for, but taking the lab out of the inbox is only right
        // when it was actually acknowledged (a tickler-only macro succeeds and acknowledges
        // nothing).
        assertThat(jsp)
                .contains("return response.json();")
                .contains("if (json && json.success)")
                .contains("closeLabAfterMacro(formid, json.acknowledged);")
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
                .contains("String embeddedDocumentHref = request.getContextPath() + \"/lab/DownloadEmbeddedDocumentFromLab\" + embeddedDocumentQuery;")
                .contains("String observationHref = isEmbeddedDocumentResult ? embeddedDocumentHref : labValuesHref;")
                .contains("href=\"<%= SafeEncode.forHtmlAttribute(observationHref) %>\"")
                .contains("href=\"<%= SafeEncode.forHtmlAttribute(embeddedDocumentHref) %>\" class=\"lab-embedded-pdf-download\">"
                        + "<fmt:message key=\"lab.embeddedPdf.download\"/></a>");
    }

    private void assertEmbeddedPdfPreviewIsRendered(String jsp) {
        // Detection is per OBX by value type (any lab type) and requires a verified PDF, not the
        // former hard-coded ExcellerisON/PATHL7 test; the legacy PATHL7 flag is resolved server-side.
        assertThat(jsp)
                .contains("EmbeddedLabDocumentLoader.Inspection embeddedDocument = handler.isOBXEmbeddedDocument(j, k)")
                .contains("boolean isEmbeddedDocumentResult = embeddedDocument != null && embeddedDocument.isPdf();")
                .contains("String embeddedDocumentViewHref = request.getContextPath() + \"/lab/ViewEmbeddedDocumentFromLab\" + embeddedDocumentQuery;")
                .contains("<%@ include file=\"/WEB-INF/jspf/lab-embedded-pdf-preview.jspf\" %>")
                // A binary ED payload that is not a PDF shows a note, never its encoded bytes.
                .contains("boolean isUndisplayableEmbeddedDocument = embeddedDocument != null && embeddedDocument.isUndisplayable();")
                .contains("<em class=\"lab-embedded-document-unsupported\"><fmt:message key=\"lab.embeddedPdf.notPdf\"/></em>")
                .doesNotContain("handler.getMsgType().equals(\"ExcellerisON\") || handler.getMsgType().equals(\"PATHL7\")) && handler.getOBXValueType(j, k).equals(\"ED\")")
                .doesNotContain("&legacy=true");
    }

    /**
     * Epsilon rows are filtered by header inside their own branch, which never reaches the shared
     * ED row, so the branch itself must carry the download link, the not-a-PDF note and the preview
     * row for both of its row shapes (#4124).
     */
    private void assertEpsilonBranchRendersEmbeddedDocuments(String jsp, String nextBranchMsgType) {
        int start = jsp.indexOf("// Epsilon rows are filtered by header here");
        assertThat(start).as("Epsilon row branch").isNotNegative();
        int end = jsp.indexOf("handler.getMsgType().equals(\"" + nextBranchMsgType + "\")", start);
        assertThat(end).as("branch after the Epsilon row branch").isGreaterThan(start);
        String branch = jsp.substring(start, end);

        assertThat(branch)
                .contains("href=\"<%= SafeEncode.forHtmlAttribute(observationHref) %>\"")
                .contains("<% if (isEmbeddedDocumentResult) { %>")
                .contains("<% } else if (isUndisplayableEmbeddedDocument) { %>")
                .doesNotContain("/lab/CA/ON/ViewLabValues");
        assertThat(occurrences(branch, "href=\"<%= SafeEncode.forHtmlAttribute(embeddedDocumentHref) %>\" class=\"lab-embedded-pdf-download\""))
                .isEqualTo(2);
        assertThat(occurrences(branch, "<fmt:message key=\"lab.embeddedPdf.notPdf\"/>")).isEqualTo(2);
        assertThat(occurrences(branch, "<%@ include file=\"/WEB-INF/jspf/lab-embedded-pdf-preview.jspf\" %>")).isEqualTo(2);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
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
