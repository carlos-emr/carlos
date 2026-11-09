/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.util.PatientApiAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.ToIntFunction;

/** Patient restrictions supplement the caller's global appointment permission. */
public final class AppointmentPatientAccess {
    private AppointmentPatientAccess() { }
    public static void require(LoggedInInfo actor, SecurityInfoManager policy, int patient, String right) {
        // Zero represents an unassigned scheduling slot, not a patient record.
        if (patient == 0) return;
        PatientApiAccess.require(actor, policy, "_appointment", right, patient);
    }
    public static <T> List<T> readable(LoggedInInfo actor, SecurityInfoManager policy,
            List<T> rows, ToIntFunction<T> patientId) {
        if (actor == null || !policy.hasPrivilegeReadOnly(actor, "_appointment", "r", null)) {
            throw new jakarta.ws.rs.ForbiddenException("Appointment access denied");
        }
        if (rows == null) throw new IllegalStateException("Appointment query returned no result");
        var permitted = new ArrayList<T>();
        for (int offset=0; offset<rows.size(); offset+=500) {
            var decisions = new HashMap<Integer, Boolean>();
            for (T row: rows.subList(offset, Math.min(rows.size(), offset+500))) {
                if (row == null) throw new IllegalStateException("Appointment query returned an invalid row");
                int patient=patientId.applyAsInt(row);
                if (patient<0) throw new IllegalStateException("Appointment row has an invalid patient");
                if (patient==0 || decisions.computeIfAbsent(patient, id ->
                        policy.hasPrivilegeReadOnly(actor, "_appointment", "r", id.toString())
                        && policy.isAllowedAccessToPatientRecord(actor, id))) permitted.add(row);
            }
        }
        return permitted;
    }
}
