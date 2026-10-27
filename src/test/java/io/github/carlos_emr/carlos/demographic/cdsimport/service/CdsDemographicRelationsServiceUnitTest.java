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

import io.github.carlos_emr.carlos.commn.model.Contact;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.DemographicPharmacy;
import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;
import io.github.carlos_emr.carlos.commn.model.ProfessionalContact;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicRelationsDto;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsDemographicRelationsServiceUnitTest {
    @TempDir Path directory;

    @Test
    void shouldPrepareLiteralExternalPhysicians_withoutMatchingNamesToLocalProviders() throws Exception {
        var source=CdsClinicalFixture.record(directory,"Demographics",Map.of(
                "FamilyPhysician/FirstName","  Élise ","FamilyPhysician/LastName","李",
                "ReferredPhysician/FirstName","Sam","ReferredPhysician/LastName","Same"));
        var prepared=new CdsDemographicRelationsService(new CdsFieldParser()).prepare(source,command());
        assertThat(prepared.familyPhysician()).isEqualTo(new CdsDemographicRelationsDto.Person("  Élise ","李"));
        assertThat(prepared.referringPhysician()).isEqualTo(new CdsDemographicRelationsDto.Person("Sam","Same"));
        assertThat(prepared.facilityId()).isEqualTo(42);
        assertThat(prepared.diagnostics()).extracting(d->d.code())
                .containsExactly("CDS_CONTEXT_PHYSICIAN_CONSENT_NOT_ASSERTED","CDS_CONTEXT_PHYSICIAN_CONSENT_NOT_ASSERTED");
        assertThat(prepared.toString()+prepared.familyPhysician()).doesNotContain("Élise","李");
    }

    @Test
    void shouldKeepPharmacyAddressAndPhoneMeaning_whenSourceProvidesStructuredComponents() throws Exception {
        var values=new LinkedHashMap<String,String>();
        values.put("PreferredPharmacy/Name","  Unicode 藥局 ");
        values.put("PreferredPharmacy/Address/Structured/Line1","10 Main");
        values.put("PreferredPharmacy/Address/Structured/Line2","Suite 2");
        values.put("PreferredPharmacy/Address/Structured/City","Toronto");
        values.put("PreferredPharmacy/Address/Structured/CountrySubdivisionCode","CA-ON");
        values.put("PreferredPharmacy/Address/Structured/PostalZipCode/PostalCode","M1M1M1");
        values.put("PreferredPharmacy/PhoneNumber/areaCode","613");
        values.put("PreferredPharmacy/PhoneNumber/exchange","555");
        values.put("PreferredPharmacy/PhoneNumber/number","0101");
        values.put("PreferredPharmacy/PhoneNumber/extension","12");
        values.put("PreferredPharmacy/FaxNumber/phoneNumber","+1-613-555-0102");
        values.put("PreferredPharmacy/EmailAddress","fixture@example.invalid");
        var dto=new CdsDemographicRelationsService(new CdsFieldParser())
                .prepare(CdsClinicalFixture.record(directory,"Demographics",values),command());
        var pharmacy=dto.preferredPharmacy();
        assertThat(pharmacy.name()).isEqualTo("  Unicode 藥局 ");
        assertThat(pharmacy.address()).isEqualTo("10 Main\nSuite 2");
        assertThat(pharmacy.province()).isEqualTo("ON");
        assertThat(pharmacy.phone()).isEqualTo("613-555-0101 ext 12");
        assertThat(pharmacy.fax()).isEqualTo("+1-613-555-0102");
        assertThat(pharmacy.postal()).isEqualTo("M1M1M1");
        assertThat(pharmacy.email()).isEqualTo("fixture@example.invalid");
        assertThat(dto.diagnostics()).extracting(d->d.code())
                .contains("CDS_PHONE_COMPONENTS_COMBINED","CDS_PHARMACY_CONSENT_NOT_ASSERTED","CDS_CANADIAN_PROVINCE_NORMALIZED");
    }

    @Test
    void shouldNormalizeOnlyKnownCanadianProvinceAliases_withoutGuessingUnknownOrForeignSubdivisions() throws Exception {
        for(String province:List.of("US-CA","CA-XX","ca-on","CA-ON ","-90")) {
            var dto=new CdsDemographicRelationsService(new CdsFieldParser()).prepare(
                    CdsClinicalFixture.record(directory,"Demographics",Map.of("PreferredPharmacy/Name","Pharmacy",
                            "PreferredPharmacy/Address/Structured/CountrySubdivisionCode",province)),command());
            assertThat(dto.preferredPharmacy().province()).isEqualTo(province);
            assertThat(dto.diagnostics()).extracting(d->d.code()).contains("CDS_CONTEXT_PHARMACY_PROVINCE_REQUIRES_REVIEW")
                    .doesNotContain("CDS_CANADIAN_PROVINCE_NORMALIZED");
        }
        for(String province:io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsHealthCardAuthority.CANADIAN) {
            var dto=new CdsDemographicRelationsService(new CdsFieldParser()).prepare(
                    CdsClinicalFixture.record(directory,"Demographics",Map.of("PreferredPharmacy/Name","Pharmacy",
                            "PreferredPharmacy/Address/Structured/CountrySubdivisionCode","CA-"+province)),command());
            assertThat(dto.preferredPharmacy().province()).isEqualTo(province);
            assertThat(dto.diagnostics()).extracting(d->d.code()).contains("CDS_CANADIAN_PROVINCE_NORMALIZED");
        }
    }

    @Test
    void shouldRetainOversizePharmacyFieldsInSource_withoutTruncatingOrGuessingPhoneComponents() throws Exception {
        var dto=new CdsDemographicRelationsService(new CdsFieldParser()).prepare(
                CdsClinicalFixture.record(directory,"Demographics",Map.of(
                        "PreferredPharmacy/Name","Pharmacy",
                        "PreferredPharmacy/Address/Formatted","x".repeat(256),
                        "PreferredPharmacy/Address/Structured/City","市".repeat(61),
                        "PreferredPharmacy/PhoneNumber/areaCode","613",
                        "PreferredPharmacy/PhoneNumber/exchange","555",
                        "PreferredPharmacy/PhoneNumber/number","5559999999",
                        "PreferredPharmacy/FaxNumber/phoneNumber","123456789012345678901")),command());
        assertThat(dto.preferredPharmacy().address()).isNull();
        assertThat(dto.preferredPharmacy().city()).isNull();
        assertThat(dto.preferredPharmacy().phone()).isNull();
        assertThat(dto.preferredPharmacy().fax()).isNull();
        assertThat(dto.diagnostics()).extracting(d->d.field()).contains(
                "PreferredPharmacy/Address/Formatted","PreferredPharmacy/Address/Structured/City",
                "PreferredPharmacy/PhoneNumber","PreferredPharmacy/FaxNumber");
    }

    @Test
    void shouldKeepAnUnrepresentablePharmacySourceOnly_insteadOfCreatingAnUnnamedCatalogueEntry() throws Exception {
        var dto=new CdsDemographicRelationsService(new CdsFieldParser()).prepare(
                CdsClinicalFixture.record(directory,"Demographics",Map.of("PreferredPharmacy/Name","藥".repeat(256))),command());
        assertThat(dto.preferredPharmacy()).isNull();
        assertThat(dto.diagnostics()).anySatisfy(d->{
            assertThat(d.code()).isEqualTo("CDS_NATIVE_SCALAR_SOURCE_ONLY");
            assertThat(d.field()).isEqualTo("PreferredPharmacy/Name");
        });
    }

    @Test
    void shouldWriteProfessionalRolesAndPersistentPreference_withUnknownContactConsent() throws Exception {
        var fields=mock(CdsFieldParser.class); var service=new CdsDemographicRelationsService(fields);
        var em=mock(EntityManager.class); ReflectionTestUtils.setField(service,"entityManager",em);
        List<Object> stored=new ArrayList<>();
        doAnswer(call->{
            Object row=call.getArgument(0); stored.add(row);
            if(row instanceof Contact contact) contact.setId(stored.size());
            else ReflectionTestUtils.setField(row,"id",stored.size());
            return null;
        }).when(em).persist(any());
        var dto=new CdsDemographicRelationsDto(new CdsDemographicRelationsDto.Person("Family","Doctor"),
                new CdsDemographicRelationsDto.Person("Referring","Doctor"),
                new CdsDemographicRelationsDto.Pharmacy("Pharmacy",null,null,null,null,"613-555-0101",null,null),42,List.of());
        Map<String,String> references;
        try(var unit=mockStatic(CdsImportUnitService.class)) {
            unit.when(CdsImportUnitService::isActiveForMaintenance).thenReturn(true);
            references=service.write(999998,"T099",dto);
        }
        assertThat(stored).hasSize(6);
        assertThat(stored.get(0)).isInstanceOf(ProfessionalContact.class);
        assertThat(stored.get(2)).isInstanceOf(ProfessionalContact.class);
        var family=(DemographicContact)stored.get(1); var referring=(DemographicContact)stored.get(3);
        assertThat(family.getRole()).isEqualTo("Family Doctor"); assertThat(referring.getRole()).isEqualTo("Referring Doctor");
        assertThat(family.getConsentToContact()).isNull(); assertThat(referring.getConsentToContact()).isNull();
        assertThat(family.getCategory()).isEqualTo(DemographicContact.CATEGORY_PROFESSIONAL);
        assertThat(family.getType()).isEqualTo(DemographicContact.TYPE_CONTACT);
        assertThat(family.getCreator()).isEqualTo("T099"); assertThat(family.getFacilityId()).isEqualTo(42);
        var pharmacy=(PharmacyInfo)stored.get(4); var preference=(DemographicPharmacy)stored.get(5);
        assertThat(pharmacy.getUid()).isZero(); assertThat(pharmacy.getPreferredOrder()).isNull();
        assertThat(preference.getPreferredOrder()).isEqualTo(1); assertThat(preference.getPharmacyId()).isEqualTo(pharmacy.getId());
        assertThat(preference.getConsentToContact()).isNull(); assertThat(preference.getDemographicNo()).isEqualTo(999998);
        assertThat(references).containsEntry("Contact#family","1").containsEntry("DemographicContact#family","2")
                .containsEntry("Contact#referring","3").containsEntry("DemographicContact#referring","4")
                .containsEntry("pharmacyInfo","5").containsEntry("demographicPharmacy","6");
        verifyNoInteractions(fields);
        verify(em,never()).createQuery(anyString());
    }

    @Test
    void shouldRequireTheParticipatingImportUnit_beforeWritingAnyRelation() {
        var em=mock(EntityManager.class); var service=new CdsDemographicRelationsService(new CdsFieldParser());
        ReflectionTestUtils.setField(service,"entityManager",em);
        assertThatThrownBy(()->service.write(999998,"T099",CdsDemographicRelationsDto.empty()))
                .isInstanceOf(CdsImportException.class).hasMessage("PARTICIPATING_UNIT_REQUIRED");
        verifyNoInteractions(em);
    }

    @Test
    void shouldRefuseMissingGeneratedIdentity_whenFlushedPharmacyAssociationHasNoId() {
        var service=new CdsDemographicRelationsService(mock(CdsFieldParser.class));
        var em=mock(EntityManager.class); ReflectionTestUtils.setField(service,"entityManager",em);
        doAnswer(call->{
            Object row=call.getArgument(0);
            if (row instanceof PharmacyInfo pharmacy) pharmacy.setId(42);
            return null;
        }).when(em).persist(any());
        var dto=new CdsDemographicRelationsDto(null,null,
                new CdsDemographicRelationsDto.Pharmacy("Pharmacy",null,null,null,null,null,null,null),42,List.of());
        try(var unit=mockStatic(CdsImportUnitService.class)) {
            unit.when(CdsImportUnitService::isActiveForMaintenance).thenReturn(true);
            assertThatThrownBy(()->service.write(999998,"T099",dto))
                    .isInstanceOf(CdsImportException.class).hasMessage("DEMOGRAPHIC_RELATION_NATIVE_ID_MISSING");
        }
        verify(em,times(2)).persist(any());
        verify(em,times(2)).flush();
    }

    @Test
    void shouldCreateNoRelations_whenSourceHasNoContextualAssociations() throws Exception {
        var dto=new CdsDemographicRelationsService(new CdsFieldParser()).prepare(
                CdsClinicalFixture.record(directory,"Demographics",Map.of("UniqueVendorIdSequence","fixture")),command());
        assertThat(dto.familyPhysician()).isNull(); assertThat(dto.referringPhysician()).isNull();
        assertThat(dto.preferredPharmacy()).isNull(); assertThat(dto.diagnostics()).isEmpty();
    }

    @Test
    void shouldTraverseTheMetadataOnlyOnce_whenContextualRelationsAreAbsentFromALargeDemographic() throws Exception {
        Map<String,String> values=new LinkedHashMap<>();
        for(int index=1;index<=2000;index++) values.put("Extensions/VendorValue["+index+"]","unrelated source value");
        var record=CdsClinicalFixture.record(directory,"Demographics",values);
        var fields=spy(new CdsFieldParser()); var dto=new CdsDemographicRelationsService(fields).prepare(record,command());
        assertThat(dto.familyPhysician()).isNull(); assertThat(dto.preferredPharmacy()).isNull();
        verify(fields,times(1)).forEachField(eq(record),any());
        verify(fields,never()).firstText(any(),anyString(),anyInt());
    }

    private CdsImportCommand command() {
        return new CdsImportCommand(directory,"relations-test","T099",null,42,"synthetic","pcon-2024-02",
                false,false,false,0,null,false);
    }
}
