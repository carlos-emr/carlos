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
package io.github.carlos_emr.carlos.integration.patientportal.web;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteDeliveryService;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteDeliveryService.Decision;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteException.Reason;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.Locale;

/**
 * The JSON shape of an invitation delivery attempt, shared by the invite and panel actions.
 *
 * <p>{@code outcome} is a stable code for why the attempt stands where it does, and {@code revokeFailed}
 * says an unused code could not be withdrawn and will expire on its own.
 * {@code decisions} lists what staff may do now; it is empty until the attempt has been idle for
 * {@link PortalInviteDeliveryService#RECOVERY_MIN_AGE}, so a UI never offers an action the server
 * would refuse as too early.
 *
 * @since 2026-09-22
 */
final class InviteDeliveryJson {

    private InviteDeliveryJson() {
    }

    static ObjectNode write(ObjectNode node, PatientPortalInviteDelivery row, PortalInviteDeliveryService service) {
        node.put("deliveryId", row.getId());
        node.put("state", row.getState().name().toLowerCase(Locale.ROOT));
        node.put("finished", row.getState().isTerminal());
        node.put("channel", row.getChannel().name().toLowerCase(Locale.ROOT));
        putNullable(node, "inviteId", row.getPortalInviteId());
        putNullable(node, "supersededInviteId", row.getSupersededInviteId());
        node.put("requestedBy", row.getRequestedBy());
        // A code, not prose: the staff page words it in the reader's language.
        node.put("outcome", row.getOutcome() == null ? null : row.getOutcome().name().toLowerCase(Locale.ROOT));
        node.put("revokeFailed", row.isRevokeFailed());
        putDate(node, "createdAt", row.getCreatedAt());
        putDate(node, "updatedAt", row.getUpdatedAt());
        putDate(node, "expiresAt", row.getExpiresAt());
        ArrayNode decisions = node.putArray("decisions");
        if (service.isRecoverable(row)) {
            for (Decision decision : PortalInviteDeliveryService.decisionsFor(row.getState())) {
                decisions.add(decision.requestValue());
            }
        }
        return node;
    }

    /** Maps a refusal to the HTTP status a caller should see. */
    static int statusFor(Reason reason) {
        return switch (reason) {
            case MISSING_EMAIL, INVALID_EMAIL, INCOMPLETE_DATE_OF_BIRTH, MISSING_HEALTH_CARD ->
                    HttpServletResponse.SC_BAD_REQUEST;
            case DELIVERY_NOT_FOUND -> HttpServletResponse.SC_NOT_FOUND;
            case NOT_CONFIGURED, CHANNEL_UNAVAILABLE -> HttpServletResponse.SC_SERVICE_UNAVAILABLE;
            case CONSENT_BLOCKED, STALE_ATTEMPT_EXISTS, PENDING_INVITE_EXISTS, INVITE_NOT_PENDING, RECOVERY_TOO_EARLY,
                    RECOVERY_NOT_ALLOWED, PORTAL_CONNECTION_CHANGED, STATE_CHANGED -> HttpServletResponse.SC_CONFLICT;
        };
    }

    private static void putNullable(ObjectNode node, String field, Long value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static void putDate(ObjectNode node, String field, Date value) {
        node.put(field, value == null ? null : value.toInstant().toString());
    }
}
