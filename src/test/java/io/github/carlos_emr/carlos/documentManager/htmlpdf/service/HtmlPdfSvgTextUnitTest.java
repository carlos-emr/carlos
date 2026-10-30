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
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Right-to-left text in an inline SVG prints in the order it does in a paragraph. The pipeline puts the text of
 * the page in visual order for Flying Saucer, which has no bidirectional layout; Batik, which draws SVG text, has
 * its own, so SVG text must reach it in logical order or it is reordered twice and prints back to front.
 *
 * <p>The line is one Hebrew letter, a space, then nine of another, so that the order shows in where the space
 * falls: near the right end of the line when the order is right (the first word is on the right), near the left
 * end when it is reversed.</p>
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("HTML-to-PDF SVG text Unit Tests")
class HtmlPdfSvgTextUnitTest {

    private static final String ALEF = "א";
    private static final String BET = "ב";
    private static final String LINE = ALEF + " " + BET.repeat(9);

    @TempDir
    Path directory;

    /** Where the widest gap inside the ink falls, from 0 (the left end) to 1 (the right end). */
    private static double gapPosition(BufferedImage page, int top, int height) {
        int width = page.getWidth();
        boolean[] ink = new boolean[width];
        int first = width;
        int last = 0;
        for (int y = top; y < Math.min(page.getHeight(), top + height); y++) {
            for (int x = 0; x < width; x++) {
                int rgb = page.getRGB(x, y);
                if (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF) < 300) {
                    ink[x] = true;
                    first = Math.min(first, x);
                    last = Math.max(last, x);
                }
            }
        }
        assertThat(last).as("ink in the band").isGreaterThan(first);
        int bestStart = first;
        int bestLength = 0;
        int run = 0;
        int start = first;
        for (int x = first; x <= last; x++) {
            if (ink[x]) {
                run = 0;
            } else {
                if (run == 0) {
                    start = x;
                }
                run++;
                if (run > bestLength) {
                    bestLength = run;
                    bestStart = start;
                }
            }
        }
        return (bestStart + bestLength / 2.0 - first) / (last - first);
    }

    @Test
    @DisplayName("should print Hebrew in an inline SVG in the same order as in a paragraph")
    void shouldPrintHebrewInSvg_inTheOrderOfAParagraph() throws Exception {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='300' height='60' style='display:block'>"
                + "<text x='10' y='40' font-size='30' font-family='DejaVu Sans'>" + LINE + "</text></svg>";
        String html = "<html><body style='margin:0'>" + svg + "<p style='font-size:30px'>" + LINE + "</p></body></html>";

        byte[] pdf = HtmlPdfTestServices.newService().render(new HtmlPdfRenderRequest(html, directory, List.of(directory),
                HtmlPdfRenderSource.FORM)).pdf();
        BufferedImage page = TestPdfs.image(pdf, 1);

        // The SVG occupies the first 36 pt below the margin, the paragraph follows it.
        double inSvg = gapPosition(page, 28, 40);
        double inParagraph = gapPosition(page, 28 + 45, 30);
        assertThat(inParagraph).as("the space in the paragraph, near the right end: the first word is on the right").isGreaterThan(0.7);
        assertThat(inSvg).as("the space in the SVG, near the right end as well").isGreaterThan(0.7);
    }
}
