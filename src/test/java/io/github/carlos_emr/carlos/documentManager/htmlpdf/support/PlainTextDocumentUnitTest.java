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
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Unit tests for {@link PlainTextDocument}.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("PlainTextDocument Unit Tests")
class PlainTextDocumentUnitTest {

    private static List<String> linesOf(String bodyHtml) {
        Document result = PlainTextDocument.from(Jsoup.parse("<html><head><title>T</title></head><body>" + bodyHtml + "</body></html>"));
        return result.select("body > p").stream().map(Element::wholeText).toList();
    }

    @Test
    @DisplayName("should turn block elements into paragraphs, in reading order")
    void shouldMakeParagraphs_forBlockElements() {
        assertThat(linesOf("<h1>Title</h1><p>First <b>bold</b> and <i>italic</i> text.</p><div>Second</div>"
                + "<ul><li>One</li><li>Two</li></ul>tail"))
                .containsExactly("Title", "First bold and italic text.", "Second", "One", "Two", "tail");
    }

    @Test
    @DisplayName("should join the cells of a table row with a bar, keeping empty cells in place")
    void shouldJoinRowCells_withBar() {
        assertThat(linesOf("<table><thead><tr><th>Name</th><th>Phone</th></tr></thead><tbody>"
                + "<tr><td>FAKE-A</td><td>FAKE-B</td></tr><tr><td>FAKE-C</td><td></td><td>FAKE-D</td></tr>"
                + "<tr><td></td><td> </td></tr></tbody></table>"))
                .containsExactly("Name | Phone", "FAKE-A | FAKE-B", "FAKE-C | | FAKE-D");
    }

    @Test
    @DisplayName("should flatten blocks, breaks and nested tables inside a cell to one line")
    void shouldFlattenCellContent_toOneLine() {
        assertThat(linesOf("<table><tr><td><p>One</p><p>Two<br>Three</p></td><td>"
                + "<table><tr><td>in1</td><td>in2</td></tr><tr><td>in3</td></tr></table></td></tr></table><p>after</p>"))
                .containsExactly("One Two Three | in1 in2 in3", "after");
    }

    @Test
    @DisplayName("should start a new line at a line break outside tables")
    void shouldBreakLines_atBr() {
        assertThat(linesOf("<p>one<br>two<br/>three</p>")).containsExactly("one", "two", "three");
    }

    @Test
    @DisplayName("should collapse whitespace as a browser does")
    void shouldCollapseWhitespace_inFlowingText() {
        assertThat(linesOf("<p>  a \n\t b   <span> c </span>d  </p>")).containsExactly("a b c d");
    }

    @Test
    @DisplayName("should keep the lines of preformatted text and of a printed text area")
    void shouldKeepLines_ofPreformattedText() {
        Document source = Jsoup.parse("<body><pre>line one\n  line two\r\nline three</pre>"
                + "<input type=\"text\"><textarea>area one\narea two</textarea></body>");
        PrintFormControls.replaceControls(source);

        List<String> lines = PlainTextDocument.from(source).select("body > p").stream().map(Element::wholeText).toList();

        assertThat(lines).containsExactly("line one", "line two", "line three", "area one", "area two");
    }

    @Test
    @DisplayName("should keep the text of printed form controls")
    void shouldKeepFormControlText_inTextOnlyDocument() {
        Document source = Jsoup.parse("<body><table><tr><td>Name</td><td><input type=\"text\" value=\"FAKE-Name\"></td></tr>"
                + "<tr><td>Agree</td><td><input type=\"checkbox\" checked></td></tr></table>"
                + "<p>Plan: <select><option selected>Option A</option></select></p></body>");
        PrintFormControls.replaceControls(source);

        List<String> lines = PlainTextDocument.from(source).select("body > p").stream().map(Element::wholeText).toList();

        assertThat(lines).containsExactly("Name | FAKE-Name", "Agree | " + new String(Character.toChars(0x2612)), "Plan: Option A");
    }

    @Test
    @DisplayName("should leave out images, graphics, scripts, styles and anything else without text")
    void shouldLeaveOutNonText_elements() {
        assertThat(linesOf("<p>keep</p><img src=\"a.png\" alt=\"ALT\"><svg><text>svg text</text></svg>"
                + "<script>var x = 1</script><style>p { color: red }</style><noscript>enable js</noscript>"
                + "<canvas>fallback</canvas><object data=\"x\">alt object</object><math><mi>E</mi><mo>=</mo></math><p>also</p>"))
                .containsExactly("keep", "also");
    }

    @Test
    @DisplayName("should carry the title over and carry no classes, styles or attributes")
    void shouldKeepTitle_andNothingElse() {
        Document result = PlainTextDocument.from(Jsoup.parse("<html><head><title>FAKE title</title><style>p{}</style></head>"
                + "<body class=\"c\" style=\"x\"><p class=\"c\" style=\"y\" id=\"i\">text</p></body></html>"));

        assertThat(result.title()).isEqualTo("FAKE title");
        assertThat(result.select("style, script, link, img")).isEmpty();
        assertThat(result.select("p").first().attributes().size()).isZero();
        assertThat(result.body().attributes().size()).isZero();
        assertThat(result.documentType()).isNotNull();
    }

    @Test
    @DisplayName("should give an empty body for a document without text")
    void shouldGiveEmptyBody_whenNoText() {
        assertThat(linesOf("<div> </div><img src=\"x.png\"><p></p>")).isEmpty();
        assertThat(PlainTextDocument.from(Jsoup.parse("")).title()).isEmpty();
    }

    @Test
    @DisplayName("should cope with a very deeply nested document")
    void shouldCopeWithDeepNesting_withoutStackOverflow() {
        Document source = Jsoup.parse("<html><body></body></html>");
        Element current = source.body();
        for (int i = 0; i < 50_000; i++) {
            current = current.appendElement("div");
        }
        current.appendText("deep text");

        Document result = assertTimeoutPreemptively(Duration.ofSeconds(30), () -> PlainTextDocument.from(source));

        assertThat(result.select("p").text()).isEqualTo("deep text");
    }

    @Test
    @DisplayName("should handle a large table quickly")
    void shouldHandleLargeTable_quickly() {
        StringBuilder html = new StringBuilder("<table>");
        for (int i = 0; i < 20_000; i++) {
            html.append("<tr><td>FAKE-").append(i).append("</td><td>value</td></tr>");
        }
        html.append("</table>");
        Document source = Jsoup.parse(html.toString());

        Document result = assertTimeoutPreemptively(Duration.ofSeconds(30), () -> PlainTextDocument.from(source));

        assertThat(result.select("p")).hasSize(20_000);
        assertThat(result.select("p").last().text()).isEqualTo("FAKE-19999 | value");
    }

    @Test
    @DisplayName("should reject a null document")
    void shouldRejectNull_whenSourceMissing() {
        assertThatNullPointerException().isThrownBy(() -> PlainTextDocument.from(null));
    }
}
