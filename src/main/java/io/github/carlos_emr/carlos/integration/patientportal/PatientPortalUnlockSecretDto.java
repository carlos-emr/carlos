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
 * A generated passphrase for an encrypted message CARLOS is about to send.
 *
 * <p>The record is created in {@code pending} and is invisible to the patient and to the FHIR
 * surface until it is published. The publish/revoke decision is the whole point of the type:
 *
 * <ol>
 *   <li>Create the passphrase here.
 *   <li>Encrypt and send the message with it.
 *   <li>Publish <b>only</b> once the send is confirmed; revoke if it failed.
 * </ol>
 *
 * <p>Publishing before the send confirms shows the patient a passphrase for a message that may never
 * arrive; skipping the publish leaves them holding a message they cannot open. Neither failure
 * raises an error anywhere, which is why the ordering belongs in one reviewed workflow.
 *
 * <p>{@code created} is {@code false} when the portal matched an existing record by {@code
 * sourceReference}, which makes creation idempotent so a CARLOS retry cannot mint a second
 * passphrase for one message. Use a stable, unique reference per outbound message.
 *
 * <p>Creation answers with {@code pending}. The later transitions are reported by {@link
 * PatientPortalUnlockSecretStatusDto}, which is a different and narrower payload. Recreating a
 * revoked {@code sourceReference} answers {@code 409}, which means a new reference is needed rather
 * than a retry.
 *
 * @param id portal unlock-secret id
 * @param created {@code false} when an existing record was returned instead
 * @param secret the passphrase itself; treat as a credential
 * @param sourceReference the CARLOS message this passphrase belongs to
 * @param status portal lifecycle status, {@code pending} until published
 * @since 2026-08-19
 */
public record PatientPortalUnlockSecretDto(
        long id, boolean created, String secret, String sourceReference, String status) {

    private static final String DESCRIPTION =
            "PatientPortalUnlockSecretDto[id=%d, created=%s, source=%s, status=%s, secret=REDACTED]";

    static PatientPortalUnlockSecretDto fromJson(JsonNode node) {
        return new PatientPortalUnlockSecretDto(
                PortalJson.requiredLong(node, "id"),
                PortalJson.requiredBool(node, "created"),
                PortalJson.text(node, "secret"),
                PortalJson.text(node, "source_reference"),
                PortalJson.text(node, "status"));
    }

    /** Renders the record without the passphrase, which unlocks patient correspondence. */
    @Override
    public String toString() {
        return String.format(Locale.ROOT, DESCRIPTION, id, created, sourceReference, status);
    }
}
