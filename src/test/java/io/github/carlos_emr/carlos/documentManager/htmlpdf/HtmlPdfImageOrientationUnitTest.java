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
package io.github.carlos_emr.carlos.documentManager.htmlpdf;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderResult;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfRenderService;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.service.HtmlPdfTestServices;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * A JPEG stored sideways, as a phone stores a portrait shot, printed through the real service and read back off the
 * page: the picture must come out the way a viewer shows it for each of the eight EXIF orientations, at the proportions
 * of what is shown, however large the file is. A solid colour cannot show a wrong turn, so the pictures here have four
 * quarters of four colours, or a scene with an obvious way up.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("HTML-to-PDF image orientation")
class HtmlPdfImageOrientationUnitTest {

    private static final HtmlPdfRenderService SERVICE = HtmlPdfTestServices.newService();

    @TempDir
    Path directory;

    /**
     * The picture's quarters, red and green above and blue and yellow below (R G / B Y), as a viewer shows them for each
     * EXIF orientation: 1 as stored, 2 mirrored left to right, 3 turned half way, 4 mirrored top to bottom, 5 mirrored
     * along the main diagonal, 6 turned a quarter clockwise, 7 mirrored along the other diagonal, 8 turned a quarter
     * anticlockwise. Written out from the definition (where the stored first row and first column end up), not
     * computed with the service's own transforms.
     */
    @ParameterizedTest(name = "[{index}] orientation {0} shows {1}")
    @CsvSource({"1, RG/BY", "2, GR/YB", "3, YB/GR", "4, BY/RG", "5, RB/GY", "6, BR/YG", "7, YG/BR", "8, GY/RB"})
    @DisplayName("should print a JPEG the way a viewer shows it, for every EXIF orientation")
    void shouldPrintJpegAsAViewerShowsIt_forEveryExifOrientation(int orientation, String expected) throws Exception {
        byte[] jpeg = TestImages.withOrientation(TestImages.encode(TestImages.quarters(64, 48), "jpeg"), orientation);

        Shown shown = print(jpeg, "128");

        assertThat(shown.quarters()).isEqualTo(expected);
        // Orientations 5 to 8 swap the sides: a 64 x 48 picture is shown 48 wide and 64 high.
        double expectedRatio = orientation >= 5 ? 64.0 / 48 : 48.0 / 64;
        assertThat((double) shown.height() / shown.width()).isCloseTo(expectedRatio, within(0.03));
    }

    @Test
    @DisplayName("should print a 12-megapixel phone photo, stored sideways, upright and in portrait proportions")
    void shouldPrintPhonePhotoUpright_atFullSize() throws Exception {
        byte[] jpeg = TestImages.withOrientation(TestImages.encode(TestImages.quarters(4032, 3024), "jpeg"), 6);

        Shown shown = print(jpeg, "200");

        assertThat(shown.quarters()).isEqualTo("BR/YG");
        assertThat((double) shown.height() / shown.width()).isCloseTo(4032.0 / 3024, within(0.03));
    }

    @Test
    @DisplayName("should print the scene of the images grid with the sun at the top right and the ground below")
    void shouldPrintTheScene_withTheSunAtTheTopRight() throws Exception {
        Shown shown = print(TestImages.phonePhoto(), "120");

        assertThat(shown.width()).isLessThan(shown.height());
        // The sky above, in the upper left; the sun in the upper right; the roof and the wall of the house; the ground.
        assertThat(shown.at(0.15, 0.12)).as("sky").satisfies(rgb -> assertThat(blue(rgb) > 200 && red(rgb) < 200).isTrue());
        assertThat(shown.at(0.78, 0.16)).as("sun").satisfies(rgb -> assertThat(red(rgb) > 200 && green(rgb) > 170 && blue(rgb) < 90).isTrue());
        assertThat(shown.at(0.5, 0.53)).as("roof").satisfies(rgb -> assertThat(red(rgb) > 80 && red(rgb) < 160 && green(rgb) < 80).isTrue());
        assertThat(shown.at(0.35, 0.76)).as("wall").satisfies(rgb -> assertThat(red(rgb) > 160 && green(rgb) < 110 && blue(rgb) < 110).isTrue());
        assertThat(shown.at(0.12, 0.92)).as("ground").satisfies(rgb -> assertThat(green(rgb) > red(rgb) + 50 && green(rgb) > blue(rgb) + 50).isTrue());
    }

    // ---- reading the page --------------------------------------------------------------------

    /** The picture as it came out on the page: its size and what colour it is at fractions of its width and height. */
    private record Shown(BufferedImage page, Rectangle box) {

        int width() {
            return box.width;
        }

        int height() {
            return box.height;
        }

        int at(double across, double down) {
            return page.getRGB(box.x + (int) (box.width * across), box.y + (int) (box.height * down));
        }

        /** The colours of the four quarters as letters, in the form R G / B Y: R, G, B and Y, or ? for anything else. */
        String quarters() {
            return letter(at(0.25, 0.25)) + letter(at(0.75, 0.25)) + "/" + letter(at(0.25, 0.75)) + letter(at(0.75, 0.75));
        }

        private static String letter(int rgb) {
            if (red(rgb) > 200 && green(rgb) < 60 && blue(rgb) < 60) {
                return "R";
            }
            if (red(rgb) < 60 && green(rgb) > 110 && green(rgb) < 210 && blue(rgb) < 60) {
                return "G";
            }
            if (red(rgb) < 60 && green(rgb) < 60 && blue(rgb) > 200) {
                return "B";
            }
            if (red(rgb) > 200 && green(rgb) > 200 && blue(rgb) < 80) {
                return "Y";
            }
            return "?";
        }
    }

    private Shown print(byte[] jpeg, String width) throws Exception {
        Path webRoot = HtmlPdfFixtures.webRoot(directory);
        TestImages.write(webRoot, "photo.jpg", jpeg);
        String html = "<html><body style=\"margin:0\"><img src=\"photo.jpg\" width=\"" + width + "\"></body></html>";

        HtmlPdfRenderResult result = SERVICE.render(HtmlPdfFixtures.request(html, webRoot));

        BufferedImage page = TestPdfs.image(result.pdf(), 1);
        Rectangle box = TestPdfs.inkBounds(page);
        assertThat(box).as("the picture is on the page").isNotNull();
        return new Shown(page, box);
    }

    private static int red(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    private static int green(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }
}
