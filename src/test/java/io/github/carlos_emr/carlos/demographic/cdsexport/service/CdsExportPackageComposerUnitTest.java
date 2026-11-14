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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageMemberDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportPackageValidator;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipInputStream;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportPackageComposerUnitTest {
    @TempDir Path root;
    private CdsExportStagingService store;

    @BeforeEach
    void prepare() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        store =
                new CdsExportStagingService(
                        root,
                        UUID.randomUUID().toString(),
                        1,
                        new SecretKeySpec(new byte[32], "AES"),
                        16L << 20,
                        0,
                        () -> {});
    }

    @Test
    void shouldProduceAnIndependentlyReadableFinishedZip_andEraseTemporaryReceipts()
            throws Exception {
        var members =
                List.of(
                        member("Group/Patient.xml", "patient é 🩺"),
                        member("ReadMe.txt", "readme"));
        var proof = compose(members);
        assertThat(proof.entries()).isEqualTo(2);
        try (var input = store.open(proof.archive());
                var zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("Group/Patient.xml");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("patient é 🩺");
            assertThat(zip.getNextEntry().getName()).isEqualTo("ReadMe.txt");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("readme");
            assertThat(zip.getNextEntry()).isNull();
        }
        try (var files = Files.list(root)) {
            assertThat(files).hasSize(3);
        }
    }

    @Test
    void shouldRefuseCaseAndUnicodePathCollisions_withoutReturningAnArchive() throws Exception {
        for (var paths :
                List.of(
                        List.of("a.xml", "A.xml"),
                        List.of("é.xml", "é.xml"),
                        List.of("a", "a/b"))) {
            var members = List.of(member(paths.getFirst(), "one"), member(paths.getLast(), "two"));
            assertThatThrownBy(() -> compose(members))
                    .hasMessage("CDS_EXPORT_PACKAGE_PATH_COLLISION_OR_ORDER");
        }
    }

    @Test
    void shouldRefuseUnsortedOrUnsafePaths_insteadOfRenamingThem() throws Exception {
        for (String path :
                List.of(
                        "../x",
                        "a//b",
                        "a/./b",
                        "/absolute",
                        "C:/x",
                        "a\\b",
                        "a/CON.xml",
                        "a./b",
                        "a/ b")) {
            assertThatThrownBy(() -> compose(List.of(member(path, "one"))))
                    .hasMessage("CDS_EXPORT_PACKAGE_PATH_INVALID");
        }
        assertThatThrownBy(() -> compose(List.of(member("z", "one"), member("a", "two"))))
                .hasMessage("CDS_EXPORT_PACKAGE_PATH_COLLISION_OR_ORDER");
    }

    @Test
    void shouldRefuseTruncatedCentralDirectory_evenWhenLocalEntriesAreReadable() throws Exception {
        var members = List.of(member("a", "payload"));
        var proof = compose(members);
        byte[] bytes;
        try (var input = store.open(proof.archive())) {
            bytes = input.readAllBytes();
        }
        int central = index(bytes, 0x02014b50);
        byte[] prefix = Arrays.copyOf(bytes, central);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(prefix))) {
            assertThat(zip.getNextEntry()).isNotNull();
            assertThat(zip.readAllBytes()).isEqualTo("payload".getBytes(StandardCharsets.UTF_8));
        }
        assertThatThrownBy(() -> validateReplacement(prefix, members, proof))
                .hasMessage("CDS_EXPORT_PACKAGE_TRUNCATED");
    }

    @Test
    void shouldRefuseChangedCentralCrcAndLocalOffset_andTrailingBytes() throws Exception {
        var members = List.of(member("a", "payload"));
        var proof = compose(members);
        byte[] bytes;
        try (var input = store.open(proof.archive())) {
            bytes = input.readAllBytes();
        }
        for (int offset :
                new int[] {index(bytes, 0x02014b50) + 16, index(bytes, 0x02014b50) + 42}) {
            byte[] changed = bytes.clone();
            changed[offset] ^= 1;
            assertThatThrownBy(() -> validateReplacement(changed, members, proof))
                    .hasMessage("CDS_EXPORT_PACKAGE_INVALID");
        }
        assertThatThrownBy(
                        () ->
                                validateReplacement(
                                        Arrays.copyOf(bytes, bytes.length + 1), members, proof))
                .hasMessage("CDS_EXPORT_PACKAGE_INVALID");
    }

    @Test
    void shouldRefuseChangedPayload_evenWhenTheStagedArchiveHashIsValid() throws Exception {
        var members = List.of(member("a", "payload"));
        var proof = compose(members);
        byte[] bytes;
        try (var input = store.open(proof.archive())) {
            bytes = input.readAllBytes();
        }
        bytes[31] ^= 1;
        assertThatThrownBy(() -> validateReplacement(bytes, members, proof))
                .hasMessage("CDS_EXPORT_PACKAGE_INVALID");
    }

    @Test
    void shouldRefuseAnInventoryThatChangesAfterComposition_orBeforeCentralValidation()
            throws Exception {
        var first = List.of(member("a", "one"));
        var different = List.of(member("a", "two"));
        for (int changeAt : new int[] {2, 3}) {
            AtomicInteger opens = new AtomicInteger();
            CdsExportPackageMembers source =
                    () -> source(opens.incrementAndGet() >= changeAt ? different : first).open();
            long before;
            try (var files = Files.list(root)) {
                before = files.count();
            }
            assertThatThrownBy(
                            () ->
                                    new CdsExportPackageComposer()
                                            .compose(store, source, () -> {}))
                    .hasMessage("CDS_EXPORT_PACKAGE_INVALID");
            try (var files = Files.list(root)) {
                assertThat(files.count()).isEqualTo(before);
            }
        }
    }

    @Test
    void shouldComposeTheArchive_withoutAVolumeOrEntryCeiling() throws Exception {
        // Ten bytes of archive and one entry were once refusals; no size or count is now.
        var members = List.of(member("a", "one"), member("b", "two"));
        var proof = new CdsExportPackageComposer().compose(store, source(members), () -> {});
        assertThat(proof.entries()).isEqualTo(2);
        assertThat(proof.archive().plaintextBytes()).isGreaterThan(10);
    }

    @Test
    void shouldPropagateCancellation_andRetainOnlyCapturedInputs() throws Exception {
        var members = List.of(member("a", "one"), member("b", "two"));
        AtomicInteger probes = new AtomicInteger();
        assertThatThrownBy(
                        () ->
                                new CdsExportPackageComposer()
                                        .compose(
                                                store,
                                                source(members),
                                                () -> {
                                                    if (probes.incrementAndGet() == 5)
                                                        throw new IllegalStateException(
                                                                "CDS_EXPORT_CANCELLED");
                                                }))
                .hasMessage("CDS_EXPORT_CANCELLED");
        try (var files = Files.list(root)) {
            assertThat(files).hasSize(2);
        }
    }

    private CdsExportPackageDto compose(List<CdsExportPackageMemberDto> members) throws Exception {
        return new CdsExportPackageComposer()
                .compose(store, source(members), () -> {});
    }

    private void validateReplacement(
            byte[] bytes, List<CdsExportPackageMemberDto> members, CdsExportPackageDto proof)
            throws Exception {
        var changed = store.stage("PACKAGE_ZIP", output -> output.write(bytes));
        new CdsExportPackageValidator()
                .validate(
                        store,
                        source(members),
                        new CdsExportPackageDto(
                                changed,
                                proof.entries(),
                                proof.expandedBytes(),
                                proof.membersSha256()),
                        () -> {});
    }

    private CdsExportPackageMemberDto member(String name, String content) throws Exception {
        return new CdsExportPackageMemberDto(
                name,
                store.stage(
                        "TEST_INPUT", out -> out.write(content.getBytes(StandardCharsets.UTF_8))));
    }

    static CdsExportPackageMembers source(List<CdsExportPackageMemberDto> members) {
        return () ->
                new CdsExportPackageMembers.Cursor() {
                    private final java.util.Iterator<CdsExportPackageMemberDto> iterator =
                            members.iterator();

                    @Override
                    public CdsExportPackageMemberDto next() {
                        return iterator.hasNext() ? iterator.next() : null;
                    }

                    @Override
                    public void close() {}
                };
    }

    private static int index(byte[] bytes, int signature) {
        for (int i = 0; i <= bytes.length - 4; i++) {
            int found =
                    (bytes[i] & 255)
                            | (bytes[i + 1] & 255) << 8
                            | (bytes[i + 2] & 255) << 16
                            | (bytes[i + 3] & 255) << 24;
            if (found == signature) return i;
        }
        throw new AssertionError("Missing test ZIP signature");
    }
}
