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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.openpdf.text.Image;
import org.xhtmlrenderer.extend.FSImage;
import org.xhtmlrenderer.pdf.ITextFSImage;
import org.xhtmlrenderer.pdf.ITextOutputDevice;
import org.xhtmlrenderer.pdf.SvgImage;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * An {@link ITextOutputDevice} that makes the bitmap of an SVG once and keeps it within a bound.
 *
 * <p>Flying Saucer draws an SVG as vectors, unless it holds a gradient, pattern, filter or mask or is
 * translucent (the things a PDF page cannot say in vectors here). Those it draws as a bitmap, made by
 * Batik at the size of the SVG's box <em>in device dots</em>, 20 to a CSS pixel, <em>every time the SVG is drawn</em>:
 * a 500 x 500 px chart with one gradient becomes 10,000 x 10,000 pixels, about 400 MB as a bitmap and more
 * while it is encoded and embedded, an author who writes {@code width: 20000px} asks for 160 billion, and a
 * small gradient used as a repeating background is made and embedded again for each of its thousands of
 * tiles (measured: 55 seconds and a 17 MB PDF for one 800 x 800 px box). The packaged server exits on an
 * {@code OutOfMemoryError}, so one such image in an HRM report would restart it.</p>
 *
 * <p>This device draws such an SVG from one bitmap, kept for the length of the render, of at most
 * {@link #maxRasterPixels()} pixels, kept in proportion and stretched to the box, which is still 300 dots
 * per inch or better for the images a form or a report holds. An SVG drawn as vectors and every other image
 * is drawn as Flying Saucer draws it. The decision whether an SVG can be drawn as vectors is Flying Saucer's
 * own, read from its package-private method; if that cannot be read, an SVG is drawn from the bounded
 * bitmap, which is the safe way to be wrong.</p>
 *
 * <p>An SVG that Batik cannot draw (a {@code use} of an element that does not exist throws, and so does a
 * path it cannot parse) is left out of the page, not allowed to fail the document: the exception would
 * end the attempt, and the ladder would print the whole document as text for the sake of one picture.
 * Flying Saucer draws a vector SVG into a template of its own and adds the template to the page only
 * when the drawing is complete, so the page is as if the SVG were not there. Each SVG that fails is
 * tried once and counted by {@link #leftOutImages()}, so that a background of it tiled forty thousand times
 * does not fail forty thousand times. Only the exception's class is logged; its message can quote
 * the document.</p>
 *
 * @since 2026-10-30
 */
public final class BoundedOutputDevice extends ITextOutputDevice {

    /** The smallest bound; below it an SVG would print too coarsely to be of use. */
    static final long MIN_RASTER_PIXELS = 1_000_000L;

    /** The largest bound: 8 million pixels is a full page at 300 dots per inch. */
    static final long MAX_RASTER_PIXELS = 8_000_000L;

    /** Flying Saucer's own test of whether an SVG can be drawn as vectors, or {@code null} if it cannot be read. */
    private static final Method DRAWABLE_AS_VECTOR = drawableAsVectorMethod();

    private static final Logger logger = MiscUtils.getLogger();

    private final long maxRasterPixels;
    private final Map<SvgImage, ITextFSImage> rasters = new IdentityHashMap<>();
    private final Set<SvgImage> failed = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Creates a device with the bound that suits the JVM's heap.
     *
     * @param dotsPerPoint the dots per point of the renderer this device belongs to
     */
    public BoundedOutputDevice(float dotsPerPoint) {
        this(dotsPerPoint, defaultMaxRasterPixels());
    }

    /**
     * Creates a device with a given bound.
     *
     * @param dotsPerPoint the dots per point of the renderer this device belongs to
     * @param maxRasterPixels the most pixels the bitmap of an SVG may have
     * @throws IllegalArgumentException if the bound is below one
     */
    public BoundedOutputDevice(float dotsPerPoint, long maxRasterPixels) {
        super(dotsPerPoint);
        if (maxRasterPixels < 1) {
            throw new IllegalArgumentException("The bound must be at least one pixel");
        }
        this.maxRasterPixels = maxRasterPixels;
    }

    /**
     * The bound for this JVM: sixteen bytes a pixel for every one of the sixteen renders that could share the heap
     * (a bitmap costs about that much while it is drawn, encoded and embedded), between one and eight million.
     *
     * @return the bound in pixels
     */
    public static long defaultMaxRasterPixels() {
        return defaultMaxRasterPixels(Runtime.getRuntime().maxMemory());
    }

    static long defaultMaxRasterPixels(long maxMemory) {
        return Math.max(MIN_RASTER_PIXELS, Math.min(MAX_RASTER_PIXELS, maxMemory / 256));
    }

    /** {@return the most pixels the bitmap of an SVG may have} */
    public long maxRasterPixels() {
        return maxRasterPixels;
    }

    /** {@return how many SVG images could not be drawn and were left out of the pages} */
    public int leftOutImages() {
        return failed.size();
    }

    @Override
    public void drawImage(FSImage image, int x, int y) {
        if (!(image instanceof SvgImage svg)) {
            super.drawImage(image, x, y);
        } else if (!failed.contains(svg)) {
            try {
                super.drawImage(bounded(svg), x, y);
            } catch (RuntimeException e) {
                failed.add(svg);
                logger.warn("An SVG image could not be drawn and is left out ({})", e.getClass().getSimpleName());
            }
        }
    }

    /** The image to draw: the same one, unless it is an SVG that Flying Saucer would turn into a bitmap. */
    FSImage bounded(FSImage image) {
        if (image instanceof SvgImage svg && !drawableAsVector(DRAWABLE_AS_VECTOR, svg)) {
            return rasters.computeIfAbsent(svg, source -> new BoundedSvgRaster(source, maxRasterPixels));
        }
        return image;
    }

    /** Whether Flying Saucer draws the SVG as vectors; {@code false} if the method is missing or fails. */
    static boolean drawableAsVector(Method method, SvgImage svg) {
        if (method == null) {
            return false;
        }
        try {
            return (boolean) method.invoke(svg);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static Method drawableAsVectorMethod() {
        try {
            Method method = SvgImage.class.getDeclaredMethod("isDrawableAsVector");
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /**
     * An SVG drawn from a bitmap of bounded size, made once and stretched to the SVG's box.
     *
     * <p>{@code FSImage.scale} is generic in its result type; like Flying Saucer's own images, this class returns
     * its own type, which javac can only accept with an unchecked conversion.</p>
     */
    @SuppressWarnings("unchecked")
    static final class BoundedSvgRaster extends ITextFSImage {

        private final SvgImage svg;
        private final long maxPixels;
        private Image bitmap;

        BoundedSvgRaster(SvgImage svg, long maxPixels) {
            super(null);
            this.svg = svg;
            this.maxPixels = maxPixels;
        }

        @Override
        public int getWidth() {
            return svg.getWidth();
        }

        @Override
        public int getHeight() {
            return svg.getHeight();
        }

        /** The bitmap, made once: a logo drawn fifty times, or tiled over a page, is one image in the PDF. */
        // FindSecBugs/SpotBugs EI_EXPOSE_REP: Flying Saucer's contract is that getImage() hands the renderer the very image it embeds, as ITextFSImage does
        @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "the FSImage contract: the renderer embeds the very image it is given, as ITextFSImage.getImage does")
        @Override
        public Image getImage() {
            if (bitmap == null) {
                bitmap = boundedSource().getImage();
            }
            return bitmap;
        }

        /** The SVG at its own size if that is within the bound, else a copy small enough to rasterise. */
        private SvgImage boundedSource() {
            if ((long) svg.getWidth() * svg.getHeight() <= maxPixels) {
                return svg;
            }
            double factor = Math.sqrt((double) maxPixels / ((double) svg.getWidth() * svg.getHeight()));
            int width = Math.max(1, (int) Math.floor(svg.getWidth() * factor));
            int height = Math.max(1, (int) Math.floor(svg.getHeight() * factor));
            // A side raised to one pixel (a box a thousand million times longer than wide) leaves the other
            // side the whole bound at most.
            if ((long) width * height > maxPixels) {
                if (width == 1) {
                    height = (int) Math.min(height, maxPixels);
                } else {
                    width = (int) Math.min(width, maxPixels);
                }
            }
            return svg.scale(width, height);
        }

        @Override
        public ITextFSImage scale(int width, int height) {
            return new BoundedSvgRaster(svg.scale(width, height), maxPixels);
        }

        @Override
        public Object clone() {
            return new BoundedSvgRaster(svg, maxPixels);
        }
    }
}
