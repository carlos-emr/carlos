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
package io.github.carlos_emr.carlos.integration.patientportal;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * One page of the pending contact-review queue.
 *
 * <p>{@code nextOffset} is {@code null} on the last page. Staff-facing copy should use {@code total}
 * rather than {@code items.size()}, because a clinic with more pending reviews than one page would
 * otherwise appear to have only a handful of items outstanding.
 *
 * @param items reviews on this page
 * @param limit page size the portal applied
 * @param offset offset this page started at
 * @param total pending reviews across the whole queue
 * @param nextOffset offset of the next page, or {@code null} when this is the last
 * @since 2026-08-19
 */
public record PatientPortalContactReviewPageDto(
        List<PatientPortalContactReviewDto> items,
        int limit,
        int offset,
        int total,
        Integer nextOffset) {

    private static final String MISSING_ITEMS = "portal review page is missing its items array";

    static PatientPortalContactReviewPageDto fromJson(JsonNode node) {
        JsonNode itemNodes = node.get("items");
        if (itemNodes == null || !itemNodes.isArray()) {
            throw new PortalContractException(MISSING_ITEMS);
        }
        List<PatientPortalContactReviewDto> items = new ArrayList<>();
        for (JsonNode item : itemNodes) {
            items.add(PatientPortalContactReviewDto.fromJson(item));
        }
        return new PatientPortalContactReviewPageDto(
                List.copyOf(items),
                PortalJson.requiredInt(node, "limit"),
                PortalJson.requiredInt(node, "offset"),
                PortalJson.requiredInt(node, "total"),
                PortalJson.optionalInt(node, "next_offset"));
    }
}
