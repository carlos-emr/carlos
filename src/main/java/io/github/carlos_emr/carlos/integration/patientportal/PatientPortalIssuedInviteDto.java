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

/**
 * An invite together with the one-time token the portal just minted for it.
 *
 * <p><b>The token is returned exactly once.</b> The portal stores only its hash and cannot reissue
 * it; a resend mints a replacement and invalidates this one immediately. CARLOS owns delivery from
 * here, so losing this value means the patient has no way in until someone resends.
 *
 * <p>The token must never be logged, persisted in a CARLOS audit row, or rendered anywhere but the
 * outbound message to the patient. {@link #toString()} is overridden accordingly.
 *
 * @param invite the invite record the token belongs to
 * @param inviteToken one-time activation token; treat as a credential
 * @since 2026-08-19
 */
public record PatientPortalIssuedInviteDto(
        PatientPortalInviteDto invite, PortalSecret inviteToken) {

    private static final String DESCRIPTION = "PatientPortalIssuedInviteDto[invite=%s, token=%s]";
    private static final String MISSING_TOKEN =
            "portal issued an invite without the one-time token";

    static PatientPortalIssuedInviteDto fromJson(JsonNode node) {
        PortalSecret token = PortalSecret.ofNullable(PortalJson.text(node, "invite_token"));
        if (token == null) {
            throw new PortalContractException(MISSING_TOKEN);
        }
        return new PatientPortalIssuedInviteDto(PatientPortalInviteDto.fromJson(node), token);
    }

    /** Renders the invite without the token, which is a credential. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, DESCRIPTION, invite, inviteToken);
    }
}
