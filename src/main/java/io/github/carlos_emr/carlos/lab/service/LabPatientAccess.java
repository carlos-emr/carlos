/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.lab.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/** Patient locks for lab reads, resolved from the lab's source-specific routing rows. */
public final class LabPatientAccess {
    private LabPatientAccess() { }

    public static Integer positiveIdentity(String value) {
        if (value == null || !value.matches("\\d{1,10}")) return null;
        try {
            int identity = Integer.parseInt(value);
            return identity > 0 ? identity : null;
        } catch (NumberFormatException invalid) { return null; }
    }

    public static void requirePatient(SecurityInfoManager security, LoggedInInfo info, int demographicNo) {
        if (demographicNo > 0 && !security.isAllowedAccessToPatientRecord(info, demographicNo)) {
            throw new SecurityException("access to patient record is restricted");
        }
    }

    public static boolean isAllowedLab(SecurityInfoManager security, PatientLabRoutingDao routings,
            LoggedInInfo info, int labNo, String labType) {
        // A source is part of a lab identity: HL7 and BCP can reuse the same lab number.
        List<PatientLabRouting> owners = routings.findByLabNoAndLabType(labNo, labType);
        if (owners == null) throw new SecurityException("lab ownership could not be resolved");
        for (PatientLabRouting owner : owners) {
            if (owner == null) throw new SecurityException("lab ownership could not be resolved");
            Integer demographic = owner.getDemographicNo();
            if (demographic != null && demographic > 0
                    && !security.isAllowedAccessToPatientRecord(info, demographic)) return false;
        }
        // Unmatched lab inbox access still uses the caller's existing global _lab read gate.
        return true;
    }

    public static void requireLab(SecurityInfoManager security, PatientLabRoutingDao routings,
            LoggedInInfo info, int labNo, String labType) {
        if (!isAllowedLab(security, routings, info, labNo, labType)) {
            throw new SecurityException("access to patient record is restricted");
        }
    }

    /** Preflight every selected version before a forwarded JSP parses its first report. */
    public static void requireLabs(SecurityInfoManager security, PatientLabRoutingDao routings,
            LoggedInInfo info, String[] labIds, String labType) {
        if (labIds == null || labIds.length == 0) throw new IllegalArgumentException("Missing lab identity");
        Set<Integer> checked = new LinkedHashSet<>();
        for (String labId : labIds) {
            Integer labNo = positiveIdentity(labId);
            if (labNo == null) throw new IllegalArgumentException("Invalid lab identity");
            if (checked.add(labNo)) requireLab(security, routings, info, labNo, labType);
        }
    }

    /** Filter the actual lab rows, not a caller-supplied demographic or provider filter. */
    public static ArrayList<LabResultData> visibleResults(List<LabResultData> results,
            SecurityInfoManager security, PatientLabRoutingDao routings, LoggedInInfo info) {
        ArrayList<LabResultData> visible = new ArrayList<>();
        for (LabResultData lab : results) {
            Integer labNo = positiveIdentity(lab.segmentID);
            if (labNo != null && lab.labType != null
                    && isAllowedLab(security, routings, info, labNo, lab.labType)) visible.add(lab);
        }
        return visible;
    }
}
