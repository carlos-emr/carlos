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
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportImplementationIdentityServiceUnitTest {
    @TempDir Path root;
    private static final String BASE = "io/github/carlos_emr/carlos/";
    private static final String ANCHOR = BASE + "demographic/cdsexport/CdsExport.class";
    private static final List<String> RESOURCES =
            List.of(
                    "cds-import/schema/EMR_Data_Migration_Schema.xsd",
                    "cds-import/schema/EMR_Data_Migration_Schema_DT.xsd",
                    "spoken_languages_codes.properties",
                    "oscar/prevention/PreventionItems.xml",
                    BASE + "lab/ca/all/parsers/message_config.xml",
                    "carlos-build.properties");

    private void fixture() throws Exception {
        write(ANCHOR, "APPLICATION");
        write(BASE + "commn/dao/AdditionalSource.class", "SOURCE");
        write("cds-export/source-schema/demographic.json", "{}");
        write("db/migration/common/V1.0.1__fixture.sql", "SELECT 1;");
        for (String name : RESOURCES)
            write(
                    name,
                    name.equals("carlos-build.properties")
                            ? "build.version=fixture\nbuild.date=fixture\n"
                            : "RESOURCE " + name);
    }

    private void write(String name, String text) throws Exception {
        Path path = root.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }

    private URLClassLoader loader() throws Exception {
        return new URLClassLoader(new URL[] {root.toUri().toURL()}, null);
    }

    @Test
    void shouldHashTheActualApplicationAndContracts_deterministicallyWithinOneArtifact()
            throws Exception {
        fixture();
        try (var loader = loader()) {
            var service = new CdsExportImplementationIdentityService(loader, () -> "a".repeat(64));
            String first = service.current();
            assertThat(first).matches("[0-9a-f]{64}");
            assertThat(service.current()).isEqualTo(first);
            assertThat(
                            new CdsExportImplementationIdentityService(loader, () -> "a".repeat(64))
                                    .current())
                    .isEqualTo(first);
        }
    }

    @Test
    void shouldChangeIdentity_whenAnOwningSourceClassChangesInAReplacementArtifact()
            throws Exception {
        fixture();
        String first;
        try (var loader = loader()) {
            first =
                    new CdsExportImplementationIdentityService(loader, () -> "a".repeat(64))
                            .current();
        }
        write(BASE + "commn/dao/AdditionalSource.class", "CHANGED SOURCE");
        try (var loader = loader()) {
            assertThat(
                            new CdsExportImplementationIdentityService(loader, () -> "a".repeat(64))
                                    .current())
                    .isNotEqualTo(first);
        }
    }

    @Test
    void shouldKeepIdentity_whenOnlyTheBuildTimestampJobOrNumberChanges() throws Exception {
        fixture();
        write("carlos-build.properties", "build.version=2026.08.0\nbuild.date=2026-11-01T10:00:00Z\nbuild.job=\nbuild.number=\n");
        String first;
        try (var loader = loader()) {
            first = new CdsExportImplementationIdentityService(loader, () -> "a".repeat(64)).current();
        }
        write("carlos-build.properties", "build.version=2026.08.0\nbuild.date=2026-11-13T02:00:00Z\nbuild.job=nightly\nbuild.number=42\n");
        try (var loader = loader()) {
            assertThat(new CdsExportImplementationIdentityService(loader, () -> "a".repeat(64)).current())
                    .isEqualTo(first);
        }
        write("carlos-build.properties", "build.version=2026.08.1\nbuild.date=2026-11-13T02:00:00Z\n");
        try (var loader = loader()) {
            assertThat(new CdsExportImplementationIdentityService(loader, () -> "a".repeat(64)).current())
                    .isNotEqualTo(first);
        }
    }

    @Test
    void shouldRefuseAShadowedApplicationClass_fromAnotherArtifact() throws Exception {
        fixture();
        Path other = root.resolveSibling(root.getFileName() + "-other");
        Files.createDirectories(other.resolve(BASE + "commn/dao"));
        Files.writeString(other.resolve(BASE + "commn/dao/AdditionalSource.class"), "SHADOW");
        try (var loader =
                new URLClassLoader(new URL[] {other.toUri().toURL(), root.toUri().toURL()}, null)) {
            assertThatThrownBy(
                            () ->
                                    new CdsExportImplementationIdentityService(
                                                    loader, () -> "a".repeat(64))
                                            .current())
                    .hasMessage("CDS_EXPORT_IMPLEMENTATION_IDENTITY_UNAVAILABLE");
        }
        Files.delete(other.resolve(BASE + "commn/dao/AdditionalSource.class"));
    }

    @Test
    void shouldRetryAfterAMissingRequiredResource_withoutPoisoningItsCache() throws Exception {
        fixture();
        var resource = root.resolve(RESOURCES.getFirst());
        Files.delete(resource);
        try (var loader = loader()) {
            var service = new CdsExportImplementationIdentityService(loader, () -> "a".repeat(64));
            assertThatThrownBy(service::current)
                    .hasMessage("CDS_EXPORT_IMPLEMENTATION_IDENTITY_UNAVAILABLE");
            write(RESOURCES.getFirst(), "RECOVERED");
            assertThat(service.current()).matches("[0-9a-f]{64}");
        }
    }

    @Test
    void shouldRejectSemanticRuntimeDrift_afterSuccessfulCalculation() throws Exception {
        fixture();
        var runtime = new AtomicReference<>("a".repeat(64));
        try (var loader = loader()) {
            var service = new CdsExportImplementationIdentityService(loader, runtime::get);
            service.current();
            runtime.set("b".repeat(64));
            assertThatThrownBy(service::current)
                    .hasMessage("CDS_EXPORT_IMPLEMENTATION_IDENTITY_UNAVAILABLE");
        }
    }

    /**
     * A jar holding the fixture, {@code generated} more classes and one class whose name is
     * longer than 4,096 characters, with the directory entries a class loader lists.
     */
    private Path jar(String name, int generated, String longClass) throws Exception {
        Path jar = root.resolve(name);
        var entries = new java.util.TreeMap<String, byte[]>();
        entries.put(ANCHOR, "APPLICATION".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put(BASE + "commn/dao/AdditionalSource.class", "SOURCE".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("cds-export/source-schema/demographic.json", "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put("db/migration/common/V1.0.1__fixture.sql", "SELECT 1;".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (String resource : RESOURCES)
            entries.put(
                    resource,
                    (resource.equals("carlos-build.properties")
                                    ? "build.version=fixture\n"
                                    : "RESOURCE " + resource)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (int index = 0; index < generated; index++)
            entries.put(
                    BASE + "generated/G" + index + ".class",
                    ("G" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        entries.put(
                BASE + "generated/" + "N".repeat(4_100) + ".class",
                longClass.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var directories = new java.util.TreeSet<String>();
        for (String entry : entries.keySet())
            for (int slash = entry.indexOf('/'); slash > 0; slash = entry.indexOf('/', slash + 1))
                directories.add(entry.substring(0, slash + 1));
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            for (String directory : directories) {
                output.putNextEntry(new java.util.zip.ZipEntry(directory));
                output.closeEntry();
            }
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return jar;
    }

    private String identity(Path jar) throws Exception {
        try (var loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
            return new CdsExportImplementationIdentityService(loader, () -> "a".repeat(64)).current();
        }
    }

    @Test
    void shouldHashEveryClass_whenAnArtifactHoldsMoreThanTheFormerCounts() throws Exception {
        // At most 32,768 resources a pattern, 65,536 hashed entries and names of 4,096
        // characters were once hashed; CARLOS growing past them would fail every export.
        String first = identity(jar("first.jar", 65_537, "LONG"));
        assertThat(first).matches("[0-9a-f]{64}");
        // The class past every former bound is hashed: changing only it changes the identity.
        assertThat(identity(jar("second.jar", 65_537, "CHANGED"))).isNotEqualTo(first);
    }

    @Test
    void shouldHashAResource_whenTheBytesHashedPassTheFormer512MiB() throws Exception {
        // 512 MiB hashed in all was once the ceiling. The resource is sparse: its zeros are read,
        // never stored.
        fixture();
        Path large = root.resolve(BASE + "commn/dao/Large.class");
        try (var file = new java.io.RandomAccessFile(large.toFile(), "rw")) {
            file.setLength((512L << 20) + 1);
        }
        String first;
        try (var loader = loader()) {
            first = new CdsExportImplementationIdentityService(loader, () -> "a".repeat(64)).current();
        }
        assertThat(first).matches("[0-9a-f]{64}");
        // Its last byte is hashed too.
        try (var file = new java.io.RandomAccessFile(large.toFile(), "rw")) {
            file.seek(512L << 20);
            file.write(1);
        }
        try (var loader = loader()) {
            assertThat(new CdsExportImplementationIdentityService(loader, () -> "a".repeat(64)).current())
                    .isNotEqualTo(first);
        }
    }

    @Test
    void shouldExcludeOperatorPolicyAndPrivateKeys_fromTheImplementationDigest() throws Exception {
        fixture();
        String first;
        try (var loader = loader()) {
            first =
                    new CdsExportImplementationIdentityService(loader, () -> "a".repeat(64))
                            .current();
        }
        write("policy/private.key", "OWNED SYNTHETIC KEY");
        try (var loader = loader()) {
            assertThat(
                            new CdsExportImplementationIdentityService(loader, () -> "a".repeat(64))
                                    .current())
                    .isEqualTo(first);
        }
    }
}
