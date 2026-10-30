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
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Random;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link SystemColors}: the keyword Word writes in every table border, and everything that
 * must be left alone.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("SystemColors Unit Tests")
class SystemColorsUnitTest {

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", "a", "/* x */", "a /* x */ b", "/**/", "/*/", "/*/ x", "/* x", "a /* x", "/* a */ b /* c", "/* a */ /* b */", "/* /* a */ */",
            "x /* a\nb */ y", "/*", "*/", "/* */ */", "a/**/b/**/c"})
    @DisplayName("should remove comments, and an unterminated one stays")
    void shouldRemoveComments(String text) {
        assertThat(SystemColors.withoutComments(text)).isEqualTo(Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL).matcher(text).replaceAll(""));
    }

    @Test
    @DisplayName("should remove comments as the pattern it replaces did, for random text")
    void shouldMatchTheOldPattern_forRandomText() {
        Pattern old = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
        String[] pieces = {"/*", "*/", "/", "*", "a", ":", " ", "\n", "/**/", "/*/", "**/", "color", ""};
        Random random = new Random(20261030);
        for (int i = 0; i < 20_000; i++) {
            StringBuilder text = new StringBuilder();
            for (int n = random.nextInt(12); n > 0; n--) {
                text.append(pieces[random.nextInt(pieces.length)]);
            }
            assertThat(SystemColors.withoutComments(text.toString())).as(text.toString()).isEqualTo(old.matcher(text).replaceAll(""));
        }
    }

    @Test
    @DisplayName("should read text made of comment openers with no closer in linear time")
    void shouldReadUnterminatedComments_inLinearTime() {
        // /\*.*?\*/ scanned to the end from every opener: about n squared over six steps.
        String hostile = "/* ".repeat(300_000) + ":";

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertThat(SystemColors.replaceIn(hostile)).isEqualTo(hostile);
            assertThat(SystemColors.replaceIn("/* ".repeat(300_000) + "color: windowtext")).isEqualTo("/* ".repeat(300_000) + "color: windowtext");
        });
    }

    @Test
    @DisplayName("should turn the border Word writes into one Flying Saucer draws")
    void shouldReplaceWindowText_inWordBorder() {
        assertThat(SystemColors.replaceIn("border: solid windowtext 1.0pt")).isEqualTo("border: solid #000000 1.0pt");
        assertThat(SystemColors.replaceIn("border:solid windowtext 1.0pt;border-top:none;padding:0cm 5.4pt"))
                .isEqualTo("border:solid #000000 1.0pt;border-top:none;padding:0cm 5.4pt");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"color", "background", "background-color", "border", "border-color", "border-top", "border-right",
            "border-bottom", "border-left", "border-top-color", "border-right-color", "border-bottom-color", "border-left-color",
            "outline", "outline-color", "fill", "stroke", "text-decoration-color", "column-rule", "column-rule-color"})
    @DisplayName("should replace the keyword in every colour property")
    void shouldReplaceKeyword_inColourProperties(String property) {
        assertThat(SystemColors.replaceIn(property + ": windowtext")).isEqualTo(property + ": #000000");
        assertThat(SystemColors.replaceIn(property.toUpperCase(java.util.Locale.ROOT) + ":Window")).isEqualTo(property.toUpperCase(java.util.Locale.ROOT) + ":#ffffff");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"activeborder,#b4b4b4", "activecaption,#99b4d1", "appworkspace,#ababab", "buttonface,#f0f0f0",
            "buttonhighlight,#ffffff", "buttonshadow,#a0a0a0", "buttontext,#000000", "captiontext,#000000", "graytext,#6d6d6d",
            "highlight,#3399ff", "highlighttext,#ffffff", "inactiveborder,#f4f7fc", "inactivecaption,#bfcddb",
            "inactivecaptiontext,#434e54", "infobackground,#ffffe1", "infotext,#000000", "menu,#f0f0f0", "menutext,#000000",
            "scrollbar,#c8c8c8", "threeddarkshadow,#696969", "threedface,#f0f0f0", "threedhighlight,#ffffff",
            "threedlightshadow,#e3e3e3", "threedshadow,#a0a0a0", "window,#ffffff", "windowframe,#646464", "windowtext,#000000",
            "canvas,#ffffff", "canvastext,#000000", "linktext,#0000ee", "visitedtext,#551a8b", "activetext,#ff0000",
            "buttonborder,#767676", "field,#ffffff", "fieldtext,#000000", "mark,#ffff00", "marktext,#000000"})
    @DisplayName("should map every system colour to the colour of the default Windows theme, whatever the case")
    void shouldMapEveryKeyword(String keyword, String color) {
        assertThat(SystemColors.replaceIn("color: " + keyword)).isEqualTo("color: " + color);
        assertThat(SystemColors.replaceIn("color: " + keyword.toUpperCase(java.util.Locale.ROOT))).isEqualTo("color: " + color);
    }

    @Test
    @DisplayName("should replace every keyword of one value and keep the rest of it, including !important")
    void shouldReplaceEachKeyword_inOneValue() {
        assertThat(SystemColors.replaceIn("border-color: windowtext window buttonshadow window")).isEqualTo("border-color: #000000 #ffffff #a0a0a0 #ffffff");
        assertThat(SystemColors.replaceIn("color: windowtext !important")).isEqualTo("color: #000000 !important");
        assertThat(SystemColors.replaceIn("border: /* windowtext */ solid windowtext 1pt")).isEqualTo("border: /* windowtext */ solid #000000 1pt");
        assertThat(SystemColors.replaceIn("background: window url(x.png) no-repeat")).isEqualTo("background: #ffffff url(x.png) no-repeat");
    }

    @Test
    @DisplayName("should leave alone what is not a colour: other properties, selectors, strings, URLs and comments")
    void shouldLeaveAlone_whatIsNotAColour() {
        for (String css : new String[]{
                "font: menu", "font-family: Window, serif", "content: \"windowtext\"", "content: 'window'", "background: url(window.png)",
                "background: url(\"a;windowtext\")", "background-image: url(data:image/png;base64,windowtext)",
                ".window { color: red }", "window { color: red }", "#windowtext { color: red }", "a:hover { color: red }",
                "color: red /* windowtext */", "border: 1px solid #windowtext", "color: windowtexts", "color: mywindow",
                "color: window-text", "color: window_x", "color: window1", "margin: 0 window", "/* color: windowtext */",
                "background: \"window\"", "border: 1px solid 'windowtext'", "content: \"x; color: windowtext\"",
                "content: \"it's; border: window\"", "background: url(\"x\") url('y;windowtext')"}) {
            assertThat(SystemColors.replaceIn(css)).as(css).isEqualTo(css);
        }
    }

    @Test
    @DisplayName("should find the declaration after a comment, and a keyword after a url() that holds a semicolon")
    void shouldReplace_afterCommentsAndDataUrls() {
        assertThat(SystemColors.replaceIn("p { /* note */ border: solid windowtext 1pt }")).isEqualTo("p { /* note */ border: solid #000000 1pt }");
        assertThat(SystemColors.replaceIn("p { /* a; b { */ border: windowtext }")).isEqualTo("p { /* a; b { */ border: #000000 }");
        assertThat(SystemColors.replaceIn("p { background: url(data:image/png;base64,AAAA) windowtext }"))
                .isEqualTo("p { background: url(data:image/png;base64,AAAA) #000000 }");
        assertThat(SystemColors.replaceIn("p { content: \"a;b\"; border: windowtext }")).isEqualTo("p { content: \"a;b\"; border: #000000 }");
    }

    @Test
    @DisplayName("should change only the colour declarations of a style sheet and understand nested rules and data: URLs")
    void shouldReplaceInStyleSheet_withNestedRules() {
        String css = "p.MsoNormal { margin: 0; font: menu; border: solid windowtext 1pt }\n"
                + "@media print { td { background: window url(data:image/png;base64,AAAA); color: windowtext } }\n"
                + "/* border: windowtext */ .x { fill: highlight }";

        assertThat(SystemColors.replaceIn(css)).isEqualTo("p.MsoNormal { margin: 0; font: menu; border: solid #000000 1pt }\n"
                + "@media print { td { background: #ffffff url(data:image/png;base64,AAAA); color: #000000 } }\n"
                + "/* border: windowtext */ .x { fill: #3399ff }");
    }

    @Test
    @DisplayName("should return the same string when there is nothing to replace, and cope with unbalanced input")
    void shouldReturnSame_whenNothingToReplace() {
        String css = "color: red; border: 1px solid #000";

        assertThat(SystemColors.replaceIn(css)).isSameAs(css);
        assertThat(SystemColors.replaceIn("")).isEmpty();
        assertThat(SystemColors.replaceIn("color: windowtext; content: \"unterminated")).isEqualTo("color: #000000; content: \"unterminated");
        assertThat(SystemColors.replaceIn("color: windowtext) ) ; border: window /* open")).isEqualTo("color: #000000) ) ; border: #ffffff /* open");
    }

    @Test
    @DisplayName("should replace the keywords in style attributes and style elements of a document")
    void shouldReplaceInDocument_attributesAndStyleElements() {
        Document document = Jsoup.parse("<html><head><style>td { border: solid windowtext 1pt }</style></head><body>"
                + "<table><tr><td id=\"a\" style=\"border-top: none; border: solid WindowText 1.0pt\">x</td></tr></table>"
                + "<p id=\"b\" style=\"font: menu\">y</p><p id=\"c\">z</p>"
                + "<svg><style>.s { fill: highlight }</style></svg></body></html>");

        SystemColors.replace(document);

        assertThat(document.select("head style").first().data()).isEqualTo("td { border: solid #000000 1pt }");
        assertThat(document.getElementById("a").attr("style")).isEqualTo("border-top: none; border: solid #000000 1.0pt");
        assertThat(document.getElementById("b").attr("style")).isEqualTo("font: menu");
        assertThat(document.getElementById("c").hasAttr("style")).isFalse();
        assertThat(document.select("svg style").first().text()).isEqualTo(".s { fill: #3399ff }");
    }

    @Test
    @DisplayName("should replace the keywords in style text held as data and leave a document without any untouched")
    void shouldReplaceInDataNodes_andLeaveCleanDocumentsAlone() {
        Document document = Jsoup.parse("<html><body><p>x</p></body></html>");
        document.body().appendElement("style").appendChild(new DataNode("p { color: windowtext }"));
        String before = Jsoup.parse("<html><body><p style=\"color: red\">x</p></body></html>").outerHtml();
        Document clean = Jsoup.parse("<html><body><p style=\"color: red\">x</p></body></html>");

        SystemColors.replace(document);
        SystemColors.replace(clean);

        assertThat(document.select("style").first().data()).isEqualTo("p { color: #000000 }");
        assertThat(clean.outerHtml()).isEqualTo(before);
    }

    @Test
    @DisplayName("should reject a null document")
    void shouldRejectNull_whenDocumentMissing() {
        assertThatNullPointerException().isThrownBy(() -> SystemColors.replace(null));
    }
}
