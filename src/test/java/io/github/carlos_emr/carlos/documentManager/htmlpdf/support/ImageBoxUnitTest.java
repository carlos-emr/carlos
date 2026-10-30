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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link ImageBox}, the placeholder for an image that cannot be shown.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("ImageBox Unit Tests")
class ImageBoxUnitTest {

    private static Element img(String attributes) {
        return Jsoup.parse("<html><body><img " + attributes + "></body></html>").selectFirst("img");
    }

    @Test
    @DisplayName("should be a span of the box class holding the unavailable-image text")
    void shouldBuildBox_withClassAndText() {
        Element box = ImageBox.replacing(img("src=\"x.png\""));

        assertThat(box.normalName()).isEqualTo("span");
        assertThat(box.hasClass("carlos-pdf-image-unavailable")).isTrue();
        assertThat(box.text()).isEqualTo("[image unavailable]");
        assertThat(box.attr("style")).isEmpty();
        assertThat(box.attributes().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("should take its size from the width and height attributes")
    void shouldTakeSize_fromAttributes() {
        assertThat(ImageBox.replacing(img("width=\"120\" height=\"80\"")).attr("style")).isEqualTo("width: 120px; height: 80px");
        assertThat(ImageBox.replacing(img("width=\"120px\"")).attr("style")).isEqualTo("width: 120px");
        assertThat(ImageBox.replacing(img("height=\"50%\"")).attr("style")).isEqualTo("height: 50%");
    }

    @Test
    @DisplayName("should prefer the inline style to the attributes, as CSS does")
    void shouldPreferInlineStyle_toAttributes() {
        Element box = ImageBox.replacing(img("width=\"120\" height=\"80\" style=\"width: 50%; height: 3em !important; color: red\""));

        assertThat(box.attr("style")).isEqualTo("width: 50%; height: 48px");
    }

    @Test
    @DisplayName("should convert absolute and relative units to pixels")
    void shouldConvertUnits_toPixels() {
        assertThat(ImageBox.replacing(img("style=\"width: 30pt; height: 1in\"")).attr("style")).isEqualTo("width: 40px; height: 96px");
        assertThat(ImageBox.replacing(img("style=\"width: 2.54cm; height: 25.4mm\"")).attr("style")).isEqualTo("width: 96px; height: 96px");
        assertThat(ImageBox.replacing(img("style=\"width: 2rem; height: 1.5em\"")).attr("style")).isEqualTo("width: 32px; height: 24px");
    }

    @Test
    @DisplayName("should limit a box to the width of a page so an absurd size cannot widen the document")
    void shouldLimitSize_toAPageWidth() {
        assertThat(ImageBox.replacing(img("width=\"5000\" height=\"99999999\"")).attr("style")).isEqualTo("width: 900px; height: 900px");
        assertThat(ImageBox.replacing(img("width=\"250%\"")).attr("style")).isEqualTo("width: 100%");
        assertThat(ImageBox.replacing(img("style=\"width: 100in\"")).attr("style")).isEqualTo("width: 900px");
    }

    @Test
    @DisplayName("should accept padding around a length and upper-case units, as HTML and CSS do")
    void shouldAcceptPaddingAndUpperCase_asHtmlAndCssDo() {
        assertThat(ImageBox.replacing(img("width=\" 40 \" height=\"20PX\"")).attr("style")).isEqualTo("width: 40px; height: 20px");
        assertThat(ImageBox.replacing(img("style=\"width: 1IN; height: 50Pt\"")).attr("style")).isEqualTo("width: 96px; height: 67px");
    }

    @ParameterizedTest
    @ValueSource(strings = {"auto", "calc(1px + 2px)", "abc", "-5", "0", "0%", "0.4", "", "10 20", "10 px", "expression(alert(1))", "url(x)", "inherit"})
    @DisplayName("should ignore a size that is not a plain positive length")
    void shouldIgnoreSizes_thatAreNotPlainPositiveLengths(String value) {
        Element box = ImageBox.replacing(img("width=\"" + value.replace("\"", "'") + "\" style=\"height: " + value + "\""));

        assertThat(box.attr("style")).isEmpty();
    }

    @Test
    @DisplayName("should not mistake min-width or max-width for the width")
    void shouldNotMistakeMinOrMaxWidth_forTheWidth() {
        assertThat(ImageBox.replacing(img("style=\"min-width: 100px; max-width: 200px; line-height: 3\"")).attr("style")).isEmpty();
    }

    @Test
    @DisplayName("should say whether an element declares its own size")
    void shouldDetectDeclaredSize_ofAnElement() {
        assertThat(ImageBox.declaresSize(img("src=\"x\""))).isFalse();
        assertThat(ImageBox.declaresSize(img("width=\"10\""))).isTrue();
        assertThat(ImageBox.declaresSize(img("height=\"10\""))).isTrue();
        assertThat(ImageBox.declaresSize(img("style=\"width: 10px\""))).isTrue();
        assertThat(ImageBox.declaresSize(img("style=\"height:10px\""))).isTrue();
        assertThat(ImageBox.declaresSize(img("style=\"max-width: 10px; line-height: 2\""))).isFalse();
    }

    @Test
    @DisplayName("should reject a null element")
    void shouldRejectNull_whenElementMissing() {
        assertThatNullPointerException().isThrownBy(() -> ImageBox.replacing(null));
        assertThatNullPointerException().isThrownBy(() -> ImageBox.declaresSize(null));
    }
}
