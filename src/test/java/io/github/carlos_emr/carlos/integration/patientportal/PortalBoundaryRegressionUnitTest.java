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

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Regression tests for malformed, oversized and retried portal responses. */
@Tag("unit")
@Tag("patient-portal")
class PortalBoundaryRegressionUnitTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private PatientPortalStaffContext staff() {
        return new PatientPortalStaffContext("999998", "Synthetic Provider",
                Set.of(PatientPortalStaffContext.PERMISSION_SECRET_MANAGE));
    }
    private PatientPortalService service(String body) {
        var settings = new PatientPortalSettings("https://portal.example", "clinic",
                PortalSecret.of("synthetic-service-token"),
                PortalSecret.of(PortalTestKeys.PRIVATE_KEY), Duration.ofSeconds(1),
                Duration.ofSeconds(1), Set.of());
        return new PatientPortalService(settings, request -> new PatientPortalHttpResponse(201, body));
    }

    @Test void shouldRedactMalformedJsonTokenFromExceptionChain() {
        Throwable failure = catchThrowable(() -> service("{\"secret\":SyntheticSecretToken123}")
                .createUnlockSecret(123, "message-1", null, staff()));
        assertThat(failure).isInstanceOf(PatientPortalException.class);
        StringWriter rendered = new StringWriter();
        failure.printStackTrace(new PrintWriter(rendered));
        assertThat(rendered.toString()).doesNotContain("SyntheticSecretToken123");
    }

    @Test void shouldRejectFractionalIdentifier() throws Exception {
        var value = mapper.readTree("{\"id\":1.9}");
        assertThatThrownBy(() -> PortalJson.requiredLong(value, "id"))
                .isInstanceOf(PortalContractException.class);
    }

    @Test void shouldRejectOverflowingIdentifier() throws Exception {
        var value = mapper.readTree("{\"id\":18446744073709551617}");
        assertThatThrownBy(() -> PortalJson.requiredInt(value, "id"))
                .isInstanceOf(PortalContractException.class);
    }

    @Test void shouldRejectMissingPassphrase() {
        assertThatThrownBy(() -> service("{\"id\":1,\"created\":true,\"status\":\"pending\"}")
                .createUnlockSecret(123, "message-1", null, staff()))
                .isInstanceOf(PatientPortalException.class);
    }

    @ParameterizedTest @ValueSource(ints = {429, 503})
    void shouldNotReplayMutationAfterTransientFailure(int failureStatus) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/mutate", exchange -> {
            exchange.getRequestBody().readAllBytes();
            int status = calls.incrementAndGet() == 1 ? failureStatus : 200;
            exchange.getResponseHeaders().set("Retry-After", "0");
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        try (var transport = new PatientPortalHttpClientExchange(
                Duration.ofMillis(300), Duration.ofSeconds(2))) {
            transport.send(ClassicRequestBuilder.post("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/mutate").build());
            assertThat(calls.get()).as("one staff action must not replay a mutation").isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test void shouldAbortOversizedResponseWithoutDrainingItsTail() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/large", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                out.write(new byte[PatientPortalHttpClientExchange.MAX_RESPONSE_CHARS + 16384]);
                out.flush();
                // Stay below the per-read timeout, but exceed the body cap for three seconds.
                for (int i = 0; i < 60; i++) {
                    out.write(new byte[1024]); out.flush(); Thread.sleep(50);
                }
            } catch (Exception expectedAfterAbort) { }
        });
        server.start();
        try (var transport = new PatientPortalHttpClientExchange(
                Duration.ofMillis(300), Duration.ofMillis(300))) {
            long start = System.nanoTime();
            assertThatThrownBy(() -> transport.send(ClassicRequestBuilder.get("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/large").build()))
                    .isInstanceOf(PortalResponseTooLargeException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start))
                    .as("size cap must terminate the network read")
                    .isLessThan(Duration.ofSeconds(2));
        } finally { server.stop(0); }
    }
    @ParameterizedTest @ValueSource(strings = {"null", "0", "true", "[]", "{}", "\"\"", "\"  \""})
    void rejectsInvalidCredentialValues(String secret) {
        assertThatThrownBy(() -> service("{\"id\":1,\"created\":true,\"status\":\"pending\",\"secret\":" + secret + "}")
                .createUnlockSecret(123, "message-1", null, staff()))
                .isInstanceOf(PatientPortalException.class);
    }

    @Test void rejectsTrailingContent() {
        assertThatThrownBy(() -> service("[] {} ").listInvites(123, 100, staff()))
                .isInstanceOf(PatientPortalException.class);
    }

    @Test void withholdsArbitraryTextInErrorDetails() {
        var settings = new PatientPortalSettings("https://portal.example", "clinic",
                PortalSecret.of("synthetic-service-token"),
                PortalSecret.of(PortalTestKeys.PRIVATE_KEY), Duration.ofSeconds(1),
                Duration.ofSeconds(1), Set.of());
        var client = new PatientPortalService(settings, request -> new PatientPortalHttpResponse(
                409, "{\"detail\":\"SyntheticSecretToken123\"}"));
        var failure = catchThrowableOfType(() -> client.listInvites(123, 100, staff()), PatientPortalException.class);
        assertThat(failure.detail()).isNull();
        assertThat(failure).hasMessageNotContaining("SyntheticSecretToken123");
    }

    @ParameterizedTest @ValueSource(strings = {"sha256/first", "sha256/%%%%", ",,", "sha256/AAAA"})
    void rejectsInvalidPinConfiguration(String pins) {
        var values = java.util.Map.of(PatientPortalSettings.BASE_URL_KEY, "https://portal.example",
                PatientPortalSettings.CLINIC_ID_KEY, "clinic",
                PatientPortalSettings.SERVICE_TOKEN_KEY, "synthetic-service-token",
                PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY,
                PatientPortalSettings.CERTIFICATE_PINS_KEY, pins);
        assertThatThrownBy(() -> PatientPortalSettings.fromProperties(values))
                .isInstanceOf(PatientPortalConfigurationException.class);
    }

    @Test void distinguishesPartialConfigurationFromAbsentPortal() {
        assertThat(PatientPortalSettings.isConfigured(key -> null)).isFalse();
        assertThat(PatientPortalSettings.isConfigured(key ->
                PatientPortalSettings.BASE_URL_KEY.equals(key) ? "https://portal.example" : null)).isTrue();
    }

    @Test void rejectsMissingUnlockState() {
        assertThatThrownBy(() -> service("{\"id\":1,\"force_password_reset\":true}")
                .unlockAccount(123, staff())).isInstanceOf(PatientPortalException.class);
    }

    @ParameterizedTest @ValueSource(strings = {
            "{\"id\":1,\"clinic_id\":\"other-clinic\",\"demographic_no\":123,\"status\":\"pending\",\"issued_count\":1}",
            "{\"id\":1,\"clinic_id\":\"clinic\",\"demographic_no\":456,\"status\":\"pending\",\"issued_count\":1}"})
    void rejectsInvitationWithWrongResponseScope(String invite) {
        assertThatThrownBy(() -> service("[" + invite + "]").listInvites(123, 100, staff()))
                .isInstanceOf(PatientPortalException.class);
    }
    @Test void redactsMalformedHttpStatusFromTransportExceptionChain() throws Exception {
        try (var server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
                var workers = java.util.concurrent.Executors.newSingleThreadExecutor();
                var transport = new PatientPortalHttpClientExchange(Duration.ofSeconds(1), Duration.ofSeconds(1))) {
            var response = workers.submit(() -> {
                try (var connection = server.accept()) {
                    connection.getOutputStream().write(
                            "HTTP/1.1 SyntheticSecretToken123\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                    connection.getOutputStream().flush();
                }
                return null;
            });
            Throwable raw = catchThrowable(() -> transport.send(ClassicRequestBuilder.get(
                    "http://localhost:" + server.getLocalPort() + "/malformed").build()));
            assertThat(raw).isInstanceOf(java.io.IOException.class);
            var failure = PatientPortalException.ofTransportFailure("/internal/carlos/patients/{id}/unlock", raw);
            StringWriter rendered = new StringWriter();
            failure.printStackTrace(new PrintWriter(rendered));
            assertThat(rendered.toString()).doesNotContain("SyntheticSecretToken123");
            response.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
}
