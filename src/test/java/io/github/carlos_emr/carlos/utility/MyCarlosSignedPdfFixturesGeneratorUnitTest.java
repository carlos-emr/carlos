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
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/** Filesystem lifecycle of the developer-only generator; no tracked fixture is regenerated. */
@Tag("unit")
@Tag("pdf")
class MyCarlosSignedPdfFixturesGeneratorUnitTest {
    @TempDir
    Path directory;

    @Test
    void shouldPublishCompleteJson_withAnExplicitOperatorPath() throws Exception {
        Path out = directory.resolve("selected output").resolve("fixture Ω.json");
        AtomicReference<Path> work = new AtomicReference<>();
        MyCarlosSignedPdfFixtures.generate(out, path -> {
            work.set(path);
            Files.writeString(path.resolve("FAKE-keystore"), "FAKE temporary key material");
            return "{\"complete\":true,\"generation\":2}\n";
        });
        assertThat(new ObjectMapper().readTree(out.toFile()).get("complete").asBoolean()).isTrue();
        assertThat(Files.exists(work.get())).isFalse();
        try (var siblings = Files.list(out.getParent())) {
            assertThat(siblings.toList()).containsExactly(out);
        }
    }

    @Test
    void shouldPreservePreviousOutput_whenRenderingFails() throws Exception {
        Path out = Files.writeString(directory.resolve("fixture.json"), "{\"previous\":true}\n");
        AtomicReference<Path> work = new AtomicReference<>();
        assertThatThrownBy(() -> MyCarlosSignedPdfFixtures.generate(out, path -> {
            work.set(path);
            Files.createDirectories(path.resolve("nested"));
            Files.writeString(path.resolve("nested/FAKE-keystore"), "FAKE temporary key material");
            throw new IOException("render failed");
        })).isInstanceOf(IOException.class).hasMessage("render failed");
        assertThat(Files.readString(out)).isEqualTo("{\"previous\":true}\n");
        assertThat(Files.exists(work.get())).isFalse();
        try (var siblings = Files.list(directory)) {
            assertThat(siblings.toList()).containsExactly(out);
        }
    }

    @Test
    void shouldPreservePreviousOutput_whenAtomicReplacementIsUnsupported() throws Exception {
        Path out = Files.writeString(directory.resolve("fixture.json"), "{\"previous\":true}\n");
        AtomicReference<Path> staged = new AtomicReference<>();
        AtomicReference<Path> work = new AtomicReference<>();
        assertThatThrownBy(() -> MyCarlosSignedPdfFixtures.generate(out, path -> {
            work.set(path);
            return "{\"complete\":true}\n";
        }, (complete, destination) -> {
            staged.set(complete);
            assertThat(complete.getParent()).isEqualTo(out.getParent());
            assertThat(Files.readString(complete)).isEqualTo("{\"complete\":true}\n");
            assertThat(Files.readString(destination)).isEqualTo("{\"previous\":true}\n");
            throw new AtomicMoveNotSupportedException("staging", "destination", "unsupported filesystem");
        })).isInstanceOf(AtomicMoveNotSupportedException.class);
        assertThat(Files.readString(out)).isEqualTo("{\"previous\":true}\n");
        assertThat(Files.exists(staged.get())).isFalse();
        assertThat(Files.exists(work.get())).isFalse();
        try (var siblings = Files.list(directory)) {
            assertThat(siblings.toList()).containsExactly(out);
        }
    }

    @Test
    void shouldKeepEachPublishedJsonComplete_withConcurrentGenerators() throws Exception {
        Path out = Files.writeString(directory.resolve("fixture.json"), "{\"generation\":\"previous\"}");
        CountDownLatch rendered = new CountDownLatch(2);
        CountDownLatch publish = new CountDownLatch(1);
        List<Path> workPaths = java.util.Collections.synchronizedList(new ArrayList<>());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tasks = List.of("first", "second").stream().map(name -> pool.submit(() -> {
                MyCarlosSignedPdfFixtures.generate(out, work -> {
                    workPaths.add(work);
                    rendered.countDown();
                    if (!publish.await(10, TimeUnit.SECONDS)) {
                        throw new IOException("publication was not released");
                    }
                    return "{\"generation\":\"" + name + "\",\"padding\":\"" + "x".repeat(65536) + "\"}";
                });
                return null;
            })).toList();
            assertThat(rendered.await(10, TimeUnit.SECONDS)).isTrue();
            publish.countDown();
            for (int i = 0; i < 30; i++) {
                assertThat(new ObjectMapper().readTree(out.toFile()).get("generation").asText())
                        .isIn("previous", "first", "second");
            }
            for (var task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
            assertThat(new ObjectMapper().readTree(out.toFile()).get("generation").asText()).isIn("first", "second");
            assertThat(workPaths).hasSize(2).allMatch(path -> !Files.exists(path));
            try (var siblings = Files.list(directory)) {
                assertThat(siblings.toList()).containsExactly(out);
            }
        } finally {
            publish.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shouldRejectDirectoryOutput_beforeRendering() {
        assertThatThrownBy(() -> MyCarlosSignedPdfFixtures.generate(directory, work -> {
            throw new AssertionError("directory output must be rejected before rendering");
        })).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRemoveEncryptedTemporaryPdf_whenRealRenderingSucceeds() throws Exception {
        AtomicReference<Path> encrypted = new AtomicReference<>();
        AtomicReference<Path> work = new AtomicReference<>();
        Path out = directory.resolve("generated-for-this-test.json");
        try (MockedStatic<PDFEncryptionUtil> encryption = mockStatic(PDFEncryptionUtil.class, CALLS_REAL_METHODS)) {
            encryption.when(() -> PDFEncryptionUtil.encryptPDF(any(Path.class), eq(MyCarlosSignedPdfFixtures.PASSWORD)))
                    .thenAnswer(call -> {
                        Path path = (Path) call.callRealMethod();
                        encrypted.set(path);
                        return path;
                    });
            MyCarlosSignedPdfFixtures.generate(out, path -> {
                work.set(path);
                return MyCarlosSignedPdfFixtures.render(path);
            });
        }
        assertThat(new ObjectMapper().readTree(out.toFile()).get("documents")).hasSize(12);
        assertThat(encrypted.get()).isNotNull();
        assertThat(Files.exists(encrypted.get())).isFalse();
        assertThat(Files.exists(work.get())).isFalse();
    }

    @Test
    void shouldRemoveEncryptedTemporaryPdf_whenSigningFails() throws Exception {
        AtomicReference<Path> encrypted = new AtomicReference<>();
        AtomicReference<Path> work = new AtomicReference<>();
        Path out = Files.writeString(directory.resolve("fixture.json"), "{\"previous\":true}\n");
        try (MockedStatic<PDFEncryptionUtil> encryption = mockStatic(PDFEncryptionUtil.class, CALLS_REAL_METHODS);
             MockedStatic<PDFSigningUtil> signing = mockStatic(PDFSigningUtil.class, CALLS_REAL_METHODS)) {
            encryption.when(() -> PDFEncryptionUtil.encryptPDF(any(Path.class), eq(MyCarlosSignedPdfFixtures.PASSWORD)))
                    .thenAnswer(call -> {
                        Path path = (Path) call.callRealMethod();
                        encrypted.set(path);
                        return path;
                    });
            signing.when(() -> PDFSigningUtil.signPDFWithManifestJson(any(Path.class), any(PDFSigningConfig.class),
                    any(), any(), eq(MyCarlosSignedPdfFixtures.SIGNATURE_SIZE))).thenThrow(new IOException("signing failed"));
            assertThatThrownBy(() -> MyCarlosSignedPdfFixtures.generate(out, path -> {
                work.set(path);
                return MyCarlosSignedPdfFixtures.render(path);
            })).isInstanceOf(IOException.class).hasMessage("signing failed");
        }
        assertThat(Files.readString(out)).isEqualTo("{\"previous\":true}\n");
        assertThat(encrypted.get()).isNotNull();
        assertThat(Files.exists(encrypted.get())).isFalse();
        assertThat(Files.exists(work.get())).isFalse();
    }
}
