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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageMemberDto;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Crosses the real ZIP16 entry boundary without retaining a patient/member list. */
@Tag("integration") @Tag("cds-export")
class CdsExportZip64IntegrationTest {
    @TempDir Path root;
    @Test void shouldRoundTripMoreThan65535Entries_throughIndependentJavaZipInput() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        var store = new CdsExportStagingService(root, UUID.randomUUID().toString(), 1,
                new SecretKeySpec(new byte[32], "AES"), 32L << 20, 0, () -> {});
        var empty = store.stage("TEST_INPUT", out -> {}); final int count = 65537;
        CdsExportPackageMembers members = () -> new CdsExportPackageMembers.Cursor() {
            private int index;
            @Override public CdsExportPackageMemberDto next() {
                return index == count ? null : new CdsExportPackageMemberDto("files/f" + String.format(java.util.Locale.ROOT, "%05d", index++), empty);
            }
            @Override public void close() { }
        };
        var proof = new CdsExportPackageComposer().compose(store, members, 16L << 20, 100_000, () -> {});
        assertThat(proof.entries()).isEqualTo(count); assertThat(proof.expandedBytes()).isZero();
        int actual = 0;
        try (var input = store.open(proof.archive()); var zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                assertThat(entry.getName()).isEqualTo("files/f" + String.format(java.util.Locale.ROOT, "%05d", actual++));
                assertThat(zip.read()).isEqualTo(-1); zip.closeEntry();
            }
        }
        assertThat(actual).isEqualTo(count);
        // Verify a second independent library's central-directory/ZIP64 interpretation, not only local headers.
        Path synthetic = root.resolve("synthetic.zip");
        try (var input = store.open(proof.archive()); var output = Files.newOutputStream(synthetic)) { input.transferTo(output); }
        try (var zip = new java.util.zip.ZipFile(synthetic.toFile(), StandardCharsets.UTF_8)) {
            assertThat(zip.size()).isEqualTo(count); assertThat(zip.getEntry("files/f65536")).isNotNull();
        } finally { Files.delete(synthetic); }
        try (var files = Files.list(root)) { assertThat(files).hasSize(2); }
    }
}
