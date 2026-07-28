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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.util.logging.Level;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.remote.http.ClientConfig;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("EFormBrowserPdfService unit tests")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EFormBrowserPdfServiceUnitTest {

    @Test
    @DisplayName("should build the dedicated browser-render servlet path carrying the render token")
    void shouldBuildAppPath_whenRenderingSavedEformPdf() {
        String appPath = EFormBrowserPdfService.buildAppPath(187,
                new EFormRenderTokenService.RenderToken("tok-abc_123"));

        assertThat(appPath)
                .startsWith("/EFormViewForPdfGenerationServlet?")
                .contains("fdid=187")
                .contains("browserRender=true")
                .contains("renderToken=tok-abc_123")
                .doesNotContain("providerId");
    }

    @Test
    @DisplayName("should keep the zero-margin, exact-color and chrome-hiding rules in the print preparation script")
    void shouldKeepPrintRules_inPrintPreparationScript() {
        assertThat(EFormBrowserPdfService.PREPARE_PRINT_JS)
                .contains("@page")
                .contains("margin: 0")
                .contains("print-color-adjust: exact !important")
                .contains(".DoNotPrint")
                .contains("#BottomButtons")
                .contains("#BaseSelect")
                .contains("#SupplementalInfo")
                .contains("#labDetail")
                // Stray in-flow non-page content (marked by the geometry script) must be hidden so it
                // cannot paginate into extra pages the raster path never produced.
                .contains(".carlos-render-nonpage")
                .contains("resize: none !important")
                // The raster-era screenshot hacks must NOT come back: they broke native pagination.
                .doesNotContain("max-content")
                .doesNotContain("overflow");
    }

    @Test
    @DisplayName("should never paint a root background over the negative z-index layer when preparing print")
    void shouldNotPaintRootBackground_whenPreparingPrint() {
        // Empirical rule, not a derived one: with a background declared on <html>, a form whose
        // scanned background is an <img> at `position:absolute; z-index:-1` printed blank; removing
        // that statement restored it. The loss is undetectable by any gate because the image still
        // loads with HTTP 200. This is a tripwire on three exact spellings — the real guard is the
        // Playwright render check, which prints a fixture using that idiom and inspects the output.
        assertThat(EFormBrowserPdfService.PREPARE_PRINT_JS)
                .doesNotContain("html.style.background")
                .doesNotContain("background = 'white'")
                .doesNotContain("background: white");
    }

    @Test
    @DisplayName("should measure the larger of div box and content box in the page geometry script")
    void shouldMeasureMaxOfDivAndContentBox_inPageGeometryScript() {
        assertThat(EFormBrowserPdfService.COMPUTE_PAGE_GEOMETRY_JS)
                .contains("contentBox")
                .contains("getBoundingClientRect")
                .contains("/^page\\d+$/i")
                .contains("id: pageNode.id")
                // Width is the content extent FROM THE PAGE-DIV ORIGIN (box.right - own.left), not the
                // content span: sizing to the span discarded the content's left offset and cropped
                // offset content when a full-width background 404'd. Height takes the LARGER of the
                // div's flow extent and the content extent from the div top.
                .contains("width: (box.has && (box.right - own.left) > 0) ? (box.right - own.left) : own.width")
                .contains("Math.max(own.height, box.has ? (box.bottom - own.top) : 0)")
                // In-flow body children that neither are nor contain a page div are marked for
                // hiding (interstitial/trailing corpus content the raster path never captured);
                // absolutely-positioned overlays are deliberately left visible.
                .contains("carlos-render-nonpage")
                .contains("position !== 'absolute' && position !== 'fixed'")
                // Substantive excluded content (real text/visual elements, vs invisible spacer junk)
                // is counted and measured so the JVM can WARN that authored content was excluded.
                .contains("const substantive")
                .contains("excludedCount")
                .contains("excludedHeight")
                // Signed-form safety: a spliced-but-failed-to-load signature image is detected and
                // reported so the JVM can offer the clinician the render-anyway choice. The
                // #carlos-signature-unrendered marker covers the no-op-splice case where the composer
                // could not place the signature at all (no img exists to test naturalWidth on).
                .contains("signatureBroken")
                .contains("#signatureDisplay img")
                .contains("naturalWidth === 0")
                .contains("#carlos-signature-unrendered");
        // Ordering is load-bearing: the element must be MEASURED (getBoundingClientRect / substantive)
        // BEFORE it is hidden — the baseline print stylesheet display:none-s .carlos-render-nonpage, so
        // adding the class first would collapse every candidate to 0x0 and the WARN could never fire.
        String geometry = EFormBrowserPdfService.COMPUTE_PAGE_GEOMETRY_JS;
        assertThat(geometry.indexOf("const substantive"))
                .as("substantive must be computed before the element is hidden")
                .isLessThan(geometry.indexOf("child.classList.add('carlos-render-nonpage')"));
    }

    @Test
    @DisplayName("should read complete geometry diagnostics and reject malformed omission fields")
    void shouldReadCompleteGeometryDiagnostics_fromGeometryResult() throws PDFGenerationException {
        EFormBrowserPdfService.PageGeometry geometry = EFormBrowserPdfService.readPageGeometry(Map.of(
                "pages", List.of(Map.of("id", "page1", "width", 750L, "height", 971L)),
                "excludedCount", 2L,
                "excludedHeight", 210.5d,
                "signatureBroken", false,
                "timerCompatibilityFailure", true,
                "labDecisionSupportStubbed", true,
                "providerStampMissing", true));

        assertThat(geometry.pages()).hasSize(1);
        assertThat(geometry.excludedCount()).isEqualTo(2);
        assertThat(geometry.excludedHeight()).isEqualTo(210.5d);
        assertThat(geometry.signatureBroken()).isFalse();
        // Distinct from signatureBroken: a stamp the provider never uploaded is routine, while a
        // signed document that lost its signature is an integrity failure. They must not collapse.
        assertThat(geometry.providerStampMissing()).isTrue();
        assertThat(geometry.timerCompatibilityFailure()).isTrue();
        assertThat(geometry.labDecisionSupportStubbed()).isTrue();

        assertThatThrownBy(() -> EFormBrowserPdfService.readPageGeometry(Map.of(
                "pages", List.of(),
                "excludedCount", -3L,
                "excludedHeight", Double.NaN,
                "signatureBroken", false,
                "timerCompatibilityFailure", false,
                "labDecisionSupportStubbed", false)))
                .isInstanceOf(PDFGenerationException.class);
        assertThatThrownBy(() -> EFormBrowserPdfService.readPageGeometry(Map.of(
                "pages", List.of(),
                "excludedCount", 4L,
                "excludedHeight", 0L,
                "signatureBroken", false)))
                .isInstanceOf(PDFGenerationException.class);

        assertThatThrownBy(() -> EFormBrowserPdfService.readPageGeometry("not-a-map"))
                .isInstanceOf(PDFGenerationException.class);
    }

    @Test
    @DisplayName("should inject the page-size CSS into a dedicated style element from argument zero")
    void shouldInjectPageSizeCss_intoDedicatedStyleElement() {
        assertThat(EFormBrowserPdfService.INJECT_PAGE_SIZE_CSS_JS)
                .contains("arguments[0]")
                .contains("eform-browser-pdf-page-size")
                .contains("style.textContent = css");
    }

    @Test
    @DisplayName("should keep the font, image, and DOM-quiescence settle waits in the stabilization script")
    void shouldKeepSettleWaits_inStabilizationScript() {
        assertThat(EFormBrowserPdfService.STABILIZE_ASYNC_JS)
                .contains("document.fonts.ready instanceof Promise")
                .contains("!image.complete")
                // Script-built forms (the Rich Text Letter editor) assemble their content after
                // onload; without a DOM-quiet window the capture raced the editor and sometimes
                // printed half-built chrome. Bounded so a perpetual animation cannot stall renders.
                .contains("MutationObserver")
                .contains("PerformanceObserver")
                .contains("resourceObserver.observe({ type: 'resource', buffered: true })")
                .contains("quietWindowMillis = 500")
                .contains("maxWaitMillis = 5000")
                // Cap-exit is signalled distinctly from a quiet settle ('CAPPED' vs null) so the JVM
                // can WARN that a still-mutating page was captured as-is rather than logging it clean.
                .contains("'CAPPED'")
                .contains("requestAnimationFrame");
    }

    @Test
    @DisplayName("should resolve the renderer temp root under catalina base so fax path validation accepts the output")
    void shouldResolveRendererTempRoot_underCatalinaBaseWhenConfigured() {
        Path root = EFormBrowserPdfService.resolveRendererTempRoot(
                "/var/lib/tomcat10",
                "/tmp");

        assertThat(root)
                .isEqualTo(Paths.get("/var/lib/tomcat10", "work", "carlos", "eform-browser-pdf-temp"));
    }

    @Test
    @DisplayName("should resolve the renderer temp root under a namespaced system temp fallback")
    void shouldResolveRendererTempRoot_underNamespacedSystemTempFallback() {
        Path root = EFormBrowserPdfService.resolveRendererTempRoot(
                null,
                "/tmp");

        assertThat(root)
                .isEqualTo(Paths.get("/tmp", "carlos-eform-browser-pdf-temp"));
    }

    @Test
    @DisplayName("should accept only loopback hosts for the renderer host check")
    void shouldAcceptOnlyLoopbackHosts_forRendererHostCheck() {
        assertThat(EFormBrowserPdfService.isLocalRendererHost("localhost")).isTrue();
        assertThat(EFormBrowserPdfService.isLocalRendererHost("127.0.0.1")).isTrue();
        assertThat(EFormBrowserPdfService.isLocalRendererHost("::1")).isTrue();
        assertThat(EFormBrowserPdfService.isLocalRendererHost("10.0.0.5")).isFalse();
        assertThat(EFormBrowserPdfService.isLocalRendererHost("192.168.1.20")).isFalse();
        assertThat(EFormBrowserPdfService.isLocalRendererHost("host.docker.internal")).isFalse();
        assertThat(EFormBrowserPdfService.isLocalRendererHost("carlos")).isFalse();
    }

    @Test
    @DisplayName("should create a secure temporary renderer pdf file inside the managed temp root")
    void shouldCreateSecureTempFile_insideManagedTempRoot() throws IOException {
        Path root = Files.createTempDirectory("eform-browser-render-root-");
        Path file = EFormBrowserPdfService.createSecureTempFile(root, "eform-browser-render-test-", ".pdf");
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
    @DisplayName("should emit a single anonymous @page rule plus per-div pagination when every page shares a size")
    void shouldEmitSingleAnonymousPageRule_whenPagesUniform() {
        String css = EFormBrowserPdfService.buildPageSizeCss(List.of(
                new EFormBrowserPdfService.PageSize("page1", 816d, 1056d),
                new EFormBrowserPdfService.PageSize("page2", 816d, 1056d)));

        assertThat(css)
                .contains("@page { size: 816px 1056px; margin: 0; }")
                // A uniform corpus must not fall into the named-page branch.
                .doesNotContain("carlosPage")
                // Every div gets the explicit pagination contract: pinned height (its flow extent can
                // never spill a blank page), region-capture clipping parity, no inter-page gaps, and a
                // forced break after every page BUT the last (a trailing forced break would emit a
                // blank final page on forms that author inline page-break-after on the last div).
                .contains("#page1 { height: 1056px !important; margin: 0 !important; overflow: hidden !important;"
                        + " break-inside: avoid !important; break-after: page !important; }")
                .contains("#page2 { height: 1056px !important; margin: 0 !important; overflow: hidden !important;"
                        + " break-inside: avoid !important; break-after: auto !important; }");
    }

    @Test
    @DisplayName("should emit named @page rules bound by id when page sizes differ")
    void shouldEmitNamedPageRules_whenPageSizesDiffer() {
        String css = EFormBrowserPdfService.buildPageSizeCss(List.of(
                new EFormBrowserPdfService.PageSize("page1", 816d, 1056d),
                new EFormBrowserPdfService.PageSize("page2", 1056d, 816d)));

        assertThat(css)
                .contains("@page carlosPage1 { size: 816px 1056px; margin: 0; }")
                .contains("@page carlosPage2 { size: 1056px 816px; margin: 0; }")
                .contains("#page1 { page: carlosPage1; height: 1056px !important;")
                .contains("#page2 { page: carlosPage2; height: 816px !important;")
                .contains("break-after: page !important; }")
                .contains("break-after: auto !important; }");
    }

    @Test
    @DisplayName("should round fractional page dimensions up to whole css pixels")
    void shouldRoundFractionalDimensionsUp_toWholeCssPixels() {
        String css = EFormBrowserPdfService.buildPageSizeCss(List.of(
                new EFormBrowserPdfService.PageSize("page1", 815.2d, 1055.1d)));

        // Ceil, so a fractional content box is never a hair too small to hold its content.
        assertThat(css)
                .contains("@page { size: 816px 1056px; margin: 0; }")
                .contains("#page1 { height: 1056px !important;");
    }

    @Test
    @DisplayName("should return empty page-size CSS for a free-flow form with no page divs")
    void shouldReturnEmptyPageSizeCss_forFreeFlowForm() {
        assertThat(EFormBrowserPdfService.buildPageSizeCss(List.of())).isEmpty();
    }

    @Test
    @DisplayName("should build a local base URL from project home")
    void shouldBuildLocalBaseUrl_whenNoOverrideIsProvided() {
        assertThat(EFormBrowserPdfService.buildDefaultBaseUrl("carlos"))
                .isEqualTo("http://127.0.0.1:8080/carlos");
    }

    @Test
    @DisplayName("should build a local base URL from the active servlet context")
    void shouldBuildLocalBaseUrl_whenUsingTheActiveRequestContext() {
        assertThat(EFormBrowserPdfService.buildLocalBaseUrl("http", 8080, "/carlos"))
                .isEqualTo("http://127.0.0.1:8080/carlos");
    }

    @Test
    @DisplayName("should use http for the loopback hop when TLS terminated upstream")
    void shouldDeriveHttpLoopbackScheme_whenProxyTerminatesTls() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerPort(443);
        request.setLocalPort(8080);
        assertThat(EFormBrowserPdfService.deriveLoopbackScheme(request)).isEqualTo("http");
    }

    @Test
    @DisplayName("should keep https for the loopback hop when Tomcat terminates TLS")
    void shouldKeepHttpsLoopbackScheme_whenTomcatTerminatesTls() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerPort(8443);
        request.setLocalPort(8443);
        assertThat(EFormBrowserPdfService.deriveLoopbackScheme(request)).isEqualTo("https");
    }

    @Test
    @DisplayName("should reject non-local base URLs for the browser renderer")
    void shouldRejectNonLocalBaseUrl_whenValidatingRendererTarget() {
        assertThatThrownBy(() -> EFormBrowserPdfService.validateRendererBaseUrl("https://evil.example/steal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("loopback");
    }

    @Test
    @DisplayName("should reject non-root-relative app paths for the browser renderer")
    void shouldRejectNonRootRelativeAppPath_whenValidatingRendererTarget() {
        assertThatThrownBy(() -> EFormBrowserPdfService.validateRendererAppPath("https://evil.example/steal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Application path");
    }

    @Test
    @DisplayName("should pin egress lockdown and capture settings in the browser launch options")
    void shouldPinSecurityAndCaptureSettings_inChromeOptions() {
        ChromeOptions options = EFormBrowserPdfService.buildChromeOptions(
                "/opt/chromium/chrome", true, "http://127.0.0.1:8080");

        Map<String, Object> capabilities = options.asMap();
        assertThat(capabilities.get("acceptInsecureCerts")).isEqualTo(Boolean.TRUE);

        @SuppressWarnings("unchecked")
        Map<String, Object> chromeOptions = (Map<String, Object>) capabilities.get("goog:chromeOptions");
        assertThat(chromeOptions.get("binary")).isEqualTo("/opt/chromium/chrome");

        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) chromeOptions.get("args");
        assertThat(args)
                .contains("--headless=new")
                .contains("--proxy-server=" + EFormBrowserPdfService.DEAD_PROXY)
                .contains("--proxy-bypass-list=<-loopback>;127.0.0.1:8080")
                .contains("--remote-debugging-pipe")
                .contains("--disable-file-system")
                // WebRTC (UDP) would bypass the HTTP proxy and the CDP gate — force all WebRTC UDP
                // through the dead proxy so non-proxied ICE/STUN/TURN cannot leave the host.
                .contains("--force-webrtc-ip-handling-policy=disable_non_proxied_udp")
                // The no-op --disable-features=WebRtc flag must not be relied upon.
                .doesNotContain("--disable-features=WebRtc")
                .contains("--window-size=1800,3200")
                .contains("--force-device-scale-factor=1")
                .contains("--no-sandbox")
                // INVARIANT: these flags would enable local file reads and must never be present.
                .doesNotContain("--allow-file-access-from-files")
                .doesNotContain("--disable-web-security");
    }

    @Test
    @DisplayName("should omit --no-sandbox when the OS sandbox is enabled (opt-in)")
    void shouldOmitNoSandbox_whenSandboxEnabled() {
        // Opt-in hardened posture (EFORM_RENDER_SANDBOX=true -> unsandboxed=false): keep Chromium's OS
        // sandbox, so --no-sandbox must be absent. Assert the args are actually populated first so
        // doesNotContain cannot pass vacuously (SonarCloud S5841).
        ChromeOptions options = EFormBrowserPdfService.buildChromeOptions(null, false, "http://127.0.0.1:8080");

        @SuppressWarnings("unchecked")
        Map<String, Object> chromeOptions = (Map<String, Object>) options.asMap().get("goog:chromeOptions");
        @SuppressWarnings("unchecked")
        List<String> args = (List<String>) chromeOptions.get("args");
        assertThat(args).isNotEmpty();
        assertThat(args).doesNotContain("--no-sandbox");
    }

    @Test
    @DisplayName("should default to unsandboxed when EFORM_RENDER_SANDBOX is unset")
    void shouldDefaultToUnsandboxed_whenSandboxEnvVarUnset() {
        // The renderer is unsandboxed by default so it starts out of the box where Chromium's sandbox
        // cannot initialize (root / no user namespaces). The OS sandbox is opt-in via EFORM_RENDER_SANDBOX.
        // The test JVM has no such env var, so sandboxEnabled() must report false.
        assertThat(EFormBrowserPdfService.sandboxEnabled()).isFalse();
    }

    @Test
    @DisplayName("should bound session creation well below the per-command render budget so a doomed launch fails fast")
    void shouldBoundDriverStart_belowRenderBudget() {
        // A doomed browser launch is bounded by DRIVER_START_TIMEOUT (a dedicated watchdog on session
        // creation), which must be shorter than the per-command read budget so it fails fast instead of
        // waiting chromedriver's internal ~60s browser-start timeout and stacking across the fax flow.
        assertThat(EFormBrowserPdfService.DRIVER_START_TIMEOUT)
                .isPositive()
                .isLessThan(EFormBrowserPdfService.WEBDRIVER_COMMAND_READ_TIMEOUT);
    }

    @Test
    @DisplayName("should reject a base URL carrying user-info, query, or fragment components")
    void shouldRejectBaseUrl_withUserInfoQueryOrFragment() {
        // Servlet paths are appended verbatim to the base; a query/fragment would swallow them
        // and fail every render far from the misconfiguration.
        assertThatThrownBy(() -> EFormBrowserPdfService.validateRendererBaseUrl("http://127.0.0.1:8080/carlos?x=1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user-info, query, or fragment");
        assertThatThrownBy(() -> EFormBrowserPdfService.validateRendererBaseUrl("http://127.0.0.1/carlos#frag"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user-info, query, or fragment");
        assertThatThrownBy(() -> EFormBrowserPdfService.validateRendererBaseUrl("http://user:pass@127.0.0.1/carlos"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user-info, query, or fragment");
        // The path component (the context path) must stay allowed.
        assertThat(EFormBrowserPdfService.validateRendererBaseUrl("http://127.0.0.1:8080/carlos"))
                .isEqualTo("http://127.0.0.1:8080/carlos");
    }

    @Test
    @DisplayName("should detect PDF magic bytes for the rendered output gate")
    void shouldDetectPdfMagicBytes_forRenderedOutputGate(@TempDir Path dir) throws IOException {
        Path pdf = dir.resolve("ok.pdf");
        Files.write(pdf, "%PDF-1.7 rest".getBytes(StandardCharsets.US_ASCII));
        Path notPdf = dir.resolve("bad.pdf");
        Files.write(notPdf, new byte[] {1, 2, 3, 4, 5});
        Path tooShort = dir.resolve("short.pdf");
        Files.write(tooShort, "%PD".getBytes(StandardCharsets.US_ASCII));

        // The success gate must reject a nonempty-but-garbage output (a crashed assembly, a
        // stray file) instead of handing it to the fax/eDoc pipeline; an unreadable path is
        // equally a failed render, never an exception.
        assertThat(EFormBrowserPdfService.hasPdfMagicBytes(pdf)).isTrue();
        assertThat(EFormBrowserPdfService.hasPdfMagicBytes(notPdf)).isFalse();
        assertThat(EFormBrowserPdfService.hasPdfMagicBytes(tooShort)).isFalse();
        assertThat(EFormBrowserPdfService.hasPdfMagicBytes(dir.resolve("absent.pdf"))).isFalse();
    }

    @Test
    @DisplayName("should never chain a raw WebDriver cause into the Chromium startup failure")
    void shouldNotChainRawWebDriverCause_intoChromiumStartupFailure() {
        // The raw WebDriver throwable can embed local filesystem paths; a downstream handler
        // that logs the chain would re-emit them unredacted. createDriver's catch throws this
        // factory's exception, so pinning cause-lessness here pins the PHI-safe logging contract
        // against a well-meaning future "preserve the cause for debugging" edit.
        assertThat(EFormBrowserPdfService.chromiumStartupFailure(false))
                .hasNoCause()
                .hasMessageContaining("sandboxed");
        assertThat(EFormBrowserPdfService.chromiumStartupFailure(true))
                .hasNoCause()
                .hasMessageContaining("Chromium renderer");
    }

    @Test
    @DisplayName("should bypass the dead proxy only for the exact render origin, disabling the implicit loopback exemption")
    void shouldScopeProxyBypass_toExactRenderOrigin() {
        // "<-loopback>" is load-bearing: without it Chromium's implicit bypass rules exempt EVERY
        // loopback host and port from the dead proxy, so the explicit entries were advisory only.
        // Its position is load-bearing too: the sentinel must precede the explicit entry, or the
        // subtraction removes that entry as well and dead-proxies the render origin itself
        // (verified empirically against Chromium 148).
        assertThat(EFormBrowserPdfService.proxyBypassListFor("http://127.0.0.1:8080"))
                .isEqualTo("<-loopback>;127.0.0.1:8080");
        // Default ports are made explicit so other loopback ports never match the bypass.
        assertThat(EFormBrowserPdfService.proxyBypassListFor("http://localhost"))
                .isEqualTo("<-loopback>;localhost:80");
        assertThat(EFormBrowserPdfService.proxyBypassListFor("https://[::1]:8443"))
                .isEqualTo("<-loopback>;[::1]:8443");
        assertThat(EFormBrowserPdfService.proxyBypassListFor("https://127.0.0.1"))
                .isEqualTo("<-loopback>;127.0.0.1:443");
    }

    @Test
    @DisplayName("should classify request URLs against the allowed loopback origin")
    void shouldClassifyRequestUrls_againstAllowedOrigin() {
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");

        assertThat(EFormBrowserPdfService.isDisallowedRendererRequestUrl(
                "http://127.0.0.1:8080/carlos/EFormImageViewForPdfGenerationServlet?imagefile=a.png", allowedOrigin)).isFalse();
        assertThat(EFormBrowserPdfService.isDisallowedRendererRequestUrl(
                "data:image/png;base64,AAAA", allowedOrigin)).isFalse();
        assertThat(EFormBrowserPdfService.isDisallowedRendererRequestUrl(
                "about:blank", allowedOrigin)).isFalse();
        assertThat(EFormBrowserPdfService.isDisallowedRendererRequestUrl(
                "https://evil.example/exfil?x=1", allowedOrigin)).isTrue();
        assertThat(EFormBrowserPdfService.isDisallowedRendererRequestUrl(
                "http://127.0.0.1:9999/other-port", allowedOrigin)).isTrue();
        assertThat(EFormBrowserPdfService.isDisallowedRendererRequestUrl(
                "http://10.0.0.5/internal", allowedOrigin)).isTrue();
    }

    @Test
    @DisplayName("should fail closed on local-file and other non-web schemes")
    void shouldFailClosed_onLocalFileAndOtherNonWebSchemes() {
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");

        assertThat(EFormBrowserPdfService.isDisallowedRendererRequestUrl(
                "file:///etc/passwd", allowedOrigin)).isTrue();
        assertThat(EFormBrowserPdfService.isDisallowedRendererRequestUrl(
                "file:///var/lib/OscarDocument/secret.pdf", allowedOrigin)).isTrue();
        assertThat(EFormBrowserPdfService.isDisallowedRendererRequestUrl(
                "filesystem:http://127.0.0.1:8080/temporary/x", allowedOrigin)).isTrue();
        assertThat(EFormBrowserPdfService.isDisallowedRendererRequestUrl(
                "chrome://settings", allowedOrigin)).isTrue();
        assertThat(EFormBrowserPdfService.isDisallowedRendererRequestUrl(
                "view-source:http://127.0.0.1:8080/carlos", allowedOrigin)).isTrue();
        assertThat(EFormBrowserPdfService.isDisallowedRendererRequestUrl(
                "ftp://127.0.0.1/x", allowedOrigin)).isTrue();
    }

    @Test
    @DisplayName("should normalize default ports when computing request origins")
    void shouldNormalizeDefaultPorts_whenComputingOrigins() {
        assertThat(EFormBrowserPdfService.originOf("http://127.0.0.1/x"))
                .isEqualTo(EFormBrowserPdfService.originOf("http://127.0.0.1:80/y"));
        assertThat(EFormBrowserPdfService.originOf("https://127.0.0.1/x"))
                .isEqualTo(EFormBrowserPdfService.originOf("https://127.0.0.1:443/y"));
        assertThat(EFormBrowserPdfService.originOf("not a url")).isNull();
    }

    @Test
    @DisplayName("should sweep stale dirs and long-orphaned output PDFs, keeping fresh and recently-returned artifacts")
    void shouldSweepStaleRendererArtifacts_keepingFreshOnes() throws IOException {
        Path root = Files.createTempDirectory("eform-browser-render-sweep-root-");
        Path staleDir = Files.createDirectory(root.resolve("eform-browser-render-stale123"));
        Path orphanedOutputPdf = Files.createFile(root.resolve("eform-browser-render-orphan.pdf"));
        Path recentOutputPdf = Files.createFile(root.resolve("eform-browser-render-recent.pdf"));
        Path freshDir = Files.createDirectory(root.resolve("eform-browser-render-fresh123"));
        Path unrelated = Files.createFile(root.resolve("keepme.txt"));
        try {
            // Older than the 1h dir window but within the 24h output window.
            FileTime twoHoursOld = FileTime.fromMillis(System.currentTimeMillis() - Duration.ofHours(2).toMillis());
            // Older than the 24h output window — clearly orphaned by a caller that never cleaned up.
            FileTime twoDaysOld = FileTime.fromMillis(System.currentTimeMillis() - Duration.ofHours(48).toMillis());
            Files.setLastModifiedTime(staleDir, twoHoursOld);
            Files.setLastModifiedTime(recentOutputPdf, twoHoursOld);
            Files.setLastModifiedTime(orphanedOutputPdf, twoDaysOld);

            EFormBrowserPdfService.sweepStaleRendererRoots(root);

            assertThat(Files.exists(staleDir)).as("stale render capture dir removed").isFalse();
            // A caller-owned output is reclaimed only long past any request lifetime, never while
            // a workflow could still hold it.
            assertThat(Files.exists(orphanedOutputPdf)).as("output orphaned past 24h swept").isFalse();
            assertThat(Files.exists(recentOutputPdf)).as("recently-returned output pdf kept").isTrue();
            assertThat(Files.exists(freshDir)).as("fresh render dir kept").isTrue();
            assertThat(Files.exists(unrelated)).as("unrelated file kept").isTrue();
        } finally {
            Files.deleteIfExists(staleDir);
            Files.deleteIfExists(orphanedOutputPdf);
            Files.deleteIfExists(recentOutputPdf);
            Files.deleteIfExists(freshDir);
            Files.deleteIfExists(unrelated);
            Files.deleteIfExists(root);
        }
    }

    @Test
    @DisplayName("should convert script page-geometry maps into typed page sizes")
    void shouldConvertScriptGeometryMaps_toPageSizes() throws PDFGenerationException {
        List<EFormBrowserPdfService.PageSize> sizes = EFormBrowserPdfService.readPageSizes(List.of(
                Map.of("id", "page1", "width", 1650L, "height", 2200.5d),
                // A zero-area measured box is skipped (that page falls back to Chromium default paper).
                Map.of("id", "page2", "width", 1650L, "height", 0L)));

        assertThat(sizes).hasSize(1);
        assertThat(sizes.get(0).id()).isEqualTo("page1");
        assertThat(sizes.get(0).width()).isEqualTo(1650d);
        assertThat(sizes.get(0).height()).isEqualTo(2200.5d);
    }

    @Test
    @DisplayName("should accept an empty geometry list for a free-flow form")
    void shouldAcceptEmptyGeometry_forFreeFlowForm() throws PDFGenerationException {
        assertThat(EFormBrowserPdfService.readPageSizes(List.of())).isEmpty();
    }

    @Test
    @DisplayName("should reject unexpected page-geometry payload shapes from the page script")
    void shouldRejectUnexpectedGeometryPayload_fromPageScript() {
        assertThatThrownBy(() -> EFormBrowserPdfService.readPageSizes("not-a-list"))
                .isInstanceOf(PDFGenerationException.class);
        assertThatThrownBy(() -> EFormBrowserPdfService.readPageSizes(List.of(Map.of("id", "page1", "width", "NaN"))))
                .isInstanceOf(PDFGenerationException.class);
    }

    @Test
    @DisplayName("should reject a page with non-finite geometry")
    void shouldRejectPage_whenGeometryNonFinite() {
        assertThatThrownBy(() -> EFormBrowserPdfService.readPageSizes(List.of(
                Map.of("id", "page1", "width", Double.NaN, "height", 100d))))
                .isInstanceOf(PDFGenerationException.class);
        assertThatThrownBy(() -> EFormBrowserPdfService.readPageSizes(List.of(
                Map.of("id", "page1", "width", 100d, "height", Double.POSITIVE_INFINITY))))
                .isInstanceOf(PDFGenerationException.class);
    }

    @Test
    @DisplayName("should reject a page that exceeds the maximum page dimension")
    void shouldRejectPage_whenDimensionExceedsCap() {
        assertThatThrownBy(() -> EFormBrowserPdfService.readPageSizes(List.of(
                Map.of("id", "page1", "width", 999_999L, "height", 100L))))
                .isInstanceOf(PDFGenerationException.class);
    }

    @Test
    @DisplayName("should reject a form with more pages than the safe page cap")
    void shouldRejectForm_whenPageCountExceedsCap() {
        List<Map<String, Object>> tooMany = java.util.Collections.nCopies(201,
                Map.of("id", "page1", "width", 816L, "height", 1056L));
        assertThatThrownBy(() -> EFormBrowserPdfService.readPageSizes(tooMany))
                .isInstanceOf(PDFGenerationException.class);
    }

    @Test
    @DisplayName("should count disallowed origins and take the first document status from network events")
    void shouldScanNetworkEvents_forGateDecisions() {
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");
        List<String> rawEntries = List.of(
                cdpMessage("Network.requestWillBeSent", "\"request\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet?fdid=1\"}"),
                cdpMessage("Network.responseReceived", "\"type\":\"Document\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet?fdid=1\",\"status\":200}"),
                // Later Document events belong to iframes and must not overwrite the main status.
                cdpMessage("Network.responseReceived", "\"type\":\"Document\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/signature\",\"status\":404}"),
                cdpMessage("Network.requestWillBeSent", "\"request\":{\"url\":\"https://evil.example/exfil\"}"),
                cdpMessage("Network.requestWillBeSent", "\"request\":{\"url\":\"data:image/png;base64,AAAA\"}"),
                "not-json");

        EFormBrowserPdfService.NetworkGateScan scan = EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        assertThat(scan.disallowedRequests()).isEqualTo(1);
        assertThat(scan.mainDocumentStatus()).isEqualTo(200);
        // The later 404'd iframe Document is not the main document; it is a same-origin (CARLOS)
        // visual/structural asset — the signature frame — so it counts as a CRITICAL failure (our
        // EMR failed to serve the form's own content), not an advisory one.
        assertThat(scan.failedCriticalSubresources()).isEqualTo(1);
        assertThat(scan.failedSubresources()).isZero();
        // The "not-json" entry is evidence the replay could not account for; it must be counted,
        // not silently skipped, so the gate can fail closed on truncated evidence.
        assertThat(scan.parseFailures()).isEqualTo(1);
    }

    @Test
    @DisplayName("should count renderer write attempts while allowing only GET and HEAD")
    void shouldCountNonReadRequests_whenScanningRendererNetworkEvents() {
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");
        List<String> rawEntries = List.of(
                cdpMessage("Network.requestWillBeSent",
                        "\"request\":{\"url\":\"http://127.0.0.1:8080/carlos/render\",\"method\":\"GET\"}"),
                cdpMessage("Network.requestWillBeSent",
                        "\"request\":{\"url\":\"http://127.0.0.1:8080/carlos/form.css\",\"method\":\"HEAD\"}"),
                cdpMessage("Network.requestWillBeSent",
                        "\"request\":{\"url\":\"http://127.0.0.1:8080/carlos/eform/save\",\"method\":\"POST\"}"),
                cdpMessage("Network.requestWillBeSent",
                        "\"request\":{\"url\":\"https://evil.example/exfil\",\"method\":\"PUT\"}"));

        EFormBrowserPdfService.NetworkGateScan scan =
                EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        assertThat(scan.nonReadRequests()).isEqualTo(2);
    }

    @Test
    @DisplayName("should require the exact renderer URL and validate interaction evidence")
    void shouldRequireExactRendererUrl_andValidInteractionCount() throws Exception {
        String expected = "http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet"
                + "?fdid=1&browserRender=true&renderToken=secret";

        assertThat(EFormBrowserPdfService.isExpectedRendererUrl(expected, expected)).isTrue();
        assertThat(EFormBrowserPdfService.isExpectedRendererUrl(
                "http://127.0.0.1:8080/carlos/other?fdid=1", expected)).isFalse();
        assertThat(EFormBrowserPdfService.isExpectedRendererUrl(
                expected + "&unexpected=true", expected)).isFalse();
        assertThat(EFormBrowserPdfService.readContainedInteractionCount(4L)).isEqualTo(4);
        assertThatThrownBy(() -> EFormBrowserPdfService.readContainedInteractionCount(-1L))
                .isInstanceOf(PDFGenerationException.class);
        assertThatThrownBy(() -> EFormBrowserPdfService.readContainedInteractionCount("4"))
                .isInstanceOf(PDFGenerationException.class);
    }

    @Test
    @DisplayName("should classify a failed self-URL image fetch as advisory for the empty-src idiom")
    void shouldClassifySelfUrlImageFailure_asAdvisory() {
        // Legacy corpus forms carry <img src=""> placeholders (JS-populated signature stamps); an
        // empty src resolves to the page's own URL, and the single-use render token has already been
        // consumed by the navigation, so this self-fetch always fails — but it is not real form
        // content failing, so it must be advisory, never a missing-content signal.
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");
        String mainDocUrl = "http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet?fdid=1";
        List<String> rawEntries = List.of(
                cdpMessage("Network.responseReceived", "\"type\":\"Document\",\"response\":{\"url\":\"" + mainDocUrl + "\",\"status\":200}"),
                cdpMessage("Network.responseReceived", "\"type\":\"Image\",\"response\":{\"url\":\"" + mainDocUrl + "\",\"status\":403}"));

        EFormBrowserPdfService.NetworkGateScan scan = EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        assertThat(scan.mainDocumentStatus()).isEqualTo(200);
        assertThat(scan.failedCriticalSubresources()).isZero();
        assertThat(scan.failedSubresources()).isEqualTo(1);
    }

    @Test
    @DisplayName("should count failed render-critical subresources from both CDP failure legs")
    void shouldCountFailedSubresources_forHttpErrorAndConnectionFailures() {
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");
        List<String> rawEntries = List.of(
                cdpMessage("Network.responseReceived", "\"type\":\"Document\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet?fdid=1\",\"status\":200}"),
                // A 404'd same-origin form background arrives as an HTTP error response, not
                // loadingFailed. It is a CARLOS-served Image → counts as a CRITICAL subresource.
                cdpMessage("Network.responseReceived", "\"type\":\"Image\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormImageViewForPdfGenerationServlet?imagefile=bg.png\",\"status\":404}"),
                // Connection-level failures carry no URL, so a render-critical failure must be
                // reported as potentially incomplete.
                cdpMessage("Network.loadingFailed", "\"type\":\"Image\",\"errorText\":\"net::ERR_CONNECTION_REFUSED\",\"canceled\":false"),
                // Benign: canceled loads are navigation aborts, not broken content.
                cdpMessage("Network.loadingFailed", "\"type\":\"Image\",\"errorText\":\"net::ERR_ABORTED\",\"canceled\":true"),
                // Benign: Chrome's own speculative requests (favicon etc.) are typed Other.
                cdpMessage("Network.responseReceived", "\"type\":\"Other\",\"response\":{\"url\":\"http://127.0.0.1:8080/favicon.ico\",\"status\":404}"),
                cdpMessage("Network.loadingFailed", "\"type\":\"Other\",\"errorText\":\"net::ERR_FAILED\",\"canceled\":false"),
                // Healthy subresource: not counted.
                cdpMessage("Network.responseReceived", "\"type\":\"Script\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormImageViewForPdfGenerationServlet?imagefile=form.js\",\"status\":200}"));

        EFormBrowserPdfService.NetworkGateScan scan = EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        assertThat(scan.mainDocumentStatus()).isEqualTo(200);
        assertThat(scan.disallowedRequests()).isZero();
        // Both failures can remove visible clinical content, even when a connection-level event
        // does not carry a URL that can be attributed to an origin.
        assertThat(scan.failedCriticalSubresources()).isEqualTo(2);
        assertThat(scan.failedSubresources()).isZero();
    }

    @Test
    @DisplayName("should treat failed stylesheets and fonts as advisory rather than missing content")
    void shouldTreatPresentationResources_asAdvisoryFailures() {
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");
        List<String> rawEntries = List.of(
                cdpMessage("Network.responseReceived", "\"type\":\"Document\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet?fdid=1\",\"status\":200}"),
                // An icon-font stylesheet the corpus references relative to the viewer base. Losing it
                // restyles the letter; it does not remove any clinical statement from it, so it must
                // NOT withhold the document from the clinician.
                cdpMessage("Network.responseReceived", "\"type\":\"Stylesheet\",\"response\":{\"url\":\"http://127.0.0.1:8080/css/fontawesome-all.min.css\",\"status\":404}"),
                cdpMessage("Network.loadingFailed", "\"type\":\"Font\",\"errorText\":\"net::ERR_CONNECTION_REFUSED\",\"canceled\":false"),
                // Content-bearing failures in the same scan must still block.
                cdpMessage("Network.responseReceived", "\"type\":\"Image\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormImageViewForPdfGenerationServlet?imagefile=bg.png\",\"status\":404}"));

        EFormBrowserPdfService.NetworkGateScan scan = EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        // Only the missing background image is content; the stylesheet and font are advisory.
        assertThat(scan.failedCriticalSubresources()).isEqualTo(1);
        assertThat(scan.failedSubresources()).isEqualTo(2);
    }

    @Test
    @DisplayName("should treat a containment-blocked off-origin asset as advisory, not missing content")
    void shouldTreatContainmentBlockedAsset_asAdvisoryFailure() {
        // A real corpus form (Saphnelo PSP) embeds a Creative Commons licence badge from
        // i.creativecommons.org. The renderer blocks off-origin fetches by design, so that image can
        // NEVER load here — the failure is proof containment worked, not evidence that clinical
        // content went missing. Blocking the PDF for a decorative third-party badge withholds an
        // otherwise complete clinical record from the clinician.
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");
        List<String> rawEntries = List.of(
                cdpMessage("Network.responseReceived", "\"type\":\"Document\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet?fdid=1\",\"status\":200}"),
                cdpMessage("Network.requestWillBeSent", "\"requestId\":\"CC-1\",\"request\":{\"url\":\"http://i.creativecommons.org/l/by-sa/3.0/80x15.png\",\"method\":\"GET\"}"),
                cdpMessage("Network.loadingFailed", "\"requestId\":\"CC-1\",\"type\":\"Image\",\"errorText\":\"net::ERR_BLOCKED_BY_CLIENT\",\"canceled\":false"));

        EFormBrowserPdfService.NetworkGateScan scan = EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        assertThat(scan.failedCriticalSubresources()).as("must not block the document").isZero();
        assertThat(scan.failedSubresources()).as("still reported as advisory").isEqualTo(1);
        // The off-origin attempt remains separately visible to the operator.
        assertThat(scan.disallowedRequests()).isEqualTo(1);
    }

    @Test
    @DisplayName("should still block when a same-origin image fails, however the off-origin rule reads")
    void shouldStillBlock_whenSameOriginImageFails() {
        // The narrowness is the point. A same-origin image genuinely should have loaded, and a
        // missing scanned background is exactly the catastrophic case this gate exists for.
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");
        List<String> rawEntries = List.of(
                cdpMessage("Network.responseReceived", "\"type\":\"Document\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet?fdid=1\",\"status\":200}"),
                cdpMessage("Network.requestWillBeSent", "\"requestId\":\"BG-1\",\"request\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormImageViewForPdfGenerationServlet?imagefile=bg.png\",\"method\":\"GET\"}"),
                cdpMessage("Network.loadingFailed", "\"requestId\":\"BG-1\",\"type\":\"Image\",\"errorText\":\"net::ERR_FAILED\",\"canceled\":false"));

        EFormBrowserPdfService.NetworkGateScan scan = EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        assertThat(scan.failedCriticalSubresources()).as("a missing same-origin background still blocks").isEqualTo(1);
        assertThat(scan.failedSubresources()).isZero();
    }

    @Test
    @DisplayName("should treat a failed duplicate reference as advisory when the same file also loaded")
    void shouldTreatDuplicateReference_asAdvisoryWhenSameFileLoaded() {
        // Much of the shared-eForm corpus references each asset twice on purpose: once bare, so the
        // form opens off a local disk, and once through ${oscar_image_path} so it resolves when
        // served. Over HTTP the bare reference 404s by design. Measured on the Greig Ultrasound
        // Requisition: /carlos/onBodyLoad_Oct2018.js 404 alongside
        // EFormImageViewForPdfGenerationServlet?imagefile=onBodyLoad_Oct2018.js 200 — the script was
        // present and executing, yet the render was blocked for "missing content".
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");
        List<String> rawEntries = List.of(
                cdpMessage("Network.responseReceived", "\"type\":\"Document\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet?fdid=1\",\"status\":200}"),
                cdpMessage("Network.responseReceived", "\"type\":\"Script\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormImageViewForPdfGenerationServlet?imagefile=onBodyLoad_Oct2018.js\",\"status\":200}"),
                cdpMessage("Network.responseReceived", "\"type\":\"Script\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/onBodyLoad_Oct2018.js\",\"status\":404}"));

        EFormBrowserPdfService.NetworkGateScan scan = EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        assertThat(scan.failedCriticalSubresources()).as("the file demonstrably loaded").isZero();
        assertThat(scan.failedSubresources()).as("still reported as advisory").isEqualTo(1);
    }

    @Test
    @DisplayName("should treat a duplicate reference as advisory even when the failure is scanned first")
    void shouldTreatDuplicateReference_asAdvisoryWhenFailureScannedFirst() {
        // The events are replayed from a buffered performance log in arrival order, so the 404 can
        // precede the 200 for the same file. Classifying inline would make the verdict depend on
        // event ordering; this is the pin for the deferred second pass.
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");
        List<String> rawEntries = List.of(
                cdpMessage("Network.responseReceived", "\"type\":\"Document\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet?fdid=1\",\"status\":200}"),
                cdpMessage("Network.responseReceived", "\"type\":\"Image\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/bg.png\",\"status\":404}"),
                cdpMessage("Network.responseReceived", "\"type\":\"Image\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormImageViewForPdfGenerationServlet?imagefile=bg.png\",\"status\":200}"));

        EFormBrowserPdfService.NetworkGateScan scan = EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        assertThat(scan.failedCriticalSubresources()).isZero();
        assertThat(scan.failedSubresources()).isEqualTo(1);
    }

    @Test
    @DisplayName("should still block a failed resource when no other request loaded that file")
    void shouldStillBlock_whenNoOtherRequestLoadedThatFile() {
        // The downgrade is narrow by construction: it needs an observed 2xx for the SAME filename.
        // A genuinely absent asset has no such response, so it keeps blocking — otherwise this
        // change would have quietly disabled the missing-background gate entirely.
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");
        List<String> rawEntries = List.of(
                cdpMessage("Network.responseReceived", "\"type\":\"Document\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet?fdid=1\",\"status\":200}"),
                cdpMessage("Network.responseReceived", "\"type\":\"Image\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormImageViewForPdfGenerationServlet?imagefile=other.png\",\"status\":200}"),
                cdpMessage("Network.responseReceived", "\"type\":\"Image\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormImageViewForPdfGenerationServlet?imagefile=bg.png\",\"status\":404}"));

        EFormBrowserPdfService.NetworkGateScan scan = EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        assertThat(scan.failedCriticalSubresources()).as("a different file loading proves nothing").isEqualTo(1);
    }

    @Test
    @DisplayName("should not let a redirect license the downgrade of a failed reference")
    void shouldNotDowngrade_whenSameNameOnlyRedirected() {
        // A 302 is not a load, so it cannot license downgrading a failure for the same filename.
        // Both references here genuinely failed to deliver the asset — the redirect is now counted
        // as a failure in its own right — so the expected total is 2, and crucially neither is
        // softened to advisory.
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");
        List<String> rawEntries = List.of(
                cdpMessage("Network.responseReceived", "\"type\":\"Document\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet?fdid=1\",\"status\":200}"),
                cdpMessage("Network.responseReceived", "\"type\":\"Image\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/bg.png\",\"status\":302}"),
                cdpMessage("Network.responseReceived", "\"type\":\"Image\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormImageViewForPdfGenerationServlet?imagefile=bg.png\",\"status\":404}"));

        EFormBrowserPdfService.NetworkGateScan scan = EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        assertThat(scan.failedCriticalSubresources()).isEqualTo(2);
        assertThat(scan.failedSubresources()).as("neither reference is downgraded").isZero();
    }

    @Test
    @DisplayName("should match the imagefile parameter and the path segment as the same asset")
    void shouldResolveResourceBasename_fromQueryAndPath() {
        assertThat(EFormBrowserPdfService.resourceBasename(
                "http://127.0.0.1:8080/carlos/EFormImageViewForPdfGenerationServlet?imagefile=bg.png"))
                .isEqualTo("bg.png");
        assertThat(EFormBrowserPdfService.resourceBasename("http://127.0.0.1:8080/carlos/bg.png"))
                .isEqualTo("bg.png");
        // Percent-encoded names must compare equal to their on-disk form, or bracketed filenames
        // would never match their own successful load.
        assertThat(EFormBrowserPdfService.resourceBasename(
                "http://127.0.0.1:8080/carlos/eform/displayImage?imagefile=scan-1%5B1%5D.png"))
                .isEqualTo("scan-1[1].png");
        assertThat(EFormBrowserPdfService.resourceBasename(null)).isNull();
        assertThat(EFormBrowserPdfService.resourceBasename("")).isNull();
    }

    @Test
    @DisplayName("should fail closed on WebSocket and WebTransport egress attempts")
    void shouldFailClosed_onWebSocketAndWebTransportEgress() {
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");
        List<String> rawEntries = List.of(
                cdpMessage("Network.webSocketCreated", "\"url\":\"wss://evil.example/exfil\""),
                cdpMessage("Network.webTransportCreated", "\"url\":\"https://evil.example/wt\""),
                // Even a WebSocket back to the app's own loopback port is fail-closed: the render
                // surface never opens one, so its presence is treated as an egress attempt.
                cdpMessage("Network.webSocketCreated", "\"url\":\"ws://127.0.0.1:8080/carlos/live\""),
                // A same-origin WebTransport uses an https: URL that WOULD pass the origin allowlist,
                // yet is still a live bidirectional egress channel — it must fail closed too.
                cdpMessage("Network.webTransportCreated", "\"url\":\"https://127.0.0.1:8080/carlos/wt\""));

        EFormBrowserPdfService.NetworkGateScan scan = EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        // WebSocket/WebTransport creations are tallied as live-channel attempts (an always-on hard
        // fail-closed signal), separate from off-origin HTTP, which is only advisory.
        assertThat(scan.liveChannelAttempts()).isEqualTo(4);
        assertThat(scan.disallowedRequests()).isZero();
    }

    @Test
    @DisplayName("should match the main document status when the base URL uses a default port")
    void shouldMatchMainDocumentStatus_withDefaultPortBaseUrl() {
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1/carlos");
        List<String> rawEntries = List.of(
                cdpMessage("Network.responseReceived", "\"type\":\"Document\",\"response\":{\"url\":\"http://127.0.0.1/carlos/EFormViewForPdfGenerationServlet?fdid=1\",\"status\":200}"));

        EFormBrowserPdfService.NetworkGateScan scan = EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        assertThat(scan.mainDocumentStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("should report no main document status when only foreign documents responded")
    void shouldReportNoMainDocumentStatus_whenOnlyForeignDocumentsResponded() {
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");
        List<String> rawEntries = List.of(
                cdpMessage("Network.responseReceived", "\"type\":\"Document\",\"response\":{\"url\":\"http://127.0.0.1:9999/other\",\"status\":200}"));

        EFormBrowserPdfService.NetworkGateScan scan = EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        assertThat(scan.mainDocumentStatus()).isNull();
        assertThat(scan.disallowedRequests()).isZero();
    }

    private static String cdpMessage(String method, String paramsJson) {
        return "{\"message\":{\"method\":\"" + method + "\",\"params\":{" + paramsJson + "}}}";
    }

    @Test
    @DisplayName("should refuse a render slot when the concurrency bound is saturated")
    void shouldRefuseRenderSlot_whenConcurrencyBoundSaturated() {
        Semaphore drained = new Semaphore(0);

        assertThat(EFormBrowserPdfService.acquireRenderSlot(drained, Duration.ofMillis(50)))
                .isEqualTo(EFormBrowserPdfService.SlotAcquisition.TIMED_OUT);

        Semaphore available = new Semaphore(1);
        assertThat(EFormBrowserPdfService.acquireRenderSlot(available, Duration.ofMillis(50)))
                .isEqualTo(EFormBrowserPdfService.SlotAcquisition.ACQUIRED);
    }

    @Test
    @DisplayName("should report interrupted when the waiting thread is interrupted before a slot is taken")
    void shouldReportInterrupted_whenWaitingThreadInterrupted() {
        // A shutdown-time interrupt is not load-shed capacity: it must be distinguishable so the
        // caller gives "aborted" guidance (no retry advice) instead of the capacity message.
        Thread.currentThread().interrupt();
        try {
            EFormBrowserPdfService.SlotAcquisition result =
                    EFormBrowserPdfService.acquireRenderSlot(new Semaphore(0), Duration.ofSeconds(30));

            assertThat(result).isEqualTo(EFormBrowserPdfService.SlotAcquisition.INTERRUPTED);
            // The interrupt status must be re-raised for the caller / shutdown machinery.
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // Clear the flag so it cannot leak into sibling tests running on this thread.
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("should bound every WebDriver command at the render budget instead of Selenium's default")
    void shouldBoundDriverCommandReadTimeout_toRenderBudget() {
        ClientConfig clientConfig = EFormBrowserPdfService.rendererClientConfig();

        assertThat(clientConfig.readTimeout()).isEqualTo(Duration.ofSeconds(90));
        assertThat(clientConfig.connectionTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("should keep the client read timeout above the in-band page-load and script timeouts")
    void shouldKeepReadTimeoutAboveInBandTimeouts_forDriverCommands() {
        // Invariant: a legitimate slow page must always hit the in-band timeout (which chromedriver
        // answers with a clean error) before the HTTP client gives up on the connection — otherwise
        // slow-but-successful renders would start failing with transport errors.
        assertThat(EFormBrowserPdfService.WEBDRIVER_COMMAND_READ_TIMEOUT)
                .isGreaterThan(EFormBrowserPdfService.PAGE_LOAD_TIMEOUT)
                .isGreaterThan(EFormBrowserPdfService.SCRIPT_TIMEOUT);
    }

    @Test
    @DisplayName("should classify only resource-load console entries as network-gated")
    void shouldClassifyResourceLoadConsoleEntries_forConsoleGateExclusion() {
        // Resource failures are handled type-aware by the network scan; the console gate must not
        // double-count them — headless Chrome's speculative /favicon.ico fetch 404s at the origin
        // root on every render, and counting that severe entry failed every real render.
        assertThat(EFormBrowserPdfService.isResourceLoadConsoleEntry(
                "http://127.0.0.1:8080/favicon.ico - Failed to load resource: the server responded with a status of 404 ()")).isTrue();
        assertThat(EFormBrowserPdfService.isResourceLoadConsoleEntry(
                "http://127.0.0.1:8080/carlos/x.png - Failed to load resource: net::ERR_CONNECTION_REFUSED")).isTrue();
        // JavaScript errors remain the console gate's job and must still fail the render.
        assertThat(EFormBrowserPdfService.isResourceLoadConsoleEntry(
                "http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet 12:3 Uncaught ReferenceError: loadSig is not defined")).isFalse();
        assertThat(EFormBrowserPdfService.isResourceLoadConsoleEntry(null)).isFalse();
    }

    @Test
    @DisplayName("should classify CSP containment notices as policy-blocked, not render failures")
    void shouldClassifyCspContainmentEntries_forConsoleGateExclusion() {
        // A CSP block is the render surface's own containment working (the content was refused,
        // fail-safe by construction); the normal viewer emits the identical notices while
        // displaying the form fine, so the render must not fail on them.
        assertThat(EFormBrowserPdfService.isPolicyContainmentConsoleEntry(
                "Loading plugin data from 'data:text/plain,x' violates the following Content Security Policy directive: \"object-src 'none'\". The action has been blocked.")).isTrue();
        assertThat(EFormBrowserPdfService.isPolicyContainmentConsoleEntry(
                "Uncaught TypeError: cannot read properties of undefined")).isFalse();
        assertThat(EFormBrowserPdfService.isPolicyContainmentConsoleEntry(null)).isFalse();
    }

    @Test
    @DisplayName("should fail the render when the performance log cannot be drained")
    void shouldFailRender_whenPerformanceLogUnavailable() {
        // The performance log is the ONLY detector for failed render-critical subresources (the
        // console gate delegates resource failures to it), so a drain fault must fail closed —
        // returning 0 would let a broken render pass every gate on truncated evidence.
        ChromeDriver driver = mock(ChromeDriver.class, RETURNS_DEEP_STUBS);
        when(driver.manage().logs().get(org.openqa.selenium.logging.LogType.PERFORMANCE))
                .thenThrow(new org.openqa.selenium.WebDriverException("perf log broke"));

        EFormBrowserPdfService service = new EFormBrowserPdfService();

        assertThatThrownBy(() -> service.drainPerformanceLog(driver, new java.util.ArrayList<>()))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("network activity");
    }

    @Test
    @DisplayName("should fail the render when the browser console log cannot be retrieved")
    void shouldFailRender_whenConsoleLogUnavailable() {
        // The console gate is the only defense against capturing a form whose background image
        // never painted; an unreadable console must fail closed, not pass with a zero count.
        ChromeDriver driver = mock(ChromeDriver.class, RETURNS_DEEP_STUBS);
        when(driver.manage().logs().get(LogType.BROWSER))
                .thenThrow(new org.openqa.selenium.WebDriverException("log retrieval broke"));

        EFormBrowserPdfService service = new EFormBrowserPdfService();

        assertThatThrownBy(() -> service.enforceRenderGates(
                driver, List.of(), 200, "http://127.0.0.1:8080/carlos", 42))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("console error state");
    }

    // ---------------------------------------------------------------------------------------------
    // enforceRenderGates rejection coverage: each fail-closed reason exercised in isolation. The
    // CDP performance entries are raw JSON strings shaped exactly as scanNetworkEvents/
    // parsePerformanceMessage parse them ({"message":{"method":...,"params":{...}}}), wrapped in
    // LogEntry the way renderWithSlot collects them, and the browser console is a real LogEntries
    // stubbed onto the deep-stub driver so the console gate reads a deterministic set.
    // ---------------------------------------------------------------------------------------------

    private static final String GATE_BASE_URL = "http://127.0.0.1:8080/carlos";
    private static final String MAIN_DOC_URL =
            "http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet?fdid=1";

    @Test
    @DisplayName("should fail the render when no main-document status was ever observed")
    void shouldFailRender_whenMainDocumentStatusMissing() {
        // latched=null and no Document responseReceived → the main-document status is unknown, which
        // must fail closed rather than pass a render whose top-level response is unaccounted for.
        ChromeDriver driver = driverWithConsole(browserConsole());
        EFormBrowserPdfService service = new EFormBrowserPdfService();

        assertThatThrownBy(() -> service.enforceRenderGates(
                driver, List.of(), null, GATE_BASE_URL, 42))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("successful eForm page response");
    }

    @Test
    @DisplayName("should fail the render when the main document responded with a non-200 status")
    void shouldFailRender_whenMainDocumentStatusNon200() {
        ChromeDriver driver = driverWithConsole(browserConsole());
        EFormBrowserPdfService service = new EFormBrowserPdfService();

        assertThatThrownBy(() -> service.enforceRenderGates(
                driver, List.of(), 500, GATE_BASE_URL, 42))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("status=500");
    }

    @Test
    @DisplayName("should report a failed same-origin image as incomplete content")
    void shouldReportMissingContent_whenSameOriginImageFails() throws PDFGenerationException {
        ChromeDriver driver = driverWithConsole(browserConsole());
        EFormBrowserPdfService service = new EFormBrowserPdfService();
        List<LogEntry> entries = List.of(
                perfEntry(responseReceivedJson("Document", MAIN_DOC_URL, 200)),
                perfEntry(responseReceivedJson("Image",
                        "http://127.0.0.1:8080/carlos/EFormImageViewForPdfGenerationServlet?imagefile=bg.png", 404)));

        EFormRenderCompletenessReport report = service.enforceRenderGates(
                driver, entries, 200, GATE_BASE_URL, 42);

        assertThat(report.failedContentResources()).isEqualTo(1);
        assertThat(report.isComplete()).isFalse();
    }

    @Test
    @DisplayName("should fail closed on live egress independently of content approval")
    void shouldFailOnLiveEgress_independentlyOfContentApproval() {
        // Exact incomplete-content approval never overrides a live egress security failure.
        ChromeDriver driver = driverWithConsole(browserConsole());
        EFormBrowserPdfService service = new EFormBrowserPdfService();
        List<LogEntry> entries = List.of(
                perfEntry(responseReceivedJson("Document", MAIN_DOC_URL, 200)),
                perfEntry(cdpMessage("Network.webSocketCreated", "\"url\":\"wss://evil.example/exfil\"")));

        assertThatThrownBy(() -> service.enforceRenderGates(
                driver, entries, 200, GATE_BASE_URL, 42))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("liveChannelAttempts=1");
    }

    @Test
    @DisplayName("should report failed images and scripts as potentially incomplete")
    void shouldReportIncompleteRender_whenImageOrScriptFailed() throws PDFGenerationException {
        ChromeDriver driver = driverWithConsole(browserConsole());
        EFormBrowserPdfService service = new EFormBrowserPdfService();
        List<LogEntry> entries = List.of(
                perfEntry(responseReceivedJson("Document", MAIN_DOC_URL, 200)),
                perfEntry(responseReceivedJson("Image", "https://cdn.example.com/logo.png", 404)),
                perfEntry(responseReceivedJson("Script",
                        "http://127.0.0.1:8080/carlos/share/javascript/faxControl.js", 404)));

        EFormRenderCompletenessReport report = service.enforceRenderGates(
                driver, entries, 200, GATE_BASE_URL, 42);

        assertThat(report.failedContentResources()).isEqualTo(2);
        assertThat(report.isComplete()).isFalse();
    }

    @Test
    @DisplayName("should fail the render when a performance entry cannot be parsed")
    void shouldFailRender_whenPerformanceEntryUnparseable() {
        // Same philosophy as the drain-fault gate: an unparseable network event is egress
        // evidence the replay could not account for — passing on truncated evidence would let a
        // broken render through every gate.
        ChromeDriver driver = driverWithConsole(browserConsole());
        EFormBrowserPdfService service = new EFormBrowserPdfService();
        List<LogEntry> entries = List.of(
                perfEntry(responseReceivedJson("Document", MAIN_DOC_URL, 200)),
                perfEntry("not-json"));

        assertThatThrownBy(() -> service.enforceRenderGates(
                driver, entries, 200, GATE_BASE_URL, 42))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("network activity");
    }

    @Test
    @DisplayName("should tolerate the render when an off-origin HTTP egress request was observed")
    void shouldTolerateRender_whenDisallowedHttpEgressObserved() {
        // An off-origin HTTP request (e.g. a form referencing an external font/CDN/image) is already
        // physically blocked by the dead proxy, so by default observing the attempt is advisory and
        // must NOT deny the fax. It stays fail-closed only under the strict network gate.
        ChromeDriver driver = driverWithConsole(browserConsole());
        EFormBrowserPdfService service = new EFormBrowserPdfService();
        List<LogEntry> entries = List.of(
                perfEntry(responseReceivedJson("Document", MAIN_DOC_URL, 200)),
                perfEntry(requestWillBeSentJson("https://evil.example/exfil")));

        assertThatCode(() -> service.enforceRenderGates(
                driver, entries, 200, GATE_BASE_URL, 42))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should fail the render when a live WebSocket/WebTransport egress channel was observed")
    void shouldFailRender_whenLiveChannelEgressObserved() {
        // A live bidirectional channel bypasses the dead HTTP proxy and is never opened by a render
        // surface, so it stays an always-on hard fail-closed signal even under the default lenient
        // gate — unlike off-origin HTTP, which the proxy already blocks.
        ChromeDriver driver = driverWithConsole(browserConsole());
        EFormBrowserPdfService service = new EFormBrowserPdfService();
        List<LogEntry> entries = List.of(
                perfEntry(responseReceivedJson("Document", MAIN_DOC_URL, 200)),
                perfEntry(cdpMessage("Network.webSocketCreated", "\"url\":\"wss://evil.example/exfil\"")));

        assertThatThrownBy(() -> service.enforceRenderGates(
                driver, entries, 200, GATE_BASE_URL, 42))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("liveChannelAttempts=1");
    }

    @Test
    @DisplayName("should tolerate the render when a severe JavaScript console error is present")
    void shouldTolerateRender_whenSevereConsoleErrorPresent() {
        // A benign page-script error (a JS TypeError) is ubiquitous across the legacy eForm corpus
        // and does not blank the form — the in-app viewer displays the same content. By default this
        // is advisory (logged) and must NOT fail the render; it stays fail-closed under strict mode.
        ChromeDriver driver = driverWithConsole(browserConsole(
                consoleEntry("http://127.0.0.1:8080/carlos/x 12:3 Uncaught TypeError: x is not a function")));
        EFormBrowserPdfService service = new EFormBrowserPdfService();
        List<LogEntry> entries = List.of(perfEntry(responseReceivedJson("Document", MAIN_DOC_URL, 200)));

        assertThatCode(() -> service.enforceRenderGates(
                driver, entries, 200, GATE_BASE_URL, 42))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should pass the render gates when only excluded console entries are present")
    void shouldPassGates_whenOnlyExcludedConsoleEntriesPresent() {
        // Both entries are SEVERE, but a favicon resource-load 404 (handled type-aware by the network
        // scan) and a CSP-containment notice (the surface's own defense working) are excluded, so a
        // healthy render with a 200 main document and no other errors must NOT fail.
        ChromeDriver driver = driverWithConsole(browserConsole(
                consoleEntry("http://127.0.0.1:8080/favicon.ico - Failed to load resource: the server responded with a status of 404 ()"),
                consoleEntry("Loading plugin data from 'data:text/plain,x' violates the following Content Security Policy directive: \"object-src 'none'\". The action has been blocked.")));
        EFormBrowserPdfService service = new EFormBrowserPdfService();
        List<LogEntry> entries = List.of(perfEntry(responseReceivedJson("Document", MAIN_DOC_URL, 200)));

        assertThatCode(() -> service.enforceRenderGates(
                driver, entries, 200, GATE_BASE_URL, 42))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should prefer the latched main-document status over a later same-origin document")
    void shouldPreferLatchedStatus_overLaterIframeDocument() {
        // The scan observes a same-origin Document 200 (e.g. a signature iframe), but the status
        // latched immediately after navigation was 500. The latched value must win so a later
        // Document 200 cannot stand in for a failed main-document response.
        ChromeDriver driver = driverWithConsole(browserConsole());
        EFormBrowserPdfService service = new EFormBrowserPdfService();
        List<LogEntry> entries = List.of(perfEntry(responseReceivedJson("Document", MAIN_DOC_URL, 200)));

        assertThatThrownBy(() -> service.enforceRenderGates(
                driver, entries, 500, GATE_BASE_URL, 42))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("status=500");
    }

    private static LogEntry perfEntry(String cdpJson) {
        // Performance-log entries reach enforceRenderGates as LogEntry whose message is the raw CDP
        // JSON; the level/timestamp are irrelevant to the network scan.
        return new LogEntry(Level.INFO, 0L, cdpJson);
    }

    private static String responseReceivedJson(String type, String url, int status) {
        return cdpMessage("Network.responseReceived",
                "\"type\":\"" + type + "\",\"response\":{\"url\":\"" + url + "\",\"status\":" + status + "}");
    }

    private static String requestWillBeSentJson(String url) {
        return cdpMessage("Network.requestWillBeSent", "\"request\":{\"url\":\"" + url + "\"}");
    }

    private static LogEntry consoleEntry(String message) {
        // SEVERE so the entry clears the console gate's level threshold; whether it is then counted
        // depends solely on the resource-load / CSP-containment exclusions.
        return new LogEntry(Level.SEVERE, 0L, message);
    }

    private static LogEntries browserConsole(LogEntry... entries) {
        return new LogEntries(List.of(entries));
    }

    private static ChromeDriver driverWithConsole(LogEntries console) {
        ChromeDriver driver = mock(ChromeDriver.class, RETURNS_DEEP_STUBS);
        when(driver.manage().logs().get(LogType.BROWSER)).thenReturn(console);
        return driver;
    }

    @Test
    @DisplayName("should wrap an invalid base-URL configuration as a PDFGenerationException")
    void shouldWrapInvalidBaseUrlConfig_asPdfGenerationException() {
        CarlosProperties properties = CarlosProperties.getInstance();
        String originalBaseUrl = properties.getProperty("eform_pdf_browser_base_url");
        properties.setProperty("eform_pdf_browser_base_url", "https://not-loopback.example");
        try {
            EFormBrowserPdfService service = new EFormBrowserPdfService();

            // A non-loopback base URL makes validateRendererBaseUrl throw IllegalArgumentException;
            // the render path must surface that as a checked PDFGenerationException naming the
            // configuration problem, not let the unchecked IAE escape the throws contract.
            assertThatThrownBy(() -> service.renderSavedEformPdfAuthorized(4242, "999998", null))
                    .isInstanceOf(PDFGenerationException.class)
                    .hasMessageContaining("configuration");
        } finally {
            if (originalBaseUrl == null) {
                properties.remove("eform_pdf_browser_base_url");
            } else {
                properties.setProperty("eform_pdf_browser_base_url", originalBaseUrl);
            }
        }
    }

    @Test
    @DisplayName("should throw a plain IllegalArgumentException, not a base-URL diagnosis, "
            + "when a printed PDF payload is corrupt base64")
    void shouldThrowIllegalArgumentException_whenPrintToPdfPayloadIsCorruptBase64(@TempDir Path tempDir)
            throws Exception {
        // Regression pin for the review finding on this task: a corrupt CDP Page.printToPDF payload
        // makes Base64.getDecoder().decode(encoded) inside printToPdf() throw a plain
        // IllegalArgumentException. If renderWithSlot's IllegalArgumentException catch wrapped the
        // ENTIRE render body, this downstream IAE would be misreported as
        // "Browser renderer base URL configuration is invalid: Illegal base64 character..." even
        // though the base URL was perfectly valid.
        //
        // renderWithSlot() itself cannot be driven end-to-end in a pure unit test past this point:
        // printToPdf() runs only after createDriver()/buildChromeOptions() launch a real Chromium via
        // chromedriver, which a unit test does not have available (see
        // EFormBrowserPdfServiceSeleniumSmokeIntegrationTest for the real-browser, full-pipeline coverage
        // that assumeTrue()-skips when no Chromium/chromedriver is present). So this test pins the
        // downstream failure at its source instead: printToPdf() (invoked here directly via
        // reflection, since it is private) throws a raw IllegalArgumentException — never a
        // PDFGenerationException naming "configuration" — for corrupt print data.
        //
        // The structural half of the guarantee is verified by reading renderWithSlot(): the
        // catch (IllegalArgumentException e) block is now lexically scoped to ONLY the
        // validateRendererBaseUrl(resolveBaseUrl(...)) call in its own try/catch, which executes
        // and completes (successfully, on the normal path) before the main try block — and
        // therefore before printToPdf() — ever runs. There is no catch (IllegalArgumentException
        // e) around the main try any more, so an IAE raised here necessarily falls through to
        // catch (RuntimeException e) and gets the honest generic "Browser rendering failed..."
        // message instead of a false base-URL configuration diagnosis.
        ChromeDriver driver = mock(ChromeDriver.class, RETURNS_DEEP_STUBS);
        when(driver.executeCdpCommand(eq("Page.printToPDF"), anyMap()))
                .thenReturn(Map.of("data", "!!!not-valid-base64!!!"));
        Path outputPdfPath = tempDir.resolve("eform-browser-render-out.pdf");
        long deadlineNanos = System.nanoTime() + Duration.ofMinutes(1).toNanos();

        Method printToPdfMethod = EFormBrowserPdfService.class.getDeclaredMethod(
                "printToPdf", ChromeDriver.class, Path.class, long.class);
        printToPdfMethod.setAccessible(true);
        EFormBrowserPdfService service = new EFormBrowserPdfService();

        assertThatThrownBy(() -> {
            try {
                printToPdfMethod.invoke(service, driver, outputPdfPath, deadlineNanos);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }).isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(PDFGenerationException.class)
                .hasMessageContaining("Illegal base64 character");
    }

    @Test
    @DisplayName("should invalidate the render grant when the render fails before the browser starts")
    void shouldInvalidateRenderGrant_whenRenderFailsBeforeBrowserStart(@TempDir Path tempDir) {
        CarlosProperties properties = CarlosProperties.getInstance();
        String originalChromedriverPath = properties.getProperty("eform_pdf_browser_chromedriver_path");
        String originalCatalinaBase = System.getProperty("catalina.base");
        properties.setProperty("eform_pdf_browser_chromedriver_path",
                tempDir.resolve("missing-chromedriver").toString());
        System.setProperty("catalina.base", tempDir.toString());

        EFormRenderTokenService tokenService = EFormRenderTokenService.getInstance();
        long grantsBefore = tokenService.size();
        try {
            EFormBrowserPdfService service = new EFormBrowserPdfService();

            assertThatThrownBy(() -> service.renderSavedEformPdfAuthorized(424242, "999998", null))
                    .isInstanceOf(PDFGenerationException.class);

            // The grant issued for this render must not linger for its TTL after the failure —
            // a live token is a loopback render capability (the exact bug this pins).
            assertThat(tokenService.size()).isEqualTo(grantsBefore);
        } finally {
            if (originalChromedriverPath == null) {
                properties.remove("eform_pdf_browser_chromedriver_path");
            } else {
                properties.setProperty("eform_pdf_browser_chromedriver_path", originalChromedriverPath);
            }
            if (originalCatalinaBase == null) {
                System.clearProperty("catalina.base");
            } else {
                System.setProperty("catalina.base", originalCatalinaBase);
            }
        }
    }


    @Test
    @DisplayName("should count a redirected subresource as missing content")
    void shouldCountRedirectedSubresource_asMissingContent() {
        // A redirect means the browser never received the asset. The gate scored a 3xx as neither
        // loaded nor failed, which is a silent hole in a completeness guarantee even though the
        // render surface does not currently produce one.
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");
        List<String> rawEntries = List.of(
                cdpMessage("Network.responseReceived", "\"type\":\"Document\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet?fdid=1\",\"status\":200}"),
                cdpMessage("Network.responseReceived", "\"type\":\"Image\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormImageViewForPdfGenerationServlet?imagefile=bg.png\",\"status\":302}"));

        EFormBrowserPdfService.NetworkGateScan scan = EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        assertThat(scan.failedCriticalSubresources()).isEqualTo(1);
    }

    @Test
    @DisplayName("should treat 304 Not Modified as a successful load, not a redirect")
    void shouldTreatNotModified_asSuccessfulLoad() {
        // The half that could actually bite. A 304 is a conditional cache hit — the browser has the
        // bytes. Lumping it in with redirects would mark every cached asset as missing content and
        // refuse essentially every document the moment a render-surface cache existed.
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");
        List<String> rawEntries = List.of(
                cdpMessage("Network.responseReceived", "\"type\":\"Document\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet?fdid=1\",\"status\":200}"),
                cdpMessage("Network.responseReceived", "\"type\":\"Script\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormImageViewForPdfGenerationServlet?imagefile=form.js\",\"status\":304}"),
                // Same filename, bare duplicate reference, fails. The 304 above must license the
                // downgrade or a cached asset would stop counting as present.
                cdpMessage("Network.responseReceived", "\"type\":\"Script\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/form.js\",\"status\":404}"));

        EFormBrowserPdfService.NetworkGateScan scan = EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        assertThat(scan.failedCriticalSubresources()).as("304 proves the asset loaded").isZero();
        assertThat(scan.failedSubresources()).as("the duplicate 404 is advisory").isEqualTo(1);
    }

    @Test
    @DisplayName("should keep a redirected presentation asset advisory")
    void shouldKeepRedirectedPresentationAsset_advisory() {
        // The redirect branch reuses the existing exceptions rather than adding a new blocking path,
        // so losing a stylesheet still degrades presentation only.
        String allowedOrigin = EFormBrowserPdfService.originOf("http://127.0.0.1:8080/carlos");
        List<String> rawEntries = List.of(
                cdpMessage("Network.responseReceived", "\"type\":\"Document\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/EFormViewForPdfGenerationServlet?fdid=1\",\"status\":200}"),
                cdpMessage("Network.responseReceived", "\"type\":\"Stylesheet\",\"response\":{\"url\":\"http://127.0.0.1:8080/carlos/css/x.css\",\"status\":302}"));

        EFormBrowserPdfService.NetworkGateScan scan = EFormBrowserPdfService.scanNetworkEvents(rawEntries, allowedOrigin);

        assertThat(scan.failedCriticalSubresources()).isZero();
        assertThat(scan.failedSubresources()).isEqualTo(1);
    }

    @Test
    @DisplayName("should hide CARLOS warning chrome from the printed document")
    void shouldHideCarlosWarningChrome_fromPrintedDocument() {
        // The timer-compat shim inserts a fixed-position red notice at the top of the body to warn a
        // clinician on screen. On the render surface it was being printed INTO the PDF, and on a real
        // corpus form it covered the document's title. Found by rasterizing a random sample of
        // rendered PDFs and looking at them — no gate could see it, because nothing was missing.
        assertThat(EFormBrowserPdfService.PREPARE_PRINT_JS)
                .contains("#carlos-eform-timer-compat-error")
                .contains("#carlos-render-advisory");
    }

    @Test
    @DisplayName("should consume the timer-idle result and re-await assets the timers started")
    void shouldConsumeTimerIdleResult_andReawaitAssets() {
        // whenIdle's boolean used to be discarded — a bare `await`. It resolves false when the 4s cap
        // expired with the form's own timers still pending, which is the only signal that the page was
        // captured before its deferred work ran, and nothing consumed it: that render reported
        // complete. The script now returns TIMERS_PENDING for it.
        assertThat(EFormBrowserPdfService.STABILIZE_ASYNC_JS)
                .contains("timersDrained = await timerCompat.whenIdle(4000)")
                .contains("timersDrained ? null : 'TIMERS_PENDING'");

        // And the asset waits must run AGAIN after the timers, not only before. document.fonts.ready
        // and the pending-image wait both appear twice for that reason; the quiet window between them
        // observes DOM mutations, not resource completion, so a timer that sets img.src would
        // otherwise be captured mid-flight.
        // Count the await, not the identifier: each block names document.fonts.ready twice (the
        // instanceof guard and the await itself), so counting mentions would say four.
        assertThat(EFormBrowserPdfService.STABILIZE_ASYNC_JS.split("await document\\.fonts\\.ready", -1).length - 1)
                .describedAs("fonts awaited before and after the legacy timers")
                .isEqualTo(2);
        assertThat(EFormBrowserPdfService.STABILIZE_ASYNC_JS.split("!image\\.complete", -1).length - 1)
                .describedAs("pending images awaited before and after the legacy timers")
                .isEqualTo(2);
    }

    /**
     * The decision that gives every approval in this feature its meaning.
     *
     * <p>Until this predicate was extracted it lived inline below {@code createDriver}, which builds
     * a real browser with no injection point — so it could not be reached from a unit test at all,
     * and deleting the {@code approval == null} clause (which would release every document when no
     * approval was supplied) broke nothing in the suite.</p>
     */
    @org.junit.jupiter.api.Nested
    @DisplayName("withholdsDocument")
    class WithholdsDocument {

        private static final String PROVIDER = "doc1";
        private static final int FDID = 42;

        private EFormRenderCompletenessReport blocking() {
            // A failed content resource: blocking, not advisory.
            return new EFormRenderCompletenessReport(1, 0, 0, 0, false, false, false, false, false);
        }

        private EFormRenderApproval approvalFor(
                EFormRenderCompletenessReport report, int fdid, String providerNo) {
            // The constructor is package-private precisely so the util package can mint one.
            return new EFormRenderApproval(providerNo, "123",
                    EFormRenderApprovalService.Operation.DOWNLOAD,
                    java.util.Map.of(fdid, report.digest()),
                    java.time.Instant.now().plusSeconds(120));
        }

        @Test
        @DisplayName("should withhold a blocking render when no approval was supplied")
        void shouldWithhold_whenNoApprovalSupplied() {
            assertThat(EFormBrowserPdfService.withholdsDocument(blocking(), null, FDID, PROVIDER))
                    .isTrue();
        }

        @Test
        @DisplayName("should release a blocking render for an exactly matching approval")
        void shouldRelease_forExactlyMatchingApproval() {
            EFormRenderCompletenessReport report = blocking();

            assertThat(EFormBrowserPdfService.withholdsDocument(
                    report, approvalFor(report, FDID, PROVIDER), FDID, PROVIDER)).isFalse();
        }

        @Test
        @DisplayName("should withhold when the approval covers a different issue set")
        void shouldWithhold_whenApprovalCoversDifferentIssueSet() {
            // The clinician approved one failed resource; this render reported two. Consent was
            // given for a document that no longer exists, so the ticket must not carry over.
            EFormRenderApproval approved = approvalFor(blocking(), FDID, PROVIDER);
            EFormRenderCompletenessReport different =
                    new EFormRenderCompletenessReport(2, 0, 0, 0, false, false, false, false, false);

            assertThat(EFormBrowserPdfService.withholdsDocument(different, approved, FDID, PROVIDER))
                    .isTrue();
        }

        @Test
        @DisplayName("should withhold when the approval belongs to another provider or eForm")
        void shouldWithhold_whenApprovalBelongsElsewhere() {
            EFormRenderCompletenessReport report = blocking();

            assertThat(EFormBrowserPdfService.withholdsDocument(
                    report, approvalFor(report, FDID, "someoneElse"), FDID, PROVIDER)).isTrue();
            assertThat(EFormBrowserPdfService.withholdsDocument(
                    report, approvalFor(report, 999, PROVIDER), FDID, PROVIDER)).isTrue();
        }

        @Test
        @DisplayName("should withhold when the approval has expired")
        void shouldWithhold_whenApprovalExpired() {
            EFormRenderCompletenessReport report = blocking();
            EFormRenderApproval stale = new EFormRenderApproval(PROVIDER, "123",
                    EFormRenderApprovalService.Operation.DOWNLOAD,
                    java.util.Map.of(FDID, report.digest()),
                    java.time.Instant.now().minusSeconds(1));

            assertThat(EFormBrowserPdfService.withholdsDocument(report, stale, FDID, PROVIDER))
                    .isTrue();
        }

        @Test
        @DisplayName("should release a render whose only conditions are advisory")
        void shouldRelease_whenOnlyAdvisoryConditionsPresent() {
            // A contained dialog and legacy timer compatibility failure are reported but never
            // withhold, so an approval is not required and none is supplied.
            EFormRenderCompletenessReport advisoryOnly =
                    new EFormRenderCompletenessReport(0, 0, 0, 1, false, true, false, false, false);

            assertThat(EFormBrowserPdfService.withholdsDocument(advisoryOnly, null, FDID, PROVIDER))
                    .isFalse();
        }

        @Test
        @DisplayName("should withhold when a missing provider stamp is the only condition")
        void shouldWithhold_whenProviderStampMissingAlone() {
            // The 9th report component, and the one that reaches the gate without any failed
            // resource to accompany it. Nothing else constructs it true.
            EFormRenderCompletenessReport stampMissing =
                    new EFormRenderCompletenessReport(0, 0, 0, 0, false, false, false, false, true);

            assertThat(stampMissing.hasBlockingOmissions()).isTrue();
            assertThat(EFormBrowserPdfService.withholdsDocument(stampMissing, null, FDID, PROVIDER))
                    .isTrue();
            // An approval minted for a STAMPED render must not release an unstamped one.
            assertThat(EFormBrowserPdfService.withholdsDocument(
                    stampMissing, approvalFor(blocking(), FDID, PROVIDER), FDID, PROVIDER)).isTrue();
        }
    }
}
