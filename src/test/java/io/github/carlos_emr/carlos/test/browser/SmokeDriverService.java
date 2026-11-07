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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The chromedriver endpoint for the opt-in browser smoke suites (the release plan's
 * {@code java-browser-pdf} lane: {@code EFormBrowserPdfServiceSeleniumSmokeIntegrationTest} and
 * {@code MeasurementChartSmokeIntegrationTest}).
 *
 * <p>Production never starts chromedriver: it connects to the separately confined service. The
 * smoke suites accept either operator contract, checked in this order:</p>
 * <ol>
 *   <li>{@value #DRIVER_URL_ENV}: the URL of a chromedriver that is already running, used as is;</li>
 *   <li>{@value #DRIVER_BINARY_ENV}: the path of a chromedriver binary. It is started on a free
 *       loopback port (chromedriver's default allow-list admits local clients only, so no
 *       {@code --allowed-ips} is passed), used once {@code /status} reports ready, and stopped by
 *       {@link #close()} from the suite's {@code @AfterAll}.</li>
 * </ol>
 * <p>With neither, {@link #endpointOrNull()} returns {@code null} and the suite skips; the release
 * plan routes those skips from {@code java-all} to the lane that sets the variables. A configured
 * binary that cannot start is a failure, never a skip: an explicitly configured driver that does not
 * work is a broken lane, and turning it into a green skip would hide that.</p>
 */
public final class SmokeDriverService implements AutoCloseable {

    /** URL of an already-running chromedriver. */
    public static final String DRIVER_URL_ENV = "CARLOS_SMOKE_DRIVER_URL";
    /** Path of a chromedriver binary the suite starts and stops itself. */
    public static final String DRIVER_BINARY_ENV = "CARLOS_SMOKE_CHROMEDRIVER";

    private static final Duration READY_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration STATUS_REQUEST_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(10);
    private static final int START_ATTEMPTS = 3;
    private static final int LOG_TAIL_CHARS = 2000;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, String> environment;
    private Process process;
    private Path log;
    private Thread shutdownHook;
    private String endpoint;

    /** Reads the two variables from the process environment. */
    public SmokeDriverService() {
        this(System.getenv());
    }

    SmokeDriverService(Map<String, String> environment) {
        this.environment = Map.copyOf(environment);
    }

    /**
     * The chromedriver URL, starting the configured binary on first use.
     *
     * @return the endpoint, or {@code null} when neither variable is set (the caller skips)
     * @throws IllegalStateException when {@value #DRIVER_BINARY_ENV} is set but the driver it names
     *         does not start and report ready
     */
    public synchronized String endpointOrNull() {
        if (endpoint != null) {
            return endpoint;
        }
        String running = blankToNull(environment.get(DRIVER_URL_ENV));
        if (running != null) {
            endpoint = running;
            return endpoint;
        }
        String binary = blankToNull(environment.get(DRIVER_BINARY_ENV));
        if (binary == null) {
            return null;
        }
        Path driver = Path.of(binary);
        if (!Files.isRegularFile(driver) || !Files.isExecutable(driver)) {
            throw new IllegalStateException(DRIVER_BINARY_ENV + " does not name an executable chromedriver");
        }
        // A free port can be taken between probing it and chromedriver binding it; a fresh port
        // on the next attempt covers that race without a fixed port that parallel runs collide on.
        // Only an early exit (a failed bind) is retried: a driver that hangs is not a port race.
        for (int attempt = 1; ; attempt++) {
            try {
                endpoint = start(driver, freeLoopbackPort());
                return endpoint;
            } catch (IllegalStateException failure) {
                boolean exited = process != null && !process.isAlive();
                stopProcess();
                if (!exited || attempt == START_ATTEMPTS) {
                    throw failure;
                }
            }
        }
    }

    /** Stops a chromedriver this instance started; a running service named by URL is left alone. */
    @Override
    public synchronized void close() {
        stopProcess();
        endpoint = null;
    }

    private String start(Path driver, int port) {
        try {
            log = Files.createTempFile("carlos-smoke-chromedriver-", ".log");
            process = new ProcessBuilder(driver.toString(), "--port=" + port)
                    .redirectErrorStream(true)
                    .redirectOutput(log.toFile())
                    .start();
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to start " + DRIVER_BINARY_ENV, failure);
        }
        Process started = process;
        shutdownHook = new Thread(() -> destroyTree(started, true), "carlos-smoke-chromedriver-stop");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        String url = "http://127.0.0.1:" + port;
        awaitReady(url);
        return url;
    }

    private void awaitReady(String url) {
        long deadline = System.nanoTime() + READY_TIMEOUT.toNanos();
        HttpRequest status = HttpRequest.newBuilder(URI.create(url + "/status"))
                .timeout(STATUS_REQUEST_TIMEOUT).GET().build();
        try (HttpClient client = HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(STATUS_REQUEST_TIMEOUT)
                .build()) {
            while (System.nanoTime() < deadline) {
                if (!process.isAlive()) {
                    throw new IllegalStateException("chromedriver exited with code " + process.exitValue()
                            + " before it was ready: " + logTail());
                }
                try {
                    HttpResponse<String> response = client.send(status, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200
                            && JSON.readTree(response.body()).path("value").path("ready").asBoolean(false)) {
                        return;
                    }
                } catch (IOException notListeningYet) {
                    // chromedriver has not bound its port yet; poll again.
                }
                Thread.sleep(100);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for chromedriver", interrupted);
        }
        throw new IllegalStateException("chromedriver did not report ready within "
                + READY_TIMEOUT.toSeconds() + "s: " + logTail());
    }

    private void stopProcess() {
        if (process != null) {
            destroyTree(process, false);
            process = null;
        }
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException shuttingDown) {
                // The JVM is already exiting and the hook is running or about to.
            }
            shutdownHook = null;
        }
        if (log != null) {
            try {
                Files.deleteIfExists(log);
            } catch (IOException ignored) {
                // best-effort cleanup of a temp log
            }
            log = null;
        }
    }

    /**
     * Stops chromedriver and any browser it still owns. Descendants first: a session a failed test
     * never quit would otherwise outlive the driver as an orphaned Chromium.
     */
    private static void destroyTree(Process target, boolean forcibly) {
        target.toHandle().descendants().forEach(forcibly ? ProcessHandle::destroyForcibly : ProcessHandle::destroy);
        if (forcibly) {
            target.destroyForcibly();
            return;
        }
        target.destroy();
        try {
            if (!target.waitFor(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                target.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
                target.destroyForcibly();
                target.waitFor(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException interrupted) {
            target.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private String logTail() {
        try {
            String text = Files.readString(log, StandardCharsets.UTF_8);
            return text.length() > LOG_TAIL_CHARS ? text.substring(text.length() - LOG_TAIL_CHARS) : text;
        } catch (IOException | RuntimeException unreadable) {
            return "(chromedriver log unavailable)";
        }
    }

    private static int freeLoopbackPort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException failure) {
            throw new IllegalStateException("No free loopback port for chromedriver", failure);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
