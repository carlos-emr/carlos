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

import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestImages;
import org.jsoup.Jsoup;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Unit tests for {@link SvgReferences}: nothing but fragments and {@code data:} URIs may be left in
 * an SVG, and parsing an SVG must never read a file or open a connection.
 */
@Tag("unit")
@Tag("htmlpdf")
@Tag("security")
@DisplayName("SvgReferences Unit Tests")
class SvgReferencesUnitTest {

    @TempDir
    Path directory;

    private static String text(SvgReferences.Cleaned cleaned) {
        return new String(cleaned.svg(), StandardCharsets.UTF_8);
    }

    private static Optional<SvgReferences.Cleaned> clean(String svg) {
        return SvgReferences.clean(svg.getBytes(StandardCharsets.UTF_8));
    }

    private static final String HEAD = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" "
            + "width=\"40\" height=\"20\">";

    /** One-pixel pictures that can be read: the header of an inline image is read, so a fragment of one no longer stays. */
    private static final String PNG_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4XmNgYGAAAAAEAAEP0q3kAAAAAElFTkSuQmCC";
    private static final String JPEG_BASE64 = "/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD5/ooooA//2Q==";
    private static final String GIF_BASE64 = "R0lGODlhAQABAPAAAAAAAAAAACwAAAAAAQABAEAIBAABBAQAOw==";
    private static final String PNG_URI = "data:image/png;base64," + PNG_BASE64;

    // ---- references -------------------------------------------------------------------------

    @Test
    @DisplayName("should remove an image element that loads from outside and keep the drawing around it")
    void shouldRemoveExternalImage_keepingRest() {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<rect id=\"r\" width=\"40\" height=\"20\" fill=\"#c00\"/>"
                + "<image xlink:href=\"http://127.0.0.1:1/probe.png\" width=\"10\" height=\"10\"/>"
                + "<image href=\"file:///etc/passwd\"/><image xlink:href=\"../secret.png\"/></svg>").orElseThrow();

        assertThat(cleaned.removedReferences()).isTrue();
        assertThat(text(cleaned)).contains("<rect").contains("fill=\"#c00\"")
                .doesNotContain("<image").doesNotContain("127.0.0.1").doesNotContain("/etc/passwd").doesNotContain("secret");
    }

    @Test
    @DisplayName("should keep fragment and data: references, and say nothing was removed")
    void shouldKeepLocalReferences_andReportNothingRemoved() {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<defs><circle id=\"c\" r=\"3\"/></defs><use xlink:href=\"#c\"/>"
                + "<use href=\"#c\"/><image xlink:href=\"" + PNG_URI + "\" width=\"1\" height=\"1\"/>"
                + "<a xlink:href=\"#c\"><rect width=\"2\" height=\"2\"/></a></svg>").orElseThrow();

        assertThat(cleaned.removedReferences()).isFalse();
        assertThat(text(cleaned)).contains("<use").contains("xlink:href=\"#c\"").contains("<image").contains("data:image/png");
    }

    /** An SVG that draws a file from anywhere: the document a {@code data:} URI must not be allowed to hold. */
    private static final String NESTED_SVG = "<svg xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink' width='30' height='30'>"
            + "<g id='a'><image xlink:href='file:///secret.png' width='30' height='30'/></g></svg>";

    private static String base64(String text) {
        return base64(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"image/svg+xml", "image/svg+xml;charset=utf-8", "image/png", "text/plain", "text/html", "application/xml", ""})
    @DisplayName("should remove a data: reference to an SVG whatever media type it declares, because Batik reads the content")
    void shouldRemoveDataUriOfSvg_whateverTheMediaType(String mediaType) {
        // Batik loads the nested document, and a use of one of its elements draws what that element names.
        String uri = "data:" + mediaType + ";base64," + base64(NESTED_SVG);
        for (String name : List.of("image", "use", "feImage")) {
            SvgReferences.Cleaned cleaned = clean(HEAD + "<rect width=\"2\" height=\"2\"/><" + name + " xlink:href=\"" + uri + "#a\"/></svg>")
                    .orElseThrow();

            assertThat(cleaned.removedReferences()).as(name).isTrue();
            assertThat(text(cleaned)).as(name).doesNotContain("<" + name).doesNotContain("data:").contains("<rect");
        }
    }

    @Test
    @DisplayName("should remove the reference attribute of an element that does not exist to load, when it holds an SVG data: URI")
    void shouldRemoveAttribute_whenDataUriOfSvgIsOnAnotherElement() {
        String uri = "data:image/svg+xml;base64," + base64(NESTED_SVG);
        SvgReferences.Cleaned cleaned = clean(HEAD + "<defs><linearGradient id=\"g\" xlink:href=\"" + uri + "#a\"/></defs>"
                + "<rect width=\"2\" height=\"2\" fill=\"url(#g)\"/></svg>").orElseThrow();

        assertThat(cleaned.removedReferences()).isTrue();
        assertThat(text(cleaned)).contains("<linearGradient").doesNotContain("data:").contains("fill=\"url(#g)\"");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {PNG_URI, "DATA:IMAGE/PNG;BASE64," + PNG_BASE64, "data:image/jpeg;base64," + JPEG_BASE64,
            "data:image/gif;base64," + GIF_BASE64, "data:image/gif;charset=x;base64," + GIF_BASE64, " data:image/png;base64," + PNG_BASE64 + " ",
            "data:;base64," + PNG_BASE64})
    @DisplayName("should keep an inline picture: base64, and a payload that begins like a PNG, a JPEG or a GIF")
    void shouldKeepDataUri_ofRasterImage(String uri) {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<image xlink:href=\"" + uri + "\" width=\"1\" height=\"1\"/></svg>").orElseThrow();

        assertThat(cleaned.removedReferences()).isFalse();
        assertThat(text(cleaned)).contains("<image").containsIgnoringCase("base64,");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"data:image/png,%89PNG%0D%0A", "data:image/png;base64,PHN2ZyB4bWxucz0=", "data:image/png;base64,", "data:image/png;base64",
            "data:image/png;base64;x,iVBORw0KGgoAAAA", "data:image/png;base64, iVBORw0KGgoAAAA", "data:", "data:,iVBORw0KGgo", "data:image/png;base64,ivborw0kggo"})
    @DisplayName("should remove a data: reference that is not base64, or whose payload is not the start of a PNG, a JPEG or a GIF")
    void shouldRemoveDataUri_thatIsNotARasterImage(String uri) {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<rect width=\"2\" height=\"2\"/><image xlink:href=\"" + uri + "\" width=\"1\" height=\"1\"/></svg>")
                .orElseThrow();

        assertThat(cleaned.removedReferences()).isTrue();
        assertThat(text(cleaned)).doesNotContain("<image").contains("<rect");
    }

    /** A PNG that is its signature and header only, declaring the given size: all an image-bomb needs to be small. */
    private static byte[] pngDeclaring(int width, int height) {
        ByteBuffer header = ByteBuffer.allocate(13).putInt(width).putInt(height).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        chunk(out, "IHDR", header.array());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(name);
        out.writeBytes(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    /** A real one-pixel JPEG whose frame header declares the given size. */
    private static byte[] jpegDeclaring(int width, int height) {
        byte[] jpeg = TestImages.jpeg(1, 1, false);
        for (int i = 0; i + 8 < jpeg.length; i++) {
            if ((jpeg[i] & 0xFF) == 0xFF && (jpeg[i + 1] & 0xFF) == 0xC0) {
                jpeg[i + 5] = (byte) (height >> 8);
                jpeg[i + 6] = (byte) height;
                jpeg[i + 7] = (byte) (width >> 8);
                jpeg[i + 8] = (byte) width;
                return jpeg;
            }
        }
        throw new AssertionError("no baseline frame header in the test JPEG");
    }

    /** A real one-pixel GIF whose screen and image descriptor declare the given size (at most 65535). */
    private static byte[] gifDeclaring(int width, int height) {
        byte[] gif = Base64.getDecoder().decode(GIF_BASE64);
        int descriptor = 13 + 3 * (1 << ((gif[10] & 7) + 1));
        assertThat(gif[descriptor]).isEqualTo((byte) 0x2C);
        for (int at : new int[]{6, descriptor + 5}) {
            gif[at] = (byte) width;
            gif[at + 1] = (byte) (width >> 8);
            gif[at + 2] = (byte) height;
            gif[at + 3] = (byte) (height >> 8);
        }
        return gif;
    }

    private static String dataUri(String mediaType, byte[] bytes) {
        return "data:" + mediaType + ";base64," + base64(bytes);
    }

    @Test
    @DisplayName("should keep a copy of the cleaned bytes, so that no holder can change what another reads")
    void shouldCopyCleanedBytes_inAndOut() {
        byte[] original = "<svg/>".getBytes(StandardCharsets.UTF_8);
        SvgReferences.Cleaned cleaned = new SvgReferences.Cleaned(original, false);

        original[1] = 'X';
        byte[] first = cleaned.svg();
        first[2] = 'Y';

        assertThat(new String(cleaned.svg(), StandardCharsets.UTF_8)).isEqualTo("<svg/>");
        assertThat(cleaned.svg()).isNotSameAs(cleaned.svg());
    }

    @Test
    @DisplayName("should keep an inline picture that declares exactly the most pixels allowed, and remove one pixel more")
    void shouldKeepPicture_atTheCapAndRemoveOneOver() {
        assertThat(SvgReferences.MAX_NESTED_RASTER_PIXELS).isEqualTo(2_000L * 2_000);

        SvgReferences.Cleaned atCap = clean(HEAD + "<image xlink:href=\"" + dataUri("image/png", pngDeclaring(2_000, 2_000)) + "\"/></svg>").orElseThrow();
        SvgReferences.Cleaned over = clean(HEAD + "<image xlink:href=\"" + dataUri("image/png", pngDeclaring(2_001, 2_000)) + "\"/></svg>").orElseThrow();

        assertThat(atCap.removedReferences()).isFalse();
        assertThat(text(atCap)).contains("<image");
        assertThat(over.removedReferences()).isTrue();
        assertThat(text(over)).doesNotContain("<image");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("hugePictures")
    @DisplayName("should remove an inline picture whose small file declares a size Batik would decode in full")
    void shouldRemovePicture_thatDeclaresAHugeSize(String label, String uri) {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<rect width=\"2\" height=\"2\"/><image xlink:href=\"" + uri + "\" width=\"1\" height=\"1\"/>"
                + "<style>.a { background: url(" + uri + ") }</style></svg>").orElseThrow();

        assertThat(cleaned.removedReferences()).as(label).isTrue();
        assertThat(text(cleaned)).doesNotContain("<image").doesNotContain("base64").contains(".a { background: none }").contains("<rect");
    }

    static Stream<Arguments> hugePictures() {
        return Stream.of(
                Arguments.of("PNG of 40,000 x 40,000", dataUri("image/png", pngDeclaring(40_000, 40_000))),
                Arguments.of("PNG of 2,147,483,647 x 1", dataUri("image/png", pngDeclaring(Integer.MAX_VALUE, 1))),
                Arguments.of("PNG of 1 x 4,000,001", dataUri("image/png", pngDeclaring(1, 4_000_001))),
                Arguments.of("JPEG of 30,000 x 30,000", dataUri("image/jpeg", jpegDeclaring(30_000, 30_000))),
                Arguments.of("GIF of 60,000 x 60,000", dataUri("image/gif", gifDeclaring(60_000, 60_000))),
                Arguments.of("a huge PNG under a media type of SVG", dataUri("image/svg+xml", pngDeclaring(40_000, 40_000))));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"data:image/png;base64,iVBORw0KGgoAAAANSUhEUg==", "data:image/jpeg;base64,/9j/4AAQSkZJRg==", "data:image/gif;base64,R0lGODlhAQABAAAAACw=",
            "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB", "data:image/png;base64,iVBORw0KGgo=", "data:image/gif;base64,R0lGOD"})
    @DisplayName("should remove an inline picture that starts like a PNG, a JPEG or a GIF but whose header cannot be read")
    void shouldRemovePicture_whoseHeaderCannotBeRead(String uri) {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<rect width=\"2\" height=\"2\"/><image xlink:href=\"" + uri + "\" width=\"1\" height=\"1\"/></svg>")
                .orElseThrow();

        assertThat(cleaned.removedReferences()).isTrue();
        assertThat(text(cleaned)).doesNotContain("<image").contains("<rect");
    }

    @Test
    @DisplayName("should not decode an inline picture more than the size of the SVG allows")
    void shouldRemovePicture_whosePayloadIsLargerThanAnSvgMayBe() {
        // A JPEG with 17 MB of padding after its end: readable, so only the size can refuse it.
        byte[] padded = Arrays.copyOf(TestImages.jpeg(1, 1, false), 17 << 20);

        SvgReferences.Cleaned cleaned = clean(HEAD + "<image xlink:href=\"" + dataUri("image/jpeg", padded) + "\"/></svg>").orElseThrow();

        assertThat(cleaned.removedReferences()).isTrue();
        assertThat(text(cleaned)).doesNotContain("<image");
    }

    @Test
    @DisplayName("should keep an inline picture of a few hundred kilobytes")
    void shouldKeepPicture_ofSeveralHundredKilobytes() {
        byte[] photo = TestImages.png(1_200, 1_200);
        assertThat(photo.length).as("a picture worth the test").isGreaterThan(4_096);

        SvgReferences.Cleaned cleaned = clean(HEAD + "<image xlink:href=\"" + dataUri("image/png", photo) + "\"/></svg>").orElseThrow();

        assertThat(cleaned.removedReferences()).isFalse();
        assertThat(text(cleaned)).contains("<image");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("otherPictures")
    @DisplayName("should remove an inline picture of a format that is not a PNG, a JPEG or a GIF, though a reader could read it")
    void shouldRemovePicture_ofAnotherFormat(String label, String uri) {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<rect width=\"2\" height=\"2\"/><image xlink:href=\"" + uri + "\"/></svg>").orElseThrow();

        assertThat(cleaned.removedReferences()).as(label).isTrue();
        assertThat(text(cleaned)).doesNotContain("<image");
    }

    static Stream<Arguments> otherPictures() {
        return Stream.of(
                Arguments.of("BMP", dataUri("image/bmp", TestImages.bmp(2, 2))),
                Arguments.of("TIFF", dataUri("image/tiff", TestImages.tiff(2, 2))),
                Arguments.of("WBMP", dataUri("image/vnd.wap.wbmp", TestImages.wbmp(2, 2))));
    }

    @Test
    @DisplayName("should turn a url() of an SVG data: URI into none and keep one of a raster image, in CSS and in a presentation attribute")
    void shouldReplaceUrlOfSvgData_butKeepRaster() {
        String svg = "data:image/svg+xml;base64," + base64(NESTED_SVG);
        String png = PNG_URI;
        SvgReferences.Cleaned cleaned = clean(HEAD + "<style>.a { fill: url(" + svg + "#a) } .b { fill: url('" + svg + "#a') } .c { background: url(" + png + ") }</style>"
                + "<rect width=\"2\" height=\"2\" fill=\"url(" + svg + "#a)\" style=\"mask: url(" + svg + "#a)\"/></svg>").orElseThrow();

        assertThat(cleaned.removedReferences()).isTrue();
        String result = text(cleaned);
        assertThat(result).contains(".a { fill: none }").contains(".b { fill: none }").contains(".c { background: url(" + png + ") }")
                .contains("fill=\"none\"").contains("mask: none").doesNotContain("image/svg+xml");
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {"url(", "url('", "url(\"", "@import url(", "@import \"", "@import url('a", "url(a) url(", "@import", "url( "})
    @DisplayName("should clean hostile CSS made of unterminated url( and @import in linear time and leave no reference in it")
    void shouldCleanUnterminatedCss_inLinearTime(String unit) {
        // 4 MB of one unterminated construct: a scan that starts again at every url( takes hours on it.
        String hostile = unit.repeat(4_000_000 / unit.length());

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            SvgReferences.Cleaned cleaned = clean(HEAD + "<style>" + hostile + "</style><rect width=\"2\" height=\"2\"/></svg>").orElseThrow();
            assertThat(cleaned.removedReferences()).isTrue();
            assertThat(text(cleaned)).doesNotContain("url(\"").doesNotContain("@import").contains("<rect");
        });
    }

    @Test
    @DisplayName("should keep a url() that closes after a long run of text, and the url( that follows it")
    void shouldCleanEveryUrl_whenManyFollowOneAnother() {
        String css = "a { fill: url(#g) } ".repeat(1000) + "b { fill: url(http://e/x) } " + "c { fill: url(#h) } ".repeat(1000);
        SvgReferences.Cleaned cleaned = clean(HEAD + "<style>" + css + "</style><rect width=\"2\" height=\"2\"/></svg>").orElseThrow();

        String result = text(cleaned);
        assertThat(result).contains("b { fill: none }").doesNotContain("http://e");
        assertThat(result.split("url\\(#g\\)", -1)).hasSize(1001);
        assertThat(result.split("url\\(#h\\)", -1)).hasSize(1001);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"image", "use", "feImage", "cursor", "font-face-uri", "mpath", "tref"})
    @DisplayName("should remove a loading element whole when it points outside, as Batik fails the drawing on one without a reference")
    void shouldRemoveLoadingElement_whenReferenceIsOutside(String name) {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<rect width=\"2\" height=\"2\"/><" + name + " xlink:href=\"http://e/x\"/></svg>")
                .orElseThrow();

        assertThat(cleaned.removedReferences()).isTrue();
        assertThat(text(cleaned)).doesNotContain("<" + name).doesNotContain("http://e").contains("<rect");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"image", "use", "feImage", "cursor", "font-face-uri", "mpath", "tref"})
    @DisplayName("should remove a loading element of an inline svg whole when it points outside")
    void shouldRemoveLoadingElement_ofInlineSvg(String name) {
        Element svg = inline("<svg width=\"4\" height=\"4\"><rect width=\"2\" height=\"2\"/><" + name + " xlink:href=\"http://e/x\"></" + name + "></svg>");

        boolean removed = SvgReferences.clean(svg);

        assertThat(removed).isTrue();
        assertThat(svg.outerHtml().toLowerCase(java.util.Locale.ROOT)).doesNotContain("<" + name.toLowerCase(java.util.Locale.ROOT)).doesNotContain("http://e").contains("<rect");
    }

    @Test
    @DisplayName("should remove an outside src attribute from any element")
    void shouldRemoveSrcAttribute_pointingOutside() {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<rect width=\"2\" height=\"2\" src=\"http://e/x.png\"/></svg>").orElseThrow();

        assertThat(cleaned.removedReferences()).isTrue();
        assertThat(text(cleaned)).doesNotContain("http://e").doesNotContain("src=").contains("<rect");
    }

    @Test
    @DisplayName("should keep a data: reference whatever the case of its scheme, and a fragment with padding around it")
    void shouldKeepLocalReferences_whateverTheCaseAndPadding() {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<defs><circle id=\"c\" r=\"3\"/></defs><use xlink:href=\" #c \"/>"
                + "<image xlink:href=\"DATA:image/png;base64," + PNG_BASE64 + "\" width=\"1\" height=\"1\"/></svg>").orElseThrow();

        assertThat(cleaned.removedReferences()).isFalse();
        assertThat(text(cleaned)).contains("<use").contains("DATA:image/png").contains("<image");
    }

    @Test
    @DisplayName("should drop the link of an anchor and keep the anchor")
    void shouldDropLink_ofAnchorElement() {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<a xlink:href=\"https://example.invalid/x\" target=\"_blank\">"
                + "<rect width=\"2\" height=\"2\"/></a></svg>").orElseThrow();

        assertThat(cleaned.removedReferences()).isTrue();
        assertThat(text(cleaned)).contains("<a").contains("<rect").doesNotContain("example.invalid");
    }

    @Test
    @DisplayName("should remove script and foreignObject elements")
    void shouldRemoveScriptAndForeignObject_elements() {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<script>fetch('http://x')</script><foreignObject><div xmlns=\"http://www.w3.org/1999/xhtml\">"
                + "<img src=\"http://x/y.png\"/></div></foreignObject><rect width=\"2\" height=\"2\"/></svg>").orElseThrow();

        assertThat(text(cleaned)).doesNotContain("script").doesNotContain("foreignObject").doesNotContain("http://x").contains("<rect");
    }

    // ---- CSS --------------------------------------------------------------------------------

    @Test
    @DisplayName("should remove @import rules in both forms and external url() values from a style element")
    void shouldCleanStyleElement_ofImportsAndUrls() {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<style>@import 'x.css'; @import url(y.css); @import url(\"http://e/z.css\") screen;"
                + " .a { fill: #c00 } .b { fill: url(http://e/p.svg#g); stroke: url(#local) }"
                + " .c { background: url(" + PNG_URI + ") } .d { cursor: url('c.cur'), auto }</style><rect class=\"a\" width=\"2\" height=\"2\"/></svg>")
                .orElseThrow();

        String svg = text(cleaned);
        assertThat(cleaned.removedReferences()).isTrue();
        assertThat(svg).doesNotContain("@import").doesNotContain("x.css").doesNotContain("y.css").doesNotContain("z.css")
                .doesNotContain("http://e").doesNotContain("c.cur");
        assertThat(svg).contains(".a { fill: #c00 }").contains("stroke: url(#local)").contains("url(" + PNG_URI + ")")
                .contains("fill: none").contains("cursor: none, auto");
    }

    @Test
    @DisplayName("should clean a style attribute and a presentation attribute")
    void shouldCleanStyleAndPresentationAttributes_inElements() {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<rect width=\"2\" height=\"2\" style=\"fill:url(http://e/p#g);stroke:red\" "
                + "filter=\"url(http://e/f.svg#f)\" mask=\"url(#m)\"/></svg>").orElseThrow();

        String svg = text(cleaned);
        assertThat(svg).doesNotContain("http://e").contains("stroke:red").contains("mask=\"url(#m)\"").contains("fill:none");
    }

    @Test
    @DisplayName("should clean CSS keywords and function names whatever their case, as CSS reads them")
    void shouldCleanCss_whateverTheCase() {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<style>@IMPORT 'a.css'; @Import url(b.css); .a { fill: URL(http://e/c.svg#g) }</style>"
                + "<rect width=\"2\" height=\"2\" style=\"fill:Url(http://e/d.svg#g)\" filter=\"URL(http://e/e.svg#f)\"/></svg>").orElseThrow();

        assertThat(cleaned.removedReferences()).isTrue();
        assertThat(text(cleaned)).doesNotContain("a.css").doesNotContain("b.css").doesNotContain("http://e").doesNotContain("@IMPORT")
                .doesNotContain("@Import");
    }

    @Test
    @DisplayName("should remove a whole @import rule: double-quoted, with a media list, or with a url() split over lines")
    void shouldRemoveWholeImportRules_inEveryForm() {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<style>@import \"a.css\"; @import url(b.css) screen and (min-width: 10px);\n"
                + "@import\n  url(\n  c.css\n  ) print; .keep { fill: #123456 }</style><rect width=\"2\" height=\"2\"/></svg>").orElseThrow();

        String svg = text(cleaned);
        assertThat(svg).doesNotContain("@import").doesNotContain("a.css").doesNotContain("b.css").doesNotContain("c.css")
                .doesNotContain("screen").doesNotContain("min-width").doesNotContain("print").contains(".keep { fill: #123456 }");
    }

    @Test
    @DisplayName("should replace a url() that is split over lines, quoted, or padded with spaces, when it points outside")
    void shouldReplaceOutsideUrl_inEveryWrittenForm() {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<style>.a { fill: url(\n  http://e/a.svg#g\n) } .b { fill: url( \"http://e/b.svg#g\" ) } "
                + ".c { fill: url('http://e/c.svg#g') } .d { fill: url(  http://e/d.svg#g  ) } .e { fill: url(http://e/e\n.svg#g) }</style>"
                + "<rect width=\"2\" height=\"2\"/></svg>")
                .orElseThrow();

        assertThat(text(cleaned)).doesNotContain("http://e").doesNotContain("a.svg").contains(".a { fill: none }").contains(".b { fill: none }")
                .contains(".c { fill: none }").contains(".d { fill: none }").contains(".e { fill: none }");
    }

    @Test
    @DisplayName("should keep a url() that refers to the drawing or carries data, quoted or not, and report nothing removed")
    void shouldKeepLocalUrls_inEveryWrittenForm() {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<style>.a { fill: url(#g) } .b { fill: url( \"#g\" ) } .c { fill: url('#g') } "
                + ".d { fill: url(\"" + PNG_URI + "\") } .e { fill: url(#a$1) } .f { fill: url( #g ) }</style>"
                + "<rect width=\"2\" height=\"2\" mask=\"url(#m)\" style=\"fill:url('#g')\"/></svg>").orElseThrow();

        String svg = text(cleaned);
        assertThat(cleaned.removedReferences()).isFalse();
        assertThat(svg).contains(".a { fill: url(#g) }").contains(".b { fill: url( \"#g\" ) }").contains(".c { fill: url('#g') }")
                .contains(".d { fill: url(\"" + PNG_URI + "\") }").contains(".e { fill: url(#a$1) }")
                .contains(".f { fill: url( #g ) }").contains("mask=\"url(#m)\"").contains("fill:url('#g')");
    }

    @Test
    @DisplayName("should clean CSS held in a CDATA section of a style element")
    void shouldCleanStyle_heldInACdataSection() {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<style><![CDATA[ @import 'x.css'; .a { fill: url(http://e/p.svg#g) } .b { fill: #123456 } ]]></style>"
                + "<rect width=\"2\" height=\"2\"/></svg>").orElseThrow();

        assertThat(cleaned.removedReferences()).isTrue();
        assertThat(text(cleaned)).doesNotContain("x.css").doesNotContain("http://e").contains(".b { fill: #123456 }");
    }

    @Test
    @DisplayName("should remove an @import from an attribute and say a style attribute was cleaned")
    void shouldCleanAttributes_andReportIt() {
        SvgReferences.Cleaned imported = clean(HEAD + "<rect width=\"2\" height=\"2\" style=\"@import 'x.css'; fill: red\"/></svg>").orElseThrow();
        SvgReferences.Cleaned external = clean(HEAD + "<rect width=\"2\" height=\"2\" style=\"fill:url(http://e/p#g)\"/></svg>").orElseThrow();

        assertThat(text(imported)).doesNotContain("x.css").contains("fill: red");
        assertThat(imported.removedReferences()).isTrue();
        assertThat(external.removedReferences()).isTrue();
    }

    @Test
    @DisplayName("should empty CSS that hides a url( behind an escape")
    void shouldEmptyCss_thatContainsEscapes() {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<style>.a { background: u\\72l(http://e/x.png) }</style>"
                + "<rect width=\"2\" height=\"2\" style=\"background:\\75rl(http://e/y.png)\"/></svg>").orElseThrow();

        assertThat(cleaned.removedReferences()).isTrue();
        assertThat(text(cleaned)).doesNotContain("http://e").doesNotContain("72l").doesNotContain("75rl");
    }

    // ---- parsing ----------------------------------------------------------------------------

    @Test
    @DisplayName("should remove processing instructions such as xml-stylesheet, and the document type")
    void shouldRemoveProcessingInstructionsAndDoctype_fromTheDocument() {
        SvgReferences.Cleaned cleaned = clean("<?xml version=\"1.0\"?><?xml-stylesheet href=\"http://e/x.css\" type=\"text/css\"?>"
                + "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">"
                + HEAD + "<rect width=\"2\" height=\"2\"/></svg>").orElseThrow();

        String svg = text(cleaned);
        assertThat(cleaned.removedReferences()).isTrue();
        assertThat(svg).doesNotContain("xml-stylesheet").doesNotContain("DOCTYPE").doesNotContain("svg11.dtd").contains("<rect");
    }

    @Test
    @DisplayName("should not count the document type as a removed reference, but count a processing instruction")
    void shouldNotCountDoctype_asRemovedReference() {
        SvgReferences.Cleaned doctype = clean("<?xml version=\"1.0\"?><!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" "
                + "\"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">" + HEAD + "<rect width=\"2\" height=\"2\"/></svg>").orElseThrow();
        SvgReferences.Cleaned instruction = clean("<?xml version=\"1.0\"?><?xml-stylesheet href=\"x.css\"?>" + HEAD
                + "<rect width=\"2\" height=\"2\"/></svg>").orElseThrow();

        assertThat(doctype.removedReferences()).isFalse();
        assertThat(text(doctype)).doesNotContain("DOCTYPE");
        assertThat(instruction.removedReferences()).isTrue();
    }

    @Test
    @DisplayName("should skip an entity that only an unread external DTD could define, and refuse an undeclared entity otherwise")
    void shouldSkipEntityOfUnreadDtd_andRefuseUndeclaredEntity() {
        Optional<SvgReferences.Cleaned> withDtd = clean("<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" "
                + "\"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">" + HEAD + "<text>a&nbsp;b</text></svg>");

        // The drawing is kept: failure is the last resort, and the rest of it is intact.
        assertThat(withDtd.map(SvgReferencesUnitTest::text).orElse("")).contains("<text>ab</text>");
        assertThat(clean(HEAD + "<text>a&nbsp;b</text></svg>")).isEmpty();
    }

    @Test
    @DisplayName("should make no connection and read no file for an external DTD or entity")
    void shouldNotResolveEntities_whenCleaningSvg() throws Exception {
        Path secret = Files.writeString(directory.resolve("secret.txt"), "root:x:0:0:FAKE-SECRET");
        AtomicInteger connections = new AtomicInteger();
        try (ServerSocket probe = new ServerSocket(0)) {
            // Every connection is counted and closed at once, so a parser that does connect fails this test
            // quickly instead of waiting for an answer that never comes.
            Thread listener = new Thread(() -> {
                try {
                    while (true) {
                        probe.accept().close();
                        connections.incrementAndGet();
                    }
                } catch (IOException closed) {
                    // The test is over.
                }
            }, "svg-xxe-probe");
            listener.setDaemon(true);
            listener.start();
            int port = probe.getLocalPort();
            String svg = "<?xml version=\"1.0\"?>\n<!DOCTYPE svg SYSTEM \"http://127.0.0.1:" + port + "/evil.dtd\" [\n"
                    + "<!ENTITY remote SYSTEM \"http://127.0.0.1:" + port + "/entity\">\n"
                    + "<!ENTITY local SYSTEM \"" + secret.toUri() + "\">\n"
                    + "<!ENTITY passwd SYSTEM \"file:///etc/passwd\">\n"
                    + "<!ENTITY % param SYSTEM \"http://127.0.0.1:" + port + "/param.dtd\"> %param;\n]>\n"
                    + "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" "
                    + "xmlns:xi=\"http://www.w3.org/2001/XInclude\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                    + "xsi:schemaLocation=\"http://www.w3.org/2000/svg http://127.0.0.1:" + port + "/svg.xsd\" width=\"40\" height=\"20\">"
                    + "<text>&remote;&local;&passwd;</text><xi:include href=\"" + secret.toUri() + "\" parse=\"text\"/>"
                    + "<xi:include href=\"http://127.0.0.1:" + port + "/included.svg\"/><rect width=\"2\" height=\"2\"/></svg>";

            Optional<SvgReferences.Cleaned> cleaned = assertTimeoutPreemptively(Duration.ofSeconds(15), () -> clean(svg));

            assertThat(cleaned.map(SvgReferencesUnitTest::text).orElse("")).doesNotContain("root:").doesNotContain("FAKE-SECRET");
            assertThat(connections.get()).isZero();
        }
    }

    @Test
    @DisplayName("should expand an internal entity and bound a billion-laughs expansion")
    void shouldExpandInternalEntities_withinLimits() {
        Optional<SvgReferences.Cleaned> small = clean("<!DOCTYPE svg [<!ENTITY name \"FAKE-Label\">]>" + HEAD + "<text>&name;</text></svg>");
        StringBuilder bomb = new StringBuilder("<!DOCTYPE svg [<!ENTITY a0 \"lol\">");
        for (int i = 1; i <= 12; i++) {
            bomb.append("<!ENTITY a").append(i).append(" \"");
            for (int j = 0; j < 10; j++) {
                bomb.append("&a").append(i - 1).append(';');
            }
            bomb.append("\">");
        }
        bomb.append("]>").append(HEAD).append("<text>&a12;</text></svg>");

        Optional<SvgReferences.Cleaned> exploded =
                assertTimeoutPreemptively(Duration.ofSeconds(20), () -> clean(bomb.toString()));

        assertThat(small.map(SvgReferencesUnitTest::text).orElse("")).contains("FAKE-Label").doesNotContain("&name;");
        assertThat(exploded).isEmpty();
    }

    @Test
    @DisplayName("should refuse an SVG that is not well-formed, empty or not XML at all")
    void shouldRefuse_unparseableSvg() {
        assertThat(clean("<svg><rect></svg>")).isEmpty();
        assertThat(clean("")).isEmpty();
        assertThat(clean("just text")).isEmpty();
        assertThat(SvgReferences.clean(new byte[]{(byte) 0xFF, 0, 1, 2})).isEmpty();
    }

    @Test
    @DisplayName("should refuse a document nested deeper than a drawing ever is")
    void shouldRefuse_veryDeepNesting() {
        String deep = "<svg xmlns=\"http://www.w3.org/2000/svg\">" + "<g>".repeat(5000) + "</g>".repeat(5000) + "</svg>";

        Optional<SvgReferences.Cleaned> cleaned = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> clean(deep));

        assertThat(cleaned).isEmpty();
    }

    @Test
    @DisplayName("should accept a drawing nested 300 groups deep, though the JDK's own limit is 100")
    void shouldAccept_reasonablyDeepNesting() {
        String deep = "<svg xmlns=\"http://www.w3.org/2000/svg\">" + "<g>".repeat(300) + "<rect width=\"1\" height=\"1\"/>"
                + "</g>".repeat(300) + "</svg>";

        assertThat(clean(deep).map(SvgReferencesUnitTest::text).orElse("")).contains("<rect");
    }

    @Test
    @DisplayName("should keep the content of a clean SVG: shapes, text, groups, namespaces")
    void shouldKeepContent_ofCleanSvg() {
        SvgReferences.Cleaned cleaned = clean(HEAD + "<g transform=\"translate(2 2)\"><path d=\"M0 0L5 5\" stroke=\"#000\"/>"
                + "<text x=\"1\" y=\"9\">FAKE-Label é</text></g></svg>").orElseThrow();

        assertThat(cleaned.removedReferences()).isFalse();
        assertThat(text(cleaned)).contains("xmlns=\"http://www.w3.org/2000/svg\"").contains("transform=\"translate(2 2)\"")
                .contains("M0 0L5 5").contains("FAKE-Label é");
    }

    // ---- inline svg -------------------------------------------------------------------------

    private static Element inline(String svgMarkup) {
        Document document = Jsoup.parse("<html><body>" + svgMarkup + "</body></html>");
        return document.selectFirst("svg");
    }

    @Test
    @DisplayName("should clean an inline svg in place, keeping its other content")
    void shouldCleanInlineSvg_inPlace() {
        Element svg = inline("<svg width=\"40\" height=\"20\"><rect width=\"40\" height=\"20\" fill=\"#c00\" style=\"fill:url(http://e/p)\"/>"
                + "<image xlink:href=\"http://e/x.png\"/><use xlink:href=\"#r\"/><a xlink:href=\"http://e/l\"><circle r=\"2\"/></a>"
                + "<style>@import url(y.css); .a { fill: url(http://e/q) }</style><script>x()</script></svg>");

        boolean removed = SvgReferences.clean(svg);

        String html = svg.outerHtml();
        assertThat(removed).isTrue();
        assertThat(html).doesNotContain("http://e").doesNotContain("@import").doesNotContain("<script").doesNotContain("<image");
        assertThat(html).contains("<rect").contains("fill=\"#c00\"").contains("xlink:href=\"#r\"").contains("<circle");
    }

    @Test
    @DisplayName("should report each kind of removal from an inline svg on its own")
    void shouldReportEachRemoval_fromInlineSvg() {
        assertThat(SvgReferences.clean(inline("<svg><rect/><image xlink:href=\"http://e/x\"/></svg>"))).as("loading element").isTrue();
        assertThat(SvgReferences.clean(inline("<svg><rect/><script>x()</script></svg>"))).as("script").isTrue();
        assertThat(SvgReferences.clean(inline("<svg><a xlink:href=\"http://e/l\"><circle r=\"2\"/></a></svg>"))).as("link attribute").isTrue();
        assertThat(SvgReferences.clean(inline("<svg><style>.a { fill: url(http://e/q) }</style></svg>"))).as("style element").isTrue();
        assertThat(SvgReferences.clean(inline("<svg><rect style=\"fill:url(http://e/q)\"/></svg>"))).as("style attribute").isTrue();
    }

    @Test
    @DisplayName("should clean CSS of an inline svg whatever the case, and an escape that could hide a url(")
    void shouldCleanInlineCss_whateverTheCaseOrEscape() {
        Element svg = inline("<svg><style>@IMPORT 'a.css'; .a { fill: URL(http://e/c.svg#g) }</style>"
                + "<rect style=\"fill:Url(http://e/d.svg#g)\" filter=\"URL(http://e/e.svg#f)\"/>"
                + "<circle style=\"background:\\75rl(http://e/y.png)\"/><ellipse style=\"@import 'z.css'; fill: red\"/></svg>");

        boolean removed = SvgReferences.clean(svg);

        String html = svg.outerHtml();
        assertThat(removed).isTrue();
        assertThat(html).doesNotContain("a.css").doesNotContain("http://e").doesNotContain("z.css").doesNotContain("75rl")
                .doesNotContain("@IMPORT").contains("fill: red");
    }

    @Test
    @DisplayName("should clean style text held as data as well as text")
    void shouldCleanInlineStyle_heldAsData() {
        Element svg = inline("<svg><rect/></svg>");
        svg.appendElement("style").appendChild(new DataNode("@import 'x.css'; .a { fill: url(http://e/p) }"));

        boolean removed = SvgReferences.clean(svg);

        assertThat(removed).isTrue();
        assertThat(svg.outerHtml()).doesNotContain("x.css").doesNotContain("http://e");
    }

    @Test
    @DisplayName("should report nothing removed from an inline svg whose CSS only refers to the drawing itself")
    void shouldReportNothingRemoved_forInlineSvgThatOnlyRefersToItself() {
        Element svg = inline("<svg><defs><linearGradient id=\"g\"></linearGradient></defs><style>.a { fill: url(#g) }</style>"
                + "<rect mask=\"url(#m)\" style=\"fill:url('#g')\"></rect></svg>");
        String before = svg.outerHtml();

        assertThat(SvgReferences.clean(svg)).isFalse();
        assertThat(svg.outerHtml()).isEqualTo(before);
    }

    @Test
    @DisplayName("should leave an inline svg without references untouched and say so")
    void shouldReportNothingRemoved_forCleanInlineSvg() {
        Element svg = inline("<svg width=\"4\" height=\"4\"><defs><circle id=\"c\" r=\"1\"/></defs><use href=\"#c\"/></svg>");
        String before = svg.outerHtml();

        assertThat(SvgReferences.clean(svg)).isFalse();
        assertThat(svg.outerHtml()).isEqualTo(before);
    }

    @Test
    @DisplayName("should reject null arguments")
    void shouldRejectNull_whenArgumentMissing() {
        assertThatNullPointerException().isThrownBy(() -> SvgReferences.clean((byte[]) null));
        assertThatNullPointerException().isThrownBy(() -> SvgReferences.clean((Element) null));
    }

    @Test
    @DisplayName("should compare cleaned SVGs by the bytes they hold, and print their size, never their content")
    void shouldCompareCleanedSvgByContent_andPrintNoContent() {
        byte[] bytes = "<svg>FAKE-patient-drawing</svg>".getBytes(StandardCharsets.UTF_8);
        SvgReferences.Cleaned first = new SvgReferences.Cleaned(bytes, true);
        SvgReferences.Cleaned same = new SvgReferences.Cleaned(bytes.clone(), true);
        SvgReferences.Cleaned otherBytes = new SvgReferences.Cleaned("<svg/>".getBytes(StandardCharsets.UTF_8), true);
        SvgReferences.Cleaned otherFlag = new SvgReferences.Cleaned(bytes, false);

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(otherBytes).isNotEqualTo(otherFlag);
        assertThat(first.equals(null)).isFalse();
        assertThat(first.toString()).isEqualTo("Cleaned[svgBytes=" + bytes.length + ", removedReferences=true]")
                .doesNotContain("FAKE-patient-drawing");
    }
}
