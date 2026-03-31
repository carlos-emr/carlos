/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ImageIoUtils} image manipulation utilities.
 *
 * <p>Uses small in-memory BufferedImage fixtures for fast execution.</p>
 *
 * @since 2026-03-31
 */
@DisplayName("ImageIoUtils Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
class ImageIoUtilsUnitTest {

    private BufferedImage createTestImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, (x * 255 / width) << 16 | (y * 255 / height) << 8);
            }
        }
        return image;
    }

    private byte[] toJpegBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    @Nested
    @DisplayName("cropCentre")
    class CropCentre {

        @Test
        @DisplayName("should crop to centre when desired size smaller than image")
        void shouldCropToCentre_whenSmallerThanImage() {
            BufferedImage image = createTestImage(200, 100);
            BufferedImage cropped = ImageIoUtils.cropCentre(image, 100, 50);
            assertThat(cropped.getWidth()).isEqualTo(100);
            assertThat(cropped.getHeight()).isEqualTo(50);
        }

        @Test
        @DisplayName("should return original when desired size equals image size")
        void shouldReturnOriginal_whenDesiredSizeEquals() {
            BufferedImage image = createTestImage(100, 100);
            BufferedImage cropped = ImageIoUtils.cropCentre(image, 100, 100);
            assertThat(cropped.getWidth()).isEqualTo(100);
            assertThat(cropped.getHeight()).isEqualTo(100);
        }

        @Test
        @DisplayName("should return original when desired size larger than image")
        void shouldReturnOriginal_whenDesiredSizeLarger() {
            BufferedImage image = createTestImage(50, 50);
            BufferedImage cropped = ImageIoUtils.cropCentre(image, 200, 200);
            assertThat(cropped.getWidth()).isEqualTo(50);
            assertThat(cropped.getHeight()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("scaleJpgSmallerProportionally")
    class ScaleJpg {

        @Test
        @DisplayName("should scale down proportionally from BufferedImage")
        void shouldScaleDown_proportionally() {
            BufferedImage image = createTestImage(200, 100);
            BufferedImage scaled = ImageIoUtils.scaleJpgSmallerProportionally(image, 100, 50);
            assertThat(scaled.getWidth()).isLessThanOrEqualTo(100);
            assertThat(scaled.getHeight()).isLessThanOrEqualTo(50);
        }

        @Test
        @DisplayName("should not upscale image smaller than max")
        void shouldNotUpscale_whenSmallerThanMax() {
            BufferedImage image = createTestImage(50, 25);
            BufferedImage scaled = ImageIoUtils.scaleJpgSmallerProportionally(image, 200, 100);
            assertThat(scaled.getWidth()).isEqualTo(50);
            assertThat(scaled.getHeight()).isEqualTo(25);
        }
    }

    @Nested
    @DisplayName("scaleJpgSmallerProportionally from bytes")
    class ScaleJpgFromBytes {

        @Test
        @DisplayName("should scale JPEG byte array")
        void shouldScaleJpegBytes() throws IOException {
            BufferedImage image = createTestImage(200, 100);
            byte[] jpegBytes = toJpegBytes(image);

            byte[] result = ImageIoUtils.scaleJpgSmallerProportionally(jpegBytes, 100, 50, 0.9f);
            assertThat(result).isNotNull();
            assertThat(result.length).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("cropSquareThenScaleSmallerProportionally")
    class CropSquareThenScale {

        @Test
        @DisplayName("should crop to square then scale")
        void shouldCropToSquare_thenScale() throws IOException {
            BufferedImage image = createTestImage(200, 100);
            byte[] jpegBytes = toJpegBytes(image);

            BufferedImage result = ImageIoUtils.cropSquareThenScaleSmallerProportionally(jpegBytes, 50, 50);
            assertThat(result).isNotNull();
            assertThat(result.getWidth()).isLessThanOrEqualTo(50);
            assertThat(result.getHeight()).isLessThanOrEqualTo(50);
        }
    }

    @Nested
    @DisplayName("writeJpg")
    class WriteJpg {

        @Test
        @DisplayName("should write JPEG to output stream")
        void shouldWriteJpeg_toOutputStream() throws IOException {
            BufferedImage image = createTestImage(100, 100);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            ImageIoUtils.writeJpg(baos, ImageIoUtils.GENERAL_GOOD_COMPRESSION, image);

            assertThat(baos.size()).isGreaterThan(0);
        }
    }
}
