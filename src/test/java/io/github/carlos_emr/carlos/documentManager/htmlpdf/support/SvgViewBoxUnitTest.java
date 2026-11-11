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
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link SvgViewBox}: an inline SVG with only a viewBox gets that viewBox's size, and no other
 * SVG is changed.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("SvgViewBox Unit Tests")
class SvgViewBoxUnitTest {

    private static Element svg(String attributes) {
        return Jsoup.parse("<html><body><svg " + attributes + "><rect width='1' height='1'/></svg></body></html>", "", Parser.htmlParser())
                .selectFirst("svg");
    }

    @ParameterizedTest(name = "[{index}] viewBox {0} gives {1} x {2}")
    @CsvSource(delimiter = '|', value = {
            "0 0 24 24 | 24 | 24",
            "0 0 100 50 | 100 | 50",
            "0,0,100,50 | 100 | 50",
            "  0   0 \t 100 , 50  | 100 | 50",
            "-10 -10 20.5 40.25 | 20.5 | 40.25",
            "0 0 1.2345 2 | 1.235 | 2",
            "0 0 10.0004 3 | 10 | 3",
            "0 0 .5 .25 | 0.5 | 0.25",
            "+0 +0 +8 +9 | 8 | 9",
            "0 0 100000 0.001 | 100000 | 0.001",
    })
    @DisplayName("should write the size of the viewBox as the width and height of an SVG that has neither")
    void shouldWriteViewBoxSize_whenSvgHasNoSize(String viewBox, String width, String height) {
        Element svg = svg("viewBox='" + viewBox + "'");

        assertThat(SvgViewBox.sizeFromViewBox(svg)).isTrue();
        assertThat(svg.attr("width")).isEqualTo(width);
        assertThat(svg.attr("height")).isEqualTo(height);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "viewBox='0 0 24 24' width='48'",
            "viewBox='0 0 24 24' height='48'",
            "viewBox='0 0 24 24' width='' height=''",
            "viewBox='0 0 24 24' width='100%'",
            "viewBox='0 0 24 24' style='width:12px'",
            "viewBox='0 0 24 24' style='HEIGHT : 12px'",
            "viewBox='0 0 24 24' style='max-width:12px'",
            "",
            "viewBox=''",
            "viewBox='0 0 24'",
            "viewBox='0 0 24 24 7'",
            "viewBox='0 0 0 24'",
            "viewBox='0 0 24 0'",
            "viewBox='0 0 -24 24'",
            "viewBox='0 0 0.0005 24'",
            "viewBox='0 0 100001 24'",
            "viewBox='0 0 24 100001'",
            "viewBox='0 0 1e2 24'",
            "viewBox='0 0 NaN 24'",
            "viewBox='0 0 Infinity 24'",
            "viewBox='0 0 0x18 24'",
            "viewBox='0 0 24px 24px'",
            "viewBox='0 0 24f 24'",
            "viewBox='0 0 24.5.5 24'",
            "viewBox='0 0 - 24 24'",
            "viewBox='0 0 1234567890123456 24'",
            "viewBox='a b c d'",
    })
    @DisplayName("should leave an SVG alone that has a size already or a viewBox it cannot read")
    void shouldLeaveSvgAlone_whenItHasSizeOrAnUnreadableViewBox(String attributes) {
        Element svg = svg(attributes);
        String before = svg.outerHtml();

        assertThat(SvgViewBox.sizeFromViewBox(svg)).isFalse();
        assertThat(svg.outerHtml()).isEqualTo(before);
    }

    @Test
    @DisplayName("should not write a size over the size a style attribute gives only to one side")
    void shouldKeepCssSize_ofTheStyleAttribute() {
        Element svg = svg("viewBox='0 0 24 24' style='color: red; width: 16px'");

        assertThat(SvgViewBox.sizeFromViewBox(svg)).isFalse();
        assertThat(svg.hasAttr("height")).isFalse();
    }

    @Test
    @DisplayName("should read an SVG that has a style attribute with no size in it")
    void shouldWriteSize_whenStyleHasNoSize() {
        Element svg = svg("viewBox='0 0 24 24' style='fill: red; display: block'");

        assertThat(SvgViewBox.sizeFromViewBox(svg)).isTrue();
        assertThat(svg.attr("width")).isEqualTo("24");
        assertThat(svg.attr("style")).isEqualTo("fill: red; display: block");
    }

    @Test
    @DisplayName("should read the four numbers of a viewBox as they are written")
    void shouldParseFourNumbers_asWritten() {
        assertThat(SvgViewBox.parse("1 2 3 4")).containsExactly(1d, 2d, 3d, 4d);
        assertThat(SvgViewBox.parse("1,2,3,4")).containsExactly(1d, 2d, 3d, 4d);
        assertThat(SvgViewBox.parse("1 2 3")).isEmpty();
    }

    @Test
    @DisplayName("should refuse a null element")
    void shouldRefuse_nullElement() {
        assertThatNullPointerException().isThrownBy(() -> SvgViewBox.sizeFromViewBox(null));
    }
}
