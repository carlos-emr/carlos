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
