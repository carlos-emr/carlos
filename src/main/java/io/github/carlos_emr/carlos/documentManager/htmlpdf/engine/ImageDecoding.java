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

import com.twelvemonkeys.imageio.metadata.CompoundDirectory;
import com.twelvemonkeys.imageio.metadata.Directory;
import com.twelvemonkeys.imageio.metadata.Entry;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegment;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegmentUtil;
import com.twelvemonkeys.imageio.metadata.tiff.TIFFReader;
import com.twelvemonkeys.imageio.stream.ByteArrayImageInputStream;
import com.twelvemonkeys.imageio.stream.SubImageInputStream;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Static helpers that read images through ImageIO and keep what they hold small and predictable:
 * header reads that never decode, the subsampling arithmetic, streamed subsampled decodes and EXIF
 * orientation. They are shared by the Flying Saucer user agent (images that arrive during layout)
 * and the image planning in {@code service} (images checked before layout).
 *
 * <p>Three rules hold for every method:</p>
 *
 * <ul>
 *   <li><b>A header read never decodes a pixel.</b> {@link #readHeader(Path)} and
 *       {@link #readHeader(byte[])} ask the ImageIO reader for the declared width and height and
 *       nothing else. A 20 000 x 20 000 PNG whose header is 25 bytes therefore costs no more than a
 *       thumbnail, which is what lets callers refuse a decompression bomb
 *       ({@link #MAX_SOURCE_PIXELS}) before anything is decoded.</li>
 *   <li><b>A decode reads only what it keeps.</b> {@link #readSubsampled(Path, int)} reads every
 *       n-th pixel in each direction straight from a random-access stream on the file, so the file
 *       is never loaded whole and the heap holds the already reduced result and the reader's
 *       buffers. (The one cost that subsampling does not reduce is native memory: see that
 *       method.)</li>
 *   <li><b>Content problems do not escape as unchecked exceptions.</b> Header and EXIF reads
 *       return empty or {@code 1} for any input they cannot use. A decode throws
 *       {@link IOException}. Only a null argument or an out-of-range subsampling (programming
 *       errors) throws anything else.</li>
 * </ul>
 *
 * <p><b>Only vetted formats are read, by vetted readers.</b> A header is reported, and a decode
 * attempted, only by a reader that is for PNG, JPEG, GIF, BMP, WBMP or TIFF <em>and</em> comes from
 * a vetted provider: the JDK's own readers (the {@code java.desktop} module) or the TwelveMonkeys
 * TIFF reader. The first such reader that claims the input is used. A reader of another
 * provider is never asked about the input, and never given the stream: ImageIO registers every
 * reader it finds on the classpath, in an order that follows the class loader, and the JAI ImageIO
 * jar that is on this classpath registers its own BMP, WBMP and TIFF readers, ahead of the JDK's
 * for BMP and TIFF, under names that pass the list of formats. A decoder that turns up later, as a
 * transitive dependency of another library for example, would otherwise start decoding untrusted bytes
 * without anyone noticing. Input no vetted reader claims is treated as one that no reader
 * recognises: {@link #readHeader(Path)} is empty and {@link #readSubsampled(Path, int)} throws
 * {@link IOException}. Adding a format or a provider here is a conscious change, made once its
 * reader has been tried on corrupt, hostile and oversized input. The TwelveMonkeys WebP reader,
 * tried that way, looped for minutes on a 36-byte file, ignored the subsampling and allocated from
 * sizes it read out of the data; that is why WebP is not on the list and its plugin is not a
 * dependency.</p>
 *
 * <p>The usual sequence, for a file that may be hostile:</p>
 *
 * <pre>{@code
 * Optional<ImageHeader> header = ImageDecoding.readHeader(file);
 * if (header.isEmpty() || header.get().pixels() > ImageDecoding.MAX_SOURCE_PIXELS) {
 *     // unusable
 * }
 * int subsampling = ImageDecoding.subsamplingToFit(header.get(), maxPixels);
 * BufferedImage image = ImageDecoding.readSubsampled(file, subsampling);
 * image = ImageDecoding.applyOrientation(image, ImageDecoding.exifOrientation(file));
 * }</pre>
 *
 * <p>The images are patients' documents, so the class logs nothing and no exception message it
 * creates contains a path, a file name or file content.</p>
 *
 * @since 2026-10-29
 */
public final class ImageDecoding {

    /**
     * The largest declared size, in pixels, that any code in the module will decode. A header
     * above it is treated as malformed or as a decompression bomb. {@link #readHeader(Path)}
     * reports the size as declared and does not apply the limit; each caller does, before it
     * decodes or hands the image on.
     */
    // about 15 800 × 15 800; sharp refuses above 268 MP, Debian ImageMagick caps area at 256 MP (spec §Images)
    public static final long MAX_SOURCE_PIXELS = 250_000_000L;

    /**
     * The most pixels any image is decoded at: a larger one is reduced to fit (an image that is
     * smaller is decoded in full). 25 megapixels across a Letter page is about 520 dpi, above fax
     * (200 dpi) and office printers (300 to 600 dpi), so nothing visible is lost by the reduction
     * (spec §Images).
     */
    public static final long MAX_DECODED_PIXELS = 25_000_000L;

    /**
     * The formats this class reads, by the lower-case name their ImageIO reader reports (see the
     * class summary). The JDK reports {@code "png"}, {@code "JPEG"}, {@code "gif"}, {@code "bmp"},
     * {@code "wbmp"} and, for TIFF, {@code "tif"}; the TwelveMonkeys TIFF reader says {@code "tiff"}.
     */
    private static final Set<String> LISTED_FORMATS = Set.of("png", "jpeg", "gif", "bmp", "wbmp", "tiff", "tif");

    /**
     * The providers from the class path (not the JDK) whose readers are vetted: the TwelveMonkeys TIFF reader.
     * Package-private, and only added to by tests, which register synthetic readers.
     */
    static final Set<String> VETTED_CLASS_PATH_PROVIDERS =
            new CopyOnWriteArraySet<>(List.of("com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReaderSpi"));

    /**
     * The message when the input is no image in a listed format, whether no vetted reader claims it or the
     * reader that does is not for a listed format. It is fixed, so it names no file and no content.
     */
    static final String UNSUPPORTED_FORMAT_MESSAGE = "the input is not an image in a supported format";

    /** EXIF/TIFF tag number of Orientation. */
    private static final int TAG_ORIENTATION = 0x0112;

    /**
     * The JPEG APP1 marker as TwelveMonkeys' {@link JPEGSegmentUtil} spells it: the full 16-bit
     * value, not the low byte {@code 0xE1} (which silently matches nothing).
     */
    private static final int APP1_MARKER = 0xFFE1;

    /**
     * How far into a file the EXIF segment is looked for. The Exif standard places it right after
     * the start-of-image marker (or the JFIF segment), a few hundred bytes in; the scan keeps every
     * Exif segment it meets, so without a bound a hostile file of thousands of 64 KB ones is held in
     * memory whole.
     */
    // measured: a 65 MB JPEG made of Exif segments made the unbounded scan allocate 67 MB
    private static final long MAX_EXIF_SCAN_BYTES = 1L << 20;

    private static final int ORIENTATION_UPRIGHT = 1;
    private static final int MAX_ORIENTATION = 8;

    private ImageDecoding() {
    }

    // ---- headers ----------------------------------------------------------------------------

    /**
     * Reads the format and declared size of an image file from its header, without decoding it.
     *
     * <p>The file is opened as a random-access {@code ImageInputStream} (no cache file is
     * written), the first reader that recognises it is asked for the width and height only, and the
     * reader and stream are closed before returning. The result is what the file claims. It is not
     * checked against {@link #MAX_SOURCE_PIXELS}, so a header that declares 400 megapixels is
     * returned as such.</p>
     *
     * @param file the image file; nothing past its header is read
     * @return the header, or empty when no reader recognises the file (HEIC, PDF, text), when the
     *         reader that does is not for a listed format, when the header is corrupt or declares a
     *         size below 1 pixel, when the file is missing, a directory or unreadable, or when the
     *         reader fails in any other way; never throws because of the file's content
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public static Optional<ImageHeader> readHeader(Path file) {
        Objects.requireNonNull(file, "file");
        try (ImageInputStream stream = ImageIO.createImageInputStream(file.toFile())) {
            return headerOf(stream);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads the format and declared size of an image held in memory from its header, without
     * decoding it. The same rules and results as {@link #readHeader(Path)}.
     *
     * <p>The bytes are read through a stream over the array itself, which copies nothing, never
     * touches the disk and reports its length. They are never given to ImageIO's default
     * stream-input cache, which is a temporary file on disk: the bytes are a patient's document
     * and must not be copied there. Reporting the length matters because a reader's own checks
     * depend on it: the TwelveMonkeys TIFF reader refuses an input that would expand to more than
     * 512 times its length (a blank 1728 x 2200 CCITT fax page of under 800 bytes does), a check
     * that is skipped for a stream of unknown length, so a byte payload would be decoded where the
     * same file is refused. Which TIFF reader claims an input depends on the order of ImageIO's
     * registry between the vetted ones (the JDK's reader has no such check); the JAI ImageIO reader, which
     * has none either, is never asked.</p>
     *
     * @param data the image bytes, for example a decoded {@code data:} URI payload; not modified
     * @return the header, or empty in the cases listed for {@link #readHeader(Path)}
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public static Optional<ImageHeader> readHeader(byte[] data) {
        Objects.requireNonNull(data, "data");
        try (ImageInputStream stream = new ByteArrayImageInputStream(data)) {
            return headerOf(stream);
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<ImageHeader> headerOf(ImageInputStream stream) throws IOException {
        if (stream == null) {
            // ImageIO returns null, not an exception, for a file it cannot open.
            return Optional.empty();
        }
        ImageReader reader = firstReader(stream);
        if (reader == null) {
            return Optional.empty();
        }
        try {
            // Forward-only and metadata-ignoring: a header read needs neither, and both let the
            // reader skip work.
            reader.setInput(stream, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width < 1 || height < 1) {
                // ImageHeader would reject it; checking here makes a bad header an empty result
                // rather than an exception.
                return Optional.empty();
            }
            return Optional.of(new ImageHeader(reader.getFormatName(), width, height));
        } finally {
            reader.dispose();
        }
    }

    // ---- subsampling ------------------------------------------------------------------------

    /**
     * Returns the smallest source subsampling that brings an image within a pixel budget.
     *
     * <p>Subsampling by {@code s} keeps every s-th pixel in each direction, and ImageIO rounds the
     * result size up: the output is {@code ceil(width / s) x ceil(height / s)}. This returns the
     * smallest integer {@code s >= 1} for which that product is at most {@code maxPixels}. A
     * 6600 x 10200 image within 25 000 000 pixels gives 2 (3300 x 5100), and a 4000 x 3000 image
     * gives 1 because it already fits.</p>
     *
     * <p>No subsampling can reach fewer than one pixel, so a limit below 1 is treated as 1 and the
     * result is the larger dimension. The products are taken as {@code long} and the answer is
     * found by bisection, so even a hostile {@code Integer.MAX_VALUE} header answers at once.</p>
     *
     * @param header the declared size of the source image
     * @param maxPixels the budget for the decoded result, in pixels
     * @return the subsampling, between 1 and the larger of the width and height
     * @throws NullPointerException if {@code header} is {@code null}
     */
    public static int subsamplingToFit(ImageHeader header, long maxPixels) {
        Objects.requireNonNull(header, "header");
        long limit = Math.max(1L, maxPixels);
        // The output only shrinks as the subsampling grows, so "fits" is false up to some value
        // and true from there on; the larger dimension always fits (a single pixel).
        int low = 1;
        int high = Math.max(header.width(), header.height());
        while (low < high) {
            int middle = low + (high - low) / 2;
            long outputPixels = (long) Math.ceilDiv(header.width(), middle) * Math.ceilDiv(header.height(), middle);
            if (outputPixels <= limit) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    // ---- decoding ---------------------------------------------------------------------------

    /**
     * Decodes an image file keeping only every {@code subsampling}-th pixel in each direction.
     *
     * <p>The reader pulls the pixels it keeps straight from a random-access stream on the file; the
     * file is never read into memory whole, and the heap holds the reduced result, about
     * {@code pixels / subsampling^2} times the bytes per pixel, and the reader's working buffers.
     * Measured with 24-megapixel images and a subsampling of 8, PNG, baseline JPEG, GIF, BMP and
     * TIFF all decode in a 96 MB heap, and a 67-megapixel, 67 MB PNG was reduced to 2048 x 2048 in
     * a 64 MB heap, where reading the file into memory first ran out of it. One cost is not
     * reduced, and is not on the heap: the JDK's JPEG reader keeps the coefficients of a
     * <em>progressive</em> JPEG in native memory, about four bytes per source pixel whatever the
     * subsampling (92 MB more process memory for 24 megapixels), which {@code -Xmx} does not limit,
     * so callers budget a progressive JPEG by its source size. The result is
     * {@code ceil(width / s) x ceil(height / s)} in whatever type the reader produces (indexed,
     * gray, 16-bit, ...), and callers convert it if they need another.</p>
     *
     * <p>This method does not check the declared size: callers refuse a header above
     * {@link #MAX_SOURCE_PIXELS} first, and choose {@code subsampling} with
     * {@link #subsamplingToFit(ImageHeader, long)}, because a result that does not fit the heap
     * fails with {@link OutOfMemoryError}.</p>
     *
     * @param file the image file
     * @param subsampling how many source pixels to step over, at least 1 (1 decodes in full)
     * @return the decoded, reduced image
     * @throws IOException if the file cannot be opened, no reader recognises it, the reader that
     *         does is not for a listed format, or the reader fails to decode it; the message names
     *         neither the file nor its content
     * @throws IllegalArgumentException if {@code subsampling} is below 1
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public static BufferedImage readSubsampled(Path file, int subsampling) throws IOException {
        Objects.requireNonNull(file, "file");
        requireSubsampling(subsampling);
        try (ImageInputStream stream = ImageIO.createImageInputStream(file.toFile())) {
            return decode(stream, subsampling);
        }
    }

    /**
     * Decodes an image held in memory keeping only every {@code subsampling}-th pixel in each
     * direction. The same rules and results as {@link #readSubsampled(Path, int)}, a reader's
     * expansion limit included: the array is read through a stream that reports its length, so a
     * check that depends on it (the TIFF reader's 512:1 limit, for one) applies to the array as to
     * a file.
     *
     * @param data the image bytes, for example a decoded {@code data:} URI payload; not modified
     * @param subsampling how many source pixels to step over, at least 1
     * @return the decoded, reduced image
     * @throws IOException if no reader recognises the data, the reader that does is not for a
     *         listed format, or the reader fails to decode it
     * @throws IllegalArgumentException if {@code subsampling} is below 1
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public static BufferedImage readSubsampled(byte[] data, int subsampling) throws IOException {
        Objects.requireNonNull(data, "data");
        requireSubsampling(subsampling);
        try (ImageInputStream stream = new ByteArrayImageInputStream(data)) {
            return decode(stream, subsampling);
        }
    }

    private static BufferedImage decode(ImageInputStream stream, int subsampling) throws IOException {
        if (stream == null) {
            throw new IOException("the image cannot be opened");
        }
        ImageReader reader = null;
        try {
            reader = firstReader(stream);
            if (reader == null) {
                throw new IOException(UNSUPPORTED_FORMAT_MESSAGE);
            }
            // Not forward-only: a TIFF may store its strips out of order, and a random-access
            // stream on a file costs nothing extra.
            reader.setInput(stream, false, true);
            ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceSubsampling(subsampling, subsampling, 0, 0);
            return reader.read(0, param);
        } catch (RuntimeException e) {
            // Corrupt data surfaces from the readers as all sorts of runtime exceptions; callers
            // handle one type. The message carries the class name only, never the reader's text.
            throw new IOException("the image cannot be decoded (" + e.getClass().getName() + ")", e);
        } finally {
            if (reader != null) {
                reader.dispose();
            }
        }
    }

    private static void requireSubsampling(int subsampling) {
        if (subsampling < 1) {
            throw new IllegalArgumentException("subsampling must be at least 1");
        }
    }

    /**
     * The reader both the header and the decode use, so that they agree on what the image is, and
     * the one place that decides which formats are read, and by whom (see the class summary): the
     * first reader of a vetted provider that claims the stream, provided it is for a listed format.
     * Providers that are not vetted are filtered out before they are asked whether they can decode the
     * stream, so none of them reads a byte of it.
     *
     * @return that reader, or {@code null} if no vetted reader claims the stream
     */
    private static ImageReader firstReader(ImageInputStream stream) throws IOException {
        Iterator<ImageReaderSpi> providers = IIORegistry.getDefaultInstance().getServiceProviders(ImageReaderSpi.class,
                provider -> isVetted((ImageReaderSpi) provider) && canDecode((ImageReaderSpi) provider, stream), true);
        if (!providers.hasNext()) {
            return null;
        }
        ImageReader reader = providers.next().createReaderInstance();
        if (!isListed(reader)) {
            // The first vetted reader to claim the input decides what it is; there is no looking for another.
            reader.dispose();
            return null;
        }
        return reader;
    }

    /** The JDK's own readers, and the TwelveMonkeys TIFF reader, whose expansion check refuses a tiny file that would fill the heap. */
    static boolean isVetted(ImageReaderSpi provider) {
        Class<?> type = provider.getClass();
        return type.getModule() == ImageReader.class.getModule() || VETTED_CLASS_PATH_PROVIDERS.contains(type.getName());
    }

    private static boolean canDecode(ImageReaderSpi provider, ImageInputStream stream) {
        try {
            return provider.canDecodeInput(stream);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static boolean isListed(ImageReader reader) throws IOException {
        return LISTED_FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT));
    }

    // ---- progressive JPEG -------------------------------------------------------------------

    /**
     * Whether a JPEG file is progressive (its frame header is SOF2 or one of the other progressive
     * markers).
     *
     * <p>The JDK's JPEG reader keeps the coefficients of a progressive JPEG in native memory, about
     * four bytes per source pixel whatever the subsampling (see {@link #readSubsampled(Path, int)}),
     * so a caller that has to decode one budgets it by its source size. Only the marker segments
     * before the frame header are read, never more than the first mebibyte of the file.</p>
     *
     * @param file the image file
     * @return {@code true} for a progressive JPEG; {@code false} for a baseline one, for anything
     *         that is not a JPEG and for a file that cannot be read; never throws because of the
     *         file's content
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public static boolean isProgressiveJpeg(Path file) {
        Objects.requireNonNull(file, "file");
        try (InputStream in = Files.newInputStream(file)) {
            return progressive(in.readNBytes((int) MAX_EXIF_SCAN_BYTES));
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Whether a JPEG held in memory is progressive. The same rules as {@link #isProgressiveJpeg(Path)}.
     *
     * @param data the image bytes; not modified
     * @return {@code true} for a progressive JPEG
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public static boolean isProgressiveJpeg(byte[] data) {
        Objects.requireNonNull(data, "data");
        try {
            return progressive(data);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Walks the marker segments from the start of the image to the frame header. */
    private static boolean progressive(byte[] head) {
        if (head.length < 4 || (head[0] & 0xFF) != 0xFF || (head[1] & 0xFF) != 0xD8) {
            return false;
        }
        int position = 2;
        while (position + 4 <= head.length) {
            if ((head[position] & 0xFF) != 0xFF) {
                return false;
            }
            int marker = head[position + 1] & 0xFF;
            if (marker == 0xFF) {
                position++;
                continue;
            }
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                // SOF2, SOF6, SOF10 and SOF14 are the progressive frames (Huffman, differential, arithmetic).
                return marker == 0xC2 || marker == 0xC6 || marker == 0xCA || marker == 0xCE;
            }
            if (marker == 0xDA || marker == 0xD9) {
                return false;
            }
            int length = ((head[position + 2] & 0xFF) << 8) | (head[position + 3] & 0xFF);
            if (length < 2) {
                return false;
            }
            position += 2 + length;
        }
        return false;
    }

    // ---- EXIF orientation -------------------------------------------------------------------

    /**
     * Reads the EXIF Orientation of a JPEG file: how the stored pixels must be turned to be shown
     * upright (a phone held in portrait stores a landscape picture and records {@code 6}).
     *
     * <p>Only the JPEG header segments up to the start of the image data are read, and never more
     * than the first mebibyte of the file, so the cost depends neither on the size of the picture
     * nor on what a hostile file puts in front of it.</p>
     *
     * @param file the image file
     * @return the orientation, 1 to 8 as defined by EXIF (see
     *         {@link #applyOrientation(BufferedImage, int)}); 1 when the file is not a JPEG, has no
     *         EXIF segment or no Orientation entry, holds a value outside 1 to 8, or cannot be
     *         read; never throws because of the file's content
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public static int exifOrientation(Path file) {
        Objects.requireNonNull(file, "file");
        try (ImageInputStream stream = ImageIO.createImageInputStream(file.toFile())) {
            return orientationOf(stream);
        } catch (IOException | RuntimeException e) {
            return ORIENTATION_UPRIGHT;
        }
    }

    /**
     * Reads the EXIF Orientation of a JPEG held in memory. The same rules and results as
     * {@link #exifOrientation(Path)}.
     *
     * @param data the image bytes; not modified
     * @return the orientation, 1 to 8, or 1 as described for {@link #exifOrientation(Path)}
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public static int exifOrientation(byte[] data) {
        Objects.requireNonNull(data, "data");
        try (ImageInputStream stream = new ByteArrayImageInputStream(data)) {
            return orientationOf(stream);
        } catch (IOException | RuntimeException e) {
            return ORIENTATION_UPRIGHT;
        }
    }

    private static int orientationOf(ImageInputStream stream) throws IOException {
        if (stream == null) {
            return ORIENTATION_UPRIGHT;
        }
        // Throws for anything that does not start with the JPEG start-of-image marker, which is
        // how a PNG or a text file ends up upright. Only segments whose identifier is "Exif" are
        // read in full, none can exceed 64 KB, and the view ends after MAX_EXIF_SCAN_BYTES, where
        // the reader treats the cut as a truncated file and returns what it has found so far.
        List<JPEGSegment> segments;
        try (ImageInputStream head = new SubImageInputStream(stream, MAX_EXIF_SCAN_BYTES)) {
            segments = JPEGSegmentUtil.readSegments(head, APP1_MARKER, "Exif");
        }
        if (segments.isEmpty()) {
            return ORIENTATION_UPRIGHT;
        }
        InputStream segmentData = segments.get(0).data();
        if (segmentData == null) {
            return ORIENTATION_UPRIGHT;
        }
        byte[] exif = segmentData.readAllBytes();
        // The identifier is "Exif" plus two zero bytes. The segment data starts at the second one,
        // a pad byte, and the TIFF structure follows. Every offset inside it counts from its first
        // byte, so the reader must be given a stream that begins there.
        int tiffStart = exif.length > 0 && exif[0] == 0 ? 1 : 0;
        try (ImageInputStream tiff = new MemoryCacheImageInputStream(
                new ByteArrayInputStream(exif, tiffStart, exif.length - tiffStart))) {
            // EXIF is a TIFF structure; TwelveMonkeys' EXIFReader is a deprecated shim over this.
            Directory directory = new TIFFReader().read(tiff);
            // Only the first IFD describes the picture. The thumbnail (the second IFD) can carry an Orientation of its
            // own, and the compound directory would hand that one back when the first has none: a photo that is already
            // upright, printed on its side.
            Directory main = directory instanceof CompoundDirectory compound && compound.directoryCount() > 0
                    ? compound.getDirectory(0) : directory;
            Entry entry = main.getEntryById(TAG_ORIENTATION);
            if (entry != null && entry.getValue() instanceof Number number) {
                int orientation = number.intValue();
                if (orientation >= ORIENTATION_UPRIGHT && orientation <= MAX_ORIENTATION) {
                    return orientation;
                }
            }
            return ORIENTATION_UPRIGHT;
        }
    }

    /**
     * Returns the image as a viewer shows it once an EXIF orientation is applied.
     *
     * <p>The orientation says how the stored pixels must be turned to be upright:</p>
     *
     * <ul>
     *   <li>1: as stored; 2: mirrored left to right; 3: rotated 180 degrees; 4: mirrored top to
     *       bottom;</li>
     *   <li>5: mirrored along the main diagonal (transposed); 6: rotated 90 degrees clockwise;
     *       7: mirrored along the other diagonal; 8: rotated 90 degrees counter-clockwise.</li>
     * </ul>
     *
     * <p>For 1, and for any value outside 1 to 8, the same instance is returned. Otherwise a new
     * image is returned: for 5 to 8 its width and height are swapped. Pixels are copied, not
     * resampled, and the result keeps the source's color model, so a palette, an alpha channel or
     * 16-bit samples are unchanged. While both images are alive they occupy twice the memory of one.</p>
     *
     * @param image the decoded image
     * @param orientation the EXIF orientation, as returned by {@link #exifOrientation(Path)}
     * @return {@code image} itself or its turned copy
     * @throws NullPointerException if {@code image} is {@code null}
     */
    public static BufferedImage applyOrientation(BufferedImage image, int orientation) {
        Objects.requireNonNull(image, "image");
        if (orientation <= ORIENTATION_UPRIGHT || orientation > MAX_ORIENTATION) {
            return image;
        }
        AffineTransform transform = orientationTransform(orientation, image.getWidth(), image.getHeight());
        // Nearest neighbour on a transform of whole turns and reflections is an exact pixel
        // copy. Unlike drawing into a Graphics2D, AffineTransformOp keeps the source's color
        // model for the result, which keeps indexed palettes, premultiplied alpha and 16-bit
        // samples bit-for-bit (a Graphics2D draw rounds those through sRGB). Its native library,
        // libmlib_image, ships in openjdk-25-jre-headless, which debian/control depends on.
        return new AffineTransformOp(transform, AffineTransformOp.TYPE_NEAREST_NEIGHBOR).filter(image, null);
    }

    /**
     * The transform that moves a stored {@code w x h} image to its upright position for EXIF
     * orientations 2 to 8. The six arguments are m00, m10, m01, m11, m02, m12, so a point
     * (x, y) lands on (m00 x + m01 y + m02, m10 x + m11 y + m12); every result stays within the
     * positive quadrant, and for 5 to 8 it is {@code h} wide and {@code w} high.
     */
    private static AffineTransform orientationTransform(int orientation, int w, int h) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // (x, y) -> (w - x, y)
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // (x, y) -> (w - x, h - y)
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // (x, y) -> (x, h - y)
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // (x, y) -> (y, x)
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // (x, y) -> (h - y, x)
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // (x, y) -> (h - y, w - x)
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);   // (x, y) -> (y, w - x)
            default -> throw new IllegalArgumentException("orientation must be 2 to 8");
        };
    }
}
