/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Published under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.fax.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@Tag("fax")
class FaxDocumentStreamingUnitTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldStreamRepeatablePayload_withFormEscapingAndPadding(boolean form) throws Exception {
        // Exercise every base64 alphabet character, all padding lengths and buffer boundaries.
        for (int length : new int[] {1, 2, 3, 8191, 8192, 8193, 32769}) {
            byte[] payload = new byte[length];
            new java.util.Random(3512).nextBytes(payload);
            Path file = Files.write(directory.resolve("document.pdf"), payload);
            var entity = new FaxDocumentEntity("prefix=", "&end", ContentType.APPLICATION_OCTET_STREAM,
                    file, "ignored backup", form);
            String base64 = Base64.getEncoder().encodeToString(payload);
            String expected = "prefix=" + (form ? java.net.URLEncoder.encode(base64, StandardCharsets.UTF_8) : base64)
                    + "&end";
            for (int attempt = 0; attempt < 2; attempt++) {
                var output = new ByteArrayOutputStream();
                entity.writeTo(output);
                output.write('!'); // The HTTP output must remain open for trailers.
                assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo(expected + "!");
            }
        }
    }

    @Test
    void shouldDecodeLegacyBackup_withoutRequiringFile() throws Exception {
        var output = new ByteArrayOutputStream();
        new FaxDocumentEntity("", "", ContentType.APPLICATION_XML, null,
                "SGVs\r\nbG8=", false).writeTo(output);
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("SGVsbG8=");
    }

    @Test
    void shouldDecodeEscapedMimePayload_withResultBeforeStatus() throws Exception {
        var entity = json("{\"Result\":\"SGVs\\r\\nbG8=\",\"Status\":\"Success\"}");
        Path spool;
        try (FaxDownload download = FaxDownload.read(entity, new ObjectMapper(), "Result")) {
            spool = download.release();
            assertThat(download.metadata().path("Status").asText()).isEqualTo("Success");
            assertThat(download.metadata().has("Result")).isFalse();
        }
        try {
            assertThat(Files.readString(spool)).isEqualTo("Hello");
        } finally {
            Files.delete(spool);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"Result\":\"%%%\"}", "{\"Result\":null}", "{\"Result\":\"\"}",
            "{\"Result\":\"SGk=\",\"Result\":\"SGk=\"}", "{}", "[]",
            "{\"Result\":\"SGk=\"", "{\"Result\":\"SGk=\"}{}",
            "{\"Result\":\"SGk=\",\"Status\":\"Success\",\"Status\":\"Failed\"}"
    })
    void shouldRejectMalformedDocument_withoutLeavingSpools(String json) throws Exception {
        assertThatThrownBy(() -> FaxDownload.read(json(json), new ObjectMapper(), "Result", directory))
                .isInstanceOf(java.io.IOException.class);
        try (var files = Files.list(directory)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @Test
    void shouldDeleteUnacceptedDownload_whenProviderValidationFails() throws Exception {
        // The caller has not released ownership: closing must remove the successful decode too.
        Path path;
        try (FaxDownload download = FaxDownload.read(json("{\"document\":\"SGk=\"}"),
                new ObjectMapper(), "document", directory)) {
            try (var files = Files.list(directory)) {
                path = files.findFirst().orElseThrow();
            }
            assertThat(path).exists();
            assertThat(download.metadata()).isEmpty();
            if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
                assertThat(Files.getPosixFilePermissions(path))
                        .isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            }
        }
        assertThat(path).doesNotExist();
    }

    @Test
    void shouldKeepSpoolPathPrivate_whenSerializingOrCopyingMetadata() throws Exception {
        FaxJob job = new FaxJob();
        job.setDocumentPath(directory.resolve("private.pdf"));
        assertThat(new ObjectMapper().writeValueAsString(job)).doesNotContain("documentPath", "private.pdf");
        var xml = new java.io.StringWriter();
        jakarta.xml.bind.JAXBContext.newInstance(FaxJob.class).createMarshaller().marshal(job, xml);
        assertThat(xml.toString()).doesNotContain("documentPath", "private.pdf");
        assertThat(new FaxJob(job).getDocumentPath()).isNull();
    }

    @Test
    void shouldRejectOversizedMetadata_beforeMaterializingTree() throws Exception {
        String response = "{\"metadata\":\"" + "x".repeat(1024 * 1024 + 8192) + "\",\"Result\":\"SGk=\"}";
        assertThatThrownBy(() -> FaxDownload.read(json(response), new ObjectMapper(), "Result", directory))
                .isInstanceOf(java.io.IOException.class).hasMessageContaining("metadata exceeds");
        try (var files = Files.list(directory)) {
            assertThat(files.toList()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"declared", "chunked", "understated"})
    void shouldRejectOverCapDocument_withoutLeavingPartialSpool(String lengthMode) throws Exception {
        var properties = io.github.carlos_emr.CarlosProperties.getInstance();
        String previous = properties.getProperty(BoundedResponseReader.MAX_RESPONSE_MB_PROPERTY);
        byte[] body = ("{\"Result\":\"" + "A".repeat(2 * 1024 * 1024) + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        long length = switch (lengthMode) {
            case "declared" -> body.length;
            case "chunked" -> -1;
            default -> 16;
        };
        properties.setProperty(BoundedResponseReader.MAX_RESPONSE_MB_PROPERTY, "1");
        try (var response = new org.apache.hc.core5.http.io.entity.BasicHttpEntity(
                new java.io.ByteArrayInputStream(body), length, ContentType.APPLICATION_JSON)) {
            assertThatThrownBy(() -> FaxDownload.read(response, new ObjectMapper(), "Result", directory))
                    .isInstanceOf(java.io.IOException.class).hasMessageContaining("fax.max_response_mb");
            try (var files = Files.list(directory)) {
                assertThat(files.toList()).isEmpty();
            }
        } finally {
            if (previous == null) {
                properties.remove(BoundedResponseReader.MAX_RESPONSE_MB_PROPERTY);
            } else {
                properties.setProperty(BoundedResponseReader.MAX_RESPONSE_MB_PROPERTY, previous);
            }
        }
    }

    @Test
    void shouldHonorXmlEncoding_withoutMaterializingEchoedDocument() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><FaxJob>"
                + "<document>discarded payload</document><jobId>3512</jobId>"
                + "<status>SENT</status><statusString>caf\u00e9</statusString></FaxJob>";
        var response = new org.apache.hc.core5.http.io.entity.ByteArrayEntity(
                xml.getBytes(StandardCharsets.ISO_8859_1), ContentType.APPLICATION_XML);
        FaxJob job = FaxXmlResponseParser.read(response);
        assertThat(job.getJobId()).isEqualTo(3512L);
        assertThat(job.getStatusString()).isEqualTo("caf\u00e9");
        assertThat(job.getDocument()).isNull();
    }

    @Test
    void shouldRejectXmlEntities_withoutResolvingExternalFiles() {
        String xml = "<!DOCTYPE FaxJob [<!ENTITY test SYSTEM 'file:///etc/passwd'>]>"
                + "<FaxJob><statusString>&test;</statusString></FaxJob>";
        assertThatThrownBy(() -> FaxXmlResponseParser.read(new StringEntity(xml, ContentType.APPLICATION_XML)))
                .isInstanceOfAny(java.io.IOException.class, jakarta.xml.bind.JAXBException.class);
    }

    @Test
    void shouldSkipRepeatedXmlDocuments_withoutGrowingCallStack() throws Exception {
        String xml = "<FaxJob>" + "<document/>".repeat(20000) + "<jobId>3512</jobId></FaxJob>";
        FaxJob job = FaxXmlResponseParser.read(new StringEntity(xml, ContentType.APPLICATION_XML));
        assertThat(job.getJobId()).isEqualTo(3512L);
        assertThat(job.getDocument()).isNull();
    }

    private StringEntity json(String text) {
        return new StringEntity(text, ContentType.APPLICATION_JSON);
    }
}
