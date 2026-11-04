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
package io.github.carlos_emr.carlos.lab.ca.all.upload.handlers;

import io.github.carlos_emr.carlos.test.unit.RecordingTransactionManager;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that a FHIR document PDF written inside a lab upload's transaction is removed with the rows
 * that roll back, and kept otherwise.
 *
 * @since 2026-09-24
 */
@Tag("unit")
@Tag("lab")
class FHIRCommunicationRequestHandlerUnitTest {
    @TempDir
    Path documentDir;

    private final RecordingTransactionManager transactions = new RecordingTransactionManager();

    @Test
    void shouldDeleteSavedPdf_whenTransactionRollsBack() throws Exception {
        Path pdf = Files.writeString(documentDir.resolve("DocUpload.request.1.pdf"), "%PDF-1.4");

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            FHIRCommunicationRequestHandler.discardOnRollback(pdf.toFile(), documentDir.toFile());
            status.setRollbackOnly();
        });

        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(pdf).doesNotExist();
    }

    @Test
    void shouldCompleteRollback_whenSavedPdfCannotBeDeleted() throws Exception {
        // A non-empty directory in the PDF's place makes the delete fail even for a privileged user.
        Path stuck = Files.createDirectory(documentDir.resolve("DocUpload.request.5.pdf"));
        Files.writeString(stuck.resolve("keep"), "x");

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            FHIRCommunicationRequestHandler.discardOnRollback(stuck.toFile(), documentDir.toFile());
            status.setRollbackOnly();
        });

        // The failure is logged and the delete deferred to shutdown; it never escapes the rollback.
        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(stuck).exists();
    }

    @Test
    void shouldKeepSavedPdf_whenTransactionCommits() throws Exception {
        Path pdf = Files.writeString(documentDir.resolve("DocUpload.request.2.pdf"), "%PDF-1.4");

        new TransactionTemplate(transactions).executeWithoutResult(status ->
                FHIRCommunicationRequestHandler.discardOnRollback(pdf.toFile(), documentDir.toFile()));

        assertThat(transactions.commits).isEqualTo(1);
        assertThat(pdf).exists();
    }

    @Test
    void shouldKeepSavedPdf_whenCommitOutcomeIsUnknown() throws Exception {
        Path pdf = Files.writeString(documentDir.resolve("DocUpload.request.3.pdf"), "%PDF-1.4");
        transactions.failCommit = true;

        try {
            new TransactionTemplate(transactions).executeWithoutResult(status ->
                    FHIRCommunicationRequestHandler.discardOnRollback(pdf.toFile(), documentDir.toFile()));
        } catch (org.springframework.transaction.TransactionSystemException expected) {
            // The document row may have committed, so its file must stay.
        }

        assertThat(pdf).exists();
    }

    @Test
    void shouldKeepSavedPdf_withoutTransaction() throws Exception {
        Path pdf = Files.writeString(documentDir.resolve("DocUpload.request.4.pdf"), "%PDF-1.4");

        FHIRCommunicationRequestHandler.discardOnRollback(pdf.toFile(), documentDir.toFile());

        assertThat(pdf).exists();
    }
}
