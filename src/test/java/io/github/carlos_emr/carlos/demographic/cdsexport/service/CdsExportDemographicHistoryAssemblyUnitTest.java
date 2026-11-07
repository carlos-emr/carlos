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
class CdsExportDemographicHistoryAssemblyUnitTest {
    private static final String HASH="a".repeat(64), ADDRESS="11111111-1111-4111-8111-111111111111:"+"b".repeat(64), CORE="c".repeat(64), HISTORY="d".repeat(64);
    private static final CdsExportNativeFieldKeyDto EMAIL=new CdsExportNativeFieldKeyDto("demographic", "0000000027", "email"), ROSTER=new CdsExportNativeFieldKeyDto("demographic", "0000000027", "roster_status"), ARCHIVE=new CdsExportNativeFieldKeyDto("demographicArchive", "09223372036854775809", "first_name");
    private final CdsExportRetainedSectionWriter.Change coreChange=new CdsExportRetainedSectionWriter.Change("Email[1]", Set.of(CORE), xml-> xml.text("edited@example.test"));
    private final CdsExportRetainedSectionWriter.Change historyChange=new CdsExportRetainedSectionWriter.Change("Enrolment[1]/EnrolmentHistory[3]", Set.of(), xml-> {});
    private CdsExportDemographicRetainedCoreReconciler.Result core(boolean review){return new CdsExportDemographicRetainedCoreReconciler.Result(HASH, ADDRESS, Set.of(CORE), Set.of(HISTORY), Map.of(EMAIL, new CdsExportFieldAssessmentDto(Presence.VALUE, review?Outcome.REQUIRES_REVIEW:Outcome.MAPPED, "Email", "TEST_CURRENT_EMAIL")), Set.of(ROSTER), Set.of(), List.of(coreChange)); }
    private CdsExportRetainedEnrolmentReconciler.Result history(boolean review){return new CdsExportRetainedEnrolmentReconciler.Result(HASH, ADDRESS, Set.of(HISTORY), Map.of(ROSTER, new CdsExportFieldAssessmentDto(Presence.VALUE, Outcome.MAPPED, "Enrolment", "TEST_CURRENT_ENROLMENT")), Set.of(ARCHIVE), review?Set.of("ENROLMENT_REQUIRES_REVIEW"):Set.of(), List.of(historyChange), 2, 1); }
    @Test void shouldComposeDisjointEdits_andKeepUnfinishedArchiveCoverageExplicit()throws Exception {var result=CdsExportDemographicRetainedCoreReconciler.withHistory(core(false), history(false)); assertThat(result.coreRepresentable()).isTrue(); assertThat(result.demographicComplete()).isFalse(); assertThat(result.coreChanges()).containsExactly(coreChange, historyChange); assertThat(result.decidedSourceFields()).containsExactlyInAnyOrder(CORE, HISTORY); assertThat(result.deferredSourceFields()).isEmpty(); assertThat(result.fields()).containsOnlyKeys(EMAIL, ROSTER); assertThat(result.deferredNativeFields()).containsExactly(ARCHIVE); }
    @Test void shouldWithholdEveryProposedEdit_whenEitherComponentRequiresReview()throws Exception {for (var pair:List.of(Map.entry(core(true), history(false)), Map.entry(core(false), history(true)))){var result=CdsExportDemographicRetainedCoreReconciler.withHistory(pair.getKey(), pair.getValue()); assertThat(result.coreRepresentable()).isFalse(); assertThat(result.proposedChanges()).isEmpty(); assertThatThrownBy(result::coreChanges).isInstanceOf(java.io.IOException.class); }}
    @Test void shouldRejectAnotherGeneration_beforeMergingAnyFieldDispositions() {var h=history(false); var other=new CdsExportRetainedEnrolmentReconciler.Result("e".repeat(64), h.unitAddress(), h.sourceFields(), h.fields(), h.deferredNativeFields(), h.blockers(), h.proposedChanges(), h.retainedHistories(), h.additionalHistories()); assertThatThrownBy(()-> CdsExportDemographicRetainedCoreReconciler.withHistory(core(false), other)).hasMessage("CDS_EXPORT_DEMOGRAPHIC_HISTORY_EVIDENCE_OVERLAP"); }
    @Test void shouldRejectSharedNativeOrUnexpectedSourceOwnership_insteadOfLettingOneMapperOverwriteAnother() {var h=history(false); var overlapping=new CdsExportRetainedEnrolmentReconciler.Result(HASH, ADDRESS, h.sourceFields(), Map.of(EMAIL, h.fields().get(ROSTER)), h.deferredNativeFields(), h.blockers(), h.proposedChanges(), 2, 1); assertThatThrownBy(()-> CdsExportDemographicRetainedCoreReconciler.withHistory(core(false), overlapping)).hasMessage("CDS_EXPORT_DEMOGRAPHIC_HISTORY_EVIDENCE_OVERLAP"); var sourceOverlap=new CdsExportRetainedEnrolmentReconciler.Result(HASH, ADDRESS, Set.of(CORE), h.fields(), h.deferredNativeFields(), h.blockers(), h.proposedChanges(), 2, 1); assertThatThrownBy(()-> CdsExportDemographicRetainedCoreReconciler.withHistory(core(false), sourceOverlap)).hasMessage("CDS_EXPORT_DEMOGRAPHIC_HISTORY_EVIDENCE_OVERLAP"); }
}
