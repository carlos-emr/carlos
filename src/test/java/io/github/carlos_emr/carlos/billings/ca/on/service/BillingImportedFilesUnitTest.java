/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingFilenameImportDao.FileReview;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.io.IOException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("billing")
class BillingImportedFilesUnitTest {
    @TempDir Path directory;

    private String digest(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private FileReview resolved(String sha) {
        return new FileReview("shared.001", "o19-claim-2021-2-0.claim", "reconciled", sha, "billing-admin", true);
    }

    @Test void shouldPreserveExistingBehavior_whenFileHasNoImportAudit() {
        assertThatCode(() -> BillingImportedFiles.requireResolved(List.of(), "ordinary.001", directory.resolve("missing")))
                .doesNotThrowAnyException();
    }

    @Test void shouldRejectSharedFile_whenOwnershipIsAmbiguous() throws Exception {
        Path file = Files.writeString(directory.resolve("shared.001"), "one surviving submission");
        FileReview review = new FileReview("shared.001", "o19-claim-2021-2-0.claim", "ambiguous", null, null, false);
        assertThatThrownBy(() -> BillingImportedFiles.requireResolved(List.of(review), "shared.001", file))
                .isInstanceOf(BillingFileWriteException.class)
                .hasMessageContaining("requires reconciliation").hasMessageNotContaining("shared.001");
    }

    @Test void shouldRejectContent_whenAllocatedNameIsUnreviewed() throws Exception {
        Path file = Files.writeString(directory.resolve("o19-claim-2021-2-0.claim"), "unassigned");
        FileReview review = new FileReview("shared.001", file.getFileName().toString(), "ambiguous", null, null, false);
        assertThatThrownBy(() -> BillingImportedFiles.requireResolved(List.of(review), review.importedFilename(), file))
                .isInstanceOf(BillingFileWriteException.class);
    }

    @Test void shouldRejectChangedContent_whenDigestDiffersFromReview() throws Exception {
        FileReview review = resolved(digest("verified submission"));
        Path file = Files.writeString(directory.resolve(review.importedFilename()), "verified submission");
        assertThatCode(() -> BillingImportedFiles.requireResolved(List.of(review), review.importedFilename(), file))
                .doesNotThrowAnyException();
        Files.writeString(file, "a different submission");
        assertThatThrownBy(() -> BillingImportedFiles.requireResolved(List.of(review), review.importedFilename(), file))
                .isInstanceOf(BillingFileWriteException.class);
    }

    @Test void shouldRejectSharedOriginal_whenAllocatedRowIsReconciled() throws Exception {
        FileReview review = resolved(digest("verified submission"));
        Path file = Files.writeString(directory.resolve(review.originalFilename()), "verified submission");
        assertThatThrownBy(() -> BillingImportedFiles.requireResolved(List.of(review), review.originalFilename(), file))
                .isInstanceOf(BillingFileWriteException.class);
    }

    @Test void shouldRejectResolution_whenReviewerMetadataIsMissing() throws Exception {
        Path file = Files.writeString(directory.resolve("o19-claim-2021-2-0.claim"), "verified");
        FileReview review = new FileReview("shared.001", file.getFileName().toString(), "reconciled", digest("verified"), "", false);
        assertThatThrownBy(() -> BillingImportedFiles.requireResolved(List.of(review), review.importedFilename(), file))
                .isInstanceOf(BillingFileWriteException.class);
    }

    @Test void shouldRejectResolvedFile_whenMissingOrSymlinked() throws Exception {
        FileReview review = resolved(digest("verified"));
        Path absent = directory.resolve(review.importedFilename());
        assertThatThrownBy(() -> BillingImportedFiles.requireResolved(List.of(review), review.importedFilename(), absent))
                .isInstanceOf(BillingFileWriteException.class);
        Path other = Files.writeString(directory.resolve("other"), "verified");
        Files.createSymbolicLink(absent, other);
        assertThatThrownBy(() -> BillingImportedFiles.requireResolved(List.of(review), review.importedFilename(), absent))
                .isInstanceOf(BillingFileWriteException.class);
    }

    @Test void shouldRejectSnapshot_whenContentDoesNotMatchReview() throws Exception {
        FileReview review = resolved(digest("reviewed submission"));
        Path file = Files.writeString(directory.resolve(review.importedFilename()), "unreviewed bytes");
        assertThatThrownBy(() -> BillingImportedFiles.openReviewed(List.of(review), review.importedFilename(), file))
                .isInstanceOf(BillingFileWriteException.class);
    }

    /** Closing the request-owned snapshot must close its stream and preserve the source. */
    @Test void shouldCloseSnapshot_whenReviewedDownloadFinishes() throws Exception {
        FileReview review = resolved(digest("reviewed submission"));
        Path file = Files.writeString(directory.resolve(review.importedFilename()), "reviewed submission");
        var reviewed = BillingImportedFiles.openReviewed(List.of(review), review.importedFilename(), file);
        try (reviewed) {
            assertThat(reviewed.input().readAllBytes()).asString().isEqualTo("reviewed submission");
        }
        assertThatThrownBy(() -> reviewed.input().read()).isInstanceOf(IOException.class);
        assertThat(Files.readString(file)).isEqualTo("reviewed submission");
    }
}
