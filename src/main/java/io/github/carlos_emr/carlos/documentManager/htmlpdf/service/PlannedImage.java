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

import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.ImageHeader;
import org.jsoup.nodes.Element;

import java.nio.file.Path;

/**
 * The decision for one element of the document, made without writing anything.
 *
 * @param element the {@code <img>} or inline {@code <svg>}
 * @param outcome what is to be done with it
 * @param file the source file, or {@code null} for a {@code data:} source, an inline SVG or a box
 * @param data the decoded payload of a {@code data:} source that must be read again (to be converted
 *        or cleaned), or {@code null}: a payload used as it is is decoded again from the element when
 *        it is checked, so the plan does not hold a second copy of every inline image
 * @param header the declared format and size of a raster image, or {@code null}
 * @param subsampling the source subsampling a conversion reads with, 1 when the whole image is read;
 *        for an image used as it is, the subsampling to convert it with in the rare case that OpenPDF
 *        turns out not to embed it (so the fallback still fits the per-image cap)
 * @param orientation the EXIF orientation to apply (1 to 8), 1 when none
 * @param charge the memory this image is charged against the render's share, in bytes; 0 for a box,
 *        an SVG, and every repeat of a source already charged to an earlier element
 * @param reason why an element is a box: a fixed phrase, never a path or a URL; {@code null} otherwise
 * @param key identifies the source (its real path, or a hash of a {@code data:} payload) so repeats
 *        share one converted file, or {@code null} for a box and an inline SVG
 * @since 2026-10-29
 */
record PlannedImage(Element element, ImageOutcome outcome, Path file, byte[] data, ImageHeader header,
        int subsampling, int orientation, long charge, String reason, String key) {
}
