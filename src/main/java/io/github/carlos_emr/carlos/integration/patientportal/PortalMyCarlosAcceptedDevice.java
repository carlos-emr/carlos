/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Checks.*;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import java.time.Instant;
import java.util.UUID;

/** Current removal state plus retained immutable accepted keys, proof and clinic receipt. */
record PortalMyCarlosAcceptedDevice(long id, long bindingId, String kid, Bytes x25519, Bytes ed25519,
        String kind, Bytes challengeId, String providerNo, Integer securityNo, String staffMethod,
        Long predecessorId, Long predecessorVersion, String predecessorApproval, String acceptance,
        Bytes acceptanceHash, Instant acceptedAt, Instant removedAt, UUID removalId, String removalReason,
        String removedBy, long version) {
    PortalMyCarlosAcceptedDevice {
        nonnegative(id);
        positive(bindingId);
        keys(kid, x25519, ed25519);
        approvalKind(kind);
        bytes(challengeId, 32);
        compact(acceptance);
        digest(acceptance, acceptanceHash);
        second(acceptedAt);
        nonnegative(version);
        if (kind.equals("staff_first")) {
            text(providerNo, 6);
            require(securityNo != null);
            positive(securityNo);
            require("in_person_device_comparison".equals(staffMethod) && predecessorId == null
                    && predecessorVersion == null && predecessorApproval == null);
        } else if (kind.equals("code")) {
            // Clinic code v1 (mycarlos#141): no predecessor. Staff fields are all absent (policy
            // "code"), or all present when staff also approved (policy "code_and_staff").
            require(predecessorId == null && predecessorVersion == null && predecessorApproval == null);
            if (providerNo == null) require(securityNo == null && staffMethod == null);
            else {
                text(providerNo, 6);
                require(securityNo != null);
                positive(securityNo);
                require("in_person_device_comparison".equals(staffMethod));
            }
        } else {
            require(providerNo == null && securityNo == null && staffMethod == null && predecessorId != null && predecessorVersion != null);
            positive(predecessorId);
            nonnegative(predecessorVersion);
            compact(predecessorApproval);
        }
        require((removedAt == null) == (removalId == null) && (removedAt == null) == (removalReason == null)
                && (removedAt == null) == (removedBy == null));
        if (removedAt != null) { require(!removedAt.isBefore(acceptedAt));
            time(removedAt);
            uuid(removalId);
            text(removalReason, 32);
            text(removedBy, 6);
            require(version > 0);
        }
    }
    boolean current() { return removedAt == null;
    }
    PortalMyCarlosAcceptedDevice removed(UUID command, String actor, Instant now) {
        require(current() && version < Long.MAX_VALUE);
        return new PortalMyCarlosAcceptedDevice(id, bindingId, kid, x25519, ed25519, kind, challengeId,
                providerNo, securityNo, staffMethod, predecessorId, predecessorVersion, predecessorApproval,
                acceptance, acceptanceHash, acceptedAt, now, command, "staff", actor, version + 1);
    }
    @Override public String toString() { return "AcceptedDevice[REDACTED]";
    }
}
