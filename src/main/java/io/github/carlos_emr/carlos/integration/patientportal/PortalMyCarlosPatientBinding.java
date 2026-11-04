/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Checks.*;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import java.time.Instant;

/** Immutable row entity for mycarlos_patient_binding; root history is never cleared. */
record PortalMyCarlosPatientBinding(long id, String clinicId, int demographicNo, String recipientId,
        String publicBase, String internalOrigin, Bytes configHash, String rootKid, Instant createdAt, long version) {
    PortalMyCarlosPatientBinding {
        nonnegative(id);
        nonnegative(version);
        positive(demographicNo);
        scope(clinicId, recipientId, publicBase);
        text(internalOrigin, 2048);
        bytes(configHash, 32);
        keyId(rootKid);
        time(createdAt);
    }
    @Override public String toString() { return "PatientBinding[REDACTED]";
    }
}
