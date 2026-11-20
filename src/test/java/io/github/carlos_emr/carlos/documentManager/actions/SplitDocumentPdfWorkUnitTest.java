/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager.actions;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronization;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.carlos_emr.carlos.documentManager.actions.SplitDocumentPdfWork.Operation.*;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class SplitDocumentPdfWorkUnitTest {
    @TempDir Path directory;

    Path source(int pages) throws Exception {
        Path path = directory.resolve("original.pdf");
        try (PDDocument pdf = new PDDocument()) {
            for (int i = 0; i < pages; i++) pdf.addPage(new PDPage());
            pdf.getDocumentInformation().setTitle("Preserved metadata");
            pdf.save(path.toFile());
        }
        return path;
    }

    @Test void repeatedSelectedPageHasIndependentRotationAndOriginalBytesSurvive() throws Exception {
        Path source = source(2);
        byte[] original = Files.readAllBytes(source);
        try (var prepared = SplitDocumentPdfWork.prepareNow(source, directory, SPLIT,
                SplitDocumentPdfWork.selections(new String[]{"2,90", "2,180", "1,-90"}));
             PDDocument pdf = Loader.loadPDF(prepared.pdf.toFile())) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(3);
            assertThat(pdf.getPage(0).getRotation()).isEqualTo(90);
            assertThat(pdf.getPage(1).getRotation()).isEqualTo(180);
            assertThat(pdf.getPage(2).getRotation()).isEqualTo(270);
            assertThat(Files.getPosixFilePermissions(prepared.pdf)).containsExactlyInAnyOrder(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ, java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
        }
        assertThat(Files.readAllBytes(source)).isEqualTo(original);
        try (var files = Files.list(directory)) { assertThat(files.toList()).containsExactly(source); }
    }

    @ParameterizedTest @ValueSource(strings = {"0,0", "1,45", "1,90,2", "-1,0", "1,2147483648", "2147483648,0", "1,", " 1,0"})
    void malformedSelectionsFailBeforePdfWork(String selection) {
        assertThatThrownBy(() -> SplitDocumentPdfWork.selections(new String[]{selection})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void outOfRangePageLeavesNoPrivateArtifacts() throws Exception {
        Path source = source(1);
        assertThatThrownBy(() -> SplitDocumentPdfWork.prepareNow(source, directory, SPLIT,
                SplitDocumentPdfWork.selections(new String[]{"2,0"}))).isInstanceOf(IllegalArgumentException.class);
        try (var files = Files.list(directory)) { assertThat(files.toList()).containsExactly(source); }
    }

    @Test void splitPreservesIntrinsicRotationAndAddsOnlyTheVisibleSorterDelta() throws Exception {
        Path source = directory.resolve("rotated-source.pdf");
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage(); page.setRotation(270); pdf.addPage(page); pdf.save(source.toFile());
        }
        try (var prepared = SplitDocumentPdfWork.prepareNow(source, directory, SPLIT,
                SplitDocumentPdfWork.selections(new String[]{"1,0", "1,90", "1,180"}));
             PDDocument pdf = Loader.loadPDF(prepared.pdf.toFile())) {
            assertThat(pdf.getPage(0).getRotation()).isEqualTo(270);
            assertThat(pdf.getPage(1).getRotation()).isZero();
            assertThat(pdf.getPage(2).getRotation()).isEqualTo(90);
        }
    }

    @Test void removingOnlyPageIsRefusedWithoutChangingSource() throws Exception {
        Path source = source(1);
        byte[] original = Files.readAllBytes(source);
        assertThatThrownBy(() -> SplitDocumentPdfWork.prepareNow(source, directory, REMOVE_FIRST, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readAllBytes(source)).isEqualTo(original);
    }

    @Test void replacementPreservesCatalogAndConfirmedRollbackRestoresExactOriginal() throws Exception {
        Path source = source(2);
        byte[] original = Files.readAllBytes(source);
        try (var prepared = SplitDocumentPdfWork.prepareNow(source, directory, ROTATE_90, List.of())) {
            var publication = new SplitDocumentPdfWork.Publication(prepared, source, true);
            publication.publish();
            try (PDDocument pdf = Loader.loadPDF(source.toFile())) {
                assertThat(pdf.getPage(0).getRotation()).isEqualTo(90);
                assertThat(pdf.getDocumentInformation().getTitle()).isEqualTo("Preserved metadata");
            }
            publication.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            assertThat(publication.mutationStarted).isFalse();
            assertThat(Files.readAllBytes(source)).isEqualTo(original);
        }
    }

    @Test void committedRemoveReportsActualCountAndKeepsChangedFile() throws Exception {
        Path source = source(3);
        var mode = java.nio.file.attribute.PosixFilePermissions.fromString("r--------");
        Files.setPosixFilePermissions(source, mode);
        try (var prepared = SplitDocumentPdfWork.prepareNow(source, directory, REMOVE_FIRST, List.of())) {
            assertThat(prepared.pageCount).isEqualTo(2);
            var publication = new SplitDocumentPdfWork.Publication(prepared, source, true);
            publication.publish();
            publication.beforeCommit(false);
            publication.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            assertThat(publication.committed).isTrue();
        }
        try (PDDocument pdf = Loader.loadPDF(source.toFile())) { assertThat(pdf.getNumberOfPages()).isEqualTo(2); }
        assertThat(Files.getPosixFilePermissions(source)).isEqualTo(mode);
    }

    @Test void splitRollbackDeletesOnlyOurPublishedInode() throws Exception {
        Path source = source(1);
        try (var prepared = SplitDocumentPdfWork.prepareNow(source, directory, SPLIT,
                SplitDocumentPdfWork.selections(new String[]{"1,0"}))) {
            var publication = new SplitDocumentPdfWork.Publication(prepared, source, false);
            publication.publish();
            assertThat(Files.isSameFile(publication.target, prepared.pdf)).isTrue();
            publication.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            assertThat(publication.target).doesNotExist();
            assertThat(source).exists();
        }
    }

    @Test void collisionNeverOverwritesOrDeletesForeignFile() throws Exception {
        Path source = source(1);
        try (var prepared = SplitDocumentPdfWork.prepareNow(source, directory, SPLIT,
                SplitDocumentPdfWork.selections(new String[]{"1,0"}))) {
            var publication = new SplitDocumentPdfWork.Publication(prepared, source, false);
            Files.writeString(publication.target, "other import");
            assertThatThrownBy(publication::publish).isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
            publication.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            assertThat(Files.readString(publication.target)).isEqualTo("other import");
        }
    }

    @Test void uncertainCommitRetainsCompleteOutputAndOriginalForReconciliation() throws Exception {
        Path source = source(2);
        byte[] original = Files.readAllBytes(source);
        var prepared = SplitDocumentPdfWork.prepareNow(source, directory, REMOVE_FIRST, List.of());
        var publication = new SplitDocumentPdfWork.Publication(prepared, source, true);
        publication.publish();
        publication.beforeCommit(false);
        publication.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
        prepared.close();
        assertThat(publication.uncertain).isTrue();
        assertThat(publication.mutationStarted).isTrue();
        assertThat(Files.readAllBytes(prepared.directory.resolve("original.pdf"))).isEqualTo(original);
        assertThat(prepared.directory.resolve("published.pdf")).exists();
        assertThat(Files.readString(prepared.directory.resolve("recovery.txt"))).contains("Do not replay");
    }

    @Test void rollbackCannotEraseAnotherPublicationAtReusedName() throws Exception {
        Path source = source(1);
        var prepared = SplitDocumentPdfWork.prepareNow(source, directory, SPLIT,
                SplitDocumentPdfWork.selections(new String[]{"1,0"}));
        var publication = new SplitDocumentPdfWork.Publication(prepared, source, false);
        publication.publish();
        Files.delete(publication.target);
        Files.writeString(publication.target, "new owner");
        publication.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        prepared.close();
        assertThat(publication.uncertain).isTrue();
        assertThat(Files.readString(publication.target)).isEqualTo("new owner");
        assertThat(prepared.pdf).exists();
    }

    @Test void timedOutCallerDisposesLateWorkerWithoutPublication() throws Exception {
        Path source = source(1);
        var handoff = new SplitDocumentPdfWork.Handoff();
        handoff.close();
        var late = SplitDocumentPdfWork.prepareNow(source, directory, ROTATE_180, List.of());
        handoff.offer(late);
        assertThat(late.directory).doesNotExist();
        assertThatThrownBy(handoff::take).isInstanceOf(java.io.IOException.class);
        try (PDDocument pdf = Loader.loadPDF(source.toFile())) { assertThat(pdf.getPage(0).getRotation()).isZero(); }
    }
}
