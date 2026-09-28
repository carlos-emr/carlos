/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("eform")
class EFormExportZipConcurrentImportUnitTest {
    @TempDir
    Path images;

    private record Result(List<String> errors, List<String> savedHtml) { }

    private static byte[] zip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] form(String name, String html) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("fixture/eform.properties", "form.name=" + name + "\nform.htmlFilename=form.html\n");
        entries.put("fixture/form.html", html);
        return zip(entries);
    }

    /** Pause after actual extraction, before the real importer reads/publishes its files. */
    private static class PausedZip extends ByteArrayInputStream {
        private final CountDownLatch extracted;
        private final CountDownLatch proceed;

        PausedZip(byte[] bytes, CountDownLatch extracted, CountDownLatch proceed) {
            super(bytes);
            this.extracted = extracted;
            this.proceed = proceed;
        }

        @Override
        public void close() throws IOException {
            extracted.countDown();
            try {
                if (!proceed.await(15, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting for the test's import handoff");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Import handoff interrupted", e);
            }
            super.close();
        }
    }

    private Result runImport(byte[] bytes, CountDownLatch extracted, CountDownLatch proceed) throws Exception {
        List<String> saved = new ArrayList<>();
        // All mocks are installed in the import's own thread. Pin the historical
        // millisecond directory name so the old collision is deterministic even
        // on a slow test host; atomic directory allocation is clock-independent.
        try (MockedStatic<ImageUpload2Action> imageFolder = mockStatic(ImageUpload2Action.class);
             MockedStatic<EFormUtil> forms = mockStatic(EFormUtil.class);
             MockedConstruction<SimpleDateFormat> clock = mockConstruction(SimpleDateFormat.class,
                     (formatter, context) -> when(formatter.format(any(Date.class))).thenReturn("same-millisecond"))) {
            imageFolder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            forms.when(() -> EFormUtil.saveEForm(any(EForm.class))).thenAnswer(invocation -> {
                saved.add(invocation.<EForm>getArgument(0).getFormHtml());
                return "owned-fixture";
            });
            List<String> errors = new EFormExportZip().importForm(new PausedZip(bytes, extracted, proceed));
            return new Result(errors, saved);
        }
    }

    private List<Path> stagingDirectories() throws IOException {
        try (Stream<Path> paths = Files.list(images.resolve("extractFolder"))) {
            return paths.toList();
        }
    }

    @Test
    void simultaneousImportsKeepTheirOwnHtmlAndCleanupCannotDeleteAnotherImportsFiles() throws Exception {
        CountDownLatch extracted = new CountDownLatch(2);
        CountDownLatch firstProceed = new CountDownLatch(1);
        CountDownLatch secondProceed = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Result> first = workers.submit(() -> runImport(form("First", "FIRST-OWNED-HTML"), extracted, firstProceed));
            Future<Result> second = workers.submit(() -> runImport(form("Second", "SECOND-OWNED-HTML"), extracted, secondProceed));
            assertThat(extracted.await(15, TimeUnit.SECONDS)).isTrue();
            List<Path> staged = stagingDirectories();
            assertThat(staged).hasSize(2);
            List<String> contents = new ArrayList<>();
            for (Path directory : staged) {
                contents.add(Files.readString(directory.resolve("form.html")));
            }
            assertThat(contents).containsExactlyInAnyOrder("FIRST-OWNED-HTML", "SECOND-OWNED-HTML");

            firstProceed.countDown();
            Result completed = first.get(15, TimeUnit.SECONDS);
            assertThat(completed.errors()).isEmpty();
            assertThat(completed.savedHtml()).containsExactly("FIRST-OWNED-HTML");
            List<Path> stillOwned = stagingDirectories();
            assertThat(stillOwned).hasSize(1);
            assertThat(Files.readString(stillOwned.getFirst().resolve("form.html"))).isEqualTo("SECOND-OWNED-HTML");

            secondProceed.countDown();
            Result other = second.get(15, TimeUnit.SECONDS);
            assertThat(other.errors()).isEmpty();
            assertThat(other.savedHtml()).containsExactly("SECOND-OWNED-HTML");
            assertThat(stagingDirectories()).isEmpty();
        } finally {
            firstProceed.countDown();
            secondProceed.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void failedImportCleansOnlyItsOwnStagingAndLeavesAnotherImportUsable() throws Exception {
        CountDownLatch extracted = new CountDownLatch(2);
        CountDownLatch invalidProceed = new CountDownLatch(1);
        CountDownLatch validProceed = new CountDownLatch(1);
        Map<String, String> malicious = new LinkedHashMap<>();
        malicious.put("fixture/staged.png", "FAILED-IMPORT-ONLY");
        malicious.put("../escape.png", "MUST-NOT-ESCAPE");
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Result> invalid = workers.submit(() -> runImport(zip(malicious), extracted, invalidProceed));
            Future<Result> valid = workers.submit(() -> runImport(form("Valid", "PRESERVED-HTML"), extracted, validProceed));
            assertThat(extracted.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(stagingDirectories()).hasSize(2);
            invalidProceed.countDown();
            assertThatThrownBy(() -> invalid.get(15, TimeUnit.SECONDS)).hasCauseInstanceOf(SecurityException.class);
            List<Path> remaining = stagingDirectories();
            assertThat(remaining).hasSize(1);
            assertThat(Files.readString(remaining.getFirst().resolve("form.html"))).isEqualTo("PRESERVED-HTML");
            assertThat(images.resolve("escape.png")).doesNotExist();
            assertThat(images.resolve("staged.png")).doesNotExist();
            validProceed.countDown();
            assertThat(valid.get(15, TimeUnit.SECONDS).savedHtml()).containsExactly("PRESERVED-HTML");
            assertThat(stagingDirectories()).isEmpty();
        } finally {
            invalidProceed.countDown();
            validProceed.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void simultaneousImportsOfOneImageKeepOneCompleteWinnerAndReportTheOtherCollision() throws Exception {
        CountDownLatch extracted = new CountDownLatch(2);
        CountDownLatch proceed = new CountDownLatch(1);
        String firstBytes = "FIRST".repeat(1000);
        String secondBytes = "SECOND".repeat(1000);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Result> first = workers.submit(() -> runImport(zip(Map.of("shared.png", firstBytes)), extracted, proceed));
            Future<Result> second = workers.submit(() -> runImport(zip(Map.of("shared.png", secondBytes)), extracted, proceed));
            assertThat(extracted.await(15, TimeUnit.SECONDS)).isTrue();
            proceed.countDown();
            List<String> errors = new ArrayList<>(first.get(15, TimeUnit.SECONDS).errors());
            errors.addAll(second.get(15, TimeUnit.SECONDS).errors());
            assertThat(errors).singleElement().asString().contains("shared.png", "already exists");
            assertThat(Files.readString(images.resolve("shared.png"))).isIn(firstBytes, secondBytes);
            assertThat(stagingDirectories()).isEmpty();
        } finally {
            proceed.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void staleAbsenceObservationCannotOverwriteAnotherWritersNewImage() throws Exception {
        Path destination = images.resolve("shared.png");
        Files.writeString(destination, "OTHER-WRITERS-COMPLETE-IMAGE");
        File stale = mock(File.class);
        when(stale.exists()).thenReturn(false); // The old check-then-open could observe this race.
        when(stale.toPath()).thenReturn(destination);
        when(stale.getPath()).thenReturn(destination.toString());
        try (MockedStatic<ImageUpload2Action> imageFolder = mockStatic(ImageUpload2Action.class);
             MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            imageFolder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            paths.when(() -> PathValidationUtils.validateGeneratedChildPath("shared.png", images.toFile())).thenReturn(stale);
            List<String> errors = new EFormExportZip().importForm(new ByteArrayInputStream(zip(Map.of("shared.png", "REPLACEMENT"))));
            assertThat(errors).singleElement().asString().contains("shared.png", "already exists");
        }
        assertThat(Files.readString(destination)).isEqualTo("OTHER-WRITERS-COMPLETE-IMAGE");
        assertThat(stagingDirectories()).isEmpty();
    }

    @Test
    void unusableSharedStagingParentFailsBeforeAnyImagePublication() throws Exception {
        Path occupied = images.resolve("extractFolder");
        Files.writeString(occupied, "EXISTING-FILE");
        try (MockedStatic<ImageUpload2Action> imageFolder = mockStatic(ImageUpload2Action.class)) {
            imageFolder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            assertThatThrownBy(() -> new EFormExportZip().importForm(
                    new ByteArrayInputStream(zip(Map.of("new.png", "UNPUBLISHED")))))
                    .isInstanceOf(IOException.class);
        }
        assertThat(Files.readString(occupied)).isEqualTo("EXISTING-FILE");
        assertThat(images.resolve("new.png")).doesNotExist();
    }

    @Test
    void publicImageAppearsCompleteAtPublicationAndSurvivesPrivateStagingCleanup() throws Exception {
        Path destination = images.resolve("complete.png");
        String completeBytes = "COMPLETE-ASSET-".repeat(10000);
        java.util.concurrent.atomic.AtomicReference<Path> staged = new java.util.concurrent.atomic.AtomicReference<>();
        try (MockedStatic<ImageUpload2Action> imageFolder = mockStatic(ImageUpload2Action.class);
             MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            imageFolder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            files.when(() -> Files.createLink(eq(destination), any(Path.class))).thenAnswer(invocation -> {
                Path source = invocation.getArgument(1);
                staged.set(source);
                assertThat(destination).doesNotExist();
                assertThat(Files.readString(source)).isEqualTo(completeBytes);
                Path published = (Path) invocation.callRealMethod();
                // Inspect while the real importer still owns its staging directory, before its
                // method returns: no reader can observe an empty or partly copied public asset.
                assertThat(Files.readString(destination)).isEqualTo(completeBytes);
                assertThat(Files.isSameFile(destination, source)).isTrue();
                return published;
            });
            assertThat(new EFormExportZip().importForm(new ByteArrayInputStream(
                    zip(Map.of("complete.png", completeBytes))))).isEmpty();
        }
        assertThat(staged.get()).isNotNull().doesNotExist();
        assertThat(Files.readString(destination)).isEqualTo(completeBytes);
        assertThat(stagingDirectories()).isEmpty();
    }

    @Test
    void failedAtomicPublicationLeavesNoPartialAssetAndDoesNotSaveAForm() throws Exception {
        assertPublicationFailure(new IOException("storage refused publication"));
    }

    @Test
    void unsupportedAtomicPublicationFailsClosedWithoutDeletingAnotherImage() throws Exception {
        assertPublicationFailure(new UnsupportedOperationException("hard links unavailable"));
    }

    private void assertPublicationFailure(Exception failure) throws Exception {
        Path destination = images.resolve("unpublished.png");
        Path unrelated = Files.writeString(images.resolve("other.png"), "OTHER-IMPORT-COMPLETE");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("fixture/eform.properties", "form.name=Owned\nform.htmlFilename=form.html\n");
        entries.put("fixture/form.html", "<img src='unpublished.png'>");
        entries.put("fixture/unpublished.png", "FULL-PRIVATE-ASSET");
        try (MockedStatic<ImageUpload2Action> imageFolder = mockStatic(ImageUpload2Action.class);
             MockedStatic<EFormUtil> forms = mockStatic(EFormUtil.class);
             MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            imageFolder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            files.when(() -> Files.createLink(eq(destination), any(Path.class))).thenThrow(failure);
            assertThatThrownBy(() -> new EFormExportZip().importForm(new ByteArrayInputStream(zip(entries))))
                    .isInstanceOf(IOException.class);
            forms.verify(() -> EFormUtil.saveEForm(any(EForm.class)), never());
        }
        assertThat(destination).doesNotExist();
        assertThat(Files.readString(unrelated)).isEqualTo("OTHER-IMPORT-COMPLETE");
        assertThat(stagingDirectories()).isEmpty();
    }

    @Test
    void laterFormSaveFailureCannotEraseAnAlreadyPublishedOrUnrelatedAsset() throws Exception {
        Path unrelated = Files.writeString(images.resolve("other.png"), "OTHER-IMPORT-COMPLETE");
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("fixture/eform.properties", "form.name=Owned\nform.htmlFilename=form.html\n");
        entries.put("fixture/form.html", "<img src='published.png'>");
        entries.put("fixture/published.png", "COMPLETE-PUBLISHED-ASSET");
        try (MockedStatic<ImageUpload2Action> imageFolder = mockStatic(ImageUpload2Action.class);
             MockedStatic<EFormUtil> forms = mockStatic(EFormUtil.class)) {
            imageFolder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            forms.when(() -> EFormUtil.saveEForm(any(EForm.class))).thenThrow(new IllegalStateException("database unavailable"));
            assertThatThrownBy(() -> new EFormExportZip().importForm(new ByteArrayInputStream(zip(entries))))
                    .isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");
        }
        assertThat(Files.readString(images.resolve("published.png"))).isEqualTo("COMPLETE-PUBLISHED-ASSET");
        assertThat(Files.readString(unrelated)).isEqualTo("OTHER-IMPORT-COMPLETE");
        assertThat(stagingDirectories()).isEmpty();
    }
}
