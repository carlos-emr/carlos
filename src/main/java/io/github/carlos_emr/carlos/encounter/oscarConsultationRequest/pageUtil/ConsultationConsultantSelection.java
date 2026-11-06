/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.Contact;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;

/** Resolves a submitted consultant against the active directory before any consultation write. */
record ConsultationConsultantSelection(ProfessionalSpecialist specialist, DemographicContact contact, String displayName) {
    static ConsultationConsultantSelection resolve(ProfessionalSpecialistDao specialists,
            DemographicContactDao demographicContacts, ContactDao contacts, int patient, String submittedId,
            boolean healthCareTeam) {
        Integer id = positiveId(submittedId);
        if (id == null) return null;
        DemographicContact contact = null;
        if (healthCareTeam) {
            // Read the raw link first: the display helper dereferences its target before validating it.
            contact = demographicContacts.find(id);
            if (contact == null || contact.isDeleted() || !contact.isActive() || !contact.isConsentToContact()
                    || contact.getDemographicNo() != patient
                    || !DemographicContact.CATEGORY_PROFESSIONAL.equals(contact.getCategory())) return null;
            id = positiveId(contact.getContactId());
            if (id == null) return null;
            if (contact.getType() == DemographicContact.TYPE_CONTACT) {
                Contact destination = contacts.find(id);
                if (!(destination instanceof ProfessionalContact professional) || professional.isDeleted()) return null;
                // Keep the contact link; do not cascade a transient converted specialist into the directory.
                return new ConsultationConsultantSelection(null, contact, professional.getFormattedName());
            }
            if (contact.getType() != DemographicContact.TYPE_PROFESSIONALSPECIALIST) return null;
        }
        ProfessionalSpecialist specialist = specialists.find(id);
        return specialist == null || specialist.isDeleted() ? null
                : new ConsultationConsultantSelection(specialist, contact, specialist.getFormattedTitle());
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
