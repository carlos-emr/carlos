/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import java.util.Set;

/** Server-owned destinations. An agent can suggest a section, never grant access to it. */
public final class ChartUpdateSections {
    private ChartUpdateSections() { }
    public static final List<String> CODES = List.of("MedHistory", "Concerns", "SocHistory", "FamHistory", "RiskFactors", "OMeds", "Reminders");
    public static final Set<String> NATIVE = Set.of("Medications", "Allergies", "Preventions", "Demographics");

    public static boolean accessible(SecurityInfoManager security, LoggedInInfo user, int patient, String code, String right) {
        if (!CODES.contains(code) || !security.hasPrivilege(user, "_eChart", right, patient)
                || !sectionPrivilege(security, user, patient, code, right)) return false;
        String display = switch (code) {
            case "MedHistory" -> "medicalHistory";
            case "FamHistory" -> "familyHistory";
            case "RiskFactors" -> "riskFactors";
            case "OMeds" -> "otherMeds";
            default -> "";
        };
        return display.isEmpty() || security.hasPrivilege(user, "_newCasemgmt." + display, "x", patient);
    }
    private static boolean sectionPrivilege(SecurityInfoManager security, LoggedInInfo user, int patient, String code, String right) {
        // Native CPP sections are unrestricted when no matching section override exists.
        // Keep that default while honoring patient overrides and active-role grants.
        var objects = security.getSecurityObjects(user);
        if (objects.stream().anyMatch(object -> ("_" + code + "$" + patient).equals(object.getObjectname_code()))) {
            return security.hasPrivilege(user, "_" + code, right, patient);
        }
        var overrides = objects.stream().filter(object -> ("_" + code).equals(object.getObjectname_code())).toList();
        return overrides.isEmpty() || security.hasPrivilege(user, "_" + code, right, patient);
    }
}
