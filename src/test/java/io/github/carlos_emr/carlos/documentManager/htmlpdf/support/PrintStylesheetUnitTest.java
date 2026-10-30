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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.xhtmlrenderer.css.parser.CSSParser;
import org.xhtmlrenderer.css.sheet.Stylesheet;
import org.xhtmlrenderer.css.sheet.StylesheetInfo;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PrintStylesheet}.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("PrintStylesheet Unit Tests")
class PrintStylesheetUnitTest {

    @Test
    @DisplayName("should begin with the pass-1 page rule and contain every rule of the spec")
    void shouldContainSpecRules_inDefaults() {
        String css = PrintStylesheet.css();

        assertThat(css).startsWith(PrintStylesheet.PASS_1_PAGE_RULE);
        assertThat(PrintStylesheet.PASS_1_PAGE_RULE)
                .isEqualTo("@page { size: 765pt 990pt !important; margin: 35.4331pt !important }");
        assertThat(css).contains(
                "html { line-height: 1.164 }",
                "html, body { margin-top: 0 !important }",
                "body > *:first-child { margin-top: 0 !important }",
                "body { word-wrap: break-word }",
                "table.carlos-pdf-paginate { -fs-table-paginate: paginate }",
                ".carlos-pdf-control { display: inline-block; vertical-align: baseline; font-size: 10pt;",
                ".carlos-pdf-text, .carlos-pdf-password, .carlos-pdf-select { border: 1px solid #000;",
                "white-space: pre-wrap; word-wrap: break-word }",
                ".carlos-pdf-textarea { border: 1px solid #000; padding: 1px 2px; white-space: pre-wrap;",
                ".carlos-pdf-checkbox, .carlos-pdf-radio { font-size: 13px }",
                ".carlos-pdf-image-unavailable { display: inline-block; border: 1px dashed #000;",
                "white-space: nowrap; overflow: hidden }",
                ".carlos-pdf-note { border: 1px solid #000; padding: 4px; margin: 0 0 6px 0; font-size: 8pt }");
    }

    @Test
    @DisplayName("should parse in Flying Saucer without a single CSS error")
    void shouldParseWithoutErrors_inFlyingSaucer() throws IOException {
        List<String> errors = new ArrayList<>();
        CSSParser parser = new CSSParser((uri, message) -> errors.add(message));

        Stylesheet stylesheet = parser.parseStylesheet("carlos-pdf-defaults", StylesheetInfo.Origin.AUTHOR,
                new StringReader(PrintStylesheet.css()));

        assertThat(errors).isEmpty();
        assertThat(stylesheet.getContents()).hasSize(12);
    }

    @Test
    @DisplayName("should be safe to put inside a style element and plain ASCII")
    void shouldBeSafeStyleText_andAscii() {
        String css = PrintStylesheet.css();

        assertThat(css).doesNotContain("</", "]]>", "<!--").isASCII();
        assertThat(PrintStylesheet.css()).isSameAs(css);
    }

    @Test
    @DisplayName("should write page rule numbers with a decimal point whatever the default locale")
    void shouldFormatPageRule_withRootLocale() {
        Locale original = Locale.getDefault();
        Locale originalFormat = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.FRENCH);
            Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY);

            assertThat(PrintStylesheet.pageRule(918.5, 1188.75, 33.6))
                    .isEqualTo("@page { size: 918.5pt 1188.75pt !important; margin: 33.6pt !important }");
        } finally {
            Locale.setDefault(original);
            Locale.setDefault(Locale.Category.FORMAT, originalFormat);
        }
    }

    @Test
    @DisplayName("should write whole numbers without a decimal part and round to four decimals")
    void shouldTrimAndRoundNumbers_inPageRule() {
        assertThat(PrintStylesheet.pageRule(765, 990, 35.4331)).isEqualTo(PrintStylesheet.PASS_1_PAGE_RULE);
        assertThat(PrintStylesheet.pageRule(1000.123456, 100.0, 0.00005))
                .isEqualTo("@page { size: 1000.1235pt 100pt !important; margin: 0.0001pt !important }");
        assertThat(PrintStylesheet.pageRule(1234567.5, 2e-6, 28.34650000))
                .isEqualTo("@page { size: 1234567.5pt 0pt !important; margin: 28.3465pt !important }");
    }

    @Test
    @DisplayName("should parse its own page rules")
    void shouldParsePageRule_inFlyingSaucer() throws IOException {
        List<String> errors = new ArrayList<>();

        new CSSParser((uri, message) -> errors.add(message)).parseStylesheet("pass-2", StylesheetInfo.Origin.AUTHOR,
                new StringReader(PrintStylesheet.pageRule(918.5, 1188.75, 33.6)));

        assertThat(errors).isEmpty();
    }
}
