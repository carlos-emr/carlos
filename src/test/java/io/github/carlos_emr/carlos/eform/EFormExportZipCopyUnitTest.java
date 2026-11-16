/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.eform.data.EForm;
import io.github.carlos_emr.carlos.eform.upload.ImageUpload2Action;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/** Exercises the actual copy helper and public ZIP round trips without a database. */
@Tag("unit")
@Tag("eform")
class EFormExportZipCopyUnitTest {
    @TempDir
    Path root;

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 8191, 8192, 8193, 1048576})
    void shouldCopyExactBytesInBoundedBulkWrites_forBoundarySizes(int size) {
        byte[] expected = bytes(size);
        CountingInput input = new CountingInput(expected, Integer.MAX_VALUE);
        CountingOutput output = new CountingOutput();

        ReflectionTestUtils.invokeMethod(new EFormExportZip(), "inputToOutput", input, output);

        assertThat(output.toByteArray()).isEqualTo(expected);
        assertThat(input.singleReads).isZero();
        assertThat(output.singleWrites).isZero();
        assertThat(output.bulkWrites).isEqualTo((size + 8191) / 8192);
        assertThat(output.largestWrite).isLessThanOrEqualTo(8192);
        assertThat(input.closed).isFalse();
        assertThat(output.closed).isFalse();
    }

    @Test
    void shouldCopyThroughEof_whenInputReturnsShortReads() {
        byte[] expected = bytes(32771);
        CountingInput input = new CountingInput(expected, 137);
        CountingOutput output = new CountingOutput();

        ReflectionTestUtils.invokeMethod(new EFormExportZip(), "inputToOutput", input, output);

        assertThat(output.toByteArray()).isEqualTo(expected);
        assertThat(output.singleWrites).isZero();
        assertThat(output.bulkWrites).isGreaterThan(1);
        assertThat(output.largestWrite).isLessThanOrEqualTo(137);
    }

    @ParameterizedTest
    @ValueSource(ints = {257, 4194304})
    void shouldRoundTripHtmlAndBinaryAsset_whenExportedThenImported(int assetSize) throws Exception {
        byte[] asset = bytes(assetSize);
        Path source = Files.write(root.resolve("fixture.png"), asset);
        Path images = Files.createDirectory(root.resolve("images"));
        String html = "<html><body><img src=\"${oscar_image_path}fixture.png\">"
                + "<p>Synthetic café — fixture</p>".repeat(assetSize / 32 + 1) + "</body></html>";
        EForm original = new EForm();
        original.setFormName("Bulk Copy Fixture");
        original.setFormFileName("fixture.html");
        original.setFormHtml(html);
        EFormExportZip exporter = new EFormExportZip() {
            @Override
            public java.io.File getImageFile(String name) {
                return source.toFile();
            }
        };
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        exporter.exportForms(List.of(original), archive);
        // Check the export's bytes independently before testing its importer.
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive.toByteArray()))) {
            ZipEntry entry;
            int entries = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().endsWith("/fixture.png")) {
                    assertThat(zip.readAllBytes()).isEqualTo(asset);
                } else if (entry.getName().endsWith("/fixture.html")) {
                    assertThat(zip.readAllBytes()).isEqualTo(html.getBytes(StandardCharsets.UTF_8));
                }
                entries++;
            }
            assertThat(entries).isEqualTo(3);
        }
        List<EForm> saved = new ArrayList<>();
        try (var imageFolder = mockStatic(ImageUpload2Action.class);
             var forms = mockStatic(EFormUtil.class)) {
            imageFolder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            forms.when(() -> EFormUtil.saveEForm(any(EForm.class))).thenAnswer(invocation -> {
                saved.add(invocation.getArgument(0));
                return "synthetic-fixture";
            });
            assertThat(new EFormExportZip().importForm(new ByteArrayInputStream(archive.toByteArray()))).isEmpty();
        }
        assertThat(saved).singleElement().satisfies(form -> {
            assertThat(form.getFormName()).isEqualTo(original.getFormName());
            assertThat(form.getFormHtml()).isEqualTo(html);
        });
        assertThat(Files.readAllBytes(images.resolve("fixture.png"))).isEqualTo(asset);
        try (var staging = Files.list(images.resolve("extractFolder"))) {
            assertThat(staging.toList()).isEmpty();
        }
    }

    @Test
    void shouldCleanStagingAndPublishNothing_whenArchiveReadFails() throws Exception {
        Path images = Files.createDirectory(root.resolve("images"));
        EForm form = new EForm();
        form.setFormName("Read Failure");
        form.setFormFileName("fixture.html");
        form.setFormHtml("<p>" + "fixture".repeat(1000) + "</p>");
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        new EFormExportZip().exportForms(List.of(form), archive);
        // The archive starts correctly but fails before extraction can finish.
        ByteArrayInputStream source = new ByteArrayInputStream(archive.toByteArray());
        java.io.InputStream failing = new java.io.InputStream() {
            private int remaining = archive.size() / 2;

            @Override
            public int read() throws IOException {
                if (remaining-- <= 0) throw new IOException("synthetic read failure");
                return source.read();
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                if (remaining <= 0) throw new IOException("synthetic read failure");
                int read = source.read(buffer, offset, Math.min(length, remaining));
                remaining -= read;
                return read;
            }
        };
        try (var imageFolder = mockStatic(ImageUpload2Action.class);
             var forms = mockStatic(EFormUtil.class)) {
            imageFolder.when(ImageUpload2Action::getImageFolder).thenReturn(images.toFile());
            assertThatThrownBy(() -> new EFormExportZip().importForm(failing)).isInstanceOf(IOException.class);
            forms.verify(() -> EFormUtil.saveEForm(any(EForm.class)), never());
        }
        try (var staging = Files.list(images.resolve("extractFolder"))) {
            assertThat(staging.toList()).isEmpty();
        }
        assertThat(images.resolve("fixture.html")).doesNotExist();
    }

    private static byte[] bytes(int size) {
        byte[] result = new byte[size];
        new Random(5310).nextBytes(result);
        return result;
    }

    private static class CountingInput extends ByteArrayInputStream {
        private final int chunkSize;
        private int singleReads;
        private boolean closed;

        CountingInput(byte[] bytes, int chunkSize) {
            super(bytes);
            this.chunkSize = chunkSize;
        }

        @Override
        public synchronized int read() {
            singleReads++;
            return super.read();
        }

        @Override
        public synchronized int read(byte[] bytes, int offset, int length) {
            return super.read(bytes, offset, Math.min(length, chunkSize));
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static class CountingOutput extends ByteArrayOutputStream {
        private int singleWrites;
        private int bulkWrites;
        private int largestWrite;
        private boolean closed;

        @Override
        public synchronized void write(int value) {
            singleWrites++;
            super.write(value);
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            bulkWrites++;
            largestWrite = Math.max(largestWrite, length);
            super.write(bytes, offset, length);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
