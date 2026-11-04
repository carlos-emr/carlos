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
package io.github.carlos_emr.carlos.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Source checks for the inbox result list ({@code oscarMDS/Page.jsp}).
 *
 * <p>Each row writes the lab's patient name, sex and date, and links that carry the provider numbers
 * and status of the request. Those values must reach the page encoded for where they land: HTML text,
 * an HTML attribute, or a URL. The links open their report from a real URL with {@code this.href}:
 * a {@code javascript:} link is percent-decoded before it runs, so URL encoding inside it does not hold.
 *
 * @since 2026-10-14
 */
@DisplayName("Inbox result list values")
@Tag("unit")
@Tag("lab")
class InboxPageValuesRegressionTest {

    private static final Path JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "oscarMDS", "Page.jsp");
    private static final Path INDEX_JS = Path.of("src", "main", "webapp", "share", "javascript", "oscarMDSIndex.js");
    private static final Path QUEUES_JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "oscarMDS", "documentsInQueues.jsp");

    private static List<String> reportLinks(String jsp) {
        return Arrays.stream(jsp.split("\n"))
                .filter(l -> l.contains("<a ") && (l.contains("reportWindow(") || l.contains("popupConsultation(")))
                .filter(l -> !l.contains("url.toString()"))
                .toList();
    }

    @Test
    @DisplayName("should open each report from a real URL that carries every request value intact")
    void shouldOpenReports_fromRealUrls() throws IOException {
        List<String> links = reportLinks(Files.readString(JSP, StandardCharsets.UTF_8));

        assertThat(links).hasSize(6);
        for (String link : links) {
            assertThat(link).as(link.strip())
                    .doesNotContain("href=\"javascript:")
                    // The consult link names its window by segment; the others open this.href.
                    .contains(link.contains("popupConsultation(") ? "/encounter/ViewRequest?segmentId=<%=SafeEncode.forUriComponent(segmentID)%>" : "this.href")
                    .doesNotContainPattern("(providerNo|searchProviderNo|status|segmentID|segmentId|id)=<%= ?(providerNo|searchProviderNo|status|segmentID) ?%>");
        }
    }

    @Test
    @DisplayName("should write the patient name into each link as HTML text")
    void shouldWritePatientName_asHtmlText() throws IOException {
        String jsp = Files.readString(JSP, StandardCharsets.UTF_8);

        // Raw only where it is a server-side parameter to an included page, never in the page itself.
        assertThat(Arrays.stream(jsp.split("\n"))
                .filter(l -> l.contains("<%=result.getPatientName()%>") || l.contains("<%=result.getPatientName() %>"))
                .filter(l -> !l.strip().startsWith("<jsp:param name=\"demoName\""))
                .toList()).isEmpty();
        for (String link : reportLinks(jsp)) {
            assertThat(link).as(link.strip())
                    .contains("<carlos:encode value='<%= result.getPatientName() %>' context=\"html\"/>");
        }
    }

    @Test
    @DisplayName("should show the hidden name field, the name comment, sex and date as stored")
    void shouldShowRowValues_asStored() throws IOException {
        String jsp = Files.readString(JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<input type=\"hidden\" name=\"demoName\" value=\"<carlos:encode value='<%= result.getPatientName() %>' context=\"htmlAttribute\"/>\"/>")
                .contains("<!-- demographic name <carlos:encode value='<%= result.getPatientName() %>' context=\"html\"/>  -->")
                .contains("<carlos:encode value='<%= result.getSex() %>' context=\"html\"/>")
                .doesNotContain("<%=result.getSex() %>")
                .doesNotContain("<%=result.getDateTime() + ");
    }

    @Test
    @DisplayName("should hand the included pages the plain patient name, which they show as stored")
    void shouldPassPlainPatientName_toIncludedPages() throws IOException {
        String jsp = Files.readString(JSP, StandardCharsets.UTF_8);

        // A JavaScript-escaped name showed its escapes (\x27) on an unlinked document.
        assertThat(jsp)
                .doesNotContain("SafeEncode.forJavaScript(result.getPatientName())")
                .doesNotContain("<jsp:param name=\"demoName\" value=\"<%=demoName%>\"/>");
    }

    @Test
    @DisplayName("should open a report URL without encoding its escapes a second time")
    void shouldOpenReportUrl_withoutReEncoding() throws IOException {
        String js = Files.readString(INDEX_JS, StandardCharsets.UTF_8);

        // this.href already holds %27 and %25; encodeURI turned them into %2527 and %2525.
        assertThat(js)
                .doesNotContain("window.open(encodeURI(page)")
                .contains("window.open(normalizeReportUrl(page), \"labreport\", windowprops)")
                .contains("new URL(page, document.baseURI).href");
    }

    @Test
    @DisplayName("should open the consultation popup with a defined window name")
    void shouldOpenConsultationPopup_withDefinedWindowName() throws IOException {
        // The consult link now has a real href: if popupConsultation throws, the browser follows the link
        // in place. msgConsReq was never defined, so it always threw.
        for (Path file : new Path[] {INDEX_JS, QUEUES_JSP}) {
            assertThat(Files.readString(file, StandardCharsets.UTF_8)).as(file.toString())
                    .doesNotContain("window.open(page, msgConsReq")
                    .contains("window.open(page, 'consultationRequest', windowprops)");
        }
    }
}
