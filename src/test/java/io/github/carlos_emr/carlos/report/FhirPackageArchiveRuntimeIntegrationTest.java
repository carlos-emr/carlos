package io.github.carlos_emr.carlos.report;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Protects the existing FHIR archive consumer when POI updates Commons Compress. */
@Tag("integration")
@Tag("report")
class FhirPackageArchiveRuntimeIntegrationTest {
    @Test
    void shouldPreserveFhirContentAndDefaultGzipTime_whenPackageIsSavedAndRead() throws Exception {
        String resource = """
                {"resourceType":"Observation","id":"synthetic-00123","status":"final",
                 "code":{"text":"Synthetic Élodie 李"},"valueString":"12345678901234567"}
                """;
        var fixture = new ByteArrayOutputStream();
        try (var gzip = new GzipCompressorOutputStream(fixture);
             var tar = new TarArchiveOutputStream(gzip)) {
            writeEntry(tar, "package/package.json", """
                    {"name":"example.fhir.regression","version":"1.0.0","type":"fhir.ig",
                     "fhirVersions":["3.0.2"]}
                    """);
            writeEntry(tar, "package/Observation-synthetic.json", resource);
        }
        var source = NpmPackage.fromPackage(new ByteArrayInputStream(fixture.toByteArray()));
        var output = new ByteArrayOutputStream();
        source.save(output);
        byte[] archive = output.toByteArray();
        // NpmPackage.save uses the default GzipParameters, whose MTIME must remain zero.
        assertThat(archive).hasSizeGreaterThan(10);
        assertThat(new byte[]{archive[4], archive[5], archive[6], archive[7]})
                .containsExactly((byte) 0, (byte) 0, (byte) 0, (byte) 0);
        var restored = NpmPackage.fromPackage(new ByteArrayInputStream(archive));
        try (var content = restored.load("package", "Observation-synthetic.json")) {
            assertThat(new String(content.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(resource);
        }
    }

    private static void writeEntry(TarArchiveOutputStream tar, String name, String text) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        var entry = new TarArchiveEntry(name);
        entry.setSize(bytes.length);
        tar.putArchiveEntry(entry);
        tar.write(bytes);
        tar.closeArchiveEntry();
    }
}
