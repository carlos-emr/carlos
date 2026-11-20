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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.NodeList;
import org.xhtmlrenderer.resource.XMLResource;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link XhtmlCleanup}: one group per rule of the render pipeline's steps a, b, d to
 * g, i, n and m. Characters a source file should not contain (controls, noncharacters, lone
 * surrogates, soft hyphens) are written as numeric code points.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("XhtmlCleanup Unit Tests")
class XhtmlCleanupUnitTest {

    private static final int SOFT_HYPHEN = 0xAD;
    private static final int LRM = 0x200E;
    private static final int RLM = 0x200F;
    private static final int ALM = 0x061C;

    private static Document parse(String html) {
        return Jsoup.parse(html);
    }

    private static String chars(int... codePoints) {
        return new String(codePoints, 0, codePoints.length);
    }

    /** Parses as Flying Saucer does: namespace-aware XML, and fails the test with the parser's message. */
    private static org.w3c.dom.Document loadAsXml(String xhtml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        org.w3c.dom.Document parsed = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xhtml)));
        // Flying Saucer's own loader accepts it too (it also rejects what a plain parser tolerates).
        XMLResource.load(new StringReader(xhtml));
        return parsed;
    }

    private static List<Comment> commentsOf(Document document) {
        List<Comment> found = new ArrayList<>();
        document.traverse((node, depth) -> {
            if (node instanceof Comment comment) {
                found.add(comment);
            }
        });
        return found;
    }

    private static String textOf(org.w3c.dom.Document xml, String tag) {
        NodeList nodes = xml.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    // ---- step a -----------------------------------------------------------------------------

    @Nested
    @DisplayName("removeScriptsAndComments")
    class ScriptsAndComments {

        @Test
        @DisplayName("should remove every script element, including one inside an SVG")
        void shouldRemoveScripts_fromHeadBodyAndSvg() {
            Document document = parse("<html><head><script>head()</script></head><body><p>kept</p>"
                    + "<script src=\"x.js\"></script><svg><script>svg()</script><rect/></svg></body></html>");

            XhtmlCleanup.removeScriptsAndComments(document);

            assertThat(document.select("script")).isEmpty();
            assertThat(document.body().text()).isEqualTo("kept");
            assertThat(document.select("rect")).hasSize(1);
        }

        @Test
        @DisplayName("should remove comments wherever they are")
        void shouldRemoveComments_everywhere() {
            Document document = parse("<!-- before --><html><head><!-- head --></head><body><!--[if IE]><p>ie</p><![endif]-->"
                    + "<table><tr><!-- row --><td>cell<!-- in cell --></td></tr></table></body></html>");
            assertThat(commentsOf(document)).isNotEmpty();

            XhtmlCleanup.removeScriptsAndComments(document);

            assertThat(commentsOf(document)).isEmpty();
            assertThat(document.select("td").text()).isEqualTo("cell");
        }

        @Test
        @DisplayName("should keep the text of a style element, which is not a comment")
        void shouldKeepStyleText_whenCommentsRemoved() {
            Document document = parse("<html><head><style>p { color: red }</style></head><body><p>x</p></body></html>");

            XhtmlCleanup.removeScriptsAndComments(document);

            assertThat(document.select("style").first().data()).contains("color: red");
        }

        @Test
        @DisplayName("should remove a comment with a double hyphen so the output is valid XML")
        void shouldRemoveCommentWithDoubleHyphen_forXmlValidity() throws Exception {
            Document document = parse("<html><body><!-- bad -- comment--><div>ok</div></body></html>");

            XhtmlCleanup.removeScriptsAndComments(document);

            assertThat(commentsOf(document)).isEmpty();
            assertThat(textOf(loadAsXml(XhtmlCleanup.serialize(document)), "div")).isEqualTo("ok");
        }

        @Test
        @DisplayName("should reject a null document")
        void shouldRejectNull_whenDocumentMissing() {
            assertThatNullPointerException().isThrownBy(() -> XhtmlCleanup.removeScriptsAndComments(null));
        }
    }

    // ---- step b -----------------------------------------------------------------------------

    @Nested
    @DisplayName("removeInvisibleCharacters")
    class InvisibleCharacters {

        @Test
        @DisplayName("should remove soft hyphens from text and from value attributes")
        void shouldRemoveSoftHyphens_fromTextAndValues() {
            Document document = parse("<p>hy" + chars(SOFT_HYPHEN) + "phen</p><input value=\"va" + chars(SOFT_HYPHEN) + "lue\">");

            XhtmlCleanup.removeInvisibleCharacters(document);

            assertThat(document.select("p").text()).isEqualTo("hyphen");
            assertThat(document.select("input").attr("value")).isEqualTo("value");
        }

        @Test
        @DisplayName("should remove every invisible bidirectional formatting character")
        void shouldRemoveBidiControls_fromTextAndValues() {
            int[] controls = {LRM, RLM, ALM, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069};
            StringBuilder noisy = new StringBuilder("a");
            for (int control : controls) {
                noisy.append(chars(control)).append('b');
            }
            Document document = parse("<p>" + noisy + "</p><input value=\"" + noisy + "\">");

            XhtmlCleanup.removeInvisibleCharacters(document);

            String expected = "a" + "b".repeat(controls.length);
            assertThat(document.select("p").text()).isEqualTo(expected);
            assertThat(document.select("input").attr("value")).isEqualTo(expected);
        }

        @Test
        @DisplayName("should keep zero-width joiners, which Arabic and Indic shaping needs")
        void shouldKeepZeroWidthJoiners_whenCleaning() {
            String text = "a" + chars(0x200C) + "b" + chars(0x200D) + "c";
            Document document = parse("<p>" + text + "</p>");

            XhtmlCleanup.removeInvisibleCharacters(document);

            assertThat(document.select("p").first().wholeText()).isEqualTo(text);
        }

        @Test
        @DisplayName("should drop characters XML forbids from text and values, and the output parses")
        void shouldDropXmlInvalidCharacters_fromTextAndValues() throws Exception {
            String name = "FAKE" + chars(0x1) + "-Name" + chars(0xB);
            String value = "a" + chars(0xFFFE) + "b" + chars(0xFFFF) + "c";
            String lone = "x" + chars(0xD800) + "y" + chars(0xDC00) + "z";
            Document document = parse("<html><body><p id=\"name\">" + name + "</p><input value=\"" + value + "\">"
                    + "<p id=\"lone\">" + lone + "</p><textarea>t" + chars(0x2, 0x1F) + "t</textarea></body></html>");
            assertThat(document.select("#name").first().wholeText()).contains(chars(0x1));

            XhtmlCleanup.removeInvisibleCharacters(document);

            assertThat(document.select("#name").first().wholeText()).isEqualTo("FAKE-Name");
            assertThat(document.select("input").attr("value")).isEqualTo("abc");
            assertThat(document.select("#lone").first().wholeText()).isEqualTo("xyz");
            assertThat(document.select("textarea").first().wholeText()).isEqualTo("tt");
            org.w3c.dom.Document xml = loadAsXml(XhtmlCleanup.serialize(document));
            assertThat(textOf(xml, "p")).isEqualTo("FAKE-Name");
        }

        @Test
        @DisplayName("should keep tabs, line feeds, carriage returns and surrogate pairs")
        void shouldKeepWhitespaceAndSurrogatePairs_whenCleaning() {
            String emoji = chars(0x1F600);
            String text = "a\tb\nc" + emoji + "d";
            Document document = parse("<pre>" + text + "</pre>");

            XhtmlCleanup.removeInvisibleCharacters(document);

            assertThat(document.select("pre").first().wholeText()).isEqualTo(text);
        }

        @Test
        @DisplayName("should leave text without such characters exactly as it was")
        void shouldLeaveText_whenNothingToRemove() {
            Document document = parse("<p>caf" + chars(0xE9) + " 中文 " + chars(0x5D0, 0x5D1) + "</p>");
            String before = document.body().html();

            XhtmlCleanup.removeInvisibleCharacters(document);

            assertThat(document.body().html()).isEqualTo(before);
        }
    }

    // ---- step d -----------------------------------------------------------------------------

    @Nested
    @DisplayName("rewriteFixedPositioning")
    class FixedPositioning {

        @Test
        @DisplayName("should turn position fixed into position absolute whatever the spacing and case")
        void shouldRewriteFixed_inInlineStyles() {
            Document document = parse("<div id=\"a\" style=\"position:fixed\"></div>"
                    + "<div id=\"b\" style=\"top:0; POSITION : FIXED ; left:0\"></div>"
                    + "<div id=\"c\" style=\"position: fixed !important\"></div>");

            XhtmlCleanup.rewriteFixedPositioning(document);

            assertThat(document.select("#a").attr("style")).isEqualTo("position:absolute");
            assertThat(document.select("#b").attr("style")).isEqualTo("top:0; POSITION : absolute ; left:0");
            assertThat(document.select("#c").attr("style")).isEqualTo("position: absolute !important");
        }

        @Test
        @DisplayName("should leave other positions and look-alike properties alone")
        void shouldLeaveStyle_whenNotFixedPosition() {
            Document document = parse("<div id=\"a\" style=\"position: relative\"></div>"
                    + "<div id=\"b\" style=\"background-position: fixed\"></div>"
                    + "<div id=\"c\" style=\"position: fixedly\"></div>"
                    + "<div id=\"d\" style=\"color: red\"></div>");

            XhtmlCleanup.rewriteFixedPositioning(document);

            assertThat(document.select("#a").attr("style")).isEqualTo("position: relative");
            assertThat(document.select("#b").attr("style")).isEqualTo("background-position: fixed");
            assertThat(document.select("#c").attr("style")).isEqualTo("position: fixedly");
            assertThat(document.select("#d").attr("style")).isEqualTo("color: red");
        }
    }

    // ---- step e -----------------------------------------------------------------------------

    @Nested
    @DisplayName("mapOrderedListTypes")
    class OrderedListTypes {

        @Test
        @DisplayName("should map the five ordered-list types to list-style-type")
        void shouldMapOrderedListTypes_toListStyleType() {
            Document document = parse("<ol id=\"a\" type=\"a\"><li>x</li></ol><ol id=\"A\" type=\"A\"><li>x</li></ol>"
                    + "<ol id=\"i\" type=\"i\"><li>x</li></ol><ol id=\"I\" type=\"I\"><li>x</li></ol>"
                    + "<ol id=\"n\" type=\"1\"><li>x</li></ol>");

            XhtmlCleanup.mapOrderedListTypes(document);

            assertThat(document.select("#a").attr("style")).isEqualTo("list-style-type: lower-alpha");
            assertThat(document.select("#A").attr("style")).isEqualTo("list-style-type: upper-alpha");
            assertThat(document.select("#i").attr("style")).isEqualTo("list-style-type: lower-roman");
            assertThat(document.select("#I").attr("style")).isEqualTo("list-style-type: upper-roman");
            assertThat(document.select("#n").attr("style")).isEqualTo("list-style-type: decimal");
        }

        @Test
        @DisplayName("should keep an existing style and add the type after it")
        void shouldAppendToExistingStyle_whenOrderedListHasOne() {
            Document document = parse("<ol id=\"a\" type=\"a\" style=\"margin: 0;\"><li>x</li></ol>"
                    + "<ol id=\"b\" type=\"i\" style=\"margin: 0\"><li>x</li></ol>");

            XhtmlCleanup.mapOrderedListTypes(document);

            assertThat(document.select("#a").attr("style")).isEqualTo("margin: 0; list-style-type: lower-alpha");
            assertThat(document.select("#b").attr("style")).isEqualTo("margin: 0; list-style-type: lower-roman");
        }

        @Test
        @DisplayName("should leave a list whose inline style already sets the list style")
        void shouldLeaveList_whenInlineStyleSetsListStyle() {
            Document document = parse("<ol id=\"a\" type=\"a\" style=\"list-style-type: square\"><li>x</li></ol>"
                    + "<ol id=\"b\" type=\"a\" style=\"LIST-STYLE: none\"><li>x</li></ol>");

            XhtmlCleanup.mapOrderedListTypes(document);

            assertThat(document.select("#a").attr("style")).isEqualTo("list-style-type: square");
            assertThat(document.select("#b").attr("style")).isEqualTo("LIST-STYLE: none");
        }

        @Test
        @DisplayName("should ignore an unknown type, a list without a type and an unordered list")
        void shouldIgnoreList_whenTypeUnknownOrAbsent() {
            Document document = parse("<ol id=\"a\" type=\"x\"><li>x</li></ol><ol id=\"b\"><li>x</li></ol>"
                    + "<ul id=\"c\" type=\"a\"><li>x</li></ul><ol id=\"d\" type=\"" + "A " + "\"><li>x</li></ol>");

            XhtmlCleanup.mapOrderedListTypes(document);

            assertThat(document.select("#a, #b, #c, #d").eachAttr("style")).isEmpty();
        }
    }

    // ---- step f -----------------------------------------------------------------------------

    @Nested
    @DisplayName("clampRowspans")
    class Rowspans {

        private String rowspanOf(Document document, String id) {
            return document.getElementById(id).attr("rowspan");
        }

        @Test
        @DisplayName("should clamp a rowspan that runs past the end of its tbody")
        void shouldClampRowspan_toRowGroupEnd() {
            Document document = parse("<table><tbody>"
                    + "<tr><td id=\"first\" rowspan=\"4\">A</td><td>B</td></tr>"
                    + "<tr><td id=\"second\" rowspan=\"3\">C</td></tr>"
                    + "<tr><td id=\"third\" rowspan=\"2\">D</td></tr></tbody></table>");

            XhtmlCleanup.clampRowspans(document);

            assertThat(rowspanOf(document, "first")).isEqualTo("3");
            assertThat(rowspanOf(document, "second")).isEqualTo("2");
            assertThat(rowspanOf(document, "third")).isEqualTo("1");
        }

        @Test
        @DisplayName("should turn a rowspan of zero into the rows left in the group")
        void shouldTurnZeroRowspan_intoRowsLeft() {
            Document document = parse("<table><tr><td id=\"a\" rowspan=\"0\">A</td></tr><tr><td>B</td></tr>"
                    + "<tr><td id=\"b\" rowspan=\"0\">C</td></tr></table>");

            XhtmlCleanup.clampRowspans(document);

            assertThat(rowspanOf(document, "a")).isEqualTo("3");
            assertThat(rowspanOf(document, "b")).isEqualTo("1");
        }

        @Test
        @DisplayName("should leave a rowspan that fits, and a cell without one")
        void shouldLeaveRowspan_whenItFits() {
            Document document = parse("<table><tbody><tr><td id=\"a\" rowspan=\"2\">A</td><td id=\"b\">B</td></tr>"
                    + "<tr><td>C</td></tr></tbody></table>");

            XhtmlCleanup.clampRowspans(document);

            assertThat(rowspanOf(document, "a")).isEqualTo("2");
            assertThat(document.getElementById("b").hasAttr("rowspan")).isFalse();
        }

        @Test
        @DisplayName("should clamp within each row group, not across thead, tbody and tfoot")
        void shouldClampWithinRowGroup_forTheadTbodyTfoot() {
            Document document = parse("<table><thead><tr><th id=\"h\" rowspan=\"3\">H</th></tr><tr><th>H2</th></tr></thead>"
                    + "<tbody><tr><td id=\"b1\" rowspan=\"5\">A</td></tr><tr><td>B</td></tr></tbody>"
                    + "<tbody><tr><td id=\"b2\" rowspan=\"2\">C</td></tr></tbody>"
                    + "<tfoot><tr><td id=\"f\" rowspan=\"2\">F</td></tr></tfoot></table>");

            XhtmlCleanup.clampRowspans(document);

            assertThat(rowspanOf(document, "h")).isEqualTo("2");
            assertThat(rowspanOf(document, "b1")).isEqualTo("2");
            assertThat(rowspanOf(document, "b2")).isEqualTo("1");
            assertThat(rowspanOf(document, "f")).isEqualTo("1");
        }

        @Test
        @DisplayName("should count only the rows of the cell's own table, not of a nested one")
        void shouldIgnoreNestedTableRows_whenCounting() {
            Document document = parse("<table><tbody><tr><td id=\"outer\" rowspan=\"3\">"
                    + "<table><tbody><tr><td id=\"inner\" rowspan=\"3\">x</td></tr><tr><td>y</td></tr>"
                    + "<tr><td>z</td></tr><tr><td>w</td></tr></tbody></table></td></tr>"
                    + "<tr><td>B</td></tr></tbody></table>");

            XhtmlCleanup.clampRowspans(document);

            assertThat(rowspanOf(document, "outer")).isEqualTo("2");
            assertThat(rowspanOf(document, "inner")).isEqualTo("3");
        }

        @Test
        @DisplayName("should clamp an absurd rowspan instead of passing it to the layout")
        void shouldClampAbsurdRowspan_toRowsLeft() {
            Document document = parse("<table><tr><td id=\"a\" rowspan=\"99999999999\">A</td></tr><tr><td>B</td></tr>"
                    + "<tr><td id=\"b\" rowspan=\"99999999999999999999999999\">C</td></tr></table>");

            XhtmlCleanup.clampRowspans(document);

            assertThat(rowspanOf(document, "a")).isEqualTo("3");
            assertThat(rowspanOf(document, "b")).isEqualTo("1");
        }

        @Test
        @DisplayName("should read a rowspan the way a browser does")
        void shouldReadRowspan_likeABrowser() {
            Document document = parse("<table><tr><td id=\"a\" rowspan=\" +9px\">A</td></tr><tr><td>B</td></tr>"
                    + "<tr><td id=\"b\" rowspan=\"abc\">C</td></tr><tr><td id=\"c\" rowspan=\"-2\">D</td></tr></table>");

            XhtmlCleanup.clampRowspans(document);

            assertThat(rowspanOf(document, "a")).isEqualTo("4");
            assertThat(rowspanOf(document, "b")).isEqualTo("abc");
            assertThat(rowspanOf(document, "c")).isEqualTo("-2");
        }
    }

    @Nested
    @DisplayName("clampColspans")
    class Colspans {

        @Test
        @DisplayName("should clamp a colspan to the HTML limit of 1000")
        void shouldClampColspan_toOneThousand() {
            Document document = parse("<table><tr><td id=\"a\" colspan=\"1001\">A</td><td id=\"b\" colspan=\"99999999999\">B</td>"
                    + "<th id=\"c\" colspan=\"1000\">C</th><td id=\"d\" colspan=\"3\">D</td><td id=\"e\" colspan=\"x\">E</td></tr></table>");

            XhtmlCleanup.clampColspans(document);

            assertThat(document.getElementById("a").attr("colspan")).isEqualTo("1000");
            assertThat(document.getElementById("b").attr("colspan")).isEqualTo("1000");
            assertThat(document.getElementById("c").attr("colspan")).isEqualTo("1000");
            assertThat(document.getElementById("d").attr("colspan")).isEqualTo("3");
            assertThat(document.getElementById("e").attr("colspan")).isEqualTo("x");
        }
    }

    // ---- step g -----------------------------------------------------------------------------

    @Nested
    @DisplayName("markPaginatedTables")
    class PaginatedTables {

        private boolean marked(Document document, String id) {
            return document.getElementById(id).hasClass(XhtmlCleanup.PAGINATE_CLASS);
        }

        @Test
        @DisplayName("should mark only tables with a thead and without row spans")
        void shouldMarkOnlyTheadTablesWithoutRowspans_forPagination() {
            Document document = parse("<table id=\"plain\"><thead><tr><th>H</th></tr></thead><tbody><tr><td>A</td></tr></tbody></table>"
                    + "<table id=\"spanning\"><thead><tr><th>H</th></tr></thead><tbody><tr><td rowspan=\"2\">A</td></tr><tr><td>B</td></tr></tbody></table>"
                    + "<table id=\"nohead\"><tbody><tr><td>A</td></tr></tbody></table>"
                    + "<table id=\"headspan\"><thead><tr><th rowspan=\"2\">H</th></tr><tr><th>I</th></tr></thead></table>"
                    + "<table id=\"colspans\"><thead><tr><th colspan=\"2\">H</th></tr></thead><tbody><tr><td>A</td><td>B</td></tr></tbody></table>");

            XhtmlCleanup.markPaginatedTables(document);

            assertThat(marked(document, "plain")).isTrue();
            assertThat(marked(document, "spanning")).isFalse();
            assertThat(marked(document, "nohead")).isFalse();
            assertThat(marked(document, "headspan")).isFalse();
            assertThat(marked(document, "colspans")).isTrue();
        }

        @Test
        @DisplayName("should treat a rowspan of 1, an empty one and an invalid one as no span")
        void shouldTreatTrivialRowspans_asNoSpan() {
            Document document = parse("<table id=\"one\"><thead><tr><th rowspan=\"1\">H</th></tr></thead></table>"
                    + "<table id=\"empty\"><thead><tr><th rowspan=\"\">H</th></tr></thead></table>"
                    + "<table id=\"bad\"><thead><tr><th rowspan=\"abc\">H</th></tr></thead></table>"
                    + "<table id=\"zero\"><thead><tr><th rowspan=\"0\">H</th></tr><tr><th>I</th></tr></thead></table>");

            XhtmlCleanup.markPaginatedTables(document);

            assertThat(marked(document, "one")).isTrue();
            assertThat(marked(document, "empty")).isTrue();
            assertThat(marked(document, "bad")).isTrue();
            assertThat(marked(document, "zero")).isFalse();
        }

        @Test
        @DisplayName("should not count the spans of a nested table against the outer one")
        void shouldIgnoreNestedTableSpans_forOuterTable() {
            Document document = parse("<table id=\"outer\"><thead><tr><th>H</th></tr></thead><tbody><tr><td>"
                    + "<table id=\"inner\"><thead><tr><th>I</th></tr></thead><tbody><tr><td rowspan=\"2\">A</td></tr><tr><td>B</td></tr></tbody></table>"
                    + "</td></tr></tbody></table>");

            XhtmlCleanup.markPaginatedTables(document);

            assertThat(marked(document, "outer")).isTrue();
            assertThat(marked(document, "inner")).isFalse();
        }

        @Test
        @DisplayName("should mark a table whose only span was clamped to one row")
        void shouldMarkTable_whenClampedSpanBecomesOne() {
            Document document = parse("<table id=\"t\"><thead><tr><th>H</th></tr></thead>"
                    + "<tbody><tr><td rowspan=\"0\">A</td></tr></tbody></table>");

            XhtmlCleanup.clampRowspans(document);
            XhtmlCleanup.markPaginatedTables(document);

            assertThat(marked(document, "t")).isTrue();
        }

        @Test
        @DisplayName("should keep the classes the table already has")
        void shouldKeepExistingClasses_whenMarking() {
            Document document = parse("<table id=\"t\" class=\"grid wide\"><thead><tr><th>H</th></tr></thead></table>");

            XhtmlCleanup.markPaginatedTables(document);

            assertThat(document.getElementById("t").classNames()).containsExactlyInAnyOrder("grid", "wide", XhtmlCleanup.PAGINATE_CLASS);
        }
    }

    // ---- step i -----------------------------------------------------------------------------

    @Nested
    @DisplayName("addMissingAltText")
    class AltText {

        @Test
        @DisplayName("should add an empty alt to an image without one and keep the others")
        void shouldAddAlt_whenImageHasNone() {
            Document document = parse("<img id=\"a\" src=\"a.png\"><img id=\"b\" src=\"b.png\" alt=\"\"><img id=\"c\" src=\"c.png\" alt=\"A chart\">");

            XhtmlCleanup.addMissingAltText(document);

            assertThat(document.getElementById("a").attr("alt")).isEmpty();
            assertThat(document.getElementById("a").hasAttr("alt")).isTrue();
            assertThat(document.getElementById("b").attr("alt")).isEmpty();
            assertThat(document.getElementById("c").attr("alt")).isEqualTo("A chart");
        }
    }

    // ---- step n -----------------------------------------------------------------------------

    @Nested
    @DisplayName("markLongWords")
    class LongWords {

        private final String longWord = "x".repeat(XhtmlCleanup.LONG_WORD_CHARS);

        @Test
        @DisplayName("should mark the block that holds a word of 40 characters or more, and nothing for a shorter one")
        void shouldMarkBlock_whenWordIsLong() {
            Document document = parse("<p id=\"long\">see " + longWord + " here</p><p id=\"short\">" + longWord.substring(1) + "</p>");

            XhtmlCleanup.markLongWords(document);

            assertThat(document.getElementById("long").hasClass("carlos-pdf-wrap")).isTrue();
            assertThat(document.getElementById("short").hasClass("carlos-pdf-wrap")).isFalse();
            assertThat(XhtmlCleanup.WRAP_CLASS).isEqualTo("carlos-pdf-wrap");
        }

        @Test
        @DisplayName("should count a run of characters without white space, and start again after any white space")
        void shouldCountRuns_betweenWhiteSpace() {
            Document document = parse("<p id=\"a\">" + "x".repeat(25) + " " + "y".repeat(25) + "</p>"
                    + "<p id=\"b\">" + "x".repeat(25) + "\n" + "y".repeat(25) + "</p>"
                    + "<p id=\"c\">" + "x".repeat(25) + "\t" + "y".repeat(25) + "</p>"
                    + "<p id=\"d\">" + "x".repeat(25) + "&nbsp;" + "y".repeat(25) + "</p>");

            XhtmlCleanup.markLongWords(document);

            assertThat(document.getElementById("a").hasClass("carlos-pdf-wrap")).isFalse();
            assertThat(document.getElementById("b").hasClass("carlos-pdf-wrap")).isFalse();
            assertThat(document.getElementById("c").hasClass("carlos-pdf-wrap")).isFalse();
            // A no-break space does not allow a line break, so the run is 51 characters long.
            assertThat(document.getElementById("d").hasClass("carlos-pdf-wrap")).isTrue();
        }

        @Test
        @DisplayName("should mark the table cell, not the link, when the long word is the text of a link in a cell")
        void shouldMarkEnclosingBlock_forWordInInlineElements() {
            Document document = parse("<table><tr><td id=\"cell\"><a id=\"link\" href=\"x\"><b id=\"bold\">" + longWord
                    + "</b></a></td><td id=\"other\">short</td></tr></table>");

            XhtmlCleanup.markLongWords(document);

            assertThat(document.getElementById("cell").hasClass("carlos-pdf-wrap")).isTrue();
            assertThat(document.getElementById("link").hasClass("carlos-pdf-wrap")).isFalse();
            assertThat(document.getElementById("bold").hasClass("carlos-pdf-wrap")).isFalse();
            assertThat(document.getElementById("other").hasClass("carlos-pdf-wrap")).isFalse();
        }

        @Test
        @DisplayName("should mark the nearest block and keep other classes and attributes of it")
        void shouldAddClass_toExistingClasses() {
            Document document = parse("<div id=\"d\" class=\"note big\" style=\"color: red\"><span>" + longWord + "</span></div>");

            XhtmlCleanup.markLongWords(document);

            assertThat(document.getElementById("d").classNames()).containsExactlyInAnyOrder("note", "big", "carlos-pdf-wrap");
            assertThat(document.getElementById("d").attr("style")).isEqualTo("color: red");
        }

        @Test
        @DisplayName("should mark the body for a long word that sits directly in it")
        void shouldMarkBody_forLooseText() {
            Document document = parse("<html><body>" + longWord + "</body></html>");

            XhtmlCleanup.markLongWords(document);

            assertThat(document.body().hasClass("carlos-pdf-wrap")).isTrue();
        }

        @Test
        @DisplayName("should leave the head, style sheets, titles, scripts and printed form controls alone")
        void shouldIgnore_headStyleTitleAndControls() {
            Document document = parse("<html><head><title>" + longWord + "</title><style>.a { content: \"" + longWord
                    + "\" }</style></head><body><span id=\"control\" class=\"carlos-pdf-control carlos-pdf-text\">" + longWord
                    + "</span><p id=\"p\">fine</p></body></html>");

            XhtmlCleanup.markLongWords(document);

            assertThat(document.select(".carlos-pdf-wrap")).isEmpty();
        }

        @Test
        @DisplayName("should mark every block that has a long word, and each only once")
        void shouldMarkEachBlock_once() {
            Document document = parse("<p id=\"a\">" + longWord + " " + longWord + "</p><p id=\"b\">" + longWord + "</p><p id=\"c\">ok</p>");

            XhtmlCleanup.markLongWords(document);

            assertThat(document.select(".carlos-pdf-wrap").eachAttr("id")).containsExactly("a", "b");
            assertThat(document.getElementById("a").classNames()).containsExactly("carlos-pdf-wrap");
        }

        @Test
        @DisplayName("should reject a null document")
        void shouldRejectNull_whenDocumentMissing() {
            assertThatNullPointerException().isThrownBy(() -> XhtmlCleanup.markLongWords(null));
        }
    }

    // ---- step m -----------------------------------------------------------------------------

    @Nested
    @DisplayName("serialize")
    class Serialization {

        @Test
        @DisplayName("should keep the doctype and write XML syntax with XHTML escaping and no pretty printing")
        void shouldWriteXhtml_withDoctypeAndNoPrettyPrinting() throws Exception {
            Document document = parse("<!DOCTYPE html><html><head><title>t</title></head><body><p>a &amp; b &lt; c&nbsp;d</p>"
                    + "<br><img src=\"x.png\" alt=\"\"><div></div></body></html>");

            String xhtml = XhtmlCleanup.serialize(document);

            assertThat(xhtml).startsWith("<!DOCTYPE html>");
            assertThat(xhtml).contains("<br />", "<img src=\"x.png\" alt=\"\" />", "<div></div>");
            assertThat(xhtml).contains("a &amp; b &lt; c&#xa0;d");
            assertThat(xhtml).doesNotContain("\n");
            assertThat(textOf(loadAsXml(xhtml), "p")).isEqualTo("a & b < c" + chars(0xA0) + "d");
        }

        @Test
        @DisplayName("should write non-ASCII text as UTF-8 characters")
        void shouldWriteUtf8_whenTextIsNotAscii() throws Exception {
            String text = "caf" + chars(0xE9) + " 中文 " + chars(0x5D0, 0x5D1) + " " + chars(0x1F600);
            Document document = parse("<html><body><p>" + text + "</p></body></html>");

            String xhtml = XhtmlCleanup.serialize(document);

            assertThat(xhtml).contains(text);
            assertThat(textOf(loadAsXml(xhtml), "p")).isEqualTo(text);
        }

        @Test
        @DisplayName("should leave the output settings of the document as they were")
        void shouldRestoreOutputSettings_afterSerializing() {
            Document document = parse("<html><body><p>x</p></body></html>");
            Document.OutputSettings.Syntax syntax = document.outputSettings().syntax();
            boolean prettyPrint = document.outputSettings().prettyPrint();

            XhtmlCleanup.serialize(document);

            assertThat(document.outputSettings().syntax()).isEqualTo(syntax);
            assertThat(document.outputSettings().prettyPrint()).isEqualTo(prettyPrint);
        }

        @Test
        @DisplayName("should give the same output when serialised twice")
        void shouldBeRepeatable_whenSerializedTwice() {
            Document document = parse("<html><body><o:p>w</o:p><p xlink:href=\"#a\">x</p></body></html>");

            assertThat(XhtmlCleanup.serialize(document)).isEqualTo(XhtmlCleanup.serialize(document));
        }

        @Test
        @DisplayName("should fold an undeclared prefix into the element name, as in Word's o:p")
        void shouldFoldPrefix_intoElementName() throws Exception {
            Document document = parse("<html><body><p>a<o:p>word</o:p>b</p><x:y z:w=\"1\" v:shapes=\"s\">t</x:y></body></html>");

            String xhtml = XhtmlCleanup.serialize(document);

            org.w3c.dom.Document xml = loadAsXml(xhtml);
            assertThat(xhtml).contains("<o_p>word</o_p>", "<x_y z_w=\"1\" v_shapes=\"s\">t</x_y>");
            assertThat(textOf(xml, "p")).isEqualTo("awordb");
        }

        @Test
        @DisplayName("should rename attribute and element names XML would reject")
        void shouldRenameInvalidNames_forXml() throws Exception {
            Document document = parse("<html><body><div @click=\"x\" :class=\"y\" a\"b=\"c\" 1x=\"2\">t</div>"
                    + "<a$b>s</a$b><p data-x.y_z=\"ok\">u</p></body></html>");

            String xhtml = XhtmlCleanup.serialize(document);

            loadAsXml(xhtml);
            assertThat(xhtml).contains("_click=\"x\"", "_class=\"y\"", "a_b=\"c\"", "_1x=\"2\"", "<a_b>s</a_b>",
                    "data-x.y_z=\"ok\"");
        }

        @Test
        @DisplayName("should keep xml: attributes and real xmlns declarations")
        void shouldKeepXmlAndXmlnsAttributes_whenSerializing() throws Exception {
            Document document = parse("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:o=\"urn:office\" xml:lang=\"en\">"
                    + "<body><p xml:space=\"preserve\">a</p></body></html>");

            String xhtml = XhtmlCleanup.serialize(document);

            loadAsXml(xhtml);
            assertThat(xhtml).contains("xmlns:o=\"urn:office\"", "xml:lang=\"en\"", "xml:space=\"preserve\"");
        }

        @Test
        @DisplayName("should drop an empty xmlns declaration, which XML forbids")
        void shouldDropEmptyPrefixDeclaration_whenSerializing() throws Exception {
            Document document = parse("<html xmlns:o=\"\"><body><p>a</p></body></html>");

            String xhtml = XhtmlCleanup.serialize(document);

            loadAsXml(xhtml);
            assertThat(xhtml).doesNotContain("xmlns:o");
        }

        @Test
        @DisplayName("should declare the xlink prefix on the root when SVG markup uses it")
        void shouldDeclareXlink_whenSvgUsesIt() throws Exception {
            Document document = parse("<html><body><svg><use xlink:href=\"#a\"></use></svg></body></html>");

            String xhtml = XhtmlCleanup.serialize(document);

            org.w3c.dom.Document xml = loadAsXml(xhtml);
            assertThat(xhtml).contains("xmlns:xlink=\"http://www.w3.org/1999/xlink\"", "xlink:href=\"#a\"");
            assertThat(xml.getElementsByTagName("use").item(0).getAttributes()
                    .getNamedItemNS("http://www.w3.org/1999/xlink", "href")).isNotNull();
        }

        @Test
        @DisplayName("should drop XML-invalid characters from a style sheet, where only the last guard catches them")
        void shouldDropXmlInvalidCharacters_fromStyleSheet() throws Exception {
            Document document = parse("<html><head><style>p { content: \"a" + chars(0x4) + "b\" }</style></head><body><p>x</p></body></html>");

            String xhtml = XhtmlCleanup.serialize(document);

            loadAsXml(xhtml);
            assertThat(xhtml).contains("content: \"ab\"");
        }

        @Test
        @DisplayName("should produce loadable XHTML from the whole pipeline of rules on hostile input")
        void shouldProduceLoadableXhtml_afterAllRules() throws Exception {
            String hostile = "<!DOCTYPE html><html><head><script>x()</script><style>a{}</style><!-- -- --></head><body>"
                    + "<p style=\"position:fixed\">t" + chars(0x1, SOFT_HYPHEN, LRM) + "</p>"
                    + "<ol type=\"a\"><li>x</li></ol><img src=\"i.png\"><o:p></o:p>"
                    + "<table><thead><tr><th>H</th></tr></thead><tbody><tr><td rowspan=\"9\" colspan=\"99999999999\">c</td></tr></tbody></table>"
                    + "</body></html>";
            Document document = parse(hostile);

            XhtmlCleanup.removeScriptsAndComments(document);
            XhtmlCleanup.removeInvisibleCharacters(document);
            XhtmlCleanup.rewriteFixedPositioning(document);
            XhtmlCleanup.mapOrderedListTypes(document);
            XhtmlCleanup.clampRowspans(document);
            XhtmlCleanup.clampColspans(document);
            XhtmlCleanup.markPaginatedTables(document);
            XhtmlCleanup.addMissingAltText(document);
            String xhtml = XhtmlCleanup.serialize(document);

            org.w3c.dom.Document xml = loadAsXml(xhtml);
            assertThat(xhtml).contains("position:absolute", "list-style-type: lower-alpha", "alt=\"\"",
                    "rowspan=\"1\"", "colspan=\"1000\"", XhtmlCleanup.PAGINATE_CLASS);
            assertThat(xml.getElementsByTagName("script").getLength()).isZero();
        }

        @Test
        @DisplayName("should reject a null document")
        void shouldRejectNull_whenSerializingNothing() {
            assertThatNullPointerException().isThrownBy(() -> XhtmlCleanup.serialize(null));
        }
    }

    // ---- misc -------------------------------------------------------------------------------

    @Test
    @DisplayName("should have no instances and only static rules")
    void shouldBeUtilityClass_withPrivateConstructor() throws Exception {
        java.lang.reflect.Constructor<XhtmlCleanup> constructor = XhtmlCleanup.class.getDeclaredConstructor();

        assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())).isTrue();
        assertThat(java.lang.reflect.Modifier.isFinal(XhtmlCleanup.class.getModifiers())).isTrue();
    }
}
