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

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Publishes import-owned native document bytes before the short clinical reference transaction. */
@Service
public class CdsDocumentPublicationService {
    @FunctionalInterface public interface Source { InputStream open() throws IOException; }
    public record Publication(String filename, String location, String digest, long length) { }
    /** Versioned ownership evidence; contains no source labels or clinical values. */
    public record PendingOwner(int protocol, String runId, long ownerEpoch, String unitKey, String variant,
            String extension, String digest, long byteLength, String finalFilename, String operationId,
            long createdEpochMillis) { }
    private final CdsImportFileService files;
    private final CdsImportConfigurationService configuration;
    private final CdsFieldParser fields;

    public CdsDocumentPublicationService(CdsImportFileService files, CdsImportConfigurationService configuration,
            CdsFieldParser fields) {
        this.files = files; this.configuration = configuration; this.fields = fields;
    }

    /** No replacement, no global content deduplication, and no patient/source text in the native filename. */
    public Publication publish(String run, long epoch, String unit, String variant, String extension,
            String expectedDigest, long expectedLength, Source source) throws IOException {
        CdsImportLedgerService.requireNoTransaction();
        if (run == null || !run.matches("[A-Za-z0-9_-]{1,36}") || epoch < 1
                || !unit.matches("[0-9a-f]{64}") || !variant.matches("[a-z]{1,16}")
                || !extension.matches("[a-z0-9]{1,8}") || !expectedDigest.matches("[0-9a-f]{64}")
                || expectedLength < 0 || expectedLength > configuration.limits().maxExpandedBytes()) {
            throw new CdsImportException("DOCUMENT_PUBLICATION_IDENTITY_INVALID");
        }
        Path root = PathValidationUtils.getRequiredDocumentDirectory().toPath();
        String filename = filename(unit, variant, extension, expectedDigest);
        Path destination = CdsPaths.within(root.resolve(filename), root);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            verify(destination, expectedDigest, expectedLength);
            files.publishedVerified(run, epoch, destination.toString(), expectedDigest, expectedLength);
        } else {
            Path pendingRoot = privateDirectory(root.resolve(".cds-pending"), root);
            Path runRoot = privateDirectory(pendingRoot.resolve(run), pendingRoot);
            String operationId = UUID.randomUUID().toString();
            Path operation = CdsPaths.privateDirectory(runRoot.resolve(operationId), runRoot);
            PendingOwner owner = new PendingOwner(1, run, epoch, unit, variant, extension, expectedDigest,
                    expectedLength, filename, operationId, System.currentTimeMillis());
            Path journal = operation.resolve("owner.json");
            try (OutputStream output = CdsPaths.newPrivateFile(journal, operation)) {
                new com.fasterxml.jackson.databind.ObjectMapper().writeValue(output, owner);
            }
            CdsPaths.forceFile(journal); CdsPaths.forceDirectory(operation); CdsPaths.forceDirectory(runRoot);
            Path pending = CdsPaths.within(operation.resolve("payload.partial"), operation);
            try (PendingPublication publication = new PendingPublication(operation, journal, pending, destination, runRoot)) {
                long count = 0;
                long nextDiskCheck = 0;
                MessageDigest digest = CdsPaths.sha256();
                try (InputStream input = source.open(); OutputStream output = CdsPaths.newPrivateFile(pending, operation)) {
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        fields.checkpoint();
                        if (Thread.currentThread().isInterrupted()) throw new CdsImportException("DOCUMENT_PUBLICATION_CANCELLED");
                        count = Math.addExact(count, read);
                        if (count > expectedLength) throw new CdsImportException("DOCUMENT_PUBLICATION_LENGTH_MISMATCH");
                        if (count >= nextDiskCheck) {
                            if (Files.getFileStore(root).getUsableSpace() < configuration.limits().minimumFreeDiskBytes()) {
                                throw new CdsImportException("DOCUMENT_PUBLICATION_DISK_RESERVE");
                            }
                            nextDiskCheck = Math.addExact(count, 16L << 20);
                        }
                        digest.update(buffer, 0, read); output.write(buffer, 0, read);
                    }
                }
                if (count != expectedLength || !CdsPaths.hex(digest.digest()).equals(expectedDigest)) {
                    throw new CdsImportException("DOCUMENT_PUBLICATION_DIGEST_MISMATCH");
                }
                CdsPaths.forceFile(pending);
                // A hard link atomically installs exclusively. ATOMIC_MOVE may replace an existing
                // destination on some providers, even without REPLACE_EXISTING.
                try { Files.createLink(destination, pending); }
                catch (FileAlreadyExistsException raced) { verify(destination, expectedDigest, expectedLength); }
                CdsPaths.forceDirectory(root);
                // Registration is acknowledged before private ownership evidence is retired. A crash
                // after linking or a lost DB acknowledgement leaves the exact source inode and journal.
                files.publishedVerified(run, epoch, destination.toString(), expectedDigest, expectedLength);
                publication.registered = true;
            }
        }
        return new Publication(filename, destination.toString(), expectedDigest, expectedLength);
    }

    /** Only metadata is consulted inside the clinical unit; expensive file verification preceded it. */
    public String reference(String run, long epoch, String unit, String variant, String extension, String digest) throws IOException {
        String filename = filename(unit, variant, extension, digest);
        Path root = PathValidationUtils.getRequiredDocumentDirectory().toPath();
        Path destination = CdsPaths.within(root.resolve(filename), root);
        files.referenceLocation(run, epoch, unit, destination.toString());
        return filename;
    }

    static String filename(String unit, String variant, String extension, String digest) {
        if (!unit.matches("[0-9a-f]{64}") || !variant.matches("[a-z]{1,16}")
                || !extension.matches("[a-z0-9]{1,8}") || !digest.matches("[0-9a-f]{64}")) {
            throw new CdsImportException("DOCUMENT_PUBLICATION_IDENTITY_INVALID");
        }
        return "cds-" + unit + "-" + variant + "-" + digest + "." + extension;
    }

    private static Path privateDirectory(Path path, Path root) throws IOException {
        Path directory = CdsPaths.within(path, root);
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            try { CdsPaths.privateDirectory(directory, root); CdsPaths.forceDirectory(root); }
            catch (FileAlreadyExistsException raced) { /* Validate the resulting directory below. */ }
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS).stream()
                        .anyMatch(p -> p.name().startsWith("GROUP_") || p.name().startsWith("OTHERS_"))) {
            throw new CdsImportException("DOCUMENT_PUBLICATION_PRIVATE_STORAGE_INSECURE");
        }
        return directory;
    }

    /** Java resource suppression retains the primary copy/registration failure when retirement also fails. */
    private static final class PendingPublication implements AutoCloseable {
        private final Path operation;
        private final Path journal;
        private final Path pending;
        private final Path destination;
        private final Path runRoot;
        private boolean registered;

        PendingPublication(Path operation, Path journal, Path pending, Path destination, Path runRoot) {
            this.operation = operation; this.journal = journal; this.pending = pending;
            this.destination = destination; this.runRoot = runRoot;
        }

        @Override public void close() {
            try {
                boolean linked = false;
                boolean uncertain = false;
                try {
                    linked = regularIfPresent(pending) && regularIfPresent(destination) && Files.isSameFile(pending, destination);
                } catch (IOException unavailable) { uncertain = true; }
                if (registered || !linked && !uncertain) {
                    Files.deleteIfExists(pending); Files.deleteIfExists(journal); CdsPaths.forceDirectory(operation);
                    Files.delete(operation); CdsPaths.forceDirectory(runRoot);
                }
            } catch (IOException | RuntimeException | Error cleanupFailure) {
                throw new CdsImportException("DOCUMENT_PUBLICATION_CLEANUP_FAILED", false, cleanupFailure);
            }
        }

        private static boolean regularIfPresent(Path path) throws IOException {
            try {
                return Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS).isRegularFile();
            } catch (java.nio.file.NoSuchFileException absent) {
                return false;
            }
        }
    }

    private void verify(Path path, String expected, long length) throws IOException {
        var before = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || before.size() != length) {
            throw new CdsImportException("DOCUMENT_PUBLICATION_CONFLICT");
        }
        MessageDigest digest = CdsPaths.sha256();
        long bytes = 0;
        fields.checkpoint();
        try (InputStream input = Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) {
                fields.checkpoint();
                if (Thread.currentThread().isInterrupted()) throw new CdsImportException("DOCUMENT_PUBLICATION_CANCELLED");
                bytes = Math.addExact(bytes, count);
                if (bytes > length) throw new CdsImportException("DOCUMENT_PUBLICATION_CONFLICT");
                digest.update(buffer, 0, count);
            }
        }
        fields.checkpoint();
        var after = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (bytes != length || !after.isRegularFile() || after.size() != length
                || !java.util.Objects.equals(before.fileKey(), after.fileKey())
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !CdsPaths.hex(digest.digest()).equals(expected)) throw new CdsImportException("DOCUMENT_PUBLICATION_CONFLICT");
    }
}
