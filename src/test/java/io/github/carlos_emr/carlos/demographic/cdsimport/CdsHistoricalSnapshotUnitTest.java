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
package io.github.carlos_emr.carlos.demographic.cdsimport;

import io.github.carlos_emr.carlos.commn.dao.CdsImportAdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.CdsUploadDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportAdmission;
import io.github.carlos_emr.carlos.commn.model.CdsUpload;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportJobCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real protected upload bytes, chunk journal and snapshot verification; only database rows are doubled. */
@Tag("unit")
class CdsHistoricalSnapshotUnitTest {
    private static final String PRIOR = "11111111-1111-4111-8111-111111111111";
    private static final String REVIEW = "22222222-2222-4222-8222-222222222222";
    private static final long EPOCH = 9;
    @TempDir Path temporary;
    private CdsImportLedgerService ledger;
    private CdsImportConfigurationService configuration;
    private CdsUploadService uploads;
    private CdsImportJobService jobs;
    private CdsImportJobDocumentDto document;
    private CdsSnapshotDto snapshot;
    private Path original;
    private String uploadId;
    private final Map<String, CdsUpload> rows = new HashMap<>();
    private final AtomicReference<CdsImportJobCommand> accepted = new AtomicReference<>();
    private final AtomicReference<CdsImportJobDto> prior = new AtomicReference<>();
    private final CdsImportActorDto actor = new CdsImportActorDto("web-42", "1", "1", 42, null, null);

    @BeforeEach void prepare() throws Exception {
        ledger = mock(CdsImportLedgerService.class);
        Properties properties = new Properties();
        properties.setProperty("cds.import.enabled", "true");
        properties.setProperty("cds.import.source.test-source.profiles", "omd-5.2");
        properties.setProperty("cds.import.allowedRoot", temporary.resolve("input").toString());
        properties.setProperty("cds.import.stagingRoot", temporary.resolve("runs").toString());
        properties.setProperty("cds.import.uploadRoot", temporary.resolve("uploads").toString());
        properties.setProperty("cds.import.upload.maxChunkBytes", "65536");
        properties.setProperty("cds.import.minimumFreeDiskBytes", "1");
        configuration = spy(new CdsImportConfigurationService(properties));
        var dao = mock(CdsUploadDao.class); var admission = mock(CdsImportAdmissionDao.class);
        var budget = new CdsImportAdmission(); budget.setId("UPLOAD_BUDGET");
        when(admission.lock("UPLOAD_BUDGET")).thenReturn(budget);
        when(dao.get(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        when(dao.lock(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        when(dao.lockActualRun(anyString())).thenAnswer(call -> rows.values().stream()
                .filter(row -> call.getArgument(0).equals(row.getRunId())).findFirst().orElse(null));
        doAnswer(call -> { CdsUpload row = call.getArgument(0); rows.put(row.getId(), row); return null; }).when(dao).persist(any());
        doAnswer(call -> { CdsUpload row = call.getArgument(0); rows.put(row.getId(), row); return null; }).when(dao).merge(any());
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        uploads = spy(new CdsUploadService(dao, admission, configuration, manager));
        jobs = new CdsImportJobService(ledger, configuration, new CdsSnapshotService(), uploads);
        when(ledger.findSubmission(anyString(), anyString())).thenAnswer(call -> accepted.get() != null
                && accepted.get().submissionKey().equals(call.getArgument(1)) ? prior.get() : null);
        when(ledger.accept(any())).thenAnswer(call -> {
            CdsImportJobCommand request = call.getArgument(0); accepted.set(request);
            prior.set(status(PRIOR, request.sourceDigest(), request.optionsDigest(), request.rulesetDigest(), 0, false));
            return prior.get();
        });
        when(ledger.status(PRIOR)).thenAnswer(call -> prior.get());
        when(ledger.snapshotLocation(PRIOR)).thenAnswer(call -> accepted.get().snapshotLocation());
        when(ledger.status(REVIEW)).thenAnswer(call -> status(REVIEW, "a".repeat(64), "b".repeat(64), "c".repeat(64), EPOCH, false));
        Path input = Files.createDirectory(temporary.resolve("input"));
        // Several real chunk receipts prove that the historical upload manifest is not a file digest.
        original = Files.writeString(input.resolve("patient.xml"), "<OmdCds xmlns='cds'><PatientRecord>"
                + " ".repeat(140000) + "</PatientRecord></OmdCds>");
    }

    @Test void shouldVerifyCancelledHistoricalWebSource_withoutChangingItsProofOrUploadState() throws Exception {
        acceptWeb(); cancelPrior();
        assertNotEquals(prior.get().sourceDigest(), snapshot.sha256());
        var before = tree(temporary); var receipt = uploads.status(actor.operator(), uploadId); var old = prior.get();
        clearInvocations(uploads);
        assertEquals(snapshot, jobs.historicalSnapshot(REVIEW, EPOCH, PRIOR, document, () -> false));
        assertEquals(before, tree(temporary)); assertEquals(old, prior.get());
        assertEquals(receipt, uploads.status(actor.operator(), uploadId));
        verify(uploads, never()).claimForSubmission(anyString(), anyString());
        verify(uploads, never()).submitted(anyString(), anyString(), anyString(), anyBoolean());
        verify(uploads, never()).snapshotPublished(anyString(), anyString(), anyString(), anyString());
        assertFalse(Files.exists(document.command().input()));
    }

    @Test void shouldVerifyCancelledHistoricalCliSnapshot_afterOriginalSourceHasGone() throws Exception {
        var command = command(original, "cli-source");
        jobs.accept(actor, command); document = jobs.load(PRIOR); snapshot = document.snapshot();
        cancelPrior(); Files.delete(original); var before = tree(temporary);
        clearInvocations(uploads);
        assertEquals(snapshot, jobs.historicalSnapshot(REVIEW, EPOCH, PRIOR, document, () -> false));
        assertEquals(before, tree(temporary)); assertTrue(prior.get().cancelRequested());
        verifyNoInteractions(uploads);
    }

    @Test void shouldRefuseChangedHistoricalSnapshotBytes_beforeArchiving() throws Exception {
        acceptWeb(); cancelPrior();
        CdsSnapshotFileDto[] member = {null}; new CdsSnapshotService().forEachXml(snapshot, value -> member[0] = value);
        Files.writeString(member[0].path(), "changed");
        var error = assertThrows(Exception.class, () -> jobs.historicalSnapshot(REVIEW, EPOCH, PRIOR, document, () -> false));
        assertTrue(error.getMessage().contains("CDS_SNAPSHOT_MEMBER_CHANGED"));
    }

    @Test void shouldRefuseChangedHistoricalChunkReceipts_evenWhenSnapshotItselfStillVerifies() throws Exception {
        acceptWeb(); cancelPrior();
        Path journal = configuration.uploadRoot().resolve(uploadId).resolve("chunks.bin");
        byte[] bytes = Files.readAllBytes(journal); bytes[51] ^= 1; Files.write(journal, bytes);
        var error = assertThrows(CdsImportException.class,
                () -> jobs.historicalSnapshot(REVIEW, EPOCH, PRIOR, document, () -> false));
        assertEquals("UPLOADED_SOURCE_CHANGED", error.getCode());
    }

    @Test void shouldRefuseAnUploadBoundToAnotherRun_withoutRepairingHistoricalState() throws Exception {
        acceptWeb(); cancelPrior(); rows.get(uploadId).setRunId(REVIEW); var before = tree(temporary);
        var error = assertThrows(CdsImportException.class,
                () -> jobs.historicalSnapshot(REVIEW, EPOCH, PRIOR, document, () -> false));
        assertEquals("UPLOAD_RUN_ASSOCIATION_INVALID", error.getCode()); assertEquals(before, tree(temporary));
        assertEquals(REVIEW, rows.get(uploadId).getRunId());
    }

    @Test void shouldObserveCurrentReviewCancellation_withoutClearingHistoricalCancellation() throws Exception {
        acceptWeb(); cancelPrior(); var before = tree(temporary);
        var error = assertThrows(CdsImportException.class,
                () -> jobs.historicalSnapshot(REVIEW, EPOCH, PRIOR, document, () -> true));
        assertEquals("REVIEW_CANCELLED", error.getCode()); assertEquals(before, tree(temporary));
        assertTrue(prior.get().cancelRequested());
    }

    @Test void shouldClassifyAnInterruptedHistoricalRead_asReviewCancellation() throws Exception {
        acceptWeb(); cancelPrior(); var before = tree(temporary);
        try {
            Thread.currentThread().interrupt();
            var error = assertThrows(CdsImportException.class,
                    () -> jobs.historicalSnapshot(REVIEW, EPOCH, PRIOR, document, () -> false));
            assertEquals("REVIEW_CANCELLED", error.getCode());
        } finally { Thread.interrupted(); }
        assertEquals(before, tree(temporary)); assertTrue(prior.get().cancelRequested());
    }

    @Test void shouldRecheckCurrentReviewEpoch_atTheVerificationBoundary() throws Exception {
        acceptWeb(); cancelPrior();
        when(ledger.status(REVIEW)).thenReturn(status(REVIEW, "a".repeat(64), "b".repeat(64), "c".repeat(64), EPOCH, false),
                status(REVIEW, "a".repeat(64), "b".repeat(64), "c".repeat(64), EPOCH + 1, false));
        var error = assertThrows(CdsImportException.class,
                () -> jobs.historicalSnapshot(REVIEW, EPOCH, PRIOR, document, () -> false));
        assertEquals("OWNER_FENCED", error.getCode()); assertTrue(prior.get().cancelRequested());
    }

    @Test void shouldRefuseMissingPublication_withoutRecreatingHistoricalSpools() throws Exception {
        acceptWeb(); cancelPrior(); Files.delete(snapshot.root().resolve("snapshot.seal")); var before = tree(temporary);
        var error = assertThrows(CdsImportException.class,
                () -> jobs.historicalSnapshot(REVIEW, EPOCH, PRIOR, document, () -> false));
        assertEquals("HISTORICAL_SNAPSHOT_NOT_PUBLISHED", error.getCode()); assertEquals(before, tree(temporary));
    }

    @Test void shouldBoundHistoricalVerificationByTheCurrentPolicyDeadline() throws Exception {
        acceptWeb(); cancelPrior(); var v = configuration.limits();
        doReturn(new CdsInputLimitsDto(v.maxSourceBytes(), v.maxExpandedBytes(), v.maxEntries(), v.maxXmlDepth(),
                v.maxBoundedScalarCharacters(), v.maxBinaryBytes(), v.minimumFreeDiskBytes(), 1, v.maxPathCharacters()))
                .when(configuration).limits();
        var error = assertThrows(CdsImportException.class, () -> jobs.historicalSnapshot(REVIEW, EPOCH, PRIOR, document, () -> {
            java.util.concurrent.locks.LockSupport.parkNanos(5_000_000L); return false;
        }));
        assertEquals("CDS_INPUT_DEADLINE", error.getCode());
    }

    private void acceptWeb() throws Exception {
        byte[] source = Files.readAllBytes(original);
        var receipt = uploads.begin(actor.operator(), "patient.xml", source.length); uploadId = receipt.id();
        for (int offset = 0; offset < source.length; offset += 65536) {
            int length = Math.min(65536, source.length - offset);
            byte[] part = java.util.Arrays.copyOfRange(source, offset, offset + length);
            uploads.append(actor.operator(), uploadId, offset, length, hash(part), new ByteArrayInputStream(part));
        }
        receipt = uploads.finish(actor.operator(), uploadId);
        Path input = configuration.uploadRoot().resolve(uploadId).resolve("source.xml");
        jobs.accept(actor, command(input, receipt.submissionKey() + "-import"));
        document = jobs.load(PRIOR); snapshot = jobs.snapshot(PRIOR, document);
        assertEquals("RETIRED", uploads.status(actor.operator(), uploadId).state());
    }

    private CdsImportCommand command(Path input, String submission) {
        return new CdsImportCommand(input, submission, "1", null, null, "test-source", "omd-5.2", false, false, false, 0, null, false);
    }
    private void cancelPrior() {
        var old = prior.get(); prior.set(status(PRIOR, old.sourceDigest(), old.optionsDigest(), old.rulesetDigest(), old.ownerEpoch(), true));
    }
    private static CdsImportJobDto status(String id, String source, String options, String rules, long epoch, boolean cancel) {
        return new CdsImportJobDto(id, "web-42", "1", "test-source", "omd-5.2", cancel ? "CANCELLED_BEFORE_WRITE" : "REVIEW_REQUIRED",
                source, options, rules, null, 0, 0, epoch, cancel, Instant.EPOCH, Instant.EPOCH, 0, null, null);
    }
    private static String hash(byte[] value) { return CdsPaths.hex(CdsPaths.sha256().digest(value)); }
    private static Map<String, String> tree(Path root) throws Exception {
        Map<String, String> result = new HashMap<>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                result.put(root.relativize(file).toString(), hash(Files.readAllBytes(file)));
            }
        }
        return result;
    }
}
