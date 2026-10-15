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

import java.util.List;

/**
 * JSON response of the Allergies block: its {@link BlockStatus} and the rows.
 *
 * <p>Null strings are normalised to {@code ""} so the client never has to
 * distinguish absent from empty. {@code items} is immutable and empty unless
 * the status is {@link BlockStatus#OK}. {@code severityCode} is the raw stored
 * code; the client maps it to a label.</p>
 *
 * @param status block outcome
 * @param items  allergy rows, in the order the manager returned them
 * @since 2026-10-08
 */
public record AllergyBlockDto(BlockStatus status, List<Item> items) {

    public AllergyBlockDto {
        items = status == BlockStatus.OK && items != null ? List.copyOf(items) : List.of();
    }

    /**
     * One allergy row.
     *
     * @param description allergen description
     * @param severityCode raw {@code severityOfReaction} code
     * @param reaction free-text reaction
     * @param startDate start date formatted per its partial-date precision
     */
    public record Item(String description, String severityCode, String reaction, String startDate) {

        public Item {
            description = description == null ? "" : description;
            severityCode = severityCode == null ? "" : severityCode;
            reaction = reaction == null ? "" : reaction;
            startDate = startDate == null ? "" : startDate;
        }
    }
}
