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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportChartCheckpointDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageMemberDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportXmlValidationDto;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A stopped attempt whose finished charts are kept for a resumed attempt: the retention sweep
 * erases every member of its real, protected staging that its checkpoint does not pin, and keeps
 * the charts' XML and attachments and the checkpoint itself; a checkpoint that does not match its
 * recorded pointer is rejected so the attempt is erased whole.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportCheckpointRetentionUnitTest {
    private final CdsExportLedgerService ledger = mock(CdsExportLedgerService.class);
    private final CdsExportConfigurationService configuration =
            mock(CdsExportConfigurationService.class);
    private final CdsExportJobDocumentDto document = mock(CdsExportJobDocumentDto.class);
    private final String run = UUID.randomUUID().toString();
    private Path base;
    private CdsExportStagingFactory stores;
    private CdsExportStagingService store;
    private CdsExportRetentionService retention;
    private Properties settings;

    /**
     * A protected store root and key in a private directory. The store refuses any ancestor that
     * another user owns or could write to: a shared temporary directory is one, and so is a
     * checkout bind-mounted from the host runner, as in the native tier, which supplies its own
     * private root ({@code cds.export.test.documentRoot}). Elsewhere the process home is used.
     */
    @BeforeEach
    void protectedStore() throws Exception {
        base =
                Files.createTempDirectory(
                        Path.of(
                                        System.getProperty(
                                                "cds.export.test.documentRoot",
                                                System.getProperty("user.home")))
                                .toAbsolutePath(),
                        "cds-export-checkpoint-",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
        Files.setPosixFilePermissions(base, PosixFilePermissions.fromString("rwx------"));
        var root = Files.createDirectory(base.resolve("store"));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        var keys = Files.createDirectory(base.resolve("keys"));
        Files.setPosixFilePermissions(keys, PosixFilePermissions.fromString("rwx------"));
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        var keyFile = keys.resolve("primary.key");
        Files.write(keyFile, key);
        Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"));
        settings = new Properties();
        settings.setProperty("cds.export.store.active", "primary");
        settings.setProperty("cds.export.masterKey.active", "primary");
        settings.setProperty("cds.export.store.primary.root", root.toString());
        settings.setProperty("cds.export.masterKeyRoot", keys.toString());
        settings.setProperty("cds.export.minimumFreeBytes", "0");
        stores = new CdsExportStagingFactory(settings, ledger);
        var binding = stores.bind();
        when(document.protocol()).thenReturn(2);
        when(document.storage()).thenReturn(binding);
        store = stores.create(new CdsExportLeaseDto(run, 1, "token", document), () -> {});
        retention = new CdsExportRetentionService(ledger, stores, configuration);
    }

    @AfterEach
    void eraseProtectedStore() throws IOException {
        // A setup that failed part way leaves nothing to close, and its own error is the report.
        if (retention != null) retention.close();
        if (base == null || !Files.exists(base)) return;
        try (var files = Files.walk(base)) {
            for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    private Set<String> members() throws IOException {
        try (var files = Files.list(base.resolve("store").resolve(run).resolve("1"))) {
            return files.map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        }
    }

    private static String file(CdsExportStagedFileDto member) {
        return member.memberId() + ".stage";
    }

    private CdsExportStagedFileDto staged(String purpose, int value) throws IOException {
        return store.stage(purpose, output -> output.write(value));
    }

    private static CdsExportClinicalPackageService.Chart chart(
            int patient, CdsExportStagedFileDto xml, List<CdsExportPackageMemberDto> attachments) {
        return new CdsExportClinicalPackageService.Chart(
                patient,
                "c".repeat(64),
                "Owned_Physician_123456/C" + patient + ".xml",
                new CdsExportPatientFileDto(
                        "Owned", "Physician", "123456", "Export", "Fixture", "C" + patient,
                        LocalDate.of(1971, 3, 4)),
                xml,
                attachments,
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

    private void kept(CdsExportChartCheckpointDto pointer) {
        when(ledger.untrimmed(16))
                .thenReturn(List.of(new CdsExportLedgerService.Untrimmed(run, 1)))
                .thenReturn(List.of());
        when(ledger.beginTrim(run, 1))
                .thenReturn(
                        new CdsExportLedgerService.Trim(
                                run, 1, UUID.randomUUID().toString(), document, pointer));
        when(ledger.expiredArtifacts(16)).thenReturn(List.of());
    }

    @Test
    void shouldEraseEveryUnpinnedMember_andKeepTheChartsTheRecordedCheckpointNames()
            throws Exception {
        var xml = staged("PATIENT_XML", 1);
        var attachment = staged("REPORT_ATTACHMENT", 2);
        var capture = staged("SOURCE_VALUES", 3);
        var selection = staged("PATIENT_SELECTION", 4);
        var later = staged("PATIENT_XML", 5);
        var persister =
                new CdsExportChartCheckpointPersister(store, run, 1, "a".repeat(64));
        persister.chart(
                27,
                true,
                "b".repeat(64),
                CdsExportClinicalPackageService.checkpointRecord(
                        chart(27, xml, List.of(new CdsExportPackageMemberDto("Reports/a.pdf", attachment)))));
        persister.held(28, "CDS_EXPORT_UNMAPPED_DATA");
        var recorded = persister.seal();
        var pointer = persister.latest();
        // A later chart sealed after the ledger last recorded the checkpoint is not pinned.
        persister.chart(
                29,
                true,
                "b".repeat(64),
                CdsExportClinicalPackageService.checkpointRecord(chart(29, later, List.of())));
        var unrecorded = persister.seal();
        kept(pointer);

        retention.sweep();

        assertThat(members())
                .containsExactlyInAnyOrder(file(recorded), file(xml), file(attachment))
                .doesNotContain(
                        file(capture), file(selection), file(later), file(unrecorded));
        verify(ledger).finishTrim(any(), org.mockito.ArgumentMatchers.eq(
                CdsExportLedgerService.TrimOutcome.TRIMMED));
        // What stays still reads back exactly from the pointer.
        var chain = CdsExportChartCheckpointReader.open(store, run, 1, pointer, () -> {});
        assertThat(chain.charts()).isEqualTo(1);
        assertThat(chain.held()).isEqualTo(1);
    }

    @Test
    void shouldRejectACheckpoint_thatDoesNotMatchItsRecordedPointer() throws Exception {
        var xml = staged("PATIENT_XML", 1);
        var persister =
                new CdsExportChartCheckpointPersister(store, run, 1, "a".repeat(64));
        persister.chart(
                27,
                true,
                "b".repeat(64),
                CdsExportClinicalPackageService.checkpointRecord(chart(27, xml, List.of())));
        persister.seal();
        var forged =
                new CdsExportChartCheckpointDto(
                        persister.latest().memberId(),
                        persister.latest().sha256(),
                        persister.latest().bytes(),
                        2);
        kept(forged);
        var before = members();

        retention.sweep();

        // Nothing is trimmed on the strength of an untrusted chain; the attempt is rejected, so
        // a later sweep erases it whole.
        assertThat(members()).isEqualTo(before);
        verify(ledger).finishTrim(any(), org.mockito.ArgumentMatchers.eq(
                CdsExportLedgerService.TrimOutcome.REJECTED));
        verify(ledger, never()).finishTrim(any(), org.mockito.ArgumentMatchers.eq(
                CdsExportLedgerService.TrimOutcome.TRIMMED));
    }

    @Test
    void shouldEraseALargeAttempt_overTwoSweepsWhenOneSweepsWindowEnds() throws Exception {
        // A purge once checked every member before erasing any, so a directory too large to
        // check in one sweep's 15 minutes was never erased. Each sweep now erases members until
        // its window ends, and the next one continues.
        for (int member = 0; member < 24; member++) staged("PATIENT_XML", member);
        int before = members().size();
        var claim = new CdsExportLedgerService.Cleanup(run, 1, UUID.randomUUID().toString(), document);
        when(ledger.expiredArtifacts(16)).thenReturn(List.of(run));
        when(ledger.artifactEpochs(run, 0, 64)).thenReturn(List.of(1L));
        when(ledger.beginCleanup(run, 1)).thenReturn(claim);
        // Each reading of the clock is a minute later, so a sweep's window holds 15 checks.
        var now = new java.util.concurrent.atomic.AtomicLong();
        var sweeping =
                new CdsExportRetentionService(
                        ledger,
                        stores,
                        configuration,
                        () -> now.addAndGet(java.util.concurrent.TimeUnit.MINUTES.toNanos(1)));
        var directory = base.resolve("store").resolve(run).resolve("1");

        sweeping.sweep();

        assertThat(directory).isDirectory();
        assertThat(members()).isNotEmpty().hasSizeLessThan(before);
        verify(ledger).finishCleanup(claim, false);

        sweeping.sweep();

        assertThat(directory).doesNotExist();
        verify(ledger).finishCleanup(claim, true);
    }

    @Test
    void shouldAcceptAFreeSpaceReserve_largerThanTheFormer1TiB() throws Exception {
        // The reserve was capped at 1 TiB. A larger one is accepted and applied as configured:
        // larger than this disk, it refuses the first staged member.
        settings.setProperty("cds.export.minimumFreeBytes", Long.toString(1L << 41));
        var reserved =
                new CdsExportStagingFactory(settings, ledger)
                        .openExisting(run, 1, document.storage(), () -> {});
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> reserved.stage("PATIENT_XML", output -> output.write(1)))
                .hasMessage("CDS_EXPORT_DISK_RESERVE");
    }
}
