/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Objects;

/**
 * An inactive invite preparation whose token may be durably queued before it replaces anything.
 *
 * <p>The portal retains the token encrypted only while the preparation is inactive, so retrying a
 * lost prepare response with the same operation id returns the same token. Once CARLOS records the
 * email job, {@code commitInviteDelivery} activates this invite and atomically retires its
 * predecessor.
 *
 * @param issuedInvite prepared invite and its sensitive one-time token
 * @param deliveryOperationId stable idempotency identifier chosen by CARLOS
 */
public record PatientPortalPreparedInviteDto(
        PatientPortalIssuedInviteDto issuedInvite, String deliveryOperationId) {

    private static final String DESCRIPTION =
            "PatientPortalPreparedInviteDto[invite=%s, operation=%s, token=REDACTED]";

    static PatientPortalPreparedInviteDto fromJson(
            JsonNode node, String expectedOperationId, Long expectedSupersededInviteId) {
        PatientPortalIssuedInviteDto issued = PatientPortalIssuedInviteDto.fromJson(node);
        String operationId = PortalJson.requiredText(node, "delivery_operation_id");
        String deliveryReference = PortalJson.nullableText(node, "delivery_reference");
        PatientPortalInviteDto invite = issued.invite();
        if (!"prepared".equals(invite.status())
                || !Objects.equals(operationId, expectedOperationId)
                || deliveryReference != null
                || !Objects.equals(invite.supersedesInviteId(), expectedSupersededInviteId)) {
            throw new PortalContractException(
                    "portal did not confirm the requested invite preparation");
        }
        return new PatientPortalPreparedInviteDto(issued, operationId);
    }

    @Override
    public String toString() {
        return String.format(
                Locale.ROOT, DESCRIPTION, issuedInvite.invite(), deliveryOperationId);
    }
}
