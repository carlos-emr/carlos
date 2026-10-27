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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import io.github.carlos_emr.carlos.commn.dao.CdsImportAdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.CdsUploadDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportAdmission;
import io.github.carlos_emr.carlos.commn.model.CdsUpload;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsUploadException;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.io.ByteArrayInputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@DisplayName("Durable bounded CDS raw uploads")
class CdsUploadServiceUnitTest extends CarlosUnitTestBase {
    @TempDir Path temporary;
    private CdsUploadService service;
    private CdsUploadDao uploads;
    private CdsImportAdmission budget;
    private final Map<String,CdsUpload> stored = new HashMap<>();
    private static final String OWNER = "web-42";
    private static final int CHUNK = 65536;

    @BeforeEach void prepare() {
        stored.clear(); uploads = mock(CdsUploadDao.class);
        CdsImportAdmissionDao admission = mock(CdsImportAdmissionDao.class);
        budget = new CdsImportAdmission(); budget.setId("UPLOAD_BUDGET");
        when(admission.lock("UPLOAD_BUDGET")).thenReturn(budget);
        when(uploads.get(anyString())).thenAnswer(call -> stored.get(call.getArgument(0)));
        when(uploads.lock(anyString())).thenAnswer(call -> stored.get(call.getArgument(0)));
        when(uploads.lockActualRun(anyString())).thenAnswer(call -> stored.values().stream()
                .filter(row -> call.getArgument(0).equals(row.getRunId())).findFirst().orElse(null));
        when(uploads.reservedCount()).thenAnswer(call -> stored.values().stream().filter(row -> !row.isQuotaReleased()).count());
        doAnswer(call -> { CdsUpload row = call.getArgument(0); stored.put(row.getId(), row); return null; }).when(uploads).persist(any());
        doAnswer(call -> { CdsUpload row = call.getArgument(0); stored.put(row.getId(), row); return null; }).when(uploads).merge(any());
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        Properties properties = new Properties();
        properties.setProperty("cds.import.enabled", "true");
        properties.setProperty("cds.import.uploadRoot", temporary.resolve("uploads").toString());
        properties.setProperty("cds.import.upload.maxChunkBytes", Integer.toString(CHUNK));
        properties.setProperty("cds.import.upload.maxFileBytes", Long.toString(4L << 20));
        properties.setProperty("cds.import.upload.maxTotalBytes", Long.toString(8L << 20));
        properties.setProperty("cds.import.minimumFreeDiskBytes", "1");
        service = new CdsUploadService(uploads, admission, new CdsImportConfigurationService(properties), manager);
    }

    @Test void shouldStoreOpaqueBytesAndExactReceipts_whenChunksCommit() throws Exception {
        var receipt = service.begin(OWNER, "Synthetic Name 1971-03-04.xml", CHUNK + 7);
        byte[] first = new byte[CHUNK]; java.util.Arrays.fill(first, (byte) 7);
        service.append(OWNER, receipt.id(), 0, first.length, hash(first), new ByteArrayInputStream(first));
        byte[] last = "lastxml".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var done = service.append(OWNER, receipt.id(), CHUNK, last.length, hash(last), new ByteArrayInputStream(last));
        assertThat(done.receivedBytes()).isEqualTo(CHUNK + 7);
        assertThat(service.finish(OWNER, receipt.id()).state()).isEqualTo("COMPLETE");
        assertThat(service.chunks(OWNER, receipt.id(), 0, 100)).hasSize(2);
        var finalPage = service.chunks(OWNER, receipt.id(), 1, 100);
        assertThat(finalPage).hasSize(1);
        assertThat(finalPage.getFirst().sha256()).isEqualTo(hash(last));
        Path directory = temporary.resolve("uploads").resolve(receipt.id());
        assertThat(Files.readAllBytes(directory.resolve("source.xml"))).hasSize(CHUNK + 7);
        try (var files = Files.list(directory)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList()).doesNotContain(receipt.fileName());
        }
        assertThat(receipt.toString()).doesNotContain("Synthetic Name", "1971");
    }

    @Test void shouldReuseExactChunkReceipt_whenAcknowledgementIsRetried() throws Exception {
        byte[] bytes = "content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var upload = service.begin(OWNER, "source.xml", bytes.length);
        service.append(OWNER, upload.id(), 0, bytes.length, hash(bytes), new ByteArrayInputStream(bytes));
        var repeated = service.append(OWNER, upload.id(), 0, bytes.length, hash(bytes), new ByteArrayInputStream(bytes));
        assertThat(repeated.committedChunks()).isEqualTo(1);
        assertThat(repeated.receivedBytes()).isEqualTo(bytes.length);
        assertThat(Files.size(temporary.resolve("uploads").resolve(upload.id()).resolve("chunks.bin"))).isEqualTo(52);
    }

    @Test void shouldRefuseChangedRetry_whenOffsetAlreadyCommitted() throws Exception {
        byte[] bytes = "content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var upload = service.begin(OWNER, "source.xml", bytes.length);
        service.append(OWNER, upload.id(), 0, bytes.length, hash(bytes), new ByteArrayInputStream(bytes));
        byte[] different = "changed".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.append(OWNER, upload.id(), 0, different.length, hash(different), new ByteArrayInputStream(different)))
                .isInstanceOf(CdsUploadException.class).hasMessage("UPLOAD_RETRY_CONTENT_CONFLICT");
        assertThat(Files.readString(temporary.resolve("uploads").resolve(upload.id()).resolve("source.xml"))).isEqualTo("content");
    }

    @Test void shouldRejectBadHashWithoutProgress_whenChunkIsMalformed() throws Exception {
        byte[] bytes = "content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var upload = service.begin(OWNER, "source.xml", bytes.length);
        assertThatThrownBy(() -> service.append(OWNER, upload.id(), 0, bytes.length, "0".repeat(64), new ByteArrayInputStream(bytes)))
                .hasMessage("UPLOAD_CHUNK_HASH_MISMATCH");
        assertThat(service.status(OWNER, upload.id()).receivedBytes()).isZero();
        assertThatThrownBy(() -> service.finish(OWNER, upload.id())).hasMessage("UPLOAD_INCOMPLETE");
    }

    @Test void shouldStopAtBoundedBodyLimit_whenClientSendsExtraBytes() throws Exception {
        var upload = service.begin(OWNER, "source.xml", CHUNK);
        java.util.concurrent.atomic.AtomicLong consumed = new java.util.concurrent.atomic.AtomicLong();
        var infinite = new java.io.InputStream() {
            @Override public int read() { consumed.incrementAndGet(); return 1; }
            @Override public int read(byte[] bytes) { java.util.Arrays.fill(bytes, (byte) 1); consumed.addAndGet(bytes.length); return bytes.length; }
        };
        assertThatThrownBy(() -> service.append(OWNER, upload.id(), 0, CHUNK, "0".repeat(64), infinite))
                .hasMessage("UPLOAD_CHUNK_SIZE_MISMATCH");
        assertThat(consumed.get()).isLessThanOrEqualTo(CHUNK + 8192L);
        assertThat(service.status(OWNER, upload.id()).receivedBytes()).isZero();
    }

    @Test void shouldTruncateUncommittedTails_whenResumingKnownCheckpoint() throws Exception {
        byte[] first = new byte[CHUNK]; byte[] last = "lastxml".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var upload = service.begin(OWNER, "source.xml", CHUNK + last.length);
        service.append(OWNER, upload.id(), 0, first.length, hash(first), new ByteArrayInputStream(first));
        Path directory = temporary.resolve("uploads").resolve(upload.id());
        Files.writeString(directory.resolve("source.xml"), "orphan", StandardOpenOption.APPEND);
        Files.writeString(directory.resolve("chunks.bin"), "orphan", StandardOpenOption.APPEND);
        service.append(OWNER, upload.id(), CHUNK, last.length, hash(last), new ByteArrayInputStream(last));
        assertThat(Files.size(directory.resolve("source.xml"))).isEqualTo(CHUNK + last.length);
        assertThat(Files.size(directory.resolve("chunks.bin"))).isEqualTo(104);
    }

    @Test void shouldNotFabricateMissingCommittedBytes_whenFileIsShorterThanCheckpoint() throws Exception {
        byte[] first = new byte[CHUNK];
        var upload = service.begin(OWNER, "source.xml", CHUNK + 1);
        service.append(OWNER, upload.id(), 0, first.length, hash(first), new ByteArrayInputStream(first));
        Files.write(temporary.resolve("uploads").resolve(upload.id()).resolve("source.xml"), new byte[0]);
        assertThatThrownBy(() -> service.append(OWNER, upload.id(), CHUNK, 1, hash(new byte[]{1}), new ByteArrayInputStream(new byte[]{1})))
                .hasMessage("UPLOAD_STORAGE_INCOMPLETE");
    }

    @Test void shouldFenceAbandonment_whenAcceptanceIsUnknownOrCommitted() throws Exception {
        byte[] bytes = "content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var upload = service.begin(OWNER, "source.xml", bytes.length);
        service.append(OWNER, upload.id(), 0, bytes.length, hash(bytes), new ByteArrayInputStream(bytes)); service.finish(OWNER, upload.id());
        Path input = service.claimForSubmission(OWNER, upload.id());
        assertThatThrownBy(() -> service.abandon(OWNER, upload.id())).hasMessage("UPLOAD_ACCEPTANCE_RECONCILIATION_REQUIRED");
        assertThat(Files.readString(input)).isEqualTo("content");
        String preview = UUID.randomUUID().toString(), actual = UUID.randomUUID().toString();
        String previewKey = upload.submissionKey() + "-dry-run", actualKey = upload.submissionKey() + "-import";
        service.submitted(OWNER, upload.id(), preview, true);
        var previewReceipt = service.status(OWNER, upload.id());
        assertThat(previewReceipt.state()).isEqualTo("SUBMITTED_DRY_RUN");
        assertThat(previewReceipt.runId()).isNull();
        assertThat(previewReceipt.dryRunId()).isEqualTo(preview);
        assertThat(service.sourceForAcceptedRun(OWNER, upload.id(), preview, previewKey, true)).isEqualTo(input);
        assertThat(service.status(OWNER, upload.id()).state()).isEqualTo("SUBMITTED_DRY_RUN");
        assertThatThrownBy(() -> service.sourceForAcceptedRun(OWNER, upload.id(), preview, actualKey, false))
                .hasMessage("UPLOAD_RUN_ASSOCIATION_INVALID");
        assertThatThrownBy(() -> service.sourceForAcceptedRun(OWNER, upload.id(), actual, previewKey, true))
                .hasMessage("UPLOAD_RUN_ASSOCIATION_INVALID");
        assertThatThrownBy(() -> service.sourceForAcceptedRun("web-99", upload.id(), preview, previewKey, true))
                .hasMessage("UPLOAD_NOT_FOUND");
        // Legacy preview workers left this state after re-claiming already accepted bytes.
        service.claimForSubmission(OWNER, upload.id());
        assertThat(service.status(OWNER, upload.id()).state()).isEqualTo("SUBMITTING");
        service.submitted(OWNER, upload.id(), preview, true);
        assertThat(service.sourceForAcceptedRun(OWNER, upload.id(), preview, previewKey, true)).isEqualTo(input);
        assertThat(service.status(OWNER, upload.id()).state()).isEqualTo("SUBMITTING");
        assertThatThrownBy(() -> service.sourceForAcceptedRun(OWNER, upload.id(), preview, actualKey, true))
                .hasMessage("UPLOAD_RUN_ASSOCIATION_INVALID");
        assertThatThrownBy(() -> service.abandon(OWNER, upload.id())).hasMessage("UPLOAD_ACCEPTANCE_RECONCILIATION_REQUIRED");
        service.submitted(OWNER, upload.id(), actual, false);
        var actualReceipt = service.status(OWNER, upload.id());
        assertThat(actualReceipt.state()).isEqualTo("SUBMITTED");
        assertThat(actualReceipt.runId()).isEqualTo(actual);
        assertThat(actualReceipt.dryRunId()).isEqualTo(preview);
        assertThat(service.sourceForAcceptedRun(OWNER, upload.id(), actual, actualKey, false)).isEqualTo(input);
        assertThat(service.status(OWNER, upload.id()).state()).isEqualTo("SUBMITTED");
        assertThatThrownBy(() -> service.sourceForAcceptedRun(OWNER, upload.id(), actual, previewKey, true))
                .hasMessage("UPLOAD_RUN_ASSOCIATION_INVALID");
        assertThatThrownBy(() -> service.abandon(OWNER, upload.id())).hasMessage("UPLOAD_ACCEPTANCE_RECONCILIATION_REQUIRED");
    }

    @Test void shouldReleaseQuotaOnce_whenUnsubmittedUploadIsAbandonedRepeatedly() throws Exception {
        var upload = service.begin(OWNER, "source.zip", 100);
        assertThat(budget.getReservedBytes()).isEqualTo(100);
        service.abandon(OWNER, upload.id()); service.abandon(OWNER, upload.id());
        assertThat(budget.getReservedBytes()).isZero();
        assertThat(Files.exists(temporary.resolve("uploads").resolve(upload.id()))).isFalse();
    }

    @Test void shouldSerializeCrossModeAcceptance_withoutWaitingOrChangingUploadState() throws Exception {
        var upload = service.begin(OWNER, "source.xml", 1);
        try (var first = service.lockAcceptance(OWNER, upload.id())) {
            assertThatThrownBy(() -> service.lockAcceptance(OWNER, upload.id())).hasMessage("UPLOAD_BUSY");
            assertThatThrownBy(() -> service.lockAcceptance("web-99", upload.id())).hasMessage("UPLOAD_NOT_FOUND");
            assertThat(service.status(OWNER, upload.id()).state()).isEqualTo("OPEN");
        }
        try (var next = service.lockAcceptance(OWNER, upload.id())) {
            assertThat(service.status(OWNER, upload.id()).state()).isEqualTo("OPEN");
        }
    }

    @Test void shouldRetirePayloadButKeepPrefixProof_whenActualSnapshotIsDurablyPublished() throws Exception {
        byte[] bytes = "content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var upload = service.begin(OWNER, "source.xml", bytes.length);
        service.append(OWNER, upload.id(), 0, bytes.length, hash(bytes), new ByteArrayInputStream(bytes)); service.finish(OWNER, upload.id());
        String dryRun = UUID.randomUUID().toString(), actual = UUID.randomUUID().toString();
        service.claimForSubmission(OWNER, upload.id()); service.submitted(OWNER, upload.id(), dryRun, true);
        assertThatThrownBy(() -> service.snapshotPublished(dryRun, "a".repeat(64))).hasMessage("UPLOAD_RUN_ASSOCIATION_NOT_READY");
        Path input = service.claimForSubmission(OWNER, upload.id()); service.submitted(OWNER, upload.id(), actual, false);
        service.snapshotPublished(actual, "a".repeat(64)); service.snapshotPublished(actual, "a".repeat(64));
        assertThat(Files.exists(input)).isFalse(); assertThat(budget.getReservedBytes()).isZero();
        assertThat(service.status(OWNER, upload.id()).state()).isEqualTo("RETIRED");
        assertThat(service.status(OWNER, upload.id()).runId()).isEqualTo(actual);
        assertThat(service.status(OWNER, upload.id()).dryRunId()).isEqualTo(dryRun);
        assertThat(service.chunks(OWNER, upload.id(), 0, 100).get(0).sha256()).isEqualTo(hash(bytes));
        assertThat(service.claimForSubmission(OWNER, upload.id())).isEqualTo(input);
        service.submitted(OWNER, upload.id(), actual, false);
        assertThat(service.status(OWNER, upload.id()).state()).isEqualTo("RETIRED");
        assertThatThrownBy(() -> service.snapshotPublished(actual, "b".repeat(64))).hasMessage("UPLOAD_SNAPSHOT_PUBLICATION_CONFLICT");
    }

    @Test void shouldRejectForeignOwnerAndUnsafeName_withoutDisclosingSource() throws Exception {
        var upload = service.begin(OWNER, "Synthetic Patient.xml", 100);
        assertThatThrownBy(() -> service.status("web-99", upload.id())).hasMessage("UPLOAD_NOT_FOUND");
        assertThatThrownBy(() -> service.begin(OWNER, "../Synthetic Patient.xml", 100)).hasMessage("UPLOAD_NAME_OR_TYPE_INVALID");
        assertThatThrownBy(() -> service.status(OWNER, "../../escape")).hasMessage("UPLOAD_ID_INVALID");
    }

    @Test void shouldAvoidInodeGrowth_whenCapacityRefusesNewUpload() throws Exception {
        service.begin(OWNER, "source.xml", 100);
        budget.setReservedBytes(8L << 20);
        long before;
        try (var files = Files.list(temporary.resolve("uploads"))) { before = files.count(); }
        assertThatThrownBy(() -> service.begin(OWNER, "source.xml", 100)).hasMessage("UPLOAD_CAPACITY_BUSY");
        try (var files = Files.list(temporary.resolve("uploads"))) { assertThat(files.count()).isEqualTo(before); }
    }

    @Test void shouldReleaseLocalAdmission_whenSameUploadIsBusy() throws Exception {
        var upload = service.begin(OWNER, "source.xml", 1);
        Path lock = temporary.resolve("uploads").resolve(upload.id()).resolve("body.lock");
        try (var channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE); var held = channel.lock()) {
            for (int n = 0; n < 10; n++) assertThatThrownBy(() -> service.append(OWNER, upload.id(), 0, 1,
                    hash(new byte[]{1}), new ByteArrayInputStream(new byte[]{1}))).hasMessage("UPLOAD_BUSY");
        }
        assertThat(service.append(OWNER, upload.id(), 0, 1, hash(new byte[]{1}), new ByteArrayInputStream(new byte[]{1})).receivedBytes()).isEqualTo(1);
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
