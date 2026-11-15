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
class CdsExportDemographicCareAssemblerUnitTest {
    private static final String HASH = "a".repeat(64), ADDRESS = "parent";
    private static final CdsExportNativeFieldKeyDto PROFESSIONAL = new CdsExportNativeFieldKeyDto("Contact", "0000000009", "firstName"),
            PERSONAL = new CdsExportNativeFieldKeyDto("Contact", "0000000010", "firstName"), PHARMACY = new CdsExportNativeFieldKeyDto("pharmacyInfo", "0000000011", "name"),
            DEFERRED = new CdsExportNativeFieldKeyDto("demographic", "0000000027", "alias");
    private static final CdsExportFieldAssessmentDto MAPPED = new CdsExportFieldAssessmentDto(Presence.VALUE, Outcome.MAPPED, "fixture", "EXACT_FIXTURE");
    private CdsExportDemographicRetainedCoreReconciler.Result core;
    private CdsExportRetainedProfessionalContactReconciler.Result professional;
    private CdsExportContactRetainedAssembler.Result personal;
    private CdsExportRetainedPharmacyReconciler.Result pharmacy;
    private static CdsExportRetainedSectionWriter.Change change(String path) { return new CdsExportRetainedSectionWriter.Change(path, Set.of(), xml -> xml.text("fixture")); }
    @BeforeEach void setup() {
        core = new CdsExportDemographicRetainedCoreReconciler.Result(HASH, ADDRESS, Set.of("core"), Set.of("professional", "personal", "pharmacy", "remaining"), Map.of(), Set.of(PROFESSIONAL, PERSONAL, PHARMACY, DEFERRED), Set.of(), List.of(change("DateOfBirth[1]")));
        professional = new CdsExportRetainedProfessionalContactReconciler.Result(HASH, ADDRESS, Set.of("professional"), Map.of(PROFESSIONAL, MAPPED), Set.of(PERSONAL),
                Map.of(PROFESSIONAL, Set.of(CdsExportProfessionalContactMapper.Role.FAMILY, CdsExportProfessionalContactMapper.Role.REFERRING)), Set.of(), List.of(change("FamilyPhysician[1]")));
        personal = new CdsExportContactRetainedAssembler.Result(HASH, ADDRESS, 1, 1, Set.of("personal"), Map.of(PERSONAL, MAPPED), List.of(change("Contact[1]")));
        pharmacy = new CdsExportRetainedPharmacyReconciler.Result(HASH, ADDRESS, Set.of("pharmacy"), Map.of(PHARMACY, MAPPED), Set.of(), List.of(change("PreferredPharmacy[1]")));
    }
    private CdsExportDemographicCareAssembler.Result combine() throws Exception { return CdsExportDemographicCareAssembler.combine(core, professional, personal, pharmacy); }
    @Test void shouldCoordinateEachSiblingFieldAndSourceId_withoutClaimingTheDeferredDemographicIsComplete() throws Exception {
        var result = combine(); assertThat(result.demographic().coreRepresentable()).isTrue(); assertThat(result.demographic().demographicComplete()).isFalse();
        assertThat(result.demographic().fields().keySet()).containsExactlyInAnyOrder(PROFESSIONAL, PERSONAL, PHARMACY);
        assertThat(result.demographic().deferredNativeFields()).containsExactly(DEFERRED); assertThat(result.demographic().deferredSourceFields()).containsExactly("remaining");
        assertThat(result.demographic().decidedSourceFields()).containsExactlyInAnyOrder("core", "professional", "personal", "pharmacy"); assertThat(result.demographic().coreChanges()).hasSize(4);
    }
    @Test void shouldRetainEveryProfessionalNameUse_whenOneContactServesBothPhysicianRoles() throws Exception {
        var result = combine(); assertThat(result.professionalNameUses().get(PROFESSIONAL)).containsExactlyInAnyOrder(CdsExportProfessionalContactMapper.Role.FAMILY, CdsExportProfessionalContactMapper.Role.REFERRING);
        assertThatThrownBy(() -> result.professionalNameUses().get(PROFESSIONAL).clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void shouldWithholdAllChanges_whenThePharmacyRequiresReview() throws Exception {
        pharmacy = new CdsExportRetainedPharmacyReconciler.Result(HASH, ADDRESS, pharmacy.sourceFields(), pharmacy.fields(), Set.of("PHARMACY_CONFLICT"), pharmacy.proposedChanges());
        var result = combine(); assertThat(result.demographic().coreRepresentable()).isFalse(); assertThat(result.demographic().proposedChanges()).isEmpty(); assertThat(result.demographic().blockers()).contains("PHARMACY_CONFLICT");
    }
    @Test void shouldWithholdAllChanges_whenTheProfessionalRelationshipRequiresReview() throws Exception {
        professional = new CdsExportRetainedProfessionalContactReconciler.Result(HASH, ADDRESS, professional.sourceFields(), professional.fields(), professional.deferredNativeFields(), professional.nameUses(), Set.of("PROFESSIONAL_CONFLICT"), professional.proposedChanges());
        assertThat(combine().demographic().proposedChanges()).isEmpty(); assertThat(combine().demographic().coreRepresentable()).isFalse();
    }
    @Test void shouldWithholdAllChanges_whenOnePersonalContactFieldStillRequiresReview() throws Exception {
        personal = new CdsExportContactRetainedAssembler.Result(HASH, ADDRESS, 1, 1, personal.sourceContactFields(), Map.of(PERSONAL, new CdsExportFieldAssessmentDto(Presence.VALUE, Outcome.REQUIRES_REVIEW, "Contact", "UNMAPPED")), personal.changes());
        assertThat(combine().demographic().proposedChanges()).isEmpty(); assertThat(combine().demographic().coreRepresentable()).isFalse();
    }
    @Test void shouldRefuseAnotherPatientCapture_beforeCombiningTheSameCounts() {
        pharmacy = new CdsExportRetainedPharmacyReconciler.Result("f".repeat(64), ADDRESS, pharmacy.sourceFields(), pharmacy.fields(), Set.of(), pharmacy.proposedChanges());
        assertThatThrownBy(this::combine).hasMessage("CDS_EXPORT_DEMOGRAPHIC_CARE_EVIDENCE_SCOPE_INVALID");
    }
    @Test void shouldRefuseAnotherRetainedParent_evenWhenItsCaptureMatches() {
        personal = new CdsExportContactRetainedAssembler.Result(HASH, "otherParent", 1, 1, personal.sourceContactFields(), personal.fields(), personal.changes());
        assertThatThrownBy(this::combine).hasMessage("CDS_EXPORT_DEMOGRAPHIC_CARE_EVIDENCE_SCOPE_INVALID");
    }
    @Test void shouldRequireExactDeferredContactFieldIdentities_insteadOfMatchingOnlyTheNumberOfFields() {
        personal = new CdsExportContactRetainedAssembler.Result(HASH, ADDRESS, 1, 1, personal.sourceContactFields(), Map.of(DEFERRED, MAPPED), personal.changes());
        assertThatThrownBy(this::combine).hasMessage("CDS_EXPORT_DEMOGRAPHIC_CARE_EVIDENCE_SCOPE_INVALID");
    }
    @Test void shouldRefuseDuplicateSourceOwnership_betweenCareSiblings() {
        pharmacy = new CdsExportRetainedPharmacyReconciler.Result(HASH, ADDRESS, Set.of("professional"), pharmacy.fields(), Set.of(), pharmacy.proposedChanges());
        assertThatThrownBy(this::combine).hasMessage("CDS_EXPORT_DEMOGRAPHIC_CARE_EVIDENCE_SCOPE_INVALID");
    }
    @Test void shouldRefuseDuplicateNativeOwnership_betweenTheCoreAndACareSibling() {
        core = new CdsExportDemographicRetainedCoreReconciler.Result(HASH, ADDRESS, core.decidedSourceFields(), core.deferredSourceFields(), Map.of(PROFESSIONAL, MAPPED), core.deferredNativeFields(), Set.of(), core.proposedChanges());
        assertThatThrownBy(this::combine).hasMessage("CDS_EXPORT_DEMOGRAPHIC_CARE_EVIDENCE_SCOPE_INVALID");
    }
    @Test void shouldProtectClinicalDebugValues_inTheCombinedResult() throws Exception { assertThat(combine().toString()).isEqualTo("CdsExportDemographicCareAssembler.Result[protected]"); }
}
