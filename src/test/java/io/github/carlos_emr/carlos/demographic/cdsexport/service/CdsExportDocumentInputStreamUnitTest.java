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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Filesystem security tests require the real trusted process/root ownership used in production. */
@Tag("unit")
@Tag("cds-export")
class CdsExportDocumentInputStreamUnitTest {
    private Path root;
    private final byte[] body =
            "Complete native report 漢字 😀\n".repeat(6000).getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setup() throws Exception {
        root =
                Files.createTempDirectory(
                        // A checkout can have writable bind-mount ancestors. The source
                        // reader must still reject those; place only this owned fixture
                        // under the process home unless the harness supplies its root.
                        Path.of(
                                        System.getProperty(
                                                "cds.export.test.documentRoot",
                                                System.getProperty("user.home")))
                                .toAbsolutePath(),
                        "owned-native-document-",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
        Files.write(root.resolve("report.bin"), body);
        Files.setPosixFilePermissions(
                root.resolve("report.bin"), PosixFilePermissions.fromString("rw-r-----"));
    }

    @AfterEach
    void cleanup() throws Exception {
        if (root != null)
            try (var paths = Files.walk(root)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
    }

    private CdsExportDocumentInputStream open(String name) throws IOException {
        return new CdsExportDocumentInputStream(root, name, 4L << 20, () -> {});
    }

    @Test
    void shouldStreamEveryByte_withBoundedReadsAndCheckpoints() throws Exception {
        var probes = new AtomicInteger();
        try (var input =
                new CdsExportDocumentInputStream(
                        root, "report.bin", 4L << 20, probes::incrementAndGet)) {
            assertThat(input.readAllBytes()).isEqualTo(body);
            assertThat(input.read()).isEqualTo(-1);
        }
        assertThat(probes.get()).isGreaterThan(4);
    }

    @Test
    void shouldCloseEarly_onlyAfterDrainingAndVerifyingTheWholeFile() throws Exception {
        var probes = new AtomicInteger();
        try (var input =
                new CdsExportDocumentInputStream(
                        root, "report.bin", 4L << 20, probes::incrementAndGet)) {
            assertThat(input.read()).isEqualTo(body[0] & 255);
        }
        assertThat(probes.get()).isGreaterThan(4);
    }

    @Test
    void shouldAuthenticateAnEmptyFile_asPresentRatherThanMissing() throws Exception {
        Files.write(root.resolve("empty.bin"), new byte[0]);
        try (var input = open("empty.bin")) {
            assertThat(input.read()).isEqualTo(-1);
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "../report.bin",
                "/report.bin",
                "a/../report.bin",
                "./report.bin",
                "a\\report.bin",
                "file:report.bin",
                "",
                ".."
            })
    void shouldRefuseTraversalAbsoluteAndAmbiguousPaths_forDocumentInputStreamContract(
            String name) {
        assertThatThrownBy(() -> open(name)).hasMessage("CDS_EXPORT_DOCUMENT_SOURCE_UNAVAILABLE");
    }

    @Test
    void shouldRefuseASymlinkFile_withoutOpeningItsTarget() throws Exception {
        Files.createSymbolicLink(root.resolve("alias.bin"), Path.of("report.bin"));
        assertThatThrownBy(() -> open("alias.bin"))
                .hasMessage("CDS_EXPORT_DOCUMENT_SOURCE_UNAVAILABLE");
    }

    @Test
    void shouldRefuseASymlinkDirectory_withoutFollowingIt() throws Exception {
        Files.createDirectory(root.resolve("real"));
        Files.write(root.resolve("real/report.bin"), body);
        Files.createSymbolicLink(root.resolve("alias"), Path.of("real"));
        assertThatThrownBy(() -> open("alias/report.bin"))
                .hasMessage("CDS_EXPORT_DOCUMENT_SOURCE_UNAVAILABLE");
    }

    @Test
    void shouldSupportSafeNestedNativeFiles_forDocumentInputStreamContract() throws Exception {
        Files.createDirectory(root.resolve("nested"));
        Files.write(root.resolve("nested/report.bin"), body);
        try (var input = open("nested/report.bin")) {
            assertThat(input.readAllBytes()).isEqualTo(body);
        }
    }

    @Test
    void shouldRefuseAFileLargerThanTheExplicitByteBudget_forDocumentInputStreamContract() {
        assertThatThrownBy(() -> new CdsExportDocumentInputStream(root, "report.bin", 2, () -> {}))
                .hasMessage("CDS_EXPORT_DOCUMENT_SOURCE_UNAVAILABLE");
    }

    @Test
    void shouldRefuseAFileReplacedWhileItsOriginalDescriptorIsOpen_forDocumentInputStreamContract()
            throws Exception {
        try (var input = open("report.bin")) {
            input.read();
            Files.move(root.resolve("report.bin"), root.resolve("original.bin"));
            Files.write(root.resolve("report.bin"), body);
            assertThatThrownBy(input::readAllBytes).isInstanceOf(IOException.class);
        }
    }

    @Test
    void shouldRefuseTruncation_duringARead() throws Exception {
        try (var input = open("report.bin")) {
            input.read();
            Files.write(root.resolve("report.bin"), new byte[] {1});
            assertThatThrownBy(input::readAllBytes).isInstanceOf(IOException.class);
        }
    }

    @Test
    void shouldRefuseGrowth_duringARead() throws Exception {
        try (var input = open("report.bin")) {
            input.read();
            Files.write(root.resolve("report.bin"), new byte[] {1}, StandardOpenOption.APPEND);
            assertThatThrownBy(input::readAllBytes).isInstanceOf(IOException.class);
        }
    }

    @Test
    void shouldCheckTheFileAgainAtClose_afterAnEarlierCompleteEof() throws Exception {
        var input = open("report.bin");
        input.readAllBytes();
        Files.write(root.resolve("report.bin"), new byte[] {1}, StandardOpenOption.APPEND);
        assertThatThrownBy(input::close).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseNewWritePermissions_atTheEndOfCapture() throws Exception {
        try (var input = open("report.bin")) {
            input.read();
            Files.setPosixFilePermissions(
                    root.resolve("report.bin"), PosixFilePermissions.fromString("rw-rw----"));
            assertThatThrownBy(input::readAllBytes).isInstanceOf(IOException.class);
        }
    }

    @Test
    void shouldPropagateCancellation_fromEveryStreamPhase() throws Exception {
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        try (var input =
                new CdsExportDocumentInputStream(
                        root,
                        "report.bin",
                        4L << 20,
                        () -> {
                            if (cancelled.get())
                                throw new java.util.concurrent.CancellationException("CANCELLED");
                        })) {
            cancelled.set(true);
            assertThatThrownBy(input::read).hasMessage("CANCELLED");
        }
    }
}
