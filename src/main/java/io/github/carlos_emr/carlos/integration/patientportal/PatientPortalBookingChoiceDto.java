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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A patient's pick of an offered time, waiting for CARLOS to book it (carlos-portal#11, #3850).
 *
 * <p>{@code slotId} is CARLOS's own opaque identifier, returned as sent; CARLOS alone knows which
 * provider and time it stands for.
 */
public record PatientPortalBookingChoiceDto(
        long promptId, long choiceId, int demographicNo, String slotId, Instant chosenAt) {

    static PatientPortalBookingChoiceDto fromJson(JsonNode node) {
        String slotId = PortalJson.requiredText(node, "slot_id");
        if (!slotId.matches("[A-Za-z0-9._:-]{1,64}")) {
            throw new PortalContractException("portal returned an invalid offered slot id");
        }
        return new PatientPortalBookingChoiceDto(
                PortalJson.positiveLong(node, "prompt_id"), PortalJson.positiveLong(node, "choice_id"),
                PortalJson.positiveInt(node, "demographic_no"), slotId,
                PortalJson.requiredTimestamp(node, "chosen_at"));
    }

    /** Picks are scheduling detail tied to a patient; keep them out of incidental logs. */
    @Override
    public String toString() {
        return "PatientPortalBookingChoiceDto[details=REDACTED]";
    }

    /** One poll's picks, oldest first; {@code hasMore} means poll again. */
    public record Page(List<PatientPortalBookingChoiceDto> items, boolean hasMore) {
        static Page fromJson(JsonNode node) {
            JsonNode items = node.get("items");
            if (items == null || !items.isArray()) {
                throw new PortalContractException("portal returned no booking choice items");
            }
            List<PatientPortalBookingChoiceDto> choices = new ArrayList<>();
            for (JsonNode item : items) {
                choices.add(PatientPortalBookingChoiceDto.fromJson(item));
            }
            return new Page(List.copyOf(choices), PortalJson.requiredBool(node, "has_more"));
        }
    }

    /** The portal's record of CARLOS's answer to one pick. */
    public record Result(
            long promptId, long choiceId, String result, String state, boolean recorded, int offeredSlotCount) {
        static final Set<String> RESULTS = Set.of("booked", "slot_unavailable");

        static Result fromJson(JsonNode node) {
            String result = PortalJson.requiredText(node, "result");
            if (!RESULTS.contains(result)) {
                throw new PortalContractException("portal returned an unknown booking choice result");
            }
            return new Result(PortalJson.positiveLong(node, "prompt_id"), PortalJson.positiveLong(node, "choice_id"),
                    result, PortalJson.requiredText(node, "state"), PortalJson.requiredBool(node, "recorded"),
                    PortalJson.nonnegativeInt(node, "offered_slot_count"));
        }
    }
}
