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
package io.github.carlos_emr.carlos.test.browser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the smoke suites' driver contract without a real browser: a running service URL wins, a
 * configured binary is started on loopback and stopped again, and a configured driver that cannot
 * start fails rather than skipping. The "driver" here is a synthetic Java program serving only
 * {@code /status}.
 */
@DisplayName("Smoke-suite chromedriver service")
@Tag("unit")
@Tag("eform")
class SmokeDriverServiceUnitTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("should return no endpoint when neither driver variable is set")
    void shouldReturnNoEndpoint_whenNoDriverIsConfigured() {
        try (SmokeDriverService service = new SmokeDriverService(Map.of())) {
            assertThat(service.endpointOrNull()).isNull();
        }
    }

    @Test
    @DisplayName("should use the running service URL ahead of a configured binary")
    void shouldUseRunningServiceUrl_whenBothVariablesAreSet() {
        Map<String, String> environment = Map.of(
                SmokeDriverService.DRIVER_URL_ENV, "http://127.0.0.1:9515",
                SmokeDriverService.DRIVER_BINARY_ENV, directory.resolve("missing-chromedriver").toString());
        try (SmokeDriverService service = new SmokeDriverService(environment)) {
            assertThat(service.endpointOrNull()).isEqualTo("http://127.0.0.1:9515");
        }
    }

    @Test
    @DisplayName("should fail rather than skip when the configured binary is not executable")
    void shouldFail_whenConfiguredBinaryIsNotExecutable() throws IOException {
        Path notExecutable = Files.writeString(directory.resolve("chromedriver"), "not a driver");
        try (SmokeDriverService service = new SmokeDriverService(
                Map.of(SmokeDriverService.DRIVER_BINARY_ENV, notExecutable.toString()))) {
            assertThatThrownBy(service::endpointOrNull)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(SmokeDriverService.DRIVER_BINARY_ENV);
        }
    }

    @Test
    @DisplayName("should report the exit code and driver output when the binary exits before ready")
    void shouldReportExitCodeAndOutput_whenDriverExitsBeforeReady() throws IOException {
        Path driver = script("chromedriver", "#!/bin/sh\necho \"synthetic bind failure for $1\"\nexit 3\n");
        try (SmokeDriverService service = new SmokeDriverService(
                Map.of(SmokeDriverService.DRIVER_BINARY_ENV, driver.toString()))) {
            assertThatThrownBy(service::endpointOrNull)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("exited with code 3")
                    .hasMessageContaining("synthetic bind failure for --port=");
        }
    }

    @Test
    @DisplayName("should start the binary on loopback, wait for ready, and stop it on close")
    void shouldStartAndStopDriver_whenBinaryIsConfigured() throws Exception {
        Path source = Files.writeString(directory.resolve("SyntheticDriver.java"), """
                import com.sun.net.httpserver.HttpServer;
                import java.net.InetSocketAddress;
                public class SyntheticDriver {
                    public static void main(String[] args) throws Exception {
                        int port = Integer.parseInt(args[0].substring("--port=".length()));
                        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
                        server.createContext("/status", exchange -> {
                            byte[] body = "{\\"value\\":{\\"ready\\":true}}".getBytes();
                            exchange.sendResponseHeaders(200, body.length);
                            exchange.getResponseBody().write(body);
                            exchange.close();
                        });
                        server.start();
                    }
                }
                """);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path driver = script("chromedriver",
                "#!/bin/sh\nexec '" + java + "' '" + source + "' \"$@\"\n");
        try (SmokeDriverService service = new SmokeDriverService(
                Map.of(SmokeDriverService.DRIVER_BINARY_ENV, driver.toString()));
             HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY)
                     .connectTimeout(Duration.ofSeconds(2)).build()) {
            String endpoint = service.endpointOrNull();

            assertThat(endpoint).matches("http://127\\.0\\.0\\.1:\\d+");
            assertThat(service.endpointOrNull()).as("started once, then reused").isEqualTo(endpoint);
            HttpRequest status = HttpRequest.newBuilder(URI.create(endpoint + "/status"))
                    .timeout(Duration.ofSeconds(2)).build();
            assertThat(client.send(status, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);

            service.close();

            assertThatThrownBy(() -> client.send(status, HttpResponse.BodyHandlers.ofString()))
                    .as("the started driver is gone after close")
                    .isInstanceOf(IOException.class);
        }
    }

    private Path script(String name, String content) throws IOException {
        Path path = Files.writeString(directory.resolve(name), content);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        return path;
    }
}
