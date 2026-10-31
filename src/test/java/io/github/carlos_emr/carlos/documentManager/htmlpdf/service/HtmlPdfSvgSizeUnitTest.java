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

import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestPdfs;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderRequest;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderResult;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An SVG prints at the size a browser gives it, whichever way it is sized: Flying Saucer takes the size of
 * an SVG in device dots, where it takes a raster image's in pixels and a viewBox-only inline
 * {@code <svg>}'s from the viewBox in dots, so the SVG with no size from its page printed a twentieth as wide.
 * A 100 x 100 CSS pixel square is 60 points on a page the pipeline zooms to 0.8.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("HTML-to-PDF SVG size Unit Tests")
class HtmlPdfSvgSizeUnitTest {

    private static final String NAMESPACE = "xmlns='http://www.w3.org/2000/svg'";
    private static final String RECT = "<rect width='100' height='100' fill='#c00'/>";
    private static final String GRADIENT = "<defs><linearGradient id='g'><stop offset='0' stop-color='#000'/>"
            + "<stop offset='1' stop-color='#000'/></linearGradient></defs><rect width='100' height='100' fill='url(#g)'/>";

    @TempDir
    Path directory;

    private HtmlPdfRenderService service;

    @BeforeEach
    void setUp() throws IOException {
        service = HtmlPdfTestServices.newService();
        write("plain.svg", "<svg " + NAMESPACE + " width='100' height='100'>" + RECT + "</svg>");
        write("boxed.svg", "<svg " + NAMESPACE + " width='100' height='100' viewBox='0 0 100 100'>" + RECT + "</svg>");
        write("viewbox-only.svg", "<svg " + NAMESPACE + " viewBox='0 0 100 100'>" + RECT + "</svg>");
        write("gradient.svg", "<svg " + NAMESPACE + " width='100' height='100'>" + GRADIENT + "</svg>");
        write("gradient-viewbox-only.svg", "<svg " + NAMESPACE + " viewBox='0 0 100 100'>" + GRADIENT + "</svg>");
    }

    private void write(String name, String content) throws IOException {
        Files.writeString(directory.resolve(name), content);
    }

    private Rectangle ink(String body) throws Exception {
        HtmlPdfRenderResult result = service.render(new HtmlPdfRenderRequest("<html><body style='margin:0'>" + body + "</body></html>",
                directory, List.of(directory), HtmlPdfRenderSource.FORM));
        return TestPdfs.darkBounds(TestPdfs.image(result.pdf(), 1));
    }

    static Stream<Arguments> sizedSvgs() {
        String inline = "<svg " + NAMESPACE + " ";
        return Stream.of(
                Arguments.of("img of an SVG that gives a size, without one on the page", "<img src='plain.svg'/>", 60),
                Arguments.of("img of an SVG that gives a size and a viewBox, without one on the page", "<img src='boxed.svg'/>", 60),
                Arguments.of("img of an SVG that gives only a viewBox, without a size on the page", "<img src='viewbox-only.svg'/>", 60),
                Arguments.of("img of an SVG with a gradient, without a size on the page", "<img src='gradient.svg'/>", 60),
                Arguments.of("img of an SVG with a gradient and only a viewBox", "<img src='gradient-viewbox-only.svg'/>", 60),
                Arguments.of("img sized by its attributes", "<img src='plain.svg' width='200' height='200'/>", 120),
                Arguments.of("img sized by its width alone", "<img src='viewbox-only.svg' width='200'/>", 120),
                Arguments.of("img sized by style", "<img src='boxed.svg' style='width: 50px; height: 50px'/>", 30),
                Arguments.of("img of a gradient SVG sized by its attributes", "<img src='gradient.svg' width='200' height='200'/>", 120),
                Arguments.of("inline svg sized by its attributes", inline + "width='100' height='100' style='display:block'>" + RECT + "</svg>", 60),
                Arguments.of("inline svg sized by style", inline + "width='100' height='100' style='display:block;width:200px;height:200px'>" + RECT + "</svg>", 120),
                Arguments.of("inline svg with a viewBox and a size on the page", inline + "viewBox='0 0 100 100' style='display:block;width:200px;height:200px'>" + RECT + "</svg>", 120),
                Arguments.of("inline svg with only a viewBox", inline + "viewBox='0 0 100 100' style='display:block'>" + RECT + "</svg>", 60),
                Arguments.of("background of an SVG", "<div style='width:300px;height:300px;background:url(plain.svg) no-repeat'></div>", 60),
                Arguments.of("background of an SVG with only a viewBox", "<div style='width:300px;height:300px;background:url(viewbox-only.svg) no-repeat'></div>", 60));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("sizedSvgs")
    @DisplayName("should print an SVG at the size a browser gives it")
    void shouldPrintSvg_atTheSizeABrowserGivesIt(String label, String body, int points) throws Exception {
        Rectangle bounds = ink(body);

        assertThat(bounds).as(label).isNotNull();
        assertThat(bounds.width).as(label + ": width in points").isBetween(points - 2, points + 1);
        assertThat(bounds.height).as(label + ": height in points").isBetween(points - 2, points + 1);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("tiledBackgrounds")
    @DisplayName("should tile a small SVG background as a browser does, at its size in pixels")
    void shouldTileSvgBackground_atItsSizeInPixels(String label, String svg, int tileCssPixels) throws Exception {
        write("tile.svg", svg);

        HtmlPdfRenderResult result = service.render(new HtmlPdfRenderRequest(
                "<html><body style='margin:0'><div style='width:400px;height:400px;background:url(tile.svg)'></div></body></html>",
                directory, List.of(directory), HtmlPdfRenderSource.FORM));

        int perSide = 400 / tileCssPixels;
        assertThat(TestPdfs.imageDraws(result.pdf())).as(label).isBetween(perSide * perSide, (perSide + 1) * (perSide + 1));
    }

    static Stream<Arguments> tiledBackgrounds() {
        return Stream.of(
                Arguments.of("a gradient 100 px square, drawn as a bitmap", "<svg " + NAMESPACE + " width='100' height='100'>" + GRADIENT + "</svg>", 100),
                Arguments.of("a gradient 80 px square with only a viewBox", "<svg " + NAMESPACE + " viewBox='0 0 80 80'>" + GRADIENT + "</svg>", 80));
    }
}
