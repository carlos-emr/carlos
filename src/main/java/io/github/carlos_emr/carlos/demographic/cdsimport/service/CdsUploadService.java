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

import io.github.carlos_emr.carlos.commn.dao.CdsImportAdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.CdsUploadDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportAdmission;
import io.github.carlos_emr.carlos.commn.model.CdsUpload;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsUploadChunkDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsUploadDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsUploadException;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Durable owner-scoped, raw-body uploads. Body reads and hashing run outside database transactions;
 * a short row-locked transaction seals each bounded append and its fixed-size chunk receipt.
 * Orphan file/journal tails are truncated to the last known database checkpoint on the next append.
 */
@Service
public class CdsUploadService {
    private static final int JOURNAL_BYTES = Long.BYTES * 2 + Integer.BYTES + 32;
    private static final Set<String> IMMUTABLE_STATES = Set.of("COMPLETE", "SUBMITTING", "SUBMITTED_DRY_RUN", "SUBMITTED", "SNAPSHOT_PUBLISHED", "RETIRED");
    private final CdsUploadDao uploads;
    private final CdsImportAdmissionDao admission;
    private final CdsImportConfigurationService configuration;
    private final TransactionTemplate transaction;
    private final Semaphore incoming = new Semaphore(8);

    public CdsUploadService(CdsUploadDao uploads, CdsImportAdmissionDao admission,
            CdsImportConfigurationService configuration, PlatformTransactionManager manager) {
        this.uploads = uploads; this.admission = admission; this.configuration = configuration;
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transaction.setTimeout(30);
    }

    public CdsUploadDto begin(String operator, String name, long bytes) throws IOException {
        configuration.requireEnabled();
        requireName(name);
        if (bytes < 1 || bytes > configuration.number("cds.import.upload.maxFileBytes", 64L << 30)) {
            throw problem("UPLOAD_FILE_SIZE_REFUSED", 413);
        }
        String id = UUID.randomUUID().toString();
        reserveDisk(root(), Math.min(bytes, chunkBytes()));
        // Reserve before creating per-upload files: repeated busy refusals cannot exhaust inodes.
        CdsUploadDto receipt = write(() -> {
            CdsImportAdmission budget = admission.lock("UPLOAD_BUDGET");
            if (budget == null) throw problem("UPLOAD_SCHEMA_NOT_READY", 503);
            long maximum = configuration.number("cds.import.upload.maxTotalBytes", 256L << 30);
            if (bytes > maximum - budget.getReservedBytes()
                    || uploads.reservedCount() >= configuration.number("cds.import.upload.maxFiles", 20_000)) {
                throw problem("UPLOAD_CAPACITY_BUSY", 429);
            }
            CdsUpload upload = new CdsUpload();
            upload.setId(id); upload.setOperator(operator); upload.setSourceName(name);
            upload.setMediaKind(name.toLowerCase(Locale.ROOT).endsWith(".zip") ? "ZIP" : "XML");
            upload.setExpectedBytes(bytes); upload.setState("OPEN");
            upload.setSubmissionKey("web-upload-" + id);
            upload.setCreatedAt(Instant.now()); upload.setUpdatedAt(upload.getCreatedAt());
            uploads.persist(upload);
            budget.setReservedBytes(Math.addExact(budget.getReservedBytes(), bytes));
            admission.merge(budget); uploads.flush();
            return dto(upload);
        });
        // If this fails, the owner can discover the durable empty upload and retry or abandon it.
        directory(id, true);
        return receipt;
    }

    public CdsUploadDto status(String operator, String id) {
        return write(() -> dto(owned(uploads.get(requireId(id)), operator)));
    }

    public List<CdsUploadDto> pending(String operator, String after, int limit) {
        String cursor = after == null || after.isBlank() ? "" : requireId(after);
        int count = Math.max(1, Math.min(limit, 100));
        return write(() -> uploads.page(operator, cursor, count).stream().map(this::dto).toList());
    }

    public CdsUploadDto append(String operator, String id, long offset, int declaredBytes,
            String expectedHash, InputStream source) throws IOException {
        configuration.requireEnabled();
        requireId(id);
        if (offset < 0 || declaredBytes < 1 || declaredBytes > chunkBytes()
                || expectedHash == null || !expectedHash.matches("[0-9a-f]{64}")) {
            throw problem("UPLOAD_CHUNK_INVALID", 400);
        }
        CdsUploadDto before = status(operator, id);
        if ("ABANDONED".equals(before.state())) throw problem("UPLOAD_ABANDONED", 410);
        Path directory = directory(id, before.receivedBytes() == 0 && "OPEN".equals(before.state()));
        Path attempt = child(directory, "attempt-" + UUID.randomUUID());
        if (!incoming.tryAcquire()) throw problem("UPLOAD_IO_BUSY", 429);
        try (FileChannel channel = openPrivate(child(directory, "body.lock"));
                FileLock lock = tryLock(channel)) {
            if (lock == null) throw problem("UPLOAD_BUSY", 409);
            reserveDisk(directory, declaredBytes);
            MessageDigest digest = sha256();
            long read = 0;
            long deadline = System.nanoTime() + 120_000_000_000L;
            try (FileChannel out = FileChannel.open(attempt,
                    Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = source.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) {
                        throw problem("UPLOAD_CHUNK_TIMEOUT", 408);
                    }
                    read = Math.addExact(read, n);
                    if (read > declaredBytes) throw problem("UPLOAD_CHUNK_SIZE_MISMATCH", 400);
                    digest.update(buffer, 0, n);
                    ByteBuffer bytes = ByteBuffer.wrap(buffer, 0, n);
                    while (bytes.hasRemaining()) out.write(bytes);
                }
                if (read != declaredBytes || !HexFormat.of().formatHex(digest.digest()).equals(expectedHash)) {
                    throw problem("UPLOAD_CHUNK_HASH_MISMATCH", 422);
                }
                out.force(true);
            }
            return write(() -> appendSealed(operator, id, offset, declaredBytes, expectedHash, directory, attempt));
        } finally {
            incoming.release();
            // Only this attempt's fresh, never-published spool is eligible here.
            try { Files.deleteIfExists(attempt); } catch (IOException ignored) {
                // The owned attempt remains discoverable by expiry cleanup; never delete the durable payload.
            }
        }
    }

    private CdsUploadDto appendSealed(String operator, String id, long offset, int bytes,
            String hash, Path directory, Path attempt) {
        CdsUpload row = owned(uploads.lock(id), operator);
        Path journal = child(directory, "chunks.bin");
        try {
            if (offset < row.getReceivedBytes() && offset % chunkBytes() == 0) {
                CdsUploadChunkDto previous = readChunk(journal, offset / chunkBytes());
                if (previous.offset() == offset && previous.bytes() == bytes && previous.sha256().equals(hash)) return dto(row);
                throw problem("UPLOAD_RETRY_CONTENT_CONFLICT", 409);
            }
            if (!"OPEN".equals(row.getState()) || offset != row.getReceivedBytes()) {
                throw problem("UPLOAD_OFFSET_CONFLICT", 409);
            }
            if (bytes != Math.min(chunkBytes(), row.getExpectedBytes() - offset)) {
                throw problem("UPLOAD_CHUNK_SIZE_MISMATCH", 400);
            }
            Path payload = child(directory, payloadName(row));
            try (FileChannel target = openPrivate(payload); FileChannel receipts = openPrivate(journal);
                    FileChannel input = FileChannel.open(attempt, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                if (target.size() < offset || receipts.size() < row.getChunkCount() * JOURNAL_BYTES) {
                    throw problem("UPLOAD_STORAGE_INCOMPLETE", 409);
                }
                target.truncate(offset); target.position(offset);
                receipts.truncate(row.getChunkCount() * JOURNAL_BYTES); receipts.position(receipts.size());
                long copied = 0;
                while (copied < bytes) {
                    long n = input.transferTo(copied, bytes - copied, target);
                    if (n < 1) throw new IOException("UPLOAD_APPEND_STALLED");
                    copied += n;
                }
                target.force(true);
                ByteBuffer entry = ByteBuffer.allocate(JOURNAL_BYTES)
                        .putLong(row.getChunkCount()).putLong(offset).putInt(bytes).put(HexFormat.of().parseHex(hash));
                entry.flip(); while (entry.hasRemaining()) receipts.write(entry);
                receipts.force(true);
            }
            syncDirectory(directory);
            row.setReceivedBytes(Math.addExact(offset, bytes)); row.setChunkCount(row.getChunkCount() + 1);
            row.setUpdatedAt(Instant.now()); uploads.merge(row); uploads.flush();
            return dto(row);
        } catch (IOException failure) {
            throw problem("UPLOAD_STORAGE_UNAVAILABLE", 503);
        }
    }

    /** Owner-only paged receipts let the browser verify every old prefix chunk before resume. */
    public List<CdsUploadChunkDto> chunks(String operator, String id, long afterIndex, int limit) throws IOException {
        CdsUploadDto receipt = status(operator, id);
        if (afterIndex < 0) throw problem("UPLOAD_CURSOR_INVALID", 400);
        int count = Math.max(1, Math.min(limit, 100));
        List<CdsUploadChunkDto> result = new ArrayList<>(count);
        Path journal = child(directory(id, false), "chunks.bin");
        for (long index = afterIndex; index < receipt.committedChunks() && result.size() < count; index++) {
            result.add(readChunk(journal, index));
        }
        return List.copyOf(result);
    }

    /** Seal the ordered verified chunk inventory; the core independently hashes the complete source before clinical writes. */
    public CdsUploadDto finish(String operator, String id) throws IOException {
        configuration.requireEnabled();
        CdsUploadDto before = status(operator, id);
        if (IMMUTABLE_STATES.contains(before.state())) return before;
        if (before.receivedBytes() != before.expectedBytes()) throw problem("UPLOAD_INCOMPLETE", 409);
        Path directory = directory(id, false);
        String manifest;
        try (InputStream input = Files.newInputStream(child(directory, "chunks.bin"), LinkOption.NOFOLLOW_LINKS)) {
            MessageDigest digest = sha256(); byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
            manifest = HexFormat.of().formatHex(digest.digest());
        }
        return write(() -> {
            CdsUpload row = owned(uploads.lock(id), operator);
            if (IMMUTABLE_STATES.contains(row.getState())) return dto(row);
            if (!"OPEN".equals(row.getState()) || row.getReceivedBytes() != row.getExpectedBytes()) {
                throw problem("UPLOAD_INCOMPLETE", 409);
            }
            try {
                if (Files.size(child(directory, payloadName(row))) != row.getExpectedBytes()
                        || Files.size(child(directory, "chunks.bin")) != row.getChunkCount() * JOURNAL_BYTES) {
                    throw problem("UPLOAD_STORAGE_INCOMPLETE", 409);
                }
                syncDirectory(directory);
            } catch (IOException failure) { throw problem("UPLOAD_STORAGE_UNAVAILABLE", 503); }
            row.setManifestDigest(manifest); row.setState("COMPLETE"); row.setUpdatedAt(Instant.now());
            uploads.merge(row); uploads.flush(); return dto(row);
        });
    }

    /** Claim before calling the job service so a lost job acknowledgement cannot race source deletion. */
    public Path claimForSubmission(String operator, String id) {
        return write(() -> {
            CdsUpload row = owned(uploads.lock(requireId(id)), operator);
            if (!IMMUTABLE_STATES.contains(row.getState())) throw problem("UPLOAD_NOT_COMPLETE", 409);
            if (!Set.of("SUBMITTED", "SNAPSHOT_PUBLISHED", "RETIRED").contains(row.getState())) {
                row.setState("SUBMITTING"); row.setUpdatedAt(Instant.now()); uploads.merge(row); uploads.flush();
            }
            return child(directory(id, false), payloadName(row));
        });
    }

    /** Serializes acceptance of different modes/keys for one immutable upload. */
    public AcceptanceGuard lockAcceptance(String operator, String id) throws IOException {
        status(operator, id);
        FileChannel channel = openPrivate(child(directory(id, false), "body.lock"));
        try {
            FileLock lock = tryLock(channel);
            if (lock == null) throw problem("UPLOAD_BUSY", 409);
            return new AcceptanceGuard(channel, lock);
        } catch (IOException | RuntimeException failure) {
            channel.close(); throw failure;
        }
    }

    public static final class AcceptanceGuard implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;
        private AcceptanceGuard(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }
        @Override public void close() throws IOException {
            try { lock.close(); } finally { channel.close(); }
        }
        @Override public String toString() { return "AcceptanceGuard[protected]"; }
    }

    /** Read an accepted run's immutable source without changing its acceptance state. */
    public Path sourceForAcceptedRun(String operator, String id, String runId, String submissionKey, boolean dryRun) {
        requireId(runId);
        return write(() -> {
            CdsUpload row = owned(uploads.lock(requireId(id)), operator);
            String accepted = dryRun ? row.getDryRunId() : row.getRunId();
            if (!runId.equals(accepted)
                    || !(row.getSubmissionKey() + (dryRun ? "-dry-run" : "-import")).equals(submissionKey)
                    || !Set.of("SUBMITTING", "SUBMITTED_DRY_RUN", "SUBMITTED", "SNAPSHOT_PUBLISHED", "RETIRED").contains(row.getState())) {
                throw problem("UPLOAD_RUN_ASSOCIATION_INVALID", 409);
            }
            return child(directory(id, false), payloadName(row));
        });
    }

    public void submitted(String operator, String id, String runId, boolean dryRun) {
        write(() -> {
            CdsUpload row = owned(uploads.lock(requireId(id)), operator);
            String prior = dryRun ? row.getDryRunId() : row.getRunId();
            if (!Set.of("SUBMITTING", "SUBMITTED", "SUBMITTED_DRY_RUN", "SNAPSHOT_PUBLISHED", "RETIRED").contains(row.getState())
                    || (prior != null && !prior.equals(runId))) throw problem("UPLOAD_SUBMISSION_CONFLICT", 409);
            if (dryRun) row.setDryRunId(requireId(runId)); else row.setRunId(requireId(runId));
            // An idempotent prior-mode acknowledgement cannot declare another uncertain claim stale.
            if (row.getSnapshotDigest() == null && !("SUBMITTING".equals(row.getState()) && prior != null)) {
                row.setState(row.getRunId() != null ? "SUBMITTED" : "SUBMITTED_DRY_RUN");
            }
            row.setUpdatedAt(Instant.now()); uploads.merge(row); uploads.flush(); return null;
        });
    }

    /**
     * Internal publication acknowledgement, never a browser operation. The job planner calls this only
     * after its complete immutable source snapshot is durable, independently verified against every
     * retained upload chunk receipt and associated with this actual (non-dry-run) run. Dry-run sources
     * stay available for execute. Unknown metadata commits retain the payload until reconciliation.
     */
    public void snapshotPublished(String runId, String snapshotDigest) throws IOException {
        snapshotPublished(null, null, runId, snapshotDigest);
    }

    /**
     * Planner-only form repairs a lost acceptance association. The caller must first verify the durable
     * job's operator/submission key and source digest against this owner's upload and every chunk receipt.
     */
    public void snapshotPublished(String operator, String uploadId, String runId, String snapshotDigest) throws IOException {
        requireId(runId);
        if ((operator == null) != (uploadId == null)) throw problem("UPLOAD_RUN_ASSOCIATION_INVALID", 400);
        if (uploadId != null) requireId(uploadId);
        if (snapshotDigest == null || !snapshotDigest.matches("[0-9a-f]{64}")) throw problem("UPLOAD_SNAPSHOT_DIGEST_INVALID", 400);
        CdsUploadDto acknowledged = write(() -> {
            CdsUpload row = uploadId == null ? uploads.lockActualRun(runId) : owned(uploads.lock(uploadId), operator);
            if (row == null) throw problem("UPLOAD_RUN_ASSOCIATION_NOT_READY", 409);
            if (uploadId != null && row.getRunId() == null) {
                if (!Set.of("SUBMITTING", "SUBMITTED_DRY_RUN").contains(row.getState())) throw problem("UPLOAD_RUN_ASSOCIATION_INVALID", 409);
                row.setRunId(runId); row.setState("SUBMITTED");
            }
            if (!Set.of("SUBMITTED", "SNAPSHOT_PUBLISHED", "RETIRED").contains(row.getState())
                    || !runId.equals(row.getRunId())
                    || (row.getSnapshotDigest() != null && !snapshotDigest.equals(row.getSnapshotDigest()))) {
                throw problem("UPLOAD_SNAPSHOT_PUBLICATION_CONFLICT", 409);
            }
            if (!"RETIRED".equals(row.getState())) {
                row.setSnapshotDigest(snapshotDigest); row.setState("SNAPSHOT_PUBLISHED"); row.setUpdatedAt(Instant.now());
                uploads.merge(row); uploads.flush();
            }
            return dto(row);
        });
        if ("RETIRED".equals(acknowledged.state())) return;
        Path directory = directory(acknowledged.id(), false);
        // Keep the small receipt journal and directory: resume/retry still proves the immutable upload prefix.
        try (FileChannel channel = openPrivate(child(directory, "body.lock")); FileLock lock = tryLock(channel)) {
            if (lock == null) throw problem("UPLOAD_BUSY", 409);
            Files.deleteIfExists(child(directory, "source.xml")); Files.deleteIfExists(child(directory, "source.zip"));
            try (var attempts = Files.newDirectoryStream(directory, "attempt-*")) {
                for (Path attempt : attempts) if (attempt.getFileName().toString().matches("attempt-[0-9a-f-]{36}")) {
                    Files.deleteIfExists(child(directory, attempt.getFileName().toString()));
                }
            }
            syncDirectory(directory);
            write(() -> {
                CdsImportAdmission budget = admission.lock("UPLOAD_BUDGET");
                CdsUpload row = uploads.lockActualRun(runId);
                if (row == null || !snapshotDigest.equals(row.getSnapshotDigest())) throw problem("UPLOAD_SNAPSHOT_PUBLICATION_CONFLICT", 409);
                if (!row.isQuotaReleased()) {
                    if (budget == null || budget.getReservedBytes() < row.getExpectedBytes()) throw problem("UPLOAD_BUDGET_RECONCILIATION_REQUIRED", 409);
                    budget.setReservedBytes(budget.getReservedBytes() - row.getExpectedBytes()); admission.merge(budget); row.setQuotaReleased(true);
                }
                row.setState("RETIRED"); row.setUpdatedAt(Instant.now()); uploads.merge(row); uploads.flush(); return null;
            });
        }
    }

    public CdsUploadDto abandon(String operator, String id) throws IOException {
        CdsUploadDto receipt = write(() -> {
            CdsImportAdmission budget = admission.lock("UPLOAD_BUDGET");
            CdsUpload row = owned(uploads.lock(requireId(id)), operator);
            if (Set.of("SUBMITTING", "SUBMITTED", "SUBMITTED_DRY_RUN", "SNAPSHOT_PUBLISHED", "RETIRED").contains(row.getState())) throw problem("UPLOAD_ACCEPTANCE_RECONCILIATION_REQUIRED", 409);
            if (!row.isQuotaReleased()) {
                if (budget == null || budget.getReservedBytes() < row.getExpectedBytes()) throw problem("UPLOAD_BUDGET_RECONCILIATION_REQUIRED", 409);
                budget.setReservedBytes(budget.getReservedBytes() - row.getExpectedBytes()); admission.merge(budget);
                row.setQuotaReleased(true);
            }
            row.setState("ABANDONED"); row.setUpdatedAt(Instant.now()); uploads.merge(row); uploads.flush(); return dto(row);
        });
        // Database acknowledgement is known before removing unsubmitted owned data. Unknown commits retain bytes.
        Path configured = root();
        if (!Files.exists(child(configured, id), LinkOption.NOFOLLOW_LINKS)) return receipt;
        Path directory = directory(id, false);
        try (var files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (Set.of("source.xml", "source.zip", "chunks.bin", "body.lock").contains(name)
                        || name.matches("attempt-[0-9a-f-]{36}")) {
                    Files.deleteIfExists(child(directory, name));
                }
            }
        }
        Files.deleteIfExists(directory);
        return receipt;
    }

    /** Bounded maintenance hook; submitted/uncertain sources are intentionally never expiry-deleted. */
    public int cleanupExpired(int limit) throws IOException {
        int count = Math.max(1, Math.min(limit, 200));
        Instant before = Instant.now().minusSeconds(configuration.number("cds.import.upload.ttlHours", 24) * 3600);
        List<CdsUploadDto> candidates = write(() -> uploads.expired(before, count).stream().map(this::dto).toList());
        int cleaned = 0;
        for (CdsUploadDto candidate : candidates) {
            String operator = write(() -> uploads.get(candidate.id()).getOperator());
            try { abandon(operator, candidate.id()); cleaned++; }
            catch (CdsUploadException changed) { if (changed.status() != 409) throw changed; }
        }
        return cleaned;
    }

    public int chunkBytes() {
        long size = configuration.number("cds.import.upload.maxChunkBytes", 4L << 20);
        if (size < 65536 || size > (16L << 20)) throw problem("UPLOAD_CHUNK_CONFIGURATION_INVALID", 503);
        return Math.toIntExact(size);
    }

    private CdsUploadChunkDto readChunk(Path path, long index) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(JOURNAL_BYTES);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            channel.position(Math.multiplyExact(index, JOURNAL_BYTES));
            while (buffer.hasRemaining()) if (channel.read(buffer) < 0) throw problem("UPLOAD_CHUNK_RECEIPT_MISSING", 409);
        }
        buffer.flip(); long stored = buffer.getLong(); long offset = buffer.getLong(); int bytes = buffer.getInt();
        byte[] hash = new byte[32]; buffer.get(hash);
        if (stored != index) throw problem("UPLOAD_CHUNK_RECEIPT_CONFLICT", 409);
        return new CdsUploadChunkDto(index, offset, bytes, HexFormat.of().formatHex(hash));
    }

    private Path directory(String id, boolean create) {
        requireId(id);
        Path configured = root();
        try {
            Path path = child(configured, id);
            if (create && !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                syncDirectory(configured);
            }
            if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw problem("UPLOAD_NOT_FOUND", 404);
            return path;
        } catch (IOException failure) { throw problem("UPLOAD_STORAGE_UNAVAILABLE", 503); }
    }

    private Path root() {
        Path configured = configuration.uploadRoot();
        try {
            if (!Files.exists(configured, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(configured,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            if (Files.isSymbolicLink(configured) || !Files.isDirectory(configured, LinkOption.NOFOLLOW_LINKS)
                    || !PosixFilePermissions.toString(Files.getPosixFilePermissions(configured)).equals("rwx------")) {
                throw problem("UPLOAD_ROOT_NOT_PRIVATE", 503);
            }
            return configured;
        } catch (IOException failure) { throw problem("UPLOAD_STORAGE_UNAVAILABLE", 503); }
    }

    private static Path child(Path directory, String name) {
        Path path = PathValidationUtils.validatePrivateImportPath(directory.resolve(name).toFile(), directory.toFile()).toPath();
        if (Files.isSymbolicLink(path)) throw problem("UPLOAD_PATH_INVALID", 400);
        return path;
    }
    private static FileChannel openPrivate(Path path) throws IOException {
        return FileChannel.open(path, Set.of(StandardOpenOption.CREATE, StandardOpenOption.READ,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    }
    private static FileLock tryLock(FileChannel channel) throws IOException {
        try { return channel.tryLock(); }
        catch (OverlappingFileLockException busy) { throw problem("UPLOAD_BUSY", 409); }
    }
    private void reserveDisk(Path path, long bytes) throws IOException {
        long floor = configuration.number("cds.import.minimumFreeDiskBytes", 512L << 20);
        if (Files.getFileStore(path).getUsableSpace() - bytes < floor) throw problem("UPLOAD_DISK_RESERVE_REFUSED", 507);
    }
    private static void syncDirectory(Path path) throws IOException {
        try (FileChannel directory = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) { directory.force(true); }
    }
    private <T> T write(Supplier<T> operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw problem("UPLOAD_AMBIENT_TRANSACTION_REFUSED", 409);
        return transaction.execute(status -> operation.get());
    }
    private static CdsUpload owned(CdsUpload row, String operator) {
        if (row == null || operator == null || !operator.equals(row.getOperator())) throw problem("UPLOAD_NOT_FOUND", 404);
        return row;
    }
    private CdsUploadDto dto(CdsUpload row) {
        return new CdsUploadDto(row.getId(), row.getSourceName(), row.getExpectedBytes(), row.getReceivedBytes(),
                chunkBytes(), row.getChunkCount(), row.getState(), row.getManifestDigest(), row.getSubmissionKey(),
                row.getRunId(), row.getDryRunId(), row.getUpdatedAt().toEpochMilli());
    }
    private static String payloadName(CdsUpload row) { return "ZIP".equals(row.getMediaKind()) ? "source.zip" : "source.xml"; }
    private static String requireId(String id) {
        if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw problem("UPLOAD_ID_INVALID", 400);
        return id;
    }
    private static void requireName(String name) {
        if (name == null || name.isBlank() || name.length() > 1000 || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0
                || name.chars().anyMatch(c -> c < 32 || c == 127)
                || !(name.toLowerCase(Locale.ROOT).endsWith(".xml") || name.toLowerCase(Locale.ROOT).endsWith(".zip"))) {
            throw problem("UPLOAD_NAME_OR_TYPE_INVALID", 400);
        }
    }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private static CdsUploadException problem(String code, int status) { return new CdsUploadException(code, status); }
}
