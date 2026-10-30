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

import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestImages;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.ImageHeader;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the applying half of {@link PrintImageService}: what is written, how the elements
 * are rewritten or boxed, which degradations are reported, and that nothing is left behind.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("PrintImageService applying Unit Tests")
class PrintImageServiceApplyUnitTest {

    private static final long SHARE = 256L << 20;

    @TempDir
    Path base;

    @TempDir
    Path tempRoot;

    private final Set<HtmlPdfRenderDegradation> degradations = EnumSet.noneOf(HtmlPdfRenderDegradation.class);

    private PrintImageService service() {
        return new PrintImageService(tempRoot);
    }

    private Document document(String bodyHtml) {
        return Jsoup.parse("<html><body>" + bodyHtml + "</body></html>");
    }

    private PreparedPrintImages prepare(Document document, long share) throws IOException {
        return service().prepare(document, base, List.of(base), PrintMode.NORMAL, share, degradations);
    }

    private PreparedPrintImages prepare(Document document) throws IOException {
        return prepare(document, SHARE);
    }

    private Path file(String name, byte[] bytes) {
        return TestImages.write(base, name, bytes);
    }

    /** The file an {@code <img>} points at after the step. */
    private static Path pathOf(Element img) {
        return Path.of(URI.create(img.attr("src")));
    }

    private static BufferedImage read(Path file) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        assertThat(image).as("readable image").isNotNull();
        return image;
    }

    private static String dataUri(String type, byte[] bytes) {
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private List<String> filesIn(Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    // ---- as is ------------------------------------------------------------------------------

    @Test
    @DisplayName("should keep the bytes of a JPEG used as it is: the source points at the same file and nothing is written")
    void shouldKeepJpegBytes_forAsIsJpeg() throws IOException {
        byte[] jpeg = TestImages.jpeg(200, 100, false);
        Path original = file("photo.jpg", jpeg);
        Document document = document("<img id=\"i\" src=\"photo.jpg?v=2\">");

        try (PreparedPrintImages images = prepare(document)) {
            Element img = document.getElementById("i");
            assertThat(Files.readAllBytes(pathOf(img))).isEqualTo(jpeg);
            assertThat(pathOf(img).toRealPath()).isEqualTo(original.toRealPath());
            assertThat(images.directory()).isNull();
        }
        assertThat(degradations).isEmpty();
        assertThat(filesIn(tempRoot)).isEmpty();
    }

    @Test
    @DisplayName("should rewrite a source to the encoded URI of the file, spaces and accents included")
    void shouldRewriteSource_toEncodedFileUri() throws IOException {
        Path png = file("sub dir/caf" + (char) 0xE9 + " photo.png", TestImages.png(30, 20));
        Document document = document("<img id=\"i\" src=\"sub%20dir/caf%C3%A9%20photo.png\">");

        try (PreparedPrintImages images = prepare(document)) {
            String src = document.getElementById("i").attr("src");
            assertThat(src).startsWith("file:").contains("sub%20dir").contains("caf%C3%A9%20photo.png").doesNotContain(" ");
            assertThat(pathOf(document.getElementById("i")).toRealPath()).isEqualTo(png.toRealPath());
            assertThat(images.directory()).isNull();
        }
    }

    @Test
    @DisplayName("should keep a data: source that is used as it is")
    void shouldKeepDataUri_usedAsIs() throws IOException {
        String uri = dataUri("image/png", TestImages.png(10, 10));
        Document document = document("<img id=\"i\" src=\"" + uri + "\">");

        try (PreparedPrintImages images = prepare(document)) {
            assertThat(document.getElementById("i").attr("src")).isEqualTo(uri);
            assertThat(images.directory()).isNull();
        }
    }

    // ---- converted --------------------------------------------------------------------------

    @Test
    @DisplayName("should turn a rotated JPEG upright and write it again as a JPEG")
    void shouldRotateAndReencode_forExifOrientation() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("htmlpdf/images/exif-orientation-6.jpg")) {
            file("phone.jpg", in.readAllBytes());
        }
        Document document = document("<img id=\"i\" src=\"phone.jpg\">");

        try (PreparedPrintImages images = prepare(document)) {
            Path written = pathOf(document.getElementById("i"));
            assertThat(written.getParent()).isEqualTo(images.directory());
            assertThat(written.getParent().getParent()).isEqualTo(tempRoot);
            assertThat(written.getFileName().toString()).endsWith(".jpg");
            // Stored 8 x 4 with orientation 6; shown upright it is 4 x 8, and that is the file's own size,
            // so the element needs no width and height.
            BufferedImage image = read(written);
            assertThat(new int[]{image.getWidth(), image.getHeight()}).containsExactly(4, 8);
            assertThat(document.getElementById("i").hasAttr("width")).isFalse();
        }
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.IMAGE_CONVERTED);
    }

    @Test
    @DisplayName("should convert a format OpenPDF cannot embed into a PNG")
    void shouldConvertWbmp_toPng() throws IOException {
        file("pixel.wbmp", TestImages.wbmp(16, 12));
        Document document = document("<img id=\"i\" src=\"pixel.wbmp\">");

        try (PreparedPrintImages images = prepare(document)) {
            Path written = pathOf(document.getElementById("i"));
            assertThat(written.getFileName().toString()).endsWith(".png");
            BufferedImage image = read(written);
            assertThat(new int[]{image.getWidth(), image.getHeight()}).containsExactly(16, 12);
        }
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.IMAGE_CONVERTED);
    }

    @Test
    @DisplayName("should read an image over the cap as a stream with subsampling and keep its printed size")
    void shouldDownscaleOverCapPng_keepingPrintedSize() throws IOException {
        file("scan.png", TestImages.png(3000, 2000));
        Document document = document("<img id=\"a\" src=\"scan.png\"><img id=\"b\" src=\"scan.png\" width=\"300\">"
                + "<img id=\"c\" src=\"scan.png\" style=\"height: 100px\">");

        // 16 MB holds 1 megapixel: every third pixel of 3000 x 2000 gives 1000 x 667.
        try (PreparedPrintImages images = prepare(document, 16L << 20)) {
            Path written = pathOf(document.getElementById("a"));
            BufferedImage image = read(written);
            assertThat(new int[]{image.getWidth(), image.getHeight()}).containsExactly(1000, 667);
            // One file for the three elements that use the same source.
            assertThat(pathOf(document.getElementById("b"))).isEqualTo(written);
            assertThat(pathOf(document.getElementById("c"))).isEqualTo(written);
            assertThat(filesIn(images.directory())).hasSize(1);
        }
        // Printed size is the original's: set when the element says nothing about its size.
        assertThat(document.getElementById("a").attr("width")).isEqualTo("3000");
        assertThat(document.getElementById("a").attr("height")).isEqualTo("2000");
        assertThat(document.getElementById("b").attr("width")).isEqualTo("300");
        assertThat(document.getElementById("b").hasAttr("height")).isFalse();
        assertThat(document.getElementById("c").hasAttr("width")).isFalse();
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.IMAGE_DOWNSCALED);
    }

    @Test
    @DisplayName("should swap the printed size of a downscaled image that is also turned")
    void shouldUseUprightSize_forDownscaledRotatedJpeg() throws IOException {
        file("rotated.jpg", TestImages.withOrientation(TestImages.jpeg(1200, 800, false), 6));
        Document document = document("<img id=\"i\" src=\"rotated.jpg\">");

        try (PreparedPrintImages images = prepare(document, 4L << 20)) {
            BufferedImage image = read(pathOf(document.getElementById("i")));
            assertThat(image.getHeight()).isGreaterThan(image.getWidth());
            assertThat(document.getElementById("i").attr("width")).isEqualTo("800");
            assertThat(document.getElementById("i").attr("height")).isEqualTo("1200");
        }
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.IMAGE_DOWNSCALED);
    }

    @ParameterizedTest(name = "orientation {0}")
    @ValueSource(ints = {2, 3, 4, 5, 6, 7, 8})
    @DisplayName("should print a downscaled JPEG at its upright size: width and height swapped for the orientations that turn it a quarter")
    void shouldUseUprightSize_forEveryOrientation(int orientation) throws IOException {
        file("o.jpg", TestImages.withOrientation(TestImages.jpeg(1200, 800, false), orientation));
        Document document = document("<img id=\"i\" src=\"o.jpg\">");
        boolean turned = orientation >= 5;

        try (PreparedPrintImages images = prepare(document, 4L << 20)) {
            BufferedImage image = read(pathOf(document.getElementById("i")));
            assertThat(image.getHeight() > image.getWidth()).isEqualTo(turned);
        }

        assertThat(document.getElementById("i").attr("width")).isEqualTo(turned ? "800" : "1200");
        assertThat(document.getElementById("i").attr("height")).isEqualTo(turned ? "1200" : "800");
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.IMAGE_DOWNSCALED);
    }

    @Test
    @DisplayName("should read a data: image over the cap with subsampling too, and keep its printed size")
    void shouldDownscaleOverCapDataUri_keepingPrintedSize() throws IOException {
        Document document = document("<img id=\"i\" src=\"" + dataUri("image/png", TestImages.png(3000, 2000)) + "\">");

        try (PreparedPrintImages images = prepare(document, 16L << 20)) {
            BufferedImage image = read(pathOf(document.getElementById("i")));
            assertThat(new int[]{image.getWidth(), image.getHeight()}).containsExactly(1000, 667);
        }

        assertThat(document.getElementById("i").attr("width")).isEqualTo("3000");
        assertThat(document.getElementById("i").attr("height")).isEqualTo("2000");
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.IMAGE_DOWNSCALED);
    }

    @Test
    @DisplayName("should give every converted file its own name")
    void shouldNameEveryConvertedFileApart() throws IOException {
        file("a.wbmp", TestImages.wbmp(8, 8));
        file("b.wbmp", TestImages.wbmp(16, 12));
        Document document = document("<img id=\"a\" src=\"a.wbmp\"><img id=\"b\" src=\"b.wbmp\">");

        try (PreparedPrintImages images = prepare(document)) {
            Path first = pathOf(document.getElementById("a"));
            Path second = pathOf(document.getElementById("b"));
            assertThat(first).isNotEqualTo(second);
            assertThat(read(first).getWidth()).isEqualTo(8);
            assertThat(read(second).getWidth()).isEqualTo(16);
            assertThat(filesIn(images.directory())).hasSize(2);
        }
    }

    @Test
    @DisplayName("should convert at the planned fallback subsampling when OpenPDF cannot embed an image planned to be used as it is")
    void shouldConvertAtFallbackSubsampling_whenAsIsImageCannotBeEmbedded() throws IOException {
        Path wbmp = file("big.wbmp", TestImages.wbmp(16, 16)).toRealPath();
        Document document = document("<img id=\"i\" src=\"big.wbmp\">");
        Element img = document.getElementById("i");
        ImagePlan plan = new ImagePlan(List.of(new PlannedImage(img, ImageOutcome.AS_IS, wbmp, null,
                new ImageHeader("wbmp", 16, 16), 2, 1, 0, null, wbmp.toString())), 0);

        try (PreparedPrintImages images = service().apply(document, plan, degradations)) {
            BufferedImage written = read(pathOf(img));
            assertThat(new int[]{written.getWidth(), written.getHeight()}).containsExactly(8, 8);
        }

        assertThat(img.attr("width")).isEqualTo("16");
        assertThat(img.attr("height")).isEqualTo("16");
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.IMAGE_DOWNSCALED);
    }

    @Test
    @DisplayName("should convert a truncated JPEG that has to be turned, as ImageIO's tolerant reader allows")
    void shouldConvertTruncatedJpeg_withTolerantReader() throws IOException {
        byte[] jpeg = TestImages.jpeg(60, 40, false);
        file("cut.jpg", TestImages.withOrientation(Arrays.copyOf(jpeg, jpeg.length - 60), 6));
        Document document = document("<img id=\"i\" src=\"cut.jpg\">");

        try (PreparedPrintImages images = prepare(document)) {
            BufferedImage image = read(pathOf(document.getElementById("i")));
            assertThat(new int[]{image.getWidth(), image.getHeight()}).containsExactly(40, 60);
        }
    }

    @Test
    @DisplayName("should convert a data: image that has to be turned, without keeping a second copy in the plan")
    void shouldConvertDataUri_thatNeedsConverting() throws IOException {
        Document document = document("<img id=\"i\" src=\"" + dataUri("image/vnd.wap.wbmp", TestImages.wbmp(8, 8)) + "\">");

        try (PreparedPrintImages images = prepare(document)) {
            assertThat(document.getElementById("i").attr("src")).startsWith("file:");
            assertThat(read(pathOf(document.getElementById("i"))).getWidth()).isEqualTo(8);
        }
    }

    // ---- boxes ------------------------------------------------------------------------------

    @Test
    @DisplayName("should replace an image that cannot be decoded with a box, and say so without naming it")
    void shouldBoxUndecodableImage_withWarningWithoutPath() throws IOException {
        byte[] png = TestImages.png(40, 30);
        file("cut.png", Arrays.copyOf(png, png.length - 40));
        Document document = document("<p><img id=\"i\" src=\"cut.png\" width=\"90\" height=\"60\"></p>");

        try (LogCapture capture = LogCapture.forLogger(PrintImageService.class);
                PreparedPrintImages images = prepare(document)) {
            assertThat(document.select("img")).isEmpty();
            Element box = document.selectFirst("p > span.carlos-pdf-image-unavailable");
            assertThat(box.text()).isEqualTo("[image unavailable]");
            assertThat(box.attr("style")).isEqualTo("width: 90px; height: 60px");
            assertThat(images.directory()).isNull();
            assertThat(capture.messages()).isNotEmpty().allSatisfy(message -> assertThat(message)
                    .doesNotContain("cut.png").doesNotContain(base.toString()).doesNotContain("file:"));
        }
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE);
    }

    @Test
    @DisplayName("should log the reason of a box, and only the reason")
    void shouldLogReasonWithoutPath_forBoxedImage() throws IOException {
        Document document = document("<img src=\"missing-FAKE-patient.png\"><img src=\"http://example.invalid/FAKE.png\">");

        try (LogCapture capture = LogCapture.forLogger(PrintImageService.class);
                PreparedPrintImages images = prepare(document)) {
            assertThat(capture.messages()).containsExactly("An image is shown as a box (missing or not a file)",
                    "An image is shown as a box (remote or unsupported source)");
        }
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE);
        assertThat(document.select("span.carlos-pdf-image-unavailable")).hasSize(2);
    }

    @Test
    @DisplayName("should box every raster image without a render slot and report that images were omitted")
    void shouldBoxEveryImage_withoutSlot_andReportOmitted() throws IOException {
        file("a.png", TestImages.png(10, 10));
        Document document = document("<img src=\"a.png\"><img src=\"missing.png\"><img src=\"a.png\">");

        try (LogCapture capture = LogCapture.forLogger(PrintImageService.class);
                PreparedPrintImages images = prepare(document, 0)) {
            assertThat(document.select("img")).isEmpty();
            assertThat(document.select("span.carlos-pdf-image-unavailable")).hasSize(3);
            // A missing slot is reported once by the render service; the boxes do not each log it.
            assertThat(capture.messages()).containsExactly("An image is shown as a box (missing or not a file)");
        }
        assertThat(degradations).containsExactlyInAnyOrder(HtmlPdfRenderDegradation.IMAGES_OMITTED_FOR_MEMORY,
                HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE);
    }

    @Test
    @DisplayName("should report only the omission, not each image as unavailable, when a missing slot is the sole reason")
    void shouldReportOnlyOmission_whenSlotIsTheOnlyReason() throws IOException {
        file("a.png", TestImages.png(10, 10));
        Document document = document("<img src=\"a.png\"><img src=\"" + dataUri("image/png", TestImages.png(5, 5)) + "\">");

        try (LogCapture capture = LogCapture.forLogger(PrintImageService.class);
                PreparedPrintImages images = prepare(document, 0)) {
            assertThat(document.select("span.carlos-pdf-image-unavailable")).hasSize(2);
            assertThat(capture.messages()).isEmpty();
        }
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.IMAGES_OMITTED_FOR_MEMORY);
    }

    @Test
    @DisplayName("should not report images as omitted when none were")
    void shouldNotReportOmitted_whenNothingWasOmitted() throws IOException {
        try (PreparedPrintImages images = prepare(document("<p>text</p><img src=\"missing.png\">"), 0)) {
            assertThat(degradations).doesNotContain(HtmlPdfRenderDegradation.IMAGES_OMITTED_FOR_MEMORY);
        }
    }

    @Test
    @DisplayName("should box an image used as it is whose source cannot be read again")
    void shouldBoxAsIsImage_whoseSourceCannotBeReadAgain() throws IOException {
        Document document = document("<img id=\"i\" src=\"no-longer-a-data-uri.png\" width=\"20\" height=\"10\">");
        Element img = document.getElementById("i");
        ImagePlan plan = new ImagePlan(List.of(new PlannedImage(img, ImageOutcome.AS_IS, null, null, null, 1, 1, 0, null, "key")), 0);

        try (LogCapture capture = LogCapture.forLogger(PrintImageService.class);
                PreparedPrintImages images = service().apply(document, plan, degradations)) {
            assertThat(document.select("img")).isEmpty();
            assertThat(document.selectFirst("span.carlos-pdf-image-unavailable").attr("style")).isEqualTo("width: 20px; height: 10px");
            assertThat(images.directory()).isNull();
            assertThat(capture.messages()).isNotEmpty().allSatisfy(message -> assertThat(message).doesNotContain("no-longer"));
        }
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE);
    }

    @Test
    @DisplayName("should box an image whose file disappeared between planning and applying, not fail the attempt")
    void shouldBoxImage_whoseFileVanishedAfterPlanning() throws IOException {
        Path png = file("gone.png", TestImages.png(10, 10));
        Path svg = file("gone.svg", TestImages.svgWithOutsideReference());
        Path wbmp = file("gone.wbmp", TestImages.wbmp(8, 8));
        Document document = document("<img src=\"gone.png\"><img src=\"gone.svg\"><img src=\"gone.wbmp\"><img src=\"kept.png\">");
        file("kept.png", TestImages.png(10, 10));
        PrintImageService service = service();
        ImagePlan plan = service.plan(document, base, List.of(base), SHARE);
        Files.delete(png);
        Files.delete(svg);
        Files.delete(wbmp);

        try (PreparedPrintImages images = service.apply(document, plan, degradations)) {
            assertThat(document.select("img")).hasSize(1);
            assertThat(document.select("span.carlos-pdf-image-unavailable")).hasSize(3);
            assertThat(images.directory()).isNull();
        }
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE);
    }

    @Test
    @DisplayName("should read a file up to the limit and refuse one past it, or one that cannot be read")
    void shouldReadAtMost_theLimit() throws IOException {
        Path exact = file("exact.bin", new byte[100]);
        Path over = file("over.bin", new byte[101]);

        assertThat(PrintImageService.readAtMost(exact, 100)).hasSize(100);
        assertThat(PrintImageService.readAtMost(over, 100)).isNull();
        assertThat(PrintImageService.readAtMost(base.resolve("missing.bin"), 100)).isNull();
        assertThat(PrintImageService.readAtMost(base, 100)).isNull();
        assertThat(PrintImageService.readAtMost(file("empty.bin", new byte[0]), 100)).isEmpty();
    }

    @Test
    @DisplayName("should box an image OpenPDF cannot embed and ImageIO cannot read either")
    void shouldBoxImage_neitherLibraryCanRead() throws IOException {
        file("fake.png", "not really an image".getBytes(StandardCharsets.UTF_8));
        file("empty.png", new byte[0]);
        Document document = document("<img src=\"fake.png\"><img src=\"empty.png\">");

        try (PreparedPrintImages images = prepare(document)) {
            assertThat(document.select("img")).isEmpty();
            assertThat(document.select("span.carlos-pdf-image-unavailable")).hasSize(2);
        }
    }

    // ---- SVG --------------------------------------------------------------------------------

    @Test
    @DisplayName("should write a cleaned SVG, strip imports and stylesheet instructions, and keep the drawing")
    void shouldStripSvgImportsAndStylesheetInstructions_fromCleanedSvg() throws IOException {
        String svg = "<?xml version=\"1.0\"?><?xml-stylesheet href=\"http://127.0.0.1:1/z.css\" type=\"text/css\"?>"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"40\" height=\"20\">"
                + "<style>@import 'x.css'; @import url(y.css); .a { fill: #c00 }</style>"
                + "<rect class=\"a\" width=\"40\" height=\"20\"/><image xlink:href=\"http://127.0.0.1:1/probe.png\"/></svg>";
        file("logo.svg", svg.getBytes(StandardCharsets.UTF_8));
        Document document = document("<img id=\"i\" src=\"logo.svg\">");

        try (PreparedPrintImages images = prepare(document)) {
            Path written = pathOf(document.getElementById("i"));
            assertThat(written.getParent()).isEqualTo(images.directory());
            assertThat(written.getFileName().toString()).endsWith(".svg");
            String cleaned = Files.readString(written);
            assertThat(cleaned).doesNotContain("@import").doesNotContain("xml-stylesheet").doesNotContain("127.0.0.1")
                    .doesNotContain("x.css").doesNotContain("y.css");
            assertThat(cleaned).contains("<rect").contains(".a { fill: #c00 }");
        }
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.SVG_REFERENCES_REMOVED);
    }

    @Test
    @DisplayName("should clean a data: SVG and an inline svg, and report nothing for a clean SVG")
    void shouldCleanDataAndInlineSvg() throws IOException {
        Document document = document("<img id=\"d\" src=\"" + dataUri("image/svg+xml", TestImages.svgWithOutsideReference()) + "\">"
                + "<svg id=\"s\" width=\"10\" height=\"10\"><rect width=\"5\" height=\"5\"/><image xlink:href=\"http://127.0.0.1:1/x\"/>"
                + "<style>.a{fill:url(http://127.0.0.1:1/p)}</style></svg>");

        try (PreparedPrintImages images = prepare(document)) {
            String cleaned = Files.readString(pathOf(document.getElementById("d")));
            assertThat(cleaned).contains("<rect").doesNotContain("127.0.0.1");
            Element inline = document.getElementById("s");
            assertThat(inline.outerHtml()).contains("<rect").doesNotContain("127.0.0.1").doesNotContain("<image");
        }
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.SVG_REFERENCES_REMOVED);

        degradations.clear();
        Document clean = document("<img id=\"c\" src=\"" + dataUri("image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"4\" height=\"4\"><rect width=\"2\" height=\"2\"/></svg>".getBytes(StandardCharsets.UTF_8)) + "\">");
        try (PreparedPrintImages images = prepare(clean)) {
            assertThat(clean.getElementById("c").attr("src")).startsWith("file:");
        }
        assertThat(degradations).isEmpty();
    }

    @Test
    @DisplayName("should report the removal of a reference from an inline svg on its own, and nothing for a clean one")
    void shouldReportInlineSvgReferenceRemoval() throws IOException {
        Document dirty = document("<svg id=\"s\" width=\"10\" height=\"10\"><image xlink:href=\"http://127.0.0.1:1/x\"/></svg>");
        try (PreparedPrintImages images = prepare(dirty)) {
            assertThat(images.directory()).isNull();
            assertThat(dirty.outerHtml()).doesNotContain("127.0.0.1");
        }
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.SVG_REFERENCES_REMOVED);

        degradations.clear();
        Document clean = document("<svg width=\"10\" height=\"10\"><rect width=\"5\" height=\"5\"/></svg>");
        try (PreparedPrintImages images = prepare(clean)) {
            assertThat(clean.select("svg rect")).hasSize(1);
        }
        assertThat(degradations).isEmpty();
    }

    @Test
    @DisplayName("should write one cleaned file for an SVG used several times, from a file or a data: source")
    void shouldShareCleanedSvgFile_betweenRepeats() throws IOException {
        file("logo.svg", TestImages.svgWithOutsideReference());
        String inline = dataUri("image/svg+xml", TestImages.svgWithOutsideReference());
        Document document = document("<img id=\"1\" src=\"logo.svg\"><img id=\"2\" src=\"./logo.svg\">"
                + "<img id=\"3\" src=\"" + inline + "\"><img id=\"4\" src=\"" + inline + "\">");

        try (PreparedPrintImages images = prepare(document)) {
            assertThat(document.getElementById("2").attr("src")).isEqualTo(document.getElementById("1").attr("src"));
            assertThat(document.getElementById("4").attr("src")).isEqualTo(document.getElementById("3").attr("src"));
            assertThat(filesIn(images.directory())).hasSize(2);
        }
    }

    @Test
    @DisplayName("should box an SVG that grew past the size limit after it was planned")
    void shouldBoxSvg_thatGrewAfterPlanning() throws IOException {
        Path svg = file("grows.svg", TestImages.svgWithOutsideReference());
        Document document = document("<img src=\"grows.svg\">");
        PrintImageService service = service();
        ImagePlan plan = service.plan(document, base, List.of(base), SHARE);
        byte[] padding = new byte[(int) PrintImageService.MAX_SVG_BYTES];
        Arrays.fill(padding, (byte) ' ');
        try (OutputStream out = Files.newOutputStream(svg)) {
            out.write("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"4\" height=\"4\">".getBytes(StandardCharsets.UTF_8));
            out.write(padding);
            out.write("</svg>".getBytes(StandardCharsets.UTF_8));
        }

        try (PreparedPrintImages images = service.apply(document, plan, degradations)) {
            assertThat(document.select("img")).isEmpty();
            assertThat(document.select("span.carlos-pdf-image-unavailable")).hasSize(1);
            assertThat(images.directory()).isNull();
        }
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE);
    }

    @Test
    @DisplayName("should make no connection and leak no file when an SVG names an external DTD and entities")
    void shouldNotResolveEntities_whenCleaningSvg() throws Exception {
        Path secret = Files.writeString(tempRoot.resolve("secret.txt"), "root:x:0:0:FAKE-SECRET");
        AtomicInteger connections = new AtomicInteger();
        try (ServerSocket probe = new ServerSocket(0)) {
            Thread listener = new Thread(() -> {
                try {
                    while (true) {
                        probe.accept().close();
                        connections.incrementAndGet();
                    }
                } catch (IOException closed) {
                    // The test is over.
                }
            });
            listener.setDaemon(true);
            listener.start();
            String svg = "<?xml version=\"1.0\"?>\n<!DOCTYPE svg SYSTEM \"http://127.0.0.1:" + probe.getLocalPort() + "/evil.dtd\" [\n"
                    + "<!ENTITY remote SYSTEM \"http://127.0.0.1:" + probe.getLocalPort() + "/entity\">\n"
                    + "<!ENTITY local SYSTEM \"" + secret.toUri() + "\">\n<!ENTITY passwd SYSTEM \"file:///etc/passwd\">]>\n"
                    + "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"40\" height=\"20\"><text>&remote;&local;&passwd;</text>"
                    + "<rect width=\"4\" height=\"4\"/></svg>";
            file("evil.svg", svg.getBytes(StandardCharsets.UTF_8));
            Document document = document("<img id=\"i\" src=\"evil.svg\">");

            try (PreparedPrintImages images = prepare(document)) {
                Element img = document.getElementById("i");
                String output = img == null ? "" : Files.readString(pathOf(img));
                assertThat(output).doesNotContain("root:").doesNotContain("FAKE-SECRET");
            }
            assertThat(connections.get()).isZero();
        }
    }

    @Test
    @DisplayName("should box an SVG that cannot be parsed safely")
    void shouldBoxSvg_thatCannotBeParsed() throws IOException {
        file("broken.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect></svg>".getBytes(StandardCharsets.UTF_8));
        Document document = document("<img src=\"broken.svg\">");

        try (PreparedPrintImages images = prepare(document)) {
            assertThat(document.select("img")).isEmpty();
            assertThat(document.select("span.carlos-pdf-image-unavailable")).hasSize(1);
            // Nothing was written, so no directory was made for it either.
            assertThat(images.directory()).isNull();
        }
        assertThat(degradations).containsExactly(HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE);
        assertThat(filesIn(tempRoot)).isEmpty();
    }

    // ---- the directory ----------------------------------------------------------------------

    @Test
    @DisplayName("should delete the image directory when the step after it throws")
    void shouldDeleteImageDirectory_whenLaterStepThrows() throws IOException {
        file("pixel.wbmp", TestImages.wbmp(8, 8));
        Document document = document("<img src=\"pixel.wbmp\">");
        Path[] directory = new Path[1];

        assertThatThrownBy(() -> {
            try (PreparedPrintImages images = prepare(document)) {
                directory[0] = images.directory();
                assertThat(Files.isDirectory(directory[0])).isTrue();
                throw new IllegalStateException("layout failed");
            }
        }).isInstanceOf(IllegalStateException.class);

        assertThat(directory[0]).isNotNull();
        assertThat(directory[0]).doesNotExist();
        assertThat(filesIn(tempRoot)).isEmpty();
    }

    @Test
    @DisplayName("should delete the directory when something unexpected fails part way through applying")
    void shouldDeleteDirectory_whenApplyingFailsPartWay() throws IOException {
        file("a.wbmp", TestImages.wbmp(8, 8));
        Document document = document("<img src=\"a.wbmp\">");
        Set<HtmlPdfRenderDegradation> frozen = Collections.unmodifiableSet(EnumSet.noneOf(HtmlPdfRenderDegradation.class));

        // The conversion writes its file first; recording that it happened then fails.
        assertThatThrownBy(() -> service().prepare(document, base, List.of(base), PrintMode.NORMAL, SHARE, frozen))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThat(filesIn(tempRoot)).isEmpty();
    }

    @Test
    @DisplayName("should delete the directory and everything in it when closed, and tolerate closing twice")
    void shouldDeleteDirectoryTree_onClose() throws IOException {
        file("a.wbmp", TestImages.wbmp(8, 8));
        file("b.svg", TestImages.svgWithOutsideReference());
        Document document = document("<img src=\"a.wbmp\"><img src=\"b.svg\">");

        PreparedPrintImages images = prepare(document);
        Path directory = images.directory();
        assertThat(filesIn(directory)).hasSize(2);
        assertThat(directory.getFileName().toString()).startsWith("carlos-pdf-images-");

        images.close();
        images.close();

        assertThat(directory).doesNotExist();
    }

    @Test
    @DisplayName("should make a directory only when a file is written, and share a converted file between repeats")
    void shouldMakeDirectoryOnlyWhenNeeded_andShareFiles() throws IOException {
        file("a.png", TestImages.png(10, 10));
        file("w.wbmp", TestImages.wbmp(8, 8));

        try (PreparedPrintImages none = prepare(document("<img src=\"a.png\">"))) {
            assertThat(none.directory()).isNull();
        }
        Document document = document("<img id=\"1\" src=\"w.wbmp\"><img id=\"2\" src=\"./w.wbmp\"><img id=\"3\" src=\"w.wbmp#x\">");
        try (PreparedPrintImages some = prepare(document)) {
            assertThat(filesIn(some.directory())).hasSize(1);
            assertThat(document.getElementById("2").attr("src")).isEqualTo(document.getElementById("1").attr("src"))
                    .isEqualTo(document.getElementById("3").attr("src"));
        }
    }

    @Test
    @DisplayName("should fail when the image directory cannot be made, leaving nothing behind")
    void shouldFail_whenDirectoryCannotBeMade() throws IOException {
        file("w.wbmp", TestImages.wbmp(8, 8));
        Path notADirectory = Files.writeString(base.resolve("not-a-directory"), "x");
        PrintImageService broken = new PrintImageService(notADirectory);

        assertThatExceptionOfType(IOException.class).isThrownBy(() -> broken.prepare(document("<img src=\"w.wbmp\">"), base,
                List.of(base), PrintMode.NORMAL, SHARE, degradations));
    }

    @Test
    @DisplayName("should give each concurrent attempt its own directory")
    void shouldGiveEachAttempt_itsOwnDirectory() throws Exception {
        file("w.wbmp", TestImages.wbmp(8, 8));
        PrintImageService service = service();

        try (PreparedPrintImages first = service.prepare(document("<img src=\"w.wbmp\">"), base, List.of(base), PrintMode.NORMAL,
                SHARE, EnumSet.noneOf(HtmlPdfRenderDegradation.class));
                PreparedPrintImages second = service.prepare(document("<img src=\"w.wbmp\">"), base, List.of(base), PrintMode.NORMAL,
                        SHARE, EnumSet.noneOf(HtmlPdfRenderDegradation.class))) {
            assertThat(first.directory()).isNotEqualTo(second.directory());
        }
    }

    // ---- modes and housekeeping -------------------------------------------------------------

    @Test
    @DisplayName("should do nothing in text-only mode: no plan, no files, the document untouched")
    void shouldDoNothing_inTextOnlyMode() throws IOException {
        file("w.wbmp", TestImages.wbmp(8, 8));
        Document document = document("<img src=\"w.wbmp\"><img src=\"missing.png\">");
        String before = document.html();

        try (PreparedPrintImages images = service().prepare(document, base, List.of(base), PrintMode.TEXT_ONLY, SHARE, degradations)) {
            assertThat(images.directory()).isNull();
        }

        assertThat(document.html()).isEqualTo(before);
        assertThat(degradations).isEmpty();
    }

    @Test
    @DisplayName("should remove srcset, which the renderer does not read")
    void shouldRemoveSrcset() throws IOException {
        file("a.png", TestImages.png(10, 10));
        Document document = document("<img id=\"i\" src=\"a.png\" srcset=\"http://example.invalid/x.png 2x\">");

        try (PreparedPrintImages images = prepare(document)) {
            assertThat(document.getElementById("i").hasAttr("srcset")).isFalse();
        }
    }

    @Test
    @DisplayName("should handle a document with several kinds of image at once, in order")
    void shouldHandleMixedDocument() throws IOException {
        file("a.jpg", TestImages.jpeg(30, 20, false));
        file("w.wbmp", TestImages.wbmp(8, 8));
        file("l.svg", TestImages.svgWithOutsideReference());
        Document document = document("<img id=\"1\" src=\"a.jpg\"><img id=\"2\" src=\"w.wbmp\"><img id=\"3\" src=\"l.svg\">"
                + "<img id=\"4\" src=\"missing.png\"><img id=\"5\" src=\"" + dataUri("image/png", TestImages.png(5, 5)) + "\">");

        try (PreparedPrintImages images = prepare(document)) {
            assertThat(document.select("img").eachAttr("id")).containsExactly("1", "2", "3", "5");
            assertThat(document.select("span.carlos-pdf-image-unavailable")).hasSize(1);
            assertThat(filesIn(images.directory())).hasSize(2);
        }
        assertThat(degradations).containsExactlyInAnyOrder(HtmlPdfRenderDegradation.IMAGE_CONVERTED,
                HtmlPdfRenderDegradation.SVG_REFERENCES_REMOVED, HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE);
    }
}
