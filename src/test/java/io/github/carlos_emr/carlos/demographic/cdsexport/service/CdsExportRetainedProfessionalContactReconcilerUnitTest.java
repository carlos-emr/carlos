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
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit") @Tag("cds-export")
class CdsExportRetainedProfessionalContactReconcilerUnitTest {
    private CdsExportRetainedProfessionalContactBindingUnitTest fixture; private final List<CdsExportCapturedTextRowDto> rows=new ArrayList<>();
    @BeforeEach void setup(){fixture=new CdsExportRetainedProfessionalContactBindingUnitTest(); fixture.setup(); rows.clear(); rows.addAll(fixture.originals); }
    private CdsExportRetainedSectionWriter.Values values(){return new CdsExportRetainedSectionWriter.Values(){public void requireActive(){}public Reader text(CdsExportRetainedFieldDto f){return new StringReader(f.logicalInline()==null?f.originalInline():f.logicalInline()); }public InputStream binary(CdsExportRetainedFieldDto f){throw new AssertionError(); }}; }
    private CdsExportRetainedProfessionalContactReconciler.NativeRows nativeRows(String hash){var witnesses=rows.stream().map(row-> {var state=CdsExportRetainedProfessionalContactBindingUnitTest.state(row); return new CdsExportNativeRowReceiptDto(row.table(), state.id(), row.recordKey(), state.digest(), state); }).toList(); return new CdsExportRetainedProfessionalContactReconciler.NativeRows(hash, rows.stream().filter(row-> row.table().equals("DemographicContact")).toList(), rows.stream().filter(row-> row.table().equals("Contact")).toList(), witnesses); }
    private CdsExportRetainedReceiptVerificationDto currentChecked(CdsExportDemographicProjectionEvidenceUnitTest.Graph g){String address=g.index().nativeParents().values().iterator().next(); var units=new LinkedHashMap<>(g.checked().units()); var references=new ArrayList<CdsExportRetainedReceiptVerificationDto.CheckedReference>(); for (var ref:units.get(address)){if (!Set.of("Contact", "DemographicContact").contains(ref.expected().table())){references.add(ref); continue; }var found=rows.stream().filter(row-> row.table().equals(ref.expected().table())&&ref.expected().id().equals(row.fields().get("id"))).toList(); if (found.isEmpty())references.add(new CdsExportRetainedReceiptVerificationDto.CheckedReference(ref.expected(), null, CdsExportRetainedReceiptVerificationDto.Reason.ROW_NOT_IN_CAPTURE)); else {var original=fixture.originals.stream().filter(row-> row.table().equals(ref.expected().table())&&ref.expected().id().equals(row.fields().get("id"))).findFirst().orElseThrow(); var state=CdsExportRetainedProfessionalContactBindingUnitTest.state(found.getFirst()); references.add(new CdsExportRetainedReceiptVerificationDto.CheckedReference(ref.expected(), state.digest(), CdsExportRetainedReceiptVerificationDto.Reason.RECEIPT_FIELD_CODEC_UNQUALIFIED, CdsNativeRowComparison.compare(CdsExportRetainedProfessionalContactBindingUnitTest.state(original), state))); }}units.put(address, references); return new CdsExportRetainedReceiptVerificationDto(g.checked().sourceManifestSha256(), units, g.checked().unchangedUnits(), g.checked().reviewUnits()); }
    private CdsExportRetainedProfessionalContactReconciler.Result reconcile()throws Exception {var g=fixture.graph(); return reconcile(g, nativeRows(g.index().sourceManifestSha256()), currentChecked(g), ()-> {}); }
    private CdsExportRetainedProfessionalContactReconciler.Result reconcile(CdsExportDemographicProjectionEvidenceUnitTest.Graph g, CdsExportRetainedProfessionalContactReconciler.NativeRows nativeRows, CdsExportRetainedReceiptVerificationDto checked, Runnable checkpoint)throws Exception {return new CdsExportRetainedProfessionalContactReconciler().reconcile(27, g.index().nativeParents().values().iterator().next(), g.index(), checked, g.fields(), g.current(), g.witness(), nativeRows, 100, 1L<<20, values(), checkpoint); }
    private void change(String table, String column, String value){for (int n=0; n<rows.size(); n++)if (rows.get(n).table().equals(table)){rows.set(n, CdsExportProfessionalContactMapperUnitTest.changed(rows.get(n), column, value)); return; }throw new AssertionError(); }
    @Test void shouldRetainAnUnchangedProjectedFamilyPhysician_withAllSourceAndNativeFieldsAccounted()throws Exception {var result=reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.sourceFields()).hasSize(2); assertThat(result.fields()).hasSize(40); assertThat(result.changes()).isEmpty(); assertThat(result.deferredNativeFields()).isEmpty(); }
    @Test void shouldReplaceTheWholeSourceRelationship_afterAProvenCurrentNameEdit()throws Exception {change("Contact", "firstName", "Updated & Name"); var result=reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.changes()).hasSize(1); assertThat(result.changes().getFirst().relativePath()).isEqualTo("FamilyPhysician[1]"); assertThat(result.changes().getFirst().expectedPriorFields()).hasSize(2); }
    @Test void shouldPreserveUnprojectedSourceNames_whenNoCurrentNativeRelationshipConflicts()throws Exception {fixture.originals.clear(); rows.clear(); var result=reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.sourceFields()).hasSize(2); assertThat(result.changes()).isEmpty(); }
    @Test void shouldRequireReview_whenANewNativeRelationshipWouldOverwriteUnprojectedSourceDetails()throws Exception {fixture.originals.clear(); var result=reconcile(); assertThat(result.representable()).isFalse(); assertThat(result.blockers()).contains("UNPROJECTED_SOURCE_PROFESSIONAL_RELATIONSHIP_CONFLICT"); assertThat(result.proposedChanges()).isEmpty(); }
    @Test void shouldAddANativeRelationship_whenTheOriginalSourceHasNoSuchPhysician()throws Exception {fixture.source.keySet().removeIf(key-> key.startsWith("FamilyPhysician[")); fixture.fixture.preparedRelations(null, null); fixture.originals.clear(); var result=reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.sourceFields()).isEmpty(); assertThat(result.changes()).hasSize(1); assertThat(result.changes().getFirst().expectedPriorFields()).isEmpty(); }
    @Test void shouldRequireReviewForAMissingOriginalAssociation_withoutPretendingItIsAnEmptySource()throws Exception {rows.removeIf(row-> row.table().equals("DemographicContact")); var result=reconcile(); assertThat(result.representable()).isFalse(); assertThat(result.proposedChanges()).isEmpty(); }
    @Test void shouldRequireReviewForDeletedOrInactiveRelationships_untilDeletionAndHistoryAreExplicitlyResolved()throws Exception {for (String field:List.of("deleted", "active")){setup(); change("DemographicContact", field, field.equals("deleted")?"1":"0"); assertThat(reconcile().representable()).isFalse(); }}
    @Test void shouldRefuseAChangedContactReference_insteadOfUsingAnEqualNameFromAnotherPerson()throws Exception {change("DemographicContact", "contactId", "10"); rows.set(0, CdsExportProfessionalContactMapperUnitTest.contact(10)); var result=reconcile(); assertThat(result.representable()).isFalse(); assertThat(result.blockers()).contains("ORIGINAL_PROFESSIONAL_CONTACT_REFERENCE_CHANGED"); }
    @Test void shouldRefuseAChangedOriginalRole_withoutSilentlyMovingItsSourceFields()throws Exception {change("DemographicContact", "role", "Referring Doctor"); var result=reconcile(); assertThat(result.representable()).isFalse(); assertThat(result.proposedChanges()).isEmpty(); }
    @Test void shouldRejectAnotherCapture_beforeCombiningItsNativeRelationshipRows()throws Exception {var g=fixture.graph(); assertThatThrownBy(()-> reconcile(g, nativeRows("f".repeat(64)), currentChecked(g), ()-> {})).hasMessage("CDS_EXPORT_PROFESSIONAL_NATIVE_GENERATION_INVALID"); }
    @Test void shouldRequireEveryCurrentWitness_beforeAnyNameCanBeEmitted()throws Exception {var g=fixture.graph(); var raw=nativeRows(g.index().sourceManifestSha256()); var missing=new CdsExportRetainedProfessionalContactReconciler.NativeRows(raw.sourceManifestSha256(), raw.associations(), raw.contacts(), List.of()); assertThatThrownBy(()-> reconcile(g, missing, currentChecked(g), ()-> {})).hasMessage("CDS_EXPORT_CAPTURED_TEXT_WITNESS_SCOPE_INVALID"); }
    @Test void shouldRejectAStaleReceiptComparison_evenWhenTheCallerSuppliesNewCurrentRows()throws Exception {change("Contact", "firstName", "Updated"); var g=fixture.graph(); assertThatThrownBy(()-> reconcile(g, nativeRows(g.index().sourceManifestSha256()), g.checked(), ()-> {})).hasMessage("CDS_EXPORT_PROFESSIONAL_CURRENT_WITNESS_SCOPE_INVALID"); }
    @Test void shouldRejectAnotherPatient_beforeDeferringOrMappingTheirAssociation()throws Exception {change("DemographicContact", "demographicNo", "28"); assertThatThrownBy(this::reconcile).hasMessage("CDS_EXPORT_PROFESSIONAL_CURRENT_WITNESS_SCOPE_INVALID"); }
    @Test void shouldKeepPersonalContactFieldsExplicitlyDeferred_forTheSiblingContactAssembler()throws Exception {var personal=CdsExportProfessionalContactMapperUnitTest.changed(CdsExportProfessionalContactMapperUnitTest.association(2, 10, "Next of Kin"), "category", "personal"); rows.add(personal); rows.add(CdsExportProfessionalContactMapperUnitTest.changed(CdsExportProfessionalContactMapperUnitTest.contact(10), "type", "Contact")); var result=reconcile(); assertThat(result.representable()).isTrue(); assertThat(result.fields()).hasSize(40); assertThat(result.deferredNativeFields()).hasSize(40); }
    @Test void shouldPropagateCancellation_beforeAnyProposedRelationshipChangeCanEscape()throws Exception {var g=fixture.graph(); assertThatThrownBy(()-> reconcile(g, nativeRows(g.index().sourceManifestSha256()), currentChecked(g), ()-> {throw new IllegalStateException("CANCELLED_RETAINED_PROFESSIONAL"); })).hasMessage("CANCELLED_RETAINED_PROFESSIONAL"); }
    @Test void shouldWithholdANameEdit_whenOtherNativeContactDetailsStillRequireReview()throws Exception {change("Contact", "firstName", "Updated"); change("Contact", "cpso", "001234"); var result=reconcile(); assertThat(result.representable()).isFalse(); assertThat(result.proposedChanges()).isEmpty(); }
}
