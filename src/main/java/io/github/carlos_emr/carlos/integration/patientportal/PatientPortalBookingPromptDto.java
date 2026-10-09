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
import java.util.Set;

/** A patient-scoped prompt; notifiedAt records a notice send, and readAt records a portal open. */
public record PatientPortalBookingPromptDto(
        long id, int demographicNo, String urgency, String appointmentType, String suggestedBy,
        String state, String createdBy, Instant createdAt, Instant expiresAt, Instant notifiedAt,
        Instant readAt, Instant withdrawnAt, String withdrawnBy) {
    private static final Set<String> STATES = Set.of(
            "sent", "read", "choice_pending", "booked", "declined_all", "withdrawn", "expired");

    static PatientPortalBookingPromptDto fromJson(JsonNode node) {
        String urgency = PortalJson.requiredText(node, "urgency");
        String appointmentType = PortalJson.requiredText(node, "appointment_type");
        String state = PortalJson.requiredText(node, "state");
        if (!PatientPortalBookingPromptRequest.URGENCIES.contains(urgency)
                || !PatientPortalBookingPromptRequest.APPOINTMENT_TYPES.contains(appointmentType)
                || !STATES.contains(state)) {
            throw new PortalContractException("portal returned unknown booking vocabulary");
        }
        return new PatientPortalBookingPromptDto(
                PortalJson.positiveLong(node, "id"), PortalJson.positiveInt(node, "demographic_no"),
                urgency, appointmentType, PortalJson.nullableText(node, "suggested_by"), state,
                PortalJson.requiredText(node, "created_by"),
                PortalJson.requiredTimestamp(node, "created_at"),
                PortalJson.requiredTimestamp(node, "expires_at"),
                PortalJson.nullableTimestamp(node, "notified_at"),
                PortalJson.nullableTimestamp(node, "read_at"),
                PortalJson.nullableTimestamp(node, "withdrawn_at"),
                PortalJson.nullableText(node, "withdrawn_by"));
    }

    /** The record includes patient-correlating and provider data, so suppress generated rendering. */
    @Override
    public String toString() {
        return "PatientPortalBookingPromptDto[details=REDACTED]";
    }

    /** created=false is a confirmed retry, rather than evidence of another notification. */
    public record Creation(PatientPortalBookingPromptDto prompt, boolean created) {}
}
