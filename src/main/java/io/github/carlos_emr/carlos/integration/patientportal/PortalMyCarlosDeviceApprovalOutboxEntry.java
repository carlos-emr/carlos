/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Checks.*;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import java.time.Instant;

/** Exact immutable outbound command; enqueue here, transport/lease workers are a separate slice. */
record PortalMyCarlosDeviceApprovalOutboxEntry(long id, Bytes challengeId, String messageKind,
        String clinicId, String targetUri, Bytes configHash, Bytes body, Bytes bodyHash, Instant createdAt,
        Instant nextAttemptAt, int failures, Bytes leaseToken, Instant leaseExpiresAt, Instant publishedAt, long version) {
    PortalMyCarlosDeviceApprovalOutboxEntry {
        nonnegative(id);
        bytes(challengeId, 32);
        require(java.util.Set.of("challenge", "decision", "removal").contains(messageKind));
        text(clinicId, 256);
        text(targetUri, 4096);
        bytes(configHash, 32);
        require(body != null && body.size() > 0 && body.size() <= 32768);
        bytes(bodyHash, 32);
        require(hashBytes(body.copy()).equals(bodyHash));
        time(createdAt);
        time(nextAttemptAt);
        nonnegative(failures);
        nonnegative(version);
        require((leaseToken == null) == (leaseExpiresAt == null));
        if (leaseToken != null) { bytes(leaseToken, 32);
            time(leaseExpiresAt);
        }
        if (publishedAt != null) { time(publishedAt);
            require(leaseToken == null);
        }
    }
    @Override public String toString() { return "DeviceApprovalOutbox[REDACTED]";
    }
}
