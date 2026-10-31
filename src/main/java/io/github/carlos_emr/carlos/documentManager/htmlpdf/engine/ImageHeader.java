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

import java.util.Locale;
import java.util.Objects;

/**
 * What an image declares about itself in its header: its format and its size in pixels.
 *
 * <p>Instances come from {@link ImageDecoding#readHeader(java.nio.file.Path)} and
 * {@link ImageDecoding#readHeader(byte[])}, which read the header only and decode no pixels. The
 * values are claims made by the file, not facts about it: a malformed or hostile file can declare
 * a size far beyond what it holds, which is why callers compare {@link #pixels()} with
 * {@link ImageDecoding#MAX_SOURCE_PIXELS} before they decode anything.</p>
 *
 * @param formatName the name the matching ImageIO reader reports, such as {@code "png"},
 *        {@code "JPEG"}, {@code "gif"}, {@code "bmp"}, {@code "wbmp"} or {@code "tiff"}; its case
 *        and spelling depend on the reader (TIFF may be {@code "tif"} or {@code "tiff"}), so test
 *        for JPEG with {@link #isJpeg()} and do not compare the string case-sensitively
 * @param width the declared width in pixels, at least 1
 * @param height the declared height in pixels, at least 1
 * @since 2026-10-29
 */
public record ImageHeader(String formatName, int width, int height) {

    /**
     * Validates the header's values.
     *
     * @throws NullPointerException if {@code formatName} is {@code null}
     * @throws IllegalArgumentException if {@code width} or {@code height} is below 1
     */
    public ImageHeader {
        Objects.requireNonNull(formatName, "formatName");
        if (width < 1 || height < 1) {
            // Names the failed check only, like the module's other validation messages.
            throw new IllegalArgumentException("image width and height must be at least 1");
        }
    }

    /**
     * {@return the declared number of pixels, {@code width * height} computed as a {@code long}
     * so that two large {@code int} dimensions cannot overflow}
     */
    public long pixels() {
        return (long) width * height;
    }

    /**
     * {@return whether the format is JPEG, whichever way the reader spells it ({@code "JPEG"},
     * {@code "jpeg"} or {@code "jpg"})}
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an ImageIO reader's format name (an internal ASCII identifier such as "JPEG", never user input); not a security or authorization decision
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an ImageIO reader's format name (an internal ASCII identifier such as JPEG, never user input); not a security or authorization decision")
    public boolean isJpeg() {
        String name = formatName.toLowerCase(Locale.ROOT);
        return name.equals("jpeg") || name.equals("jpg");
    }
}
