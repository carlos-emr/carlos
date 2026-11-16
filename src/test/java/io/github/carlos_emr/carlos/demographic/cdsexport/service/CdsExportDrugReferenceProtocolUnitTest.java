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
import java.nio.file.*;
import java.util.Properties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;

@Tag("unit")
@Tag("cds-export")
@Isolated("The exact external installed fixture owns a temporary loopback public dictionary")
class CdsExportDrugReferenceProtocolUnitTest {
    @org.junit.jupiter.api.io.TempDir Path compiled;

    @Test
    void shouldUseTheActualNativeXmlRpcClient_withTheExternalInstalledPublicDictionary()
            throws Exception {
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).isNotNull();
        try (var diagnostics = new java.io.ByteArrayOutputStream()) {
            int result =
                    compiler.run(
                            null,
                            diagnostics,
                            diagnostics,
                            "--release",
                            "25",
                            "-encoding",
                            "UTF-8",
                            "-proc:none",
                            "-classpath",
                            System.getProperty(
                                    "surefire.test.class.path",
                                    System.getProperty("java.class.path")),
                            "-d",
                            compiled.toString(),
                            Path.of("scripts/ci/CdsExportInstalledFixture.java")
                                    .toAbsolutePath()
                                    .toString());
            assertThat(result)
                    .withFailMessage("The external installed fixture must compile")
                    .isZero();
        }
        try (var loader =
                new java.net.URLClassLoader(
                        new java.net.URL[] {compiled.toUri().toURL()},
                        getClass().getClassLoader())) {
            var fixture =
                    loader.loadClass("io.github.carlos_emr.carlos.login.CdsExportInstalledFixture");
            var method = fixture.getDeclaredMethod("publicDrugReference");
            method.setAccessible(true);
            var server = (com.sun.net.httpserver.HttpServer) method.invoke(null);
            try {
                var properties = new Properties();
                properties.setProperty("drugref_url", "http://127.0.0.1:9181/RPC2");
                var lookup = new CdsExportDrugReferenceLookupService(properties);
                var reference = lookup.read("00999999");
                assertThat(reference.din()).isEqualTo("00999999");
                assertThat(reference.product()).isEqualTo("Native reference tablet");
                assertThat(reference.components()).hasSize(2);
                assertThat(reference.components().getFirst().strength()).isEqualTo("5");
                assertThat(reference.components().getFirst().unit()).isEqualTo("mg");
                assertThatThrownBy(() -> lookup.read("00000001"))
                        .hasMessage("CDS_EXPORT_DRUG_REFERENCE_UNAVAILABLE");
            } finally {
                server.stop(0);
            }
        }
    }
}
