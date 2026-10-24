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
package io.github.carlos_emr.carlos.demographic.data;

import java.util.List;

/**
 * Search state shared by the patient list and appointment patient picker.
 * The DAO fetches one additional current patient to determine whether another page exists.
 *
 * @param mode supported patient search mode
 * @param keyword patient search term, bound as data
 * @param orderBy requested column, resolved through the DAO's fixed column mapping
 * @param statuses status filter, or null for all statuses
 * @param excludeStatuses whether the listed statuses are excluded instead of included
 * @param offset zero-based position among current matching patients
 * @param limit number of patients to display, excluding the lookahead row
 * @since 2026-10-03
 */
public record DemographicListSearch(String mode, String keyword, String orderBy,
                                    List<String> statuses, boolean excludeStatuses, int offset, int limit) {
    public DemographicListSearch {
        mode = mode == null ? "search_name" : mode;
        orderBy = orderBy == null || orderBy.isEmpty() ? "last_name" : orderBy;
        statuses = statuses == null ? null : List.copyOf(statuses);
        if (offset < 0 || limit < 1 || limit > 500 || offset > Integer.MAX_VALUE - limit - 1) {
            throw new IllegalArgumentException("Invalid patient page bounds");
        }
    }
}
