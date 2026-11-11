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

/**
 * The arithmetic that puts any page width on US Letter with 10 mm margins, the way wkhtmltopdf's
 * shrink-to-fit did (spec §Output geometry).
 *
 * <p>Layout pass 1 renders on a page of Letter and 10 mm margins divided by the default zoom of 0.8
 * (765 pt wide), widened if the content is wider. If the widest page {@code W} is about that width
 * the default zoom of 0.8 is used. Otherwise pass 2 renders on a page wide enough for the content,
 * and the whole page is scaled to fit Letter:</p>
 *
 * <pre>
 * C = W - 2M / 0.8          the content width pass 1 found
 * P = C / (1 - 2M / 612)    the pass-2 page width, whose content area is C once zoomed
 * z = 612 / P               the zoom: P * z = 612, and the margin M / z * z = M
 * </pre>
 *
 * @since 2026-10-29
 */
final class PageZoomCalculator {

    /** US Letter width in points. */
    static final float LETTER_WIDTH_PT = 612f;

    /** US Letter height in points. */
    static final float LETTER_HEIGHT_PT = 792f;

    /** 10 mm in points. */
    static final float MARGIN_PT = 28.3465f;

    /** wkhtmltopdf's default zoom for a page that fits. */
    static final float DEFAULT_ZOOM = 0.8f;

    /**
     * The pass-1 width up to which the default zoom is used. Pass 1 widens a page by fractions of a
     * point for content that just fits; half a point of slack keeps such pages at the default zoom.
     */
    static final float PASS_1_MAX_WIDTH_PT = 765.5f;

    /** Below this zoom the text is very small; the service logs a WARN with the zoom. */
    static final float LOW_ZOOM_WARNING = 0.4f;

    private PageZoomCalculator() {
    }

    /**
     * The zoom for the width pass 1 found.
     *
     * @param pass1WidthPt the widest page of pass 1 in points
     * @return {@link #DEFAULT_ZOOM} for a page that fits, else the zoom of {@link #pass2Geometry(float)}
     */
    static float zoomFor(float pass1WidthPt) {
        return pass1WidthPt <= PASS_1_MAX_WIDTH_PT ? DEFAULT_ZOOM : pass2Geometry(pass1WidthPt).zoom();
    }

    /**
     * The page pass 2 is rendered on, for a pass-1 width over {@link #PASS_1_MAX_WIDTH_PT}.
     *
     * @param pass1WidthPt the widest page of pass 1 in points
     * @return the page size, margin and zoom
     */
    static PageGeometry pass2Geometry(float pass1WidthPt) {
        float content = pass1WidthPt - 2 * MARGIN_PT / DEFAULT_ZOOM;
        float width = content / (1 - 2 * MARGIN_PT / LETTER_WIDTH_PT);
        float zoom = LETTER_WIDTH_PT / width;
        return new PageGeometry(width, LETTER_HEIGHT_PT / zoom, MARGIN_PT / zoom, zoom);
    }
}
