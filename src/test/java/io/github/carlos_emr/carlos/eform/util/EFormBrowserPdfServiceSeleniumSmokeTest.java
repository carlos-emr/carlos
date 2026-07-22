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

package io.github.carlos_emr.carlos.eform.util;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.HasCdp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end fidelity smoke: serves the repo's realistic eForm test-pattern fixture over a
 * loopback HTTP server (same origin semantics as production) and drives the full Selenium
 * capture path — stabilization, region computation, CDP clip screenshots, PDFBox assembly.
 *
 * <p>Skips cleanly when no Chromium binary or matching chromedriver is available, so CI hosts
 * without a browser stay green while browser-equipped environments verify the real pipeline.</p>
 */
@DisplayName("EFormBrowserPdfService Selenium smoke test")
@Tag("integration")
@Tag("eform")
class EFormBrowserPdfServiceSeleniumSmokeTest {

    @Test
    @DisplayName("should render the eForm test-pattern fixture to a PDF with headless Chromium")
    void shouldRenderTestPatternFixture_toPdfWithHeadlessChromium() throws Exception {
        Path fixture = Paths.get("scripts", "fixtures", "eform", "test-pattern.html");
        assumeTrue(Files.isReadable(fixture), "eForm test-pattern fixture not present in checkout");
        String chromiumBinary = findChromiumBinary();
        assumeTrue(chromiumBinary != null, "no headless Chromium binary available on this host");

        byte[] fixtureHtml = Files.readAllBytes(fixture);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, fixtureHtml.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(fixtureHtml);
            }
        });
        server.start();

        ChromeDriver driver = null;
        Path tempDir = Files.createTempDirectory("eform-selenium-smoke-");
        try {
            String allowedOrigin = "http://127.0.0.1:" + server.getAddress().getPort();
            ChromeOptions options = EFormBrowserPdfService.buildChromeOptions(chromiumBinary, true, allowedOrigin);
            driver = startDriverOrSkip(options);
            driver.manage().timeouts()
                    .pageLoadTimeout(Duration.ofSeconds(30))
                    .scriptTimeout(Duration.ofSeconds(30));

            driver.get("http://127.0.0.1:" + server.getAddress().getPort() + "/test-pattern.html");
            Thread.sleep(1500);
            Object settleError = driver.executeAsyncScript(EFormBrowserPdfService.STABILIZE_ASYNC_JS);
            assertThat(settleError).as("stabilization script error").isNull();
            driver.executeScript(EFormBrowserPdfService.PREPARE_CAPTURE_JS);

            List<EFormBrowserPdfService.CaptureRegion> regions =
                    EFormBrowserPdfService.readRegions(driver.executeScript(EFormBrowserPdfService.COMPUTE_REGIONS_JS));
            assertThat(regions).as("computed capture regions").isNotEmpty();

            HasCdp cdp = driver;
            List<Path> captures = new ArrayList<>();
            for (int index = 0; index < regions.size(); index++) {
                EFormBrowserPdfService.CaptureRegion region = regions.get(index);
                Map<String, Object> result = cdp.executeCdpCommand("Page.captureScreenshot", Map.of(
                        "format", "png",
                        "clip", Map.of(
                                "x", Math.max(0, Math.floor(region.x())),
                                "y", Math.max(0, Math.floor(region.y())),
                                "width", Math.ceil(region.width()),
                                "height", Math.ceil(region.height()),
                                "scale", 1.0d),
                        "captureBeyondViewport", Boolean.TRUE));
                byte[] png = Base64.getDecoder().decode(String.valueOf(result.get("data")));
                assertThat(png).as("capture %s bytes", index).isNotEmpty();
                Path capturePath = tempDir.resolve(String.format("page-%03d.png", index + 1));
                Files.write(capturePath, png);
                captures.add(capturePath);
            }

            Path pdfPath = tempDir.resolve("test-pattern.pdf");
            EFormBrowserPdfService.convertCapturesToPdf(captures, pdfPath);

            byte[] header = new byte[4];
            try (var in = Files.newInputStream(pdfPath)) {
                assertThat(in.read(header)).isEqualTo(4);
            }
            assertThat(new String(header, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
            assertThat(Files.size(pdfPath)).isGreaterThan(1000);
        } finally {
            // Nest so a throw from any one cleanup step still runs the rest (a failed driver.quit()
            // must not leak the loopback server or orphan the temp dir).
            try {
                if (driver != null) {
                    driver.quit();
                }
            } finally {
                try {
                    server.stop(0);
                } finally {
                    deleteRecursively(tempDir);
                }
            }
        }
    }

    @Test
    @DisplayName("should not load a local file subresource referenced by a malicious eForm")
    void shouldNotLoadLocalFileSubresource_referencedByMaliciousEform() throws Exception {
        String chromiumBinary = findChromiumBinary();
        assumeTrue(chromiumBinary != null, "no headless Chromium binary available on this host");
        Path secret = Files.createTempFile("eform-file-access-probe-", ".txt");
        Files.writeString(secret, "TOP-SECRET-LOCAL-FILE-CONTENT");

        // A page served over http that tries to pull a local file into an <img>. Chromium's
        // default cross-scheme policy must keep the image broken (naturalWidth 0), proving the
        // renderer's launch flags never opened file access.
        String html = "<!doctype html><html><body>"
                + "<img id='probe' src='file://" + secret.toAbsolutePath() + "'>"
                + "</body></html>";
        byte[] pageBytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, pageBytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(pageBytes);
            }
        });
        server.start();

        ChromeDriver driver = null;
        try {
            String allowedOrigin = "http://127.0.0.1:" + server.getAddress().getPort();
            driver = startDriverOrSkip(EFormBrowserPdfService.buildChromeOptions(chromiumBinary, true, allowedOrigin));
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30)).scriptTimeout(Duration.ofSeconds(30));
            driver.get(allowedOrigin + "/probe.html");
            Thread.sleep(1000);

            Object naturalWidth = driver.executeScript(
                    "var img = document.getElementById('probe'); return img ? img.naturalWidth : -1;");
            assertThat(((Number) naturalWidth).intValue())
                    .as("local file:// image must not load into the render surface")
                    .isZero();
        } finally {
            // Nest so a throw from any one cleanup step still runs the rest.
            try {
                if (driver != null) {
                    driver.quit();
                }
            } finally {
                try {
                    server.stop(0);
                } finally {
                    Files.deleteIfExists(secret);
                }
            }
        }
    }

    private static ChromeDriver startDriverOrSkip(ChromeOptions options) {
        try {
            // Same per-command client timeouts as production createDriver, so the smoke run
            // exercises the bounded-connection configuration against a real chromedriver.
            return new ChromeDriver(options, EFormBrowserPdfService.rendererClientConfig());
        } catch (WebDriverException e) {
            // Skip ONLY when the driver/browser genuinely cannot start (offline host, no matching
            // chromedriver, missing binary). Any other WebDriver failure — a bad options build, a
            // Selenium regression, a browser that starts then crashes — must fail the test, not be
            // silently converted into a green skip.
            String message = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
            boolean driverUnavailable = message.contains("cannot find")
                    || message.contains("unable to find")
                    || message.contains("no such file")
                    || message.contains("executable")
                    || message.contains("chrome failed to start")
                    || message.contains("binary");
            if (driverUnavailable) {
                assumeTrue(false, "chromedriver unavailable: " + e.getMessage());
                throw new IllegalStateException("unreachable");
            }
            throw e;
        }
    }

    private static String findChromiumBinary() throws IOException {
        String configured = System.getenv("CARLOS_SMOKE_CHROMIUM");
        if (configured != null && Files.isExecutable(Paths.get(configured))) {
            return configured;
        }
        Path playwrightWrapper = Paths.get("/opt/pw-browsers/chromium");
        if (Files.isRegularFile(playwrightWrapper) && Files.isExecutable(playwrightWrapper)) {
            return playwrightWrapper.toString();
        }
        Path browsersRoot = Paths.get("/opt/pw-browsers");
        if (Files.isDirectory(browsersRoot)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(browsersRoot, "chromium-*")) {
                for (Path candidate : stream) {
                    Path chrome = candidate.resolve("chrome-linux").resolve("chrome");
                    if (Files.isExecutable(chrome)) {
                        return chrome.toString();
                    }
                }
            }
        }
        return null;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup of the smoke workspace
                }
            });
        }
    }

}
