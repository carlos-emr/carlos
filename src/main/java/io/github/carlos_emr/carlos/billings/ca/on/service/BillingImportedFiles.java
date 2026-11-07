/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingFilenameImportDao.FileReview;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Never infer which historical submission owns a surviving shared filename. */
public final class BillingImportedFiles {
    private BillingImportedFiles() { }

    /**
     * Requires reviewed file evidence for an imported filename collision.
     * An empty review list performs no checks or IO, preserving ordinary file handling.
     * Shared original names remain unavailable; only the exact allocated name with
     * reconciled status, a nonblank reviewer, review date and lowercase SHA-256 is accepted.
     * The file must be regular, not a symlink, readable and match that digest.
     * Regeneration that changes its bytes requires a new review and recorded digest.
     *
     * @param reviews the {@code List<FileReview>} returned by the audit DAO
     * @param filename the exact requested {@code String} basename
     * @param path the {@link Path} already validated by the caller for billing-directory containment
     * @throws BillingFileWriteException if ownership or review is unresolved, the file is
     *         missing, nonregular, symlinked or unreadable, or its SHA-256 does not match
     */
    public static void requireResolved(List<FileReview> reviews, String filename, Path path) {
        if (reviews.isEmpty()) return;
        requireReview(reviews, filename);
        for (FileReview review : reviews) {
            try {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw unresolved(null);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
                }
                if (!HexFormat.of().formatHex(digest.digest()).equals(review.sha256())) throw unresolved(null);
            } catch (IOException | NoSuchAlgorithmException failure) {
                throw unresolved(failure);
            }
        }
    }

    /** Rejects ambiguous originals and incomplete reconciliation before any file IO. */
    private static void requireReview(List<FileReview> reviews, String filename) {
        for (FileReview review : reviews) {
            // A shared original stays ambiguous even after a specific renamed
            // row is reconciled. Only that row's allocated name can be served.
            if (filename.equals(review.originalFilename())
                    || !filename.equals(review.importedFilename())
                    || !"reconciled".equals(review.status()) || !review.dated()
                    || review.reviewer() == null || review.reviewer().isBlank()
                    || review.sha256() == null || !review.sha256().matches("[0-9a-f]{64}")) {
                throw unresolved(null);
            }
        }
    }

    /**
     * Copies an audited file into a private snapshot and verifies that snapshot before returning it.
     * The caller must serve these bytes, rather than reopen the replaceable billing pathname,
     * and close the result to remove the temporary file. Memory use stays bounded for large files.
     * Ordinary files return {@code null} without IO and retain the existing download path.
     *
     * @param reviews the audit DAO's {@code List<FileReview>}
     * @param filename the exact requested basename
     * @param path a billing path already validated for directory containment
     * @return the verified snapshot, or {@code null} when there is no import audit
     * @throws BillingFileWriteException if the review, copy or snapshot verification fails
     */
    public static ReviewedFile openReviewed(List<FileReview> reviews, String filename, Path path) {
        if (reviews.isEmpty()) return null;
        requireReview(reviews, filename);
        Path snapshot = null;
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw unresolved(null);
            // createTempFile uses restrictive permissions; the snapshot is never published
            // in HOME_DIR and no billing writer knows its pathname.
            snapshot = Files.createTempFile("carlos-billing-reviewed-", ".tmp");
            try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
                 var output = Files.newOutputStream(snapshot)) {
                input.transferTo(output);
            }
            requireResolved(reviews, filename, snapshot);
            long size = Files.size(snapshot);
            return new ReviewedFile(snapshot, Files.newInputStream(snapshot,
                    StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE), size);
        } catch (IOException | RuntimeException failure) {
            if (snapshot != null) {
                try {
                    Files.deleteIfExists(snapshot);
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            if (failure instanceof BillingFileWriteException unresolved) throw unresolved;
            throw unresolved(failure);
        }
    }

    /** A request-owned verified stream; closing it also removes its private snapshot. */
    public static final class ReviewedFile implements AutoCloseable {
        private final Path snapshot;
        private final InputStream input;
        private final long size;

        private ReviewedFile(Path snapshot, InputStream input, long size) {
            this.snapshot = snapshot;
            this.input = input;
            this.size = size;
        }

        public InputStream input() { return input; }
        public long size() { return size; }

        /** Closes the stream and removes the snapshot even when stream closure fails. */
        @Override
        public void close() throws IOException {
            try {
                input.close();
            } finally {
                Files.deleteIfExists(snapshot);
            }
        }
    }

    private static BillingFileWriteException unresolved(Throwable cause) {
        return BillingFileWriteException.forReason(
                BillingFileWriteException.Reason.IMPORTED_FILE_UNRESOLVED, cause);
    }
}
