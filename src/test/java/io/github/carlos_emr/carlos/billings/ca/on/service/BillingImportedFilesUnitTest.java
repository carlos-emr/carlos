/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingFilenameImportDao.FileReview;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    @Test void ordinaryFilesKeepTheirExistingBehavior() {
        assertThatCode(() -> BillingImportedFiles.requireResolved(List.of(), "ordinary.001", directory.resolve("missing")))
                .doesNotThrowAnyException();
    }

    @Test void sharedSurvivingFileDoesNotProveSubmissionOwnership() throws Exception {
        Path file = Files.writeString(directory.resolve("shared.001"), "one surviving submission");
        FileReview review = new FileReview("shared.001", "o19-claim-2021-2-0.claim", "ambiguous", null, null, false);
        assertThatThrownBy(() -> BillingImportedFiles.requireResolved(List.of(review), "shared.001", file))
                .isInstanceOf(BillingFileWriteException.class)
                .hasMessageContaining("requires reconciliation").hasMessageNotContaining("shared.001");
    }

    @Test void newNameAloneDoesNotAuthorizeUnreviewedContent() throws Exception {
        Path file = Files.writeString(directory.resolve("o19-claim-2021-2-0.claim"), "unassigned");
        FileReview review = new FileReview("shared.001", file.getFileName().toString(), "ambiguous", null, null, false);
        assertThatThrownBy(() -> BillingImportedFiles.requireResolved(List.of(review), review.importedFilename(), file))
                .isInstanceOf(BillingFileWriteException.class);
    }

    @Test void reconciledContentMustMatchItsRecordedDigest() throws Exception {
        FileReview review = resolved(digest("verified submission"));
        Path file = Files.writeString(directory.resolve(review.importedFilename()), "verified submission");
        assertThatCode(() -> BillingImportedFiles.requireResolved(List.of(review), review.importedFilename(), file))
                .doesNotThrowAnyException();
        Files.writeString(file, "a different submission");
        assertThatThrownBy(() -> BillingImportedFiles.requireResolved(List.of(review), review.importedFilename(), file))
                .isInstanceOf(BillingFileWriteException.class);
    }

    @Test void sharedOriginalStaysBlockedAfterOneRowIsReconciled() throws Exception {
        FileReview review = resolved(digest("verified submission"));
        Path file = Files.writeString(directory.resolve(review.originalFilename()), "verified submission");
        assertThatThrownBy(() -> BillingImportedFiles.requireResolved(List.of(review), review.originalFilename(), file))
                .isInstanceOf(BillingFileWriteException.class);
    }

    @Test void resolutionRequiresAnActorAndDate() throws Exception {
        Path file = Files.writeString(directory.resolve("o19-claim-2021-2-0.claim"), "verified");
        FileReview review = new FileReview("shared.001", file.getFileName().toString(), "reconciled", digest("verified"), "", false);
        assertThatThrownBy(() -> BillingImportedFiles.requireResolved(List.of(review), review.importedFilename(), file))
                .isInstanceOf(BillingFileWriteException.class);
    }

    @Test void missingOrSymlinkedResolvedFilesRemainUnavailable() throws Exception {
        FileReview review = resolved(digest("verified"));
        Path absent = directory.resolve(review.importedFilename());
        assertThatThrownBy(() -> BillingImportedFiles.requireResolved(List.of(review), review.importedFilename(), absent))
                .isInstanceOf(BillingFileWriteException.class);
        Path other = Files.writeString(directory.resolve("other"), "verified");
        Files.createSymbolicLink(absent, other);
        assertThatThrownBy(() -> BillingImportedFiles.requireResolved(List.of(review), review.importedFilename(), absent))
                .isInstanceOf(BillingFileWriteException.class);
    }
}
