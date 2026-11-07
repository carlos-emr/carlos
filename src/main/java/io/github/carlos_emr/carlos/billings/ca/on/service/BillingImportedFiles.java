/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingFilenameImportDao.FileReview;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Never infer which historical submission owns a surviving shared filename. */
public final class BillingImportedFiles {
    private BillingImportedFiles() { }

    public static void requireResolved(List<FileReview> reviews, String filename, Path path) {
        if (reviews.isEmpty()) return;
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

    private static BillingFileWriteException unresolved(Throwable cause) {
        return BillingFileWriteException.forReason(
                BillingFileWriteException.Reason.IMPORTED_FILE_UNRESOLVED, cause);
    }
}
