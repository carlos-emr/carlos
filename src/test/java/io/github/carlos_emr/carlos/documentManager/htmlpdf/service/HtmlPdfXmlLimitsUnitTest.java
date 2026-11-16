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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.service;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestFonts;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestImages;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestPdfs;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderRequest;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderResult;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderSource;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.XhtmlDom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Documents the JDK's default XML limits used to refuse must print as authored.
 *
 * <p>Flying Saucer read the service's XHTML through {@code SAXParserFactory.newInstance()}. With
 * standalone Xerces gone that is the JDK parser, whose defaults stop at an element depth of 100,
 * at 100,000 characters of entity references (every {@code &amp;}, {@code &lt;} and {@code &gt;}
 * counts) and at 200 attributes on an element. A form, report or letter past any of them failed
 * the normal attempt and came out as plain text without its layout, tables and images, or not at
 * all. These tests run the real service, with production limits, on such documents.</p>
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("HTML-to-PDF beyond the JDK's default XML limits")
class HtmlPdfXmlLimitsUnitTest {

    @TempDir
    Path webRoot;

    private final HtmlPdfRenderService service = HtmlPdfTestServices.newService();

    private HtmlPdfRenderResult render(String html) throws Exception {
        return service.render(new HtmlPdfRenderRequest(html, webRoot, List.of(webRoot), HtmlPdfRenderSource.FORM));
    }

    private static String text(HtmlPdfRenderResult result) throws IOException {
        return String.join("\n", TestPdfs.pageTexts(result.pdf())).replaceAll("\\s+", " ").strip();
    }

    /** A table with a picture in it, wrapped in {@code depth} nested blocks. */
    private static String nested(int depth) {
        String picture = "data:image/png;base64," + Base64.getEncoder().encodeToString(TestImages.png(40, 30));
        return "<!DOCTYPE html><html><head><title>FAKE-Nested</title></head><body>"
                + "<div>".repeat(depth)
                + "<table border=\"1\"><tr><th>FAKE-Heading</th><th>FAKE-Signature</th></tr>"
                + "<tr><td>FAKE-Cell</td><td><img src=\"" + picture + "\" width=\"40\" height=\"30\" alt=\"FAKE-Alt\"/></td></tr></table>"
                + "</div>".repeat(depth)
                + "<p>FAKE-After</p></body></html>";
    }

    @Test
    @DisplayName("should print a document nested 150 levels deep as authored, with its table and image")
    void shouldPrintAsAuthored_whenNestedOneHundredFiftyDeep() throws Exception {
        HtmlPdfRenderResult result = render(nested(150));

        assertThat(result.degradations()).as("no step down the ladder").isEmpty();
        assertThat(text(result)).contains("FAKE-Heading", "FAKE-Signature", "FAKE-Cell", "FAKE-After")
                // The alternative text prints only when the picture itself is left out.
                .doesNotContain("FAKE-Alt");
        assertThat(TestPdfs.imageDraws(result.pdf())).as("the picture in the table is drawn").isEqualTo(1);
    }

    @Test
    @DisplayName("should print a document nested to the depth the HTML parser allows")
    void shouldPrintAsAuthored_whenNestedFiveHundredDeep() throws Exception {
        HtmlPdfRenderResult result = render(nested(500));

        assertThat(result.degradations()).isEmpty();
        assertThat(text(result)).contains("FAKE-Cell", "FAKE-After");
        assertThat(TestPdfs.imageDraws(result.pdf())).isEqualTo(1);
    }

    @Test
    @DisplayName("should hand the XML parser a tree inside its depth limit however deep the HTML nests")
    void shouldStayInsideParserDepth_whenHtmlNestsDeeperThanTheHtmlParserKeeps() throws Exception {
        PrintDocumentComposer composer = new PrintDocumentComposer();
        Set<HtmlPdfRenderDegradation> none = EnumSet.noneOf(HtmlPdfRenderDegradation.class);
        String xhtml = composer.finish(composer.prepare(nested(5_000), PrintMode.NORMAL, none), PrintMode.NORMAL,
                TestFonts.sharedFonts(), none);

        org.w3c.dom.Document document = XhtmlDom.parse(xhtml);

        // Jsoup keeps 513 levels and flattens the rest; the parser's limit is there for a string
        // that did not come through it.
        assertThat(depth(document)).isBetween(500, 600);
        assertThat(document.getElementsByTagName("td").getLength()).isEqualTo(2);
    }

    /** The deepest element of the document, counted without recursion. */
    private static int depth(org.w3c.dom.Document document) {
        int deepest = 0;
        int current = 0;
        org.w3c.dom.Node node = document.getDocumentElement();
        while (node != null) {
            if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                deepest = Math.max(deepest, current + 1);
            }
            if (node.getFirstChild() != null) {
                node = node.getFirstChild();
                current++;
                continue;
            }
            while (node != null && node.getNextSibling() == null) {
                node = node.getParentNode();
                current--;
            }
            if (node != null) {
                node = node.getNextSibling();
            }
        }
        return deepest;
    }

    @Test
    @DisplayName("should print the text and make no request when the HTML declares external entities")
    void shouldNeutraliseDocumentType_whenHtmlDeclaresExternalEntities() throws Exception {
        Path marker = Files.writeString(webRoot.resolve("marker.txt"), "FAKE-Marker-Content", StandardCharsets.UTF_8);
        try (ServerSocket listener = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
            String remote = "http://127.0.0.1:" + listener.getLocalPort();
            String html = "<!DOCTYPE html SYSTEM \"" + remote + "/evil.dtd\" [<!ENTITY local SYSTEM \"" + marker.toUri() + "\">"
                    + "<!ENTITY far SYSTEM \"" + remote + "/entity\">]>"
                    + "<html><head><title>FAKE-Hostile</title></head><body><p>FAKE-Before &local; &far; FAKE-After</p></body></html>";

            HtmlPdfRenderResult result = render(html);

            // The HTML parser never treats the subset as declarations, so nothing is left for the XML
            // parser to refuse: the references print as the text they are.
            assertThat(result.degradations()).isEmpty();
            assertThat(text(result)).contains("FAKE-Before", "FAKE-After").doesNotContain("FAKE-Marker-Content");
            listener.setSoTimeout(300);
            assertThatExceptionOfType(SocketTimeoutException.class).as("no connection to the listener")
                    .isThrownBy(() -> listener.accept().close());
        }
    }

    @Test
    @DisplayName("should print a document with more than 100,000 escaped characters as authored")
    void shouldPrintAsAuthored_whenTextHoldsOverOneHundredThousandEntityReferences() throws Exception {
        // 25 paragraphs of 1,500 "a<b & c>d" groups: 112,500 references, under the text-only size limit.
        String paragraph = "<p>" + "a&lt;b &amp; c&gt;d ".repeat(1_500) + "</p>";
        String html = "<html><head><title>FAKE-Escaped</title></head><body><p>FAKE-First</p>"
                + paragraph.repeat(25) + "<table><tr><td>FAKE-Last</td></tr></table></body></html>";
        assertThat(html.length()).isLessThan(HtmlPdfRenderService.TEXT_ONLY_HTML_CHARS);

        HtmlPdfRenderResult result = render(html);

        assertThat(result.degradations()).isEmpty();
        assertThat(text(result)).contains("FAKE-First", "a<b & c>d", "FAKE-Last");
    }

    @Test
    @DisplayName("should print an element with more than 200 attributes as authored")
    void shouldPrintAsAuthored_whenElementHasOverTwoHundredAttributes() throws Exception {
        StringBuilder attributes = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            attributes.append(" data-fake-").append(i).append("=\"").append(i).append('"');
        }
        String html = "<html><head><title>FAKE-Attributes</title></head><body><table><tr><td" + attributes
                + ">FAKE-Cell</td></tr></table></body></html>";

        HtmlPdfRenderResult result = render(html);

        assertThat(result.degradations()).isEmpty();
        assertThat(text(result)).contains("FAKE-Cell");
    }

    @Test
    @DisplayName("should print a document that declares a legacy XHTML document type as authored")
    void shouldPrintAsAuthored_whenDocumentDeclaresXhtmlTransitional() throws Exception {
        String html = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" "
                + "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>FAKE-Legacy</title></head>"
                + "<body><table><tr><td>FAKE-Cell&nbsp;FAKE-Second &copy;</td></tr></table></body></html>";

        HtmlPdfRenderResult result = render(html);

        assertThat(result.degradations()).isEmpty();
        assertThat(text(result)).contains("FAKE-Cell", "FAKE-Second", "©");
    }
}
