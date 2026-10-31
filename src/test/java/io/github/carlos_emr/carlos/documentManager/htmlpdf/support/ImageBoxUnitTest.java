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

import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestFonts;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.io.InputStream;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link ImageBox}, the placeholder for an image that cannot be shown.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("ImageBox Unit Tests")
class ImageBoxUnitTest {

    private static final Pattern FONT_SIZE = Pattern.compile("font-size: ([0-9.]+)px");

    /** The width and height declarations of a box's style, without those that fit its label to it. */
    private static String size(Element box) {
        return Arrays.stream(box.attr("style").split("; "))
                .filter(declaration -> declaration.startsWith("width:") || declaration.startsWith("height:"))
                .collect(Collectors.joining("; "));
    }

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
    @DisplayName("should let the label wrap and set it smaller when the box is too narrow for it at its usual size")
    void shouldWrapAndShrinkLabel_forNarrowBox() {
        Element box = ImageBox.replacing(img("src=\"x.png\" width=\"60\" height=\"30\""));

        // 54 pixels of room; the longest word, "unavailable]", is 12 characters: 54 / (12 x 0.6) = 7.5 pixels.
        assertThat(box.attr("style")).isEqualTo("width: 60px; height: 30px; font-size: 7.5px; white-space: normal");
    }

    @Test
    @DisplayName("should only wrap the label when its longest word fits at the usual size")
    void shouldOnlyWrapLabel_whenLongestWordFits() {
        Element box = ImageBox.replacing(img("src=\"x.png\" width=\"100\""));

        assertThat(box.attr("style")).isEqualTo("width: 100px; white-space: normal");
    }

    @Test
    @DisplayName("should keep one line, smaller, in a box too short for two")
    void shouldKeepOneLine_inBoxTooShortForTwo() {
        Element box = ImageBox.replacing(img("src=\"x.png\" width=\"60\" height=\"20\""));

        // 54 / (19 x 0.6) = 4.7 pixels, which is under the smallest worth printing (5.33): that is used.
        assertThat(box.attr("style")).isEqualTo("width: 60px; height: 20px; font-size: 5.3px");
    }

    @Test
    @DisplayName("should leave the label alone when the box is wide enough, or its width is a percentage or not known")
    void shouldLeaveLabelAlone_whenItFitsOrTheWidthIsUnknown() {
        assertThat(ImageBox.replacing(img("src=\"x.png\" width=\"200\" height=\"100\"")).attr("style"))
                .isEqualTo("width: 200px; height: 100px");
        assertThat(ImageBox.replacing(img("src=\"x.png\" width=\"50%\" height=\"30\"")).attr("style"))
                .isEqualTo("width: 50%; height: 30px");
        assertThat(ImageBox.replacing(img("src=\"x.png\" height=\"30\"")).attr("style")).isEqualTo("height: 30px");
    }

    @ParameterizedTest(name = "[{index}] a box {0} x {1} pixels")
    @CsvSource({"44, 30", "50, 30", "60, 30", "60, 20", "80, 24", "90, 40", "100, 30", "120, 20", "150, 20", "300, 60"})
    @DisplayName("should fit the label in the box, by the widths of the bundled font, down to the smallest size")
    void shouldFitLabelInBox_byTheWidthsOfTheBundledFont(int width, int height) throws Exception {
        Element box = ImageBox.replacing(img("src=\"x.png\" width=\"" + width + "\" height=\"" + height + "\""));
        String style = box.attr("style");
        Matcher fontSize = FONT_SIZE.matcher(style);
        boolean sized = style.contains("font-size");
        assertThat(fontSize.find()).as("a font size in pixels in %s", style).isEqualTo(sized);
        double size = sized ? Double.parseDouble(fontSize.group(1)) : ImageBox.LABEL_PIXELS;
        boolean wrapped = style.contains("white-space: normal");
        Font font;
        try (InputStream in = TestFonts.class.getClassLoader().getResourceAsStream(TestFonts.SANS_REGULAR)) {
            font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont((float) size);
        }
        FontRenderContext metrics = new FontRenderContext(null, true, true);

        // The widest line is the whole label, or its longest word when the label wraps between its words.
        double widest = 0;
        for (String line : wrapped ? box.text().split(" ") : new String[]{box.text()}) {
            widest = Math.max(widest, font.getStringBounds(line, metrics).getWidth());
        }

        // A box too small for even the smallest label clips it, as it always has; in any other the label fits.
        if (size > ImageBox.SMALLEST_LABEL_PIXELS + 0.05) {
            assertThat(widest).as("the widest line of the label at %.1f pixels", size)
                    .isLessThanOrEqualTo(width - ImageBox.FRAME_PIXELS);
        }
        if (wrapped) {
            assertThat(2 * size * ImageBox.LINE_HEIGHT).as("two lines at %.1f pixels", size)
                    .isLessThanOrEqualTo(height - ImageBox.FRAME_PIXELS);
        }
    }

    @Test
    @DisplayName("should take its size from the width and height attributes")
    void shouldTakeSize_fromAttributes() {
        assertThat(size(ImageBox.replacing(img("width=\"120\" height=\"80\"")))).isEqualTo("width: 120px; height: 80px");
        assertThat(size(ImageBox.replacing(img("width=\"120px\"")))).isEqualTo("width: 120px");
        assertThat(size(ImageBox.replacing(img("height=\"50%\"")))).isEqualTo("height: 50%");
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
        assertThat(size(ImageBox.replacing(img("style=\"width: 30pt; height: 1in\"")))).isEqualTo("width: 40px; height: 96px");
        assertThat(size(ImageBox.replacing(img("style=\"width: 2.54cm; height: 25.4mm\"")))).isEqualTo("width: 96px; height: 96px");
        assertThat(size(ImageBox.replacing(img("style=\"width: 2rem; height: 1.5em\"")))).isEqualTo("width: 32px; height: 24px");
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
        assertThat(size(ImageBox.replacing(img("width=\" 40 \" height=\"20PX\"")))).isEqualTo("width: 40px; height: 20px");
        assertThat(size(ImageBox.replacing(img("style=\"width: 1IN; height: 50Pt\"")))).isEqualTo("width: 96px; height: 67px");
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
