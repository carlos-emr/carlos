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

/**
 * The lifecycle state a passphrase moved to after a publish or a revoke.
 *
 * <p>These two endpoints answer with {@code id} and {@code status} only — no {@code created}, no
 * {@code secret}. An earlier revision mapped them onto the full create response, which meant a
 * publish reported {@code created=false} and a {@code null} passphrase as though the portal had said
 * so. Modelling the narrower reply separately makes that impossible rather than merely unlikely.
 *
 * <p>Observed statuses, verified against a live portal: {@code available} after a publish — not
 * {@code published} — and {@code revoked} after a revoke. Publishing twice is idempotent and answers
 * {@code 200} both times, so a retry after an ambiguous network failure is safe.
 *
 * @param id portal unlock-secret id
 * @param status lifecycle status after the transition
 * @since 2026-08-19
 */
public record PatientPortalUnlockSecretStatusDto(long id, String status) {

    static PatientPortalUnlockSecretStatusDto fromJson(JsonNode node) {
        return new PatientPortalUnlockSecretStatusDto(
                PortalJson.requiredLong(node, "id"), PortalJson.text(node, "status"));
    }
}
