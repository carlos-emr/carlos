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
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;

@Tag("unit") @Tag("cds-export")
class CdsExportDemographicPrimaryPhysicianReconcilerUnitTest {
    private CdsExportDemographicProjectionEvidenceUnitTest fixture;
    private final Map<String, String> source=new LinkedHashMap<>();
    private CdsExportCapturedTextRowDto provider;
    @BeforeEach void setup(){fixture=new CdsExportDemographicProjectionEvidenceUnitTest(); fixture.setup(); source.clear(); source.put("Names[1]/LegalName[1]/@namePurpose", "L"); source.put("Names[1]/LegalName[1]/FirstName[1]/Part[1]", "Ada"); source.put("Names[1]/LegalName[1]/FirstName[1]/PartType[1]", "GIV"); source.put("Names[1]/LegalName[1]/LastName[1]/Part[1]", "Family"); source.put("Names[1]/LegalName[1]/LastName[1]/PartType[1]", "FAMC"); source.put("DateOfBirth[1]", "1980-01-02"); source.put("Gender[1]", "F"); source.put("UniqueVendorIdSequence[1]", "27"); source.put("PersonStatusCode[1]/PersonStatusAsEnum[1]", "A"); source.put("PrimaryPhysician[1]/Name[1]/FirstName[1]", "Élodie😀"); source.put("PrimaryPhysician[1]/Name[1]/LastName[1]", "Fixture"); source.put("PrimaryPhysician[1]/OHIPPhysicianId[1]", "123456"); source.put("PrimaryPhysician[1]/PrimaryPhysicianCPSO[1]", "654321"); fixture.preparedPrimaryProvider("12"); provider=CdsExportEnrolmentHistoryUnitTest.provider("12"); }
    private CdsExportDemographicProjectionEvidenceUnitTest.Graph graph()throws Exception{return fixture.withSource(source); }
    private static String address(CdsExportDemographicProjectionEvidenceUnitTest.Graph g){return g.index().nativeParents().values().iterator().next(); }
    private CdsExportRetainedSectionWriter.Values values(){return new CdsExportRetainedSectionWriter.Values(){public void requireActive(){}public Reader text(CdsExportRetainedFieldDto f){return new StringReader(f.logicalInline()==null?f.originalInline():f.logicalInline()); }public InputStream binary(CdsExportRetainedFieldDto f){throw new AssertionError(); }}; }
    private CdsExportDemographicPrimaryPhysicianReconciler.Result reconcile()throws Exception{var g=graph(); return new CdsExportDemographicPrimaryPhysicianReconciler().reconcile(27, address(g), g.index(), g.checked(), g.fields(), g.current(), g.witness(), lookup(g), values(), ()-> {}); }
    private CdsExportPublicProviderLookupsDto lookup(CdsExportDemographicProjectionEvidenceUnitTest.Graph g){return new CdsExportPublicProviderLookupsDto(g.index().sourceManifestSha256(), provider==null?Map.of():Map.of(provider.fields().get("provider_no"), provider)); }
    private void noSource(){source.keySet().removeIf(key-> key.startsWith("PrimaryPhysician[")); }
    private void providerField(String key, String value){var fields=new LinkedHashMap<>(provider.fields()); fields.put(key, value); provider=new CdsExportCapturedTextRowDto(provider.table(), provider.recordKey(), fields); }
    private Document render(CdsExportDemographicPrimaryPhysicianReconciler.Result result)throws Exception {var g=graph(); var prepared=new CdsExportRetainedSectionWriter().prepareChanges(g.fields(), values(), result.changes(), ()-> {}); var output=new ByteArrayOutputStream(); String key="a".repeat(64), version="2026.08.0-test"; var selected=Set.of(CdsCategory.DEMOGRAPHICS); new CdsExportXmlComposer().compose(output, key, key, version, CdsLogicalProfile.ONTARIO_MD_5_2, selected, List.of(new CdsExportXmlComposer.Section(new CdsExportIdentityDto(key, CdsCategory.DEMOGRAPHICS, key), prepared.content())).iterator(), ignored-> {}, 1L<<20, ()-> {}); new CdsExportXmlValidator().validate(new ByteArrayInputStream(output.toByteArray()), "27", selected, version, CdsLogicalProfile.ONTARIO_MD_5_2, CdsExportXmlLimitsDto.defaults(), ignored-> {}, ()-> {}); var parser=DocumentBuilderFactory.newDefaultInstance(); parser.setNamespaceAware(true); return parser.newDocumentBuilder().parse(new ByteArrayInputStream(output.toByteArray())); }
    private static List<String> text(Document document, String local){var nodes=document.getElementsByTagNameNS("*", local); var values=new ArrayList<String>(); for (int n=0; n<nodes.getLength(); n++)values.add(nodes.item(n).getTextContent()); return values; }
    @Test void shouldPreserveAnUnchangedResolvedRelationship_withItsExactSourceNamesAndIdentifiers()throws Exception {var result=reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.sourceFields()).hasSize(4); assertThat(result.fields()).hasSize(7); assertThat(result.changes()).isEmpty(); assertThat(text(render(result), "PrimaryPhysicianCPSO")).containsExactly("654321"); }
    @Test void shouldReplaceTheWholePrimaryRelationship_afterAnExplicitNativeProviderChange()throws Exception {fixture.currentField("provider_no", "34"); provider=CdsExportEnrolmentHistoryUnitTest.provider("34"); providerField("first_name", "New"); providerField("last_name", "Doctor"); providerField("ohip_no", "000123"); providerField("practitionerNo", "000456"); var result=reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.changes()).hasSize(1); assertThat(result.changes().getFirst().expectedPriorFields()).hasSize(4); var xml=render(result); assertThat(text(xml, "OHIPPhysicianId")).containsExactly("000123"); assertThat(text(xml, "PrimaryPhysicianCPSO")).containsExactly("000456"); assertThat(text(xml, "FirstName")).contains("New"); }
    @Test void shouldClearTheWholePrimaryElement_whenAProjectedNativeRelationshipIsRemoved()throws Exception {for (String absent:Arrays.asList(null, "")){fixture.currentField("provider_no", absent); provider=null; var result=reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.changes()).hasSize(1); assertThat(text(render(result), "PrimaryPhysician")).isEmpty(); }}
    @Test void shouldRetainUnresolvedSourcePhysicianDetails_withoutInventingAnImportOperatorRelationship()throws Exception {fixture.preparedPrimaryProvider(null); provider=null; var result=reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.fields()).hasSize(1); assertThat(result.changes()).isEmpty(); assertThat(text(render(result), "OHIPPhysicianId")).containsExactly("123456"); }
    @Test void shouldRequireReview_whenUnprojectedSourceDetailsConflictWithANewNativeAssignment()throws Exception {fixture.preparedPrimaryProvider(null); fixture.currentField("provider_no", "12"); var result=reconcile(); assertThat(result.representable()).isFalse(); assertThat(result.blockers()).contains("UNPROJECTED_SOURCE_PRIMARY_PHYSICIAN_CONFLICT"); assertThat(result.proposedChanges()).isEmpty(); }
    @Test void shouldAddTheCompletePrimaryParent_forANewNativeRelationshipWithoutSourcePhysicianData()throws Exception {noSource(); fixture.preparedPrimaryProvider(null); fixture.currentField("provider_no", "12"); var result=reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.changes().getFirst().expectedPriorFields()).isEmpty(); assertThat(text(render(result), "OHIPPhysicianId")).containsExactly("123456"); }
    @Test void shouldFillMissingSourceIdentifiers_fromTheSameCapturedNativePhysician()throws Exception {source.remove("PrimaryPhysician[1]/OHIPPhysicianId[1]"); source.remove("PrimaryPhysician[1]/PrimaryPhysicianCPSO[1]"); var result=reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.changes()).hasSize(1); assertThat(result.changes().getFirst().expectedPriorFields()).hasSize(2); var xml=render(result); assertThat(text(xml, "OHIPPhysicianId")).containsExactly("123456"); assertThat(text(xml, "PrimaryPhysicianCPSO")).containsExactly("654321"); }
    @Test void shouldPreserveExplicitSourceCpso_whenTheNativeRegistrationHasAnotherAuthority()throws Exception {providerField("practitionerNoType", "CPSBC"); providerField("practitionerNo", "111111"); var result=reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.changes()).isEmpty(); assertThat(text(render(result), "PrimaryPhysicianCPSO")).containsExactly("654321"); }
    @Test void shouldRefuseConflictingPublicDetails_forAnUnchangedLocalProviderReference()throws Exception {providerField("first_name", "Changed"); var result=reconcile(); assertThat(result.representable()).isFalse(); assertThat(result.blockers()).contains("SOURCE_AND_CURRENT_PRIMARY_DETAILS_CONFLICT"); assertThatThrownBy(result::changes).hasMessage("CDS_EXPORT_PRIMARY_PHYSICIAN_REQUIRES_REVIEW"); }
    @Test void shouldRequireTheExactCapturedLookup_forANativePrimaryPhysician()throws Exception {provider=null; assertThat(reconcile().blockers()).contains("CAPTURED_PRIMARY_PHYSICIAN_REQUIRED"); }
    @Test void shouldRejectAnotherProviderRow_insteadOfBorrowingItsNamesOrIdentifiers()throws Exception {provider=CdsExportEnrolmentHistoryUnitTest.provider("34"); assertThat(reconcile().representable()).isFalse(); }
    @Test void shouldRejectAnUnprovenPreparedProjection_forAMatchedExistingChart()throws Exception {fixture.oldField("provider_no", "99"); provider=CdsExportEnrolmentHistoryUnitTest.provider("99"); var result=reconcile(); assertThat(result.representable()).isFalse(); assertThat(result.blockers()).contains("PRIMARY_PHYSICIAN_PROJECTION_NOT_PROVEN"); }
    @Test void shouldRejectUnsupportedSourceLengths_withoutTruncatingTheClinicalIdentifier() {source.put("PrimaryPhysician[1]/OHIPPhysicianId[1]", "1234567"); assertThatThrownBy(this::reconcile).hasMessage("CDS_EXPORT_PRIMARY_PHYSICIAN_SOURCE_CAPACITY_REQUIRES_REVIEW"); }
    @Test void shouldRejectAnotherSourceOccurrence_beforeMakingAParentReplacement() {source.put("PrimaryPhysician[2]/Name[1]/FirstName[1]", "Another"); assertThatThrownBy(this::reconcile).hasMessage("CDS_EXPORT_PRIMARY_PHYSICIAN_SOURCE_SCOPE_INVALID"); }
    @Test void shouldPropagateCancellation_beforeAnyProposedChangeCanEscape()throws Exception {var g=graph(); assertThatThrownBy(()-> new CdsExportDemographicPrimaryPhysicianReconciler().reconcile(27, address(g), g.index(), g.checked(), g.fields(), g.current(), g.witness(), lookup(g), values(), ()-> {throw new IllegalStateException("CANCELLED_PRIMARY"); })).hasMessage("CANCELLED_PRIMARY"); }
    @Test void shouldKeepAnAbsentPrimaryRelationship_absentWhenNeitherSourceNorNativeDataProvidesOne()throws Exception {noSource(); fixture.preparedPrimaryProvider(null); provider=null; var result=reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.sourceFields()).isEmpty(); assertThat(result.changes()).isEmpty(); assertThat(text(render(result), "PrimaryPhysician")).isEmpty(); }
    @Test void shouldRejectAPreparedProviderWithoutSourcePhysicianData_insteadOfInventingTheSourceRelationship()throws Exception {noSource(); var result=reconcile(); assertThat(result.representable()).isFalse(); assertThat(result.blockers()).contains("PREPARED_PRIMARY_PHYSICIAN_SOURCE_REQUIRED"); assertThat(result.proposedChanges()).isEmpty(); }
    @Test void shouldPreserveAnExplicitEmptySourceDetail_byRequiringReviewBeforeNativeEnrichment()throws Exception {source.put("PrimaryPhysician[1]/OHIPPhysicianId[1]", ""); var result=reconcile(); assertThat(result.representable()).isFalse(); assertThat(result.proposedChanges()).isEmpty(); }
    @Test void shouldRequireReviewBeforeReconstructingAnExplicitEmptyIdentifier_whenAnotherMissingIdentifierWouldBeAdded()throws Exception {source.put("PrimaryPhysician[1]/OHIPPhysicianId[1]", ""); providerField("ohip_no", null); source.remove("PrimaryPhysician[1]/PrimaryPhysicianCPSO[1]"); var result=reconcile(); assertThat(result.representable()).isFalse(); assertThat(result.blockers()).contains("EXPLICIT_EMPTY_SOURCE_PRIMARY_DETAILS_REQUIRE_REVIEW"); assertThat(result.proposedChanges()).isEmpty(); }

    @Test void shouldRejectAProviderLookupFromAnotherGeneration_beforeUsingAnyPrimaryPhysicianDetails()throws Exception {var g=graph(); var stale=new CdsExportPublicProviderLookupsDto("f".repeat(64), Map.of("12", provider)); assertThatThrownBy(()-> new CdsExportDemographicPrimaryPhysicianReconciler().reconcile(27, address(g), g.index(), g.checked(), g.fields(), g.current(), g.witness(), stale, values(), ()-> {})).hasMessage("CDS_EXPORT_PUBLIC_PROVIDER_LOOKUPS_GENERATION_INVALID"); }

}
