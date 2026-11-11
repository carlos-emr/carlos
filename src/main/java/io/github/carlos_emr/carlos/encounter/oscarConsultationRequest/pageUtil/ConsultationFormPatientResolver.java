// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.NoSuchElementException;

/** Resolves and authorizes the form's patient before any clinical fields are loaded or rendered. */
public final class ConsultationFormPatientResolver {
    private final ConsultationRequestDao requests;
    private final SecurityInfoManager security;

    public ConsultationFormPatientResolver(ConsultationRequestDao requests, SecurityInfoManager security) {
        this.requests = requests;
        this.security = security;
    }

    public Integer resolve(LoggedInInfo info, String requestId, String demographicNo) {
        Integer patient = positiveId(demographicNo);
        if (requestId != null && !requestId.isBlank() && !"null".equals(requestId)) {
            var owner = requests.find(positiveId(requestId).intValue());
            if (owner == null) throw new NoSuchElementException("Consultation not found");
            patient = owner.getDemographicId();
            if (patient == null || patient <= 0) throw new IllegalArgumentException("Consultation patient is missing");
        }
        if (!security.hasPrivilege(info, "_con", SecurityInfoManager.READ, patient == null ? null : patient.toString())) {
            throw new SecurityException("missing required sec object (_con)");
        }
        // Remote segment-only views have no local patient. Do not invent one or query patient 0.
        if (patient != null && !security.isAllowedAccessToPatientRecord(info, patient)) {
            throw new SecurityException("Patient record access denied");
        }
        return patient;
    }

    private static Integer positiveId(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) return null;
        if (!value.matches("[1-9][0-9]{0,9}")) throw new IllegalArgumentException("Invalid consultation identifier");
        return Integer.valueOf(value);
    }
}
