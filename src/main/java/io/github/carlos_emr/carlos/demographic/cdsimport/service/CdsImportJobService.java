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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportJobCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotFileDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsUploadDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/** Durable acceptance and hash-named immutable request documents; no clinical transaction spans IO. */
@Service
public class CdsImportJobService {
    private static final ObjectMapper JSON = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final CdsImportLedgerService ledger;
    private final CdsImportConfigurationService configuration;
    private final CdsSnapshotService snapshots;
    private final CdsUploadService uploads;
    private final CdsPreacceptService preaccept;

    public CdsImportJobService(CdsImportLedgerService ledger, CdsImportConfigurationService configuration,
            CdsSnapshotService snapshots, CdsUploadService uploads) {
        this(ledger, configuration, snapshots, uploads, new CdsPreacceptService(configuration, ledger));
    }

    @Autowired
    public CdsImportJobService(CdsImportLedgerService ledger, CdsImportConfigurationService configuration,
            CdsSnapshotService snapshots, CdsUploadService uploads, CdsPreacceptService preaccept) {
        this.ledger = ledger; this.configuration = configuration; this.snapshots = snapshots; this.uploads = uploads;
        this.preaccept=preaccept;
    }

    public CdsImportJobDto accept(CdsImportActorDto actor, CdsImportCommand command) throws IOException {
        CdsImportLedgerService.requireNoTransaction(); configuration.requireEnabled();
        configuration.requireSource(command.sourceNamespace(), command.sourceProfile());
        CdsImportJobDto existing = ledger.findSubmission(actor.operator(), command.submissionKey());
        if (existing != null) return existing(existing, actor, command);
        Path staging = privateRoot(configuration.stagingRoot());
        Path jobRoot = staging.resolve("job-" + CdsImportKeys.hash(actor.operator(), command.submissionKey()));
        if (!Files.exists(jobRoot, LinkOption.NOFOLLOW_LINKS)) {
            try { CdsPaths.privateDirectory(jobRoot, staging); }
            catch (java.nio.file.FileAlreadyExistsException concurrent) { /* The short acceptance lock settles ownership. */ }
        }
        privateRoot(jobRoot);
        try (var process=preaccept.enter(jobRoot);FileChannel channel = lockChannel(jobRoot.resolve("accept.lock")); FileLock ignored = lock(channel)) {
            existing = ledger.findSubmission(actor.operator(), command.submissionKey());
            if (existing != null) return existing(existing, actor, command);
            String uploadId = uploadId(command.input());
            CdsSnapshotDto snapshot = null;
            CdsPreacceptService.Ownership ownership;
            String sourceDigest;
            if (uploadId != null) {
                CdsUploadDto upload = uploads.status(actor.operator(), uploadId);
                if (!command.dryRun() && upload.runId() != null) {
                    CdsImportJobDto prior = ledger.status(upload.runId());
                    CdsImportJobDocumentDto document = load(prior.id());
                    if (!sameClinicalRequest(document.command(), command) || !document.actor().equals(actor)) {
                        throw new CdsImportException("UPLOAD_ALREADY_SUBMITTED_WITH_DIFFERENT_OPTIONS");
                    }
                    return prior;
                }
                Path claimed = uploads.claimForSubmission(actor.operator(), uploadId);
                if (!claimed.toAbsolutePath().normalize().equals(command.input().toAbsolutePath().normalize())
                        || upload.manifestSha256() == null || upload.receivedBytes() != upload.expectedBytes()) {
                    throw new CdsImportException("UPLOAD_ACCEPTANCE_CONFLICT");
                }
                sourceDigest = upload.manifestSha256();
                ownership=preaccept.begin(actor, command, jobRoot, ignored);
            } else {
                CdsPaths.within(command.input(), configuration.allowedRoot());
                ownership=preaccept.begin(actor, command, jobRoot, ignored);
                snapshot = sourceSnapshot(command.input(), configuration.allowedRoot(), jobRoot, () -> false);
                sourceDigest = snapshot.sha256();
            }
            CdsImportJobDocumentDto document = new CdsImportJobDocumentDto(command, actor, snapshot, uploadId, jobRoot, rulesetDigest(command, configuration));
            byte[] bytes = JSON.writeValueAsBytes(document);
            if (bytes.length > 65536) throw new CdsImportException("JOB_DOCUMENT_CAPACITY_EXCEEDED");
            String digest = CdsPaths.hex(CdsPaths.sha256().digest(bytes));
            Path location = jobRoot.resolve("job-" + digest + ".json");
            if (!Files.exists(location, LinkOption.NOFOLLOW_LINKS)) {
                Path pending=jobRoot.resolve(".job-document-"+ownership.generation()+"-"+UUID.randomUUID()+".pending");
                try (OutputStream output = CdsPaths.newPrivateFile(pending, jobRoot)) { output.write(bytes); }
                CdsPaths.forceFile(pending);Files.move(pending,location,StandardCopyOption.ATOMIC_MOVE);CdsPaths.forceDirectory(jobRoot);
            } else if (!java.util.Arrays.equals(bytes, boundedFile(location, 65536))) {
                throw new CdsImportException("JOB_DOCUMENT_HASH_CONFLICT");
            }
            preaccept.accepting(ownership, location, sourceDigest, ignored);
            CdsImportJobDto accepted = ledger.accept(new CdsImportJobCommand(command.submissionKey(), actor.operator(), command.providerNo(),
                    command.sourceNamespace(), command.sourceProfile(), sourceDigest, optionsDigest(command),
                    rulesetDigest(command, configuration), location.toString(), command.dryRun(), actor.securityNo() == null));
            preaccept.accepted(ownership, accepted.id(), ignored);
            if (uploadId != null) uploads.submitted(actor.operator(), uploadId, accepted.id(), command.dryRun());
            return accepted;
        }
    }

    public CdsImportJobDocumentDto load(String runId) throws IOException {
        Path location = Path.of(ledger.snapshotLocation(runId));
        Path root = CdsPaths.within(location, configuration.stagingRoot()).getParent();
        String name = location.getFileName().toString();
        if (!name.matches("job-[0-9a-f]{64}\\.json")) throw new CdsImportException("JOB_DOCUMENT_LOCATION_INVALID");
        byte[] bytes = boundedFile(location, 65536);
        if (!name.equals("job-" + CdsPaths.hex(CdsPaths.sha256().digest(bytes)) + ".json")) {
            throw new CdsImportException("JOB_DOCUMENT_HASH_MISMATCH");
        }
        CdsImportJobDocumentDto document = JSON.readValue(bytes, CdsImportJobDocumentDto.class);
        CdsImportJobDto job = ledger.status(runId);
        if (!root.equals(document.jobRoot()) || !job.operator().equals(document.actor().operator())
                || !job.providerNo().equals(document.command().providerNo())
                || !job.sourceNamespace().equals(document.command().sourceNamespace())
                || !job.profile().equals(document.command().sourceProfile())
                || document.acceptedRulesetDigest() != null && !job.rulesetDigest().equals(document.acceptedRulesetDigest())) {
            throw new CdsImportException("JOB_DOCUMENT_BINDING_MISMATCH");
        }
        return document;
    }

    public Path root(String runId) throws IOException { return load(runId).jobRoot(); }

    /** Called by the fenced planning owner; source lock excludes competing/revived IO workers. */
    public CdsSnapshotDto snapshot(String runId, CdsImportJobDocumentDto document) throws IOException {
        CdsImportLedgerService.requireNoTransaction();
        var current = ledger.status(runId);
        long started=System.nanoTime(); long[] nextPoll={0};
        java.util.function.BooleanSupplier cancelled=()-> {
            long now=System.nanoTime();
            if ((now-started)/1_000_000>configuration.limits().maxDurationMillis()) throw new CdsImportException("CDS_INPUT_DEADLINE");
            if (now<nextPoll[0]) return false;
            nextPoll[0]=now+250_000_000L;
            var owner=ledger.status(runId);
            if (owner.ownerEpoch()!=current.ownerEpoch()) throw new CdsImportException("OWNER_FENCED");
            return owner.cancelRequested();
        };
        try (FileChannel channel = lockChannel(document.jobRoot().resolve("source.lock")); FileLock ignored = lock(channel)) {
            if (document.snapshot() != null) {
                CdsSnapshotDto value = snapshots.load(document.snapshot().root(), true, cancelled);
                if (!value.sha256().equals(current.sourceDigest())) throw new CdsImportException("ACCEPTED_SOURCE_CHANGED");
                return value;
            }
            if (document.uploadId() == null) throw new CdsImportException("ACCEPTED_SOURCE_MISSING");
            CdsUploadDto upload = uploads.status(document.actor().operator(), document.uploadId());
            if (!Objects.equals(current.sourceDigest(), upload.manifestSha256())) throw new CdsImportException("UPLOAD_MANIFEST_CHANGED");
            CdsSnapshotDto copied;
            if (Files.exists(document.jobRoot().resolve("snapshot"), LinkOption.NOFOLLOW_LINKS)) {
                copied = snapshots.load(document.jobRoot().resolve("snapshot"), true, cancelled);
            } else {
                Path source = uploads.claimForSubmission(document.actor().operator(), document.uploadId());
                copied = sourceSnapshot(source, configuration.uploadRoot(), document.jobRoot(),
                        cancelled);
            }
            verifyUpload(runId, document, upload, copied, cancelled);
            if (!document.command().dryRun()) {
                uploads.snapshotPublished(document.actor().operator(), document.uploadId(), runId, copied.sha256());
            }
            return copied;
        }
    }

    private CdsSnapshotDto sourceSnapshot(Path source, Path sourceRoot, Path jobRoot,
            java.util.function.BooleanSupplier cancelled) throws IOException {
        Path published = jobRoot.resolve("snapshot");
        if (Files.exists(published, LinkOption.NOFOLLOW_LINKS)) return snapshots.load(published, true, cancelled);
        Path partial = jobRoot.resolve("snapshot.pending");
        if (Files.exists(partial, LinkOption.NOFOLLOW_LINKS)) {
            // Caller owns source.lock (or accept.lock before any run exists). Never remove a
            // prior partial copy; preserve it as failed-attempt evidence before recopying source.
            Files.move(partial, jobRoot.resolve("snapshot-abandoned-" + UUID.randomUUID()), StandardCopyOption.ATOMIC_MOVE);
            CdsPaths.forceDirectory(jobRoot);
        }
        return snapshots.snapshot(source, sourceRoot, jobRoot, "snapshot", configuration.limits(), cancelled);
    }

    private void verifyUpload(String runId, CdsImportJobDocumentDto document, CdsUploadDto upload,
            CdsSnapshotDto snapshot, java.util.function.BooleanSupplier cancelled) throws IOException {
        Path source = snapshot.root().resolve("source-archive.bin");
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            CdsSnapshotFileDto[] selected = new CdsSnapshotFileDto[1];
            snapshots.forEachFile(snapshot, file -> {
                if (file.relativeName().equals(document.command().input().getFileName().toString())) selected[0] = file;
            });
            if (selected[0] == null) throw new CdsImportException("UPLOADED_SOURCE_NOT_SNAPSHOTTED");
            source = selected[0].path();
        }
        MessageDigest manifest = CdsPaths.sha256(); long index = 0, offset = 0;
        try (InputStream input = new BufferedInputStream(Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS), 65536)) {
            byte[] buffer = new byte[65536];
            while (index < upload.committedChunks()) {
                var page = uploads.chunks(document.actor().operator(), document.uploadId(), index, 100);
                if (page.isEmpty()) throw new CdsImportException("UPLOAD_RECEIPT_INCOMPLETE");
                for (var chunk : page) {
                    if (chunk.index() != index || chunk.offset() != offset || chunk.bytes() < 1) throw new CdsImportException("UPLOAD_RECEIPT_CONFLICT");
                    MessageDigest value = CdsPaths.sha256(); int remaining = chunk.bytes();
                    while (remaining > 0) {
                        int n = input.read(buffer, 0, Math.min(buffer.length, remaining));
                        if (n < 0) throw new CdsImportException("UPLOADED_SOURCE_TRUNCATED");
                        value.update(buffer, 0, n); remaining -= n;
                    }
                    if (!chunk.sha256().equals(CdsPaths.hex(value.digest()))) throw new CdsImportException("UPLOADED_SOURCE_CHANGED");
                    ByteBuffer row = ByteBuffer.allocate(52).putLong(index).putLong(offset).putInt(chunk.bytes())
                            .put(HexFormat.of().parseHex(chunk.sha256()));
                    manifest.update(row.array()); offset = Math.addExact(offset, chunk.bytes()); index++;
                    if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new CdsImportException("IMPORT_CANCELLED");
                }
            }
            if (offset != upload.expectedBytes() || input.read() != -1
                    || !upload.manifestSha256().equals(CdsPaths.hex(manifest.digest()))) throw new CdsImportException("UPLOAD_MANIFEST_CHANGED");
        }
    }

    public static String optionsDigest(CdsImportCommand command) {
        // Attempts, transport, operator and preview mode cannot mint fresh clinical identity.
        return CdsImportKeys.hash(command.providerNo(), Objects.toString(command.programId(), ""),
                Objects.toString(command.facilityId(), ""), Boolean.toString(command.mergeExisting()),
                Boolean.toString(command.matchProviderNames()), Integer.toString(command.timeshiftDays()),
                Objects.toString(command.courseId(), ""), Boolean.toString(command.synthetic()));
    }

    public static String rulesetDigest(CdsImportCommand command, CdsImportConfigurationService configuration) {
        return CdsImportKeys.hash(CdsImportConfigurationService.RULESET, command.sourceProfile(),
                CdsSchemaValidator.MAIN_SHA256, CdsSchemaValidator.DATATYPE_SHA256,
                io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsRulesetFingerprint.current().sha256(),
                configuration.normalizationPolicyDigest(command));
    }

    private static void requireSame(CdsImportJobDocumentDto document, CdsImportActorDto actor, CdsImportCommand command) {
        if (!document.command().equals(command) || !document.actor().equals(actor)) throw new CdsImportException("SUBMISSION_CONFLICT");
    }

    private static boolean sameClinicalRequest(CdsImportCommand previous, CdsImportCommand requested) {
        return previous.input().equals(requested.input()) && previous.providerNo().equals(requested.providerNo())
                && Objects.equals(previous.programId(), requested.programId()) && Objects.equals(previous.facilityId(), requested.facilityId())
                && previous.sourceNamespace().equals(requested.sourceNamespace()) && previous.sourceProfile().equals(requested.sourceProfile())
                && previous.mergeExisting() == requested.mergeExisting() && previous.dryRun() == requested.dryRun()
                && previous.matchProviderNames() == requested.matchProviderNames() && previous.timeshiftDays() == requested.timeshiftDays()
                && Objects.equals(previous.courseId(), requested.courseId()) && previous.synthetic() == requested.synthetic();
    }

    private CdsImportJobDto existing(CdsImportJobDto prior, CdsImportActorDto actor, CdsImportCommand command) throws IOException {
        CdsImportJobDocumentDto document = load(prior.id()); requireSame(document, actor, command);
        if (document.uploadId() != null) uploads.submitted(actor.operator(), document.uploadId(), prior.id(), command.dryRun());
        return prior;
    }

    private String uploadId(Path input) {
        if (configuration.value("cds.import.uploadRoot", "").isBlank()) return null;
        Path path = input.toAbsolutePath().normalize(), base = configuration.uploadRoot().toAbsolutePath().normalize();
        if (!path.startsWith(base)) return null;
        Path relative = base.relativize(path);
        if (relative.getNameCount() != 2 || !relative.getName(0).toString().matches("[0-9a-f-]{36}")
                || !Set.of("source.xml", "source.zip").contains(relative.getFileName().toString())) {
            throw new CdsImportException("UPLOAD_SOURCE_LOCATION_INVALID");
        }
        return relative.getName(0).toString();
    }

    static byte[] boundedFile(Path file, int maximum) throws IOException {
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(maximum);
            if (input.read() != -1) throw new CdsImportException("JOB_METADATA_CAPACITY_EXCEEDED");
            return bytes;
        }
    }

    private static Path privateRoot(Path configured) throws IOException {
        if (!Files.exists(configured, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(configured,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        if (Files.isSymbolicLink(configured) || !Files.isDirectory(configured, LinkOption.NOFOLLOW_LINKS)
                || !PosixFilePermissions.toString(Files.getPosixFilePermissions(configured)).equals("rwx------")) {
            throw new CdsImportException("IMPORT_ROOT_NOT_PRIVATE");
        }
        return configured.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static FileChannel lockChannel(Path path) throws IOException {
        return FileChannel.open(CdsPaths.within(path, path.getParent()),
                Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    }

    private static FileLock lock(FileChannel channel) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) throw new CdsImportException("IMPORT_SOURCE_IO_BUSY");
            return lock;
        } catch (OverlappingFileLockException busy) { throw new CdsImportException("IMPORT_SOURCE_IO_BUSY"); }
    }
}
