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

import com.twelvemonkeys.imageio.metadata.Directory;
import com.twelvemonkeys.imageio.metadata.Entry;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegment;
import com.twelvemonkeys.imageio.metadata.jpeg.JPEGSegmentUtil;
import com.twelvemonkeys.imageio.metadata.tiff.TIFFReader;
import com.twelvemonkeys.imageio.stream.SubImageInputStream;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Static helpers that read images through ImageIO without holding more memory than the caller
 * budgeted for: header reads that never decode, the subsampling arithmetic, streamed subsampled
 * decodes and EXIF orientation. They are shared by the Flying Saucer user agent (images that
 * arrive during layout) and the image planning in {@code service} (images checked before layout).
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
 *       is never loaded whole and the one large allocation is the already reduced result.</li>
 *   <li><b>Content problems do not escape as unchecked exceptions.</b> Header and EXIF reads
 *       return empty or {@code 1} for any input they cannot use. A decode throws
 *       {@link IOException}. Only a null argument or an out-of-range subsampling (programming
 *       errors) throws anything else.</li>
 * </ul>
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

    /**
     * How many times a WebP reader may read past the end of the data before the decode is cut off
     * (see {@link EofBudgetImageInputStream}). Package-private so that the tests can check how much
     * room the valid files leave below it.
     */
    // measured: valid WebPs read past the end at most 828 times (14 588 files, tiny flat images); hostile 36-byte ones ran for minutes unguarded and fail in 273 ms at most at 65 536 (margin 79x)
    static final int WEBP_MAX_READS_PAST_END = 65_536;

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
     *         header is corrupt or declares a size below 1 pixel, when the file is missing, a
     *         directory or unreadable, or when the reader fails in any other way; never throws
     *         because of the file's content
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
     * <p>The bytes are wrapped in an in-memory cache stream and never in ImageIO's default
     * stream-input cache, which is a temporary file on disk: the bytes are a patient's document and
     * must not be copied there.</p>
     *
     * @param data the image bytes, for example a decoded {@code data:} URI payload; not modified
     * @return the header, or empty in the cases listed for {@link #readHeader(Path)}
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public static Optional<ImageHeader> readHeader(byte[] data) {
        Objects.requireNonNull(data, "data");
        try (ImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
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
     * file is never read into memory whole, and the cost is the reduced result, about
     * {@code pixels / subsampling^2} times the bytes per pixel. Measured: a 67-megapixel, 67 MB PNG
     * was reduced to 2048 x 2048 in a 64 MB heap, where reading the file into memory first ran out
     * of it. The result is {@code ceil(width / s) x ceil(height / s)} in whatever type the reader
     * produces (indexed, gray, 16-bit, ...), and callers convert it if they need another.</p>
     *
     * <p>This method does not check the declared size: callers refuse a header above
     * {@link #MAX_SOURCE_PIXELS} first, and choose {@code subsampling} with
     * {@link #subsamplingToFit(ImageHeader, long)}, because a result that does not fit the heap
     * fails with {@link OutOfMemoryError}.</p>
     *
     * <p>A WebP is read through a view that fails once the reader has read past the end of the
     * data more than 65 536 times (see {@link EofBudgetImageInputStream}). Valid files stay about
     * eighty times below that, and a corrupt lossless one, which the library alone would spend 15 s
     * or more on, fails with an {@code IOException} within a fraction of a second. No other format
     * is affected.</p>
     *
     * @param file the image file
     * @param subsampling how many source pixels to step over, at least 1 (1 decodes in full)
     * @return the decoded, reduced image
     * @throws IOException if the file cannot be opened, no reader recognises it, or the reader
     *         fails to decode it; the message names neither the file nor its content
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
     * direction. The same rules and results as {@link #readSubsampled(Path, int)}.
     *
     * @param data the image bytes, for example a decoded {@code data:} URI payload; not modified
     * @param subsampling how many source pixels to step over, at least 1
     * @return the decoded, reduced image
     * @throws IOException if no reader recognises the data or the reader fails to decode it
     * @throws IllegalArgumentException if {@code subsampling} is below 1
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public static BufferedImage readSubsampled(byte[] data, int subsampling) throws IOException {
        Objects.requireNonNull(data, "data");
        requireSubsampling(subsampling);
        try (ImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
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
                throw new IOException("no image reader recognises the input");
            }
            if (isWebp(reader)) {
                // The one reader found to loop for 15 s or more on corrupt data instead of failing. The
                // other readers get the stream as it is, so their handling of truncated files
                // (the JDK's JPEG reader returns what it has) does not change.
                try (ImageInputStream bounded = new EofBudgetImageInputStream(stream, WEBP_MAX_READS_PAST_END)) {
                    return read(reader, bounded, subsampling);
                }
            }
            return read(reader, stream, subsampling);
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

    private static BufferedImage read(ImageReader reader, ImageInputStream input, int subsampling) throws IOException {
        // Not forward-only: a TIFF may store its strips out of order, and a random-access
        // stream on a file costs nothing extra.
        reader.setInput(input, false, true);
        ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceSubsampling(subsampling, subsampling, 0, 0);
        return reader.read(0, param);
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an ImageIO reader's format name (an internal ASCII identifier such as "webp", never user input); not a security or authorization decision
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an ImageIO reader's format name (an internal ASCII identifier such as webp, never user input); not a security or authorization decision")
    private static boolean isWebp(ImageReader reader) throws IOException {
        return "webp".equals(reader.getFormatName().toLowerCase(Locale.ROOT));
    }

    private static void requireSubsampling(int subsampling) {
        if (subsampling < 1) {
            throw new IllegalArgumentException("subsampling must be at least 1");
        }
    }

    /**
     * The reader both the header and the decode use, so that they agree on what the image is.
     *
     * @return the first reader that claims the stream, or {@code null} if none does
     */
    private static ImageReader firstReader(ImageInputStream stream) {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
        return readers.hasNext() ? readers.next() : null;
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
        try (ImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
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
            Entry entry = directory.getEntryById(TAG_ORIENTATION);
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
