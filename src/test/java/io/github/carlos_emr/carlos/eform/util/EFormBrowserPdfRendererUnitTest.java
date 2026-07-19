package io.github.carlos_emr.carlos.eform.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.chrome.ChromeOptions;

import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EFormBrowserPdfRenderer unit tests")
@Tag("unit")
@Tag("fast")
class EFormBrowserPdfRendererUnitTest {

    @Test
    @DisplayName("should build the dedicated browser-render servlet path carrying the render token")
    void shouldBuildAppPath_whenRenderingSavedEformPdf() {
        String appPath = EFormBrowserPdfRenderer.buildAppPath(187, "tok-abc_123");

        assertThat(appPath)
                .startsWith("/EFormViewForPdfGenerationServlet?")
                .contains("fdid=187")
                .contains("browserRender=true")
                .contains("renderToken=tok-abc_123")
                .doesNotContain("providerId");
    }

    @Test
    @DisplayName("should keep print-only cleanup rules in the capture preparation script")
    void shouldKeepPrintCleanupRules_inCapturePreparationScript() {
        assertThat(EFormBrowserPdfRenderer.PREPARE_CAPTURE_JS)
                .contains(".DoNotPrint")
                .contains("#BottomButtons")
                .contains("#BaseSelect")
                .contains("#SupplementalInfo")
                .contains("#labDetail")
                .contains("resize: none !important");
    }

    @Test
    @DisplayName("should keep the region heuristics in the region computation script")
    void shouldKeepRegionHeuristics_inRegionComputationScript() {
        assertThat(EFormBrowserPdfRenderer.COMPUTE_REGIONS_JS)
                .contains("backgroundCandidates")
                .contains(".filter(isVisibleCaptureCandidate)")
                .contains("pageBackgroundCaptures")
                .contains("dedupeAndSortCaptureRects")
                .contains("/^page\\d+$/i");
    }

    @Test
    @DisplayName("should keep the font and image settle waits in the stabilization script")
    void shouldKeepSettleWaits_inStabilizationScript() {
        assertThat(EFormBrowserPdfRenderer.STABILIZE_ASYNC_JS)
                .contains("document.fonts.ready instanceof Promise")
                .contains("!image.complete")
                .contains("requestAnimationFrame");
    }

    @Test
    @DisplayName("should resolve the renderer temp root under catalina base so fax path validation accepts the output")
    void shouldResolveRendererTempRoot_underCatalinaBaseWhenConfigured() {
        Path root = EFormBrowserPdfRenderer.resolveRendererTempRoot(
                "/var/lib/tomcat10",
                "/tmp");

        assertThat(root)
                .isEqualTo(Paths.get("/var/lib/tomcat10", "work", "carlos", "eform-browser-pdf-temp"));
    }

    @Test
    @DisplayName("should resolve the renderer temp root under a namespaced system temp fallback")
    void shouldResolveRendererTempRoot_underNamespacedSystemTempFallback() {
        Path root = EFormBrowserPdfRenderer.resolveRendererTempRoot(
                null,
                "/tmp");

        assertThat(root)
                .isEqualTo(Paths.get("/tmp", "carlos-eform-browser-pdf-temp"));
    }

    @Test
    @DisplayName("should accept only loopback hosts for the renderer host check")
    void shouldAcceptOnlyLoopbackHosts_forRendererHostCheck() {
        assertThat(EFormBrowserPdfRenderer.isLocalRendererHost("localhost")).isTrue();
        assertThat(EFormBrowserPdfRenderer.isLocalRendererHost("127.0.0.1")).isTrue();
        assertThat(EFormBrowserPdfRenderer.isLocalRendererHost("::1")).isTrue();
        assertThat(EFormBrowserPdfRenderer.isLocalRendererHost("10.0.0.5")).isFalse();
        assertThat(EFormBrowserPdfRenderer.isLocalRendererHost("192.168.1.20")).isFalse();
        assertThat(EFormBrowserPdfRenderer.isLocalRendererHost("host.docker.internal")).isFalse();
        assertThat(EFormBrowserPdfRenderer.isLocalRendererHost("carlos")).isFalse();
    }

    @Test
    @DisplayName("should create a secure temporary renderer directory inside the managed temp root")
    void shouldCreateSecureTemporaryRendererDirectory() throws IOException {
        Path root = Files.createTempDirectory("eform-browser-render-root-");
        Path directory = EFormBrowserPdfRenderer.createSecureTempDirectory(root, "eform-browser-render-test-");
        try {
            assertThat(Files.isDirectory(directory)).isTrue();
            assertThat(directory).hasParentRaw(root);
            if (Files.getFileStore(directory).supportsFileAttributeView("posix")) {
                assertThat(Files.getPosixFilePermissions(directory))
                        .containsExactlyInAnyOrder(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE,
                                PosixFilePermission.OWNER_EXECUTE);
            }
        } finally {
            Files.deleteIfExists(directory);
            Files.deleteIfExists(root);
        }
    }

    @Test
    @DisplayName("should create a secure temporary renderer pdf file inside the managed temp root")
    void shouldCreateSecureTemporaryRendererPdfFile() throws IOException {
        Path root = Files.createTempDirectory("eform-browser-render-root-");
        Path file = EFormBrowserPdfRenderer.createSecureTempFile(root, "eform-browser-render-test-", ".pdf");
        try {
            assertThat(Files.isRegularFile(file)).isTrue();
            assertThat(file).hasParentRaw(root);
            if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
                assertThat(permissions)
                        .containsExactlyInAnyOrder(
                                PosixFilePermission.OWNER_READ,
                                PosixFilePermission.OWNER_WRITE);
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(root);
        }
    }

    @Test
    @DisplayName("should build a local base URL from project home")
    void shouldBuildLocalBaseUrl_whenNoOverrideIsProvided() {
        assertThat(EFormBrowserPdfRenderer.buildDefaultBaseUrl("carlos"))
                .isEqualTo("http://127.0.0.1:8080/carlos");
    }

    @Test
    @DisplayName("should build a local base URL from the active servlet context")
    void shouldBuildLocalBaseUrl_whenUsingTheActiveRequestContext() {
        assertThat(EFormBrowserPdfRenderer.buildLocalBaseUrl("http", 8080, "/carlos"))
                .isEqualTo("http://127.0.0.1:8080/carlos");
    }

    @Test
    @DisplayName("should reject non-local base URLs for the browser renderer")
    void shouldRejectNonLocalBaseUrl_whenValidatingRendererTarget() {
        assertThatThrownBy(() -> EFormBrowserPdfRenderer.validateRendererBaseUrl("https://evil.example/steal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loopback");
    }

    @Test
    @DisplayName("should reject non-root-relative app paths for the browser renderer")
    void shouldRejectNonRootRelativeAppPath_whenValidatingRendererTarget() {
        assertThatThrownBy(() -> EFormBrowserPdfRenderer.validateRendererAppPath("https://evil.example/steal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Application path");
    }

    @Test
    @DisplayName("should pin egress lockdown and capture settings in the browser launch options")
    void shouldPinSecurityAndCaptureSettings_inChromeOptions() {
        ChromeOptions options = EFormBrowserPdfRenderer.buildChromeOptions("/opt/chromium/chrome", true);

        Map<String, Object> capabilities = options.asMap();
        assertThat(capabilities.get("acceptInsecureCerts")).isEqualTo(Boolean.TRUE);

        @SuppressWarnings("unchecked")
        Map<String, Object> chromeOptions = (Map<String, Object>) capabilities.get("goog:chromeOptions");
        assertThat(chromeOptions.get("binary")).isEqualTo("/opt/chromium/chrome");

        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) chromeOptions.get("args");
        assertThat(args)
                .contains("--headless=new")
                .contains("--proxy-server=" + EFormBrowserPdfRenderer.DEAD_PROXY)
                .contains("--proxy-bypass-list=" + EFormBrowserPdfRenderer.PROXY_BYPASS_LOOPBACK)
                .contains("--window-size=1800,3200")
                .contains("--force-device-scale-factor=1")
                .contains("--no-sandbox");
    }

    @Test
    @DisplayName("should keep the sandbox enabled when the sandbox environment opt-in is honoured")
    void shouldKeepSandboxEnabled_whenSandboxOptInRequested() {
        ChromeOptions options = EFormBrowserPdfRenderer.buildChromeOptions(null, false);

        @SuppressWarnings("unchecked")
        Map<String, Object> chromeOptions = (Map<String, Object>) options.asMap().get("goog:chromeOptions");
        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) chromeOptions.get("args");
        assertThat(args).doesNotContain("--no-sandbox");
    }

    @Test
    @DisplayName("should classify request URLs against the allowed loopback origin")
    void shouldClassifyRequestUrls_againstAllowedOrigin() {
        String allowedOrigin = EFormBrowserPdfRenderer.originOf("http://127.0.0.1:8080/carlos");

        assertThat(EFormBrowserPdfRenderer.isDisallowedRendererRequestUrl(
                "http://127.0.0.1:8080/carlos/EFormImageViewForPdfGenerationServlet?imagefile=a.png", allowedOrigin)).isFalse();
        assertThat(EFormBrowserPdfRenderer.isDisallowedRendererRequestUrl(
                "data:image/png;base64,AAAA", allowedOrigin)).isFalse();
        assertThat(EFormBrowserPdfRenderer.isDisallowedRendererRequestUrl(
                "about:blank", allowedOrigin)).isFalse();
        assertThat(EFormBrowserPdfRenderer.isDisallowedRendererRequestUrl(
                "https://evil.example/exfil?x=1", allowedOrigin)).isTrue();
        assertThat(EFormBrowserPdfRenderer.isDisallowedRendererRequestUrl(
                "http://127.0.0.1:9999/other-port", allowedOrigin)).isTrue();
        assertThat(EFormBrowserPdfRenderer.isDisallowedRendererRequestUrl(
                "http://10.0.0.5/internal", allowedOrigin)).isTrue();
    }

    @Test
    @DisplayName("should normalize default ports when computing request origins")
    void shouldNormalizeDefaultPorts_whenComputingOrigins() {
        assertThat(EFormBrowserPdfRenderer.originOf("http://127.0.0.1/x"))
                .isEqualTo(EFormBrowserPdfRenderer.originOf("http://127.0.0.1:80/y"));
        assertThat(EFormBrowserPdfRenderer.originOf("https://127.0.0.1/x"))
                .isEqualTo(EFormBrowserPdfRenderer.originOf("https://127.0.0.1:443/y"));
        assertThat(EFormBrowserPdfRenderer.originOf("not a url")).isNull();
    }

    @Test
    @DisplayName("should redact URLs from third-party error text before logging")
    void shouldRedactUrls_fromErrorText() {
        String redacted = EFormBrowserPdfRenderer.redactUrls(
                "timeout navigating to https://127.0.0.1:8443/carlos/EFormViewForPdfGenerationServlet?fdid=9 after 30s");

        assertThat(redacted)
                .doesNotContain("fdid=9")
                .doesNotContain("127.0.0.1")
                .contains("[redacted-url]");
        assertThat(EFormBrowserPdfRenderer.redactUrls(null)).isNull();
    }

    @Test
    @DisplayName("should convert script region maps into typed capture regions")
    void shouldConvertScriptRegionMaps_toCaptureRegions() throws PDFGenerationException {
        List<EFormBrowserPdfRenderer.CaptureRegion> regions = EFormBrowserPdfRenderer.readRegions(List.of(
                Map.of("x", 0L, "y", 10.5d, "width", 1650L, "height", 2200L),
                Map.of("x", 0L, "y", 2210L, "width", 1650L, "height", 0L)));

        assertThat(regions).hasSize(1);
        assertThat(regions.get(0).width()).isEqualTo(1650d);
        assertThat(regions.get(0).y()).isEqualTo(10.5d);
    }

    @Test
    @DisplayName("should reject unexpected region payload shapes from the page script")
    void shouldRejectUnexpectedRegionPayload_fromPageScript() {
        assertThatThrownBy(() -> EFormBrowserPdfRenderer.readRegions("not-a-list"))
                .isInstanceOf(PDFGenerationException.class);
        assertThatThrownBy(() -> EFormBrowserPdfRenderer.readRegions(List.of(Map.of("x", "NaN"))))
                .isInstanceOf(PDFGenerationException.class);
    }

    @Test
    @DisplayName("should refuse a render slot when the concurrency bound is saturated")
    void shouldRefuseRenderSlot_whenConcurrencyBoundSaturated() {
        Semaphore drained = new Semaphore(0);

        assertThat(EFormBrowserPdfRenderer.acquireRenderSlot(drained, Duration.ofMillis(50))).isFalse();

        Semaphore available = new Semaphore(1);
        assertThat(EFormBrowserPdfRenderer.acquireRenderSlot(available, Duration.ofMillis(50))).isTrue();
    }

}
