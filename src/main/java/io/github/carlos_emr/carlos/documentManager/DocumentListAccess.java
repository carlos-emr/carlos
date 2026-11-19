/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.List;

/** Read-side filtering uses the same stored-owner, program and queue gate as document bytes. */
public final class DocumentListAccess {
    private DocumentListAccess() { }

    public static boolean mayReadPatient(LoggedInInfo info, SecurityInfoManager security, String patient) {
        if (info == null || patient == null || !patient.matches("[0-9]+")) return false;
        final int number;
        try { number = Integer.parseInt(patient); }
        catch (NumberFormatException invalid) { return false; }
        return number > 0 && security.hasPrivilege(info, "_edoc", "r", (String) null)
                && security.hasPrivilegeReadOnly(info, "_edoc", "r", Integer.toString(number))
                && security.isAllowedAccessToPatientRecord(info, number);
    }

    public static boolean mayReadDocument(LoggedInInfo info, SecurityInfoManager security, Integer documentNo) {
        if (info == null || documentNo == null || documentNo <= 0) return false;
        try {
            IncomingDocumentCapacityResponse.requireStoredDocumentListReadAccess(security, info, documentNo);
            return true;
        } catch (SecurityException denied) {
            return false;
        }
    }

    public static ArrayList<EDoc> readableDocuments(LoggedInInfo info, SecurityInfoManager security,
                                                  List<EDoc> candidates) {
        ArrayList<EDoc> visible = new ArrayList<>();
        if (info == null || !security.hasPrivilege(info, "_edoc", "r", (String) null) || candidates == null) {
            return visible;
        }
        for (EDoc candidate : candidates) {
            if (candidate == null || candidate.getDocId() == null || !candidate.getDocId().matches("[0-9]+")) continue;
            final Integer number;
            try { number = Integer.valueOf(candidate.getDocId()); }
            catch (NumberFormatException invalid) { continue; }
            if (mayReadDocument(info, security, number)) visible.add(candidate);
        }
        return visible;
    }
}
