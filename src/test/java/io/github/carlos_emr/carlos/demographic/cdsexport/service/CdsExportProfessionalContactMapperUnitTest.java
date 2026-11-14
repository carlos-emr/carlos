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
        }); new CdsExportXmlComposer().compose(output, HASH, HASH, "2026.08.0-test", CdsLogicalProfile.ONTARIO_MD_5_2, selected, List.of(section).iterator(), ignored-> {}, 1L<<20, ()-> {}); new CdsExportXmlValidator().validate(new ByteArrayInputStream(output.toByteArray()), "27", selected, "2026.08.0-test", CdsLogicalProfile.ONTARIO_MD_5_2, ignored-> {}, ()-> {}); return output.toByteArray();
    }
    private static Element element(byte[] xml, String name)throws Exception {var factory=DocumentBuilderFactory.newDefaultInstance(); factory.setNamespaceAware(true); return (Element)factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getElementsByTagNameNS("cds", name).item(0); }
    @Test void shouldMapTheExternalFamilyPhysician_withExactNamesAndEveryPhysicalFieldAccounted()throws Exception {var result=family(contact(9)); assertThat(result.representable()).isTrue(); assertThat(result.fields()).hasSize(40); assertThat(result.physicians()).hasSize(1); var name=element(render(result, false), "FamilyPhysician"); assertThat(name.getElementsByTagNameNS("cds_dt", "FirstName").item(0).getTextContent()).isEqualTo("Élodie😀 & Bea"); assertThat(name.getElementsByTagNameNS("cds_dt", "LastName").item(0).getTextContent()).isEqualTo("Fixture"); }
    @Test void shouldKeepBothRelationships_whenTheSameContactIsFamilyAndReferringPhysician()throws Exception {var result=map(List.of(association(1, 9, "Family Doctor"), association(2, 9, "Referring Doctor")), List.of(contact(9))); assertThat(result.representable()).isTrue(); assertThat(result.physicians()).extracting(CdsExportProfessionalContactMapper.Physician::role).containsExactly(CdsExportProfessionalContactMapper.Role.REFERRING, CdsExportProfessionalContactMapper.Role.FAMILY); assertThat(result.nameUses().values()).allSatisfy(roles-> assertThat(roles).containsExactlyInAnyOrder(CdsExportProfessionalContactMapper.Role.REFERRING, CdsExportProfessionalContactMapper.Role.FAMILY)); assertThat(element(render(result, false), "ReferredPhysician")).isNotNull(); assertThat(element(render(result, false), "FamilyPhysician")).isNotNull(); }
    @Test void shouldKeepIdenticalNamesInDifferentRoles_asDistinctRelationshipFacts()throws Exception {var result=map(List.of(association(1, 9, "Family Doctor"), association(2, 10, "Referring Doctor")), List.of(contact(9), contact(10))); assertThat(result.physicians()).hasSize(2); assertThat(result.fields()).hasSize(80); assertThat(result.physicians().getFirst().contactRecordKey()).isNotEqualTo(result.physicians().getLast().contactRecordKey()); }
    private static void assertCarried(CdsExportProfessionalContactMapper.Result result, CdsExportCapturedTextRowDto row, String... columns){assertThat(result.representable()).isTrue(); for (String column:columns)assertThat(CdsExportDemographicSupportingMapper.carries(result.fields().get(new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column)))).as(column).isTrue(); }
    @Test void shouldCarryBothAssociations_whenTwoRowsHoldOneRoleForOneContact()throws Exception {
        // Was a hold (MULTIPLE_PROFESSIONAL_RELATIONSHIPS_EXCEED_WIRE_CARDINALITY). FamilyPhysician states one physician: neither row is chosen and both travel in the native record.
        var first=association(1, 9, "Family Doctor"); var second=association(2, 9, "Family Doctor"); var result=map(List.of(first, second), List.of(contact(9)));
        assertThat(result.physicians()).isEmpty(); assertCarried(result, first, "role", "contactId", "type", "category"); assertCarried(result, second, "role", "contactId"); assertCarried(result, contact(9), "firstName", "lastName", "type"); }
    @Test void shouldCarryEveryFamilyPhysician_whenTheHealthCareTeamListsTwo()throws Exception {
        // The health care team may list two members in one role; the chart was held. No physician is chosen by id or name: both associations and both contacts are carried, and the other role is still written.
        var first=association(1, 9, "Family Doctor"); var second=association(2, 10, "Family Doctor"); var referring=association(3, 11, "Referring Doctor");
        var result=map(List.of(first, second, referring), List.of(contact(9), contact(10), contact(11)));
        assertCarried(result, first, "role", "contactId"); assertCarried(result, second, "role", "contactId"); assertCarried(result, contact(10), "firstName", "lastName");
        assertThat(result.physicians()).extracting(CdsExportProfessionalContactMapper.Physician::role).containsExactly(CdsExportProfessionalContactMapper.Role.REFERRING); }
    @Test void shouldAcceptTheDefaultContactConsent_andCarryAnExplicitRefusal()throws Exception {
        // D5: Contact2Action starts a new professional link at consentToContact TRUE and procontact.jsp lists "Consent" first.
        assertThat(map(List.of(changed(association(1, 9, "Family Doctor"), "consentToContact", "1")), List.of(contact(9))).representable()).isTrue();
        // "No Consent" is ordinary: the physician is exported and the refusal is carried exactly.
        var refused = changed(association(1, 9, "Family Doctor"), "consentToContact", "0");
        var carried = map(List.of(refused), List.of(contact(9)));
        assertThat(carried.representable()).isTrue();
        var decision = carried.fields().get(new CdsExportNativeFieldKeyDto("DemographicContact", refused.recordKey(), "consentToContact"));
        assertThat(CdsExportDemographicSupportingMapper.carries(decision)).isTrue();
        assertThat(decision.rule()).isEqualTo(CdsExportContactConsent.REFUSAL_CARRIED_RULE);
    }
    @Test void shouldCarryOtherRoles_withoutInventingAStandardRelationship()throws Exception {
        // procontact.jsp offers Specialist and Dietician: no CDS physician element means them, so the relationship travels in the native record.
        for (String role:List.of("Specialist", "Dietician")){var result=map(List.of(association(1, 9, role)), List.of(contact(9))); assertThat(result.representable()).isTrue(); assertThat(result.physicians()).isEmpty(); assertThat(CdsExportDemographicSupportingMapper.carries(result.fields().get(new CdsExportNativeFieldKeyDto("DemographicContact", association(1, 9, role).recordKey(), "role")))).isTrue(); }
    }
    @Test void shouldLeaveDeletedAndCarryInactiveRelationships_withoutEmittingEither()throws Exception {for (var entry:List.of(Map.entry("deleted", "1"), Map.entry("active", "0"))){var result=map(List.of(changed(association(1, 9, "Family Doctor"), entry.getKey(), entry.getValue())), List.of(contact(9))); assertThat(result.representable()).isTrue(); assertThat(result.physicians()).isEmpty(); assertThat(result.fields().get(new CdsExportNativeFieldKeyDto("Contact", contact(9).recordKey(), "lastName")).outcome().name()).isEqualTo(entry.getKey().equals("deleted")?"OPERATIONAL":"MAPPED"); }}
    @Test void shouldRejectAnotherPatient_insteadOfBorrowingTheirRelationship() {assertThatThrownBy(()-> map(List.of(changed(association(1, 9, "Family Doctor"), "demographicNo", "28")), List.of(contact(9)))).hasMessage("CDS_EXPORT_PROFESSIONAL_CONTACT_SOURCE_SCOPE_INVALID"); }
    @Test void shouldRejectUnreferencedContacts_beforeAnyNameCanBeEmitted() {assertThatThrownBy(()-> map(List.of(association(1, 9, "Family Doctor")), List.of(contact(10)))).hasMessage("CDS_EXPORT_PROFESSIONAL_CONTACT_SOURCE_SCOPE_INVALID"); }
    @Test void shouldCarryTheAssociation_whenItsContactRowIsNotInTheChart()throws Exception {var link=association(1, 9, "Family Doctor"); var result=map(List.of(link), List.of()); assertThat(result.physicians()).isEmpty(); assertCarried(result, link, "role", "contactId"); }
    @Test void shouldExportThePhysician_whenTheTeamNamesAPlainContactRow()throws Exception {
        // The contact search lists every contact row; the chart shows the person as the family physician either way. The stored subtype is carried.
        var plain=changed(contact(9), "type", "Contact"); var result=family(plain); assertThat(result.physicians()).hasSize(1); assertCarried(result, plain, "type");
        // A contact row marked deleted, or one with no name at all, states no physician: the association and the contact are carried.
        for (var unnamed:List.of(changed(contact(9), "deleted", "1"), changed(changed(contact(9), "firstName", " "), "lastName", null))){var held=family(unnamed); assertThat(held.physicians()).isEmpty(); assertCarried(held, association(1, 9, "Family Doctor"), "role", "contactId"); }}
    @Test void shouldWriteAnEmptyPart_whenANamePartIsMissingOrEmpty()throws Exception {
        // Both parts are required elements. A part with nothing stored (SQL NULL or empty) is written empty, never invented, and no longer holds the chart.
        for (String stored:new String[]{null, ""}){var result=family(changed(contact(9), "firstName", stored)); assertThat(result.representable()).isTrue(); var family=element(render(result, false), "FamilyPhysician"); assertThat(family.getElementsByTagNameNS("cds_dt", "FirstName").item(0).getTextContent()).isEmpty(); assertThat(family.getElementsByTagNameNS("cds_dt", "LastName").item(0).getTextContent()).isEqualTo("Fixture"); }}
    @Test void shouldCarryAdditionalClinicalAttributes_besideTheExportedPhysicianName()throws Exception {for (var entry:List.of(Map.entry("cpso", "001234"), Map.entry("fax", "6135550101"), Map.entry("specialty", "Neurology"), Map.entry("note", "Important context"))){var result=family(changed(contact(9), entry.getKey(), entry.getValue())); assertThat(result.representable()).isTrue(); assertThat(result.physicians()).hasSize(1); assertThat(CdsExportDemographicSupportingMapper.carries(result.fields().get(new CdsExportNativeFieldKeyDto("Contact", contact(9).recordKey(), entry.getKey())))).isTrue(); }}
    @Test void shouldKeepCommunicationConsentSeparate_fromDisclosureAndNameProjection()throws Exception {
        // A refusal does not suppress or hold the physician's name: the name is exported and the refusal is carried beside it.
        var result=map(List.of(changed(association(1, 9, "Family Doctor"), "consentToContact", "0")), List.of(contact(9)));
        assertThat(result.representable()).isTrue(); assertThat(result.physicians()).hasSize(1);
    }
    @Test void shouldCarryTheResponsibleProviderAndPurposeFlags_besideTheExportedPhysician()throws Exception {
        // Ticking MRP, EC or SDM on a family physician held the chart: the two elements have no place for the flag. The physician is exported and the flag is carried.
        for (String field:List.of("mrp", "ec", "sdm")){var link=changed(association(1, 9, "Family Doctor"), field, field.equals("mrp")?"1":"true"); var result=map(List.of(link), List.of(contact(9))); assertThat(result.physicians()).hasSize(1); assertCarried(result, link, field); }}
    @Test void shouldWriteTheFirstFiftyCharacters_andCarryANameLongerThanTheElement()throws Exception {
        // The column holds 100 characters and the element 50. The part is written as its first 50 code points (no code point is split) and the stored name is carried whole.
        var longName=changed(contact(9), "firstName", "😀".repeat(51)); var result=family(longName); assertCarried(result, longName, "firstName");
        assertThat(element(render(result, false), "FamilyPhysician").getElementsByTagNameNS("cds_dt", "FirstName").item(0).getTextContent()).isEqualTo("😀".repeat(50)); }
    @Test void shouldPreserveCarriageReturns_andLiteralMarkupInRepresentableNames()throws Exception {var name="A\r\nB <literal> & C"; var result=family(changed(contact(9), "firstName", name)); assertThat(result.representable()).isTrue(); assertThat(element(render(result, false), "FamilyPhysician").getElementsByTagNameNS("cds_dt", "FirstName").item(0).getTextContent()).isEqualTo(name); }
    @Test void shouldMapEveryRow_pastTheFormerRowAndByteBoundsAndStillHonourCancellation() throws Exception {
        // Before: two rows against a bound of one, or 64 bytes, stopped the job (CDS_EXPORT_PROFESSIONAL_CONTACT_RESOURCE_LIMIT).
        assertThat(mapper.map(27, List.of(association(1, 9, "Family Doctor")), List.of(contact(9)), 1, 1L<<20, ()-> {}).physicians()).hasSize(1); assertThat(mapper.map(27, List.of(association(1, 9, "Family Doctor")), List.of(contact(9)), 100, 64, ()-> {}).physicians()).hasSize(1); assertThatThrownBy(()-> mapper.map(27, List.of(), List.of(), 100, 1000, ()-> {throw new IllegalStateException("CANCELLED_PROFESSIONAL"); })).hasMessage("CANCELLED_PROFESSIONAL"); }
    @Test void shouldLatchWriterFailure_evenWhenAProducerCatchesTheReviewException()throws Exception {
        // No stored value makes the mapper ask for review any more; the writer still refuses a result that carries one.
        var mapped=family(contact(9)); var fields=new LinkedHashMap<>(mapped.fields()); var key=fields.keySet().iterator().next(); var decided=fields.get(key);
        fields.put(key, new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto(decided.presence(), io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW, "FamilyPhysician", "SYNTHETIC_REVIEW"));
        var blocked=new CdsExportProfessionalContactMapper.Result(mapped.patient(), mapped.associationRows(), mapped.contactRows(), mapped.physicians(), fields, mapped.nameUses());
        assertThat(blocked.representable()).isFalse(); assertThatThrownBy(()-> render(blocked, true)).isInstanceOf(IOException.class); }
    @Test void shouldPreserveImmutableEvidence_andProtectedDebugStrings()throws Exception {var result=family(contact(9)); assertThatThrownBy(()-> result.nameUses().clear()).isInstanceOf(UnsupportedOperationException.class); assertThat(result.toString()+result.physicians().getFirst()).doesNotContain("Élodie", "Fixture"); }
}
