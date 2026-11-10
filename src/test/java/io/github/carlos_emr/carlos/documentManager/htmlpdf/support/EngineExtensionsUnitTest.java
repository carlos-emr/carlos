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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Unit tests for {@link EngineExtensions}: Flying Saucer's {@code -fs-} CSS properties are removed from
 * the CSS a document brings, whichever way they are written, and nothing else is.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("EngineExtensions Unit Tests")
class EngineExtensionsUnitTest {

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            "-fs-table-cell-rowspan: 99999999999 | ''",
            "-fs-table-cell-rowspan: 99999999999; | ''",
            "color: red; -fs-table-cell-rowspan: 99999999999 | 'color: red; '",
            "-fs-table-cell-colspan: 2000000000; color: red | ' color: red'",
            "color: red;-fs-table-cell-rowspan:5;width: 3px | 'color: red;width: 3px'",
            "-FS-Table-Cell-RowSpan  :  5 ; width: 3px | ' width: 3px'",
            "-fs-table-cell-rowspan /* why */ : 5; width: 3px | ' width: 3px'",
            "/* before */ -fs-table-cell-rowspan: 5; width: 3px | '/* before */  width: 3px'",
            // A comment splits an identifier in CSS, but a parser that joined the halves would read the property: drop it.
            "-fs-/**/table-cell-rowspan: 5; width: 3px | ' width: 3px'",
            "-fs-page-orientation: landscape; -fs-pdf-font-embed: embed; width: 3px | '  width: 3px'",
    })
    @DisplayName("should drop the declaration of an extension property, whatever its case, spacing or position")
    void shouldDropDeclaration_ofExtensionProperty(String css, String expected) {
        assertThat(EngineExtensions.strip(css)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "\\2d fs-table-cell-rowspan: 99999999999",
            "-fs-table-cell-r\\6fwspan: 99999999999",
            "\\-fs-table-cell-rowspan: 99999999999",
            "\\2D FS-table-cell-rowspan : 99999999999; width: 3px",
    })
    @DisplayName("should drop a declaration whose property name is written with an escape, which could spell an extension property")
    void shouldDropDeclaration_whenPropertyNameHasEscape(String css) {
        String stripped = EngineExtensions.strip(css);

        assertThat(stripped).doesNotContain("99999999999").doesNotContain("rowspan");
    }

    @Test
    @DisplayName("should remove the extension properties of a style sheet, in rules, at-rules and nested blocks, and keep the rest")
    void shouldStripStyleSheet_keepingEverythingElse() {
        String css = "@page { -fs-page-orientation: landscape; margin: 5mm }\n"
                + "td { -fs-table-cell-rowspan: 99999999999; border: 1px solid #000 }\n"
                + "@media print { td.a { color: red; -fs-table-cell-colspan: 99999999999 } p { margin: 0 } }\n"
                + "a:hover { color: blue } td[title=\"x:y\"] { width: 1px }";

        String stripped = EngineExtensions.strip(css);

        assertThat(stripped).isEqualTo("@page {  margin: 5mm }\n"
                + "td {  border: 1px solid #000 }\n"
                + "@media print { td.a { color: red; } p { margin: 0 } }\n"
                + "a:hover { color: blue } td[title=\"x:y\"] { width: 1px }");
    }

    @Test
    @DisplayName("should not be fooled by a semicolon, a brace or a colon inside a string, a comment or a url()")
    void shouldNotSplitDeclaration_insideStringCommentOrUrl() {
        String css = "a { background: url(data:image/png;base64,AAAA); content: \"; -fs-table-cell-rowspan: 5; }\"; /* ; -fs-x: 1; */ "
                + "-fs-table-cell-rowspan: 99999999999; b: url('x;y') }";

        String stripped = EngineExtensions.strip(css);

        assertThat(stripped).contains("url(data:image/png;base64,AAAA)").contains("content: \"; -fs-table-cell-rowspan: 5; }\"")
                .contains("/* ; -fs-x: 1; */").contains("b: url('x;y') }").doesNotContain("99999999999");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(delimiter = '|', value = {
            "-fs-a: f(1;2); color: red | ' color: red'",
            "-fs-a: \"x;y\"; color: red | ' color: red'",
            "-fs-a: 'x}y'; color: red | ' color: red'",
            "-fs-a: url(data:image/png;base64,AAAA); color: red | ' color: red'",
            "-fs-a: 1 /* ; */ ; color: red | ' color: red'",
    })
    @DisplayName("should end a dropped declaration at its own semicolon, not at one inside parentheses, a string or a comment")
    void shouldEndDroppedDeclaration_atItsOwnSemicolon(String css, String expected) {
        assertThat(EngineExtensions.strip(css)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "background: url(a;-fs-table-cell-rowspan:5)",
            "background: url('a;-fs-table-cell-rowspan:5')",
            "width: calc(1px;-fs-table-cell-rowspan:5)",
            "content: \";-fs-table-cell-rowspan:5\"",
            "/*;-fs-table-cell-rowspan:5*/ width: 1px",
            "-fs-a b: 1; width: 1px",
            "-fs-a;b: 1",
            "-fs-a{b: 1}",
    })
    @DisplayName("should keep text that only looks like a declaration: inside parentheses, a string or a comment, or not a single name")
    void shouldKeepText_thatOnlyLooksLikeADeclaration(String css) {
        assertThat(EngineExtensions.strip(css)).isEqualTo(css);
    }

    @Test
    @DisplayName("should still see a declaration after a parenthesis that never closes")
    void shouldStripDeclaration_afterUnclosedParenthesis() {
        String stripped = EngineExtensions.strip("a { width: calc(1px ; -fs-table-cell-rowspan: 99999999999; color: red }");

        assertThat(stripped).doesNotContain("99999999999").contains("color: red");
    }

    @Test
    @DisplayName("should end a string at a newline, as CSS does, so a declaration after it is still seen")
    void shouldStripDeclaration_afterStringThatNeverCloses() {
        String stripped = EngineExtensions.strip("a { content: \"unterminated\n}\n b { -fs-table-cell-rowspan: 99999999999 }");

        assertThat(stripped).doesNotContain("99999999999").contains("b {");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"width: 3px", "font-size: 12px; background: url(a-fs-b.png)", "-webkit-box-sizing: border-box", "-moz-fs-x: 1",
            "fs-table-cell-rowspan: 5", "content: \"-fs-table-cell-rowspan: 5\"", "a:hover { color: red }", "", "-fs", "-fs-", "/* -fs-a: 1 */ width: 1px"})
    @DisplayName("should leave CSS without an extension property exactly as it is")
    void shouldLeaveCss_withoutExtensionProperty(String css) {
        assertThat(EngineExtensions.strip(css)).isSameAs(css);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"(", ")", "{", "}", ";", ":", "\"", "'", "/*", "-fs-a:", "-fs-a:(", "a:(", ";-fs-a:1;", "\\", "url(", "\"\\"})
    @DisplayName("should read hostile CSS made of one repeated construct in linear time")
    void shouldStrip_hostileCssInLinearTime(String unit) {
        // 4 MB of a single construct: a scan that starts again at every delimiter takes hours on it.
        String hostile = unit.repeat(4_000_000 / unit.length());

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            String stripped = EngineExtensions.strip(hostile);
            assertThat(stripped.length()).isLessThanOrEqualTo(hostile.length());
        });
    }

    @Test
    @DisplayName("should remove the properties from style attributes and style elements, wherever they are, and drop an attribute left empty")
    void shouldRemoveProperties_fromAttributesAndStyleElements() {
        Document document = Jsoup.parse("<html><head><style>td { -fs-table-cell-colspan: 99999999999; color: red }</style></head><body>"
                + "<table><tr><td id=\"a\" style=\"-fs-table-cell-rowspan: 99999999999\">A</td>"
                + "<td id=\"b\" style=\"width: 3px; -fs-table-cell-rowspan: 99999999999\">B</td><td id=\"c\" style=\"width: 4px\">C</td></tr></table>"
                + "<svg><style>rect { -fs-x: 1; fill: red }</style><rect style=\"-fs-y: 2\"/></svg></body></html>");

        EngineExtensions.remove(document);

        assertThat(document.getElementById("a").hasAttr("style")).isFalse();
        assertThat(document.getElementById("b").attr("style")).isEqualTo("width: 3px; ");
        assertThat(document.getElementById("c").attr("style")).isEqualTo("width: 4px");
        assertThat(document.select("style").eachText().toString() + document.select("style").eachAttr("x") + document.html())
                .doesNotContain("-fs-").contains("color: red").contains("fill: red");
        assertThat(document.select("rect").first().hasAttr("style")).isFalse();
    }

    @Test
    @DisplayName("should reject a null document")
    void shouldRejectNull_whenDocumentMissing() {
        assertThatNullPointerException().isThrownBy(() -> EngineExtensions.remove(null));
    }
}
