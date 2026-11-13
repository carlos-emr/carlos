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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.*;
import org.w3c.dom.*;

@Tag("unit") @Tag("cds-export")
class CdsExportProfessionalContactMapperUnitTest {
    private static final String HASH="a".repeat(64);
    private final CdsExportProfessionalContactMapper mapper=new CdsExportProfessionalContactMapper();
    static CdsExportCapturedTextRowDto contact(long id){var values=columns(CdsExportPatientTableReadSupport.Plan.CONTACT); values.putAll(Map.of("id", Long.toString(id), "type", "ProfessionalContact", "deleted", "0", "firstName", "Élodie😀 & Bea", "lastName", "Fixture")); return new CdsExportCapturedTextRowDto("Contact", CdsExportPatientTableSourceReader.key(id), values); }
    static CdsExportCapturedTextRowDto association(long id, long contact, String role){var values=columns(CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_CONTACT); values.putAll(Map.of("id", Long.toString(id), "demographicNo", "27", "contactId", Long.toString(contact), "type", "2", "category", "professional", "deleted", "0", "active", "1", "mrp", "0", "role", role)); return new CdsExportCapturedTextRowDto("DemographicContact", CdsExportPatientTableSourceReader.key(id), values); }
    private static Map<String, String> columns(CdsExportPatientTableReadSupport.Plan plan){var values=new TreeMap<String, String>(); plan.columns().forEach(column-> values.put(column, null)); return values; }
    static CdsExportCapturedTextRowDto changed(CdsExportCapturedTextRowDto row, String column, String value){var values=new TreeMap<>(row.fields()); values.put(column, value); return new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), values); }
    private CdsExportProfessionalContactMapper.Result map(List<CdsExportCapturedTextRowDto> links, List<CdsExportCapturedTextRowDto> contacts)throws Exception {return mapper.map(27, links, contacts, 100, 1L<<20, ()-> {}); }
    private CdsExportProfessionalContactMapper.Result family(CdsExportCapturedTextRowDto contact)throws Exception{return map(List.of(association(1, 9, "Family Doctor")), List.of(contact)); }
    private byte[] render(CdsExportProfessionalContactMapper.Result result, boolean swallow)throws Exception {
        var output=new ByteArrayOutputStream(); var selected=Set.of(CdsCategory.DEMOGRAPHICS); var section=new CdsExportXmlComposer.Section(new CdsExportIdentityDto(HASH, CdsCategory.DEMOGRAPHICS, HASH), xml-> {
            xml.start("cds", "Names"); xml.start("cds_dt", "LegalName"); xml.attribute("", "namePurpose", "L"); for (var part:List.of(Map.entry("FirstName", "GIV"), Map.entry("LastName", "FAMC"))){xml.start("cds_dt", part.getKey()); xml.element("cds_dt", "Part", "Fixture"); xml.element("cds_dt", "PartType", part.getValue()); xml.end(); }xml.end(); xml.end(); xml.element("cds", "DateOfBirth", "1980-01-02"); xml.element("cds", "Gender", "U"); xml.element("cds", "UniqueVendorIdSequence", "27"); xml.start("cds", "PersonStatusCode"); xml.element("cds", "PersonStatusAsEnum", "A"); xml.end();
            try {new CdsExportProfessionalContactXmlWriter().write(result, xml, ()-> {}); }catch (IOException failure){if (!swallow)throw failure; }
        }); new CdsExportXmlComposer().compose(output, HASH, HASH, "2026.08.0-test", CdsLogicalProfile.ONTARIO_MD_5_2, selected, List.of(section).iterator(), ignored-> {}, 1L<<20, ()-> {}); new CdsExportXmlValidator().validate(new ByteArrayInputStream(output.toByteArray()), "27", selected, "2026.08.0-test", CdsLogicalProfile.ONTARIO_MD_5_2, CdsExportXmlLimitsDto.defaults(), ignored-> {}, ()-> {}); return output.toByteArray();
    }
    private static Element element(byte[] xml, String name)throws Exception {var factory=DocumentBuilderFactory.newDefaultInstance(); factory.setNamespaceAware(true); return (Element)factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getElementsByTagNameNS("cds", name).item(0); }
    @Test void shouldMapTheExternalFamilyPhysician_withExactNamesAndEveryPhysicalFieldAccounted()throws Exception {var result=family(contact(9)); assertThat(result.representable()).isTrue(); assertThat(result.fields()).hasSize(40); assertThat(result.physicians()).hasSize(1); var name=element(render(result, false), "FamilyPhysician"); assertThat(name.getElementsByTagNameNS("cds_dt", "FirstName").item(0).getTextContent()).isEqualTo("Élodie😀 & Bea"); assertThat(name.getElementsByTagNameNS("cds_dt", "LastName").item(0).getTextContent()).isEqualTo("Fixture"); }
    @Test void shouldKeepBothRelationships_whenTheSameContactIsFamilyAndReferringPhysician()throws Exception {var result=map(List.of(association(1, 9, "Family Doctor"), association(2, 9, "Referring Doctor")), List.of(contact(9))); assertThat(result.representable()).isTrue(); assertThat(result.physicians()).extracting(CdsExportProfessionalContactMapper.Physician::role).containsExactly(CdsExportProfessionalContactMapper.Role.REFERRING, CdsExportProfessionalContactMapper.Role.FAMILY); assertThat(result.nameUses().values()).allSatisfy(roles-> assertThat(roles).containsExactlyInAnyOrder(CdsExportProfessionalContactMapper.Role.REFERRING, CdsExportProfessionalContactMapper.Role.FAMILY)); assertThat(element(render(result, false), "ReferredPhysician")).isNotNull(); assertThat(element(render(result, false), "FamilyPhysician")).isNotNull(); }
    @Test void shouldKeepIdenticalNamesInDifferentRoles_asDistinctRelationshipFacts()throws Exception {var result=map(List.of(association(1, 9, "Family Doctor"), association(2, 10, "Referring Doctor")), List.of(contact(9), contact(10))); assertThat(result.physicians()).hasSize(2); assertThat(result.fields()).hasSize(80); assertThat(result.physicians().getFirst().contactRecordKey()).isNotEqualTo(result.physicians().getLast().contactRecordKey()); }
    @Test void shouldRequireReviewForDuplicateAssociations_insteadOfCollapsingTwoNativeRowsIntoOneRole()throws Exception {var result=map(List.of(association(1, 9, "Family Doctor"), association(2, 9, "Family Doctor")), List.of(contact(9))); assertThat(result.representable()).isFalse(); assertThat(result.physicians()).isEmpty(); }
    @Test void shouldRequireReviewForMultipleFamilyPhysicians_withoutChoosingByIdOrName()throws Exception {assertThat(map(List.of(association(1, 9, "Family Doctor"), association(2, 10, "Family Doctor")), List.of(contact(9), contact(10))).representable()).isFalse(); }
    @Test void shouldAcceptTheDefaultContactConsent_whileHoldingAnExplicitRefusal()throws Exception {
        // D5: Contact2Action starts a new professional link at consentToContact TRUE and procontact.jsp lists "Consent" first.
        assertThat(map(List.of(changed(association(1, 9, "Family Doctor"), "consentToContact", "1")), List.of(contact(9))).representable()).isTrue();
        assertThat(map(List.of(changed(association(1, 9, "Family Doctor"), "consentToContact", "0")), List.of(contact(9))).representable()).isFalse();
    }
    @Test void shouldRequireReviewForUnknownRoles_withoutInventingAStandardRelationship()throws Exception {assertThat(map(List.of(association(1, 9, "Specialist")), List.of(contact(9))).representable()).isFalse(); }
    @Test void shouldRefuseHistoricalOrInactiveRelationships_untilHistoryIsExplicitlyReconciled()throws Exception {for (var entry:List.of(Map.entry("deleted", "1"), Map.entry("active", "0"))){assertThat(map(List.of(changed(association(1, 9, "Family Doctor"), entry.getKey(), entry.getValue())), List.of(contact(9))).representable()).isFalse(); }}
    @Test void shouldRejectAnotherPatient_insteadOfBorrowingTheirRelationship() {assertThatThrownBy(()-> map(List.of(changed(association(1, 9, "Family Doctor"), "demographicNo", "28")), List.of(contact(9)))).hasMessage("CDS_EXPORT_PROFESSIONAL_CONTACT_SOURCE_SCOPE_INVALID"); }
    @Test void shouldRejectUnreferencedContacts_beforeAnyNameCanBeEmitted() {assertThatThrownBy(()-> map(List.of(association(1, 9, "Family Doctor")), List.of(contact(10)))).hasMessage("CDS_EXPORT_PROFESSIONAL_CONTACT_SOURCE_SCOPE_INVALID"); }
    @Test void shouldRequireTheExactReferencedContact_whenTheLookupIsMissing()throws Exception {assertThat(map(List.of(association(1, 9, "Family Doctor")), List.of()).representable()).isFalse(); }
    @Test void shouldRejectPersonalContactSubtypes_forProfessionalNameProjection()throws Exception {assertThat(family(changed(contact(9), "type", "Contact")).representable()).isFalse(); }
    @Test void shouldPreserveMissingVersusEmptyNameParts_withoutInventingASurnameOrGivenName()throws Exception {assertThat(family(changed(contact(9), "firstName", null)).representable()).isFalse(); var empty=family(changed(contact(9), "firstName", "")); assertThat(empty.representable()).isTrue(); assertThat(element(render(empty, false), "FamilyPhysician").getElementsByTagNameNS("cds_dt", "FirstName").item(0).getTextContent()).isEmpty(); }
    @Test void shouldRequireReviewForAdditionalClinicalAttributes_untilSupportingNativeDataIsProjected()throws Exception {for (var entry:List.of(Map.entry("cpso", "001234"), Map.entry("fax", "6135550101"), Map.entry("specialty", "Neurology"), Map.entry("note", "Important context"))){assertThat(family(changed(contact(9), entry.getKey(), entry.getValue())).representable()).isFalse(); }}
    @Test void shouldKeepCommunicationConsentSeparate_fromDisclosureAndNameProjection()throws Exception {assertThat(map(List.of(changed(association(1, 9, "Family Doctor"), "consentToContact", "0")), List.of(contact(9))).representable()).isFalse(); }
    @Test void shouldRefuseAdditionalClinicalRoles_withoutDroppingTheExtraPurpose()throws Exception {for (String field:List.of("mrp", "ec", "sdm")){assertThat(map(List.of(changed(association(1, 9, "Family Doctor"), field, field.equals("mrp")?"1":"true")), List.of(contact(9))).representable()).isFalse(); }}
    @Test void shouldRefuseOverlengthNames_withoutTruncatingAnyUnicodeCodePoint()throws Exception {assertThat(family(changed(contact(9), "firstName", "😀".repeat(51))).representable()).isFalse(); }
    @Test void shouldPreserveCarriageReturns_andLiteralMarkupInRepresentableNames()throws Exception {var name="A\r\nB <literal> & C"; var result=family(changed(contact(9), "firstName", name)); assertThat(result.representable()).isTrue(); assertThat(element(render(result, false), "FamilyPhysician").getElementsByTagNameNS("cds_dt", "FirstName").item(0).getTextContent()).isEqualTo(name); }
    @Test void shouldRespectRowByteAndCancellationBounds_beforeReturningAnyProjection() {assertThatThrownBy(()-> mapper.map(27, List.of(association(1, 9, "Family Doctor")), List.of(contact(9)), 1, 1L<<20, ()-> {})).hasMessage("CDS_EXPORT_PROFESSIONAL_CONTACT_RESOURCE_LIMIT"); assertThatThrownBy(()-> mapper.map(27, List.of(association(1, 9, "Family Doctor")), List.of(contact(9)), 100, 64, ()-> {})).hasMessage("CDS_EXPORT_PROFESSIONAL_CONTACT_RESOURCE_LIMIT"); assertThatThrownBy(()-> mapper.map(27, List.of(), List.of(), 100, 1000, ()-> {throw new IllegalStateException("CANCELLED_PROFESSIONAL"); })).hasMessage("CANCELLED_PROFESSIONAL"); }
    @Test void shouldLatchWriterFailure_evenWhenAProducerCatchesTheReviewException()throws Exception {var blocked=family(changed(contact(9), "cpso", "001234")); assertThatThrownBy(()-> render(blocked, true)).isInstanceOf(IOException.class); }
    @Test void shouldPreserveImmutableEvidence_andProtectedDebugStrings()throws Exception {var result=family(contact(9)); assertThatThrownBy(()-> result.nameUses().clear()).isInstanceOf(UnsupportedOperationException.class); assertThat(result.toString()+result.physicians().getFirst()).doesNotContain("Élodie", "Fixture"); }
}
