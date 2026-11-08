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
package io.github.carlos_emr.carlos.demographic.cdsexport.cli;

import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit") @Tag("cds-export")
class CdsExportCliOutputUnitTest {
    @TempDir Path root;
    private CdsExportCliOutput output;
    @BeforeEach void privateFixture() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        output = new CdsExportCliOutput(directory -> {
            if (!directory.equals(root) || Files.isSymbolicLink(directory) || !Files.getPosixFilePermissions(directory).equals(PosixFilePermissions.fromString("rwx------"))) throw new IOException("PRIVATE_ROOT_REQUIRED");
            return directory.toRealPath();
        });
    }
    @Test void shouldInstallCompletePrivateBytes_andRemoveThePendingName() throws Exception {
        var target = root.resolve("transfer.p7m"); output.writeNew(target, stream -> stream.write(new byte[] {1,2,3}));
        assertThat(Files.readAllBytes(target)).containsExactly(1,2,3); assertThat(Files.getPosixFilePermissions(target)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
        try (var names = Files.list(root)) { assertThat(names.toList()).containsExactly(target); }
    }
    @Test void shouldRemoveAPartialWrite_withoutInstallingADestination() {
        var target = root.resolve("transfer.p7m");
        assertThatThrownBy(() -> output.writeNew(target, stream -> { stream.write(1); throw new IOException("WRITE_FAILED"); })).hasMessage("WRITE_FAILED");
        assertThat(target).doesNotExist(); try (var names = Files.list(root)) { assertThat(names.count()).isZero(); } catch (IOException failed) { throw new AssertionError(failed); }
    }
    @Test void shouldPreserveAnExistingDestination_withoutOpeningTheWriter() throws Exception {
        var target = root.resolve("transfer.p7m"); Files.writeString(target, "EXISTING");
        assertThatThrownBy(() -> output.writeNew(target, stream -> { throw new AssertionError("Writer must not be called"); })).hasMessage("CDS_EXPORT_OUTPUT_EXISTS");
        assertThat(Files.readString(target)).isEqualTo("EXISTING");
    }
    @Test void shouldRefuseASymlinkDestination_withoutChangingItsTarget() throws Exception {
        var original = root.resolve("original"); Files.writeString(original, "EXISTING"); var target = root.resolve("transfer.p7m"); Files.createSymbolicLink(target, original);
        assertThatThrownBy(() -> output.writeNew(target, stream -> stream.write(1))).hasMessage("CDS_EXPORT_OUTPUT_EXISTS"); assertThat(Files.readString(original)).isEqualTo("EXISTING");
    }
    @Test void shouldPreserveAConcurrentDestination_andRemoveOnlyItsOwnPendingFile() throws Exception {
        var target = root.resolve("transfer.p7m");
        assertThatThrownBy(() -> output.writeNew(target, stream -> { stream.write(1); Files.writeString(target, "CONCURRENT"); })).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(target)).isEqualTo("CONCURRENT"); try (var names = Files.list(root)) { assertThat(names.toList()).containsExactly(target); }
    }
    @Test void shouldRefuseANonPrivateOutputDirectory_beforeWriting() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwxr-xr-x"));
        assertThatThrownBy(() -> output.writeNew(root.resolve("transfer.p7m"), stream -> { throw new AssertionError("Writer must not be called"); })).hasMessage("PRIVATE_ROOT_REQUIRED");
    }
}
