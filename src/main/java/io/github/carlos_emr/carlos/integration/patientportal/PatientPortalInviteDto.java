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
import java.time.OffsetDateTime;

/**
 * One portal invite as the portal reports it.
 *
 * <p><b>{@code issuedCount}, {@code lastIssuedAt}, and {@code lastIssuedBy} describe token issuance,
 * not delivery.</b> The portal never sends invites; CARLOS does. Presenting these to staff as
 * evidence that a patient received anything would be wrong, and is the single most likely
 * misreading of this type.
 *
 * @param id portal invite id
 * @param clinicId clinic the invite belongs to
 * @param demographicNo CARLOS demographic number; PHI-correlating, sanitize before logging
 * @param status portal invite lifecycle status
 * @param createdById stable provider id that created the invite, if recorded
 * @param createdBy display name of the creating provider
 * @param issuedCount how many times a token has been issued, not delivered
 * @param lastIssuedAt when a token was last issued, not delivered
 * @param lastIssuedBy who last issued a token
 * @param expiresAt server-side expiry, seven days from issuance
 * @param acceptedAccountId portal account id once the patient activates, otherwise {@code null}
 * @param supersedesInviteId the invite this one replaced, otherwise {@code null}
 * @since 2026-08-19
 */
public record PatientPortalInviteDto(
        long id,
        String clinicId,
        int demographicNo,
        String status,
        String createdById,
        String createdBy,
        int issuedCount,
        Instant lastIssuedAt,
        String lastIssuedBy,
        Instant expiresAt,
        Long acceptedAccountId,
        Long supersedesInviteId) {

    static PatientPortalInviteDto fromJson(JsonNode node) {
        return new PatientPortalInviteDto(
                node.path("id").asLong(),
                text(node, "clinic_id"),
                node.path("demographic_no").asInt(),
                text(node, "status"),
                text(node, "created_by_id"),
                text(node, "created_by"),
                node.path("issued_count").asInt(),
                timestamp(node, "last_issued_at"),
                text(node, "last_issued_by"),
                timestamp(node, "expires_at"),
                optionalLong(node, "accepted_account_id"),
                optionalLong(node, "supersedes_invite_id"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Long optionalLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    /**
     * Parses a portal timestamp.
     *
     * <p>The portal emits ISO-8601 with an explicit offset — {@code Z} in practice, though pydantic
     * renders a configured offset as {@code +00:00}. {@link OffsetDateTime} accepts both forms;
     * {@link Instant#parse} would reject the second.
     */
    private static Instant timestamp(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : OffsetDateTime.parse(value).toInstant();
    }
}
