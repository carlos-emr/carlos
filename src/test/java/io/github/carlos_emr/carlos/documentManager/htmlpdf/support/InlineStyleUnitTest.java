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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Random;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Unit tests for {@link InlineStyle}, the scanner of the inline {@code style} attribute.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("InlineStyle Unit Tests")
class InlineStyleUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {"width: 10px", "color: red; width:1em", "WIDTH : 2px", "width:1px;height:2px"})
    @DisplayName("should find an inline width")
    void shouldFindWidth_whenStyleSetsIt(String style) {
        assertThat(InlineStyle.setsWidth(style)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "min-width: 10px", "max-width:5px", "border-width: 1px", "-webkit-width: 3", "color: red"})
    @DisplayName("should not mistake other properties for width")
    void shouldNotFindWidth_whenOtherProperty(String style) {
        assertThat(InlineStyle.setsWidth(style)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"height: 10px", "color: red; HEIGHT:1em"})
    @DisplayName("should find an inline height")
    void shouldFindHeight_whenStyleSetsIt(String style) {
        assertThat(InlineStyle.setsHeight(style)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "min-height: 10px", "line-height: 1.5", "max-height:5px", "color: red"})
    @DisplayName("should not mistake other properties for height")
    void shouldNotFindHeight_whenOtherProperty(String style) {
        assertThat(InlineStyle.setsHeight(style)).isFalse();
    }

    @Test
    @DisplayName("should find text-align and not look-alikes")
    void shouldFindTextAlign_whenStyleSetsIt() {
        assertThat(InlineStyle.setsTextAlign("text-align: right")).isTrue();
        assertThat(InlineStyle.setsTextAlign("color: red; TEXT-ALIGN :center")).isTrue();
        assertThat(InlineStyle.setsTextAlign("text-align-last: right")).isFalse();
        assertThat(InlineStyle.setsTextAlign("vertical-align: top")).isFalse();
        assertThat(InlineStyle.setsTextAlign("")).isFalse();
    }

    @Test
    @DisplayName("should find list-style and list-style-type, and not look-alikes")
    void shouldFindListStyle_whenStyleSetsIt() {
        assertThat(InlineStyle.setsListStyle("list-style-type: square")).isTrue();
        assertThat(InlineStyle.setsListStyle("margin: 0; LIST-STYLE : none")).isTrue();
        assertThat(InlineStyle.setsListStyle("list-style-position: inside")).isFalse();
        assertThat(InlineStyle.setsListStyle("")).isFalse();
    }

    @Test
    @DisplayName("should rewrite only a position declaration that says fixed")
    void shouldRewriteFixedPosition_onlyAsPositionDeclaration() {
        assertThat(InlineStyle.withFixedPositionAbsolute("position:fixed;top:0")).isEqualTo("position:absolute;top:0");
        assertThat(InlineStyle.withFixedPositionAbsolute("position: FIXED !important"))
                .isEqualTo("position: absolute !important");
        assertThat(InlineStyle.withFixedPositionAbsolute("background-position: fixed")).isEqualTo("background-position: fixed");
        assertThat(InlineStyle.withFixedPositionAbsolute("position: fixed-ish")).isEqualTo("position: fixed-ish");
    }

    @Test
    @DisplayName("should read the last value of a property, without !important")
    void shouldReadValue_ofProperty() {
        assertThat(InlineStyle.valueOf("width: 10px", "width")).hasValue("10px");
        assertThat(InlineStyle.valueOf("color: red; WIDTH : 10px !important ; height: 2px", "width")).hasValue("10px");
        assertThat(InlineStyle.valueOf("width: 1px; width: 2px", "width")).hasValue("2px");
        assertThat(InlineStyle.valueOf("min-width: 10px; line-height: 3", "width")).isEmpty();
        assertThat(InlineStyle.valueOf("width: ", "width")).isEmpty();
        assertThat(InlineStyle.valueOf("", "width")).isEmpty();
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"10px", "10px !important", "10px!important", "10px !IMPORTANT  ", "  10px  ", "!important", " !important ",
            "10px !important !important", "10px !impor", "10px important", "10px !important x", "x\n!important\n", "10px !important\u2028"})
    @DisplayName("should strip a trailing !important and the white space around the value")
    void shouldStripTrailingImportant_withTheSurroundingWhiteSpace(String value) {
        assertThat(InlineStyle.withoutImportant(value)).isEqualTo(Pattern.compile("(?i)\\s*!important\\s*$").matcher(value).replaceAll("").strip());
    }

    @Test
    @DisplayName("should strip !important as the pattern it replaces did, for random values")
    void shouldMatchTheOldPattern_forRandomValues() {
        Pattern old = Pattern.compile("(?i)\\s*!important\\s*$");
        String[] pieces = {" ", "\t", "\n", "\r\n", "\f", "!important", "!IMPORTANT", "!imp", "!", "important", "x", "10px", ""};
        Random random = new Random(20261030);
        for (int i = 0; i < 20_000; i++) {
            StringBuilder value = new StringBuilder();
            for (int n = random.nextInt(9); n > 0; n--) {
                value.append(pieces[random.nextInt(pieces.length)]);
            }
            assertThat(InlineStyle.withoutImportant(value.toString())).as(value.toString()).isEqualTo(old.matcher(value).replaceAll("").strip());
        }
    }

    @Test
    @DisplayName("should read a value of half a million spaces in linear time")
    void shouldReadValue_ofHalfAMillionSpacesInLinearTime() {
        // \s*!important\s*$ tried every space as the start of a match: about w squared over two steps.
        String style = "width: x" + " ".repeat(500_000) + "y; height: 2px";

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertThat(InlineStyle.valueOf(style, "width")).hasValue("x" + " ".repeat(500_000) + "y");
            assertThat(InlineStyle.valueOf("width:" + " ".repeat(500_000) + "x" + " ".repeat(500_000), "width")).hasValue("x");
        });
    }

    @Test
    @DisplayName("should append a declaration after the existing ones")
    void shouldAppendDeclaration_afterExistingStyle() {
        assertThat(InlineStyle.append("", "a: 1")).isEqualTo("a: 1");
        assertThat(InlineStyle.append("  ", "a: 1")).isEqualTo("a: 1");
        assertThat(InlineStyle.append("b: 2", "a: 1")).isEqualTo("b: 2; a: 1");
        assertThat(InlineStyle.append("b: 2;", "a: 1")).isEqualTo("b: 2; a: 1");
        assertThat(InlineStyle.append(" b: 2 ; ; ", "a: 1")).isEqualTo("b: 2; a: 1");
    }
}
