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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Unit tests for {@link XhtmlDom}: the limits it states in place of the JDK's defaults, and
 * everything a document type could bring, which it refuses. Hostile documents name a listener on the
 * loopback interface and a synthetic marker file only; nothing here contacts another host.
 */
@Tag("unit")
@Tag("htmlpdf")
@Tag("security")
@DisplayName("XhtmlDom Unit Tests")
class XhtmlDomUnitTest {

    private static final String XHTML_NAMESPACE = "http://www.w3.org/1999/xhtml";

    private static String page(String body) {
        return "<!DOCTYPE html><html><head><title>FAKE-Title</title></head><body>" + body + "</body></html>";
    }

    private static Element body(Document document) {
        return (Element) document.getDocumentElement().getElementsByTagName("body").item(0);
    }

    @Nested
    @DisplayName("the tree Flying Saucer lays out")
    class Tree {

        @Test
        @DisplayName("should build a namespace-aware tree without a document type node")
        void shouldBuildNamespaceAwareTree_withoutDocumentTypeNode() throws Exception {
            Document document = XhtmlDom.parse("<!DOCTYPE html><html xmlns=\"" + XHTML_NAMESPACE + "\">"
                    + "<head><title>FAKE-Title</title></head><body><p class=\"a\">FAKE-Text</p>"
                    + "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">"
                    + "<use xlink:href=\"#a\"/></svg></body></html>");

            assertThat(document.getDoctype()).isNull();
            Element root = document.getDocumentElement();
            assertThat(root.getLocalName()).isEqualTo("html");
            assertThat(root.getNamespaceURI()).isEqualTo(XHTML_NAMESPACE);
            Element use = (Element) document.getElementsByTagNameNS("http://www.w3.org/2000/svg", "use").item(0);
            assertThat(use.getAttributeNS("http://www.w3.org/1999/xlink", "href")).isEqualTo("#a");
        }

        @Test
        @DisplayName("should give an element without a namespace its local name")
        void shouldKeepLocalName_forElementWithoutNamespace() throws Exception {
            Document document = XhtmlDom.parse(page("<p>FAKE-Text</p>"));

            Element paragraph = (Element) body(document).getFirstChild();
            assertThat(paragraph.getLocalName()).isEqualTo("p");
            assertThat(paragraph.getNamespaceURI()).isNull();
        }

        @Test
        @DisplayName("should keep white space, expand references and merge CDATA into one text node")
        void shouldMergeTextAndCdata_intoOneTextNode() throws Exception {
            Document document = XhtmlDom.parse(
                    page("<pre>  a &lt; b &amp;&#xa0;c\n<![CDATA[ <d> ]]> e </pre> <span>x</span>"));

            Node pre = body(document).getFirstChild();
            assertThat(pre.getChildNodes().getLength()).isEqualTo(1);
            assertThat(pre.getFirstChild().getNodeType()).isEqualTo(Node.TEXT_NODE);
            assertThat(pre.getTextContent()).isEqualTo("  a < b & c\n <d>  e ");
            // The space between the two elements is a text node of its own: no DTD says it may go.
            assertThat(pre.getNextSibling().getNodeType()).isEqualTo(Node.TEXT_NODE);
            assertThat(pre.getNextSibling().getTextContent()).isEqualTo(" ");
        }

        @Test
        @DisplayName("should add no DTD attribute defaults for a legacy XHTML document type")
        void shouldAddNoAttributeDefaults_forLegacyDocumentType() throws Exception {
            Document document = XhtmlDom.parse("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" "
                    + "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html><head><title>FAKE</title></head>"
                    + "<body><table><tr><td>FAKE-Cell</td></tr></table></body></html>");

            Element cell = (Element) document.getElementsByTagName("td").item(0);
            assertThat(cell.getAttributes().getLength()).isZero();
            assertThat(document.getDocumentElement().getNamespaceURI()).isNull();
        }
    }

    @Nested
    @DisplayName("limits in place of the JDK's defaults")
    class Limits {

        @Test
        @DisplayName("should accept more than 100,000 predefined references")
        void shouldAcceptPredefinedReferences_beyondJdkDefault() throws Exception {
            Document document = XhtmlDom.parse(page("<p>" + "&amp;&lt;&gt;&quot;&apos;".repeat(40_000) + "</p>"));

            assertThat(body(document).getTextContent()).hasSize(200_000).startsWith("&<>\"'");
        }

        @Test
        @DisplayName("should accept an element nested as deep as the limit and refuse one deeper")
        void shouldBoundElementDepth_atItsLimit() throws Exception {
            // html and body are two of the levels.
            int inside = XhtmlDom.MAX_ELEMENT_DEPTH - 2;

            Document deepest = XhtmlDom.parse(page("<div>".repeat(inside) + "FAKE" + "</div>".repeat(inside)));

            assertThat(deepest.getElementsByTagName("div").getLength()).isEqualTo(inside);
            assertThat(XhtmlDom.MAX_ELEMENT_DEPTH).isGreaterThan(513);
            assertThatExceptionOfType(SAXException.class).isThrownBy(() -> XhtmlDom.parse(
                    page("<div>".repeat(inside + 1) + "FAKE" + "</div>".repeat(inside + 1))));
        }

        @Test
        @DisplayName("should accept thousands of attributes on an element and refuse more than the limit")
        void shouldBoundAttributes_atTheirLimit() throws Exception {
            Document document = XhtmlDom.parse(page("<p" + attributes(5_000) + ">FAKE</p>"));

            assertThat(body(document).getFirstChild().getAttributes().getLength()).isEqualTo(5_000);
            assertThatExceptionOfType(SAXException.class).isThrownBy(() -> XhtmlDom.parse(
                    page("<p" + attributes(XhtmlDom.MAX_ATTRIBUTES_PER_ELEMENT + 1) + ">FAKE</p>")));
        }

        private String attributes(int count) {
            StringBuilder attributes = new StringBuilder(count * 16);
            for (int i = 0; i < count; i++) {
                attributes.append(" data-a").append(i).append("=\"").append(i).append('"');
            }
            return attributes.toString();
        }
    }

    @Nested
    @DisplayName("document types, entities and inclusions")
    class Hostile {

        @TempDir
        Path directory;

        @Test
        @DisplayName("should refuse a document type that declares an entity")
        void shouldRefuseDocumentType_withInternalSubset() {
            String declared = "<!DOCTYPE html [<!ENTITY fake \"FAKE-Expanded\">]>"
                    + "<html><head><title>FAKE</title></head><body><p>&fake;</p></body></html>";

            assertThatExceptionOfType(SAXException.class).isThrownBy(() -> XhtmlDom.parse(declared));
        }

        @Test
        @DisplayName("should refuse an entity expansion attack instead of expanding it")
        void shouldRefuseEntityExpansionAttack_beforeAnyExpansion() {
            String bomb = "<!DOCTYPE html [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">"
                    + "<!ENTITY c \"&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;\">]>"
                    + "<html><head><title>FAKE</title></head><body><p>&c;</p></body></html>";

            assertThatExceptionOfType(SAXException.class).isThrownBy(() -> XhtmlDom.parse(bomb));
        }

        @Test
        @DisplayName("should refuse an external entity and read neither the file nor the address it names")
        void shouldRefuseExternalEntity_withoutReadingIt() throws Exception {
            Path marker = Files.writeString(directory.resolve("marker.txt"), "FAKE-Marker-Content", StandardCharsets.UTF_8);
            try (ServerSocket listener = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
                String remote = "http://127.0.0.1:" + listener.getLocalPort();
                String hostile = "<!DOCTYPE html SYSTEM \"" + remote + "/evil.dtd\" [<!ENTITY % remote SYSTEM \"" + remote + "/p.dtd\"> %remote;"
                        + "<!ENTITY local SYSTEM \"" + marker.toUri() + "\"><!ENTITY far SYSTEM \"" + remote + "/entity\">]>"
                        + "<html><head><title>FAKE</title></head><body><p>&local;&far;</p></body></html>";

                assertThatExceptionOfType(SAXException.class).isThrownBy(() -> XhtmlDom.parse(hostile))
                        .satisfies(refused -> assertThat(refused.getMessage()).doesNotContain("FAKE-Marker-Content"));

                assertNoConnection(listener);
            }
        }

        @Test
        @DisplayName("should drop a legacy document type without fetching the DTD it names")
        void shouldDropExternalDocumentType_withoutFetchingIt() throws Exception {
            try (ServerSocket listener = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
                String legacy = "<!DOCTYPE html PUBLIC \"-//FAKE//DTD Unknown//EN\" \"http://127.0.0.1:" + listener.getLocalPort()
                        + "/unknown.dtd\"><html><head><title>FAKE</title></head><body><p>FAKE-Text</p></body></html>";

                Document document = XhtmlDom.parse(legacy);

                assertThat(body(document).getTextContent()).isEqualTo("FAKE-Text");
                assertThat(document.getDoctype()).isNull();
                assertNoConnection(listener);
            }
        }

        @Test
        @DisplayName("should refuse a document type that is not at the head of the document")
        void shouldRefuseDocumentType_afterTheHead() {
            assertThatExceptionOfType(SAXException.class).isThrownBy(() -> XhtmlDom.parse(
                    "<!DOCTYPE html><!DOCTYPE html><html><head><title>FAKE</title></head><body/></html>"));
        }

        @Test
        @DisplayName("should leave an XInclude element as an ordinary element and include nothing")
        void shouldNotProcessXInclude_inTheDocument() throws Exception {
            Path marker = Files.writeString(directory.resolve("included.xml"), "<p>FAKE-Marker-Content</p>", StandardCharsets.UTF_8);

            Document document = XhtmlDom.parse(page("<xi:include xmlns:xi=\"http://www.w3.org/2001/XInclude\" href=\""
                    + marker.toUri() + "\" parse=\"text\"/><p>FAKE-Text</p>"));

            assertThat(body(document).getTextContent()).isEqualTo("FAKE-Text");
            assertThat(document.getElementsByTagNameNS("http://www.w3.org/2001/XInclude", "include").getLength()).isEqualTo(1);
        }

        /** Fails if anything connected to the listener: the accept times out when nothing did. */
        private void assertNoConnection(ServerSocket listener) throws Exception {
            listener.setSoTimeout(300);
            assertThatExceptionOfType(SocketTimeoutException.class).as("no connection to the listener")
                    .isThrownBy(() -> listener.accept().close());
        }
    }

    @Nested
    @DisplayName("taking the document type off")
    class DocumentType {

        @Test
        @DisplayName("should remove the declaration the serialiser keeps at the head")
        void shouldRemoveDeclaration_atTheHead() {
            assertThat(XhtmlDom.withoutDocumentType("<!DOCTYPE html><html/>")).isEqualTo("<html/>");
            assertThat(XhtmlDom.withoutDocumentType("<?xml version=\"1.0\"?>\n<!DOCTYPE html>\n<html/>"))
                    .isEqualTo("<?xml version=\"1.0\"?>\n\n<html/>");
            assertThat(XhtmlDom.withoutDocumentType("<html/>")).isEqualTo("<html/>");
        }

        @Test
        @DisplayName("should treat a quoted identifier as a whole, whatever it contains")
        void shouldSkipQuotedIdentifiers_whenRemovingDeclaration() {
            assertThat(XhtmlDom.withoutDocumentType("<!DOCTYPE html PUBLIC \"a>b[c\" 'd\"e>'><html/>"))
                    .isEqualTo("<html/>");
        }

        @Test
        @DisplayName("should leave a declaration with an internal subset, or one that is not closed, for the parser to refuse")
        void shouldLeaveDeclaration_withInternalSubsetOrUnclosed() {
            String subset = "<!DOCTYPE html [<!ENTITY a \"b\">]><html/>";
            String unclosed = "<!DOCTYPE html \"<html/>";

            assertThat(XhtmlDom.withoutDocumentType(subset)).isEqualTo(subset);
            assertThat(XhtmlDom.withoutDocumentType(unclosed)).isEqualTo(unclosed);
        }

        @Test
        @DisplayName("should leave a declaration alone when the text that precedes it is not prolog")
        void shouldLeaveDeclaration_afterOtherContent() {
            String inText = "<html><body>&lt;!DOCTYPE html&gt; <!DOCTYPE html></body></html>";

            assertThat(XhtmlDom.withoutDocumentType(inText)).isEqualTo(inText);
        }
    }
}
