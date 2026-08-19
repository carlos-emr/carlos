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

/**
 * What the portal reports back after an unlock or an access change.
 *
 * <p>This is deliberately not a {@link PatientPortalAccountDto}. The unlock and access endpoints
 * answer with a narrower payload than the status endpoint, and an earlier revision squeezed both
 * into one type — which meant an absent {@code demographic_no} was read as {@code 0} and an absent
 * {@code force_password_reset} as {@code false}. Both are PHI-correlating or safety-relevant fields
 * where a fabricated value reads as reassuring: "this is patient 0" and "the patient can just sign
 * in". A separate type means the fields the endpoint does not report simply do not exist here.
 *
 * <p>{@code forcePasswordReset} is {@code true} after an unlock. The patient must complete the reset
 * flow before signing in, so staff-facing copy must not say the account is simply usable again.
 *
 * @param id portal account id
 * @param status portal account status where the endpoint reports one, otherwise {@code null}
 * @param forcePasswordReset whether the patient must reset before their next sign-in
 * @param lockedAt when lockout took effect, or {@code null} when no lockout is in force
 * @since 2026-08-19
 */
public record PatientPortalAccountAcknowledgementDto(
        long id, String status, boolean forcePasswordReset, Instant lockedAt) {

    static PatientPortalAccountAcknowledgementDto fromJson(JsonNode node) {
        return new PatientPortalAccountAcknowledgementDto(
                PortalJson.requiredLong(node, "id"),
                PortalJson.text(node, "status"),
                PortalJson.requiredBool(node, "force_password_reset"),
                PortalJson.timestamp(node, "locked_at"));
    }

    /**
     * @return {@code true} when lockout is currently in force
     */
    public boolean locked() {
        return lockedAt != null;
    }
}
