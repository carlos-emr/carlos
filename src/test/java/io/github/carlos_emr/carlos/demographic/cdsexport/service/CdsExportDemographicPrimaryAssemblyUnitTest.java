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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.*;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit") @Tag("cds-export")
class CdsExportDemographicPrimaryAssemblyUnitTest {
    private static final String HASH="a".repeat(64), ADDRESS="11111111-1111-4111-8111-111111111111:"+"b".repeat(64), CORE="c".repeat(64), PRIMARY="d".repeat(64);
    private static final CdsExportNativeFieldKeyDto EMAIL=new CdsExportNativeFieldKeyDto("demographic", "0000000027", "email"), PRIMARY_ID=new CdsExportNativeFieldKeyDto("demographic", "0000000027", "provider_no"), OHIP=new CdsExportNativeFieldKeyDto("provider", "0012", "ohip_no"), CPSO=new CdsExportNativeFieldKeyDto("provider", "0012", "practitionerNo");
    private final CdsExportRetainedSectionWriter.Change coreChange=new CdsExportRetainedSectionWriter.Change("Email[1]", Set.of(CORE), xml-> xml.text("edited@example.test"));
    private final CdsExportRetainedSectionWriter.Change primaryChange=new CdsExportRetainedSectionWriter.Change("PrimaryPhysician[1]", Set.of(PRIMARY), xml-> {});
    private static CdsExportFieldAssessmentDto assessment(Outcome outcome, String path){return new CdsExportFieldAssessmentDto(Presence.VALUE, outcome, path, "EXPLICIT_TEST_USE"); }
    private CdsExportDemographicRetainedCoreReconciler.Result core(Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> lookup){var fields=new LinkedHashMap<>(lookup); fields.put(EMAIL, assessment(Outcome.MAPPED, "Email")); return new CdsExportDemographicRetainedCoreReconciler.Result(HASH, ADDRESS, Set.of(CORE), Set.of(PRIMARY), fields, Set.of(PRIMARY_ID), Set.of(), List.of(coreChange)); }
    private CdsExportDemographicPrimaryPhysicianReconciler.Result primary(Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> lookup){var fields=new LinkedHashMap<>(lookup); fields.put(PRIMARY_ID, assessment(Outcome.MAPPED, "PrimaryPhysician")); return new CdsExportDemographicPrimaryPhysicianReconciler.Result(HASH, ADDRESS, Set.of(PRIMARY), fields, Set.of(), List.of(primaryChange)); }
    @Test void shouldKeepBothProviderUses_whenOnePhysicianIsPrimaryAndEnrolled()throws Exception {var enrolled=assessment(Outcome.MAPPED, "Enrolment/EnrolmentHistory/EnrolledToPhysician/OHIPPhysicianId"); var primary=assessment(Outcome.MAPPED, "PrimaryPhysician/OHIPPhysicianId"); var result=CdsExportDemographicRetainedCoreReconciler.withPrimaryPhysician(core(Map.of(OHIP, enrolled)), primary(Map.of(OHIP, primary))); assertThat(result.coreRepresentable()).isTrue(); assertThat(result.fields()).hasSize(3); assertThat(result.providerUses().get(OHIP)).containsExactly(enrolled, primary); assertThat(result.fields().get(OHIP).outcome()).isEqualTo(Outcome.MAPPED); assertThat(result.decidedSourceFields()).containsExactlyInAnyOrder(CORE, PRIMARY); assertThat(result.deferredSourceFields()).isEmpty(); assertThat(result.deferredNativeFields()).isEmpty(); assertThat(result.coreChanges()).containsExactly(coreChange, primaryChange); }
    @Test void shouldRetainRegistrationMetadataAndClinicalCpsoUses_withoutLettingOneOverwriteTheOther()throws Exception {var metadata=assessment(Outcome.OPERATIONAL, ""); var mapped=assessment(Outcome.MAPPED, "PrimaryPhysician/PrimaryPhysicianCPSO"); var result=CdsExportDemographicRetainedCoreReconciler.withPrimaryPhysician(core(Map.of(CPSO, metadata)), primary(Map.of(CPSO, mapped))); assertThat(result.providerUses().get(CPSO)).containsExactly(metadata, mapped); assertThat(result.fields().get(CPSO).outcome()).isEqualTo(Outcome.MAPPED); }
    @Test void shouldWithholdEveryChange_whenEitherSharedProviderUseRequiresReview()throws Exception {for (boolean reviewFirst:List.of(false, true)){var first=assessment(reviewFirst?Outcome.REQUIRES_REVIEW:Outcome.MAPPED, "EnrolledToPhysician"); var second=assessment(reviewFirst?Outcome.MAPPED:Outcome.REQUIRES_REVIEW, "PrimaryPhysician"); var result=CdsExportDemographicRetainedCoreReconciler.withPrimaryPhysician(core(Map.of(OHIP, first)), primary(Map.of(OHIP, second))); assertThat(result.coreRepresentable()).isFalse(); assertThat(result.proposedChanges()).isEmpty(); assertThat(result.providerUses().get(OHIP)).containsExactly(first, second); }}
    @Test void shouldRejectConflictingPresence_forOneCapturedProviderField() {var other=new CdsExportFieldAssessmentDto(Presence.SQL_NULL, Outcome.ABSENT, "PrimaryPhysician/OHIPPhysicianId", "ABSENT_TEST"); assertThatThrownBy(()-> CdsExportDemographicRetainedCoreReconciler.withPrimaryPhysician(core(Map.of(OHIP, assessment(Outcome.MAPPED, "EnrolledToPhysician"))), primary(Map.of(OHIP, other)))).hasMessage("CDS_EXPORT_DEMOGRAPHIC_PRIMARY_EVIDENCE_OVERLAP"); }
    @Test void shouldRejectOverlappingPatientFields_evenWhenTheAssessmentsMatch() {assertThatThrownBy(()-> CdsExportDemographicRetainedCoreReconciler.withPrimaryPhysician(core(Map.of()), primary(Map.of(EMAIL, assessment(Outcome.MAPPED, "Email"))))).hasMessage("CDS_EXPORT_DEMOGRAPHIC_PRIMARY_EVIDENCE_OVERLAP"); }
    @Test void shouldRejectAnotherCapture_beforeCombiningPrimaryAndEnrolmentFacts() {var p=primary(Map.of()); var other=new CdsExportDemographicPrimaryPhysicianReconciler.Result("e".repeat(64), p.unitAddress(), p.sourceFields(), p.fields(), p.blockers(), p.proposedChanges()); assertThatThrownBy(()-> CdsExportDemographicRetainedCoreReconciler.withPrimaryPhysician(core(Map.of()), other)).hasMessage("CDS_EXPORT_DEMOGRAPHIC_PRIMARY_EVIDENCE_OVERLAP"); }
    @Test void shouldKeepProviderUseEvidenceImmutable_afterACombinedResultIsReturned()throws Exception {var result=CdsExportDemographicRetainedCoreReconciler.withPrimaryPhysician(core(Map.of(OHIP, assessment(Outcome.MAPPED, "EnrolledToPhysician"))), primary(Map.of(OHIP, assessment(Outcome.MAPPED, "PrimaryPhysician")))); assertThatThrownBy(()-> result.providerUses().clear()).isInstanceOf(UnsupportedOperationException.class); assertThatThrownBy(()-> result.providerUses().get(OHIP).clear()).isInstanceOf(UnsupportedOperationException.class); }
    @Test void shouldRejectProviderUseEvidenceThatHidesReview_insteadOfAcceptingItsSuccessfulSummary() {var result=core(Map.of(OHIP, assessment(Outcome.MAPPED, "EnrolledToPhysician"))); assertThatThrownBy(()-> new CdsExportDemographicRetainedCoreReconciler.Result(result.sourceManifestSha256(), result.unitAddress(), result.decidedSourceFields(), result.deferredSourceFields(), result.fields(), result.deferredNativeFields(), result.blockers(), result.proposedChanges(), Map.of(OHIP, List.of(assessment(Outcome.REQUIRES_REVIEW, "PrimaryPhysician"))))).hasMessage("CDS_EXPORT_DEMOGRAPHIC_PROVIDER_USES_INVALID"); }
    @Test void shouldPreserveAllPriorProviderUses_whenAnotherHistoryComponentIsAddedLater()throws Exception {
        var combined=CdsExportDemographicRetainedCoreReconciler.withPrimaryPhysician(core(Map.of(OHIP, assessment(Outcome.MAPPED, "EnrolledToPhysician"))), primary(Map.of(OHIP, assessment(Outcome.MAPPED, "PrimaryPhysician"))));
        String sourceHistory="e".repeat(64); var pending=new CdsExportDemographicRetainedCoreReconciler.Result(combined.sourceManifestSha256(), combined.unitAddress(), combined.decidedSourceFields(), Set.of(sourceHistory), combined.fields(), combined.deferredNativeFields(), combined.blockers(), combined.proposedChanges(), combined.providerUses());
        var history=new CdsExportRetainedEnrolmentReconciler.Result(HASH, ADDRESS, Set.of(sourceHistory), Map.of(), Set.of(), Set.of(), List.of(), 0, 0); var result=CdsExportDemographicRetainedCoreReconciler.withHistory(pending, history);
        assertThat(result.providerUses()).isEqualTo(combined.providerUses()); assertThat(result.providerUses().get(OHIP)).hasSize(2);
    }

}
