/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

/**
 * Patient-specific privilege and record-access boundary for chart and registry actions.
 *
 * @since 2026-10-24
 */
public final class PatientRecordAccess {
    private PatientRecordAccess() { }

    /**
     * Requires both the operation's privilege and access to this patient's record. Callers
     * validate and canonicalize the patient identifier before calling this method.
     */
    public static void require(SecurityInfoManager security, LoggedInInfo login, String patient,
                               String object, String right) {
        if (login == null || !security.hasPrivilege(login, object, right, patient)) {
            throw new SecurityException("missing required sec object (" + object + ")");
        }
        if (!security.isAllowedAccessToPatientRecord(login, Integer.valueOf(patient))) {
            throw new SecurityException("Patient record access denied");
        }
    }
}
