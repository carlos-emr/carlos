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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link PageZoomCalculator}. Widths are the pass-1 page widths in points: a content
 * of W CSS pixels on a page with 10 mm margins divided by 0.8 is {@code W * 0.75 + 70.87} pt wide.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("PageZoomCalculator Unit Tests")
class PageZoomCalculatorUnitTest {

    @ParameterizedTest
    @ValueSource(floats = {1f, 400f, 612f, 765f, 765.5f})
    @DisplayName("should keep the default zoom for a page that fits")
    void shouldKeepDefaultZoom_forNarrowPages(float width) {
        assertThat(PageZoomCalculator.zoomFor(width)).isEqualTo(0.8f);
    }

    @Test
    @DisplayName("should compute the zoom from the pass-1 width of a 1000 px and a 1400 px page")
    void shouldComputeZoom_forWidePassOneWidths() {
        // 1000 px of content: 750 pt + 70.87 pt of margins; 1400 px: 1050 pt + 70.87 pt.
        PageGeometry thousand = PageZoomCalculator.pass2Geometry(820.87f);
        PageGeometry fourteenHundred = PageZoomCalculator.pass2Geometry(1120.87f);

        assertThat(thousand.zoom()).isCloseTo(0.7404f, within(0.0005f));
        assertThat(fourteenHundred.zoom()).isCloseTo(0.5289f, within(0.0005f));
        for (PageGeometry geometry : new PageGeometry[]{thousand, fourteenHundred}) {
            assertThat(geometry.widthPt() * geometry.zoom()).isCloseTo(612f, within(0.01f));
            assertThat(geometry.heightPt() * geometry.zoom()).isCloseTo(792f, within(0.01f));
            assertThat(geometry.marginPt() * geometry.zoom()).isCloseTo(28.3465f, within(0.01f));
        }
        assertThat(PageZoomCalculator.zoomFor(820.87f)).isEqualTo(thousand.zoom());
    }

    @Test
    @DisplayName("should keep the content width: the pass-2 page minus its margins holds what pass 1 found")
    void shouldKeepContentWidth_inPassTwoPage() {
        float passOneWidth = 900f;

        PageGeometry geometry = PageZoomCalculator.pass2Geometry(passOneWidth);

        float contentOfPassOne = passOneWidth - 2 * 28.3465f / 0.8f;
        assertThat(geometry.widthPt() - 2 * geometry.marginPt()).isCloseTo(contentOfPassOne, within(0.05f));
    }

    @Test
    @DisplayName("should shrink more as the page gets wider")
    void shouldShrinkMonotonically_asWidthGrows() {
        float previous = PageZoomCalculator.zoomFor(765.5f);
        for (float width = 766f; width < 5000f; width += 37.3f) {
            float zoom = PageZoomCalculator.zoomFor(width);
            assertThat(zoom).as("width " + width).isLessThan(previous);
            assertThat(zoom).isPositive();
            previous = zoom;
        }
    }

    @Test
    @DisplayName("should stay close to the default zoom just over the threshold")
    void shouldBeContinuous_atTheThreshold() {
        assertThat(PageZoomCalculator.zoomFor(765.6f)).isCloseTo(0.8f, within(0.001f));
    }

    @Test
    @DisplayName("should know the Letter constants")
    void shouldDefineLetterConstants_inPoints() {
        assertThat(PageZoomCalculator.LETTER_WIDTH_PT).isEqualTo(612f);
        assertThat(PageZoomCalculator.LETTER_HEIGHT_PT).isEqualTo(792f);
        assertThat(PageZoomCalculator.MARGIN_PT).isEqualTo(28.3465f);
        assertThat(PageZoomCalculator.LOW_ZOOM_WARNING).isEqualTo(0.4f);
    }
}
