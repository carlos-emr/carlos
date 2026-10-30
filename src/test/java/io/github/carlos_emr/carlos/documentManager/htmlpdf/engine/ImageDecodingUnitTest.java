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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.FileCacheImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import com.twelvemonkeys.imageio.stream.ByteArrayImageInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit coverage for {@link ImageDecoding} and {@link ImageHeader}: header reads that never decode,
 * the subsampling arithmetic, streamed subsampled reads, and EXIF orientation.
 *
 * <p>Fixtures live in {@code src/test/resources/htmlpdf/images/} and were generated once with
 * Pillow (CI has no Pillow, so no test creates them at run time). Everything else is generated in
 * memory or in a temporary directory, from synthetic pixels only.</p>
 *
 * @since 2026-10-29
 */
@DisplayName("ImageDecoding")
@Tag("unit")
@Tag("htmlpdf")
class ImageDecodingUnitTest {

    @TempDir
    Path tempDir;

    // ---- readHeader -------------------------------------------------------------------------

    @Test
    void shouldReadHeaderWithoutDecoding_forHugeDeclaredPng() {
        // 25 bytes of IHDR declaring 400 megapixels and no IDAT: only a header read can succeed.
        byte[] png = PngHeaders.declaring(20_000, 20_000);

        assertThat(ImageDecoding.readHeader(png)).hasValueSatisfying(header -> {
            assertThat(header.formatName()).isEqualTo("png");
            assertThat(header.width()).isEqualTo(20_000);
            assertThat(header.height()).isEqualTo(20_000);
            assertThat(header.pixels()).isEqualTo(400_000_000L);
        });
        // Guard against a vacuous test: the very same bytes cannot be decoded (subsampling 200
        // keeps the would-be output at 100 x 100), so the header read above did not decode.
        assertThatThrownBy(() -> ImageDecoding.readSubsampled(png, 200)).isInstanceOf(IOException.class);
    }

    @Test
    void shouldReadHeaderFromFile_forHugeDeclaredPng() throws IOException {
        Path file = Files.write(tempDir.resolve("declared.png"), PngHeaders.declaring(20_000, 20_000));

        assertThat(ImageDecoding.readHeader(file)).hasValueSatisfying(header -> {
            assertThat(header.formatName()).isEqualTo("png");
            assertThat(header.pixels()).isEqualTo(400_000_000L);
        });
    }

    @Test
    void shouldReturnEmpty_forUnknownOrCorruptHeader() throws IOException {
        byte[] heic = new byte[64];
        System.arraycopy(new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c'}, 0, heic, 0, 12);
        byte[] random = new byte[512];
        for (int i = 0; i < random.length; i++) {
            random[i] = (byte) (i * 131 + 7);
        }
        byte[] signatureOnly = Arrays.copyOf(PngHeaders.declaring(10, 10), 8);
        byte[] signatureThenGarbage = new byte[40];
        System.arraycopy(signatureOnly, 0, signatureThenGarbage, 0, 8);
        Arrays.fill(signatureThenGarbage, 8, 40, (byte) 0x5A);

        Map<String, byte[]> inputs = new LinkedHashMap<>();
        inputs.put("HEIC ftyp bytes", heic);
        inputs.put("random bytes", random);
        inputs.put("empty", new byte[0]);
        inputs.put("PNG signature only", signatureOnly);
        inputs.put("PNG signature then garbage", signatureThenGarbage);
        inputs.put("PNG declaring zero size", PngHeaders.declaring(0, 0));

        int count = 0;
        for (Map.Entry<String, byte[]> input : inputs.entrySet()) {
            Path file = Files.write(tempDir.resolve("input-" + count++), input.getValue());
            assertThat(ImageDecoding.readHeader(input.getValue())).as("bytes: " + input.getKey()).isEmpty();
            assertThat(ImageDecoding.readHeader(file)).as("file: " + input.getKey()).isEmpty();
        }
    }

    @Test
    void shouldReturnEmpty_forMissingFileOrDirectory() {
        assertThat(ImageDecoding.readHeader(tempDir.resolve("does-not-exist.png"))).isEmpty();
        assertThat(ImageDecoding.readHeader(tempDir)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"png,png", "jpeg,jpeg", "gif,gif", "bmp,bmp", "tiff,tif"})
    void shouldReadHeader_forEachCommonFormat(String writerFormat, String formatNamePrefix) throws IOException {
        byte[] data = encode(gradient(7, 5), writerFormat);
        Path file = Files.write(tempDir.resolve("image." + writerFormat), data);

        assertThat(ImageDecoding.readHeader(data)).hasValueSatisfying(header -> {
            // Reader names differ in case and spelling ("JPEG", "tif" or "tiff"); compare loosely.
            assertThat(header.formatName().toLowerCase(Locale.ROOT)).startsWith(formatNamePrefix);
            assertThat(header.width()).isEqualTo(7);
            assertThat(header.height()).isEqualTo(5);
            assertThat(header.isJpeg()).isEqualTo("jpeg".equals(writerFormat));
        });
        assertThat(ImageDecoding.readHeader(file)).isEqualTo(ImageDecoding.readHeader(data));
    }

    @Test
    void shouldThrowNullPointerException_forNullInputs() {
        assertThatThrownBy(() -> ImageDecoding.readHeader((Path) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ImageDecoding.readHeader((byte[]) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ImageDecoding.exifOrientation((Path) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ImageDecoding.exifOrientation((byte[]) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ImageDecoding.readSubsampled((Path) null, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ImageDecoding.readSubsampled((byte[]) null, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ImageDecoding.applyOrientation(null, 6)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ImageDecoding.subsamplingToFit(null, 1)).isInstanceOf(NullPointerException.class);
    }

    // ---- ImageHeader ------------------------------------------------------------------------

    @Test
    void shouldComputePixelsAsLong_forLargeHeader() {
        // 100 000 x 100 000 overflows an int (10^10); the product must be taken in long.
        assertThat(new ImageHeader("png", 100_000, 100_000).pixels()).isEqualTo(10_000_000_000L);
        assertThat(new ImageHeader("png", Integer.MAX_VALUE, Integer.MAX_VALUE).pixels())
                .isEqualTo((long) Integer.MAX_VALUE * Integer.MAX_VALUE);
    }

    @ParameterizedTest
    @CsvSource({"JPEG,true", "jpeg,true", "Jpeg,true", "jpg,true", "JPG,true",
            "png,false", "gif,false", "wbmp,false", "tiff,false", "jpeg2000,false", "j,false"})
    void shouldRecogniseJpeg_regardlessOfCase(String formatName, boolean expected) {
        assertThat(new ImageHeader(formatName, 1, 1).isJpeg()).isEqualTo(expected);
    }

    @Test
    void shouldRejectInvalidValues_forImageHeader() {
        assertThatThrownBy(() -> new ImageHeader(null, 1, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ImageHeader("png", 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImageHeader("png", 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ImageHeader("png", -5, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- subsamplingToFit -------------------------------------------------------------------

    @Test
    void shouldPickSmallestSubsampling_toFitLimit() {
        // 6600 x 10200 is 67.3 MP; halving gives 3300 x 5100 = 16.8 MP <= 25 MP.
        assertThat(ImageDecoding.subsamplingToFit(new ImageHeader("png", 6600, 10200), 25_000_000)).isEqualTo(2);
        // 12 MP already fits.
        assertThat(ImageDecoding.subsamplingToFit(new ImageHeader("png", 4000, 3000), 25_000_000)).isEqualTo(1);
        // 400 MP down to 4 MP: exactly 2000 x 2000 at 10, while 9 still gives 2223 x 2223 = 4.9 MP.
        assertThat(ImageDecoding.subsamplingToFit(new ImageHeader("png", 20_000, 20_000), 4_000_000)).isEqualTo(10);
    }

    @Test
    void shouldFitExactlyAtTheLimit_forBoundarySizes() {
        // 5000 x 5000 is exactly 25 MP: it fits, one more column does not.
        assertThat(ImageDecoding.subsamplingToFit(new ImageHeader("png", 5000, 5000), 25_000_000)).isEqualTo(1);
        assertThat(ImageDecoding.subsamplingToFit(new ImageHeader("png", 5001, 5000), 25_000_000)).isEqualTo(2);
        // ImageIO rounds the output size up, so 5 x 5 at 2 is 3 x 3 = 9 and not 2 x 2.
        assertThat(ImageDecoding.subsamplingToFit(new ImageHeader("png", 5, 5), 9)).isEqualTo(2);
        assertThat(ImageDecoding.subsamplingToFit(new ImageHeader("png", 5, 5), 8)).isEqualTo(3);
    }

    @Test
    void shouldMatchLinearSearch_forManySizesAndLimits() {
        int[] sizes = {1, 2, 3, 5, 7, 31, 100, 999, 1024, 4000, 6600};
        long[] limits = {1, 2, 3, 5, 100, 1_000, 65_536, 4_000_000, 25_000_000};
        for (int width : sizes) {
            for (int height : sizes) {
                for (long limit : limits) {
                    assertThat(ImageDecoding.subsamplingToFit(new ImageHeader("png", width, height), limit))
                            .as("%dx%d at %d", width, height, limit)
                            .isEqualTo(linearSubsamplingToFit(width, height, limit));
                }
            }
        }
    }

    @Test
    void shouldTreatLimitBelowOneAsOnePixel_forSubsampling() {
        // No subsampling can reach fewer than one pixel; the strongest reduction is returned.
        assertThat(ImageDecoding.subsamplingToFit(new ImageHeader("png", 30, 20), 0)).isEqualTo(30);
        assertThat(ImageDecoding.subsamplingToFit(new ImageHeader("png", 30, 20), -7)).isEqualTo(30);
        assertThat(ImageDecoding.subsamplingToFit(new ImageHeader("png", 30, 20), 1)).isEqualTo(30);
    }

    @Test
    void shouldNotOverflowOrLoop_forHugeHeader() {
        // The product of two int maxima does not fit an int, and a linear search would take
        // two billion steps; the answer is still immediate and exact.
        ImageHeader huge = new ImageHeader("png", Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertThat(ImageDecoding.subsamplingToFit(huge, 1)).isEqualTo(Integer.MAX_VALUE);
        assertThat(ImageDecoding.subsamplingToFit(huge, Long.MAX_VALUE)).isEqualTo(1);
    }

    @Test
    void shouldCapSourcePixels_at250Megapixels() {
        assertThat(ImageDecoding.MAX_SOURCE_PIXELS).isEqualTo(250_000_000L);
        // "About 15 800 x 15 800": that square is under the cap and 15 900 x 15 900 is over it.
        assertThat(15_800L * 15_800L).isLessThan(ImageDecoding.MAX_SOURCE_PIXELS);
        assertThat(15_900L * 15_900L).isGreaterThan(ImageDecoding.MAX_SOURCE_PIXELS);
    }

    // ---- readSubsampled ---------------------------------------------------------------------

    @Test
    void shouldReadSubsampledFromStream_forLargePng() throws IOException {
        int width = 4000;
        int height = 3000;
        BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                source.getRaster().setSample(x, y, 0, (x + 3 * y) % 251);
            }
        }
        Path file = tempDir.resolve("large.png");
        assertThat(ImageIO.write(source, "png", file.toFile())).isTrue();

        BufferedImage result = ImageDecoding.readSubsampled(file, 2);

        assertThat(result.getWidth()).isEqualTo(2000);
        assertThat(result.getHeight()).isEqualTo(1500);
        // Output pixel (i, j) is source pixel (2i, 2j): subsampling, not averaging or cropping.
        for (int[] point : new int[][] {{0, 0}, {1, 0}, {0, 1}, {10, 7}, {1999, 1499}}) {
            int expected = (2 * point[0] + 3 * 2 * point[1]) % 251;
            assertThat(result.getRaster().getSample(point[0], point[1], 0)).as("pixel %d,%d", point[0], point[1])
                    .isEqualTo(expected);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"png", "jpeg", "gif", "bmp", "tiff"})
    void shouldRoundOutputSizeUp_forEachFormatAndSource(String writerFormat) throws IOException {
        byte[] data = encode(gradient(7, 5), writerFormat);
        Path file = Files.write(tempDir.resolve("image." + writerFormat), data);

        // ceil(7 / 2) x ceil(5 / 2) = 4 x 3; this is the size subsamplingToFit plans with.
        for (BufferedImage result : List.of(ImageDecoding.readSubsampled(data, 2), ImageDecoding.readSubsampled(file, 2))) {
            assertThat(result.getWidth()).isEqualTo(4);
            assertThat(result.getHeight()).isEqualTo(3);
        }
        assertThat(ImageDecoding.readSubsampled(data, 1).getWidth()).isEqualTo(7);
        assertThat(ImageDecoding.readSubsampled(file, 3).getHeight()).isEqualTo(2);
    }

    // ---- the list of formats ----------------------------------------------------------------

    @ParameterizedTest(name = "reader named {0}")
    @ValueSource(strings = {"png", "PNG", "jpeg", "JPEG", "gif", "bmp", "wbmp", "WBMP", "tiff", "TIFF", "tif"})
    void shouldConsultReader_forEveryListedFormatName(String formatName) throws IOException {
        // The double fails whenever it is asked anything, so a header is empty and a decode throws
        // whether or not it was consulted. What tells the two apart is that a consulted reader is
        // given the stream, and the message of the decode.
        withDoubleNamed(formatName, (data, file) -> {
            assertThat(ImageDecoding.readHeader(data)).isEmpty();
            assertThat(ExplodingReader.lastInput).as("header read of bytes").isNotNull();
            ExplodingReader.lastInput = null;
            assertThat(ImageDecoding.readHeader(file)).isEmpty();
            assertThat(ExplodingReader.lastInput).as("header read of a file").isNotNull();
            ExplodingReader.lastInput = null;
            assertThatThrownBy(() -> ImageDecoding.readSubsampled(data, 1))
                    .isInstanceOf(IOException.class).hasMessageContaining("IllegalStateException");
            assertThat(ExplodingReader.lastInput).as("decode of bytes").isNotNull();
            ExplodingReader.lastInput = null;
            assertThatThrownBy(() -> ImageDecoding.readSubsampled(file, 1))
                    .isInstanceOf(IOException.class).hasMessageContaining("IllegalStateException");
            assertThat(ExplodingReader.lastInput).as("decode of a file").isNotNull();
        });
    }

    @ParameterizedTest(name = "reader named {0}")
    @ValueSource(strings = {"webp", "WebP", "pcx"})
    void shouldRefuseReader_whoseFormatIsNotListed(String formatName) throws IOException {
        // A reader that is not for a listed format is treated as no reader at all, whatever it would
        // have done, and is never given the stream. "webp" is the name that matters: it is what a
        // WebP plugin put back on the classpath would report.
        withDoubleNamed(formatName, (data, file) -> {
            assertThat(ImageDecoding.readHeader(data)).isEmpty();
            assertThat(ImageDecoding.readHeader(file)).isEmpty();
            assertThatThrownBy(() -> ImageDecoding.readSubsampled(data, 1))
                    .isExactlyInstanceOf(IOException.class).hasMessage(ImageDecoding.UNSUPPORTED_FORMAT_MESSAGE);
            assertThatThrownBy(() -> ImageDecoding.readSubsampled(file, 1))
                    .isExactlyInstanceOf(IOException.class).hasMessage(ImageDecoding.UNSUPPORTED_FORMAT_MESSAGE);
            assertThat(ExplodingReader.lastInput).as("the reader was never given a stream").isNull();
        });
    }

    /** A reader provider that claims every input (or fails when asked), under the given format names, and counts being asked. */
    private static final class GreedyReaderSpi extends ImageReaderSpi {
        static final java.util.concurrent.atomic.AtomicInteger ASKED = new java.util.concurrent.atomic.AtomicInteger();

        private final String failure;

        GreedyReaderSpi(String failure, String... formatNames) {
            vendorName = "CARLOS tests";
            version = "1";
            names = formatNames;
            inputTypes = new Class<?>[] {ImageInputStream.class};
            this.failure = failure;
        }

        GreedyReaderSpi() {
            this(null, "bmp", "png", "tiff");
        }

        @Override
        public boolean canDecodeInput(Object source) throws IOException {
            ASKED.incrementAndGet();
            if ("io".equals(failure)) {
                throw new IOException("claim failure");
            }
            if ("runtime".equals(failure)) {
                throw new IllegalStateException("claim failure");
            }
            return true;
        }

        @Override
        public ImageReader createReaderInstance(Object extension) {
            return new ExplodingReader(this);
        }

        @Override
        public String getDescription(Locale locale) {
            return "CARLOS test reader that claims every input";
        }
    }

    /** Registers the provider ahead of every real reader (and, if asked, vets it), runs the body, and puts the registry back. */
    private static void withProviderFirst(GreedyReaderSpi first, boolean vetted, Runnable body) {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        registry.registerServiceProvider(first);
        if (vetted) {
            ImageDecoding.VETTED_CLASS_PATH_PROVIDERS.add(GreedyReaderSpi.class.getName());
        }
        List<ImageReaderSpi> others = new ArrayList<>();
        registry.getServiceProviders(ImageReaderSpi.class, false).forEachRemaining(provider -> {
            if (provider != first) {
                others.add(provider);
            }
        });
        others.forEach(provider -> registry.setOrdering(ImageReaderSpi.class, first, provider));
        GreedyReaderSpi.ASKED.set(0);
        ExplodingReader.lastInput = null;
        try {
            body.run();
        } finally {
            others.forEach(provider -> registry.unsetOrdering(ImageReaderSpi.class, first, provider));
            ImageDecoding.VETTED_CLASS_PATH_PROVIDERS.remove(GreedyReaderSpi.class.getName());
            registry.deregisterServiceProvider(first);
            ExplodingReader.lastInput = null;
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"png", "bmp", "tiff"})
    void shouldNeverAskAProviderThatIsNotVetted_evenWhenItIsFirst(String format) throws IOException {
        // The JAI ImageIO jar on this classpath is such a provider: its BMP and TIFF readers come
        // before the JDK's. This one is placed ahead of every real reader of the format and claims all input.
        byte[] data = format.equals("png") ? TestImages.png(5, 4) : format.equals("bmp") ? TestImages.bmp(5, 4) : TestImages.tiff(5, 4);
        Path file = Files.write(tempDir.resolve("greedy." + format), data);

        withProviderFirst(new GreedyReaderSpi(), false, () -> {
            try {
                Optional<ImageHeader> fromBytes = ImageDecoding.readHeader(data);
                Optional<ImageHeader> fromFile = ImageDecoding.readHeader(file);
                BufferedImage decoded = ImageDecoding.readSubsampled(data, 1);

                assertThat(fromBytes).isPresent().get().satisfies(header -> {
                    assertThat(header.width()).isEqualTo(5);
                    assertThat(header.height()).isEqualTo(4);
                });
                assertThat(fromFile).isEqualTo(fromBytes);
                assertThat(decoded.getWidth()).isEqualTo(5);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            assertThat(GreedyReaderSpi.ASKED.get()).as("times the unvetted provider was asked whether it can decode").isZero();
            assertThat(ExplodingReader.lastInput).as("the unvetted provider's reader was never given a stream").isNull();
        });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"io", "runtime"})
    void shouldPassOverAVettedProvider_whoseClaimFails(String failure) {
        byte[] png = TestImages.png(5, 4);

        withProviderFirst(new GreedyReaderSpi(failure, "png"), true, () -> {
            // Asked first, it fails; that is "no", and the next provider reads the picture.
            assertThat(ImageDecoding.readHeader(png)).isPresent().get().satisfies(header -> assertThat(header.width()).isEqualTo(5));
            assertThat(GreedyReaderSpi.ASKED.get()).as("it was asked").isEqualTo(1);
            assertThat(ExplodingReader.lastInput).isNull();
        });
    }

    @Test
    void shouldLetTheFirstVettedReaderToClaimDecide_whatTheInputIs() {
        byte[] png = TestImages.png(5, 4);

        // A vetted reader for an unlisted format claims the PNG first: the input is then of a format
        // that is not read, and no other reader is looked for.
        withProviderFirst(new GreedyReaderSpi(null, "webp"), true, () -> {
            assertThat(ImageDecoding.readHeader(png)).isEmpty();
            assertThat(ExplodingReader.lastInput).as("no reader was given the stream").isNull();
        });
    }

    @Test
    void shouldVetOnlyTheJdksReaders_andTheTwelveMonkeysTiffReader() {
        Iterator<ImageReaderSpi> providers = IIORegistry.getDefaultInstance().getServiceProviders(ImageReaderSpi.class, false);
        List<String> vetted = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        while (providers.hasNext()) {
            ImageReaderSpi provider = providers.next();
            (ImageDecoding.isVetted(provider) ? vetted : refused).add(provider.getClass().getName());
        }

        assertThat(vetted).isNotEmpty().allSatisfy(name -> assertThat(name)
                .matches("com\\.sun\\.imageio\\.plugins\\..*|com\\.twelvemonkeys\\.imageio\\.plugins\\.tiff\\.TIFFImageReaderSpi"));
        assertThat(vetted).contains("com.sun.imageio.plugins.png.PNGImageReaderSpi", "com.sun.imageio.plugins.jpeg.JPEGImageReaderSpi",
                "com.sun.imageio.plugins.gif.GIFImageReaderSpi", "com.sun.imageio.plugins.bmp.BMPImageReaderSpi",
                "com.sun.imageio.plugins.wbmp.WBMPImageReaderSpi", "com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReaderSpi");
        // The JAI readers of the same formats are on the test class path, and are not read through.
        assertThat(refused).contains("com.github.jaiimageio.impl.plugins.bmp.BMPImageReaderSpi",
                "com.github.jaiimageio.impl.plugins.tiff.TIFFImageReaderSpi");
    }

    @Test
    void shouldNotVetAProvider_byItsNameAlone() {
        ImageReaderSpi lookalike = new ExplodingReaderSpi("png");

        assertThat(ImageDecoding.isVetted(lookalike)).isFalse();
    }

    @Test
    void shouldNotDecodeWebp_withoutAnyDecoderOnTheClasspath() throws IOException {
        // The real file. The WebP plugin is no longer a dependency, so no reader claims it; one put
        // back would be refused all the same (the test above).
        byte[] webp = fixtureBytes("opaque-4x4.webp");
        Path file = fixture("opaque-4x4.webp");

        assertThat(ImageDecoding.readHeader(webp)).isEmpty();
        assertThat(ImageDecoding.readHeader(file)).isEmpty();
        assertThatThrownBy(() -> ImageDecoding.readSubsampled(webp, 1))
                .isExactlyInstanceOf(IOException.class).hasMessage(ImageDecoding.UNSUPPORTED_FORMAT_MESSAGE);
        assertThatThrownBy(() -> ImageDecoding.readSubsampled(file, 1))
                .isExactlyInstanceOf(IOException.class).hasMessage(ImageDecoding.UNSUPPORTED_FORMAT_MESSAGE);
    }

    @Test
    void shouldReadHeaderAndDecode_forWbmp() throws IOException {
        // The one listed format the other tests do not write.
        byte[] wbmp = encode(new BufferedImage(7, 5, BufferedImage.TYPE_BYTE_BINARY), "wbmp");

        assertThat(ImageDecoding.readHeader(wbmp)).hasValueSatisfying(header -> {
            assertThat(header.formatName()).isEqualToIgnoringCase("wbmp");
            assertThat(header.width()).isEqualTo(7);
            assertThat(header.height()).isEqualTo(5);
        });
        assertThat(ImageDecoding.readSubsampled(wbmp, 2).getWidth()).isEqualTo(4);
    }

    // ---- byte payloads ----------------------------------------------------------------------

    @Test
    void shouldNotCopyTheBytes_whenDecodingFromMemory() throws IOException {
        com.sun.management.ThreadMXBean threads = allocationCounter();
        Path big = writeStoredGrayPng(tempDir.resolve("big-bytes.png"), 6000, 4000);
        byte[] png = Files.readAllBytes(big);
        ImageDecoding.readSubsampled(writeStoredGrayPng(tempDir.resolve("tiny-bytes.png"), 8, 8), 1); // warm up

        long before = threads.getCurrentThreadAllocatedBytes();
        BufferedImage image = ImageDecoding.readSubsampled(png, 8);
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;

        // A memory cache stream copied the 24 MB payload as it read it (about 24 MB more, 48 MB
        // in all with the array); a stream over the array itself allocates the 375 KB result and
        // the reader's buffers.
        assertThat(image.getWidth()).isEqualTo(750);
        assertThat(allocated).as("bytes allocated decoding a %d byte payload", png.length).isLessThan(png.length / 4L);
    }

    @Test
    void shouldNotCopyTheBytes_whenReadingExifFromMemory() {
        com.sun.management.ThreadMXBean threads = allocationCounter();
        // An Exif segment first, then 20 comment segments of 64 KB (1.3 MB): the scan stops at its
        // first mebibyte, and a memory cache stream would have copied all of it on the way.
        byte[] exif = exifSegment(tiffWithOrientation(ByteOrder.LITTLE_ENDIAN, 6));
        byte[] comment = segment(0xFE, new byte[65_533]);
        byte[][] segments = new byte[21][];
        segments[0] = exif;
        Arrays.fill(segments, 1, 21, comment);
        byte[] jpeg = jpegOf(segments);
        ImageDecoding.exifOrientation(jpegOf(exif)); // warm up

        long before = threads.getCurrentThreadAllocatedBytes();
        int orientation = ImageDecoding.exifOrientation(jpeg);
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;

        assertThat(orientation).isEqualTo(6);
        assertThat(allocated).as("bytes allocated scanning %d bytes of header", jpeg.length).isLessThan(512_000L);
    }

    @Test
    void shouldReadFile_withSpaceInName() throws IOException {
        Path file = tempDir.resolve("scan page 1.png");
        assertThat(ImageIO.write(gradient(6, 4), "png", file.toFile())).isTrue();

        assertThat(ImageDecoding.readHeader(file)).hasValueSatisfying(header -> assertThat(header.width()).isEqualTo(6));
        assertThat(ImageDecoding.readSubsampled(file, 2).getWidth()).isEqualTo(3);
        assertThat(ImageDecoding.exifOrientation(file)).isEqualTo(1);
    }

    @Test
    void shouldThrowIoException_whenNoReaderCanDecode() throws IOException {
        byte[] notAnImage = "this is not an image".getBytes(StandardCharsets.US_ASCII);
        Path notAnImageFile = Files.write(tempDir.resolve("note.png"), notAnImage);

        assertThatThrownBy(() -> ImageDecoding.readSubsampled(notAnImage, 1)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ImageDecoding.readSubsampled(notAnImageFile, 1)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ImageDecoding.readSubsampled(new byte[0], 1)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ImageDecoding.readSubsampled(tempDir.resolve("missing.png"), 1))
                .isInstanceOf(IOException.class);
        // A reader is found (valid signature and header) but there is no pixel data to decode.
        assertThatThrownBy(() -> ImageDecoding.readSubsampled(PngHeaders.declaring(8, 8), 1))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldNotNameTheFile_inDecodeFailureMessage() throws IOException {
        Path file = Files.write(tempDir.resolve("patient-name-secret.png"), new byte[] {1, 2, 3, 4});

        assertThatThrownBy(() -> ImageDecoding.readSubsampled(file, 1))
                .isInstanceOf(IOException.class)
                .hasMessageNotContaining("patient-name-secret")
                .hasMessageNotContaining(tempDir.toString());
    }

    @Test
    void shouldWrapUncheckedReaderFailure_asIoExceptionNamingOnlyTheClass() throws IOException {
        // Real readers throw ClassCastException, ArrayIndexOutOfBounds, ArithmeticException and
        // the like on corrupt files (a fuzz run of the TIFF reader alone produced hundreds). A
        // synthetic reader makes that deterministic, whatever the libraries on the classpath do.
        byte[] data = Arrays.copyOf(EXPLODING_MAGIC, EXPLODING_MAGIC.length + 16);
        Path file = Files.write(tempDir.resolve("exploding.img"), data);
        IIORegistry registry = IIORegistry.getDefaultInstance();
        ExplodingReaderSpi spi = new ExplodingReaderSpi();
        registry.registerServiceProvider(spi);
        ImageDecoding.VETTED_CLASS_PATH_PROVIDERS.add(ExplodingReaderSpi.class.getName());
        try {
            // A header read swallows the failure ...
            assertThat(ImageDecoding.readHeader(data)).isEmpty();
            assertThat(ImageDecoding.readHeader(file)).isEmpty();
            // ... and a decode reports it as the one exception type callers handle, without
            // copying the reader's message (library text can echo file content).
            assertThatThrownBy(() -> ImageDecoding.readSubsampled(data, 1))
                    .isExactlyInstanceOf(IOException.class)
                    .hasMessageContaining("java.lang.IllegalStateException")
                    .hasMessageNotContaining("patient-secret")
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> ImageDecoding.readSubsampled(file, 1))
                    .isExactlyInstanceOf(IOException.class)
                    .hasMessageNotContaining("patient-secret")
                    .hasCauseInstanceOf(IllegalStateException.class);
        } finally {
            ImageDecoding.VETTED_CLASS_PATH_PROVIDERS.remove(ExplodingReaderSpi.class.getName());
            registry.deregisterServiceProvider(spi);
        }
    }

    @Test
    void shouldReadBytesFromMemory_andFilesThroughTheFile() throws IOException {
        // Bytes are a patient's document: ImageIO's default stream for an InputStream is a cache
        // file in the temporary directory (when caching is on, as it is by default), so byte
        // arrays go through a stream over the array itself instead: no copy, no disk, and a
        // length, which a reader's own checks need (a memory cache stream reports -1, and a check
        // such as the TwelveMonkeys TIFF reader's 512:1 expansion limit is then skipped). The
        // length is asserted here, not through a real TIFF, because which of the three TIFF
        // readers on the classpath claims an input depends on the classpath order. A file is read in
        // place, never copied into memory first. The synthetic reader records the stream it is given.
        byte[] data = Arrays.copyOf(EXPLODING_MAGIC, EXPLODING_MAGIC.length + 16);
        Path file = Files.write(tempDir.resolve("recording.img"), data);
        IIORegistry registry = IIORegistry.getDefaultInstance();
        ExplodingReaderSpi spi = new ExplodingReaderSpi();
        registry.registerServiceProvider(spi);
        ImageDecoding.VETTED_CLASS_PATH_PROVIDERS.add(ExplodingReaderSpi.class.getName());
        try {
            ExplodingReader.lastInput = null;
            ImageDecoding.readHeader(data);
            assertThat(ExplodingReader.lastInput).as("stream for a header read of bytes").isEqualTo(ByteArrayImageInputStream.class);
            assertThat(ExplodingReader.lastInputLength).as("length the reader sees, header read of bytes").isEqualTo(data.length);

            ExplodingReader.lastInput = null;
            assertThatThrownBy(() -> ImageDecoding.readSubsampled(data, 1)).isInstanceOf(IOException.class);
            assertThat(ExplodingReader.lastInput).as("stream for a decode of bytes").isEqualTo(ByteArrayImageInputStream.class);
            assertThat(ExplodingReader.lastInputLength).as("length the reader sees, decode of bytes").isEqualTo(data.length);

            ExplodingReader.lastInput = null;
            ImageDecoding.readHeader(file);
            assertThat(ExplodingReader.lastInput).as("stream for a header read of a file")
                    .isNotNull().isNotEqualTo(ByteArrayImageInputStream.class).isNotEqualTo(MemoryCacheImageInputStream.class)
                    .isNotEqualTo(FileCacheImageInputStream.class);

            ExplodingReader.lastInput = null;
            assertThatThrownBy(() -> ImageDecoding.readSubsampled(file, 1)).isInstanceOf(IOException.class);
            assertThat(ExplodingReader.lastInput).as("stream for a decode of a file")
                    .isNotNull().isNotEqualTo(ByteArrayImageInputStream.class).isNotEqualTo(MemoryCacheImageInputStream.class)
                    .isNotEqualTo(FileCacheImageInputStream.class);
        } finally {
            ImageDecoding.VETTED_CLASS_PATH_PROVIDERS.remove(ExplodingReaderSpi.class.getName());
            registry.deregisterServiceProvider(spi);
            ExplodingReader.lastInput = null;
        }
    }

    @Test
    void shouldStreamFromFile_withoutLoadingItWhole() throws IOException {
        com.sun.management.ThreadMXBean threads = allocationCounter();

        // 24 million pixels of noise in a 24 MB file: large enough that loading it whole is
        // unmistakable, written with stored (uncompressed) blocks so that it takes a fraction of a
        // second, and a quarter of its size is far above anything a streamed read allocates.
        Path big = writeStoredGrayPng(tempDir.resolve("big.png"), 6000, 4000);
        long fileBytes = Files.size(big);
        assertThat(fileBytes).isGreaterThan(24_000_000L);
        Path tiny = writeStoredGrayPng(tempDir.resolve("tiny.png"), 8, 8);
        // Warm up, so that class loading and reader lookup are not counted.
        ImageDecoding.readHeader(tiny);
        ImageDecoding.readSubsampled(tiny, 1);

        long before = threads.getCurrentThreadAllocatedBytes();
        Optional<ImageHeader> header = ImageDecoding.readHeader(big);
        long headerBytes = threads.getCurrentThreadAllocatedBytes() - before;

        before = threads.getCurrentThreadAllocatedBytes();
        BufferedImage image = ImageDecoding.readSubsampled(big, 8);
        long decodeBytes = threads.getCurrentThreadAllocatedBytes() - before;

        assertThat(header).hasValueSatisfying(h -> assertThat(h.pixels()).isEqualTo(24_000_000L));
        assertThat(image.getWidth()).isEqualTo(750);
        assertThat(image.getHeight()).isEqualTo(500);
        // Measured: about 21 KB for the header and 450 KB for the decode (the 375 KB result and
        // the reader's buffers). Reading the file into memory first costs about 48 MB (the bytes
        // and the stream's copy of them), which the quarter-of-the-file bound fails by a wide margin.
        assertThat(headerBytes).as("bytes allocated by the header read").isLessThan(1_000_000L);
        assertThat(decodeBytes).as("bytes allocated by the subsampled read of a %d byte file", fileBytes)
                .isLessThan(fileBytes / 4);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void shouldRejectSubsamplingBelowOne_forReads(int subsampling) throws IOException {
        byte[] data = encode(gradient(4, 4), "png");
        Path file = Files.write(tempDir.resolve("small.png"), data);

        assertThatThrownBy(() -> ImageDecoding.readSubsampled(data, subsampling))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ImageDecoding.readSubsampled(file, subsampling))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- exifOrientation --------------------------------------------------------------------

    @Test
    void shouldReadExifOrientation_forRotatedJpeg() throws IOException {
        byte[] jpeg = fixtureBytes("exif-orientation-6.jpg");

        assertThat(ImageDecoding.exifOrientation(jpeg)).isEqualTo(6);
        assertThat(ImageDecoding.exifOrientation(fixture("exif-orientation-6.jpg"))).isEqualTo(6);
        // A JPEG that carries no EXIF at all, and a PNG, are upright.
        assertThat(ImageDecoding.exifOrientation(encode(gradient(8, 4), "jpeg"))).isEqualTo(1);
        assertThat(ImageDecoding.exifOrientation(encode(gradient(8, 4), "png"))).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void shouldReadEveryOrientation_fromBigAndLittleEndianExif(int orientation) throws IOException {
        byte[] original = fixtureBytes("exif-orientation-6.jpg");

        // Pillow wrote big-endian ("MM") EXIF; many cameras and phones write little-endian ("II").
        for (ByteOrder order : List.of(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN)) {
            byte[] jpeg = withExif(original, tiffWithOrientation(order, orientation));
            Path file = Files.write(tempDir.resolve("o" + orientation + "-" + order + ".jpg"), jpeg);

            assertThat(ImageDecoding.exifOrientation(jpeg)).as("%s bytes", order).isEqualTo(orientation);
            assertThat(ImageDecoding.exifOrientation(file)).as("%s file", order).isEqualTo(orientation);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 9, 16, 255, 32768, 65535})
    void shouldReturnOne_forOutOfRangeOrientation(int orientation) throws IOException {
        byte[] jpeg = withExif(fixtureBytes("exif-orientation-6.jpg"), tiffWithOrientation(ByteOrder.BIG_ENDIAN, orientation));

        assertThat(ImageDecoding.exifOrientation(jpeg)).isEqualTo(1);
    }

    @Test
    void shouldReturnOne_forCorruptOrMissingExif() throws IOException {
        byte[] original = fixtureBytes("exif-orientation-6.jpg");
        byte[] validTiff = tiffWithOrientation(ByteOrder.BIG_ENDIAN, 6);

        // EXIF present but with no Orientation entry at all.
        ByteBuffer emptyIfd = ByteBuffer.allocate(14).order(ByteOrder.BIG_ENDIAN);
        emptyIfd.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8).putShort((short) 0).putInt(0);

        Map<String, byte[]> jpegs = new LinkedHashMap<>();
        jpegs.put("EXIF without Orientation", withExif(original, emptyIfd.array()));
        jpegs.put("EXIF that is not TIFF", withExif(original, "this is not a TIFF structure".getBytes(StandardCharsets.US_ASCII)));
        jpegs.put("EXIF truncated inside the IFD", withExif(original, Arrays.copyOf(validTiff, 12)));
        jpegs.put("EXIF whose IFD offset is outside the segment",
                withExif(original, new byte[] {'M', 'M', 0, 42, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}));
        jpegs.put("empty EXIF payload", withExif(original, new byte[0]));
        jpegs.put("JPEG cut off after the start marker", Arrays.copyOf(original, 2));
        jpegs.put("JPEG cut off inside the EXIF segment", Arrays.copyOf(original, 30));

        int count = 0;
        for (Map.Entry<String, byte[]> jpeg : jpegs.entrySet()) {
            Path file = Files.write(tempDir.resolve("corrupt-" + count++ + ".jpg"), jpeg.getValue());
            assertThat(ImageDecoding.exifOrientation(jpeg.getValue())).as("bytes: " + jpeg.getKey()).isEqualTo(1);
            assertThat(ImageDecoding.exifOrientation(file)).as("file: " + jpeg.getKey()).isEqualTo(1);
        }
    }

    @Test
    void shouldReturnOne_forNonImageMissingOrEmptyInput() {
        assertThat(ImageDecoding.exifOrientation(new byte[0])).isEqualTo(1);
        assertThat(ImageDecoding.exifOrientation("not an image".getBytes(StandardCharsets.US_ASCII))).isEqualTo(1);
        assertThat(ImageDecoding.exifOrientation(PngHeaders.declaring(10, 10))).isEqualTo(1);
        assertThat(ImageDecoding.exifOrientation(tempDir.resolve("missing.jpg"))).isEqualTo(1);
        assertThat(ImageDecoding.exifOrientation(tempDir)).isEqualTo(1);
    }

    @Test
    void shouldNotHoldAHostileHeaderInMemory_whenScanningForExif() throws IOException {
        com.sun.management.ThreadMXBean threads = allocationCounter();
        // 200 full-size (64 KB) Exif segments in a row, 13 MB, before the image data. The scan
        // keeps every Exif segment it meets, so unbounded it allocated about 1x the file (67 MB
        // for a 65 MB file); bounded to the first mebibyte it stays near that mebibyte.
        byte[] junkExif = segment(0xE1, concat("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), new byte[65_533 - 6]));
        byte[][] segments = new byte[200][];
        Arrays.fill(segments, junkExif);
        Path hostile = Files.write(tempDir.resolve("hostile.jpg"), jpegOf(segments));
        ImageDecoding.exifOrientation(hostile); // warm up

        long before = threads.getCurrentThreadAllocatedBytes();
        int orientation = ImageDecoding.exifOrientation(hostile);
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;

        assertThat(orientation).isEqualTo(1);
        assertThat(allocated).as("bytes allocated scanning a %d byte file", Files.size(hostile)).isLessThan(4_000_000L);
    }

    @Test
    void shouldFindExif_afterLargeEarlierSegments() {
        // A 60 KB comment segment ahead of the Exif segment: well inside the scanned mebibyte.
        byte[] exif = exifSegment(tiffWithOrientation(ByteOrder.LITTLE_ENDIAN, 6));
        byte[] comment = segment(0xFE, new byte[60_000]);

        assertThat(ImageDecoding.exifOrientation(jpegOf(comment, exif))).isEqualTo(6);
    }

    @Test
    void shouldIgnoreExif_beyondTheScannedMebibyte() {
        // Documents the bound: 20 comment segments of 64 KB put the Exif segment at about 1.25 MB.
        byte[] exif = exifSegment(tiffWithOrientation(ByteOrder.LITTLE_ENDIAN, 6));
        byte[] comment = segment(0xFE, new byte[65_533]);
        byte[][] segments = new byte[21][];
        Arrays.fill(segments, 0, 20, comment);
        segments[20] = exif;

        assertThat(ImageDecoding.exifOrientation(jpegOf(segments))).isEqualTo(1);
        // The same file is readable with the Exif segment first, so only the position mattered.
        segments[0] = exif;
        assertThat(ImageDecoding.exifOrientation(jpegOf(segments))).isEqualTo(6);
    }

    // ---- applyOrientation -------------------------------------------------------------------

    @Test
    void shouldRotateQuarterTurn_forOrientation6() {
        BufferedImage wide = new BufferedImage(8, 4, BufferedImage.TYPE_INT_RGB);

        BufferedImage upright = ImageDecoding.applyOrientation(wide, 6);

        assertThat(upright.getWidth()).isEqualTo(4);
        assertThat(upright.getHeight()).isEqualTo(8);
    }

    @ParameterizedTest(name = "orientation {0}")
    @CsvSource({"1,abc/def", "2,cba/fed", "3,fed/cba", "4,def/abc",
            "5,ad/be/cf", "6,da/eb/fc", "7,fc/eb/da", "8,cf/be/ad"})
    void shouldMapEveryPixel_forEachOrientation(int orientation, String expectedRows) {
        // Stored pixels, one letter per colour:   a b c
        //                                         d e f
        // Expected rows are what a viewer shows once the EXIF orientation is applied (checked
        // against Pillow's ImageOps.exif_transpose: 6 turns the stored image a quarter turn
        // clockwise, 8 a quarter turn counter-clockwise, 5 and 7 are the two diagonal reflections).
        int[] colours = {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF};
        BufferedImage stored = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < colours.length; i++) {
            stored.setRGB(i % 3, i / 3, colours[i]);
        }

        BufferedImage shown = ImageDecoding.applyOrientation(stored, orientation);

        StringBuilder rows = new StringBuilder();
        for (int y = 0; y < shown.getHeight(); y++) {
            if (y > 0) {
                rows.append('/');
            }
            for (int x = 0; x < shown.getWidth(); x++) {
                int index = -1;
                for (int i = 0; i < colours.length; i++) {
                    if ((shown.getRGB(x, y) & 0xFFFFFF) == colours[i]) {
                        index = i;
                    }
                }
                rows.append(index < 0 ? '?' : (char) ('a' + index));
            }
        }
        assertThat(rows).hasToString(expectedRows);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 0, -1, 9, 100, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void shouldReturnSameImage_forIdentityOrUnknownOrientation(int orientation) {
        BufferedImage image = gradient(5, 3);

        assertThat(ImageDecoding.applyOrientation(image, orientation)).isSameAs(image);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("imagesOfEveryKind")
    void shouldKeepSamplesAndColorModel_forEveryImageType(String label, BufferedImage source) {
        for (int orientation = 2; orientation <= 8; orientation++) {
            BufferedImage result = ImageDecoding.applyOrientation(source, orientation);

            boolean swapped = orientation >= 5;
            assertThat(result.getWidth()).as("%s o%d width", label, orientation)
                    .isEqualTo(swapped ? source.getHeight() : source.getWidth());
            assertThat(result.getHeight()).as("%s o%d height", label, orientation)
                    .isEqualTo(swapped ? source.getWidth() : source.getHeight());
            // The colour model carries the palette, the alpha mode and the sample depth: a copy
            // through a different model would change colours, which a 16-bit scan must not suffer.
            assertThat(result.getColorModel()).as("%s o%d model", label, orientation).isEqualTo(source.getColorModel());
            assertEverySampleMoved(source, result, orientation, label);
        }
    }

    @Test
    void shouldProduceUprightImage_forRotatedJpegFixture() throws IOException {
        Path file = fixture("exif-orientation-6.jpg");

        BufferedImage stored = ImageDecoding.readSubsampled(file, 1);
        BufferedImage upright = ImageDecoding.applyOrientation(stored, ImageDecoding.exifOrientation(file));

        // The phone photo is stored 8 wide by 4 high and must print 4 wide by 8 high.
        assertThat(stored.getWidth()).isEqualTo(8);
        assertThat(stored.getHeight()).isEqualTo(4);
        assertThat(upright.getWidth()).isEqualTo(4);
        assertThat(upright.getHeight()).isEqualTo(8);
    }

    // ---- PngHeaders (test helper shared with later tasks) -----------------------------------

    @Test
    void shouldWriteSignatureHeaderAndEndWithoutPixels_forPngHeaders() {
        byte[] png = PngHeaders.declaring(0x01020304, 0x0A0B0C0D);
        ByteBuffer buffer = ByteBuffer.wrap(png).order(ByteOrder.BIG_ENDIAN);

        // 8 signature bytes + IHDR (4 length + 4 type + 13 data + 4 CRC) + IEND (4 + 4 + 0 + 4).
        assertThat(png).hasSize(8 + 25 + 12);
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        assertThat(Arrays.copyOf(png, 8)).isEqualTo(signature);
        assertThat(buffer.getInt(8)).isEqualTo(13);
        assertThat(new String(png, 12, 4, StandardCharsets.US_ASCII)).isEqualTo("IHDR");
        assertThat(buffer.getInt(16)).isEqualTo(0x01020304);
        assertThat(buffer.getInt(20)).isEqualTo(0x0A0B0C0D);
        CRC32 crc = new CRC32();
        crc.update(png, 12, 4 + 13);
        assertThat(buffer.getInt(29)).isEqualTo((int) crc.getValue());
        assertThat(buffer.getInt(33)).isZero();
        assertThat(new String(png, 37, 4, StandardCharsets.US_ASCII)).isEqualTo("IEND");
        // IEND with no data always has this CRC.
        assertThat(buffer.getInt(41)).isEqualTo(0xAE426082);
        assertThat(new String(png, StandardCharsets.ISO_8859_1)).doesNotContain("IDAT");
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static Stream<Arguments> imagesOfEveryKind() {
        byte[] red = new byte[256];
        byte[] green = new byte[256];
        byte[] blue = new byte[256];
        for (int i = 0; i < 256; i++) {
            red[i] = (byte) i;
            green[i] = (byte) (255 - i);
            blue[i] = (byte) (i * 7);
        }
        BufferedImage indexed = new BufferedImage(9, 5, BufferedImage.TYPE_BYTE_INDEXED, new IndexColorModel(8, 256, red, green, blue));
        ComponentColorModel sixteenBitRgba = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                new int[] {16, 16, 16, 16}, true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_USHORT);
        BufferedImage custom = new BufferedImage(sixteenBitRgba, sixteenBitRgba.createCompatibleWritableRaster(9, 5), false, null);
        return Stream.of(
                        Arguments.of("3BYTE_BGR", new BufferedImage(9, 5, BufferedImage.TYPE_3BYTE_BGR)),
                        Arguments.of("BYTE_GRAY", new BufferedImage(9, 5, BufferedImage.TYPE_BYTE_GRAY)),
                        Arguments.of("INT_RGB", new BufferedImage(9, 5, BufferedImage.TYPE_INT_RGB)),
                        Arguments.of("INT_ARGB", new BufferedImage(9, 5, BufferedImage.TYPE_INT_ARGB)),
                        Arguments.of("INT_ARGB_PRE", new BufferedImage(9, 5, BufferedImage.TYPE_INT_ARGB_PRE)),
                        Arguments.of("4BYTE_ABGR", new BufferedImage(9, 5, BufferedImage.TYPE_4BYTE_ABGR)),
                        Arguments.of("USHORT_GRAY", new BufferedImage(9, 5, BufferedImage.TYPE_USHORT_GRAY)),
                        Arguments.of("BYTE_BINARY", new BufferedImage(9, 5, BufferedImage.TYPE_BYTE_BINARY)),
                        Arguments.of("BYTE_INDEXED", indexed),
                        Arguments.of("16-bit RGBA (custom)", custom))
                .peek(arguments -> fillWithDistinctSamples((BufferedImage) arguments.get()[1]));
    }

    /** Gives every pixel and band its own value (within the band's range), so a misplaced pixel shows. */
    private static void fillWithDistinctSamples(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                for (int band = 0; band < image.getRaster().getNumBands(); band++) {
                    int max = (1 << image.getRaster().getSampleModel().getSampleSize(band)) - 1;
                    image.getRaster().setSample(x, y, band, (x * 37 + y * 101 + band * 53 + 1) & max);
                }
            }
        }
    }

    /**
     * Checks that source pixel (x, y) arrived where the EXIF orientation says. The expected
     * coordinates are written out from the EXIF table, independent of the transforms under test.
     */
    private static void assertEverySampleMoved(BufferedImage source, BufferedImage result, int orientation, String label) {
        int w = source.getWidth();
        int h = source.getHeight();
        Raster in = source.getRaster();
        Raster out = result.getRaster();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int[] to = switch (orientation) {
                    case 2 -> new int[] {w - 1 - x, y};
                    case 3 -> new int[] {w - 1 - x, h - 1 - y};
                    case 4 -> new int[] {x, h - 1 - y};
                    case 5 -> new int[] {y, x};
                    case 6 -> new int[] {h - 1 - y, x};
                    case 7 -> new int[] {h - 1 - y, w - 1 - x};
                    case 8 -> new int[] {y, w - 1 - x};
                    default -> throw new IllegalArgumentException("orientation " + orientation);
                };
                for (int band = 0; band < in.getNumBands(); band++) {
                    assertThat(out.getSample(to[0], to[1], band))
                            .as("%s o%d pixel %d,%d band %d", label, orientation, x, y, band)
                            .isEqualTo(in.getSample(x, y, band));
                }
            }
        }
    }

    /** Smallest s with ceil(w / s) * ceil(h / s) <= limit, found the slow obvious way. */
    private static int linearSubsamplingToFit(int width, int height, long limit) {
        for (int s = 1; ; s++) {
            long outWidth = (width + s - 1L) / s;
            long outHeight = (height + s - 1L) / s;
            if (outWidth * outHeight <= limit || s == Math.max(width, height)) {
                return s;
            }
        }
    }

    /**
     * Writes a grayscale PNG of random noise whose rows are <em>stored</em> in the deflate stream
     * (compression level 0). The file is as large as its pixels, which is the point, and is
     * written in a fraction of a second; a compressing writer would spend seconds on noise.
     */
    private static Path writeStoredGrayPng(Path file, int width, int height) throws IOException {
        ByteArrayOutputStream idat = new ByteArrayOutputStream(width * height + height + 1024);
        Deflater deflater = new Deflater(Deflater.NO_COMPRESSION);
        try (DeflaterOutputStream deflate = new DeflaterOutputStream(idat, deflater)) {
            Random noise = new Random(1);
            byte[] row = new byte[1 + width];
            for (int y = 0; y < height; y++) {
                noise.nextBytes(row);
                row[0] = 0; // PNG row filter type: none
                deflate.write(row);
            }
        } finally {
            deflater.end();
        }
        byte[] declared = PngHeaders.declaring(width, height);
        int signatureAndHeader = 8 + 25;
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.write(declared, 0, signatureAndHeader);
            PngHeaders.writeChunk(out, "IDAT", idat.toByteArray());
            out.write(declared, signatureAndHeader, declared.length - signatureAndHeader);
        }
        return file;
    }

    /** The first bytes of a synthetic format that only {@link ExplodingReaderSpi} recognises. */
    private static final byte[] EXPLODING_MAGIC = "CARLOS-TEST-EXPLODING-IMAGE".getBytes(StandardCharsets.US_ASCII);

    /** Registers a reader for {@link #EXPLODING_MAGIC} data; its reader fails with an unchecked exception. */
    private static final class ExplodingReaderSpi extends ImageReaderSpi {
        /** Named like a listed format, so that {@link ImageDecoding} consults the double. */
        ExplodingReaderSpi() {
            this("bmp");
        }

        ExplodingReaderSpi(String formatName) {
            vendorName = "CARLOS tests";
            version = "1";
            names = new String[] {formatName};
            inputTypes = new Class<?>[] {ImageInputStream.class};
        }

        @Override
        public boolean canDecodeInput(Object source) throws IOException {
            ImageInputStream stream = (ImageInputStream) source;
            stream.mark();
            try {
                byte[] head = new byte[EXPLODING_MAGIC.length];
                stream.readFully(head);
                return Arrays.equals(head, EXPLODING_MAGIC);
            } catch (EOFException e) {
                return false;
            } finally {
                stream.reset();
            }
        }

        @Override
        public ImageReader createReaderInstance(Object extension) {
            return new ExplodingReader(this);
        }

        @Override
        public String getDescription(Locale locale) {
            return "CARLOS test reader that fails with an unchecked exception";
        }
    }

    private static final class ExplodingReader extends ImageReader {
        /** The class of the stream most recently given to a reader of this kind. */
        static volatile Class<?> lastInput;

        /** The length that stream reported when it was given: -1 if it did not know it. */
        static volatile long lastInputLength;

        ExplodingReader(ImageReaderSpi spi) {
            super(spi);
        }

        @Override
        public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
            super.setInput(input, seekForwardOnly, ignoreMetadata);
            lastInput = input == null ? null : input.getClass();
            lastInputLength = lengthOf(input);
        }

        private static long lengthOf(Object input) {
            try {
                return input instanceof ImageInputStream stream ? stream.length() : Long.MIN_VALUE;
            } catch (IOException e) {
                return Long.MIN_VALUE;
            }
        }

        private static IllegalStateException failure() {
            return new IllegalStateException("reader failure with patient-secret");
        }

        @Override
        public int getNumImages(boolean allowSearch) {
            return 1;
        }

        @Override
        public int getWidth(int imageIndex) {
            throw failure();
        }

        @Override
        public int getHeight(int imageIndex) {
            throw failure();
        }

        @Override
        public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) {
            throw failure();
        }

        @Override
        public IIOMetadata getStreamMetadata() {
            return null;
        }

        @Override
        public IIOMetadata getImageMetadata(int imageIndex) {
            return null;
        }

        @Override
        public BufferedImage read(int imageIndex, ImageReadParam param) {
            throw failure();
        }
    }

    /** Decodes through the byte-array overload or, as {@code kind} says, through the file overload. */
    private static BufferedImage decodeAs(String kind, byte[] data, Path file, int subsampling) throws IOException {
        return kind.equals("bytes") ? ImageDecoding.readSubsampled(data, subsampling) : ImageDecoding.readSubsampled(file, subsampling);
    }

    /** Runs {@code body} with the synthetic reader registered under {@code formatName}, on bytes and on a file holding them. */
    private void withDoubleNamed(String formatName, DoubleBody body) throws IOException {
        byte[] data = Arrays.copyOf(EXPLODING_MAGIC, EXPLODING_MAGIC.length + 16);
        Path file = Files.write(tempDir.resolve("named.img"), data);
        IIORegistry registry = IIORegistry.getDefaultInstance();
        ExplodingReaderSpi spi = new ExplodingReaderSpi(formatName);
        registry.registerServiceProvider(spi);
        ImageDecoding.VETTED_CLASS_PATH_PROVIDERS.add(ExplodingReaderSpi.class.getName());
        try {
            ExplodingReader.lastInput = null;
            body.run(data, file);
        } finally {
            ImageDecoding.VETTED_CLASS_PATH_PROVIDERS.remove(ExplodingReaderSpi.class.getName());
            registry.deregisterServiceProvider(spi);
            ExplodingReader.lastInput = null;
        }
    }

    @FunctionalInterface
    private interface DoubleBody {
        void run(byte[] data, Path file) throws IOException;
    }

    private static BufferedImage gradient(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, ((x * 31) % 256) << 16 | ((y * 47) % 256) << 8 | ((x * y) % 256));
            }
        }
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, out)).as("an ImageIO writer for " + format).isTrue();
        return out.toByteArray();
    }

    private static Path fixture(String name) {
        try {
            return Path.of(ImageDecodingUnitTest.class.getResource("/htmlpdf/images/" + name).toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("fixture " + name, e);
        }
    }

    private static byte[] fixtureBytes(String name) throws IOException {
        return Files.readAllBytes(fixture(name));
    }

    private static com.sun.management.ThreadMXBean allocationCounter() {
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assumeTrue(threads.isThreadAllocatedMemorySupported() && threads.isThreadAllocatedMemoryEnabled(),
                "per-thread allocation counting is not available on this JVM");
        return threads;
    }

    /** One JPEG marker segment: 0xFF, the marker byte, the 2-byte length (which counts itself) and the payload. */
    private static byte[] segment(int markerByte, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 4);
        out.write(0xFF);
        out.write(markerByte);
        out.write((payload.length + 2) >> 8);
        out.write((payload.length + 2) & 0xFF);
        out.write(payload, 0, payload.length);
        return out.toByteArray();
    }

    /** An Exif APP1 segment holding the given TIFF structure. */
    private static byte[] exifSegment(byte[] tiff) {
        return segment(0xE1, concat("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), tiff));
    }

    /** A JPEG consisting of the start marker, the given segments and the end marker (no image data). */
    private static byte[] jpegOf(byte[]... segments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8);
        for (byte[] segment : segments) {
            out.write(segment, 0, segment.length);
        }
        out.write(0xFF);
        out.write(0xD9);
        return out.toByteArray();
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] joined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }

    /** An EXIF TIFF structure whose IFD0 holds one Orientation entry (SHORT, count 1). */
    private static byte[] tiffWithOrientation(ByteOrder order, int orientation) {
        byte byteOrderMark = (byte) (order == ByteOrder.BIG_ENDIAN ? 'M' : 'I');
        ByteBuffer tiff = ByteBuffer.allocate(26).order(order);
        tiff.put(byteOrderMark).put(byteOrderMark).putShort((short) 42).putInt(8);
        tiff.putShort((short) 1);                                                     // one IFD entry
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1);                  // Orientation, SHORT, count 1
        tiff.putShort((short) orientation).putShort((short) 0);                       // value, padded to 4 bytes
        tiff.putInt(0);                                                               // no next IFD
        return tiff.array();
    }

    /** Returns {@code jpeg} with its APP1 segment replaced by an Exif segment holding {@code tiff}. */
    private static byte[] withExif(byte[] jpeg, byte[] tiff) {
        byte[] identifier = {'E', 'x', 'i', 'f', 0, 0};
        byte[] payload = new byte[identifier.length + tiff.length];
        System.arraycopy(identifier, 0, payload, 0, identifier.length);
        System.arraycopy(tiff, 0, payload, identifier.length, tiff.length);

        int offset = 2; // after the start-of-image marker
        while (offset + 4 <= jpeg.length) {
            int marker = ((jpeg[offset] & 0xFF) << 8) | (jpeg[offset + 1] & 0xFF);
            int length = ((jpeg[offset + 2] & 0xFF) << 8) | (jpeg[offset + 3] & 0xFF);
            if (marker == 0xFFE1) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                out.write(jpeg, 0, offset);
                out.write(0xFF);
                out.write(0xE1);
                out.write((payload.length + 2) >> 8);
                out.write((payload.length + 2) & 0xFF);
                out.write(payload, 0, payload.length);
                int tail = offset + 2 + length;
                out.write(jpeg, tail, jpeg.length - tail);
                return out.toByteArray();
            }
            offset += 2 + length;
        }
        throw new IllegalStateException("the fixture has no APP1 segment to replace");
    }
}
