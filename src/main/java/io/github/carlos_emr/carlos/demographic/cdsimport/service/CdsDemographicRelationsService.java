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

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicPharmacyDao;
import io.github.carlos_emr.carlos.commn.dao.PharmacyInfoDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalContactDao;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.DemographicPharmacy;
import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;
import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsClinicalDiagnosticDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicRelationsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicRelationsDto.Person;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicRelationsDto.Pharmacy;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsHealthCardAuthority;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPhoneComponents;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Prepares fixed source relations outside the unit and writes only new patient-scoped associations. */
@Service
public class CdsDemographicRelationsService {
    private static final Map<String, Integer> SCALARS=Map.ofEntries(
            Map.entry("FamilyPhysician/FirstName", 50), Map.entry("FamilyPhysician/LastName", 50),
            Map.entry("ReferredPhysician/FirstName", 50), Map.entry("ReferredPhysician/LastName", 50),
            Map.entry("PreferredPharmacy/Name", 255), Map.entry("PreferredPharmacy/Address/Formatted", 255),
            Map.entry("PreferredPharmacy/Address/Structured/Line1", 50), Map.entry("PreferredPharmacy/Address/Structured/Line2", 50),
            Map.entry("PreferredPharmacy/Address/Structured/Line3", 50), Map.entry("PreferredPharmacy/Address/Structured/City", 60),
            Map.entry("PreferredPharmacy/Address/Structured/CountrySubdivisionCode", 255),
            Map.entry("PreferredPharmacy/Address/Structured/PostalZipCode/PostalCode", 20),
            Map.entry("PreferredPharmacy/Address/Structured/PostalZipCode/ZipCode", 20),
            Map.entry("PreferredPharmacy/PhoneNumber/phoneNumber", 25), Map.entry("PreferredPharmacy/PhoneNumber/areaCode", 30),
            Map.entry("PreferredPharmacy/PhoneNumber/exchange", 30), Map.entry("PreferredPharmacy/PhoneNumber/number", 30),
            Map.entry("PreferredPharmacy/PhoneNumber/extension", 5),
            Map.entry("PreferredPharmacy/FaxNumber/phoneNumber", 25), Map.entry("PreferredPharmacy/FaxNumber/areaCode", 30),
            Map.entry("PreferredPharmacy/FaxNumber/exchange", 30), Map.entry("PreferredPharmacy/FaxNumber/number", 30),
            Map.entry("PreferredPharmacy/FaxNumber/extension", 5), Map.entry("PreferredPharmacy/EmailAddress", 100));
    private record Projection(Map<String, String> values, Set<String> unavailable) { }
    private final CdsFieldParser fields;
    private final ProfessionalContactDao contacts;
    private final DemographicContactDao relations;
    private final PharmacyInfoDao pharmacies;
    private final DemographicPharmacyDao preferences;

    public CdsDemographicRelationsService(CdsFieldParser fields, ProfessionalContactDao contacts,
            DemographicContactDao relations, PharmacyInfoDao pharmacies, DemographicPharmacyDao preferences) {
        this.fields=fields;
        this.contacts=java.util.Objects.requireNonNull(contacts);
        this.relations=java.util.Objects.requireNonNull(relations);
        this.pharmacies=java.util.Objects.requireNonNull(pharmacies);
        this.preferences=java.util.Objects.requireNonNull(preferences);
    }

    public CdsDemographicRelationsDto prepare(CdsRecordDto record, CdsImportCommand command) throws IOException {
        if (!"Demographics".equals(record.section())) throw new IllegalArgumentException("Wrong demographic relation section");
        Map<String, CdsClinicalDiagnosticDto> notices=new LinkedHashMap<>();
        Projection projection=project(record, notices);
        Map<String, String> values=projection.values();
        Person family=person(values, "FamilyPhysician", notices), referring=person(values, "ReferredPhysician", notices);
        String scope="PreferredPharmacy";
        String name=values.get(scope+"/Name");
        Pharmacy pharmacy=null;
        if (name!=null && !name.isBlank()) {
            String formatted=values.get(scope+"/Address/Formatted");
            List<String> lines=new ArrayList<>();
            for (String line:List.of("Line1", "Line2", "Line3")) {
                String value=values.get(scope+"/Address/Structured/"+line);
                if (value!=null) lines.add(value);
            }
            String address=formatted!=null?formatted:lines.isEmpty()?null:String.join("\n", lines);
            String postal=values.get(scope+"/Address/Structured/PostalZipCode/PostalCode");
            if (postal==null) postal=values.get(scope+"/Address/Structured/PostalZipCode/ZipCode");
            String provincePath=scope+"/Address/Structured/CountrySubdivisionCode";
            String sourceProvince=values.get(provincePath);
            String province=CdsHealthCardAuthority.normalize(sourceProvince);
            if (!java.util.Objects.equals(sourceProvince, province)) {
                notice(notices, "CDS_CANADIAN_PROVINCE_NORMALIZED", provincePath);
            } else if (province!=null && !CdsHealthCardAuthority.CANADIAN.contains(province)) {
                notice(notices, "CDS_CONTEXT_PHARMACY_PROVINCE_REQUIRES_REVIEW", provincePath);
            }
            pharmacy=new Pharmacy(name, address, values.get(scope+"/Address/Structured/City"),
                    province, postal,
                    phone(values, projection.unavailable(), scope+"/PhoneNumber", notices), phone(values, projection.unavailable(), scope+"/FaxNumber", notices),
                    values.get(scope+"/EmailAddress"));
            notice(notices, "CDS_PHARMACY_CONSENT_NOT_ASSERTED", scope);
        } else if (name!=null) notice(notices, "CDS_CONTEXT_PHARMACY_SOURCE_ONLY", scope);
        return new CdsDemographicRelationsDto(family, referring, pharmacy,
                command.facilityId()==null?0:command.facilityId(), List.copyOf(notices.values()));
    }

    private Person person(Map<String, String> values, String path, Map<String, CdsClinicalDiagnosticDto> notices) {
        String first=values.get(path+"/FirstName"), last=values.get(path+"/LastName");
        if (first==null && last==null) return null;
        if (first==null || last==null || first.isBlank() && last.isBlank()) {
            notice(notices, "CDS_CONTEXT_PHYSICIAN_SOURCE_ONLY", path); return null;
        }
        notice(notices, "CDS_CONTEXT_PHYSICIAN_CONSENT_NOT_ASSERTED", path);
        return new Person(first, last);
    }

    private String phone(Map<String, String> values, Set<String> unavailable, String path, Map<String, CdsClinicalDiagnosticDto> notices) {
        if (unavailable.stream().anyMatch(field-> field.startsWith(path+"/"))) {
            notice(notices, "CDS_CONTEXT_PHONE_SOURCE_ONLY", path); return null;
        }
        String number=values.get(path+"/phoneNumber");
        String area=values.get(path+"/areaCode"), exchange=values.get(path+"/exchange");
        String local=values.get(path+"/number"), extension=values.get(path+"/extension");
        if (number==null && (area!=null || exchange!=null || local!=null)) {
            number=CdsPhoneComponents.combine(area, exchange, local);
            notice(notices, number==null?"CDS_CONTEXT_PHONE_SOURCE_ONLY":"CDS_PHONE_COMPONENTS_COMBINED", path);
        }
        if (number==null) return null;
        String combined=number+(extension==null || extension.isEmpty()?"":" ext "+extension);
        if (combined.codePointCount(0, combined.length())>20) {
            notice(notices, "CDS_CONTEXT_PHONE_SOURCE_ONLY", path); return null;
        }
        return combined;
    }

    /** One metadata traversal and at most24 bounded values, independent of source child count. */
    private Projection project(CdsRecordDto record, Map<String, CdsClinicalDiagnosticDto> notices) throws IOException {
        Map<String, String> values=new LinkedHashMap<>(); Set<String> seen=new HashSet<>(), unavailable=new HashSet<>();
        try {
            fields.forEachField(record, field-> {
                if (field.kind().equals("BINARY") || !(field.namespace().equals("cds") || field.namespace().equals("cds_dt"))) return;
                String path=CdsFieldParser.unindexed(field.path()); Integer maximum=SCALARS.get(path);
                if (maximum==null || !seen.add(path)) return;
                try { values.put(path, fields.readLogicalText(record, field, maximum)); }
                catch (CdsInputException over) {
                    if (!"CDS_TARGET_TEXT_CAPACITY_EXCEEDED".equals(over.code())) throw new UncheckedIOException(over);
                    unavailable.add(path); notice(notices, "CDS_NATIVE_SCALAR_SOURCE_ONLY", path);
                } catch (IOException failed) {throw new UncheckedIOException(failed); }
            });
        } catch (UncheckedIOException failed) {throw failed.getCause(); }
        return new Projection(values, unavailable);
    }
    private static void notice(Map<String, CdsClinicalDiagnosticDto> notices, String code, String path) {
        notices.putIfAbsent(code+"/"+path, new CdsClinicalDiagnosticDto(code, path));
    }

    @Transactional(propagation=Propagation.MANDATORY, rollbackFor=Exception.class)
    public Map<String, String> write(int patient, String auditProvider, CdsDemographicRelationsDto value) {
        if (!CdsImportUnitService.isActiveForMaintenance()) throw new CdsImportException("PARTICIPATING_UNIT_REQUIRED");
        if (patient<1 || auditProvider==null || !auditProvider.matches("[A-Za-z0-9_-]{1,16}"))
            throw new IllegalArgumentException("DEMOGRAPHIC_RELATIONS_TARGET_INVALID");
        Map<String, String> references=new LinkedHashMap<>();
        physician(patient, auditProvider, value.facilityId(), value.familyPhysician(), "Family Doctor", "family", references);
        physician(patient, auditProvider, value.facilityId(), value.referringPhysician(), "Referring Doctor", "referring", references);
        if (value.preferredPharmacy()!=null) {
            Pharmacy p=value.preferredPharmacy(); PharmacyInfo row=new PharmacyInfo();
            row.setName(p.name()); row.setAddress(p.address()); row.setCity(p.city()); row.setProvince(p.province());
            row.setPostalCode(p.postal()); row.setPhone1(p.phone()); row.setFax(p.fax()); row.setEmail(p.email());
            row.setStatus(PharmacyInfo.ACTIVE); row.setAddDate(new Date());
            pharmacies.persist(row); pharmacies.flush();
            int pharmacyId=requiredId(row.getId());
            DemographicPharmacy association=new DemographicPharmacy(); association.setPharmacyId(pharmacyId);
            association.setDemographicNo(patient); association.setPreferredOrder(1);
            association.setStatus(DemographicPharmacy.ACTIVE); association.setConsentToContact(null); association.setAddDate(new Date());
            preferences.persist(association); preferences.flush();
            references.put("pharmacyInfo", Integer.toString(pharmacyId));
            references.put("demographicPharmacy", Integer.toString(requiredId(association.getId())));
        }
        return Map.copyOf(references);
    }
    private void physician(int patient, String provider, int facility, Person person, String role, String key, Map<String, String> references) {
        if (person==null) return;
        ProfessionalContact contact=new ProfessionalContact(); contact.setFirstName(person.firstName()); contact.setLastName(person.lastName());
        contacts.persist(contact); contacts.flush();
        int contactId=requiredId(contact.getId());
        DemographicContact relation=new DemographicContact(); relation.setContactId(Integer.toString(contactId));
        relation.setDemographicNo(patient); relation.setCategory(DemographicContact.CATEGORY_PROFESSIONAL);
        relation.setType(DemographicContact.TYPE_CONTACT); relation.setRole(role); relation.setCreator(provider);
        relation.setFacilityId(facility); relation.setConsentToContact((Boolean) null); relation.setCreated(new Date());
        relations.persist(relation); relations.flush();
        references.put("Contact#"+key, Integer.toString(contactId));
        references.put("DemographicContact#"+key, Integer.toString(requiredId(relation.getId())));
    }
    private static int requiredId(Integer id) {
        if (id==null || id<1) throw new CdsImportException("DEMOGRAPHIC_RELATION_NATIVE_ID_MISSING");
        return id;
    }
}
