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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
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
    private final CdsFieldParser fields;
    @PersistenceContext(unitName="entityManagerFactory") private EntityManager entityManager;
    public CdsDemographicRelationsService(CdsFieldParser fields) { this.fields=fields; }

    public CdsDemographicRelationsDto prepare(CdsRecordDto record,CdsImportCommand command) throws IOException {
        if(!"Demographics".equals(record.section())) throw new IllegalArgumentException("Wrong demographic relation section");
        Map<String,CdsClinicalDiagnosticDto> notices=new LinkedHashMap<>();
        Person family=person(record,"FamilyPhysician",notices), referring=person(record,"ReferredPhysician",notices);
        String scope="PreferredPharmacy";
        String name=scalar(record,scope+"/Name",255,notices);
        Pharmacy pharmacy=null;
        if(name!=null && !name.isBlank()) {
            String formatted=scalar(record,scope+"/Address/Formatted",255,notices);
            List<String> lines=new ArrayList<>();
            for(String line:List.of("Line1","Line2","Line3")) {
                String value=scalar(record,scope+"/Address/Structured/"+line,50,notices);
                if(value!=null) lines.add(value);
            }
            String address=formatted!=null?formatted:lines.isEmpty()?null:String.join("\n",lines);
            String postal=scalar(record,scope+"/Address/Structured/PostalZipCode/PostalCode",20,notices);
            if(postal==null) postal=scalar(record,scope+"/Address/Structured/PostalZipCode/ZipCode",20,notices);
            String provincePath=scope+"/Address/Structured/CountrySubdivisionCode";
            String sourceProvince=scalar(record,provincePath,255,notices);
            String province=CdsHealthCardAuthority.normalize(sourceProvince);
            if(!java.util.Objects.equals(sourceProvince,province)) {
                notice(notices,"CDS_CANADIAN_PROVINCE_NORMALIZED",provincePath);
            } else if(province!=null && !CdsHealthCardAuthority.CANADIAN.contains(province)) {
                notice(notices,"CDS_CONTEXT_PHARMACY_PROVINCE_REQUIRES_REVIEW",provincePath);
            }
            pharmacy=new Pharmacy(name,address,scalar(record,scope+"/Address/Structured/City",60,notices),
                    province,postal,
                    phone(record,scope+"/PhoneNumber",notices),phone(record,scope+"/FaxNumber",notices),
                    scalar(record,scope+"/EmailAddress",100,notices));
            notice(notices,"CDS_PHARMACY_CONSENT_NOT_ASSERTED",scope);
        } else if(name!=null) notice(notices,"CDS_CONTEXT_PHARMACY_SOURCE_ONLY",scope);
        return new CdsDemographicRelationsDto(family,referring,pharmacy,
                command.facilityId()==null?0:command.facilityId(),List.copyOf(notices.values()));
    }

    private Person person(CdsRecordDto record,String path,Map<String,CdsClinicalDiagnosticDto> notices) throws IOException {
        String first=scalar(record,path+"/FirstName",50,notices), last=scalar(record,path+"/LastName",50,notices);
        if(first==null && last==null) return null;
        if(first==null || last==null || first.isBlank() && last.isBlank()) {
            notice(notices,"CDS_CONTEXT_PHYSICIAN_SOURCE_ONLY",path); return null;
        }
        notice(notices,"CDS_CONTEXT_PHYSICIAN_CONSENT_NOT_ASSERTED",path);
        return new Person(first,last);
    }

    private String phone(CdsRecordDto record,String path,Map<String,CdsClinicalDiagnosticDto> notices) throws IOException {
        int before=notices.size();
        String number=scalar(record,path+"/phoneNumber",25,notices);
        String area=scalar(record,path+"/areaCode",30,notices), exchange=scalar(record,path+"/exchange",30,notices);
        String local=scalar(record,path+"/number",30,notices), extension=scalar(record,path+"/extension",5,notices);
        if(notices.size()!=before) { notice(notices,"CDS_CONTEXT_PHONE_SOURCE_ONLY",path); return null; }
        if(number==null && (area!=null || exchange!=null || local!=null)) {
            number=CdsPhoneComponents.combine(area,exchange,local);
            notice(notices,number==null?"CDS_CONTEXT_PHONE_SOURCE_ONLY":"CDS_PHONE_COMPONENTS_COMBINED",path);
        }
        if(number==null) return null;
        String combined=number+(extension==null || extension.isEmpty()?"":" ext "+extension);
        if(combined.codePointCount(0,combined.length())>20) {
            notice(notices,"CDS_CONTEXT_PHONE_SOURCE_ONLY",path); return null;
        }
        return combined;
    }

    private String scalar(CdsRecordDto record,String path,int maximum,Map<String,CdsClinicalDiagnosticDto> notices)
            throws IOException {
        try { return fields.firstText(record,path,maximum); }
        catch(CdsInputException over) {
            if(!"CDS_TARGET_TEXT_CAPACITY_EXCEEDED".equals(over.code())) throw over;
            notice(notices,"CDS_NATIVE_SCALAR_SOURCE_ONLY",path); return null;
        }
    }
    private static void notice(Map<String,CdsClinicalDiagnosticDto> notices,String code,String path) {
        notices.putIfAbsent(code+"/"+path,new CdsClinicalDiagnosticDto(code,path));
    }

    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public Map<String,String> write(int patient,String auditProvider,CdsDemographicRelationsDto value) {
        if(!CdsImportUnitService.isActiveForMaintenance()) throw new CdsImportException("PARTICIPATING_UNIT_REQUIRED");
        if(patient<1 || auditProvider==null || !auditProvider.matches("[A-Za-z0-9_-]{1,16}"))
            throw new IllegalArgumentException("DEMOGRAPHIC_RELATIONS_TARGET_INVALID");
        Map<String,String> references=new LinkedHashMap<>();
        physician(patient,auditProvider,value.facilityId(),value.familyPhysician(),"Family Doctor","family",references);
        physician(patient,auditProvider,value.facilityId(),value.referringPhysician(),"Referring Doctor","referring",references);
        if(value.preferredPharmacy()!=null) {
            Pharmacy p=value.preferredPharmacy(); PharmacyInfo row=new PharmacyInfo();
            row.setName(p.name()); row.setAddress(p.address()); row.setCity(p.city()); row.setProvince(p.province());
            row.setPostalCode(p.postal()); row.setPhone1(p.phone()); row.setFax(p.fax()); row.setEmail(p.email());
            row.setStatus(PharmacyInfo.ACTIVE); row.setAddDate(new Date());
            entityManager.persist(row); entityManager.flush();
            DemographicPharmacy association=new DemographicPharmacy(); association.setPharmacyId(row.getId());
            association.setDemographicNo(patient); association.setPreferredOrder(1);
            association.setStatus(DemographicPharmacy.ACTIVE); association.setConsentToContact(null); association.setAddDate(new Date());
            entityManager.persist(association); entityManager.flush();
            references.put("pharmacyInfo",row.getId().toString());
            references.put("demographicPharmacy",association.getId().toString());
        }
        return Map.copyOf(references);
    }
    private void physician(int patient,String provider,int facility,Person person,String role,String key,Map<String,String> references) {
        if(person==null) return;
        ProfessionalContact contact=new ProfessionalContact(); contact.setFirstName(person.firstName()); contact.setLastName(person.lastName());
        entityManager.persist(contact); entityManager.flush();
        DemographicContact relation=new DemographicContact(); relation.setContactId(contact.getId().toString());
        relation.setDemographicNo(patient); relation.setCategory(DemographicContact.CATEGORY_PROFESSIONAL);
        relation.setType(DemographicContact.TYPE_CONTACT); relation.setRole(role); relation.setCreator(provider);
        relation.setFacilityId(facility); relation.setConsentToContact((Boolean)null); relation.setCreated(new Date());
        entityManager.persist(relation); entityManager.flush();
        references.put("Contact#"+key,contact.getId().toString());
        references.put("DemographicContact#"+key,relation.getId().toString());
    }
}
