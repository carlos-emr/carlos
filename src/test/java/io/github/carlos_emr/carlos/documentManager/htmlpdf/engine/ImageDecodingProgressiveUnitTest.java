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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link ImageDecoding#isProgressiveJpeg(Path)}: the JDK decodes a progressive JPEG
 * with native memory the heap limit does not cover, so the image planning needs to tell them apart.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("ImageDecoding progressive JPEG detection Unit Tests")
class ImageDecodingProgressiveUnitTest {

    @TempDir
    Path directory;

    private static byte[] jpeg(boolean progressive) throws IOException {
        BufferedImage image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 48; y++) {
            for (int x = 0; x < 64; x++) {
                image.setRGB(x, y, (x * 4) << 16 | (y * 5) << 8 | (x + y));
            }
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setProgressiveMode(progressive ? ImageWriteParam.MODE_DEFAULT : ImageWriteParam.MODE_DISABLED);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    @Test
    @DisplayName("should tell a progressive JPEG from a baseline one, as bytes and as a file")
    void shouldDetectProgressiveJpeg_byFrameMarker() throws IOException {
        byte[] progressive = jpeg(true);
        byte[] baseline = jpeg(false);
        Path progressiveFile = Files.write(directory.resolve("p.jpg"), progressive);
        Path baselineFile = Files.write(directory.resolve("b.jpg"), baseline);

        assertThat(ImageDecoding.isProgressiveJpeg(progressive)).isTrue();
        assertThat(ImageDecoding.isProgressiveJpeg(progressiveFile)).isTrue();
        assertThat(ImageDecoding.isProgressiveJpeg(baseline)).isFalse();
        assertThat(ImageDecoding.isProgressiveJpeg(baselineFile)).isFalse();
    }

    @Test
    @DisplayName("should find the frame header behind an EXIF segment")
    void shouldFindFrameHeader_behindExif() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("htmlpdf/images/exif-orientation-6.jpg")) {
            byte[] withExif = in.readAllBytes();

            assertThat(ImageDecoding.isProgressiveJpeg(withExif)).isFalse();
        }
    }

    @Test
    @DisplayName("should answer false for anything that is not a JPEG, and for damaged data")
    void shouldAnswerFalse_forNonJpegAndDamagedData() throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", png);
        byte[] progressive = jpeg(true);

        assertThat(ImageDecoding.isProgressiveJpeg(png.toByteArray())).isFalse();
        assertThat(ImageDecoding.isProgressiveJpeg(new byte[0])).isFalse();
        assertThat(ImageDecoding.isProgressiveJpeg("not an image at all".getBytes())).isFalse();
        // Cut before the frame header: nothing to decide on.
        assertThat(ImageDecoding.isProgressiveJpeg(Arrays.copyOf(progressive, 12))).isFalse();
        // A segment length that points past the end of the data.
        assertThat(ImageDecoding.isProgressiveJpeg(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, (byte) 0xFF,
                (byte) 0xFF, 0, 0})).isFalse();
        // A segment length below the two bytes the length itself takes would loop forever if trusted.
        assertThat(ImageDecoding.isProgressiveJpeg(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0})).isFalse();
        assertThat(ImageDecoding.isProgressiveJpeg(directory.resolve("missing.jpg"))).isFalse();
    }

    @Test
    @DisplayName("should stop at the start of the image data when there is no frame header")
    void shouldStopAtStartOfScan_withoutFrameHeader() {
        byte[] scanFirst = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xDA, 0, 4, 0, 0};

        assertThat(ImageDecoding.isProgressiveJpeg(scanFirst)).isFalse();
    }

    @Test
    @DisplayName("should reject null arguments")
    void shouldRejectNull_whenArgumentMissing() {
        assertThatNullPointerException().isThrownBy(() -> ImageDecoding.isProgressiveJpeg((Path) null));
        assertThatNullPointerException().isThrownBy(() -> ImageDecoding.isProgressiveJpeg((byte[]) null));
    }
}
