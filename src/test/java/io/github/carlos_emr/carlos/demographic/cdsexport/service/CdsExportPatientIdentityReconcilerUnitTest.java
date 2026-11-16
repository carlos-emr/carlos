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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.util.*;
import java.io.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit") @Tag("cds-export")
class CdsExportPatientIdentityReconcilerUnitTest {
    private static final String HASH = "a".repeat(64), SOURCE = "b".repeat(64);
    private final CdsExportPatientIdentityReconciler reconciler = new CdsExportPatientIdentityReconciler();
    @Test void shouldKeepTheSameNativeIdentity_acrossRegeneratedDeliveriesWithinARegisteredNamespace() {
        assertThat(reconciler.identity("registered-clinic:v1", 27)).isEqualTo(reconciler.identity("registered-clinic:v1", 27));
        assertThat(reconciler.identity("registered-clinic:v1", 27).senderPatientId()).isEqualTo("C27");
        assertThat(reconciler.identity("registered-clinic:v1", 28).senderPatientId()).isNotEqualTo("C27");
    }
    @Test void shouldRetainTheExactNamespace_asPartOfIdentityInsteadOfTruncatingAHash() {
        assertThat(reconciler.identity("clinic-a", 27)).isNotEqualTo(reconciler.identity("clinic-b", 27));
        assertThat(reconciler.identity("clinic-a", Integer.MAX_VALUE).senderPatientId()).isEqualTo("C2147483647").hasSizeLessThanOrEqualTo(20);
    }
    @Test void shouldRejectMissingOrMalformedRegistration_andNonPatientNumbers() {
        for (String namespace : Arrays.asList(null, "", " clinic", "clinic ", "clinic/path", "x".repeat(129))) {
            assertThatThrownBy(() -> reconciler.identity(namespace, 27)).hasMessage("CDS_EXPORT_PATIENT_IDENTITY_INVALID");
        }
        for (int patient : new int[] {0, -1}) assertThatThrownBy(() -> reconciler.identity("clinic", patient)).hasMessage("CDS_EXPORT_PATIENT_IDENTITY_INVALID");
    }
    @Test void shouldReplaceOnlyThePreviousVendorId_fieldAndRetainOtherHandoffs() throws Exception {
        var prior = prior(Set.of(SOURCE, "other"), Set.of(), false);
        var result = CdsExportPatientIdentityReconciler.combine(prior, SOURCE, reconciler.identity("clinic", 27));
        assertThat(result.decidedSourceFields()).containsExactly(SOURCE); assertThat(result.deferredSourceFields()).containsExactly("other");
        assertThat(result.coreChanges()).hasSize(1); var change = result.coreChanges().getFirst();
        assertThat(change.relativePath()).isEqualTo("UniqueVendorIdSequence[1]"); assertThat(change.expectedPriorFields()).containsExactly(SOURCE);
        assertThat(result.demographicComplete()).isFalse();
    }
    @Test void shouldWithholdIdentityChanges_whenAnEarlierClinicalFieldRequiresReview() throws Exception {
        var result = CdsExportPatientIdentityReconciler.combine(prior(Set.of(SOURCE), Set.of(), true), SOURCE, reconciler.identity("clinic", 27));
        assertThat(result.coreRepresentable()).isFalse(); assertThat(result.proposedChanges()).isEmpty();
        assertThatThrownBy(result::coreChanges).hasMessage("CDS_EXPORT_DEMOGRAPHIC_CORE_REQUIRES_REVIEW");
    }
    @Test void shouldRejectMissingOrDuplicateSourceOwnership_insteadOfAssumingTheLastFieldWasIdentity() {
        for (var prior : List.of(prior(Set.of("other"), Set.of(), false), prior(Set.of(SOURCE), Set.of(SOURCE), false))) {
            assertThatThrownBy(() -> CdsExportPatientIdentityReconciler.combine(prior, SOURCE, reconciler.identity("clinic", 27)))
                    .hasMessage("CDS_EXPORT_PATIENT_IDENTITY_SCOPE_INVALID");
        }
    }
    @Test void shouldProtectDebugIdentity_withoutHidingNamespaceFromTheImmutableContract() {
        var identity = reconciler.identity("registered-clinic", 27);
        assertThat(identity.senderNamespace()).isEqualTo("registered-clinic"); assertThat(identity.toString()).doesNotContain("registered-clinic", "C27");
    }
    private CdsExportDemographicRetainedCoreReconciler.Result prior(Set<String> deferred, Set<String> decided, boolean blocked) {
        return new CdsExportDemographicRetainedCoreReconciler.Result(HASH, "parent", decided, deferred, Map.of(), Set.of(), blocked ? Set.of("REVIEW") : Set.of(), List.of());
    }
    private CdsExportRetainedSectionWriter.Values values(boolean active) {
        return new CdsExportRetainedSectionWriter.Values() {
            public void requireActive() throws IOException { if (!active) throw new IOException("EXPIRED_VALUES"); }
            public Reader text(CdsExportRetainedFieldDto field) throws IOException {
                requireActive(); return new StringReader(field.logicalInline() == null ? field.originalInline() : field.logicalInline());
            }
            public InputStream binary(CdsExportRetainedFieldDto field) { throw new AssertionError("No binary identity"); }
        };
    }
    private CdsExportDemographicRetainedCoreReconciler.Result graphPrior(CdsExportDemographicProjectionEvidenceUnitTest.Graph graph) {
        String address = graph.index().nativeParents().values().iterator().next();
        return new CdsExportDemographicRetainedCoreReconciler.Result(graph.index().sourceManifestSha256(), address, Set.of(),
                graph.fields().stream().map(CdsExportRetainedFieldDto::id).collect(java.util.stream.Collectors.toSet()), Map.of(), Set.of(), Set.of(), List.of());
    }
    @Test void shouldBindThePreviousIdToItsCapturedImportedPatient_beforeReplacingIt() throws Exception {
        var fixture = new CdsExportDemographicProjectionEvidenceUnitTest(); fixture.setup();
        var graph = fixture.withSource(Map.of("UniqueVendorIdSequence[1]", "old27"));
        var result = reconciler.reconcile(graphPrior(graph), 27, "clinic", graph.index(), graph.checked(), graph.fields(), values(true), () -> {});
        assertThat(result.deferredSourceFields()).isEmpty(); assertThat(result.coreChanges()).hasSize(1);
        assertThat(result.coreChanges().getFirst().expectedPriorFields()).containsExactly(graph.fields().getFirst().id());
    }
    @Test void shouldRejectADifferentPreviousSenderId_evenWithAValidFieldReceipt() throws Exception {
        var fixture = new CdsExportDemographicProjectionEvidenceUnitTest(); fixture.setup();
        var graph = fixture.withSource(Map.of("UniqueVendorIdSequence[1]", "different"));
        assertThatThrownBy(() -> reconciler.reconcile(graphPrior(graph), 27, "clinic", graph.index(), graph.checked(), graph.fields(), values(true), () -> {}))
                .hasMessage("CDS_EXPORT_PATIENT_IDENTITY_SCOPE_INVALID");
    }
    @Test void shouldRefuseExpiredValues_beforeReturningAnIdentityDecision() throws Exception {
        var fixture = new CdsExportDemographicProjectionEvidenceUnitTest(); fixture.setup();
        var graph = fixture.withSource(Map.of("UniqueVendorIdSequence[1]", "old27"));
        assertThatThrownBy(() -> reconciler.reconcile(graphPrior(graph), 27, "clinic", graph.index(), graph.checked(), graph.fields(), values(false), () -> {}))
                .hasMessage("EXPIRED_VALUES");
    }
    @Test void shouldRequireTheExactIdentityField_insteadOfConsumingAnotherDeferredSource() throws Exception {
        var fixture = new CdsExportDemographicProjectionEvidenceUnitTest(); fixture.setup();
        var graph = fixture.withSource(Map.of("ChartNumber[1]", "C27"));
        assertThatThrownBy(() -> reconciler.reconcile(graphPrior(graph), 27, "clinic", graph.index(), graph.checked(), graph.fields(), values(true), () -> {}))
                .hasMessage("CDS_EXPORT_PATIENT_IDENTITY_SCOPE_INVALID");
    }

    @Test void shouldApplyOnlyXmlTokenWhitespace_whenBindingThePreviousVendorId() throws Exception {
        var fixture = new CdsExportDemographicProjectionEvidenceUnitTest(); fixture.setup();
        var graph = fixture.withSource(Map.of("UniqueVendorIdSequence[1]", " \told27\r\n "));
        var result = reconciler.reconcile(graphPrior(graph), 27, "clinic", graph.index(), graph.checked(), graph.fields(), values(true), () -> {});
        assertThat(result.coreChanges()).hasSize(1);
    }
    @Test void shouldKeepUnicodeWhitespaceSignificant_insteadOfTrimmingAnUnrelatedIdentity() throws Exception {
        var fixture = new CdsExportDemographicProjectionEvidenceUnitTest(); fixture.setup();
        var graph = fixture.withSource(Map.of("UniqueVendorIdSequence[1]", "\u2003old27\u2003"));
        assertThatThrownBy(() -> reconciler.reconcile(graphPrior(graph), 27, "clinic", graph.index(), graph.checked(), graph.fields(), values(true), () -> {}))
                .hasMessage("CDS_EXPORT_PATIENT_IDENTITY_SCOPE_INVALID");
    }
    @Test void shouldPropagateCancellation_afterTheCapturedIdentityWasRead() throws Exception {
        var fixture = new CdsExportDemographicProjectionEvidenceUnitTest(); fixture.setup();
        var graph = fixture.withSource(Map.of("UniqueVendorIdSequence[1]", "old27")); var closed = new java.util.concurrent.atomic.AtomicBoolean();
        var scoped = new CdsExportRetainedSectionWriter.Values() {
            public Reader text(CdsExportRetainedFieldDto field) {
                return new StringReader(field.originalInline()) { @Override public void close() { closed.set(true); super.close(); } };
            }
            public InputStream binary(CdsExportRetainedFieldDto field) { throw new AssertionError("No binary identity"); }
        };
        assertThatThrownBy(() -> reconciler.reconcile(graphPrior(graph), 27, "clinic", graph.index(), graph.checked(), graph.fields(), scoped,
                () -> { if (closed.get()) throw new java.util.concurrent.CancellationException("CANCELLED"); }))
                .isInstanceOf(java.util.concurrent.CancellationException.class);
        assertThat(closed.get()).isTrue();
    }
    @Test void shouldRejectAnOutOfRangeNativeNumber_inTheImmutableIdentityCarrier() {
        assertThatThrownBy(() -> new CdsExportPatientIdentityReconciler.Identity("clinic", "C9999999999"))
                .hasMessage("CDS_EXPORT_PATIENT_IDENTITY_INVALID");
    }

}
