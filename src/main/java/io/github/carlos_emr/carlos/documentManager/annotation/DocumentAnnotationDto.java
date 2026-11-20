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
package io.github.carlos_emr.carlos.documentManager.annotation;

import java.util.Collections;
import java.util.List;

/**
 * One mark a provider placed on a document page, as received from the annotation
 * viewer and as consumed by {@link AnnotatedDocumentComposer}.
 *
 * <p><strong>Coordinates are normalised.</strong> {@code x}, {@code y}, {@code w},
 * {@code h} and every point in {@code points} are fractions of the page box in the
 * range 0..1, with the origin at the <em>top left</em> of the page as displayed —
 * that is, after the page's {@code /Rotate} has been applied. Normalised coordinates
 * keep the model independent of the DPI the viewer happened to render at, so a mark
 * placed at 96 dpi composes identically when the same page is later rendered at 192.
 * The composer converts to PDF user space (origin bottom left, 72 units per inch)
 * and re-applies rotation.
 *
 * <p>Instances are immutable and always well-formed: {@link DocumentAnnotationParser}
 * is the only intended construction path and enforces every bound documented on
 * {@link DocumentAnnotationParser} before an instance exists. Code downstream of the
 * parser may therefore treat the values as already range-checked.
 *
 * <p>{@code text} can contain PHI (a provider's note to a specialist). It must never
 * be logged. See {@code docs/annotated-document-copies-design.md}.
 *
 * @since 2026-09
 */
public final class DocumentAnnotationDto {

    /** The kind of mark, which selects the drawing routine in the composer. */
    public enum Type {
        /** Translucent filled rectangle drawn in multiply blend so text stays readable. */
        HIGHLIGHT,
        /** A free-text note drawn in an embedded Unicode font. */
        TEXT,
        /** A freehand stroke: a polyline through {@link #getPoints()}. */
        INK,
        /** The saving provider's stored signature stamp, scaled into the box. */
        SIGNATURE,
        /** A date stamp; rendered exactly like {@link #TEXT} but labelled for audit clarity. */
        DATE
    }

    private final Type type;
    private final int page;
    private final double x;
    private final double y;
    private final double w;
    private final double h;
    private final List<double[]> points;
    private final String text;
    private final String color;
    private final double strokeWidth;
    private final double fontSize;

    // Sonar S107: a builder is the usual answer to a long parameter list, but it would let a
    // half-filled instance exist, and this class documents the opposite invariant -- every
    // instance is fully populated and range-checked. The constructor is package-private with
    // DocumentAnnotationParser as its only caller, so the arity is not a public API burden.
    @SuppressWarnings("java:S107") // immutable carrier; a builder would break the always-well-formed invariant
    DocumentAnnotationDto(Type type, int page, double x, double y, double w, double h,
                          List<double[]> points, String text, String color,
                          double strokeWidth, double fontSize) {
        this.type = type;
        this.page = page;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.points = points == null ? List.of() : Collections.unmodifiableList(points);
        this.text = text;
        this.color = color;
        this.strokeWidth = strokeWidth;
        this.fontSize = fontSize;
    }

    public Type getType() {
        return type;
    }

    /** 1-based page number, guaranteed by the parser to be within the document. */
    public int getPage() {
        return page;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getW() {
        return w;
    }

    public double getH() {
        return h;
    }

    /**
     * Freehand stroke vertices for {@link Type#INK}, each a {@code {x, y}} pair in the
     * same normalised space as {@link #getX()}. Empty for every other type. The returned
     * list is unmodifiable, but the arrays inside it are shared; callers must not write
     * to them.
     */
    public List<double[]> getPoints() {
        return points;
    }

    /** Note or date content for {@link Type#TEXT} and {@link Type#DATE}; PHI, never log it. */
    public String getText() {
        return text;
    }

    /** A name from the parser's colour allowlist, never a caller-supplied CSS value. */
    public String getColor() {
        return color;
    }

    /** Stroke width in PDF points for {@link Type#INK}. */
    public double getStrokeWidth() {
        return strokeWidth;
    }

    /** Font size in PDF points for {@link Type#TEXT} and {@link Type#DATE}. */
    public double getFontSize() {
        return fontSize;
    }

    /**
     * Deliberately omits {@link #getText()} so an annotation cannot leak PHI into a log
     * line or an exception message through an accidental string concatenation.
     */
    @Override
    public String toString() {
        return "DocumentAnnotationDto{type=" + type + ", page=" + page + "}";
    }
}
