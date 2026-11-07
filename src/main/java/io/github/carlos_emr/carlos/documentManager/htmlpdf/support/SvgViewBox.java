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

import org.jsoup.nodes.Element;

import java.util.Locale;
import java.util.Objects;

/**
 * Gives an inline {@code <svg>} that declares only a {@code viewBox} the size that viewBox names.
 *
 * <p>Flying Saucer reads the size of an inline SVG from its {@code width} and {@code height}
 * attributes, in pixels, and turns those into the element's CSS size; with no attributes it reads the
 * viewBox's width and height as <em>device dots</em>, twenty to a pixel. An icon such as
 * {@code <svg viewBox="0 0 24 24">} that the page does not size would print 24 dots wide, about a
 * millimetre, where a browser draws it at least as wide as its viewBox. Writing the viewBox's size as
 * the attributes gives it the size of its viewBox, and since attributes are presentational hints, a
 * rule in the document's style sheets that sizes the element still wins.</p>
 *
 * <p>An element with either attribute, with a {@code width} or {@code height} in its own
 * {@code style}, or without a viewBox of four plain decimal numbers is left alone. A stylesheet rule
 * that gives only one of the two sizes of such an element (a {@code class} with {@code width} and no
 * {@code height}) meets a {@code height} attribute that a browser would not have: the element's box is
 * taller than its width, the drawing in it still in proportion (a known limitation).</p>
 *
 * @since 2026-10-30
 */
public final class SvgViewBox {

    /** No SVG a form prints is wider than this many pixels; a viewBox beyond it is somebody's mistake. */
    private static final double MAX_SIDE = 100_000d;

    /** The least a side may be and still be written with the three decimals {@link #format} keeps. */
    private static final double MIN_SIDE = 0.001d;

    private static final double[] NONE = new double[0];

    private SvgViewBox() {
    }

    /**
     * Writes the viewBox's width and height as the {@code width} and {@code height} attributes of an
     * SVG that has neither, and no CSS size of its own.
     *
     * @param svg the {@code <svg>} element to change
     * @return {@code true} if the attributes were written
     */
    public static boolean sizeFromViewBox(Element svg) {
        Objects.requireNonNull(svg, "svg");
        if (svg.hasAttr("width") || svg.hasAttr("height") || declaresSize(svg.attr("style"))) {
            return false;
        }
        double[] box = parse(svg.attr("viewBox"));
        if (box.length == 0) {
            return false;
        }
        svg.attr("width", format(box[2]));
        svg.attr("height", format(box[3]));
        return true;
    }

    /** Whether CSS text holds a {@code width} or {@code height} declaration (or something that looks like one). */
    private static boolean declaresSize(String style) {
        String lower = style.toLowerCase(Locale.ROOT);
        return lower.contains("width") || lower.contains("height");
    }

    /**
     * Reads {@code min-x min-y width height}, separated by white space and/or commas, as plain decimals.
     *
     * @return the four numbers, or an empty array if there are not exactly four, one is not a plain decimal,
     *         or the width or height is not between 0.001 and 100,000
     */
    static double[] parse(String viewBox) {
        double[] box = new double[4];
        int count = 0;
        int i = 0;
        int length = viewBox.length();
        while (i < length) {
            char c = viewBox.charAt(i);
            if (Character.isWhitespace(c) || c == ',') {
                i++;
                continue;
            }
            int end = i;
            while (end < length && !Character.isWhitespace(viewBox.charAt(end)) && viewBox.charAt(end) != ',') {
                end++;
            }
            if (count == box.length || !isPlainDecimal(viewBox, i, end)) {
                return NONE;
            }
            box[count++] = Double.parseDouble(viewBox.substring(i, end));
            i = end;
        }
        // With fewer than four numbers the height, at least, is still zero, which is below the least a side may be.
        if (!(box[2] >= MIN_SIDE) || !(box[3] >= MIN_SIDE) || box[2] > MAX_SIDE || box[3] > MAX_SIDE) {
            return NONE;
        }
        return box;
    }

    /** {@code [+-]? digits [. digits]} or {@code [+-]? . digits}: not an exponent, NaN, Infinity, hex or a suffix. */
    private static boolean isPlainDecimal(String text, int from, int to) {
        int i = from;
        if (i < to && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
            i++;
        }
        int digits = 0;
        while (i < to && isDigit(text.charAt(i))) {
            i++;
            digits++;
        }
        if (i < to && text.charAt(i) == '.') {
            i++;
            while (i < to && isDigit(text.charAt(i))) {
                i++;
                digits++;
            }
        }
        return i == to && digits > 0;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** The number with at most three decimals and no trailing zeros, so that the attribute is a plain number. */
    private static String format(double value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        int end = text.length();
        while (text.charAt(end - 1) == '0') {
            end--;
        }
        if (text.charAt(end - 1) == '.') {
            end--;
        }
        return text.substring(0, end);
    }
}
