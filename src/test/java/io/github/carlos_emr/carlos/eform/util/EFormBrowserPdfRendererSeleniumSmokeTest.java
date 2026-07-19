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
import java.util.Map;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
@DisplayName("EFormBrowserPdfRenderer Selenium smoke test")
@Tag("integration")
@Tag("eform")
class EFormBrowserPdfRendererSeleniumSmokeTest {

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
            ChromeOptions options = EFormBrowserPdfRenderer.buildChromeOptions(chromiumBinary, true);
            driver = startDriverOrSkip(options);
            driver.manage().timeouts()
                    .pageLoadTimeout(Duration.ofSeconds(30))
                    .scriptTimeout(Duration.ofSeconds(30));

            driver.get("http://127.0.0.1:" + server.getAddress().getPort() + "/test-pattern.html");
            Thread.sleep(1500);
            Object settleError = driver.executeAsyncScript(EFormBrowserPdfRenderer.STABILIZE_ASYNC_JS);
            assertThat(settleError).as("stabilization script error").isNull();
            driver.executeScript(EFormBrowserPdfRenderer.PREPARE_CAPTURE_JS);

            List<EFormBrowserPdfRenderer.CaptureRegion> regions =
                    EFormBrowserPdfRenderer.readRegions(driver.executeScript(EFormBrowserPdfRenderer.COMPUTE_REGIONS_JS));
            assertThat(regions).as("computed capture regions").isNotEmpty();

            HasCdp cdp = driver;
            List<Path> captures = new ArrayList<>();
            for (int index = 0; index < regions.size(); index++) {
                EFormBrowserPdfRenderer.CaptureRegion region = regions.get(index);
                Map<String, Object> result = cdp.executeCdpCommand("Page.captureScreenshot", Map.of(
                        "format", "png",
                        "clip", Map.of(
                                "x", Math.max(0, Math.floor(region.x())),
                                "y", Math.max(0, Math.floor(region.y())),
                                "width", Math.ceil(region.width()),
                                "height", Math.ceil(region.height()),
                                "scale", 1.0d),
                        "captureBeyondViewport", Boolean.TRUE));
                byte[] png = Base64.getDecoder().decode((String) result.get("data"));
                assertThat(png).as("capture %s bytes", index).isNotEmpty();
                Path capturePath = tempDir.resolve(String.format("page-%03d.png", index + 1));
                Files.write(capturePath, png);
                captures.add(capturePath);
            }

            Path pdfPath = tempDir.resolve("test-pattern.pdf");
            EFormBrowserPdfRenderer.convertCapturesToPdf(captures, pdfPath);

            byte[] header = new byte[4];
            try (var in = Files.newInputStream(pdfPath)) {
                assertThat(in.read(header)).isEqualTo(4);
            }
            assertThat(new String(header, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
            assertThat(Files.size(pdfPath)).isGreaterThan(1000);
        } finally {
            if (driver != null) {
                driver.quit();
            }
            server.stop(0);
            deleteRecursively(tempDir);
        }
    }

    private static ChromeDriver startDriverOrSkip(ChromeOptions options) {
        try {
            return new ChromeDriver(options);
        } catch (RuntimeException e) {
            // No matching chromedriver (offline host, unsupported browser build): skip, not fail.
            assumeTrue(false, "chromedriver unavailable: " + e.getMessage());
            throw new IllegalStateException("unreachable");
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
