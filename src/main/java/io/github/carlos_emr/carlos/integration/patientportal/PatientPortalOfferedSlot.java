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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * One open time offered with a booking prompt (carlos-portal#11, CARLOS #3850).
 *
 * <p>The portal accepts only these fields: no provider, reason, or free text. {@code slotId} must be
 * opaque: a random identifier that encodes nothing readable, because the portal stores it. The
 * limits mirror the portal's own validation so a bad offer is a CARLOS error, not a {@code 422}.
 */
public record PatientPortalOfferedSlot(
        String slotId, OffsetDateTime startsAt, int durationMinutes, String visitMode, String locationCode) {
    /** The portal shows at most this many times per prompt, including replacements. */
    public static final int MAX_PER_PROMPT = 8;
    static final Set<String> VISIT_MODES = Set.of("in_person", "phone", "video");

    public PatientPortalOfferedSlot {
        if (slotId == null || !slotId.matches("[A-Za-z0-9._:-]{1,64}")) {
            throw new PortalRequestPreparationException("offered slot id is invalid");
        }
        if (startsAt == null) {
            throw new PortalRequestPreparationException("offered slot start is missing");
        }
        if (durationMinutes < 5 || durationMinutes > 480) {
            throw new PortalRequestPreparationException("offered slot duration is out of range");
        }
        if (visitMode == null || !VISIT_MODES.contains(visitMode)) {
            throw new PortalRequestPreparationException("offered slot visit mode is invalid");
        }
        if (locationCode != null && !locationCode.matches("[a-z0-9_-]{1,32}")) {
            throw new PortalRequestPreparationException("offered slot location code is invalid");
        }
    }

    /** The portal requires an explicit UTC offset on {@code starts_at}. */
    ObjectNode toJson(ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        node.put("slot_id", slotId);
        node.put("starts_at", startsAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        node.put("duration_minutes", durationMinutes);
        node.put("visit_mode", visitMode);
        if (locationCode != null) {
            node.put("location_code", locationCode);
        }
        return node;
    }

    /** A time joined to a patient's prompt is scheduling detail; keep it out of incidental logs. */
    @Override
    public String toString() {
        return "PatientPortalOfferedSlot[details=REDACTED]";
    }
}
