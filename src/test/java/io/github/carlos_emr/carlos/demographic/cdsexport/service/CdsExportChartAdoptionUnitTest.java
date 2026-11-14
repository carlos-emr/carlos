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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTableDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportChartCheckpointDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeCaptureDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNoteAccessDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageMemberDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientCaptureDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStorageBindingDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportXmlValidationDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A resumed export attempt adopts a chart an earlier attempt kept only when the patient's new
 * capture is exactly the one the chart was built from, copies it into its own store, evaluates a
 * held patient again, never adopts a chart whose build read live inputs or was made under other
 * parameters, lets the newest earlier attempt that reached a patient decide, and stops adopting
 * when an earlier attempt's store cannot give a chart back intact.
 */
@Tag("unit")
@Tag("cds-export")
@org.junit.jupiter.api.extension.ExtendWith(
        io.github.carlos_emr.carlos.test.mocks.MockInvocationReleaseExtension.class)
class CdsExportChartAdoptionUnitTest {
    private static final String PARAMETERS = "a".repeat(64);
    @TempDir Path temporary;
    private final String run = UUID.randomUUID().toString();
    private final CdsExportLedgerService ledger = mock(CdsExportLedgerService.class);
    private final CdsExportStagingFactory stores = mock(CdsExportStagingFactory.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportStorageBindingDto binding =
            new CdsExportStorageBindingDto("primary", "primary", "b".repeat(64));
    private final Map<Long, CdsExportStagingService> attempts = new java.util.HashMap<>();
    private CdsExportStagingService current;
    private CdsExportLeaseDto lease;

    @BeforeEach
    void stores() throws IOException {
        current = store(3);
        lease = lease(3);
        when(stores.openExisting(eq(run), anyLong(), eq(binding), any()))
                .thenAnswer(call -> attempts.get(call.<Long>getArgument(1)));
    }

    private CdsExportStagingService store(long epoch) throws IOException {
        var root = Files.createDirectory(temporary.resolve("attempt-" + epoch));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        var store =
                new CdsExportStagingService(
                        root.toRealPath(),
                        run,
                        epoch,
                        new SecretKeySpec(new byte[32], "AES"),
                        0L,
                        () -> {});
        attempts.put(epoch, store);
        return store;
    }

    private CdsExportLeaseDto lease(long epoch) {
        var command =
                new CdsExportCommand(
                        "adoption-proof",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(5),
                        EnumSet.of(CdsCategory.DEMOGRAPHICS, CdsCategory.REPORTS),
                        "receiver",
                        "audit");
        return new CdsExportLeaseDto(
                run,
                epoch,
                UUID.randomUUID().toString(),
                new CdsExportJobDocumentDto(
                        2,
                        new CdsExportActorDto("agent", "999998", null, null),
                        command,
                        "c".repeat(64),
                        "sender",
                        "d".repeat(64),
                        "e".repeat(64),
                        binding));
    }

    /** A capture receipt of {@code patient} whose value member has the given digest. */
    private static CdsExportPatientCaptureDto capture(int patient, String values) {
        var notes = List.of(new CdsExportNoteAccessDto(41, patient, "10", "1"));
        var tables =
                Map.of(
                        "demographic",
                        new CdsExportCapturedTableDto(
                                Set.of("demographic_no"), 1, "a".repeat(64)),
                        "casemgmt_note",
                        new CdsExportCapturedTableDto(
                                Set.of(
                                        "note_id",
                                        "demographic_no",
                                        "program_no",
                                        "reporter_caisi_role",
                                        "locked"),
                                1,
                                "a".repeat(64),
                                List.of(),
                                notes));
        var now = Instant.parse("2026-11-14T00:00:00Z");
        return new CdsExportPatientCaptureDto(
                new CdsExportNativeCaptureDto(
                        2,
                        UUID.randomUUID().toString(),
                        3,
                        patient,
                        "f".repeat(64),
                        now,
                        now,
                        tables,
                        6,
                        12,
                        new CdsExportStagedFileDto(random(), "SOURCE_VALUES", values, 12),
                        new CdsExportStagedFileDto(random(), "SOURCE_INDEX", "9".repeat(64), 30)),
                new CdsExportStagedFileDto(random(), "SOURCE_MANIFEST", "8".repeat(64), 40));
    }

    private static String random() {
        var bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** A finished chart of {@code patient} staged in {@code store}, with one attachment. */
    private static CdsExportClinicalPackageService.Chart chart(
            CdsExportStagingService store, int patient) throws IOException {
        var xml =
                store.stage(
                        "PATIENT_XML",
                        output ->
                                output.write(
                                        ("<chart " + patient + "/>")
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var attachment = store.stage("REPORT_ATTACHMENT", output -> output.write(patient));
        return new CdsExportClinicalPackageService.Chart(
                patient,
                "7".repeat(64),
                "Owned_Physician_123456/C" + patient + ".xml",
                new CdsExportPatientFileDto(
                        "Owned", "Physician", "123456", "Export", "Fixture", "C" + patient,
                        LocalDate.of(1971, 3, 4)),
                xml,
                List.of(new CdsExportPackageMemberDto("Reports/" + patient + ".pdf", attachment)),
                new CdsExportXmlValidationDto(
                        "C" + patient,
                        "Export",
                        "Fixture",
                        "1971-03-04",
                        Map.of(CdsCategory.DEMOGRAPHICS, 1L),
                        0,
                        0,
                        0,
                        xml.plaintextSha256(),
                        xml.plaintextBytes()),
                Map.of(),
                List.of(),
                Map.of(),
                List.of());
    }

    /** One earlier attempt's kept checkpoint, as its entries describe it. */
    private final class Kept {
        private final long epoch;
        private final CdsExportChartCheckpointPersister persister;
        private final Map<Integer, CdsExportClinicalPackageService.Chart> charts =
                new java.util.HashMap<>();

        Kept(long epoch, String parameters) throws IOException {
            this.epoch = epoch;
            persister = new CdsExportChartCheckpointPersister(store(epoch), run, epoch, parameters);
        }

        Kept chart(int patient, boolean reusable, String fingerprint) throws IOException {
            var chart = CdsExportChartAdoptionUnitTest.chart(attempts.get(epoch), patient);
            charts.put(patient, chart);
            persister.chart(
                    patient,
                    reusable,
                    fingerprint,
                    CdsExportClinicalPackageService.checkpointRecord(chart));
            return this;
        }

        Kept held(int patient) throws IOException {
            persister.held(patient, "CDS_EXPORT_UNMAPPED_DATA");
            return this;
        }

        CdsExportLedgerService.KeptCheckpoint seal() throws IOException {
            persister.seal();
            return new CdsExportLedgerService.KeptCheckpoint(epoch, persister.latest());
        }
    }

    private CdsExportChartAdoptionService.Session open(
            String parameters, CdsExportLedgerService.KeptCheckpoint... kept) throws IOException {
        when(ledger.keptCheckpoints(lease)).thenReturn(List.of(kept));
        return new CdsExportChartAdoptionService(ledger, stores)
                .open(lease, current, parameters, control);
    }

    private static byte[] read(CdsExportStagingService store, CdsExportStagedFileDto member)
            throws IOException {
        try (var input = store.open(member)) {
            return input.readAllBytes();
        }
    }

    @Test
    void shouldAdoptAnUnchangedChart_byCopyingItIntoThisAttemptsOwnStore() throws Exception {
        var capture = capture(5, "1".repeat(64));
        String fingerprint = CdsExportChartFingerprints.capture(capture.capture());
        var prior = new Kept(1, PARAMETERS).chart(5, true, fingerprint);
        var kept = prior.seal();
        try (var session = open(PARAMETERS, kept)) {
            var candidate = session.candidate(5);
            assertThat(candidate).isNotNull();
            var adopted = session.adopt(candidate, fingerprint, capture, current);
            assertThat(adopted).isNotNull();
            assertThat(adopted.sourceEpoch()).isEqualTo(1);
            var original = prior.charts.get(5);
            // The same bytes, as members of this attempt: another identifier, another key.
            assertThat(adopted.chart().xml().memberId()).isNotEqualTo(original.xml().memberId());
            assertThat(adopted.chart().xml().plaintextSha256())
                    .isEqualTo(original.xml().plaintextSha256());
            assertThat(read(current, adopted.chart().xml()))
                    .isEqualTo(read(attempts.get(1L), original.xml()));
            assertThat(adopted.chart().attachments()).hasSize(1);
            assertThat(adopted.chart().attachments().getFirst().relativePath())
                    .isEqualTo("Reports/5.pdf");
            assertThat(read(current, adopted.chart().attachments().getFirst().file()))
                    .isEqualTo(new byte[] {5});
            assertThat(adopted.members())
                    .containsExactly(
                            adopted.chart().xml(),
                            adopted.chart().attachments().getFirst().file());
            // The chart's notes and capture evidence are this attempt's new capture's.
            assertThat(adopted.chart().notes())
                    .isEqualTo(capture.capture().tables().get("casemgmt_note").noteAccess());
            assertThat(adopted.chart().sourceManifestSha256())
                    .isEqualTo(capture.manifest().plaintextSha256());
            assertThat(adopted.chart().validation()).isEqualTo(original.validation());
            session.accepted();
            session.checkpoints()
                    .chart(
                            5,
                            true,
                            fingerprint,
                            CdsExportClinicalPackageService.checkpointRecord(adopted.chart()));
            session.checkpoints().seal();
            session.record(true);
            verify(ledger)
                    .recordCheckpoint(
                            lease, session.checkpoints().latest(), 1, Set.of(1L), true);
        }
    }

    @Test
    void shouldBuildTheChartAgain_whenItsNewCaptureDiffers() throws Exception {
        var unchanged = capture(5, "1".repeat(64));
        var prior =
                new Kept(1, PARAMETERS)
                        .chart(5, true, CdsExportChartFingerprints.capture(unchanged.capture()));
        try (var session = open(PARAMETERS, prior.seal())) {
            var changed = capture(5, "2".repeat(64));
            var candidate = session.candidate(5);
            assertThat(candidate).isNotNull();
            assertThat(
                            session.adopt(
                                    candidate,
                                    CdsExportChartFingerprints.capture(changed.capture()),
                                    changed,
                                    current))
                    .isNull();
            try (var files = Files.list(temporary.resolve("attempt-3"))) {
                assertThat(files).isEmpty();
            }
        }
    }

    @Test
    void shouldEvaluateAHeldPatientAgain_andNeverAdoptAChartWhoseBuildReadLiveInputs()
            throws Exception {
        var prior =
                new Kept(1, PARAMETERS)
                        .held(5)
                        .chart(6, false, "1".repeat(64))
                        .chart(7, true, "2".repeat(64));
        try (var session = open(PARAMETERS, prior.seal())) {
            assertThat(session.candidate(4)).isNull();
            assertThat(session.candidate(5)).isNull();
            assertThat(session.candidate(6)).isNull();
            assertThat(session.candidate(7)).isNotNull();
            // A patient past the checkpoint, or removed from the cohort, has nothing kept.
            assertThat(session.candidate(9)).isNull();
        }
    }

    @Test
    void shouldLetTheNewestEarlierAttemptThatReachedAPatientDecide() throws Exception {
        var oldest =
                new Kept(1, PARAMETERS).chart(3, true, "1".repeat(64)).chart(8, true, "2".repeat(64));
        var newer = new Kept(2, PARAMETERS).held(3).chart(4, true, "3".repeat(64));
        try (var session = open(PARAMETERS, newer.seal(), oldest.seal())) {
            // Attempt 2 reached patient 3 and held it: it is evaluated again, not adopted from 1.
            assertThat(session.candidate(3)).isNull();
            assertThat(session.candidate(4)).isNotNull();
            // Attempt 2 stopped before patient 8: attempt 1's chart may be adopted.
            var candidate = session.candidate(8);
            assertThat(candidate).isNotNull();
            var capture = capture(8, "4".repeat(64));
            assertThat(session.adopt(candidate, "2".repeat(64), capture, current).sourceEpoch())
                    .isEqualTo(1);
        }
    }

    @Test
    void shouldSupersedeAnEarlierAttempt_onceThisAttemptsRecordedCheckpointPassedIt()
            throws Exception {
        var prior = new Kept(1, PARAMETERS).chart(5, true, "1".repeat(64));
        try (var session = open(PARAMETERS, prior.seal())) {
            session.checkpoints().held(4, "CDS_EXPORT_UNMAPPED_DATA");
            session.checkpoints().seal();
            session.record(false);
            verify(ledger)
                    .recordCheckpoint(lease, session.checkpoints().latest(), 0, Set.of(), false);
            assertThat(session.candidate(5)).isNotNull();
            session.checkpoints().held(5, "CDS_EXPORT_UNMAPPED_DATA");
            session.checkpoints().seal();
            session.record(false);
            verify(ledger)
                    .recordCheckpoint(
                            lease, session.checkpoints().latest(), 0, Set.of(1L), false);
        }
    }

    @Test
    void shouldAdoptNothing_fromACheckpointMadeUnderOtherParameters() throws Exception {
        var prior = new Kept(1, "f".repeat(64)).chart(5, true, "1".repeat(64));
        try (var session = open(PARAMETERS, prior.seal())) {
            assertThat(session.candidate(5)).isNull();
            session.checkpoints().held(5, "CDS_EXPORT_UNMAPPED_DATA");
            session.checkpoints().seal();
            // Released only once this attempt's whole roster is decided.
            session.record(false);
            verify(ledger)
                    .recordCheckpoint(lease, session.checkpoints().latest(), 0, Set.of(), false);
            session.record(true);
            verify(ledger)
                    .recordCheckpoint(
                            lease, session.checkpoints().latest(), 0, Set.of(1L), true);
        }
        verify(ledger, never()).rejectCheckpoint(any(), anyLong());
    }

    @Test
    void shouldRejectAnUntrustedCheckpoint_andBuildEveryChart() throws Exception {
        var prior = new Kept(1, PARAMETERS).chart(5, true, "1".repeat(64));
        var kept = prior.seal();
        // The ledger's pointer names more charts than the chain holds.
        var forged =
                new CdsExportLedgerService.KeptCheckpoint(
                        1,
                        new CdsExportChartCheckpointDto(
                                kept.pointer().memberId(),
                                kept.pointer().sha256(),
                                kept.pointer().bytes(),
                                2));
        try (var session = open(PARAMETERS, forged)) {
            assertThat(session.candidate(5)).isNull();
        }
        verify(ledger).rejectCheckpoint(lease, 1);
    }

    @Test
    void shouldStopAdopting_whenAnEarlierAttemptsChartIsGone() throws Exception {
        var capture = capture(5, "1".repeat(64));
        String fingerprint = CdsExportChartFingerprints.capture(capture.capture());
        var prior =
                new Kept(1, PARAMETERS)
                        .chart(5, true, fingerprint)
                        .chart(6, true, fingerprint);
        try (var session = open(PARAMETERS, prior.seal())) {
            Files.delete(
                    temporary
                            .resolve("attempt-1")
                            .resolve(prior.charts.get(5).xml().memberId() + ".stage"));
            var candidate = session.candidate(5);
            assertThat(session.adopt(candidate, fingerprint, capture, current)).isNull();
            // Nothing half-copied is left, and nothing more is adopted by this attempt.
            try (var files = Files.list(temporary.resolve("attempt-3"))) {
                assertThat(files).isEmpty();
            }
            assertThat(session.candidate(6)).isNull();
        }
        verify(ledger).rejectCheckpoint(lease, 1);
    }

    @Test
    void shouldOpenWithoutEarlierAttempts_whenTheJobKeptNone() throws Exception {
        try (var session = open(PARAMETERS)) {
            assertThat(session.candidate(5)).isNull();
            assertThat(session.adopted()).isZero();
        }
        try (var alone =
                CdsExportChartAdoptionService.withoutPriors(lease, ledger, current, PARAMETERS)) {
            assertThat(alone.candidate(5)).isNull();
            assertThat(alone.checkpoints().pending()).isFalse();
        }
    }
}
