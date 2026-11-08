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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStorageBindingDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportProtectedFiles;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Creates a private attempt only from accepted version-two storage bindings and current ownership.
 * Binding records contain identifiers/fingerprints, never a private key or physical path. Old roots
 * and immutable key IDs may remain configured for retained artifacts; active-policy rotation cannot
 * silently redirect an accepted job or substitute a different key. No policy/key read occurs during
 * metadata-only context startup. This supplies encrypted storage, not clinical publication authority.
 */
@Service
public class CdsExportStagingFactory {
    private final Properties settings = new Properties();
    private final CdsExportLedgerService ledger;
    public CdsExportStagingFactory(@Qualifier("cdsExportProperties") Properties supplied, CdsExportLedgerService ledger) {
        settings.putAll(supplied); this.ledger = ledger;
    }
    public CdsExportStorageBindingDto bind() throws IOException {
        String root = token("cds.export.store.active"), key = token("cds.export.masterKey.active");
        root(root); byte[] bytes = keyBytes(key);
        try { return new CdsExportStorageBindingDto(root, key, digest(bytes)); }
        finally { Arrays.fill(bytes, (byte) 0); }
    }
    /** Each claim owns a fresh epoch directory. Reusing a partial attempt is refused. */
    public CdsExportStagingService create(CdsExportLeaseDto lease, Runnable checkpoint) throws IOException {
        Objects.requireNonNull(lease); Objects.requireNonNull(checkpoint);
        if (lease.document().protocol() != 2 || lease.document().storage() == null) {
            throw new IOException("CDS_EXPORT_STORAGE_BINDING_REQUIRED");
        }
        scope(lease.runId(), lease.epoch()); ledger.checkpoint(lease); checkpoint.run();
        var binding = lease.document().storage(); var key = key(binding);
        Path base = root(binding.rootId()), run = base.resolve(lease.runId()), attempt = run.resolve(Long.toString(lease.epoch()));
        try { Files.createDirectory(run, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))); }
        catch (FileAlreadyExistsException existing) { CdsExportProtectedFiles.dataDirectory(run); }
        Files.createDirectory(attempt, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        boolean complete = false;
        try {
            CdsExportProtectedFiles.dataDirectory(attempt); ledger.checkpoint(lease); checkpoint.run();
            var store = new CdsExportStagingService(attempt, lease.runId(), lease.epoch(), key,
                    number("cds.export.maxStagedFileBytes", 128L << 30, 1L << 40),
                    number("cds.export.minimumFreeBytes", 512L << 20, 1L << 40), checkpoint);
            complete = true; return store;
        } finally { if (!complete) Files.delete(attempt); }
    }
    /** Callers separately authorize retained-artifact access/cleanup and bind its exact run, epoch and file evidence. */
    public CdsExportStagingService openExisting(String runId, long epoch, CdsExportStorageBindingDto binding,
            Runnable checkpoint) throws IOException {
        scope(runId, epoch); Objects.requireNonNull(binding); Objects.requireNonNull(checkpoint);
        Path directory = root(binding.rootId()).resolve(runId).resolve(Long.toString(epoch));
        CdsExportProtectedFiles.dataDirectory(directory);
        return new CdsExportStagingService(directory, runId, epoch, key(binding),
                number("cds.export.maxStagedFileBytes", 128L << 30, 1L << 40),
                number("cds.export.minimumFreeBytes", 512L << 20, 1L << 40), checkpoint);
    }
    /**
     * Retires only an exclusively leased, expired export attempt. This also removes crash
     * fragments predating the ownership manifest. No configuration, key, import or native
     * source directory is traversed. A missing/changed key does not prevent safe erasure.
     */
    public void purgeAttempt(String runId, long epoch, CdsExportStorageBindingDto binding, Runnable checkpoint) throws IOException {
        scope(runId, epoch); Objects.requireNonNull(binding); Objects.requireNonNull(checkpoint); checkpoint.run();
        Path base = root(binding.rootId()), run = base.resolve(runId), directory = run.resolve(Long.toString(epoch));
        if (!Files.exists(run, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        CdsExportProtectedFiles.dataDirectory(run);
        if (!Files.exists(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        CdsExportProtectedFiles.dataDirectory(directory);
        // SecureDirectoryStream keeps all unlink operations anchored to this verified directory.
        try (var opened = Files.newDirectoryStream(directory)) {
            if (!(opened instanceof java.nio.file.SecureDirectoryStream<Path> secure)) throw new IOException("CDS_EXPORT_SECURE_DIRECTORY_REQUIRED");
            long count = 0;
            for (Path entry : opened) {
                checkpoint.run(); if (++count > 4_000_000 || !entry.getFileName().toString().matches("[0-9a-f]{64}\\.stage")) throw new IOException("CDS_EXPORT_CLEANUP_UNKNOWN_MEMBER");
                var attrs = secure.getFileAttributeView(entry.getFileName(), java.nio.file.attribute.PosixFileAttributeView.class, java.nio.file.LinkOption.NOFOLLOW_LINKS).readAttributes();
                if (!attrs.isRegularFile() || !attrs.owner().equals(Files.getOwner(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                        || !attrs.permissions().equals(PosixFilePermissions.fromString("rw-------"))
                        || ((Number) Files.getAttribute(entry, "unix:nlink", java.nio.file.LinkOption.NOFOLLOW_LINKS)).longValue() != 1) throw new IOException("CDS_EXPORT_CLEANUP_MEMBER_CHANGED");
            }
        }
        checkpoint.run();
        try (var opened = Files.newDirectoryStream(directory)) {
            if (!(opened instanceof java.nio.file.SecureDirectoryStream<Path> secure)) throw new IOException("CDS_EXPORT_SECURE_DIRECTORY_REQUIRED");
            long count = 0;
            for (Path entry : opened) {
                checkpoint.run(); if (++count > 4_000_000 || !entry.getFileName().toString().matches("[0-9a-f]{64}\\.stage")) throw new IOException("CDS_EXPORT_CLEANUP_UNKNOWN_MEMBER");
                var attrs = secure.getFileAttributeView(entry.getFileName(), java.nio.file.attribute.PosixFileAttributeView.class, java.nio.file.LinkOption.NOFOLLOW_LINKS).readAttributes();
                if (!attrs.isRegularFile() || !attrs.owner().equals(Files.getOwner(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                        || !attrs.permissions().equals(PosixFilePermissions.fromString("rw-------"))
                        || ((Number) Files.getAttribute(entry, "unix:nlink", java.nio.file.LinkOption.NOFOLLOW_LINKS)).longValue() != 1) throw new IOException("CDS_EXPORT_CLEANUP_MEMBER_CHANGED");
                secure.deleteFile(entry.getFileName());
            }
        }
        checkpoint.run(); Files.delete(directory);
        try (var channel = java.nio.channels.FileChannel.open(run, java.nio.file.StandardOpenOption.READ, java.nio.file.LinkOption.NOFOLLOW_LINKS)) { channel.force(true); }
        // Preserve the run directory: another epoch can be active concurrently.
    }
    private Path root(String id) throws IOException {
        return CdsExportProtectedFiles.dataDirectory(CdsExportProtectedFiles.absolute(required("cds.export.store." + id + ".root")));
    }
    private SecretKeySpec key(CdsExportStorageBindingDto binding) throws IOException {
        byte[] bytes = keyBytes(binding.keyId());
        try {
            if (!MessageDigest.isEqual(HexFormat.of().parseHex(binding.keySha256()), sha256(bytes))) {
                throw new IOException("CDS_EXPORT_STORAGE_KEY_CHANGED");
            }
            return new SecretKeySpec(bytes, "AES");
        } finally { Arrays.fill(bytes, (byte) 0); }
    }
    private byte[] keyBytes(String id) throws IOException {
        Path directory = CdsExportProtectedFiles.absolute(required("cds.export.masterKeyRoot"));
        byte[] bytes = CdsExportProtectedFiles.readRootFile(directory.resolve(id + ".key"), 32);
        if (bytes.length != 32) { Arrays.fill(bytes, (byte) 0); throw new IOException("CDS_EXPORT_AES256_KEY_REQUIRED"); }
        return bytes;
    }
    private String token(String property) throws IOException {
        String text = required(property); if (!text.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) invalid(); return text;
    }
    private String required(String property) throws IOException {
        String value = settings.getProperty(property); if (value == null || value.isEmpty()) invalid(); return value;
    }
    private long number(String property, long fallback, long maximum) throws IOException {
        String text = settings.getProperty(property, Long.toString(fallback));
        try {
            if (!text.matches("[0-9]{1,13}")) invalid(); long value = Long.parseLong(text);
            if (value < 0 || value > maximum || property.endsWith("maxStagedFileBytes") && value == 0) invalid(); return value;
        } catch (NumberFormatException invalid) { throw new IOException("CDS_EXPORT_STORAGE_CONFIGURATION_INVALID"); }
    }
    private static void scope(String runId, long epoch) throws IOException {
        try {
            if (runId == null || !UUID.fromString(runId).toString().equals(runId) || epoch < 1) {
                throw new IOException("CDS_EXPORT_STAGING_SCOPE_INVALID");
            }
        }
        catch (RuntimeException invalid) { throw new IOException("CDS_EXPORT_STAGING_SCOPE_INVALID"); }
    }
    private static byte[] sha256(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String digest(byte[] bytes) { return HexFormat.of().formatHex(sha256(bytes)); }
    private static void invalid() throws IOException { throw new IOException("CDS_EXPORT_STORAGE_CONFIGURATION_INVALID"); }
}
