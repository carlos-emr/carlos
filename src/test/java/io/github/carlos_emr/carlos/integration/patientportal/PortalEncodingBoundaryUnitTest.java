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
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The real transport must preserve valid credentials and reject invalid encoding without replay. */
@Tag("unit")
@Tag("patient-portal")
class PortalEncodingBoundaryUnitTest {
    private static final String SYNTHETIC_SECRET = "synthetic-credential";

    @ParameterizedTest
    @ValueSource(strings = {"c328", "eda080", "f09f"})
    void shouldRejectMalformedUtf8_withoutChangingCredentialsOrReplaying(String invalidHex) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(("{\"id\":1,\"created\":true,\"status\":\"pending\",\"secret\":\""
                + SYNTHETIC_SECRET).getBytes(StandardCharsets.UTF_8));
        bytes.write(HexFormat.of().parseHex(invalidHex));
        bytes.write("\"}".getBytes(StandardCharsets.UTF_8));
        byte[] malformed = bytes.toByteArray();
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/secret", request -> {
            request.getRequestBody().readAllBytes();
            calls.incrementAndGet();
            request.sendResponseHeaders(201, malformed.length);
            try (var output = request.getResponseBody()) {
                output.write(malformed);
            }
        });
        server.start();
        try (var transport = new PatientPortalHttpClientExchange(Duration.ofSeconds(5), Duration.ofSeconds(5))) {
            var settings = new PatientPortalSettings("https://portal.example", "clinic",
                    PortalSecret.of("synthetic-service-token"),
                    PortalSecret.of(PortalTestKeys.PRIVATE_KEY), Duration.ofSeconds(5),
                    Duration.ofSeconds(5), Set.of());
            // Only this test redirects the request to a loopback socket; production settings stay HTTPS-only.
            var service = new PatientPortalService(settings, request -> transport.send(
                    ClassicRequestBuilder.copy(request).setUri("http://127.0.0.1:"
                            + server.getAddress().getPort() + "/secret").build()));
            var staff = new PatientPortalStaffContext("999998", "Synthetic Provider",
                    Set.of(PatientPortalStaffContext.PERMISSION_SECRET_MANAGE));

            PatientPortalException failure = catchThrowableOfType(
                    () -> service.createUnlockSecret(123, "message-1", null, staff), PatientPortalException.class);

            assertThat(failure).isNotNull();
            assertThat(failure.kind()).isEqualTo(PatientPortalException.Kind.MALFORMED_RESPONSE);
            assertThat(failure.statusCode()).isEqualTo(201);
            assertThat(failure.getCause()).hasMessage("portal response is not valid UTF-8");
            StringWriter rendered = new StringWriter();
            failure.printStackTrace(new PrintWriter(rendered));
            assertThat(rendered.toString()).doesNotContain(SYNTHETIC_SECRET);
            assertThat(calls.get()).as("a decoding error must not replay a mutation").isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldPreserveValidUtf8_includingLiteralReplacementCharacters() throws Exception {
        String expected = "{\"secret\":\"café-患者-😀-\uFFFD\"}";
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/valid", request -> {
            byte[] bytes = expected.getBytes(StandardCharsets.UTF_8);
            request.sendResponseHeaders(200, bytes.length);
            try (var output = request.getResponseBody()) {
                // Send byte by byte, covering multi-byte sequences across writes as well as ASCII.
                for (byte value : bytes) {
                    output.write(value);
                    output.flush();
                }
            }
        });
        server.start();
        try (var transport = new PatientPortalHttpClientExchange(Duration.ofSeconds(5), Duration.ofSeconds(5))) {
            assertThat(transport.send(ClassicRequestBuilder.get("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/valid").build()).body()).isEqualTo(expected);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldRedactRawBody_whenRenderingAResponse() {
        var response = new PatientPortalHttpResponse(200, "{\"secret\":\"" + SYNTHETIC_SECRET + "\"}");
        assertThat(response.toString()).contains("statusCode=200", "body=REDACTED")
                .doesNotContain(SYNTHETIC_SECRET);
        assertThat(response.body()).contains(SYNTHETIC_SECRET);
    }
}
