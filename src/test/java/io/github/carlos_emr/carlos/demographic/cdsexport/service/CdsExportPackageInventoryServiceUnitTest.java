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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageMemberDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit") @Tag("cds-export")
class CdsExportPackageInventoryServiceUnitTest {
    @TempDir Path root;
    private CdsExportStagingService store;
    private CdsExportStagedFileDto payload;
    private final CdsExportPackageInventoryService service = new CdsExportPackageInventoryService();
    @BeforeEach void prepare() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        store = new CdsExportStagingService(root, UUID.randomUUID().toString(), 1, new SecretKeySpec(new byte[32], "AES"), 128L << 20, 0, () -> {});
        payload = store.stage("PATIENT_XML", output -> output.write(new byte[]{1, 2, 3}));
    }
    private CdsExportPackageMemberDto member(String path) { return new CdsExportPackageMemberDto(path, payload); }
    private List<String> paths(CdsExportPackageMembers inventory) throws IOException {
        var result = new ArrayList<String>();
        try (var input = inventory.open()) { CdsExportPackageMemberDto member; while ((member = input.next()) != null) result.add(member.relativePath()); }
        return result;
    }
    private long files() throws IOException { try (var files = Files.list(root)) { return files.count(); } }
    @Test void shouldSortByCanonicalComponents_andPreserveOriginalNamesAndExactMemberDescriptors() throws Exception {
        var sealed = service.seal(store, 100, 1L << 20, () -> {}, output -> {
            output.accept(member("Zed/second.xml")); output.accept(member("Élodie/first.xml")); output.accept(member("alpha/one.xml"));
        });
        assertThat(paths(sealed)).containsExactly("alpha/one.xml", "Zed/second.xml", "Élodie/first.xml");
        assertThat(paths(sealed)).isEqualTo(paths(sealed)); assertThat(sealed.entries()).isEqualTo(3); assertThat(files()).isEqualTo(2);
        assertThat(sealed.toString()).isEqualTo("CdsExportSealedPackageInventory[protected]");
        try (var input = sealed.open()) { assertThat(input.next().file()).isEqualTo(payload); }
    }
    @Test void shouldRejectCaseAndUnicodeCollisions_withoutRemovingCallerOwnedPayloads() throws Exception {
        for (var names : List.of(List.of("Alpha/one.xml", "alpha/ONE.xml"), List.of("Élodie/one.xml", "Élodie/one.xml"))) {
            assertThatThrownBy(() -> service.seal(store, 100, 1L << 20, () -> {}, output -> {
                for (String name : names) output.accept(member(name));
            })).hasMessage("CDS_EXPORT_PACKAGE_PATH_COLLISION_OR_ORDER");
            assertThat(files()).isEqualTo(1);
            try (var input = store.open(payload)) { assertThat(input.readAllBytes()).containsExactly((byte) 1, (byte) 2, (byte) 3); }
        }
    }
    @Test void shouldRejectFileDirectoryAliases_acrossSortedComponents() {
        assertThatThrownBy(() -> service.seal(store, 100, 1L << 20, () -> {}, output -> {
            output.accept(member("a/file/child.xml")); output.accept(member("a/file"));
        })).hasMessage("CDS_EXPORT_PACKAGE_PATH_COLLISION_OR_ORDER");
    }
    @Test void shouldRefusePrivateSourceMembers_insteadOfAddingThemToTheClinicalArchive() throws Exception {
        var source = store.stage("SOURCE_VALUES", output -> output.write(1)); long before = files();
        assertThatThrownBy(() -> service.seal(store, 100, 1L << 20, () -> {}, output -> output.accept(new CdsExportPackageMemberDto("source.xml", source))))
                .hasMessage("CDS_EXPORT_PACKAGE_INVENTORY_INVALID");
        assertThat(files()).isEqualTo(before);
    }
    @Test void shouldEnforceEntryAndTotalIndexByteLimits_withoutTruncatingTheInventory() throws Exception {
        assertThatThrownBy(() -> service.seal(store, 1, 1L << 20, () -> {}, output -> {
            output.accept(member("a.xml")); output.accept(member("b.xml"));
        })).hasMessage("CDS_EXPORT_PACKAGE_INVENTORY_LIMIT");
        assertThatThrownBy(() -> service.seal(store, 100, 12, () -> {}, output -> output.accept(member("a.xml"))))
                .hasMessage("CDS_EXPORT_PACKAGE_INVENTORY_LIMIT");
        assertThat(files()).isEqualTo(1);
    }
    @Test void shouldRemainFailed_whenTheProducerSwallowsARejectedMember() throws Exception {
        assertThatThrownBy(() -> service.seal(store, 100, 1L << 20, () -> {}, output -> {
            try { output.accept(member("../outside.xml")); } catch (IOException expected) { }
            output.accept(member("a.xml"));
        })).hasMessage("CDS_EXPORT_PACKAGE_INVENTORY_INVALID");
        assertThat(files()).isEqualTo(1);
    }
    @Test void shouldCleanEarlierSpills_afterALaterProducerFailure() throws Exception {
        assertThatThrownBy(() -> service.seal(store, 10000, 8L << 20, () -> {}, output -> {
            for (int i = 0; i < 5000; i++) output.accept(member(String.format(Locale.ROOT, "P/%08d.xml", i)));
            throw new IOException("PRODUCER_FAILED");
        })).hasMessage("PRODUCER_FAILED");
        assertThat(files()).isEqualTo(1);
    }
    @Test void shouldDetectCollisions_betweenDifferentEncryptedSortRuns() throws Exception {
        assertThatThrownBy(() -> service.seal(store, 10000, 8L << 20, () -> {}, output -> {
            output.accept(member("patient/duplicate.xml"));
            for (int i = 0; i < 5000; i++) output.accept(member(String.format(Locale.ROOT, "P/%08d.xml", i)));
            output.accept(member("PATIENT/DUPLICATE.XML"));
        })).hasMessage("CDS_EXPORT_PACKAGE_PATH_COLLISION_OR_ORDER");
        assertThat(files()).isEqualTo(1);
    }
    @Test void shouldMergeBeyondTheOpenDescriptorFanIn_andRemoveEveryIntermediate() throws Exception {
        int total = 4096 * 33 + 1;
        var sealed = service.seal(store, total, 64L << 20, () -> {}, output -> {
            for (int i = total - 1; i >= 0; i--) output.accept(member(String.format(Locale.ROOT, "P/%08d.xml", i)));
        });
        assertThat(sealed.entries()).isEqualTo(total); int count = 0;
        try (var input = sealed.open()) {
            CdsExportPackageMemberDto next;
            while ((next = input.next()) != null) assertThat(next.relativePath()).isEqualTo(String.format(Locale.ROOT, "P/%08d.xml", count++));
        }
        assertThat(count).isEqualTo(total); assertThat(files()).isEqualTo(2);
    }
    @Test void shouldDriveTheExistingFinishedZipValidator_fromTheSameSealedMemberSequence() throws Exception {
        var sealed = service.seal(store, 100, 1L << 20, () -> {}, output -> {
            output.accept(member("Z/patient.xml")); output.accept(member("A/patient.xml"));
        });
        var result = new CdsExportPackageComposer().compose(store, sealed, 1L << 20, 100, () -> {});
        assertThat(result.entries()).isEqualTo(2); assertThat(result.expandedBytes()).isEqualTo(6); assertThat(files()).isEqualTo(3);
    }
    @Test void shouldRefuseAChangedAuthenticatedInventory_insteadOfReturningAPartialList() throws Exception {
        var sealed = service.seal(store, 100, 1L << 20, () -> {}, output -> output.accept(member("a.xml")));
        Path file = root.resolve(sealed.member().memberId() + ".stage"); byte[] bytes = Files.readAllBytes(file); bytes[bytes.length - 1] ^= 1; Files.write(file, bytes);
        assertThatThrownBy(() -> paths(sealed)).isInstanceOf(IOException.class);
    }
    @Test void shouldExpireTheProducerCapability_afterSealing() throws Exception {
        var escaped = new AtomicReference<CdsExportPackageInventoryService.Members>();
        var sealed = service.seal(store, 100, 1L << 20, () -> {}, output -> { escaped.set(output); output.accept(member("a.xml")); });
        assertThatThrownBy(() -> escaped.get().accept(member("b.xml"))).hasMessage("CDS_EXPORT_PACKAGE_INVENTORY_INVALID");
        assertThat(paths(sealed)).containsExactly("a.xml"); assertThat(files()).isEqualTo(2);
    }
    @Test void shouldRefuseAnEmptyInventory() {
        assertThatThrownBy(() -> service.seal(store, 100, 1L << 20, () -> {}, output -> {})).hasMessage("CDS_EXPORT_PACKAGE_INVENTORY_INVALID");
    }
    @Test void shouldPoisonTheInventory_afterARejectedForeignProducerCallback() throws Exception {
        assertThatThrownBy(() -> service.seal(store, 100, 1L << 20, () -> {}, output -> {
            var error = new AtomicReference<Throwable>();
            Thread foreign = new Thread(() -> { try { output.accept(member("foreign.xml")); } catch (Throwable failure) { error.set(failure); } });
            foreign.start();
            try { foreign.join(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            assertThat(error.get()).isInstanceOf(IOException.class); output.accept(member("a.xml"));
        })).hasMessage("CDS_EXPORT_PACKAGE_INVENTORY_INVALID");
        assertThat(files()).isEqualTo(1);
    }
}
