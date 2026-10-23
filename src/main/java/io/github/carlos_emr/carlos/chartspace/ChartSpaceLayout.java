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
package io.github.carlos_emr.carlos.chartspace;

import java.util.Arrays;
import java.util.Optional;

/**
 * Layout presets a clinic can choose as the ChartSpace default.
 *
 * <p>The ids are the persisted value and must match the keys of {@code PRESETS} in
 * {@code chartspace-layout.js}.</p>
 *
 * @since 2026-10-23
 */
public enum ChartSpaceLayout {
    FAMILY_PRACTICE("familyPractice"),
    EYE_CARE("eyeCare");

    /** Layout used when nothing valid is stored. */
    public static final ChartSpaceLayout DEFAULT = FAMILY_PRACTICE;

    private final String id;

    ChartSpaceLayout(String id) {
        this.id = id;
    }

    /** @return the persisted id, equal to a key of {@code PRESETS} in {@code chartspace-layout.js} */
    public String id() {
        return id;
    }

    /**
     * Allow-list lookup: the id must match exactly (no trimming, no case folding), so a stored or
     * submitted value that is not one of the known ids never becomes a layout.
     *
     * @param id the candidate id; may be {@code null}
     * @return the matching layout, or empty when the id is {@code null} or unknown
     */
    public static Optional<ChartSpaceLayout> fromId(String id) {
        return Arrays.stream(values()).filter(layout -> layout.id.equals(id)).findFirst();
    }
}
