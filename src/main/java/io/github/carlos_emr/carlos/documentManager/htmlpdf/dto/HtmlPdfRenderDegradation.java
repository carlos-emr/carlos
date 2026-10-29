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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.dto;

import org.apache.logging.log4j.Level;

/**
 * One reportable step a render took down from printing the document as authored.
 *
 * <p>A degradation is data, not an error: the render still produced a PDF. The render service
 * records each one on the {@link HtmlPdfRenderResult} and logs them once per render, at the level
 * of the most serious one, so facades do not log them again. Each constant carries the
 * {@linkplain #logLevel() level} an operator should see it at:</p>
 *
 * <ul>
 *   <li>{@code INFO}: the content was kept; nothing to do;</li>
 *   <li>{@code WARN}: content is visibly missing; check the source document or the server's
 *       load;</li>
 *   <li>{@code ERROR}: a template or engine defect to fix.</li>
 * </ul>
 *
 * <p>The constant names are the greppable tokens in that log line, so renaming one is a change
 * to what operators search for.</p>
 *
 * @since 2026-10-29
 */
public enum HtmlPdfRenderDegradation {

    /**
     * An image was decoded at a lower resolution than its source, to fit the per-image pixel cap
     * or the render's share of image memory. Its printed size is unchanged.
     */
    IMAGE_DOWNSCALED(Level.INFO),

    /**
     * An image was decoded and re-encoded as JPEG or PNG because it could not be embedded as it
     * was, for example a format the PDF library cannot draw, damaged data or an EXIF rotation.
     */
    IMAGE_CONVERTED(Level.INFO),

    /**
     * An SVG pointed at resources outside itself (elements, attributes, {@code url()} values,
     * {@code @import} rules or processing instructions). Those references were removed and the
     * rest of the SVG was rendered.
     */
    SVG_REFERENCES_REMOVED(Level.INFO),

    /**
     * The second layout pass failed, so the first pass (complete content on a widened page) was
     * scaled onto the Letter page and its margins came out smaller than the intended 10 mm.
     */
    MARGINS_REDUCED(Level.INFO),

    /**
     * Right-to-left text long enough to wrap was shaped and reordered on a best-effort basis; its
     * lines may print in reverse order, so a document note asks the reader to check it against
     * the original.
     */
    RTL_BEST_EFFORT(Level.INFO),

    /**
     * An image was missing, remote, unreadable, of an unknown format or too large to decode, and
     * printed as a box. Remote sources are never fetched.
     */
    IMAGE_UNAVAILABLE(Level.WARN),

    /**
     * No render slot was free within the wait limit, so raster images printed as boxes and a
     * document note says to print again.
     */
    IMAGES_OMITTED_FOR_MEMORY(Level.WARN),

    /**
     * A bundled fallback font (CJK or symbols) could not be loaded, so characters that needed it
     * printed as U+FFFD.
     */
    FALLBACK_FONT_UNAVAILABLE(Level.WARN),

    /**
     * The caller could not resolve the webapp root, so the render used a private empty base
     * directory: stylesheets and images from the webapp are unavailable and print as boxes.
     */
    WEBAPP_ROOT_UNAVAILABLE(Level.WARN),

    /**
     * Normal layout failed and the document was printed in safe mode, with the author's styles
     * removed and inline SVG as boxes, and a document note saying so.
     */
    SAFE_MODE(Level.ERROR),

    /**
     * Safe mode failed too, or the HTML was over the size limit, so only the document's text was
     * printed, with a document note saying so.
     */
    TEXT_ONLY(Level.ERROR);

    private final Level logLevel;

    HtmlPdfRenderDegradation(Level logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * {@return the Log4j level a render carrying this degradation is logged at}
     */
    public Level logLevel() {
        return logLevel;
    }
}
