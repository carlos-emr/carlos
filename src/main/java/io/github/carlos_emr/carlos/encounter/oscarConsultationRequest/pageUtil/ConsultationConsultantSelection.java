/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/** Resolves a submitted consultant against the active directory before any consultation write. */
record ConsultationConsultantSelection(ProfessionalSpecialist specialist, DemographicContact contact) {
    static ConsultationConsultantSelection resolve(ProfessionalSpecialistDao specialists,
            DemographicManager demographics, LoggedInInfo loggedInInfo, int patient, String submittedId,
            boolean healthCareTeam) {
        Integer id = positiveId(submittedId);
        if (id == null) return null;
        DemographicContact contact = null;
        if (healthCareTeam) {
            contact = demographics.getHealthCareMemberbyId(loggedInInfo, id);
            if (contact == null || contact.isDeleted() || !contact.isActive()
                    || contact.getDemographicNo() != patient
                    || contact.getType() != DemographicContact.TYPE_PROFESSIONALSPECIALIST) return null;
            id = positiveId(contact.getContactId());
            if (id == null) return null;
        }
        ProfessionalSpecialist specialist = specialists.find(id);
        return specialist == null || specialist.isDeleted() ? null
                : new ConsultationConsultantSelection(specialist, contact);
    }

    private static Integer positiveId(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) return null;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
