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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.model.Contact;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsContactDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Creates an external contact and its source role associations without creating another patient. */
@Service
public class CdsContactPersister {
    private final ContactDao contacts;
    private final DemographicContactDao associations;
    private final CdsImportLedgerService ledger;
    public CdsContactPersister(ContactDao contacts, DemographicContactDao associations, CdsImportLedgerService ledger) {
        this.contacts = contacts; this.associations = associations; this.ledger = ledger;
    }
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Map<String, String> write(String run, long epoch, String unitKey, int patient,
            CdsRecordDto record, CdsImportCommand command, CdsContactDto value) {
        CdsImportUnitService.requireActive(run, epoch, unitKey);
        Contact contact = candidate(value);
        contacts.persist(contact); contacts.flush();
        Map<String, String> references = new LinkedHashMap<>();
        references.put("Contact", contact.getId().toString());
        int index = 0;
        for (String role : value.roles()) {
            DemographicContact association = association(patient, contact.getId(), role, command, value);
            associations.persist(association); associations.flush();
            references.put("DemographicContact#" + index++, association.getId().toString());
        }
        String patientKey = ledger.unit(run, unitKey).getPatientKey();
        for (var diagnostic : value.diagnostics()) {
            ledger.diagnostic(run, epoch, patientKey, unitKey, "WARNING", diagnostic.code(), "NORMALIZATION",
                    record.sourceXPath() + (diagnostic.field().isEmpty() ? "" : "/" + diagnostic.field()),
                    summary(diagnostic.code()),
                    null, false);
        }
        return Map.copyOf(references);
    }
    private static String summary(String code) {
        return switch (code) {
            case "CDS_CONTACT_CONSENT_NOT_ASSERTED" -> "The source does not assert permission to contact; consent remains unknown";
            case "CDS_CONTACT_MULTIPLE_PURPOSES_SOURCE_VIEW" -> "Multiple contact purposes remain available in imported Contact details; the native role explicitly refers to those details";
            case "CDS_CONTACT_AMBIGUOUS_PHONE_SOURCE_VIEW" -> "Multiple distinct telephone numbers share a native slot; all remain in imported Contact details and that native slot is unfilled";
            case "CDS_CONTACT_PHONE_SOURCE_VIEW" -> "The telephone components cannot be projected safely; the complete telephone remains in imported Contact details";
            case "CDS_PHONE_COMPONENTS_COMBINED" -> "Consistent telephone components were combined into a complete telephone number; original components remain available";
            default -> throw new IllegalArgumentException("Unsupported contact diagnostic");
        };
    }
    Contact candidate(CdsContactDto value) {
        Contact contact = new Contact();
        contact.setFirstName(value.firstName()); contact.setLastName(value.lastName());
        contact.setEmail(value.email()); contact.setNote(value.note());
        if (value.phones().containsKey("R")) contact.setResidencePhone(phoneWithExtension(value.phones().get("R")));
        if (value.phones().containsKey("C")) contact.setCellPhone(phoneWithExtension(value.phones().get("C")));
        if (value.phones().containsKey("W")) {
            contact.setWorkPhone(value.phones().get("W").number());
            contact.setWorkPhoneExtension(value.phones().get("W").extension());
        }
        return contact;
    }
    DemographicContact association(int patient, int contactId, String role, CdsImportCommand command, CdsContactDto value) {
        if (patient < 1 || contactId < 1) throw new IllegalArgumentException("Invalid contact association");
        DemographicContact row = new DemographicContact();
        row.setDemographicNo(patient); row.setContactId(Integer.toString(contactId));
        row.setType(DemographicContact.TYPE_CONTACT); row.setCategory(DemographicContact.CATEGORY_PERSONAL);
        row.setRole(role); row.setNote(value.note());
        row.setEc(Boolean.TRUE.equals(value.emergencyContact()) ? "true" : null);
        row.setSdm(Boolean.TRUE.equals(value.substituteDecisionMaker()) ? "true" : null);
        row.setConsentToContact((Boolean) null); // No source Contact consent assertion exists in the CDS schema.
        row.setActive(true); row.setMrp(false);
        row.setFacilityId(command.facilityId() == null ? 0 : command.facilityId());
        row.setCreator(command.providerNo()); row.setCreated(new Date());
        return row;
    }
    private static String phoneWithExtension(CdsContactDto.Phone phone) {
        return phone.number() + (phone.extension() == null || phone.extension().isEmpty() ? "" : " ext " + phone.extension());
    }
}
