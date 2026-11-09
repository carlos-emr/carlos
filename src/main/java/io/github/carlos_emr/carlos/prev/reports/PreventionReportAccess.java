/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prev.reports;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.List;

/** Decide permitted report membership before calculating any clinical rows or totals. */
final class PreventionReportAccess {
    private PreventionReportAccess() { }
    static List<Integer> readableCandidates(LoggedInInfo loggedInInfo, SecurityInfoManager permissions,
            List<Integer> demographicList) {
        List<Integer> permitted = new ArrayList<>();
        // Decide membership before processing any numerator, total or clinical record.
        for (int offset = 0; offset < demographicList.size(); offset += 500) {
            var batch = demographicList.subList(offset, Math.min(demographicList.size(), offset + 500));
            for (Integer patient : batch) {
                if (patient == null || patient <= 0) throw new IllegalStateException("Report candidate has no patient identity");
            }
            var readable = permissions.getReadableDemographicIds(loggedInInfo, batch);
            if (readable == null) throw new IllegalStateException("Report patient policy returned no result");
            for (Integer patient : batch) {
                if (readable.contains(patient)
                        && permissions.hasPrivilegeReadOnly(loggedInInfo, "_prevention", "r", patient.toString())
                        && permissions.isAllowedAccessToPatientRecord(loggedInInfo, patient)) permitted.add(patient);
            }
        }
        return permitted;
    }

}
