/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Checks.*;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import java.time.Instant;
import java.util.UUID;

/** One exact challenge and its terminal evidence; not local acceptance by itself. */
record PortalMyCarlosDeviceApprovalChallenge(Bytes challengeId, UUID requestId, Long bindingId,
        String clinicId, int demographicNo, String recipientId, String publicBase, Bytes configHash,
        String candidateKid, Bytes candidateX, Bytes candidateEd, String intent, Bytes intentHash,
        String signedChallenge, Bytes challengeHash, String kind, Long predecessorId, String predecessorKid,
        Long predecessorVersion, Instant issuedAt, Instant expiresAt, Instant consumedAt, UUID approvalJti,
        String consumedApproval, Bytes consumedApprovalHash, Instant cancelledAt, String cancellationReason) {
    PortalMyCarlosDeviceApprovalChallenge {
        bytes(challengeId, 32);
        uuid(requestId);
        nullablePositive(bindingId);
        positive(demographicNo);
        scope(clinicId, recipientId, publicBase);
        bytes(configHash, 32);
        keys(candidateKid, candidateX, candidateEd);
        compact(intent);
        compact(signedChallenge);
        digest(intent, intentHash);
        digest(signedChallenge, challengeHash);
        approvalKind(kind);
        second(issuedAt);
        second(expiresAt);
        require(expiresAt.isAfter(issuedAt) && java.time.Duration.between(issuedAt, expiresAt).getSeconds() <= 600);
        if (!kind.equals("accepted_device")) require(predecessorId == null && predecessorKid == null && predecessorVersion == null
                && approvalJti == null && consumedApproval == null && consumedApprovalHash == null);
        else {
            nullablePositive(predecessorId);
            require(predecessorId != null && bindingId != null);
            keyId(predecessorKid);
            require(predecessorVersion != null);
            nonnegative(predecessorVersion);
            if (consumedAt == null) require(approvalJti == null && consumedApproval == null && consumedApprovalHash == null);
            else { uuid(approvalJti);
                compact(consumedApproval);
                digest(consumedApproval, consumedApprovalHash);
            }
        }
        require(consumedAt == null || (bindingId != null && cancelledAt == null));
        require((cancelledAt == null) == (cancellationReason == null));
        if (consumedAt != null) time(consumedAt);
        if (cancelledAt != null) { time(cancelledAt);
            text(cancellationReason, 32);
        }
    }
    boolean pending() { return consumedAt == null && cancelledAt == null;
    }
    PortalMyCarlosDeviceApprovalChallenge consumed(long binding, String approval, UUID jti, Instant now) {
        require(pending() && (bindingId == null || bindingId == binding));
        return new PortalMyCarlosDeviceApprovalChallenge(challengeId, requestId, binding, clinicId, demographicNo,
                recipientId, publicBase, configHash, candidateKid, candidateX, candidateEd, intent, intentHash,
                signedChallenge, challengeHash, kind, predecessorId, predecessorKid, predecessorVersion,
                issuedAt, expiresAt, now, jti, approval, approval == null ? null : hash(approval), null, null);
    }
    PortalMyCarlosDeviceApprovalChallenge cancelled(Instant now, String reason) {
        require(pending());
        return new PortalMyCarlosDeviceApprovalChallenge(challengeId, requestId, bindingId, clinicId, demographicNo,
                recipientId, publicBase, configHash, candidateKid, candidateX, candidateEd, intent, intentHash,
                signedChallenge, challengeHash, kind, predecessorId, predecessorKid, predecessorVersion,
                issuedAt, expiresAt, null, null, null, null, now, reason);
    }
    @Override public String toString() { return "DeviceApprovalChallenge[REDACTED]";
    }
}
