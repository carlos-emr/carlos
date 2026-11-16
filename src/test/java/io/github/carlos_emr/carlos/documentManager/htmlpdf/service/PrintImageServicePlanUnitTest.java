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
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.PngHeaders;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for the planning half of {@link PrintImageService}: what is decided for every
 * {@code <img>}, with the memory charged, and nothing written. Headers are enough to plan, so large
 * images are {@link PngHeaders#declaring(int, int) header-only PNGs}: a planner that decoded one
 * would fail with an exception instead of passing.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("PrintImageService planning Unit Tests")
class PrintImageServicePlanUnitTest {

    private static final long SHARE = 256L << 20;

    @TempDir
    Path base;

    private final PrintImageService service = new PrintImageService();

    private ImagePlan plan(String bodyHtml) {
        return plan(bodyHtml, SHARE);
    }

    private ImagePlan plan(String bodyHtml, long share) {
        Document document = Jsoup.parse("<html><body>" + bodyHtml + "</body></html>");
        return service.plan(document, base, List.of(base), share);
    }

    private static PlannedImage only(ImagePlan plan) {
        assertThat(plan.images()).hasSize(1);
        return plan.images().get(0);
    }

    private Path file(String name, byte[] bytes) {
        return TestImages.write(base, name, bytes);
    }

    private static String dataUri(String type, byte[] bytes) {
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    @DisplayName("should bound retained inline payloads together and reuse duplicate sources")
    void shouldBoundInlinePayloads_acrossTheWholePlan() {
        byte[] first = java.util.Arrays.copyOf(TestImages.png(2, 2), 100_000);
        byte[] second = first.clone();
        second[second.length - 1] = 1;
        String a = dataUri("image/png", first);
        String b = dataUri("image/png", second);

        ImagePlan plan = plan("<img src='" + a + "'><img src='" + b + "'><img src='" + a + "'>", 150_000);

        assertThat(plan.images()).extracting(PlannedImage::outcome)
                .containsExactly(ImageOutcome.AS_IS, ImageOutcome.BOX, ImageOutcome.AS_IS);
        assertThat(plan.images().get(1).reason()).isEqualTo(ImagePlan.REASON_MEMORY);
    }

    // ---- as is ------------------------------------------------------------------------------

    @Test
    @DisplayName("should use a small JPEG file as it is and charge four times its size")
    void shouldChargeJpegFourTimesFileSize_forAsIs() {
        Path jpeg = file("photo.jpg", TestImages.jpeg(200, 100, false));

        PlannedImage image = only(plan("<img src=\"photo.jpg\">"));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.AS_IS);
        assertThat(image.file()).isEqualTo(realPath(jpeg));
        assertThat(image.charge()).isEqualTo(4 * sizeOf(jpeg));
        assertThat(image.header().isJpeg()).isTrue();
        assertThat(image.orientation()).isEqualTo(1);
        assertThat(image.subsampling()).isEqualTo(1);
    }

    @Test
    @DisplayName("should embed a huge JPEG as it is, charged by its file size and not its pixels")
    void shouldUseHugeJpegAsIs_chargedBySize() throws IOException {
        // A 48-megapixel photo is 7 MB: cheap to embed whatever its resolution. Header-only stand-in:
        // a JPEG that declares 8000 x 6000 pixels in its frame header.
        byte[] jpeg = TestImages.jpeg(64, 48, false);
        byte[] declaring = jpeg.clone();
        patchJpegSize(declaring, 6000, 8000);
        file("big.jpg", declaring);

        PlannedImage image = only(plan("<img src=\"big.jpg\">"));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.AS_IS);
        assertThat(image.header().pixels()).isEqualTo(48_000_000L);
        assertThat(image.charge()).isEqualTo(4L * declaring.length);
        // Should OpenPDF fail to embed it, the fallback conversion fits the 16.7-megapixel cap: 4000 x 3000.
        assertThat(image.subsampling()).isEqualTo(2);
    }

    /** Rewrites the dimensions in the first frame header (SOF) of a JPEG. */
    private static void patchJpegSize(byte[] jpeg, int height, int width) {
        for (int i = 2; i + 9 < jpeg.length; i++) {
            if ((jpeg[i] & 0xFF) == 0xFF && ((jpeg[i + 1] & 0xFF) == 0xC0 || (jpeg[i + 1] & 0xFF) == 0xC2)) {
                jpeg[i + 5] = (byte) (height >> 8);
                jpeg[i + 6] = (byte) height;
                jpeg[i + 7] = (byte) (width >> 8);
                jpeg[i + 8] = (byte) width;
                return;
            }
        }
        throw new IllegalStateException("no frame header");
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "gif", "bmp", "tiff"})
    @DisplayName("should use a small PNG, GIF, BMP or TIFF as it is and charge 16 bytes a pixel")
    void shouldUseSmallDecodedFormat_asIs(String format) {
        byte[] bytes = switch (format) {
            case "png" -> TestImages.png(120, 80);
            case "gif" -> TestImages.gif(120, 80);
            case "bmp" -> TestImages.bmp(120, 80);
            default -> TestImages.tiff(120, 80);
        };
        file("picture." + format, bytes);

        PlannedImage image = only(plan("<img src=\"picture." + format + "\">"));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.AS_IS);
        assertThat(image.charge()).isEqualTo(16L * 120 * 80);
    }

    @Test
    @DisplayName("should use a data: image as it is without keeping its bytes in the plan")
    void shouldUseDataUri_asIs() {
        byte[] png = TestImages.png(30, 20);

        PlannedImage image = only(plan("<img src=\"" + dataUri("image/png", png) + "\">"));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.AS_IS);
        assertThat(image.file()).isNull();
        assertThat(image.data()).isNull();
        assertThat(image.charge()).isEqualTo(16L * 30 * 20);
        assertThat(image.key()).startsWith("data:");
    }

    @Test
    @DisplayName("should resolve a path with spaces and accents written as an encoded URI")
    void shouldResolveEncodedPath_withSpacesAndAccents() {
        file("my folder/photo été.png".replace("é", "e" + (char) 0x301), TestImages.png(10, 10));
        Path accented = file("sub dir/caf" + (char) 0xE9 + " photo.png", TestImages.png(10, 10));

        PlannedImage image = only(plan("<img src=\"sub%20dir/caf%C3%A9%20photo.png\">"));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.AS_IS);
        assertThat(image.file()).isEqualTo(realPath(accented));
    }

    @ParameterizedTest
    @ValueSource(strings = {"photo.jpg?v=2", "photo.jpg#section", "photo.jpg?v=2#x", "./photo.jpg", "sub/../photo.jpg"})
    @DisplayName("should resolve a file source ignoring its query and fragment")
    void shouldResolveFile_ignoringQueryAndFragment(String src) {
        Path jpeg = file("photo.jpg", TestImages.jpeg(50, 50, false));

        PlannedImage image = only(plan("<img src=\"" + src + "\">"));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.AS_IS);
        assertThat(image.file()).isEqualTo(realPath(jpeg));
    }

    @Test
    @DisplayName("should resolve a file: URI inside an allowed directory")
    void shouldResolveFileUri_insideAllowedDirectory() {
        Path png = file("a.png", TestImages.png(10, 10));

        PlannedImage image = only(plan("<img src=\"" + png.toUri() + "\">"));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.AS_IS);
    }

    // ---- conversion -------------------------------------------------------------------------

    @Test
    @DisplayName("should convert a JPEG that must be turned upright instead of embedding it")
    void shouldConvertRotatedJpeg_insteadOfEmbedding() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("htmlpdf/images/exif-orientation-6.jpg")) {
            file("phone.jpg", in.readAllBytes());
        }

        PlannedImage image = only(plan("<img src=\"phone.jpg\">"));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.CONVERT);
        assertThat(image.orientation()).isEqualTo(6);
        assertThat(image.subsampling()).isEqualTo(1);
        assertThat(image.charge()).isEqualTo(16L * image.header().pixels());
    }

    @Test
    @DisplayName("should convert a JPEG file over 32 MB instead of holding it in memory four times")
    void shouldConvertJpeg_over32Megabytes() throws IOException {
        Path big = file("huge-file.jpg", TestImages.jpeg(200, 100, false));
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(big.toFile(), "rw")) {
            // Trailing bytes after the end-of-image marker: still the same picture, in a (sparse) 33 MB file.
            raf.setLength(33L << 20);
        }

        PlannedImage image = only(plan("<img src=\"huge-file.jpg\">"));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.CONVERT);
        assertThat(image.charge()).isEqualTo(16L * 200 * 100);
    }

    @Test
    @DisplayName("should convert a PNG file over 32 MB even when its pixels fit the cap")
    void shouldConvertPng_over32Megabytes() throws IOException {
        Path big = file("huge-file.png", PngHeaders.declaring(100, 100));
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(big.toFile(), "rw")) {
            raf.setLength(33L << 20);
        }

        PlannedImage image = only(plan("<img src=\"huge-file.png\">"));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.CONVERT);
        assertThat(image.subsampling()).isEqualTo(1);
        assertThat(image.charge()).isEqualTo(16L * 100 * 100);
    }

    @Test
    @DisplayName("should convert a rotated progressive JPEG and charge its native decoding memory")
    void shouldChargeNativeMemory_forRotatedProgressiveJpeg() {
        byte[] progressive = TestImages.withOrientation(TestImages.jpeg(100, 50, true), 6);
        file("progressive.jpg", progressive);

        PlannedImage image = only(plan("<img src=\"progressive.jpg\">"));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.CONVERT);
        assertThat(image.orientation()).isEqualTo(6);
        assertThat(image.charge()).isEqualTo(16L * 100 * 50 + 4L * 100 * 50);
    }

    @Test
    @DisplayName("should convert a format OpenPDF cannot embed, such as WBMP")
    void shouldConvertWbmp_whichOpenPdfCannotEmbed() {
        file("pixel.wbmp", TestImages.wbmp(16, 16));

        PlannedImage image = only(plan("<img src=\"pixel.wbmp\">"));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.CONVERT);
        assertThat(image.subsampling()).isEqualTo(1);
    }

    @Test
    @DisplayName("should convert a decoded format over the per-image cap, at the subsampling that fits it")
    void shouldConvertLargePng_atSubsamplingThatFitsCap() throws IOException {
        // 6600 x 10200 (67 megapixels) against the 16.7-megapixel cap of a 256 MB share: every second pixel
        // gives 3300 x 5100 = 16.83 megapixels, just over it, so every third: 2200 x 3400. With the
        // 25-megapixel cap of a larger share, every second pixel is enough.
        file("scan.png", PngHeaders.declaring(6600, 10200));

        PlannedImage image = only(plan("<img src=\"scan.png\">"));
        PlannedImage withBiggerShare = only(plan("<img src=\"scan.png\">", 1L << 30));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.CONVERT);
        assertThat(image.subsampling()).isEqualTo(3);
        assertThat(image.charge()).isEqualTo(16L * 2200 * 3400);
        assertThat(withBiggerShare.subsampling()).isEqualTo(2);
        assertThat(withBiggerShare.charge()).isEqualTo(16L * 3300 * 5100);
    }

    @Test
    @DisplayName("should convert a 25-megapixel image when the share cannot hold it, and bound the per-image cap by the share")
    void shouldBoundCap_byTheShare() {
        file("big.png", PngHeaders.declaring(5000, 5000));

        PlannedImage generous = only(plan("<img src=\"big.png\">", 1L << 30));
        PlannedImage tight = only(plan("<img src=\"big.png\">", SHARE));

        assertThat(generous.outcome()).isEqualTo(ImageOutcome.AS_IS);
        assertThat(tight.outcome()).isEqualTo(ImageOutcome.CONVERT);
        assertThat(tight.subsampling()).isEqualTo(2);
    }

    @Test
    @DisplayName("should convert an image over 25 megapixels even when the share could hold it")
    void shouldConvertImageOver25Megapixels_evenWhenShareHoldsIt() {
        // 6000 x 5000 is 30 megapixels: 480 MB decoded, inside a 1 GB share, but over the 25-megapixel cap.
        file("scan.png", PngHeaders.declaring(6000, 5000));

        PlannedImage image = only(plan("<img src=\"scan.png\">", 1L << 30));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.CONVERT);
        assertThat(image.subsampling()).isEqualTo(2);
        assertThat(image.charge()).isEqualTo(16L * 3000 * 2500);
    }

    // ---- the memory share -------------------------------------------------------------------

    @Test
    @DisplayName("should plan the largest images smaller when the document is over its share, largest first")
    void shouldPlanLargestImagesSmaller_whenOverShare() {
        file("a.png", PngHeaders.declaring(4000, 3000));
        file("b.png", PngHeaders.declaring(4000, 3000));
        file("c.png", PngHeaders.declaring(4000, 3000));
        file("small.png", TestImages.png(100, 100));

        ImagePlan plan = plan("<img src=\"a.png\"><img src=\"b.png\"><img src=\"c.png\"><img src=\"small.png\">");

        assertThat(plan.totalCharge()).isLessThanOrEqualTo(SHARE);
        List<PlannedImage> images = plan.images();
        for (int i = 0; i < 3; i++) {
            assertThat(images.get(i).outcome()).isEqualTo(ImageOutcome.CONVERT);
            assertThat(images.get(i).subsampling()).isGreaterThan(1);
            assertThat(images.get(i).charge()).isLessThan(16L * 4000 * 3000);
        }
        // The small image is not touched.
        assertThat(images.get(3).outcome()).isEqualTo(ImageOutcome.AS_IS);
        assertThat(images.get(3).charge()).isEqualTo(16L * 100 * 100);
        assertThat(images.get(0).subsampling()).isEqualTo(images.get(1).subsampling()).isEqualTo(images.get(2).subsampling());
    }

    @Test
    @DisplayName("should reduce only the images above the common level and leave the others alone")
    void shouldLeaveSmallerImagesAlone_whenReducingLargest() {
        file("big1.png", PngHeaders.declaring(4000, 3000));
        file("big2.png", PngHeaders.declaring(4000, 3000));
        file("big3.png", PngHeaders.declaring(4000, 3000));
        file("medium.png", PngHeaders.declaring(1000, 1000));

        ImagePlan plan = plan("<img src=\"big1.png\"><img src=\"big2.png\"><img src=\"big3.png\"><img src=\"medium.png\">");

        // Three 192 MB images and a 16 MB one do not fit 256 MB. Each large one goes to the next whole
        // subsampling step, 2000 x 1500 (48 MB); the 16 MB one is below that level and keeps its size.
        assertThat(plan.images().subList(0, 3)).allSatisfy(image -> {
            assertThat(image.outcome()).isEqualTo(ImageOutcome.CONVERT);
            assertThat(image.subsampling()).isEqualTo(2);
            assertThat(image.charge()).isEqualTo(16L * 2000 * 1500);
        });
        assertThat(plan.images().get(3).outcome()).isEqualTo(ImageOutcome.AS_IS);
        assertThat(plan.images().get(3).charge()).isEqualTo(16L * 1000 * 1000);
        assertThat(plan.totalCharge()).isEqualTo(3 * 16L * 2000 * 1500 + 16L * 1000 * 1000).isLessThanOrEqualTo(SHARE);
    }

    @Test
    @DisplayName("should convert a JPEG that still does not fit after the decoded images were reduced")
    void shouldConvertJpeg_whenItStillDoesNotFit() {
        byte[] jpeg = TestImages.jpeg(800, 600, false);
        file("photo.jpg", jpeg);
        // The share holds a quarter of the JPEG's own charge: embedding it cannot fit, converting can.
        long budget = jpeg.length;

        PlannedImage image = only(plan("<img src=\"photo.jpg\">", budget));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.CONVERT);
        assertThat(image.subsampling()).isGreaterThan(1);
        assertThat(image.charge()).isLessThanOrEqualTo(budget);
    }

    @Test
    @DisplayName("should box every raster image when the render has no memory share")
    void shouldBoxEveryRasterImage_withoutSlot() {
        file("a.png", TestImages.png(10, 10));
        file("b.jpg", TestImages.jpeg(10, 10, false));
        file("c.svg", TestImages.svgWithOutsideReference());

        ImagePlan plan = plan("<img src=\"a.png\"><img src=\"b.jpg\"><img src=\"c.svg\"><img src=\"missing.png\">"
                + "<img src=\"" + dataUri("image/png", TestImages.png(5, 5)) + "\">", 0);

        assertThat(plan.images().get(0).reason()).isEqualTo(ImagePlan.REASON_NO_SLOT);
        assertThat(plan.images().get(1).reason()).isEqualTo(ImagePlan.REASON_NO_SLOT);
        assertThat(plan.images().get(2).outcome()).isEqualTo(ImageOutcome.CLEAN_SVG);
        assertThat(plan.images().get(3).reason()).isEqualTo(PrintImageService.REASON_MISSING);
        assertThat(plan.images().get(4).reason()).isEqualTo(ImagePlan.REASON_NO_SLOT);
        assertThat(plan.images()).filteredOn(image -> image.outcome() == ImageOutcome.BOX).hasSize(4);
        assertThat(plan.hasImagesOmittedForWantOfSlot()).isTrue();
        assertThat(plan.totalCharge()).isZero();
    }

    @Test
    @DisplayName("should not claim images were omitted when the document has no raster image to omit")
    void shouldNotReportOmitted_whenNothingWasOmitted() {
        assertThat(plan("<p>no images</p>", 0).hasImagesOmittedForWantOfSlot()).isFalse();
        assertThat(plan("<img src=\"missing.png\">", 0).hasImagesOmittedForWantOfSlot()).isFalse();
    }

    @Test
    @DisplayName("should box the last images when even one pixel each does not fit the share")
    void shouldBoxLastImages_whenEvenOnePixelEachDoesNotFit() {
        for (int i = 0; i < 5; i++) {
            file("p" + i + ".png", PngHeaders.declaring(3000, 3000));
        }
        // 16 bytes a pixel, and room for three such pixels in all.
        ImagePlan plan = plan("<img src=\"p0.png\"><img src=\"p1.png\"><img src=\"p2.png\"><img src=\"p3.png\"><img src=\"p4.png\">", 50);

        assertThat(plan.images().stream().map(PlannedImage::outcome).toList())
                .containsExactly(ImageOutcome.CONVERT, ImageOutcome.CONVERT, ImageOutcome.CONVERT, ImageOutcome.BOX, ImageOutcome.BOX);
        assertThat(plan.images().get(3).reason()).isEqualTo(ImagePlan.REASON_MEMORY);
        assertThat(plan.totalCharge()).isLessThanOrEqualTo(50);
    }

    @Test
    @DisplayName("should give the same plan for the same document and share every time")
    void shouldGiveSamePlan_forSameDocumentAndShare() {
        file("a.png", PngHeaders.declaring(4000, 3000));
        file("b.png", PngHeaders.declaring(3000, 3000));
        file("c.jpg", TestImages.jpeg(300, 200, false));
        String html = "<img src=\"a.png\"><img src=\"b.png\"><img src=\"c.jpg\"><img src=\"a.png\">";

        List<String> first = signature(plan(html, 100L << 20));
        List<String> second = signature(plan(html, 100L << 20));

        assertThat(second).isEqualTo(first);
    }

    private static List<String> signature(ImagePlan plan) {
        return plan.images().stream().map(image -> image.outcome() + "/" + image.subsampling() + "/" + image.charge()
                + "/" + image.reason()).toList();
    }

    @Test
    @DisplayName("should charge a repeated source once and plan every repeat the same way")
    void shouldCountRepeatedSources_once() {
        file("logo.png", TestImages.png(100, 50));
        String data = dataUri("image/png", TestImages.png(20, 20));

        ImagePlan plan = plan("<img src=\"logo.png\"><img src=\"logo.png\"><img src=\"./logo.png?x=1\">"
                + "<img src=\"" + data + "\"><img src=\"" + data + "\">");

        assertThat(plan.images().stream().map(PlannedImage::charge).toList())
                .containsExactly(16L * 100 * 50, 0L, 0L, 16L * 20 * 20, 0L);
        assertThat(plan.totalCharge()).isEqualTo(16L * 100 * 50 + 16L * 20 * 20);
        assertThat(plan.images().get(0).key()).isEqualTo(plan.images().get(1).key()).isEqualTo(plan.images().get(2).key());
        assertThat(plan.images().get(3).key()).isEqualTo(plan.images().get(4).key());
    }

    @Test
    @DisplayName("should reduce a repeated large image once, for all its occurrences")
    void shouldReduceRepeatedImage_onceForAllOccurrences() {
        file("scan.png", PngHeaders.declaring(6600, 10200));

        ImagePlan plan = plan("<img src=\"scan.png\"><img src=\"scan.png\">");

        assertThat(plan.images().get(0).subsampling()).isEqualTo(plan.images().get(1).subsampling());
        assertThat(plan.images().get(1).charge()).isZero();
        assertThat(plan.totalCharge()).isEqualTo(plan.images().get(0).charge());
    }

    // ---- boxes ------------------------------------------------------------------------------

    @Test
    @DisplayName("should box a header that declares more than 250 megapixels without decoding it")
    void shouldBoxHeaderOverLimit_withoutDecoding() {
        file("bomb.png", PngHeaders.declaring(20_000, 20_000));

        PlannedImage image = only(plan("<img src=\"bomb.png\">"));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.BOX);
        assertThat(image.reason()).isEqualTo(PrintImageService.REASON_TOO_MANY_PIXELS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.invalid/x.png", "https://example.invalid/x.png", "//example.invalid/x.png",
            "ftp://example.invalid/x.png", "jar:http://example.invalid/x.jar!/x.png", "javascript:alert(1)",
            "file://example.invalid/x.png"})
    @DisplayName("should never plan a remote or non-file source")
    void shouldBoxRemoteSources_inEverySpelling(String src) {
        PlannedImage image = only(plan("<img src=\"" + src + "\">"));

        assertThat(image.outcome()).isEqualTo(ImageOutcome.BOX);
        assertThat(image.reason()).isEqualTo(PrintImageService.REASON_NOT_LOCAL);
    }

    @Test
    @DisplayName("should box a file outside the allowed directories, however the path is written")
    void shouldBoxFile_outsideAllowedDirectories() throws IOException {
        Path outside = Files.createTempFile("carlos-outside-", ".png");
        try {
            Files.write(outside, TestImages.png(10, 10));
            Path sibling = Files.createDirectory(base.resolveSibling(base.getFileName() + "-sibling"));
            Files.write(sibling.resolve("x.png"), TestImages.png(10, 10));

            assertThat(only(plan("<img src=\"" + outside.toUri() + "\">")).reason()).isEqualTo(PrintImageService.REASON_OUTSIDE);
            assertThat(only(plan("<img src=\"../" + sibling.getFileName() + "/x.png\">")).reason()).isEqualTo(PrintImageService.REASON_OUTSIDE);
            assertThat(only(plan("<img src=\"/etc/passwd\">")).reason()).isEqualTo(PrintImageService.REASON_OUTSIDE);
            assertThat(only(plan("<img src=\"" + base.relativize(outside.getParent()).toString().replace("..", "..") + "\">")).outcome())
                    .isEqualTo(ImageOutcome.BOX);
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    @DisplayName("should box a symbolic link that leads out of the allowed directories")
    void shouldBoxSymlink_leadingOutside() throws IOException {
        Path outside = Files.createTempFile("carlos-outside-", ".png");
        try {
            Files.write(outside, TestImages.png(10, 10));
            try {
                Files.createSymbolicLink(base.resolve("link.png"), outside);
            } catch (UnsupportedOperationException e) {
                org.junit.jupiter.api.Assumptions.abort("symbolic links are not available here");
            }

            assertThat(only(plan("<img src=\"link.png\">")).reason()).isEqualTo(PrintImageService.REASON_OUTSIDE);
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    @DisplayName("should box a missing file, a directory and an empty file, each for its own reason")
    void shouldBoxMissingFileDirectoryAndEmptyFile_eachForItsOwnReason() throws IOException {
        Files.createDirectory(base.resolve("folder.png"));
        file("empty.png", new byte[0]);

        ImagePlan plan = plan("<img src=\"missing.png\"><img src=\"folder.png\"><img src=\"empty.png\">");

        assertThat(plan.images().stream().map(PlannedImage::reason).toList())
                .containsExactly(PrintImageService.REASON_MISSING, PrintImageService.REASON_MISSING, PrintImageService.REASON_EMPTY);
    }

    @Test
    @DisplayName("should box a raw space, an empty source, a missing source and the unavailable marker")
    void shouldBoxUnusableSources_ofEveryKind() {
        file("my photo.png", TestImages.png(10, 10));

        ImagePlan plan = plan("<img src=\"my photo.png\"><img src=\"\"><img><img src=\"   \">"
                + "<img src=\"a.png\" data-carlos-image-unavailable=\"\"><img src=\"back\\slash.png\">");

        assertThat(plan.images().stream().map(PlannedImage::reason).toList()).containsExactly(
                PrintImageService.REASON_INVALID_SOURCE, PrintImageService.REASON_NO_SOURCE, PrintImageService.REASON_NO_SOURCE,
                PrintImageService.REASON_NO_SOURCE, PrintImageService.REASON_MARKED_UNAVAILABLE, PrintImageService.REASON_INVALID_SOURCE);
    }

    @Test
    @DisplayName("should box a PDF, HEIC, WebP and unknown bytes, a corrupt data: URI and a non-base64 one")
    void shouldBoxPdfHeicWebpAndUnknownBytes_andBadDataUris() throws IOException {
        file("doc.png", TestImages.pdf());
        file("photo.heic", TestImages.heic());
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("htmlpdf/images/opaque-4x4.webp")) {
            file("photo.webp", in.readAllBytes());
        }
        file("unknown.png", "just some text".getBytes());

        ImagePlan plan = plan("<img src=\"doc.png\"><img src=\"photo.heic\"><img src=\"photo.webp\"><img src=\"unknown.png\">"
                + "<img src=\"data:image/png;base64,!!!\"><img src=\"data:image/png,%89PNG\"><img src=\"data:image/png;base64,AAAA\">");

        // The MIME decoder drops characters outside the base64 alphabet, so "!!!" is an empty payload.
        assertThat(plan.images().stream().map(PlannedImage::reason).toList()).containsExactly(PrintImageService.REASON_PDF,
                PrintImageService.REASON_UNSUPPORTED_FORMAT, PrintImageService.REASON_UNSUPPORTED_FORMAT,
                PrintImageService.REASON_UNSUPPORTED_FORMAT, PrintImageService.REASON_EMPTY,
                PrintImageService.REASON_INVALID_SOURCE, PrintImageService.REASON_UNSUPPORTED_FORMAT);
    }

    @Test
    @DisplayName("should box a data: PDF as well")
    void shouldBoxDataPdf_asWell() {
        PlannedImage image = only(plan("<img src=\"" + dataUri("application/pdf", TestImages.pdf()) + "\">"));

        assertThat(image.reason()).isEqualTo(PrintImageService.REASON_PDF);
    }

    // ---- SVG --------------------------------------------------------------------------------

    @Test
    @DisplayName("should plan an SVG file and a data: SVG to be cleaned, and an inline svg in place")
    void shouldPlanSvg_toBeCleaned() {
        Path svg = file("logo.svg", TestImages.svgWithOutsideReference());

        ImagePlan plan = plan("<img src=\"logo.svg\"><img src=\"" + dataUri("image/svg+xml", TestImages.svgWithOutsideReference())
                + "\"><svg width=\"10\" height=\"10\"><rect width=\"5\" height=\"5\"/><svg><circle r=\"2\"/></svg></svg>");

        assertThat(plan.images()).hasSize(3);
        PlannedImage fromFile = plan.images().get(0);
        PlannedImage fromData = plan.images().get(1);
        PlannedImage inline = plan.images().get(2);
        assertThat(fromFile.outcome()).isEqualTo(ImageOutcome.CLEAN_SVG);
        assertThat(fromFile.file()).isEqualTo(realPath(svg));
        assertThat(fromData.outcome()).isEqualTo(ImageOutcome.CLEAN_SVG);
        assertThat(fromData.data()).isEqualTo(TestImages.svgWithOutsideReference());
        assertThat(inline.outcome()).isEqualTo(ImageOutcome.CLEAN_SVG);
        assertThat(inline.element().normalName()).isEqualTo("svg");
        assertThat(inline.file()).isNull();
        assertThat(plan.totalCharge()).isZero();
    }

    @Test
    @DisplayName("should recognise an SVG behind a long prolog")
    void shouldRecogniseSvg_behindLongProlog() {
        String prolog = "<?xml version=\"1.0\"?><!-- " + "x".repeat(2500) + " --><!DOCTYPE svg><svg xmlns=\"http://www.w3.org/2000/svg\"/>";
        file("long.svg", prolog.getBytes());

        assertThat(only(plan("<img src=\"long.svg\">")).outcome()).isEqualTo(ImageOutcome.CLEAN_SVG);
    }

    @Test
    @DisplayName("should box an SVG file that is too large to parse")
    void shouldBoxSvg_tooLarge() throws IOException {
        Path big = base.resolve("big.svg");
        try (java.io.OutputStream out = Files.newOutputStream(big)) {
            out.write("<svg xmlns=\"http://www.w3.org/2000/svg\">".getBytes());
            byte[] block = new byte[1 << 20];
            java.util.Arrays.fill(block, (byte) ' ');
            for (int i = 0; i < 17; i++) {
                out.write(block);
            }
            out.write("</svg>".getBytes());
        }

        assertThat(only(plan("<img src=\"big.svg\">")).reason()).isEqualTo(PrintImageService.REASON_SVG_TOO_LARGE);
    }

    // ---- housekeeping -----------------------------------------------------------------------

    @Test
    @DisplayName("should leave the document and the disk as it found them")
    void shouldWriteNothing_andChangeNothing() throws IOException {
        file("a.png", PngHeaders.declaring(6600, 10200));
        file("b.jpg", TestImages.jpeg(30, 30, false));
        Document document = Jsoup.parse("<html><body><img src=\"a.png\"><img src=\"b.jpg\"><svg><rect/></svg></body></html>");
        String before = document.html();
        List<String> files = listing();

        service.plan(document, base, List.of(base), SHARE);

        assertThat(document.html()).isEqualTo(before);
        assertThat(listing()).isEqualTo(files);
    }

    private List<String> listing() throws IOException {
        try (var stream = Files.walk(base)) {
            return stream.map(path -> base.relativize(path) + ":" + path.toFile().length()).sorted().toList();
        }
    }

    @Test
    @DisplayName("should plan nothing for a document without images")
    void shouldPlanNothing_whenNoImages() {
        ImagePlan plan = plan("<p>text only</p>");

        assertThat(plan.images()).isEmpty();
        assertThat(plan.totalCharge()).isZero();
    }

    @Test
    @DisplayName("should keep the plan in document order")
    void shouldKeepDocumentOrder_inThePlan() {
        file("a.png", TestImages.png(5, 5));

        ImagePlan plan = plan("<p><img id=\"1\" src=\"missing.png\"></p><div><svg id=\"2\"/><img id=\"3\" src=\"a.png\"></div><img id=\"4\" src=\"x\">");

        assertThat(plan.images().stream().map(image -> image.element().id()).toList()).containsExactly("1", "2", "3", "4");
    }

    @Test
    @DisplayName("should reject null arguments")
    void shouldRejectNull_whenArgumentMissing() {
        Document document = Jsoup.parse("<p>x</p>");

        assertThatNullPointerException().isThrownBy(() -> service.plan(null, base, List.of(base), SHARE));
        assertThatNullPointerException().isThrownBy(() -> service.plan(document, null, List.of(base), SHARE));
        assertThatNullPointerException().isThrownBy(() -> service.plan(document, base, null, SHARE));
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
