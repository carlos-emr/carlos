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

    /**
     * Applies the patient lock to a page's optional, caller-supplied patient parameter before the
     * page reads that patient's lab values. Absent values, the legacy "null"/"undefined" sentinels
     * and 0 (the unassigned-lab bucket) name no patient and stay behind the caller's global gate.
     *
     * @param demographic raw request value
     * @return false when a supplied value is not a patient identity, so the caller answers 400
     * @throws SecurityException when the named patient's record is locked for the caller
     */
    public static boolean requireRequestedPatient(SecurityInfoManager security, LoggedInInfo info, String demographic) {
        if (demographic == null || demographic.isEmpty() || "null".equals(demographic)
                || "undefined".equals(demographic) || "0".equals(demographic)) return true;
        Integer demographicNo = positiveIdentity(demographic);
        if (demographicNo == null) return false;
        requirePatient(security, info, demographicNo);
        return true;
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

    /**
     * Authorizes the other versions a display page reads beside a selected report that the caller
     * has already passed through {@link #requireLab}: version links, acknowledgement history and
     * comments. {@code versions} must be the same server-resolved chain the page itself uses, never
     * a request value. Empty entries (a legacy empty chain) name no report and are skipped.
     *
     * @return false when a resolved version is not a lab identity, so the caller answers 400
     * @throws SecurityException when any version belongs to a locked patient
     */
    public static boolean requireOtherVersions(SecurityInfoManager security, PatientLabRoutingDao routings,
            LoggedInInfo info, int selectedLabNo, String versions, String labType) {
        if (versions == null) return true;
        Set<Integer> checked = new LinkedHashSet<>();
        checked.add(selectedLabNo);
        for (String version : versions.split(",", -1)) {
            if (version.isEmpty()) continue;
            Integer versionNo = positiveIdentity(version);
            if (versionNo == null) return false;
            if (checked.add(versionNo)) requireLab(security, routings, info, versionNo, labType);
        }
        return true;
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
