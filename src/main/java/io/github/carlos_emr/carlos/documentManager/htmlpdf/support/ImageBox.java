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

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The placeholder that stands for an image that cannot be shown: a bordered box holding the text
 * "[image unavailable]" ({@link PrintMessages#imageUnavailable()}), the size of the image it replaces
 * when that is known.
 *
 * <p>The size comes from the element's {@code width} and {@code height} attributes or, taking
 * precedence as in CSS, from the same properties in its inline style. Only plain lengths are used
 * (pixels, percent, and points, ems, inches and centimetres converted to pixels), and a length is
 * limited to {@value #MAX_PIXELS} pixels: a box is a placeholder, and an absurd size would widen the
 * whole page and shrink everything else on it. The box has the class
 * {@code carlos-pdf-image-unavailable}, which {@code PrintStylesheet} gives its border and font.</p>
 *
 * <p>The label has to fit the box it is in, or only the start of it shows ("[image una"). When the width is
 * known in pixels and the label does not fit it on one line at the style sheet's 8 pt, the box says so itself: it
 * lets the label wrap between its words, and sets it smaller if the longest word would not fit either, down to 4 pt.
 * A box too short for two lines keeps one line and a smaller size instead. The widths of the letters are
 * estimated, a little wide, for the bundled DejaVu Sans, which every document is set in.</p>
 *
 * @since 2026-10-29
 */
public final class ImageBox {

    /** The class that styles the box. */
    public static final String CLASS = "carlos-pdf-image-unavailable";

    /** The largest width or height, in CSS pixels, a box takes: about the width of a printed page's content. */
    static final int MAX_PIXELS = 900;

    /** The size of the label in the style sheet (8 pt), in CSS pixels. */
    static final double LABEL_PIXELS = 8 * 4.0 / 3;

    /** The smallest label that is worth printing (4 pt), in CSS pixels. */
    static final double SMALLEST_LABEL_PIXELS = 4 * 4.0 / 3;

    /** What a character of the label is wide, as a share of its font size: DejaVu Sans lower-case text, rounded up. */
    static final double CHARACTER_WIDTH = 0.6;

    /** The line height of a document (the style sheet's {@code line-height}). */
    static final double LINE_HEIGHT = 1.164;

    /** The border and the padding on both sides of the box, in CSS pixels: 2 x (1 + 2). */
    static final int FRAME_PIXELS = 6;

    private static final Pattern LENGTH = Pattern.compile("(\\d+(?:\\.\\d+)?)(px|pt|em|rem|in|cm|mm|%)?");

    private ImageBox() {
    }

    /**
     * Builds the box for an image or graphic.
     *
     * @param original the {@code <img>} or {@code <svg>} to be replaced; not changed
     * @return the new element, not yet placed in the document
     */
    public static Element replacing(Element original) {
        Objects.requireNonNull(original, "original");
        Element box = new Element("span");
        box.addClass(CLASS);
        StringBuilder style = new StringBuilder();
        Optional<String> width = sizeOf(original, "width");
        Optional<String> height = sizeOf(original, "height");
        append(style, "width", width);
        append(style, "height", height);
        String label = PrintMessages.imageUnavailable();
        fitLabel(style, label, pixels(width), pixels(height));
        if (!style.isEmpty()) {
            box.attr("style", style.toString());
        }
        box.appendText(label);
        return box;
    }

    /** Wraps the label, or sets it smaller, when a box of this size is too narrow for it at the style sheet's size. */
    private static void fitLabel(StringBuilder style, String label, OptionalInt width, OptionalInt height) {
        if (width.isEmpty()) {
            return;
        }
        double room = Math.max(1, width.getAsInt() - FRAME_PIXELS);
        if (room >= label.length() * CHARACTER_WIDTH * LABEL_PIXELS) {
            return;
        }
        int longestWord = Arrays.stream(label.strip().split("\\s+")).mapToInt(String::length).max().orElse(label.length());
        double wrapped = Math.min(LABEL_PIXELS, room / (longestWord * CHARACTER_WIDTH));
        boolean twoLines = wrapped >= SMALLEST_LABEL_PIXELS
                && (height.isEmpty() || height.getAsInt() - FRAME_PIXELS >= 2 * wrapped * LINE_HEIGHT);
        double size = twoLines ? wrapped : Math.max(SMALLEST_LABEL_PIXELS, room / (label.length() * CHARACTER_WIDTH));
        if (size < LABEL_PIXELS) {
            // Rounded down, so that the estimate is never the reason a label does not fit.
            append(style, "font-size", Optional.of(String.format(Locale.ROOT, "%.1fpx", Math.floor(size * 10) / 10)));
        }
        if (twoLines) {
            append(style, "white-space", Optional.of("normal"));
        }
    }

    /** The length in pixels when it is one ({@code 60px}); empty for a percentage or no size. */
    private static OptionalInt pixels(Optional<String> length) {
        return length.filter(value -> value.endsWith("px"))
                .map(value -> OptionalInt.of(Integer.parseInt(value.substring(0, value.length() - 2))))
                .orElse(OptionalInt.empty());
    }

    /**
     * Whether an element declares its own size, by a {@code width} or {@code height} attribute or in
     * its inline style.
     *
     * @param element an {@code <img>} or {@code <svg>}
     * @return {@code true} if either dimension is set
     */
    public static boolean declaresSize(Element element) {
        Objects.requireNonNull(element, "element");
        String style = element.attr("style");
        return element.hasAttr("width") || element.hasAttr("height")
                || InlineStyle.setsWidth(style) || InlineStyle.setsHeight(style);
    }

    private static void append(StringBuilder style, String property, Optional<String> value) {
        value.ifPresent(length -> {
            if (!style.isEmpty()) {
                style.append("; ");
            }
            style.append(property).append(": ").append(length);
        });
    }

    /** The inline style's value if it has one that is usable, else the attribute's. */
    private static Optional<String> sizeOf(Element element, String property) {
        Optional<String> fromStyle = InlineStyle.valueOf(element.attr("style"), property).flatMap(ImageBox::length);
        return fromStyle.isPresent() ? fromStyle : length(element.attr(property));
    }

    /** A usable length as CSS: pixels (limited) or a percentage; empty for anything else. */
    // FindSecBugs IMPROPER_UNICODE: case-fold of a CSS length unit (ASCII letters); selects a conversion, not a security decision
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "case-fold of a CSS length unit; selects a conversion, not a security decision")
    private static Optional<String> length(String value) {
        Matcher matcher = LENGTH.matcher(value.strip().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return Optional.empty();
        }
        double number = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2) == null ? "px" : matcher.group(2);
        if (unit.equals("%")) {
            return number > 0 ? Optional.of(Math.round(Math.min(number, 100)) + "%") : Optional.empty();
        }
        double pixels = switch (unit) {
            case "pt" -> number * 4 / 3;
            case "em", "rem" -> number * 16;
            case "in" -> number * 96;
            case "cm" -> number * 96 / 2.54;
            case "mm" -> number * 96 / 25.4;
            default -> number;
        };
        long rounded = Math.round(Math.min(pixels, MAX_PIXELS));
        return rounded > 0 ? Optional.of(rounded + "px") : Optional.empty();
    }
}
