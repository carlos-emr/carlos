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

import java.util.List;

/**
 * The plan for every image of a document: one {@link PlannedImage} per {@code <img>} and outermost
 * inline {@code <svg>}, in document order, and the memory the plan charges in all.
 *
 * <p>The plan depends only on the document and the memory share, never on load, so the same
 * document always prints the same.</p>
 *
 * @param images the decisions, in document order
 * @param totalCharge the sum of the charges, in bytes; at most the memory share the plan was made for
 * @since 2026-10-29
 */
record ImagePlan(List<PlannedImage> images, long totalCharge) {

    /** The reason of a box made because the render has no memory share for images (no render slot). */
    static final String REASON_NO_SLOT = "no render slot";

    /** The reason of a box made because the share is too small for the image even at one pixel. */
    static final String REASON_MEMORY = "not enough image memory";

    ImagePlan {
        images = List.copyOf(images);
    }

    /**
     * Whether any image was boxed for want of a render slot.
     *
     * @return {@code true} if at least one element carries {@link #REASON_NO_SLOT}
     */
    boolean hasImagesOmittedForWantOfSlot() {
        return images.stream().anyMatch(image -> REASON_NO_SLOT.equals(image.reason()));
    }
}
