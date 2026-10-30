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

import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestImages;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestPdfs;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.cos.COSName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openpdf.text.Image;
import org.xhtmlrenderer.extend.FSImage;
import org.xhtmlrenderer.extend.Size;
import org.xhtmlrenderer.pdf.ITextFSImage;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.SvgImage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Unit tests for {@link BoundedOutputDevice}: the bitmap Flying Saucer makes of an SVG it cannot draw as vectors
 * is as large as the SVG's box in device dots, 20 to a CSS pixel, and the device keeps it within a bound.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("BoundedOutputDevice Unit Tests")
class BoundedOutputDeviceUnitTest {

    private static final float DOTS_PER_POINT = 26.666666f;
    private static final long BOUND = 1_000_000L;

    private static final String NAMESPACE = "xmlns='http://www.w3.org/2000/svg'";
    private static final String PLAIN = "<svg " + NAMESPACE + " width='100' height='100'><rect width='100' height='100' fill='#c00'/></svg>";
    private static final String GRADIENT = "<svg " + NAMESPACE + " width='100' height='100'><defs><linearGradient id='g'><stop offset='0' stop-color='#000'/>"
            + "<stop offset='1' stop-color='#fff'/></linearGradient></defs><rect width='100' height='100' fill='url(#g)'/></svg>";

    private final BoundedOutputDevice device = new BoundedOutputDevice(DOTS_PER_POINT, BOUND);

    private static SvgImage svg(String xml, int width, int height) {
        return new SvgImage(xml.getBytes(StandardCharsets.UTF_8), new Size(width, height), "test");
    }

    @Test
    @DisplayName("should draw a large SVG of vectors as it is, however large its box")
    void shouldKeepVectorSvg_whateverItsSize() {
        SvgImage plain = svg(PLAIN, 20_000, 20_000);

        assertThat(device.bounded(plain)).isSameAs(plain);
    }

    @Test
    @DisplayName("should draw an SVG whose box is within the bound from a bitmap of the box's own size")
    void shouldMakeBitmapOfTheBoxSize_whenBoxIsWithinTheBound() {
        SvgImage gradient = svg(GRADIENT, 1_000, 1_000);

        FSImage drawn = device.bounded(gradient);

        assertThat(drawn).isInstanceOf(BoundedOutputDevice.BoundedSvgRaster.class);
        Image bitmap = ((ITextFSImage) drawn).getImage();
        assertThat(bitmap.getWidth()).isEqualTo(1_000);
        assertThat(bitmap.getHeight()).isEqualTo(1_000);
        // One dot more and the box is over the bound.
        Image over = ((ITextFSImage) device.bounded(svg(GRADIENT, 1_000, 1_001))).getImage();
        assertThat((long) over.getWidth() * over.getHeight()).isLessThanOrEqualTo(BOUND).isGreaterThan(BOUND * 99 / 100);
        assertThat(over.getWidth() < 1_000 || over.getHeight() < 1_001).isTrue();
    }

    @Test
    @DisplayName("should make the bitmap of a small SVG once however often it is drawn, as tiles of a background are")
    void shouldMakeBitmapOnce_forASmallSvgDrawnOften() {
        SvgImage tile = svg(GRADIENT, 100, 100);

        FSImage first = device.bounded(tile);
        FSImage second = device.bounded(tile);

        assertThat(second).isSameAs(first).isInstanceOf(BoundedOutputDevice.BoundedSvgRaster.class);
        assertThat(((ITextFSImage) second).getImage()).isSameAs(((ITextFSImage) first).getImage());
        // At the size of its box, not enlarged to the bound.
        assertThat(((ITextFSImage) second).getImage().getWidth()).isEqualTo(100);
        assertThat(((ITextFSImage) second).getImage().getHeight()).isEqualTo(100);
        // Another SVG is another bitmap.
        assertThat(device.bounded(svg(GRADIENT, 100, 100))).isNotSameAs(first);
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {
            "<defs><linearGradient id='g'><stop offset='0' stop-color='#000'/></linearGradient></defs><rect width='1' height='1' fill='url(#g)'/>",
            "<defs><radialGradient id='g'><stop offset='0' stop-color='#000'/></radialGradient></defs><rect width='1' height='1' fill='url(#g)'/>",
            "<defs><pattern id='p' width='2' height='2' patternUnits='userSpaceOnUse'><rect width='1' height='1'/></pattern></defs><rect width='9' height='9' fill='url(#p)'/>",
            "<defs><filter id='f'><feGaussianBlur stdDeviation='1'/></filter></defs><rect width='9' height='9' filter='url(#f)'/>",
            "<defs><mask id='m'><rect width='9' height='9' fill='#fff'/></mask></defs><rect width='9' height='9' mask='url(#m)'/>",
            "<rect width='9' height='9' opacity='0.5'/>",
            "<rect width='9' height='9' style='opacity: 0.5'/>",
            "<style>rect { opacity: 0.5 }</style><rect width='9' height='9'/>",
    })
    @DisplayName("should draw a large SVG that Flying Saucer would turn into a bitmap from a bounded bitmap")
    void shouldBoundBitmap_ofLargeSvgThatCannotBeDrawnAsVectors(String content) {
        SvgImage large = svg("<svg " + NAMESPACE + " width='9' height='9'>" + content + "</svg>", 20_000, 10_000);

        FSImage drawn = device.bounded(large);

        assertThat(drawn).isInstanceOf(BoundedOutputDevice.BoundedSvgRaster.class);
        // The box does not change: the bitmap is stretched to it.
        assertThat(drawn.getWidth()).isEqualTo(20_000);
        assertThat(drawn.getHeight()).isEqualTo(10_000);
        Image bitmap = ((ITextFSImage) drawn).getImage();
        assertThat((long) bitmap.getWidth() * bitmap.getHeight()).isLessThanOrEqualTo(BOUND).isGreaterThan(BOUND * 9 / 10);
        assertThat((double) bitmap.getWidth() / bitmap.getHeight()).isCloseTo(2.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("should make the bitmap of an SVG once, however often the SVG is drawn")
    void shouldMakeBitmapOnce_forTheSameSvg() {
        SvgImage large = svg(GRADIENT, 20_000, 20_000);

        FSImage first = device.bounded(large);
        FSImage second = device.bounded(large);

        assertThat(second).isSameAs(first);
        assertThat(((ITextFSImage) first).getImage()).isSameAs(((ITextFSImage) second).getImage());
    }

    @Test
    @DisplayName("should keep the bound whatever the shape of the box, a product that overflows an int and a side of one dot included")
    void shouldKeepBound_forAnyShapeOfBox() {
        for (int[] box : new int[][]{{20_000, 20_000}, {40_000, 100}, {100, 40_000}, {2_000_000, 3}, {1_001, 1_000},
                {65_536, 65_536}, {1, 2_000_000_000}, {2_000_000_000, 1}, {46_341, 46_341}}) {
            FSImage drawn = device.bounded(svg(GRADIENT, box[0], box[1]));
            String shape = box[0] + " x " + box[1];

            assertThat(drawn).as(shape).isInstanceOf(BoundedOutputDevice.BoundedSvgRaster.class);
            Image bitmap = ((ITextFSImage) drawn).getImage();

            assertThat((long) bitmap.getWidth() * bitmap.getHeight()).as(shape).isLessThanOrEqualTo(BOUND);
            assertThat(bitmap.getWidth()).as(shape).isGreaterThanOrEqualTo(1);
            assertThat(bitmap.getHeight()).as(shape).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("should draw every other image as it is")
    void shouldPassImages_otherThanSvgThrough() throws Exception {
        ITextFSImage picture = new ITextFSImage(Image.getInstance(TestImages.png(30, 30)));

        assertThat(device.bounded(picture)).isSameAs(picture);
    }

    @Test
    @DisplayName("should scale the wrapper like the SVG it stands for")
    void shouldScaleWrapper_likeTheSvg() {
        FSImage drawn = device.bounded(svg(GRADIENT, 20_000, 20_000));

        FSImage scaled = drawn.scale(30_000, 10_000);

        assertThat(scaled).isInstanceOf(BoundedOutputDevice.BoundedSvgRaster.class);
        assertThat(scaled.getWidth()).isEqualTo(30_000);
        assertThat(scaled.getHeight()).isEqualTo(10_000);

        Object copy = ((BoundedOutputDevice.BoundedSvgRaster) drawn).clone();

        assertThat(copy).isInstanceOf(BoundedOutputDevice.BoundedSvgRaster.class);
        Image bitmap = ((ITextFSImage) copy).getImage();
        assertThat((long) bitmap.getWidth() * bitmap.getHeight()).isLessThanOrEqualTo(BOUND);
        Image scaledBitmap = ((ITextFSImage) scaled).getImage();
        assertThat((long) scaledBitmap.getWidth() * scaledBitmap.getHeight()).isLessThanOrEqualTo(BOUND);
    }

    @ParameterizedTest(name = "[{index}] heap of {0} bytes bounds an SVG to {1} pixels")
    @CsvSource({
            "0, 1000000",
            "104857600, 1000000",
            "268435456, 1048576",
            "1073741824, 4194304",
            "2147483648, 8000000",
            "68719476736, 8000000",
    })
    @DisplayName("should take its default bound from the heap, between one and eight million pixels")
    void shouldDefaultBound_fromTheHeap(long heap, long expected) {
        assertThat(BoundedOutputDevice.defaultMaxRasterPixels(heap)).isEqualTo(expected);
        assertThat(BoundedOutputDevice.defaultMaxRasterPixels()).isBetween(1_000_000L, 8_000_000L)
                .isEqualTo(BoundedOutputDevice.defaultMaxRasterPixels(Runtime.getRuntime().maxMemory()));
        assertThat(new BoundedOutputDevice(DOTS_PER_POINT).maxRasterPixels()).isEqualTo(BoundedOutputDevice.defaultMaxRasterPixels());
    }

    @Test
    @DisplayName("should say an SVG is not drawn as vectors when Flying Saucer's own test cannot be read or run")
    void shouldTreatSvgAsBitmap_whenTheVectorTestFails() throws Exception {
        SvgImage plain = svg(PLAIN, 10, 10);

        assertThat(BoundedOutputDevice.drawableAsVector(null, plain)).isFalse();
        // A method that throws, and one that does not answer a boolean.
        assertThat(BoundedOutputDevice.drawableAsVector(Object.class.getMethod("wait"), plain)).isFalse();
        assertThat(BoundedOutputDevice.drawableAsVector(Object.class.getMethod("hashCode"), plain)).isFalse();
        // And Flying Saucer's own, for the same SVG, reads true.
        java.lang.reflect.Method own = SvgImage.class.getDeclaredMethod("isDrawableAsVector");
        own.setAccessible(true);
        assertThat(BoundedOutputDevice.drawableAsVector(own, plain)).isTrue();
        assertThat(BoundedOutputDevice.drawableAsVector(own, svg(GRADIENT, 10, 10))).isFalse();
    }

    @Test
    @DisplayName("should refuse a bound below one pixel")
    void shouldRefuse_boundBelowOne() {
        assertThatIllegalArgumentException().isThrownBy(() -> new BoundedOutputDevice(DOTS_PER_POINT, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new BoundedOutputDevice(DOTS_PER_POINT, -5));

        // One pixel is the smallest bound there is, and an SVG of a hundred dots is drawn from it.
        BoundedOutputDevice single = new BoundedOutputDevice(DOTS_PER_POINT, 1);
        Image bitmap = ((ITextFSImage) single.bounded(svg(GRADIENT, 10, 10))).getImage();
        assertThat(single.maxRasterPixels()).isEqualTo(1);
        assertThat((long) bitmap.getWidth() * bitmap.getHeight()).isEqualTo(1);
    }

    @Test
    @DisplayName("should be the device of the renderers the pipeline makes")
    void shouldBeTheDevice_ofRestrictedRenderers(@TempDir Path directory) {
        ITextRenderer renderer = LocalOnlyUserAgent.createRestrictedRenderer(List.of(directory), new ITextFontResolver());

        assertThat(renderer.getOutputDevice()).isInstanceOf(BoundedOutputDevice.class);
    }

    /** Renders the HTML with a device bounded to {@link #BOUND}, within a minute, as the pipeline's renderers are made. */
    private static byte[] render(Path directory, String html) {
        return render(new BoundedOutputDevice(DOTS_PER_POINT, BOUND), directory, html);
    }

    private static byte[] render(BoundedOutputDevice bounded, Path directory, String html) {
        LocalOnlyUserAgent agent = new LocalOnlyUserAgent(bounded, 20, List.of(directory));
        ITextRenderer renderer = new ITextRenderer(DOTS_PER_POINT, 20, bounded, agent, new ITextFontResolver());
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();

        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            renderer.setDocumentFromString(html, directory.toUri().toString());
            renderer.layout();
            renderer.createPDF(pdf, true);
        });
        return pdf.toByteArray();
    }

    @Test
    @DisplayName("should draw a page of huge gradient SVGs from bounded bitmaps, in the time and memory a small bitmap takes")
    void shouldRenderHugeGradientSvgs_fromBoundedBitmaps(@TempDir Path directory) throws Exception {
        // 1000 x 1000 CSS px is 20,000 x 20,000 device dots: 400 million pixels, about 1.6 GB as the bitmap
        // Flying Saucer would make, and far more than this test JVM has.
        Files.writeString(directory.resolve("big.svg"), GRADIENT);
        String html = "<html><body style='margin:0'><img src='big.svg' width='1000' height='1000'/><img src='big.svg' width='3000' height='20'/>"
                + "<img src='big.svg' width='100' height='100'/></body></html>";

        byte[] pdf = render(directory, html);

        // Three SVGs, three bitmaps: the first is drawn on two pages and is one image in the PDF.
        List<int[]> bitmaps = imageSizes(pdf);
        assertThat(bitmaps).hasSize(3);
        // 20,000 x 20,000 dots, 60,000 x 400 and 2,000 x 2,000 all became bitmaps of at most a million pixels.
        assertThat(bitmaps).allSatisfy(size -> assertThat((long) size[0] * size[1]).isLessThanOrEqualTo(BOUND));
    }

    /** An SVG that Batik cannot build: it uses an element that does not exist, and the id quotes what a document might hold. */
    private static final String BROKEN = "<svg " + NAMESPACE + " xmlns:xlink='http://www.w3.org/1999/xlink' width='100' height='100'>"
            + "<use xlink:href='#FAKE-Patient-Id-123'/><rect width='100' height='100'/></svg>";

    /** The same, with a gradient, so that it is drawn from a bitmap. */
    private static final String BROKEN_BITMAP = "<svg " + NAMESPACE + " xmlns:xlink='http://www.w3.org/1999/xlink' width='100' height='100'>"
            + "<defs><linearGradient id='g'><stop offset='0' stop-color='#000'/></linearGradient></defs>"
            + "<use xlink:href='#FAKE-Patient-Id-123'/><rect width='100' height='100' fill='url(#g)'/></svg>";

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(strings = {BROKEN, BROKEN_BITMAP})
    @DisplayName("should leave out an SVG that cannot be drawn and print the rest of the page, as one that never was there")
    void shouldLeaveOutSvg_thatCannotBeDrawn(String svg, @TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("broken.svg"), svg);
        BoundedOutputDevice bounded = new BoundedOutputDevice(DOTS_PER_POINT, BOUND);

        byte[] pdf = render(bounded, directory, "<html><body style='margin:0'><p>FAKE-Before</p><img src='broken.svg' width='100' height='100'/>"
                + "<p>FAKE-After</p><img src='broken.svg' width='100' height='100'/></body></html>");

        assertThat(bounded.leftOutImages()).as("two images, each its own SVG").isEqualTo(2);
        assertThat(String.join(" ", TestPdfs.pageTexts(pdf))).contains("FAKE-Before").contains("FAKE-After");
        assertThat(TestPdfs.imageDraws(pdf)).isZero();
        // Two 100 x 100 px black squares would be 7,200 dark points on the page; the two lines of text are a few hundred.
        assertThat(darkPoints(TestPdfs.image(pdf, 1))).as("only the text is dark").isLessThan(2_000);
    }

    private static int darkPoints(java.awt.image.BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                if (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF) < 3 * 96) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    @DisplayName("should try an SVG that cannot be drawn once, however many tiles of a background it would fill")
    void shouldTryBrokenSvgOnce_whateverTheTiles(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("tile.svg"), "<svg " + NAMESPACE + " xmlns:xlink='http://www.w3.org/1999/xlink' width='4' height='4'>"
                + "<use xlink:href='#FAKE-Patient-Id-123'/></svg>");
        BoundedOutputDevice bounded = new BoundedOutputDevice(DOTS_PER_POINT, BOUND);

        byte[] pdf = render(bounded, directory,
                "<html><body style='margin:0'><div style='width:800px;height:800px;background-image:url(tile.svg)'></div></body></html>");

        assertThat(bounded.leftOutImages()).isEqualTo(1);
        assertThat(TestPdfs.imageDraws(pdf)).isZero();
    }

    @Test
    @DisplayName("should log only the class of the exception of an SVG it cannot draw")
    void shouldLogOnlyTheClass_ofTheExceptionOfASvg(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("broken.svg"), BROKEN);

        try (LogCapture capture = LogCapture.forLogger(BoundedOutputDevice.class)) {
            render(directory, "<html><body><img src='broken.svg' width='10' height='10'/></body></html>");

            assertThat(capture.messages()).containsExactly("An SVG image could not be drawn and is left out (BridgeException)");
            assertThat(capture.events()).allSatisfy(event -> assertThat(event.getThrown()).isNull());
        }
    }

    @Test
    @DisplayName("should still fail on an image that is not an SVG that it cannot draw")
    void shouldStillFail_forAnotherKindOfImage() {
        FSImage foreign = new FSImage() {
            @Override
            public int getWidth() {
                return 1;
            }

            @Override
            public int getHeight() {
                return 1;
            }

            @Override
            @SuppressWarnings("unchecked")
            public FSImage scale(int width, int height) {
                return this;
            }
        };

        assertThatThrownBy(() -> device.drawImage(foreign, 0, 0)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(device.leftOutImages()).isZero();
    }

    @Test
    @DisplayName("should draw a huge inline svg with a gradient from a bounded bitmap")
    void shouldRenderHugeInlineSvg_fromABoundedBitmap(@TempDir Path directory) throws Exception {
        String html = "<html><body style='margin:0'><svg xmlns='http://www.w3.org/2000/svg' width='1000' height='1000' style='display:block'>"
                + "<defs><linearGradient id='g'><stop offset='0' stop-color='#000'/><stop offset='1' stop-color='#fff'/></linearGradient></defs>"
                + "<rect width='1000' height='1000' fill='url(#g)'/></svg></body></html>";

        List<int[]> bitmaps = imageSizes(render(directory, html));

        assertThat(bitmaps).hasSize(1);
        assertThat((long) bitmaps.get(0)[0] * bitmaps.get(0)[1]).isLessThanOrEqualTo(BOUND);
    }

    @Test
    @DisplayName("should embed one bitmap for all the tiles of a repeating SVG background, not one for each tile")
    void shouldEmbedOneBitmap_forTilesOfARepeatingBackground(@TempDir Path directory) throws Exception {
        // A 4 px tile over 800 x 800 px is 40,000 tiles; made afresh for each, it took a minute and a 17 MB PDF.
        Files.writeString(directory.resolve("tile.svg"), "<svg xmlns='http://www.w3.org/2000/svg' width='4' height='4'><defs><linearGradient id='g'>"
                + "<stop offset='0' stop-color='#000'/><stop offset='1' stop-color='#fff'/></linearGradient></defs><rect width='4' height='4' fill='url(#g)'/></svg>");
        // The agent gives the SVG the size of its pixels, which the raw renderer here does not: 4 x 4 dots tiles.
        String html = "<html><body style='margin:0'><div style='width:800px;height:800px;background-image:url(tile.svg)'></div></body></html>";

        byte[] pdf = render(directory, html);

        assertThat(imageSizes(pdf)).hasSize(1);
        assertThat(pdf.length).isLessThan(1 << 20);
        assertThat(TestPdfs.imageDraws(pdf)).isGreaterThan(1_000);
    }

    /** The pixel size of every distinct image any page draws, into forms too: one on two pages is one image, a soft mask is part of its image. */
    private static List<int[]> imageSizes(byte[] pdf) throws IOException {
        List<PDImageXObject> images = new ArrayList<>();
        List<Object> seen = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (PDPage page : document.getPages()) {
                collect(page.getResources(), images, seen);
            }
            List<Object> masks = new ArrayList<>();
            for (PDImageXObject image : images) {
                if (image.getSoftMask() != null) {
                    masks.add(image.getSoftMask().getCOSObject());
                }
            }
            List<int[]> sizes = new ArrayList<>();
            for (PDImageXObject image : images) {
                if (masks.stream().noneMatch(mask -> mask == image.getCOSObject())) {
                    sizes.add(new int[]{image.getWidth(), image.getHeight()});
                }
            }
            return sizes;
        }
    }

    private static void collect(PDResources resources, List<PDImageXObject> images, List<Object> seen) throws IOException {
        if (resources == null) {
            return;
        }
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xobject = resources.getXObject(name);
            if (seen.stream().anyMatch(o -> o == xobject.getCOSObject())) {
                continue;
            }
            seen.add(xobject.getCOSObject());
            if (xobject instanceof PDImageXObject image) {
                images.add(image);
            } else if (xobject instanceof PDFormXObject form) {
                collect(form.getResources(), images, seen);
            }
        }
    }
}
