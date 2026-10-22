/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Content identity observed by a viewer, never a server-side permission or a new-edit instruction. */
public final class StoredDocumentRevision {
    private StoredDocumentRevision() { }

    /** Caller must authorize the document before observing its bytes. */
    public static String forDocumentFile(String filename) throws IOException {
        File directory = PathValidationUtils.validateConfiguredDirectory(
                CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"), "DOCUMENT_DIR");
        return sha256(resolveStoredChild(directory, filename).toPath());
    }

    /**
     * Resolves a persisted filename under the caller's validated document directory.
     * Canonical containment rejects parent traversal and escaping symlinks without
     * flattening legitimate nested paths. As with validateExistingPath, callers
     * still enforce existence, regular-file requirements and their source lease.
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "The only File construction is immediately checked by PathValidationUtils canonical directory containment, including symlink resolution, before returning; stored nested filenames retain existing semantics")
    public static File resolveStoredChild(File directory, String filename) {
        return PathValidationUtils.validateExistingPath(new File(directory, filename), directory);
    }

    public static boolean valid(String revision) {
        return revision != null && revision.matches("[0-9a-f]{64}");
    }

    /** Checked while holding the source mutation lease; never silently rebase selected page numbers. */
    public static void requireMatch(Path source, String observed) throws IOException {
        if (!valid(observed) || !observed.equals(sha256(source))) throw new ConflictException();
    }

    /**
     * Fixed-memory streaming hash. A view does not need a PDF worker or mutation
     * lease: replacement during this observation fails closed, and replacement
     * afterwards merely leaves a stale token that the mutation endpoint rejects.
     */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "Callers supply a PathValidationUtils-contained document under its mutation lease, an authorized viewer path, or private generated staging; this method never accepts request paths")
    public static String sha256(Path source) throws IOException {
        BasicFileAttributes before = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile()) throw new IOException("Document revision requires a regular file");
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 unavailable", unavailable); }
        long count = 0;
        try (InputStream input = Files.newInputStream(source)) {
            byte[] buffer = new byte[64 * 1024];
            int length;
            while ((length = input.read(buffer)) != -1) {
                digest.update(buffer, 0, length);
                count += length;
            }
        }
        BasicFileAttributes after = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!after.isRegularFile() || !Objects.equals(before.fileKey(), after.fileKey())
                || before.size() != after.size() || count != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
            throw new IOException("Document changed while its revision was observed");
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Safe conflict: no cache, file or database mutation has been accepted. */
    public static final class ConflictException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public ConflictException() { super("The document changed; refresh its source before submitting a new selection"); }
    }
}
