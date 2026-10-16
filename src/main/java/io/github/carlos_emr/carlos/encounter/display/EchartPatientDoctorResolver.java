/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.encounter.display;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.Contact;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.demographic.util.DemographicXml;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Picks the name to show in the eChart's Family Doctor and Referring Doctor panels.
 *
 * <p>CARLOS has no single "family doctor" column, so each name comes from a short priority chain
 * over the places the master record keeps it:</p>
 * <ul>
 *   <li><b>Referring doctor</b>: the master record's "Referral Doctor" field (the {@code <rd>}
 *       element of {@code demographic.family_doctor}); otherwise an active professional contact in
 *       the "Referring Doctor" role.</li>
 *   <li><b>Family doctor</b>: an active professional contact in the "Family Doctor" role (how the
 *       Health Care Team and the CDS import record it); otherwise the legacy {@code <family_doc>}
 *       element of {@code demographic.family_doctor}.</li>
 * </ul>
 *
 * <p>Contacts are read through {@link DemographicManager} (active, non-deleted rows; the manager
 * checks {@code _demographic} read and writes the usual per-contact access log). Callers must
 * already be authorized to view the patient's chart.</p>
 *
 * @since 2026-10-16
 */
@Service
public class EchartPatientDoctorResolver {

    /** Role values the professional-contact screens and the CDS import write for a family doctor. */
    static final Set<String> FAMILY_DOCTOR_ROLES = Set.of("Family Doctor", "Family Physician");
    /** Role value the professional-contact screens write for a referring doctor. */
    static final Set<String> REFERRING_DOCTOR_ROLES = Set.of("Referring Doctor");

    private final DemographicManager demographicManager;
    private final ProviderDao providerDao;
    private final ContactDao contactDao;
    private final ProfessionalSpecialistDao professionalSpecialistDao;

    public EchartPatientDoctorResolver(DemographicManager demographicManager, ProviderDao providerDao,
                                       ContactDao contactDao, ProfessionalSpecialistDao professionalSpecialistDao) {
        this.demographicManager = demographicManager;
        this.providerDao = providerDao;
        this.contactDao = contactDao;
        this.professionalSpecialistDao = professionalSpecialistDao;
    }

    /**
     * @param loggedInInfo the viewer, already authorized for the chart
     * @param demographic  the patient
     * @return the family doctor's display name, or {@code ""} when none is recorded
     */
    public String familyDoctorName(LoggedInInfo loggedInInfo, Demographic demographic) {
        if (demographic == null) {
            return "";
        }
        String fromContacts = contactName(loggedInInfo, demographic, FAMILY_DOCTOR_ROLES);
        if (!fromContacts.isEmpty()) {
            return fromContacts;
        }
        return StringUtils.trimToEmpty(DemographicXml.familyDoc(demographic.getFamilyDoctor()));
    }

    /**
     * @param loggedInInfo the viewer, already authorized for the chart
     * @param demographic  the patient
     * @return the referring doctor's display name, or {@code ""} when none is recorded
     */
    public String referringDoctorName(LoggedInInfo loggedInInfo, Demographic demographic) {
        if (demographic == null) {
            return "";
        }
        String fromMasterRecord = StringUtils.trimToEmpty(DemographicXml.referralDoctor(demographic.getFamilyDoctor()));
        if (!fromMasterRecord.isEmpty()) {
            return fromMasterRecord;
        }
        return contactName(loggedInInfo, demographic, REFERRING_DOCTOR_ROLES);
    }

    private String contactName(LoggedInInfo loggedInInfo, Demographic demographic, Set<String> roles) {
        if (demographic.getDemographicNo() == null) {
            return "";
        }
        // Active, non-deleted contacts only, filtered in the query: legacy rows may carry a NULL
        // active flag, which DemographicContact#isActive() cannot unbox.
        List<DemographicContact> contacts = demographicManager.getDemographicContacts(loggedInInfo,
                demographic.getDemographicNo());
        if (contacts == null) {
            return "";
        }
        for (DemographicContact contact : contacts) {
            if (DemographicContact.CATEGORY_PROFESSIONAL.equals(contact.getCategory())
                    && roles.contains(StringUtils.trimToEmpty(contact.getRole()))) {
                String name = nameOf(contact);
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }
        return "";
    }

    /** The contact's display name by contact type; {@code ""} when the referenced row is gone. */
    private String nameOf(DemographicContact contact) {
        String contactId = StringUtils.trimToEmpty(contact.getContactId());
        if (contactId.isEmpty()) {
            return "";
        }
        return switch (contact.getType()) {
            case DemographicContact.TYPE_PROVIDER -> {
                Provider provider = providerDao.getProvider(contactId);
                yield provider == null ? "" : StringUtils.trimToEmpty(provider.getFormattedName());
            }
            case DemographicContact.TYPE_PROFESSIONALSPECIALIST -> {
                Integer id = toInteger(contactId);
                ProfessionalSpecialist specialist = id == null ? null : professionalSpecialistDao.find(id);
                yield specialist == null ? "" : StringUtils.trimToEmpty(specialist.getFormattedName());
            }
            case DemographicContact.TYPE_CONTACT -> {
                Integer id = toInteger(contactId);
                Contact other = id == null ? null : contactDao.find(id);
                yield other == null ? "" : StringUtils.trimToEmpty(other.getFormattedName());
            }
            default -> "";
        };
    }

    private static Integer toInteger(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
