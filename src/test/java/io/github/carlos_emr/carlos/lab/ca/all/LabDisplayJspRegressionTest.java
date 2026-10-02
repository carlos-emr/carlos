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
    @DisplayName("should encode the preview's localised text and count only rendered frames")
    void shouldEncodePreviewMessages_andCountOnlyRenderedFrames() throws IOException {
        String fragment = Files.readString(Path.of("src/main/webapp/WEB-INF/jspf/lab-embedded-pdf-preview.jspf"),
                StandardCharsets.UTF_8);

        assertThat(fragment)
                .contains("title=\"<carlos:encode value=\"${labEmbeddedPdfFrameTitle}\" context=\"htmlAttribute\"/>\"")
                .contains("<carlos:encode value=\"${labEmbeddedPdfTooLarge}\"/>")
                .contains("<carlos:encode value=\"${labEmbeddedPdfPreview}\"/>")
                .contains("<carlos:encode value=\"${labEmbeddedPdfFallback}\"/>");
        // Every message is resolved into a variable, never written straight into the page, in
        // either the self-closing or the block (<fmt:param>) form.
        assertThat(fmtMessagesWithoutVar(fragment)).isEmpty();
        // An over-limit PDF shows a note, not a frame, so it must not use up an expanded slot.
        int tooLargeBranch = fragment.indexOf("EmbeddedLabDocumentLoader.Status.TOO_LARGE");
        int frameBranch = fragment.indexOf("<% } else {", tooLargeBranch);
        assertThat(fragment.indexOf("labPdfPreviewCount++")).isGreaterThan(frameBranch);
        assertThat(frameBranch).isGreaterThan(tooLargeBranch);
    }

    @Test
    @DisplayName("should flag every fmt:message without var, whatever its form or attribute order")
    void shouldFlagFmtMessageWithoutVar_inEitherForm() {
        assertThat(fmtMessagesWithoutVar("<fmt:message key=\"a\"/>")).hasSize(1);
        assertThat(fmtMessagesWithoutVar("<fmt:message key=\"a\"><fmt:param value=\"1\"/></fmt:message>")).hasSize(1);
        assertThat(fmtMessagesWithoutVar("<fmt:message\n    key=\"a\" bundle=\"${b}\">x</fmt:message>")).hasSize(1);
        assertThat(fmtMessagesWithoutVar("<fmt:message key=\"a\" var=\"v\"/>")).isEmpty();
        assertThat(fmtMessagesWithoutVar("<fmt:message var=\"v\" key=\"a\"><fmt:param value=\"1\"/></fmt:message>")).isEmpty();
        // A closing tag or another tag with a similar prefix is not a message.
        assertThat(fmtMessagesWithoutVar("</fmt:message><fmt:messageFormat key=\"a\"/>")).isEmpty();
    }

    @Test
    @DisplayName("should send ED rows past the HHSEMR, CML and Spire renderers in lab display")
    void shouldRouteEmbeddedDocumentRows_pastLabSpecificRenderersInLabDisplay() throws IOException {
        String jsp = Files.readString(LAB_DISPLAY_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("} else if (embeddedDocument == null && (handler.getMsgType().equals(\"HHSEMR\") || handler.getMsgType().equals(\"CML\"))) {")
                .contains("} else if (embeddedDocument == null && handler.getMsgType().equals(\"Spire\")) {");
    }

    @Test
    @DisplayName("should keep lab-specific result cells off ED rows so each gets one result cell")
    void shouldGuardLabSpecificResultCells_againstEmbeddedDocumentRows() throws IOException {
        String jsp = Files.readString(LAB_DISPLAY_JSP, StandardCharsets.UTF_8);
        String ajax = Files.readString(LAB_DISPLAY_AJAX_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("if(embeddedDocument == null && handler instanceof CLSHandler && ( (CLSHandler) handler).isUnstructured()) {")
                .contains("else if(embeddedDocument == null && handler.getMsgType().equals(\"MEDITECH\")  && isUnstructuredDoc ) {")
                .contains("} else if(embeddedDocument == null && handler.getMsgType().equals(\"MEDITECH\")  && ((MEDITECHHandler) handler).isReportData() ) { %>")
                .doesNotContain("if(handler instanceof CLSHandler && ( (CLSHandler) handler).isUnstructured()) {");
        assertThat(ajax)
                .contains("if (embeddedDocument == null && (handler.getOBXResult(j, k) != null && handler.getOBXResult(j, k).length() > 100) && isSGorCDC) {%>");
    }

    @Test
    @DisplayName("should send ED rows past the HHSEMR renderer in ajax lab display")
    void shouldRouteEmbeddedDocumentRows_pastLabSpecificRendererInAjaxLabDisplay() throws IOException {
        String jsp = Files.readString(LAB_DISPLAY_AJAX_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("} else if (embeddedDocument == null && handler.getMsgType().equals(\"HHSEMR\")) {");
    }

    @Test
    @DisplayName("should send PDF and binary ED rows past the unstructured-report layout in both lab views")
    void shouldRouteEmbeddedPdfRows_pastUnstructuredLayoutInBothViews() throws IOException {
        for (Path view : List.of(LAB_DISPLAY_JSP, LAB_DISPLAY_AJAX_JSP)) {
            String jsp = Files.readString(view, StandardCharsets.UTF_8);

            assertThat(jsp).as(view.toString())
                    .contains("if (isUnstructuredDoc && !isEmbeddedDocumentResult && !isUndisplayableEmbeddedDocument) {")
                    // Text ED rows stay in the narrative layout but show the ED.5 text.
                    .contains("embeddedDocument != null && embeddedDocument.status() == EmbeddedLabDocumentLoader.Status.TEXT"
                            + " ? handler.getOBXEmbeddedDocumentText(j, k) : handler.getOBXResult(j, k)");
        }
    }

    @Test
    @DisplayName("should show ED text normalised and keep the Excelleris sub-ID label in both lab views")
    void shouldRenderEmbeddedText_withExcellerisSubIdInBothViews() throws IOException {
        for (Path view : List.of(LAB_DISPLAY_JSP, LAB_DISPLAY_AJAX_JSP)) {
            String jsp = Files.readString(view, StandardCharsets.UTF_8);

            assertThat(jsp).as(view.toString())
                    .contains("<em><carlos:encode value='<%= ((ExcellerisOntarioHandler) handler).getOBXSubIdWithEmbeddedDocumentText(j, k) %>' context=\"htmlWithBreakMarkers\"/></em>")
                    .contains("<carlos:encode value='<%= handler.getOBXEmbeddedDocumentText(j, k) %>' context=\"htmlWithBreakMarkers\"/>")
                    .doesNotContain("handler.getOBXEmbeddedDocumentData(j, k) %>");
        }
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

    private static final Pattern FMT_MESSAGE_START_TAG = Pattern.compile("<fmt:message(?=[\\s/>])[^>]*>");
    private static final Pattern VAR_ATTRIBUTE = Pattern.compile("\\svar\\s*=");

    /** Every {@code <fmt:message>} start or self-closing tag that has no {@code var} attribute. */
    private static List<String> fmtMessagesWithoutVar(String jsp) {
        Matcher matcher = FMT_MESSAGE_START_TAG.matcher(jsp);
        List<String> tags = new ArrayList<>();
        while (matcher.find()) {
            if (!VAR_ATTRIBUTE.matcher(matcher.group()).find()) {
                tags.add(matcher.group());
            }
        }
        return tags;
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
