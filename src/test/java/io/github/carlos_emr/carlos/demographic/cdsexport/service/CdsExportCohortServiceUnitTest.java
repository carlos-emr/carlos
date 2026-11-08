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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportSelectionDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStorageBindingDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.crypto.spec.SecretKeySpec;

@Tag("unit") @Tag("cds-export")
class CdsExportCohortServiceUnitTest {
    @TempDir Path root;
    private final String run = UUID.randomUUID().toString();
    private CdsExportLedgerService ledger;
    private CdsExportCohortService cohorts;
    private CdsExportStagingService store;
    private CdsExportLeaseDto lease;
    private final CdsExportRunControl control = new CdsExportRunControl() {
        @Override public void checkpoint() { }
        @Override public void captureCheckpoint() { throw new AssertionError("Cohort verification cannot open a source transaction"); }
        @Override public void authorizePatient(int patient) { }
        @Override public void authorizeCategory(int patient, CdsCategory category, boolean sensitive) { }
    };
    @BeforeEach void prepare() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        ledger = mock(CdsExportLedgerService.class);
        lease = lease(CdsExportCommand.Selection.SELECTED_PATIENTS, List.of(), List.of(27, 28));
        store = new CdsExportStagingService(root, run, 1, new SecretKeySpec(new byte[32], "AES"), 8L << 20, 0, () -> {});
        var factory = new CdsExportStagingFactory(new Properties(), ledger) {
            @Override public CdsExportStagingService openExisting(String id, long epoch, CdsExportStorageBindingDto binding, Runnable probe) {
                assertThat(id).isEqualTo(run); assertThat(epoch).isEqualTo(1); return store;
            }
        };
        cohorts = new CdsExportCohortService(factory, ledger);
    }
    @Test void shouldIssueOnlyTheExactSealedPatientSequence_andExpireEachScopeOnAdvanceOrClose() throws Exception {
        var selected = selection(row(27, "100") + row(28, null), 2);
        var sealed = cohorts.prepare(lease, selected, control); when(ledger.cohort(lease)).thenReturn(sealed.member());
        assertThat(sealed.patients()).isEqualTo(2); assertThat(sealed.toString()).doesNotContain("27");
        try (var cursor = cohorts.open(sealed, control)) {
            var first = cursor.next(); first.require(lease); assertThat(first.patient()).isEqualTo(27);
            assertThat(first.selection().physicianNo()).isEqualTo("100");
            assertThat(first.cohortSha256()).isEqualTo(sealed.member().plaintextSha256());
            var second = cursor.next(); second.require(lease); assertThat(second.patient()).isEqualTo(28);
            assertThatThrownBy(() -> first.require(lease)).hasMessage("CDS_EXPORT_PATIENT_SCOPE_EXPIRED");
            assertThat(cursor.next()).isNull(); assertThat(cursor.complete()).isTrue();
            assertThatThrownBy(() -> second.require(lease)).hasMessage("CDS_EXPORT_PATIENT_SCOPE_EXPIRED");
        }
        try (var restored = cohorts.open(lease, control)) {
            assertThat(restored.next().patient()).isEqualTo(27); assertThat(restored.next().patient()).isEqualTo(28);
            assertThat(restored.next()).isNull(); assertThat(restored.complete()).isTrue();
        }
    }
    @Test void shouldNeverTreatAPartialClosedCursor_asACompleteSelection() throws Exception {
        var sealed = cohorts.prepare(lease, selection(row(27, "100") + row(28, "100"), 2), control);
        when(ledger.cohort(lease)).thenReturn(sealed.member());
        var cursor = cohorts.open(sealed, control); var patient = cursor.next(); cursor.close();
        assertThat(cursor.complete()).isFalse();
        assertThatThrownBy(() -> patient.require(lease)).hasMessage("CDS_EXPORT_PATIENT_SCOPE_EXPIRED");
        assertThatThrownBy(cursor::next).hasMessage("CDS_EXPORT_COHORT_CURSOR_CLOSED");
    }
    @Test void shouldRejectMissingDuplicateUnorderedExtraAndSubstitutedPatients_beforeIssuingAnyScope() throws Exception {
        for (String body : List.of(row(27, null), row(27, null) + row(27, null), row(28, null) + row(27, null),
                row(27, null) + row(29, null), row(27, null) + row(28, null) + row(29, null))) {
            var file = selection(body, 2); long before = files();
            assertThatThrownBy(() -> cohorts.prepare(lease, file, control)).hasMessage("CDS_EXPORT_COHORT_INVENTORY_INVALID");
            assertThat(files()).isEqualTo(before);
        }
    }
    @Test void shouldRejectMalformedJsonAndCoercions_insteadOfNormalizingTheSelection() throws Exception {
        for (String bad : List.of(
                "{\"demographicNo\":\"27\",\"physicianNo\":null}\n",
                "{\"demographicNo\":27.0,\"physicianNo\":null}\n",
                "{\"demographicNo\":null,\"physicianNo\":null}\n",
                "{\"demographicNo\":27,\"physicianNo\":27}\n",
                "{\"demographicNo\":27}\n",
                "{\"demographicNo\":27,\"physicianNo\":null,\"extra\":true}\n",
                "{\"demographicNo\":27,\"demographicNo\":27,\"physicianNo\":null}\n",
                "{\"demographicNo\":27,\"physicianNo\":\"\\ud800\"}\n",
                row(27, null).stripTrailing(), "\n", "{} {}\n")) {
            var file = selection(bad + row(28, null), 2); long before = files();
            assertThatThrownBy(() -> cohorts.prepare(lease, file, control)).isInstanceOf(IOException.class);
            assertThat(files()).isEqualTo(before);
        }
    }
    @Test void shouldBindRosterMembership_toTheRequestedPhysicians_withoutUsingCurrentRosterDiscovery() throws Exception {
        lease = lease(CdsExportCommand.Selection.PHYSICIAN_ROSTER, List.of("100"), List.of());
        var valid = cohorts.prepare(lease, selection(row(27, "100") + row(28, "100"), 2), control);
        when(ledger.cohort(lease)).thenReturn(valid.member());
        try (var cursor = cohorts.open(lease, control)) {
            assertThat(cursor.next().patient()).isEqualTo(27); assertThat(cursor.next().patient()).isEqualTo(28);
            assertThat(cursor.next()).isNull(); assertThat(cursor.complete()).isTrue();
        }
        for (String provider : new String[]{null, "", "200"}) {
            var wrong = selection(row(27, provider), 1);
            assertThatThrownBy(() -> cohorts.prepare(lease, wrong, control)).hasMessage("CDS_EXPORT_COHORT_INVENTORY_INVALID");
        }
    }
    @Test void shouldRefuseAnUncommittedOrSubstitutedManifestPointer() throws Exception {
        var sealed = cohorts.prepare(lease, selection(row(27, null) + row(28, null), 2), control);
        when(ledger.cohort(lease)).thenThrow(new IllegalStateException("CDS_EXPORT_SELECTION_NOT_SEALED"));
        assertThatThrownBy(() -> cohorts.open(sealed, control)).hasMessage("CDS_EXPORT_SELECTION_NOT_SEALED");
        org.mockito.Mockito.doReturn(new CdsExportStagedFileDto("b".repeat(64), "COHORT_MANIFEST", "c".repeat(64), 1)).when(ledger).cohort(lease);
        assertThatThrownBy(() -> cohorts.open(sealed, control)).hasMessage("CDS_EXPORT_COHORT_INVENTORY_INVALID");
    }
    @Test void shouldRefuseScopesFromAnotherAttemptAndForeignThreadUse() throws Exception {
        var sealed = cohorts.prepare(lease, selection(row(27, null) + row(28, null), 2), control);
        when(ledger.cohort(lease)).thenReturn(sealed.member());
        try (var cursor = cohorts.open(sealed, control)) {
            var patient = cursor.next();
            assertThatThrownBy(() -> patient.require(new CdsExportLeaseDto(run, 2, lease.token(), lease.document())))
                    .hasMessage("CDS_EXPORT_COHORT_SCOPE_MISMATCH");
            var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            var thread = new Thread(() -> { try { cursor.next(); } catch (Throwable rejected) { failure.set(rejected); } });
            thread.start(); thread.join(); assertThat(failure.get()).hasMessage("CDS_EXPORT_COHORT_CURSOR_CLOSED");
            assertThatThrownBy(() -> patient.require(lease)).hasMessage("CDS_EXPORT_PATIENT_SCOPE_EXPIRED");
            assertThat(cursor.complete()).isFalse();
        }
    }
    @Test void shouldAuthenticateTheWholeInventory_beforePreparingASeal() throws Exception {
        var selected = selection(row(27, null) + row(28, null), 2);
        Path path = root.resolve(selected.inventory().memberId() + ".stage");
        byte[] bytes = Files.readAllBytes(path); Files.write(path, java.util.Arrays.copyOf(bytes, bytes.length - 1)); long before = files();
        assertThatThrownBy(() -> cohorts.prepare(lease, selected, control)).hasMessage("CDS_EXPORT_STAGED_TRUNCATED");
        assertThat(files()).isEqualTo(before);
    }
    @Test void shouldRejectEmptySelectionAndInvalidReadBounds_insteadOfInventingASelectionInstant() throws Exception {
        var empty = selection("", 0);
        assertThatThrownBy(() -> cohorts.prepare(lease, empty, control)).hasMessage("CDS_EXPORT_NO_PATIENTS_SELECTED");
        var start = Instant.parse("2026-11-05T10:00:00Z");
        assertThatThrownBy(() -> new CdsExportSelectionDto(start, start.minusSeconds(1), 1, empty.inventory()))
                .hasMessage("CDS_EXPORT_SELECTION_RECEIPT_INVALID");
        assertThatThrownBy(() -> new CdsExportSelectionDto(start, start.plusNanos(1), 1, empty.inventory()))
                .hasMessage("CDS_EXPORT_SELECTION_RECEIPT_INVALID");
    }
    private CdsExportSelectionDto selection(String body, long count) throws IOException {
        var file = store.stage("PATIENT_SELECTION", output -> output.write(body.getBytes(StandardCharsets.UTF_8)));
        var now = Instant.now().truncatedTo(ChronoUnit.MICROS); return new CdsExportSelectionDto(now, now, count, file);
    }
    private long files() throws IOException { try (var files = Files.list(root)) { return files.count(); } }
    private static String row(int patient, String provider) {
        return "{\"demographicNo\":" + patient + ",\"physicianNo\":" + (provider == null ? "null" : "\"" + provider + "\"") + "}\n";
    }
    private CdsExportLeaseDto lease(CdsExportCommand.Selection selection, List<String> physicians, List<Integer> patients) {
        var command = new CdsExportCommand("cohort-proof", "999998", null, CdsLogicalProfile.ONTARIO_MD_5_2,
                selection, physicians, patients, Set.of(CdsCategory.DEMOGRAPHICS), "receiver", "audit");
        return new CdsExportLeaseDto(run, 1, UUID.randomUUID().toString(), new CdsExportJobDocumentDto(2,
                new CdsExportActorDto("agent", "999998", null, null), command, "a".repeat(64), "sender", "b".repeat(64), "c".repeat(64),
                new CdsExportStorageBindingDto("primary", "primary", "d".repeat(64))));
    }
    @Test void shouldReadRetainedPatientIds_withoutRevivingAnExecutionLeaseOrCaptureCapability() throws Exception {
        var sealed = cohorts.prepare(lease, selection(row(27, "100") + row(28, null), 2), control);
        var ids = new java.util.ArrayList<Integer>();
        assertThat(cohorts.readRetained(store, run, 1, lease.document(), sealed.member(), 2, () -> {}, patient -> ids.add(patient.demographicNo()))).isEqualTo(2);
        assertThat(ids).containsExactly(27, 28); org.mockito.Mockito.verifyNoInteractions(ledger);
    }
    @Test void shouldRefuseAnotherRetainedEpochOrPatientCount_beforeReturningAnyIdentifiers() throws Exception {
        var sealed = cohorts.prepare(lease, selection(row(27, null) + row(28, null), 2), control);
        var ids = new java.util.ArrayList<Integer>();
        assertThatThrownBy(() -> cohorts.readRetained(store, run, 2, lease.document(), sealed.member(), 2, () -> {}, patient -> ids.add(patient.demographicNo())))
                .hasMessage("CDS_EXPORT_COHORT_INVENTORY_INVALID");
        assertThatThrownBy(() -> cohorts.readRetained(store, run, 1, lease.document(), sealed.member(), 3, () -> {}, patient -> ids.add(patient.demographicNo())))
                .hasMessage("CDS_EXPORT_COHORT_INVENTORY_INVALID"); assertThat(ids).isEmpty();
    }
    @Test void shouldRequireExactRetainedIntent_insteadOfRediscoveringOrSubstitutingAPatient() throws Exception {
        var sealed = cohorts.prepare(lease, selection(row(27, null) + row(28, null), 2), control);
        var other = lease(CdsExportCommand.Selection.SELECTED_PATIENTS, List.of(), List.of(27, 29));
        assertThatThrownBy(() -> cohorts.readRetained(store, run, 1, other.document(), sealed.member(), 2, () -> {}, patient -> {}))
                .hasMessage("CDS_EXPORT_COHORT_INVENTORY_INVALID");
    }
    @Test void shouldRejectAChangedRetainedSelectionTerminator_evenAfterReturningItsLastPatient() throws Exception {
        var selection = selection(row(27, null) + row(28, null), 2); var sealed = cohorts.prepare(lease, selection, control);
        Path file = root.resolve(selection.inventory().memberId() + ".stage"); byte[] bytes = Files.readAllBytes(file); bytes[bytes.length - 1] ^= 1; Files.write(file, bytes);
        assertThatThrownBy(() -> cohorts.readRetained(store, run, 1, lease.document(), sealed.member(), 2, () -> {}, patient -> {}))
                .isInstanceOf(java.io.IOException.class);
    }
    @Test void shouldPropagateRetainedPermissionFailure_withoutReturningACompletionReceipt() throws Exception {
        var sealed = cohorts.prepare(lease, selection(row(27, null) + row(28, null), 2), control);
        assertThatThrownBy(() -> cohorts.readRetained(store, run, 1, lease.document(), sealed.member(), 2, () -> {}, patient -> { throw new SecurityException("REVOKED"); }))
                .hasMessage("REVOKED");
    }
}
