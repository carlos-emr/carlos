/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for EXIF metadata stripping methods in {@link ImageIoUtils}.
 *
 * <p>Verifies that {@link ImageIoUtils#stripExifMetadata(byte[], String)} and
 * {@link ImageIoUtils#stripExifToFile(File, String, File)} produce valid,
 * decodable images and that {@link ImageIoUtils#normalizeImageFormatName(String)}
 * maps type hints to correct ImageIO format names.</p>
 *
 * @since 2026-04-01
 * @see ImageIoUtils
 */
@DisplayName("ImageIoUtils EXIF Stripping Tests")
@Tag("unit")
@Tag("fast")
@Tag("security")
class ImageIoUtilsExifTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Creates a 4x4 BufferedImage with a solid color fill. */
    private static BufferedImage createTestImage() {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 4, 4);
        g.dispose();
        return img;
    }

    /** Encodes a BufferedImage to raw bytes in the given ImageIO format. */
    private static byte[] encodeImage(BufferedImage img, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, format, baos);
        return baos.toByteArray();
    }

    // -------------------------------------------------------------------------
    // normalizeImageFormatName
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("normalizeImageFormatName")
    class NormalizeImageFormatNameTests {

        @ParameterizedTest(name = "type ''{0}'' → ''{1}''")
        @CsvSource({
                "jpg,     jpeg",
                "jpeg,    jpeg",
                "JPG,     jpeg",
                "JPEG,    jpeg",
                "image/jpeg, jpeg",
                "image/jpg,  jpeg",
                "png,     png",
                "PNG,     png",
                "image/png, png",
                "gif,     gif",
                "GIF,     gif",
                "image/gif, gif",
        })
        @DisplayName("should map common type hints to ImageIO format names")
        void shouldMapCommonTypeHints_toImageIoFormatNames(String input, String expected) {
            assertThat(ImageIoUtils.normalizeImageFormatName(input.trim())).isEqualTo(expected.trim());
        }

        @Test
        @DisplayName("should return 'png' for null input")
        void shouldReturnPng_forNullInput() {
            assertThat(ImageIoUtils.normalizeImageFormatName(null)).isEqualTo("png");
        }

        @Test
        @DisplayName("should return 'png' for unrecognized type")
        void shouldReturnPng_forUnrecognisedType() {
            assertThat(ImageIoUtils.normalizeImageFormatName("bmp")).isEqualTo("png");
        }

        @Test
        @DisplayName("should extract extension from filename string")
        void shouldExtractExtension_fromFilenameString() {
            assertThat(ImageIoUtils.normalizeImageFormatName("photo.jpg")).isEqualTo("jpeg");
            assertThat(ImageIoUtils.normalizeImageFormatName("chart.png")).isEqualTo("png");
        }
    }

    // -------------------------------------------------------------------------
    // stripExifMetadata(byte[], String)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("stripExifMetadata(byte[], String)")
    class StripExifMetadataBytesTests {

        @ParameterizedTest(name = "format ''{0}''")
        @ValueSource(strings = {"jpeg", "png", "gif"})
        @DisplayName("should return decodable image bytes for supported formats")
        void shouldReturnDecodableImageBytes_forSupportedFormats(String format) throws IOException {
            // Given
            byte[] input = encodeImage(createTestImage(), format);

            // When
            byte[] result = ImageIoUtils.stripExifMetadata(input, format);

            // Then: result must be a valid image
            assertThat(result).isNotNull().isNotEmpty();
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result));
            assertThat(decoded).isNotNull();
            assertThat(decoded.getWidth()).isEqualTo(4);
            assertThat(decoded.getHeight()).isEqualTo(4);
        }

        @ParameterizedTest(name = "mime type ''{0}''")
        @ValueSource(strings = {"image/jpeg", "image/png", "image/gif"})
        @DisplayName("should accept MIME type strings as format hints")
        void shouldAcceptMimeTypeStrings_asFormatHints(String mimeType) throws IOException {
            // Given: create a JPEG image (works for any MIME type check)
            byte[] input = encodeImage(createTestImage(), "png");

            // When
            byte[] result = ImageIoUtils.stripExifMetadata(input, mimeType);

            // Then: result should be a decodable image
            assertThat(result).isNotNull().isNotEmpty();
            assertThat(ImageIO.read(new ByteArrayInputStream(result))).isNotNull();
        }

        @Test
        @DisplayName("should return original bytes when input is not a recognized image")
        void shouldReturnOriginalBytes_whenInputIsNotRecognisedImage() {
            // Given
            byte[] notAnImage = "not an image".getBytes();

            // When
            byte[] result = ImageIoUtils.stripExifMetadata(notAnImage, "jpeg");

            // Then: original bytes returned unchanged
            assertThat(result).isEqualTo(notAnImage);
        }

        @Test
        @DisplayName("should produce JPEG output bytes for jpeg type hint")
        void shouldProduceJpegOutputBytes_forJpegTypeHint() throws IOException {
            // Given: a PNG-encoded image (any decodable input works)
            byte[] input = encodeImage(createTestImage(), "png");

            // When: strip requested as jpeg
            byte[] result = ImageIoUtils.stripExifMetadata(input, "jpeg");

            // Then: output should start with JPEG SOI marker (FF D8)
            assertThat(result[0] & 0xFF).isEqualTo(0xFF);
            assertThat(result[1] & 0xFF).isEqualTo(0xD8);
        }

        @Test
        @DisplayName("should produce PNG output bytes for png type hint")
        void shouldProducePngOutputBytes_forPngTypeHint() throws IOException {
            // Given
            byte[] input = encodeImage(createTestImage(), "png");

            // When
            byte[] result = ImageIoUtils.stripExifMetadata(input, "png");

            // Then: PNG signature bytes: 89 50 4E 47
            assertThat(result[0] & 0xFF).isEqualTo(0x89);
            assertThat(result[1] & 0xFF).isEqualTo(0x50); // 'P'
            assertThat(result[2] & 0xFF).isEqualTo(0x4E); // 'N'
            assertThat(result[3] & 0xFF).isEqualTo(0x47); // 'G'
        }
    }

    // -------------------------------------------------------------------------
    // stripExifToFile(File, String, File)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("stripExifToFile(File, String, File)")
    class StripExifToFileTests {

        @ParameterizedTest(name = "format ''{0}''")
        @ValueSource(strings = {"jpeg", "png", "gif"})
        @DisplayName("should write decodable image file for supported formats")
        void shouldWriteDecodableImageFile_forSupportedFormats(String format) throws IOException {
            // Given
            byte[] imageBytes = encodeImage(createTestImage(), format);
            File sourceFile = tempDir.resolve("source." + format).toFile();
            Files.write(sourceFile.toPath(), imageBytes);
            File destFile = tempDir.resolve("dest." + format).toFile();

            // When
            ImageIoUtils.stripExifToFile(sourceFile, format, destFile);

            // Then
            assertThat(destFile).exists();
            byte[] resultBytes = Files.readAllBytes(destFile.toPath());
            assertThat(resultBytes).isNotEmpty();
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(resultBytes));
            assertThat(decoded).isNotNull();
            assertThat(decoded.getWidth()).isEqualTo(4);
            assertThat(decoded.getHeight()).isEqualTo(4);
        }

        @Test
        @DisplayName("should fall back to raw copy when source is not a recognized image")
        void shouldFallBackToRawCopy_whenSourceIsNotRecognisedImage() throws IOException {
            // Given
            byte[] notAnImage = "not an image".getBytes();
            File sourceFile = tempDir.resolve("not-an-image.bin").toFile();
            Files.write(sourceFile.toPath(), notAnImage);
            File destFile = tempDir.resolve("dest.bin").toFile();

            // When
            ImageIoUtils.stripExifToFile(sourceFile, "jpeg", destFile);

            // Then: destination should exist and match original content
            assertThat(destFile).exists();
            assertThat(Files.readAllBytes(destFile.toPath())).isEqualTo(notAnImage);
        }

        @Test
        @DisplayName("should accept content-type string as format hint for jpeg")
        void shouldAcceptContentTypeString_asFormatHintForJpeg() throws IOException {
            // Given
            byte[] imageBytes = encodeImage(createTestImage(), "png");
            File sourceFile = tempDir.resolve("source.png").toFile();
            Files.write(sourceFile.toPath(), imageBytes);
            File destFile = tempDir.resolve("dest.jpg").toFile();

            // When: content-type used as hint
            ImageIoUtils.stripExifToFile(sourceFile, "image/jpeg", destFile);

            // Then: result is a valid JPEG
            assertThat(destFile).exists();
            byte[] result = Files.readAllBytes(destFile.toPath());
            assertThat(result[0] & 0xFF).isEqualTo(0xFF);
            assertThat(result[1] & 0xFF).isEqualTo(0xD8);
        }
    }
}
