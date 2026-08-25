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
import java.util.Locale;
import java.time.Instant;

/**
 * A patient's portal account as the portal reports it.
 *
 * <p>{@code forcePasswordReset} is the field staff-facing copy most often gets wrong. After an
 * unlock it is {@code true}, and the patient must complete the password reset flow before they can
 * sign in again. "Unlocked" does not mean "can sign in with the old password", and telling a patient
 * otherwise sends them back to a login that will not work.
 *
 * @param id portal account id
 * @param clinicId clinic the account belongs to
 * @param demographicNo CARLOS demographic number; PHI-correlating, sanitize before logging
 * @param status portal account lifecycle status
 * @param locked whether lockout is currently in force
 * @param forcePasswordReset whether the patient must reset before their next sign-in
 * @param disabledAt when staff disabled the account, otherwise {@code null}
 * @param disabledReason why staff disabled the account, otherwise {@code null}
 * @since 2026-08-19
 */
public record PatientPortalAccountDto(
        long id,
        String clinicId,
        int demographicNo,
        String status,
        boolean locked,
        boolean forcePasswordReset,
        Instant disabledAt,
        String disabledReason) {

    static PatientPortalAccountDto fromJson(JsonNode node) {
        return new PatientPortalAccountDto(
                PortalJson.requiredLong(node, "id"),
                PortalJson.text(node, "clinic_id"),
                PortalJson.requiredInt(node, "demographic_no"),
                PortalJson.text(node, "status"),
                PortalJson.requiredBool(node, "locked"),
                PortalJson.requiredBool(node, "force_password_reset"),
                PortalJson.timestamp(node, "disabled_at"),
                PortalJson.text(node, "disabled_reason"));
    }

    private static final String DESCRIPTION =
            "PatientPortalAccountDto[id=%d, clinicId=%s, status=%s, locked=%s, patient=REDACTED]";

    /**
     * Renders the record without {@code demographicNo}, a PHI-correlating identifier that the
     * generated record {@code toString} would otherwise print into any log line.
     */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, DESCRIPTION, id, clinicId, status, locked);
    }
}
