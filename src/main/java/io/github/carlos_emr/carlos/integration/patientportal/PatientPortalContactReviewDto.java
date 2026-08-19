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
import java.util.Locale;

/**
 * A patient contact change awaiting clinic review.
 *
 * <p>The patient has already proven ownership of the new destination; this is the clinic's
 * record-of-truth sync, not an approval of the change itself.
 *
 * <p><b>{@code revision} carries the ordering rule.</b> CARLOS must update the eChart first, then
 * confirm this exact revision. A stale revision returns {@code 409}, which means the request moved
 * underneath the reviewer and the item must be re-read and re-presented — never retried blindly.
 *
 * <p>Every contact field here is PHI. {@link #toString()} redacts them so a queue rendered into a
 * log does not become a list of patient email addresses and phone numbers.
 *
 * @param id review request id, used on the decision call
 * @param clinicId clinic the review belongs to
 * @param demographicNo CARLOS demographic number; PHI-correlating, sanitize before logging
 * @param emailBefore current email on the portal account; PHI
 * @param emailAfter requested email; PHI
 * @param phoneNumberBefore current phone, if the change touches it; PHI
 * @param phoneNumberAfter requested phone, if the change touches it; PHI
 * @param requestedAt when the patient submitted the change
 * @param revision opaque token that must be echoed back on the decision
 * @since 2026-08-19
 */
public record PatientPortalContactReviewDto(
        long id,
        String clinicId,
        int demographicNo,
        String emailBefore,
        String emailAfter,
        String phoneNumberBefore,
        String phoneNumberAfter,
        Instant requestedAt,
        String revision) {

    private static final String DESCRIPTION =
            "PatientPortalContactReviewDto[id=%d, clinicId=%s, requestedAt=%s, contact=REDACTED]";

    static PatientPortalContactReviewDto fromJson(JsonNode node) {
        return new PatientPortalContactReviewDto(
                PortalJson.requiredLong(node, "id"),
                PortalJson.text(node, "clinic_id"),
                PortalJson.requiredInt(node, "demographic_no"),
                PortalJson.text(node, "email_before"),
                PortalJson.text(node, "email_after"),
                PortalJson.text(node, "phone_number_before"),
                PortalJson.text(node, "phone_number_after"),
                PortalJson.timestamp(node, "requested_at"),
                PortalJson.text(node, "revision"));
    }

    /** Renders the review without the patient contact details, all of which are PHI. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, DESCRIPTION, id, clinicId, requestedAt);
    }
}
