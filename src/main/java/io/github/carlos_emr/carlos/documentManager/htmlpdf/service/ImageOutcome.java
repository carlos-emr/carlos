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
 * What the image step does with one {@code <img>} or inline {@code <svg>}.
 *
 * @since 2026-10-29
 */
enum ImageOutcome {

    /** Used as it is: a JPEG embedded without decoding, or a PNG, GIF, BMP or TIFF within the per-image cap. */
    AS_IS,

    /**
     * Decoded and written again: a file or format that cannot be used as it is (too large, too many
     * pixels, a format OpenPDF cannot embed, a JPEG that must be turned upright), at the planned
     * subsampling.
     */
    CONVERT,

    /** An SVG, written again with every reference to anything but the document itself removed. */
    CLEAN_SVG,

    /** Replaced by the placeholder box. */
    BOX
}
