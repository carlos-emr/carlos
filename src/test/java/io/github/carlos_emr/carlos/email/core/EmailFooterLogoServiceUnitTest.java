/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.email.core;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import io.github.carlos_emr.carlos.commn.dao.EmailFooterLogoDao;
import io.github.carlos_emr.carlos.commn.model.EmailFooterLogo;
import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService.LogoRejectedException;
import io.github.carlos_emr.carlos.email.core.EmailFooterLogoService.Rejection;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The clinic's email footer logo (issue #3981): which uploads are accepted, that CARLOS stores its
 * own re-saved copy, and how a replaced or removed logo is kept.
 */
@Tag("unit")
@Tag("fast")
@Tag("email")
class EmailFooterLogoServiceUnitTest {

    private EmailFooterLogoDao logoDao;
    private EmailFooterLogoService service;

    @BeforeEach
    void setUp() {
        logoDao = mock(EmailFooterLogoDao.class);
        service = new EmailFooterLogoService(logoDao);
    }

    @Nested
    @DisplayName("prepare")
    class Prepare {

        @Test
        @DisplayName("should accept a PNG and store a re-saved copy with its size and hash")
        void shouldResavePng_whenUploadValid() throws IOException {
            byte[] upload = withTrailingComment(image("png", 120, 40, BufferedImage.TYPE_INT_ARGB));

            EmailFooterLogoService.PreparedLogo logo = EmailFooterLogoService.prepare(upload);

            assertThat(logo.contentType()).isEqualTo("image/png");
            assertThat(logo.width()).isEqualTo(120);
            assertThat(logo.height()).isEqualTo(40);
            assertThat(logo.sha256()).isEqualTo(DigestUtils.sha256Hex(logo.bytes()));
            // The copy is CARLOS's own: whatever followed the picture in the upload is gone.
            assertThat(logo.bytes()).isNotEqualTo(upload);
            assertThat(new String(logo.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("SECRET-COMMENT");
            BufferedImage reread = ImageIO.read(new ByteArrayInputStream(logo.bytes()));
            assertThat(reread.getWidth()).isEqualTo(120);
            assertThat(reread.getHeight()).isEqualTo(40);
        }

        @Test
        @DisplayName("should drop a PNG's text chunk and a JPEG's camera (EXIF) data from the stored copy")
        void shouldDropMetadata_whenUploadCarriesIt() throws IOException {
            byte[] png = withPngTextChunk(image("png", 60, 20, BufferedImage.TYPE_INT_RGB), "Author", "SECRET-PNG-TEXT");
            byte[] jpeg = withJpegExif(image("jpeg", 60, 20, BufferedImage.TYPE_INT_RGB), "SECRET-JPEG-EXIF");
            // The uploads really carry it, and can still be read.
            assertThat(new String(png, StandardCharsets.ISO_8859_1)).contains("tEXt", "SECRET-PNG-TEXT");
            assertThat(new String(jpeg, StandardCharsets.ISO_8859_1)).contains("Exif", "SECRET-JPEG-EXIF");

            String storedPng = new String(EmailFooterLogoService.prepare(png).bytes(), StandardCharsets.ISO_8859_1);
            String storedJpeg = new String(EmailFooterLogoService.prepare(jpeg).bytes(), StandardCharsets.ISO_8859_1);

            assertThat(storedPng).doesNotContain("tEXt", "SECRET-PNG-TEXT");
            assertThat(storedJpeg).doesNotContain("Exif", "SECRET-JPEG-EXIF");
        }

        @Test
        @DisplayName("should say so when the upload fits but CARLOS's saved copy would not")
        void shouldRefuseWithCopyReason_whenResavedCopyTooBig() throws IOException {
            // Random noise saved at low JPEG quality is small; CARLOS's copy at quality 0.9 is about
            // two and a half times bigger (about 24 KB against 60 KB here), so a 40,000-byte limit
            // takes the upload and refuses the copy.
            BufferedImage noise = new BufferedImage(EmailFooterLogoService.MAX_WIDTH, EmailFooterLogoService.MAX_HEIGHT,
                    BufferedImage.TYPE_INT_RGB);
            Random random = new Random(3981);
            for (int x = 0; x < noise.getWidth(); x++) {
                for (int y = 0; y < noise.getHeight(); y++) {
                    noise.setRGB(x, y, random.nextInt(0xFFFFFF));
                }
            }
            byte[] upload = jpeg(noise, 0.2f);
            int limit = 40_000;
            assertThat(upload.length).isLessThanOrEqualTo(limit);

            assertThatThrownBy(() -> EmailFooterLogoService.prepare(upload, limit))
                    .isInstanceOf(LogoRejectedException.class)
                    .satisfies(e -> assertThat(((LogoRejectedException) e).reason()).isEqualTo(Rejection.COPY_TOO_BIG));
        }

        @Test
        @DisplayName("should accept a JPEG at the largest size allowed")
        void shouldAcceptJpeg_atMaximumSize() throws IOException {
            EmailFooterLogoService.PreparedLogo logo = EmailFooterLogoService.prepare(
                    image("jpeg", EmailFooterLogoService.MAX_WIDTH, EmailFooterLogoService.MAX_HEIGHT, BufferedImage.TYPE_INT_RGB));

            assertThat(logo.contentType()).isEqualTo("image/jpeg");
            assertThat(logo.width()).isEqualTo(600);
            assertThat(logo.height()).isEqualTo(200);
        }

        @Test
        @DisplayName("should refuse a missing or empty file")
        void shouldRefuse_whenUploadEmpty() {
            assertRefused(null, Rejection.EMPTY);
            assertRefused(new byte[0], Rejection.EMPTY);
        }

        @Test
        @DisplayName("should refuse a file over 100 KB before reading it")
        void shouldRefuse_whenUploadTooBig() {
            assertRefused(new byte[EmailFooterLogoService.MAX_BYTES + 1], Rejection.TOO_BIG);
        }

        @Test
        @DisplayName("should refuse a picture wider or taller than allowed")
        void shouldRefuse_whenPictureTooLarge() throws IOException {
            assertRefused(image("png", EmailFooterLogoService.MAX_WIDTH + 1, 10, BufferedImage.TYPE_INT_RGB), Rejection.TOO_LARGE);
            assertRefused(image("png", 10, EmailFooterLogoService.MAX_HEIGHT + 1, BufferedImage.TYPE_INT_RGB), Rejection.TOO_LARGE);
            // A narrow strip whose header claims thousands of pixels is refused from its header.
            assertRefused(image("png", 20_000, 1, BufferedImage.TYPE_BYTE_GRAY), Rejection.TOO_LARGE);
        }

        @Test
        @DisplayName("should refuse other formats, other files and damaged pictures")
        void shouldRefuse_whenNotPngOrJpeg() throws IOException {
            assertRefused(image("gif", 20, 20, BufferedImage.TYPE_INT_RGB), Rejection.NOT_AN_IMAGE);
            assertRefused(image("bmp", 20, 20, BufferedImage.TYPE_INT_RGB), Rejection.NOT_AN_IMAGE);
            assertRefused("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8), Rejection.NOT_AN_IMAGE);
            assertRefused("%PDF-1.7 not a picture".getBytes(StandardCharsets.US_ASCII), Rejection.NOT_AN_IMAGE);
            byte[] png = image("png", 50, 50, BufferedImage.TYPE_INT_RGB);
            byte[] truncated = Arrays.copyOf(png, 40);
            assertRefused(truncated, Rejection.NOT_AN_IMAGE);
        }

        private void assertRefused(byte[] upload, Rejection reason) {
            assertThatThrownBy(() -> EmailFooterLogoService.prepare(upload))
                    .isInstanceOf(LogoRejectedException.class)
                    .satisfies(e -> assertThat(((LogoRejectedException) e).reason()).isEqualTo(reason));
        }
    }

    @Nested
    @DisplayName("replace and remove")
    class ReplaceAndRemove {

        @Test
        @DisplayName("should mark the logo in use removed and store the new one")
        void shouldKeepOldRow_whenLogoReplaced() throws IOException {
            EmailFooterLogo old = new EmailFooterLogo();
            when(logoDao.lockCurrent()).thenReturn(new ArrayList<>(List.of(old)));

            EmailFooterLogo saved = service.replace(image("png", 80, 30, BufferedImage.TYPE_INT_RGB), "999998");

            assertThat(old.getRemovedBy()).isEqualTo("999998");
            assertThat(old.getRemovedAt()).isNotNull();
            verify(logoDao).merge(same(old));
            ArgumentCaptor<EmailFooterLogo> stored = ArgumentCaptor.forClass(EmailFooterLogo.class);
            verify(logoDao).persist(stored.capture());
            assertThat(stored.getValue()).isSameAs(saved);
            assertThat(saved.getContentType()).isEqualTo("image/png");
            assertThat(saved.getWidth()).isEqualTo(80);
            assertThat(saved.getHeight()).isEqualTo(30);
            assertThat(saved.getSha256()).isEqualTo(DigestUtils.sha256Hex(saved.getImageData()));
            assertThat(saved.getUploadedBy()).isEqualTo("999998");
            assertThat(saved.getUploadedAt()).isEqualTo(old.getRemovedAt());
            assertThat(saved.getRemovedAt()).isNull();
        }

        @Test
        @DisplayName("should touch nothing when the upload is refused")
        void shouldTouchNothing_whenUploadRefused() {
            assertThatThrownBy(() -> service.replace("not a picture".getBytes(StandardCharsets.UTF_8), "999998"))
                    .isInstanceOf(LogoRejectedException.class);

            verifyNoInteractions(logoDao);
        }

        @Test
        @DisplayName("should mark every logo in use removed, and report when there was none")
        void shouldMarkRemoved_whenLogoRemoved() {
            EmailFooterLogo newer = new EmailFooterLogo();
            EmailFooterLogo older = new EmailFooterLogo();
            when(logoDao.lockCurrent()).thenReturn(List.of(newer, older), List.of());

            assertThat(service.remove("999998")).isTrue();
            assertThat(newer.getRemovedBy()).isEqualTo("999998");
            assertThat(older.getRemovedBy()).isEqualTo("999998");
            verify(logoDao).merge(same(newer));
            verify(logoDao).merge(same(older));

            assertThat(service.remove("999998")).isFalse();
            verify(logoDao, never()).persist(any());
        }
    }

    @Nested
    @DisplayName("inlineLogo")
    class InlineLogo {

        @Test
        @DisplayName("should name the logo by its hash and carry its bytes")
        void shouldBuildInlineImage_fromCurrentLogo() {
            EmailFooterLogo logo = new EmailFooterLogo();
            logo.setContentType("image/jpeg");
            logo.setImageData(new byte[] {1, 2, 3});
            logo.setSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
            logo.setUploadedAt(new Date());
            when(logoDao.findCurrent()).thenReturn(logo);

            EmailInlineImage inline = service.inlineLogo();

            assertThat(inline.contentId()).isEqualTo("clinic-logo-0123456789abcdef@carlos-emr");
            assertThat(inline.contentType()).isEqualTo("image/jpeg");
            assertThat(inline.bytes()).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("should return null when the clinic has no logo")
        void shouldReturnNull_whenNoLogo() {
            assertThat(service.inlineLogo()).isNull();
            assertThat(service.currentLogo()).isNull();
        }
    }

    private static byte[] image(String format, int width, int height, int type) throws IOException {
        BufferedImage image = new BufferedImage(width, height, type);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, Math.max(1, width / 2), Math.max(1, height / 2));
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, out)).as(format + " writer").isTrue();
        return out.toByteArray();
    }

    /** A PNG with a tEXt chunk (keyword, text) inserted straight after its IHDR chunk. */
    private static byte[] withPngTextChunk(byte[] png, String keyword, String text) {
        byte[] data = (keyword + "\0" + text).getBytes(StandardCharsets.ISO_8859_1);
        byte[] type = "tEXt".getBytes(StandardCharsets.ISO_8859_1);
        CRC32 crc = new CRC32();
        crc.update(type);
        crc.update(data);
        ByteBuffer chunk = ByteBuffer.allocate(12 + data.length)
                .putInt(data.length).put(type).put(data).putInt((int) crc.getValue());
        // Signature (8) + IHDR chunk (4 length + 4 type + 13 data + 4 CRC) = 33 bytes.
        int afterHeader = 33;
        byte[] out = new byte[png.length + chunk.capacity()];
        System.arraycopy(png, 0, out, 0, afterHeader);
        System.arraycopy(chunk.array(), 0, out, afterHeader, chunk.capacity());
        System.arraycopy(png, afterHeader, out, afterHeader + chunk.capacity(), png.length - afterHeader);
        return out;
    }

    /** A JPEG with an APP1 "Exif" segment inserted straight after its start-of-image marker. */
    private static byte[] withJpegExif(byte[] jpeg, String payload) {
        byte[] body = ("Exif\0\0" + payload).getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer segment = ByteBuffer.allocate(4 + body.length)
                .put((byte) 0xFF).put((byte) 0xE1).putShort((short) (body.length + 2)).put(body);
        byte[] out = new byte[jpeg.length + segment.capacity()];
        System.arraycopy(jpeg, 0, out, 0, 2);
        System.arraycopy(segment.array(), 0, out, 2, segment.capacity());
        System.arraycopy(jpeg, 2, out, 2 + segment.capacity(), jpeg.length - 2);
        return out;
    }

    private static byte[] jpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** A PNG with bytes after its end, as a camera or editor might leave: the re-saved copy drops them. */
    private static byte[] withTrailingComment(byte[] png) {
        byte[] comment = "SECRET-COMMENT".getBytes(StandardCharsets.ISO_8859_1);
        byte[] out = Arrays.copyOf(png, png.length + comment.length);
        System.arraycopy(comment, 0, out, png.length, comment.length);
        return out;
    }
}
