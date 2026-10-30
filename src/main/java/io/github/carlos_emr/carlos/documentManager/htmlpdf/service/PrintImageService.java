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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.AllowedDirectories;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.DataUris;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.ImageDecoding;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.ImageHeader;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.ImageBox;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.SvgReferences;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.SvgViewBox;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Decides what to do with every image of a document, and then does it (step j of the render
 * pipeline): an {@code <img>} is used, converted, cleaned or replaced by a box, always within the
 * memory the render is given for images. {@link #prepare} does both in turn; {@link #plan} is the
 * <em>planning</em> half and {@link #apply} the half that writes files and rewrites the document.
 *
 * <p>{@link #plan} reads the document in order, rules 1 to 5 of the spec's image section, and writes
 * nothing:</p>
 *
 * <ol>
 *   <li><b>Resolve the source as the renderer will:</b> a {@code data:} URI is decoded; anything else
 *       must be a valid URI reference resolved against the render's base directory, naming an existing
 *       regular file inside an allowed directory. Query and fragment are ignored for a file. A remote
 *       source is never fetched.</li>
 *   <li><b>Type:</b> a PDF is unusable (Flying Saucer would render it as an image page).</li>
 *   <li><b>SVG</b> is planned to be cleaned, with an inline {@code <svg>}.</li>
 *   <li><b>Header:</b> only the header is read. No vetted reader (so HEIC, WebP and the rest) or a
 *       header over {@value io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.ImageDecoding#MAX_SOURCE_PIXELS}
 *       pixels makes the image unusable.</li>
 *   <li><b>Plan and charge:</b> a JPEG of at most 32 MB that needs no turning is used as it is at any
 *       resolution (charged four times its file size); another format Open PDF embeds, of at most 32 MB
 *       and the per-image pixel cap, is used as it is (16 bytes a pixel); everything else a vetted
 *       reader can read is converted at the smallest subsampling that fits the cap. The per-image cap
 *       is the smaller of 25 megapixels and what the memory share holds at 16 bytes a pixel.</li>
 * </ol>
 *
 * <p>If the document's total charge (each distinct file or {@code data:} payload once) is more than
 * the share, the largest images are planned smaller first ("water-filling": the biggest are brought
 * down to a common level, the smaller ones untouched), and a JPEG that still does not fit is converted.
 * Printed sizes are kept. With no memory share at all (no render slot) every raster image is a box. The
 * plan depends only on the document and the numbers it is given.</p>
 *
 * <p>{@link #apply} carries the plan out (rules 6 and 7, and the cleaning of SVG): an image used as it
 * is keeps its bytes and gets the encoded file URI as its source; one that must be converted or cleaned is
 * written into the attempt's private directory (a JPEG again as a JPEG, anything else as a PNG) and the
 * element points at that file, with the original size set when it was reduced and declares none; an unusable
 * one becomes a box. A file is read again here only through {@link #readAtMost}, because it may have
 * changed since the plan was made, and an image that cannot be used at this point is boxed rather than
 * failing the attempt.</p>
 *
 * @since 2026-10-29
 */
@Service
class PrintImageService {

    private static final Logger logger = MiscUtils.getLogger();

    /** The largest file used without conversion: it is held in memory up to four times (renderer, layout PDF, page import, final PDF). */
    static final long AS_IS_MAX_FILE_BYTES = 32L << 20;

    /** What a decoded pixel costs in memory while Flying Saucer holds, converts and embeds it (measured, spec §Images). */
    static final int BYTES_PER_DECODED_PIXEL = 16;

    /** An embedded JPEG is charged this many times its file size. */
    static final int JPEG_CHARGE_FACTOR = 4;

    /**
     * Native memory the JDK uses to decode a progressive JPEG, per source pixel, whatever the
     * subsampling and outside the Java heap (see {@link ImageDecoding#readSubsampled}). Charged to
     * a progressive JPEG that has to be decoded.
     */
    static final int PROGRESSIVE_JPEG_BYTES_PER_SOURCE_PIXEL = 4;

    /** Quality of a JPEG written after conversion: the result is cheap to embed again. */
    static final float JPEG_QUALITY = 0.9f;

    /** An SVG larger than this is a box: it is parsed into memory (a guard, not a measured figure). */
    static final long MAX_SVG_BYTES = 16L << 20;

    /** The most a {@code data:} payload may decode to; the user agent refuses a file above this at layout. */
    static final long MAX_DATA_URI_BYTES = 128L << 20;

    /** How much of a file is looked at to tell a PDF or an SVG from a raster image. */
    private static final int SNIFF_BYTES = 4096;

    /** Formats OpenPDF embeds as they are (a JPEG is handled on its own). */
    private static final Set<String> EMBEDDABLE_AS_IS = Set.of("png", "gif", "bmp", "tiff", "tif");

    // Fixed phrases for the reason a box was made; logged, never containing a path or a URL.
    static final String REASON_MARKED_UNAVAILABLE = "marked unavailable";
    static final String REASON_NO_SOURCE = "no source";
    static final String REASON_INVALID_SOURCE = "invalid source";
    static final String REASON_NOT_LOCAL = "remote or unsupported source";
    static final String REASON_OUTSIDE = "outside the allowed directories";
    static final String REASON_MISSING = "missing or not a file";
    static final String REASON_EMPTY = "empty file";
    static final String REASON_UNREADABLE = "unreadable";
    static final String REASON_PDF = "PDF";
    static final String REASON_SVG_TOO_LARGE = "SVG too large";
    static final String REASON_UNSUPPORTED_FORMAT = "unsupported image format";
    static final String REASON_TOO_MANY_PIXELS = "header declares too many pixels";

    /** Where each attempt's private image directory is made. */
    private final Path tempRoot;

    /** Production wiring: the JVM's temporary directory. */
    @Autowired
    PrintImageService() {
        this(Path.of(System.getProperty("java.io.tmpdir")));
    }

    /**
     * For tests: a chosen directory for the attempt's image directory.
     *
     * @param tempRoot where {@code carlos-pdf-images-*} directories are created
     */
    PrintImageService(Path tempRoot) {
        this.tempRoot = Objects.requireNonNull(tempRoot, "tempRoot");
    }

    /**
     * Step j: plans the images and carries the plan out (text-only mode has none).
     *
     * @param document the document after the other document rules; its images are rewritten or boxed
     * @param baseDirectory the directory relative sources resolve against
     * @param allowedDirectories the directories a source file may be in
     * @param mode the rung of the ladder; {@code TEXT_ONLY} has no images
     * @param imageMemoryBytes the render's memory share for images; 0 without a render slot
     * @param degradations the attempt's degradations, added to as images are converted or boxed
     * @return the files written; close it when the attempt is over
     * @throws IOException if the attempt's image directory cannot be made
     */
    PreparedPrintImages prepare(Document document, Path baseDirectory, List<Path> allowedDirectories, PrintMode mode,
            long imageMemoryBytes, Set<HtmlPdfRenderDegradation> degradations) throws IOException {
        Objects.requireNonNull(mode, "mode");
        if (mode == PrintMode.TEXT_ONLY) {
            return PreparedPrintImages.none();
        }
        return apply(document, plan(document, baseDirectory, allowedDirectories, imageMemoryBytes), degradations);
    }

    /**
     * Plans the images of a document without writing a file.
     *
     * @param document the document after the other document rules; its elements are not changed
     * @param baseDirectory the directory relative sources resolve against (absolute)
     * @param allowedDirectories the directories a source file may be in
     * @param imageMemoryBytes the render's memory share for images; 0 when it has no render slot
     * @return one decision per {@code <img>} and outermost {@code <svg>}, in document order
     */
    ImagePlan plan(Document document, Path baseDirectory, List<Path> allowedDirectories, long imageMemoryBytes) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(baseDirectory, "baseDirectory");
        Objects.requireNonNull(allowedDirectories, "allowedDirectories");
        long share = Math.max(0, imageMemoryBytes);
        long pixelCap = Math.min(ImageDecoding.MAX_DECODED_PIXELS, share / BYTES_PER_DECODED_PIXEL);
        URI base = directoryUri(baseDirectory);

        List<Entry> entries = new ArrayList<>();
        Map<String, Raster> rasters = new LinkedHashMap<>();
        Map<String, Svg> svgs = new LinkedHashMap<>();
        for (Element element : document.select("img, svg")) {
            if (element.normalName().equals("svg")) {
                if (element.parents().stream().noneMatch(parent -> parent.normalName().equals("svg"))) {
                    entries.add(Entry.inlineSvg(element));
                }
                continue;
            }
            entries.add(examine(element, base, allowedDirectories, share, pixelCap, rasters, svgs));
        }

        fit(rasters, share);
        return assemble(entries, rasters);
    }

    // ---- one element ------------------------------------------------------------------------

    private Entry examine(Element img, URI base, List<Path> allowedDirectories, long share, long pixelCap,
            Map<String, Raster> rasters, Map<String, Svg> svgs) {
        if (img.hasAttr("data-carlos-image-unavailable")) {
            return Entry.box(img, REASON_MARKED_UNAVAILABLE);
        }
        String src = img.attr("src").strip();
        if (src.isEmpty()) {
            return Entry.box(img, REASON_NO_SOURCE);
        }
        Source source = load(src, base, allowedDirectories);
        if (source.reason != null) {
            return Entry.box(img, source.reason);
        }
        byte[] head = source.head();
        if (startsWithPdf(head)) {
            return Entry.box(img, REASON_PDF);
        }
        if (looksLikeSvg(head)) {
            if (source.size > MAX_SVG_BYTES) {
                return Entry.box(img, REASON_SVG_TOO_LARGE);
            }
            Svg svg = svgs.computeIfAbsent(source.key, key -> new Svg(source));
            return Entry.svg(img, svg);
        }
        Raster existing = rasters.get(source.key);
        if (existing != null) {
            return Entry.raster(img, existing);
        }
        return rasterEntry(img, source, share, pixelCap, rasters);
    }

    /** Resolves {@code src} as the renderer would, and reads the file or decodes the payload. */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of a URI scheme to recognise file: URIs; the allowed-directory containment check decides access, not this comparison
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of a URI scheme; the allowed-directory containment check decides access, not this comparison")
    private Source load(String src, URI base, List<Path> allowedDirectories) {
        if (DataUris.isDataUri(src)) {
            return DataUris.decodeBase64(src, MAX_DATA_URI_BYTES)
                    .map(bytes -> bytes.length == 0 ? Source.failed(REASON_EMPTY) : Source.ofData(bytes))
                    .orElseGet(() -> Source.failed(REASON_INVALID_SOURCE));
        }
        URI resolved;
        try {
            resolved = base.resolve(new URI(src));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return Source.failed(REASON_INVALID_SOURCE);
        }
        if (!"file".equalsIgnoreCase(resolved.getScheme()) || (resolved.getAuthority() != null && !resolved.getAuthority().isEmpty())) {
            return Source.failed(REASON_NOT_LOCAL);
        }
        Path file;
        try {
            // getPath() is decoded and leaves out the query and the fragment, which a file source ignores.
            file = Path.of(resolved.getPath());
        } catch (RuntimeException e) {
            return Source.failed(REASON_INVALID_SOURCE);
        }
        if (!AllowedDirectories.contains(allowedDirectories, file)) {
            return Source.failed(REASON_OUTSIDE);
        }
        try {
            if (!Files.isRegularFile(file)) {
                return Source.failed(REASON_MISSING);
            }
            long size = Files.size(file);
            return size == 0 ? Source.failed(REASON_EMPTY) : Source.ofFile(file.toRealPath(), size);
        } catch (IOException | RuntimeException e) {
            return Source.failed(REASON_UNREADABLE);
        }
    }

    /** Reads the header of a raster source and chooses how it is used, before the memory share is applied. */
    private Entry rasterEntry(Element img, Source source, long share, long pixelCap, Map<String, Raster> rasters) {
        if (share <= 0 || pixelCap < 1) {
            return Entry.box(img, ImagePlan.REASON_NO_SLOT);
        }
        java.util.Optional<ImageHeader> read = source.file != null
                ? ImageDecoding.readHeader(source.file) : ImageDecoding.readHeader(source.data);
        if (read.isEmpty()) {
            return Entry.box(img, REASON_UNSUPPORTED_FORMAT);
        }
        ImageHeader header = read.get();
        if (header.pixels() > ImageDecoding.MAX_SOURCE_PIXELS) {
            return Entry.box(img, REASON_TOO_MANY_PIXELS);
        }
        boolean jpeg = header.isJpeg();
        int orientation = jpeg ? (source.file != null ? ImageDecoding.exifOrientation(source.file)
                : ImageDecoding.exifOrientation(source.data)) : 1;
        boolean progressive = jpeg && (source.file != null ? ImageDecoding.isProgressiveJpeg(source.file)
                : ImageDecoding.isProgressiveJpeg(source.data));
        Kind kind;
        int subsampling = 1;
        if (jpeg && orientation == 1 && source.size <= AS_IS_MAX_FILE_BYTES) {
            kind = Kind.JPEG_AS_IS;
        } else if (!jpeg && orientation == 1 && source.size <= AS_IS_MAX_FILE_BYTES && header.pixels() <= pixelCap
                && EMBEDDABLE_AS_IS.contains(header.formatName().toLowerCase(Locale.ROOT))) {
            kind = Kind.DECODED_AS_IS;
        } else {
            kind = Kind.CONVERT;
            subsampling = ImageDecoding.subsamplingToFit(header, pixelCap);
        }
        // If OpenPDF cannot embed an image planned as it is, it is converted instead, and must still fit the cap.
        int fallback = kind == Kind.CONVERT ? subsampling : ImageDecoding.subsamplingToFit(header, pixelCap);
        Raster raster = new Raster(source, header, orientation, progressive, kind, subsampling, fallback);
        rasters.put(source.key, raster);
        return Entry.raster(img, raster);
    }

    // ---- the memory share -------------------------------------------------------------------

    /**
     * Brings the total charge within the share by planning the largest images smaller first. The
     * level every charge is capped at is the largest one that fits (found by bisection), so the
     * biggest images are reduced to a common size and smaller ones stay as they are.
     */
    private void fit(Map<String, Raster> rasters, long share) {
        long total = rasters.values().stream().mapToLong(raster -> raster.charge).sum();
        if (total <= share) {
            return;
        }
        long low = 0;
        long high = rasters.values().stream().mapToLong(raster -> raster.charge).max().orElse(0);
        // The largest level at which everything fits, if any; at level 0 every image is as small as it gets.
        while (low < high) {
            long middle = low + (high - low + 1) / 2;
            if (chargeAt(rasters.values(), middle) <= share) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        for (Raster raster : rasters.values()) {
            raster.reduceTo(low);
        }
        // Even every image at one pixel does not fit (very many images): box the last ones until it does.
        List<Raster> byAppearance = new ArrayList<>(rasters.values());
        for (int i = byAppearance.size() - 1; i >= 0 && chargeOf(byAppearance) > share; i--) {
            byAppearance.get(i).boxReason = ImagePlan.REASON_MEMORY;
        }
    }

    private static long chargeAt(Iterable<Raster> rasters, long level) {
        long total = 0;
        for (Raster raster : rasters) {
            total += raster.chargeAtLevel(level);
        }
        return total;
    }

    private static long chargeOf(List<Raster> rasters) {
        long total = 0;
        for (Raster raster : rasters) {
            total += raster.boxReason == null ? raster.charge : 0;
        }
        return total;
    }

    // ---- the plan ---------------------------------------------------------------------------

    private ImagePlan assemble(List<Entry> entries, Map<String, Raster> rasters) {
        List<PlannedImage> images = new ArrayList<>();
        Set<String> charged = new java.util.HashSet<>();
        long total = 0;
        for (Entry entry : entries) {
            switch (entry.kind) {
                case BOX -> images.add(new PlannedImage(entry.element, ImageOutcome.BOX, null, null, null, 1, 1, 0,
                        entry.reason, null));
                case INLINE_SVG -> images.add(new PlannedImage(entry.element, ImageOutcome.CLEAN_SVG, null, null, null, 1, 1, 0,
                        null, null));
                case SVG -> images.add(new PlannedImage(entry.element, ImageOutcome.CLEAN_SVG, entry.svg.source.file,
                        entry.svg.source.data, null, 1, 1, 0, null, entry.svg.source.key));
                case RASTER -> {
                    Raster raster = entry.raster;
                    if (raster.boxReason != null) {
                        images.add(new PlannedImage(entry.element, ImageOutcome.BOX, null, null, null, 1, 1, 0,
                                raster.boxReason, null));
                        continue;
                    }
                    long charge = charged.add(raster.source.key) ? raster.charge : 0;
                    total += charge;
                    boolean convert = raster.kind == Kind.CONVERT;
                    images.add(new PlannedImage(entry.element, convert ? ImageOutcome.CONVERT : ImageOutcome.AS_IS,
                            raster.source.file, convert ? raster.source.data : null, raster.header,
                            convert ? raster.subsampling : raster.fallbackSubsampling, raster.orientation, charge, null,
                            raster.source.key));
                }
                default -> throw new IllegalStateException("an image entry the planner cannot hold");
            }
        }
        return new ImagePlan(images, total);
    }

    // ---- applying the plan ------------------------------------------------------------------

    /**
     * Carries a plan out: writes the converted and cleaned images into a private directory, rewrites
     * each {@code <img>} to its final source, boxes what is unusable, and cleans inline SVG in place.
     * An image that turns out not to be usable at this point (it cannot be decoded, an SVG does not
     * parse) becomes a box like any other; nothing here fails the attempt except being unable to make
     * the directory.
     *
     * @param document the document the plan was made for
     * @param plan the plan
     * @param degradations the attempt's degradations, added to as images are converted or boxed
     * @return the files written; the directory is deleted if this throws
     * @throws IOException if the attempt's image directory cannot be made
     */
    PreparedPrintImages apply(Document document, ImagePlan plan, Set<HtmlPdfRenderDegradation> degradations)
            throws IOException {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(degradations, "degradations");
        Workspace workspace = new Workspace();
        try {
            Applier applier = new Applier(workspace, degradations);
            for (PlannedImage image : plan.images()) {
                applier.apply(image);
            }
            if (applier.omittedForMemory) {
                degradations.add(HtmlPdfRenderDegradation.IMAGES_OMITTED_FOR_MEMORY);
            }
            return workspace.hand();
        } catch (Throwable failure) {
            workspace.discard();
            throw failure;
        }
    }

    /** The attempt's image directory, made when the first file is written. */
    private final class Workspace {
        private Path directory;
        private int counter;

        Path newFile(String suffix) throws IOException {
            if (directory == null) {
                directory = Files.createTempDirectory(tempRoot, "carlos-pdf-images-");
            }
            return directory.resolve("image-" + (++counter) + suffix);
        }

        PreparedPrintImages hand() {
            return PreparedPrintImages.of(directory);
        }

        void discard() {
            PreparedPrintImages.deleteRecursively(directory);
        }
    }

    /** The outcome of a conversion or cleaning, shared by every element that uses the same source. */
    private record Written(Path file, boolean downscaled, int uprightWidth, int uprightHeight, boolean removedReferences) {
    }

    /** Carries out the planned decisions one element at a time. */
    private final class Applier {
        private final Workspace workspace;
        private final Set<HtmlPdfRenderDegradation> degradations;
        private final Map<String, Optional<Written>> written = new HashMap<>();
        private final Map<String, Boolean> embeddable = new HashMap<>();
        boolean omittedForMemory;

        Applier(Workspace workspace, Set<HtmlPdfRenderDegradation> degradations) {
            this.workspace = workspace;
            this.degradations = degradations;
        }

        void apply(PlannedImage image) throws IOException {
            Element element = image.element();
            switch (image.outcome()) {
                case BOX -> box(image, image.reason());
                case AS_IS -> asIs(image);
                case CONVERT -> use(image, convertOnce(image, image.subsampling()));
                case CLEAN_SVG -> cleanSvg(image);
                default -> throw new IllegalStateException("an image outcome the plan cannot hold");
            }
            if (element.ownerDocument() != null && element.hasAttr("srcset")) {
                // Flying Saucer does not read srcset; it never reaches a request, but it has no use either.
                element.removeAttr("srcset");
            }
        }

        // -- as is ---------------------------------------------------------------------------

        private void asIs(PlannedImage image) throws IOException {
            Boolean ok = embeddable.get(image.key());
            if (ok == null) {
                ok = canEmbed(bytesOf(image));
                embeddable.put(image.key(), ok);
            }
            if (!ok) {
                use(image, convertOnce(image, image.subsampling()));
                return;
            }
            if (image.file() != null) {
                image.element().attr("src", image.file().toUri().toString());
            }
        }

        /** Whether OpenPDF can embed the image: the call Flying Saucer makes (for a JPEG it reads only the header). */
        private boolean canEmbed(byte[] bytes) {
            if (bytes == null) {
                return false;
            }
            try {
                org.openpdf.text.Image.getInstance(bytes);
                return true;
            } catch (Exception | LinkageError e) {
                return false;
            }
        }

        // -- converted -----------------------------------------------------------------------

        private Optional<Written> convertOnce(PlannedImage image, int subsampling) throws IOException {
            // A failed conversion is remembered too (as an empty Optional), so a source is tried once.
            if (written.containsKey(image.key())) {
                return written.get(image.key());
            }
            Optional<Written> result = convert(image, subsampling);
            written.put(image.key(), result);
            return result;
        }

        private Optional<Written> convert(PlannedImage image, int subsampling) throws IOException {
            BufferedImage decoded;
            try {
                decoded = image.file() != null ? ImageDecoding.readSubsampled(image.file(), subsampling)
                        : ImageDecoding.readSubsampled(dataOf(image), subsampling);
            } catch (IOException | RuntimeException e) {
                logger.warn("An image could not be decoded and is left out ({})", e.getClass().getSimpleName());
                return Optional.empty();
            }
            BufferedImage upright = ImageDecoding.applyOrientation(decoded, image.orientation());
            boolean jpeg = image.header().isJpeg();
            Path target = workspace.newFile(jpeg ? ".jpg" : ".png");
            try {
                if (jpeg) {
                    writeJpegOrPng(upright, target);
                } else {
                    writePng(upright, target);
                }
            } catch (IOException | RuntimeException e) {
                logger.warn("A converted image could not be written and is left out ({})", e.getClass().getSimpleName());
                Files.deleteIfExists(target);
                return Optional.empty();
            }
            boolean turned = image.orientation() >= 5 && image.orientation() <= 8;
            ImageHeader header = image.header();
            return Optional.of(new Written(target, subsampling > 1, turned ? header.height() : header.width(),
                    turned ? header.width() : header.height(), false));
        }

        // -- SVG -----------------------------------------------------------------------------

        private void cleanSvg(PlannedImage image) throws IOException {
            if (image.file() == null && image.data() == null) {
                if (SvgReferences.clean(image.element())) {
                    degradations.add(HtmlPdfRenderDegradation.SVG_REFERENCES_REMOVED);
                }
                // Flying Saucer reads the size of an inline SVG with only a viewBox in device dots.
                SvgViewBox.sizeFromViewBox(image.element());
                return;
            }
            Optional<Written> result;
            if (written.containsKey(image.key())) {
                result = written.get(image.key());
            } else {
                result = writeCleanedSvg(image);
                written.put(image.key(), result);
            }
            use(image, result);
        }

        private Optional<Written> writeCleanedSvg(PlannedImage image) throws IOException {
            byte[] svg = image.file() != null ? readAtMost(image.file(), MAX_SVG_BYTES) : image.data();
            if (svg == null) {
                return Optional.empty();
            }
            Optional<SvgReferences.Cleaned> cleaned = SvgReferences.clean(svg);
            if (cleaned.isEmpty()) {
                logger.warn("An SVG image could not be parsed safely and is left out");
                return Optional.empty();
            }
            Path target = workspace.newFile(".svg");
            try {
                Files.write(target, cleaned.get().svg());
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(target);
                return Optional.empty();
            }
            return Optional.of(new Written(target, false, 0, 0, cleaned.get().removedReferences()));
        }

        // -- rewriting the element -----------------------------------------------------------

        /** Points the element at what was written, or boxes it if nothing could be. */
        private void use(PlannedImage image, Optional<Written> result) {
            Element element = image.element();
            if (result.isEmpty()) {
                box(image, REASON_UNREADABLE);
                return;
            }
            Written output = result.get();
            element.attr("src", output.file().toUri().toString());
            if (image.outcome() == ImageOutcome.CLEAN_SVG) {
                if (output.removedReferences()) {
                    degradations.add(HtmlPdfRenderDegradation.SVG_REFERENCES_REMOVED);
                }
                return;
            }
            degradations.add(output.downscaled() ? HtmlPdfRenderDegradation.IMAGE_DOWNSCALED
                    : HtmlPdfRenderDegradation.IMAGE_CONVERTED);
            if (output.downscaled() && !ImageBox.declaresSize(element)) {
                // The printed size is the original's, not the smaller file's.
                element.attr("width", Integer.toString(output.uprightWidth()));
                element.attr("height", Integer.toString(output.uprightHeight()));
            }
        }

        private void box(PlannedImage image, String reason) {
            image.element().replaceWith(ImageBox.replacing(image.element()));
            if (ImagePlan.REASON_NO_SLOT.equals(reason)) {
                omittedForMemory = true;
            } else {
                degradations.add(HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE);
                logger.warn("An image is shown as a box ({})", reason);
            }
        }

        /** The image as bytes, or {@code null} if it can no longer be read (the file changed after the plan was made). */
        private byte[] bytesOf(PlannedImage image) {
            if (image.file() != null) {
                return readAtMost(image.file(), AS_IS_MAX_FILE_BYTES);
            }
            return dataOf(image);
        }

        /** The decoded payload: kept in the plan if it had to be, else decoded again from the element. */
        private byte[] dataOf(PlannedImage image) {
            if (image.data() != null) {
                return image.data();
            }
            return DataUris.decodeBase64(image.element().attr("src").strip(), MAX_DATA_URI_BYTES).orElse(null);
        }
    }

    private static void writeJpegOrPng(BufferedImage image, Path target) throws IOException {
        try {
            writeJpeg(image, target);
        } catch (IOException | RuntimeException e) {
            // A color model the JPEG writer refuses; a lossless copy keeps the picture.
            writePng(image, target);
        }
    }

    private static void writeJpeg(BufferedImage image, Path target) throws IOException {
        BufferedImage rgb = image;
        if (image.getColorModel().hasAlpha()) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            try {
                graphics.setColor(java.awt.Color.WHITE);
                graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(target.toFile())) {
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(out);
            writer.write(null, new IIOImage(rgb, null, null), parameters);
        } finally {
            writer.dispose();
        }
    }

    private static void writePng(BufferedImage image, Path target) throws IOException {
        if (!ImageIO.write(image, "png", target.toFile())) {
            throw new IOException("no PNG writer for this image");
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    /**
     * Reads a file the plan found to be within a size limit, without trusting that it still is: the
     * file may have been replaced since the plan was made.
     *
     * @param file the file
     * @param limit the most bytes accepted
     * @return the bytes, or {@code null} if the file cannot be read or is larger than the limit
     */
    static byte[] readAtMost(Path file, long limit) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] bytes = in.readNBytes((int) Math.min(limit + 1, Integer.MAX_VALUE - 8L));
            return bytes.length > limit ? null : bytes;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The base directory as a URI that ends in a slash, so a relative reference resolves inside it. */
    private static URI directoryUri(Path baseDirectory) {
        String uri = baseDirectory.toUri().toString();
        return URI.create(uri.endsWith("/") ? uri : uri + "/");
    }

    private static boolean startsWithPdf(byte[] head) {
        return head.length >= 5 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F' && head[4] == '-';
    }

    private static boolean looksLikeSvg(byte[] head) {
        return new String(head, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT).contains("<svg");
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    // ---- value types ------------------------------------------------------------------------

    /** A resolved source: a real file or a decoded payload, or the reason it is unusable. */
    private static final class Source {
        final Path file;
        final byte[] data;
        final long size;
        final String key;
        final String reason;

        private Source(Path file, byte[] data, long size, String key, String reason) {
            this.file = file;
            this.data = data;
            this.size = size;
            this.key = key;
            this.reason = reason;
        }

        static Source ofFile(Path realPath, long size) {
            return new Source(realPath, null, size, realPath.toString(), null);
        }

        static Source ofData(byte[] data) {
            return new Source(null, data, data.length, "data:" + sha256(data), null);
        }

        static Source failed(String reason) {
            return new Source(null, null, 0, null, reason);
        }

        /** The first bytes, enough to tell a PDF or an SVG from a raster image. */
        byte[] head() {
            if (data != null) {
                return Arrays.copyOf(data, (int) Math.min(data.length, SNIFF_BYTES));
            }
            try (InputStream in = Files.newInputStream(file)) {
                return in.readNBytes(SNIFF_BYTES);
            } catch (IOException | RuntimeException e) {
                return new byte[0];
            }
        }
    }

    private enum Kind {
        JPEG_AS_IS, DECODED_AS_IS, CONVERT
    }

    /** One distinct raster image of the document and how it is currently planned. */
    private static final class Raster {
        final Source source;
        final ImageHeader header;
        final int orientation;
        final boolean progressiveJpeg;

        // The plan as first made, before the memory share is applied; the bisection asks what each
        // image would cost at a level against these and changes nothing.
        private final Kind initialKind;
        private final int initialSubsampling;
        private final long initialCharge;

        // The plan as it stands.
        Kind kind;
        int subsampling;
        long charge;
        String boxReason;

        /** The subsampling a conversion uses if the image planned as it is turns out not to be embeddable. */
        final int fallbackSubsampling;

        Raster(Source source, ImageHeader header, int orientation, boolean progressiveJpeg, Kind kind, int subsampling,
                int fallbackSubsampling) {
            this.source = source;
            this.fallbackSubsampling = fallbackSubsampling;
            this.header = header;
            this.orientation = orientation;
            this.progressiveJpeg = progressiveJpeg;
            this.initialKind = kind;
            this.initialSubsampling = subsampling;
            this.initialCharge = chargeOf(kind, subsampling);
            this.kind = kind;
            this.subsampling = subsampling;
            this.charge = initialCharge;
        }

        private long chargeOf(Kind forKind, int forSubsampling) {
            return switch (forKind) {
                case JPEG_AS_IS -> JPEG_CHARGE_FACTOR * source.size;
                case DECODED_AS_IS -> BYTES_PER_DECODED_PIXEL * header.pixels();
                case CONVERT -> convertedCharge(forSubsampling);
            };
        }

        /** Decoding a progressive JPEG uses native memory per source pixel, however small the result. */
        private long decodeOverhead() {
            return progressiveJpeg ? (long) PROGRESSIVE_JPEG_BYTES_PER_SOURCE_PIXEL * header.pixels() : 0;
        }

        private long convertedCharge(int forSubsampling) {
            long outputPixels = (long) Math.ceilDiv(header.width(), forSubsampling)
                    * Math.ceilDiv(header.height(), forSubsampling);
            return (long) BYTES_PER_DECODED_PIXEL * outputPixels + decodeOverhead();
        }

        /** The smallest subsampling, not below {@code atLeast}, at which a conversion costs at most {@code level}. */
        private int subsamplingFor(long level, int atLeast) {
            long pixels = Math.max(0, level - decodeOverhead()) / BYTES_PER_DECODED_PIXEL;
            return Math.max(atLeast, ImageDecoding.subsamplingToFit(header, pixels));
        }

        private int reducedSubsampling(long level) {
            return subsamplingFor(level, initialKind == Kind.CONVERT ? initialSubsampling : 1);
        }

        /**
         * What the image would be charged if no image were charged more than {@code level}. An image
         * that cannot be made cheaper than it is (a JPEG whose conversion would cost more than
         * embedding it, because decoding a progressive JPEG has a fixed native cost) keeps its plan.
         */
        long chargeAtLevel(long level) {
            if (initialCharge <= level) {
                return initialCharge;
            }
            return Math.min(initialCharge, convertedCharge(reducedSubsampling(level)));
        }

        /** Applies the level found by the bisection to this image. */
        void reduceTo(long level) {
            if (chargeAtLevel(level) == initialCharge) {
                kind = initialKind;
                subsampling = initialSubsampling;
                charge = initialCharge;
            } else {
                kind = Kind.CONVERT;
                subsampling = reducedSubsampling(level);
                charge = convertedCharge(subsampling);
            }
        }
    }

    private static final class Svg {
        final Source source;

        Svg(Source source) {
            this.source = source;
        }
    }

    private enum EntryKind {
        BOX, INLINE_SVG, SVG, RASTER
    }

    private static final class Entry {
        final Element element;
        final EntryKind kind;
        final String reason;
        final Raster raster;
        final Svg svg;

        private Entry(Element element, EntryKind kind, String reason, Raster raster, Svg svg) {
            this.element = element;
            this.kind = kind;
            this.reason = reason;
            this.raster = raster;
            this.svg = svg;
        }

        static Entry box(Element element, String reason) {
            return new Entry(element, EntryKind.BOX, reason, null, null);
        }

        static Entry inlineSvg(Element element) {
            return new Entry(element, EntryKind.INLINE_SVG, null, null, null);
        }

        static Entry svg(Element element, Svg svg) {
            return new Entry(element, EntryKind.SVG, null, null, svg);
        }

        static Entry raster(Element element, Raster raster) {
            return new Entry(element, EntryKind.RASTER, null, raster, null);
        }
    }
}
