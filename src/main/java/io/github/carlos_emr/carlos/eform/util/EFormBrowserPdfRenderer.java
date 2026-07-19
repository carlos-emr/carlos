/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.imageio.ImageIO;

import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.struts2.ServletActionContext;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.chromium.HasCdp;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.springframework.stereotype.Service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

/**
 * Browser-backed eForm PDF renderer driven entirely from the JVM.
 *
 * <p>Selenium launches a pinned headless Chromium (no Node.js runtime anywhere), navigates over
 * loopback to {@link EFormViewForPdfGenerationServlet} using a render-scoped token from
 * {@link EFormRenderTokenService}, captures stabilized page regions via CDP screenshots, and
 * assembles the captures into a PDF for fax and eDoc workflows.</p>
 *
 * <p>Security invariants (change together or not at all):</p>
 * <ul>
 *   <li>Browser egress is locked to loopback by a dead proxy + loopback bypass list, and any
 *       observed non-loopback request fails the render. {@code acceptInsecureCerts} is safe only
 *       because of this lockdown — it can never be leveraged against an external host.</li>
 *   <li>The browser holds no HTTP session or cookies; authorization is a render-scoped fdid-bound
 *       token minted after the caller's {@code _eform} privilege check.</li>
 *   <li>A fresh browser is launched per render so no state can bleed between users' renders.</li>
 * </ul>
 */
@Service
public class EFormBrowserPdfRenderer {

    private static final Logger logger = MiscUtils.getLogger();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Duration RENDER_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SCRIPT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration NETWORK_QUIET_WINDOW = Duration.ofMillis(500);
    private static final Duration NETWORK_QUIET_MAX_WAIT = Duration.ofSeconds(10);
    private static final long SETTLE_DELAY_MILLIS = 1500;

    /** Bounded well below Tomcat's worker pool so renders can never saturate request threads. */
    private static final int MAX_CONCURRENT_RENDERS = 2;
    private static final Duration RENDER_SLOT_WAIT = Duration.ofSeconds(30);
    private static final Semaphore RENDER_SLOTS = new Semaphore(MAX_CONCURRENT_RENDERS, true);

    private static final String BASE_URL_PROPERTY = "eform_pdf_browser_base_url";
    private static final String CHROME_PATH_PROPERTY = "eform_pdf_browser_chromium_path";
    private static final String CHROMEDRIVER_PATH_PROPERTY = "eform_pdf_browser_chromedriver_path";
    private static final String CATALINA_BASE_PROPERTY = "catalina.base";
    private static final String ENV_ALLOW_UNSANDBOXED = "EFORM_RENDER_ALLOW_UNSANDBOXED";

    /**
     * Dead proxy plus a port-scoped loopback bypass: only the application's own loopback origin
     * escapes the dead proxy, so a request to any other host — or any other loopback port — is
     * blocked before it is ever sent. Together with the performance-log gate this reproduces the
     * previous renderer's pre-send route aborts at two independent layers.
     */
    static final String DEAD_PROXY = "http://127.0.0.1:1";

    private static final float CSS_PIXEL_TO_POINTS = 72f / 96f;
    private static final int VIEWPORT_WIDTH = 1800;
    private static final int VIEWPORT_HEIGHT = 3200;

    // DOM-controlled capture geometry caps (fail-closed): a clinic-authored form cannot drive
    // unbounded Chromium/JVM memory or temp storage. Generous vs. any real multi-page eForm.
    private static final int MAX_CAPTURE_REGIONS = 200;
    private static final double MAX_CAPTURE_DIMENSION = 20_000;
    // Peak decoded-image memory is one region at a time (~4 bytes/pixel); 64M px ≈ 256 MB, generous
    // vs. any real eForm page yet far below a single 20000×20000 (1.6 GB) region.
    private static final double MAX_CAPTURE_REGION_PIXELS = 64_000_000d;
    private static final double MAX_CAPTURE_TOTAL_PIXELS = 300_000_000d;

    // ---------------------------------------------------------------------------------------------
    // Browser-side JS, ported verbatim from the retired Playwright renderer script so capture
    // fidelity is unchanged. These run inside the same Chromium engine as before.
    // ---------------------------------------------------------------------------------------------

    /** Async settle: fonts ready, pending images resolved, two animation frames. */
    static final String STABILIZE_ASYNC_JS =
            "var callback = arguments[arguments.length - 1];\n"
            + "(async () => {\n"
            + "  if (document.fonts && document.fonts.ready instanceof Promise) {\n"
            + "    await document.fonts.ready;\n"
            + "  }\n"
            + "  const pendingImages = Array.from(document.images).filter((image) => !image.complete);\n"
            + "  await Promise.all(pendingImages.map((image) => new Promise((resolve) => {\n"
            + "    image.addEventListener('load', resolve, { once: true });\n"
            + "    image.addEventListener('error', resolve, { once: true });\n"
            + "  })));\n"
            + "  await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));\n"
            + "})().then(() => callback(null)).catch((error) => callback(String(error)));";

    /** Print-cleanup style injection and page-flattening layout prep before capture. */
    static final String PREPARE_CAPTURE_JS =
            "const existingCleanupStyle = document.getElementById('eform-browser-pdf-render-cleanup');\n"
            + "if (!existingCleanupStyle) {\n"
            + "  const cleanupStyle = document.createElement('style');\n"
            + "  cleanupStyle.id = 'eform-browser-pdf-render-cleanup';\n"
            + "  cleanupStyle.textContent = `\n"
            + "    .DoNotPrint,\n"
            + "    #BottomButtons,\n"
            + "    #BaseSelect,\n"
            + "    #SupplementalInfo,\n"
            + "    #labDetail {\n"
            + "      display: none !important;\n"
            + "      visibility: hidden !important;\n"
            + "    }\n"
            + "    textarea {\n"
            + "      resize: none !important;\n"
            + "    }\n"
            + "  `;\n"
            + "  document.head.appendChild(cleanupStyle);\n"
            + "}\n"
            + "const body = document.body;\n"
            + "if (body) {\n"
            + "  body.style.margin = '0';\n"
            + "  body.style.padding = '0';\n"
            + "  body.style.width = 'max-content';\n"
            + "  body.style.overflow = 'visible';\n"
            + "}\n"
            + "const html = document.documentElement;\n"
            + "if (html) {\n"
            + "  html.style.margin = '0';\n"
            + "  html.style.padding = '0';\n"
            + "  html.style.background = 'white';\n"
            + "  html.style.overflow = 'visible';\n"
            + "}";

    /** Region computation: pageN nodes, BGImage candidates, dedupe/sort, visible-union fallback. */
    static final String COMPUTE_REGIONS_JS =
            "const rectFromElement = (el) => {\n"
            + "  const elementRect = el.getBoundingClientRect();\n"
            + "  return {\n"
            + "    left: elementRect.left + window.scrollX,\n"
            + "    top: elementRect.top + window.scrollY,\n"
            + "    right: elementRect.right + window.scrollX,\n"
            + "    bottom: elementRect.bottom + window.scrollY,\n"
            + "    width: elementRect.width,\n"
            + "    height: elementRect.height,\n"
            + "  };\n"
            + "};\n"
            + "const isVisibleCaptureCandidate = (el) => {\n"
            + "  const style = window.getComputedStyle(el);\n"
            + "  return style.display !== 'none' && style.visibility !== 'hidden' && style.position !== 'fixed';\n"
            + "};\n"
            + "const unionRects = (elements) => {\n"
            + "  let left = Number.POSITIVE_INFINITY;\n"
            + "  let top = Number.POSITIVE_INFINITY;\n"
            + "  let right = 0;\n"
            + "  let bottom = 0;\n"
            + "  for (const el of elements) {\n"
            + "    if (!isVisibleCaptureCandidate(el)) {\n"
            + "      continue;\n"
            + "    }\n"
            + "    const rect = rectFromElement(el);\n"
            + "    if (rect.width <= 0 || rect.height <= 0) {\n"
            + "      continue;\n"
            + "    }\n"
            + "    left = Math.min(left, rect.left);\n"
            + "    top = Math.min(top, rect.top);\n"
            + "    right = Math.max(right, rect.right);\n"
            + "    bottom = Math.max(bottom, rect.bottom);\n"
            + "  }\n"
            + "  if (!Number.isFinite(left) || !Number.isFinite(top) || right <= left || bottom <= top) {\n"
            + "    return null;\n"
            + "  }\n"
            + "  return { x: Math.max(0, left), y: Math.max(0, top), width: right - left, height: bottom - top };\n"
            + "};\n"
            + "const backgroundCandidates = (elements) => elements\n"
            + "  .filter((el) => el.tagName === 'IMG')\n"
            + "  .filter((el) => /(^BGImage$|background image|bgimage)/i.test(el.id || '')\n"
            + "    || /background image/i.test(el.getAttribute('alt') || ''))\n"
            + "  .filter(isVisibleCaptureCandidate)\n"
            + "  .map(rectFromElement)\n"
            + "  .filter((rect) => rect.width > 0 && rect.height > 0)\n"
            + "  .sort((a, b) => (b.width * b.height) - (a.width * a.height));\n"
            + "const rectFromLargestCandidate = (candidateRects) => {\n"
            + "  if (candidateRects.length === 0) {\n"
            + "    return null;\n"
            + "  }\n"
            + "  const rect = candidateRects[0];\n"
            + "  return {\n"
            + "    x: Math.max(0, rect.left),\n"
            + "    y: Math.max(0, rect.top),\n"
            + "    width: rect.width,\n"
            + "    height: rect.height,\n"
            + "  };\n"
            + "};\n"
            + "const dedupeAndSortCaptureRects = (rects) => rects\n"
            + "  .sort((a, b) => a.top - b.top || a.left - b.left)\n"
            + "  .filter((rect, index, sorted) => {\n"
            + "    if (index === 0) {\n"
            + "      return true;\n"
            + "    }\n"
            + "    const previous = sorted[index - 1];\n"
            + "    return Math.abs(rect.left - previous.left) > 2\n"
            + "      || Math.abs(rect.top - previous.top) > 2\n"
            + "      || Math.abs(rect.width - previous.width) > 2\n"
            + "      || Math.abs(rect.height - previous.height) > 2;\n"
            + "  })\n"
            + "  .map((rect) => ({\n"
            + "    x: Math.max(0, rect.left),\n"
            + "    y: Math.max(0, rect.top),\n"
            + "    width: rect.width,\n"
            + "    height: rect.height,\n"
            + "  }));\n"
            + "const allElements = Array.from(document.body ? document.body.querySelectorAll('*') : []);\n"
            + "const pageNodes = allElements.filter((el) => /^page\\d+$/i.test(el.id));\n"
            + "const captures = pageNodes\n"
            + "  .map((pageNode) => {\n"
            + "    const pageElements = [pageNode, ...pageNode.querySelectorAll('*')];\n"
            + "    return rectFromLargestCandidate(backgroundCandidates(pageElements)) || unionRects(pageElements);\n"
            + "  })\n"
            + "  .filter(Boolean);\n"
            + "if (captures.length > 0) {\n"
            + "  return captures;\n"
            + "}\n"
            + "const pageBackgroundCaptures = dedupeAndSortCaptureRects(backgroundCandidates(allElements));\n"
            + "if (pageBackgroundCaptures.length > 0) {\n"
            + "  return pageBackgroundCaptures;\n"
            + "}\n"
            + "const fallback = unionRects(allElements);\n"
            + "return fallback ? [fallback] : [];";

    /**
     * Renders a saved eForm by loading the token-authorized local servlet route in headless
     * Chromium, capturing page regions, and assembling those captures into a PDF.
     *
     * @param fdid saved eForm data identifier
     * @param providerId provider number the render surface is scoped to; carried inside the
     *        render grant, never on the URL
     * @return readable temporary PDF path; caller owns cleanup
     * @throws PDFGenerationException when no render slot is available, the browser cannot start,
     *         the page fails its gates (bad status, blocked egress, console errors), the render
     *         times out, or no readable PDF is produced
     */
    public Path renderSavedEformPdf(int fdid, String providerId) throws PDFGenerationException {
        if (!acquireRenderSlot(RENDER_SLOTS, RENDER_SLOT_WAIT)) {
            throw new PDFGenerationException("Browser rendering is at capacity; please retry shortly.");
        }
        try {
            return renderWithSlot(fdid, providerId);
        } finally {
            RENDER_SLOTS.release();
        }
    }

    private Path renderWithSlot(int fdid, String providerId) throws PDFGenerationException {
        HttpServletRequest currentRequest = currentRequestOrNull();
        String projectHome = CarlosProperties.getInstance().getProperty("project_home", "");
        String baseUrl = validateRendererBaseUrl(resolveBaseUrl(projectHome, currentRequest));
        String renderToken = EFormRenderTokenService.getInstance().issue(fdid, providerId);
        String appPath = validateRendererAppPath(buildAppPath(fdid, renderToken));
        logger.info("Browser eForm renderer starting: fdid={} baseUrl={}", fdid, baseUrl);

        Path tempRoot = resolveRendererTempRoot();
        Path outputDirectory = null;
        Path outputPdfPath = null;
        ChromeDriver driver = null;
        boolean success = false;
        long deadlineNanos = System.nanoTime() + RENDER_TIMEOUT.toNanos();

        try {
            String allowedOrigin = originOf(baseUrl);
            if (allowedOrigin == null) {
                throw new PDFGenerationException("Browser renderer configuration is invalid for the resolved local eForm URL.");
            }
            outputDirectory = createSecureTempDirectory(tempRoot, "eform-browser-render-");
            outputPdfPath = createSecureTempFile(tempRoot, "eform-browser-render-", ".pdf");

            boolean allowUnsandboxed = allowUnsandboxed();
            if (allowUnsandboxed) {
                logger.warn("Browser eForm renderer running WITHOUT Chromium's OS-level sandbox "
                        + "(EFORM_RENDER_ALLOW_UNSANDBOXED=true); OS-level containment is delegated to the container boundary.");
            }
            driver = createDriver(buildChromeOptions(resolveChromiumPath(), allowUnsandboxed, allowedOrigin));
            driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT).scriptTimeout(SCRIPT_TIMEOUT);
            ((HasCdp) driver).executeCdpCommand("Emulation.setEmulatedMedia", Map.of("media", "screen"));

            List<LogEntry> performanceEntries = new ArrayList<>();
            driver.get(baseUrl + appPath);
            // Drain immediately after navigation so the main-document response is captured into our
            // non-evicting list before any later request flood can push it out of Selenium's
            // bounded internal buffer, then latch its status as a fallback for the final gate.
            drainPerformanceLog(driver, performanceEntries);
            Integer latchedMainStatus = scanNetworkEvents(
                    performanceEntries.stream().map(LogEntry::getMessage).toList(), allowedOrigin).mainDocumentStatus();
            awaitNetworkQuiet(driver, performanceEntries, deadlineNanos);
            settle(driver, deadlineNanos);

            JavascriptExecutor js = driver;
            js.executeScript(PREPARE_CAPTURE_JS);
            List<CaptureRegion> regions = readRegions(js.executeScript(COMPUTE_REGIONS_JS));
            if (regions.isEmpty()) {
                throw new PDFGenerationException("Browser rendering could not determine any eForm page regions to capture.");
            }

            captureRegions(driver, regions, outputDirectory, deadlineNanos);
            drainPerformanceLog(driver, performanceEntries);
            enforceRenderGates(driver, performanceEntries, latchedMainStatus, baseUrl, fdid);

            List<Path> captureFiles = listCaptureFiles(outputDirectory);
            if (captureFiles.isEmpty()) {
                throw new PDFGenerationException("Browser rendering completed without producing any page captures.");
            }
            convertCapturesToPdf(captureFiles, outputPdfPath);
            if (!Files.isReadable(outputPdfPath) || Files.size(outputPdfPath) == 0) {
                throw new PDFGenerationException("Browser rendering completed without producing a readable eForm PDF.");
            }
            success = true;
            return outputPdfPath;
        } catch (PDFGenerationException e) {
            logger.error("Browser eForm renderer failed: fdid={} baseUrl={} reason={}", fdid, baseUrl, redactUrls(e.getMessage()));
            throw e;
        } catch (IOException e) {
            logger.error("Browser eForm renderer I/O failure: fdid={} baseUrl={}", fdid, baseUrl, e);
            throw new PDFGenerationException("Unable to prepare files for the browser PDF renderer.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PDFGenerationException("Browser rendering was interrupted while generating the eForm PDF.", e);
        } catch (RuntimeException e) {
            // WebDriver exception messages can embed page URLs; redact before logging.
            logger.error("Browser eForm renderer failed: fdid={} baseUrl={} error={}", fdid, baseUrl, redactUrls(String.valueOf(e.getMessage())));
            throw new PDFGenerationException("Browser rendering failed while generating the eForm PDF.", e);
        } finally {
            // The grant is render-scoped; invalidate it here so a token the browser never redeemed
            // (or is done with) cannot linger until its TTL.
            EFormRenderTokenService.getInstance().invalidate(renderToken);
            quitQuietly(driver);
            if (!success) {
                deleteQuietly(outputPdfPath);
            }
            deleteRecursivelyQuietly(outputDirectory);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Browser lifecycle and options
    // ---------------------------------------------------------------------------------------------

    /**
     * Builds the pinned launch configuration. The dead-proxy egress lockdown and
     * {@code acceptInsecureCerts} form a paired invariant: insecure certs are acceptable only for
     * loopback rendering, which the proxy configuration guarantees is the only reachable network.
     */
    static ChromeOptions buildChromeOptions(String chromiumBinary, boolean allowUnsandboxed, String allowedOrigin) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new",
                "--disable-dev-shm-usage",
                "--window-size=" + VIEWPORT_WIDTH + "," + VIEWPORT_HEIGHT,
                "--force-device-scale-factor=1",
                "--hide-scrollbars",
                "--proxy-server=" + DEAD_PROXY,
                "--proxy-bypass-list=" + proxyBypassListFor(allowedOrigin),
                // DevTools over a pipe instead of an ephemeral localhost TCP port, so no other
                // local process can attach to the render browser's control channel.
                "--remote-debugging-pipe",
                // Turn off the FileSystem API. Local file access is otherwise blocked by
                // Chromium's default cross-scheme policy plus the strict scheme gate in
                // isDisallowedRendererRequestUrl. INVARIANT: never add --allow-file-access-from-files
                // or --disable-web-security here — either would let a malicious eForm read local
                // files into the captured PDF.
                "--disable-file-system",
                // Close the WebRTC egress hole: RTCPeerConnection ICE/STUN/TURN is UDP and would
                // bypass the HTTP dead proxy entirely (and emits none of the CDP network events the
                // gate inspects). The load-bearing control is forcing all WebRTC UDP through the
                // (dead) proxy so non-proxied ICE/STUN/TURN cannot leave the host; Chromium has no
                // single "WebRtc" feature flag (an unknown --disable-features name is silently
                // ignored), so we do NOT rely on one.
                "--force-webrtc-ip-handling-policy=disable_non_proxied_udp",
                "--disable-background-networking",
                "--disable-extensions",
                "--no-first-run",
                "--no-default-browser-check");
        if (allowUnsandboxed) {
            // Explicit operator opt-out: only legitimate when the container itself is the
            // isolation boundary. Never a silent fallback — see allowUnsandboxed()/createDriver().
            options.addArguments("--no-sandbox");
        }
        options.setAcceptInsecureCerts(true);
        if (chromiumBinary != null && !chromiumBinary.isBlank()) {
            options.setBinary(chromiumBinary.trim());
        }
        LoggingPreferences loggingPreferences = new LoggingPreferences();
        loggingPreferences.enable(LogType.PERFORMANCE, Level.ALL);
        loggingPreferences.enable(LogType.BROWSER, Level.SEVERE);
        options.setCapability("goog:loggingPrefs", loggingPreferences);
        return options;
    }

    /**
     * Builds the port-scoped proxy bypass for the validated loopback origin. Only the
     * application's own {@code host:port} escapes the dead proxy; other loopback ports stay
     * behind it, so a malicious form cannot even send one-shot requests at other local services.
     */
    static String proxyBypassListFor(String allowedOrigin) {
        URI uri = URI.create(allowedOrigin);
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        return "127.0.0.1:" + port + ";localhost:" + port + ";[::1]:" + port;
    }

    /**
     * Secure by default: the render browser keeps Chromium's OS-level sandbox unless the operator
     * explicitly opts out with {@code EFORM_RENDER_ALLOW_UNSANDBOXED=true}. The opt-out is only
     * legitimate where the deployment provides isolation another way (the container is the
     * boundary); it is never chosen automatically, and there is deliberately no fallback that
     * drops the sandbox when a sandboxed launch fails — that would silently reinstate the insecure
     * default.
     */
    static boolean allowUnsandboxed() {
        return "true".equals(System.getenv(ENV_ALLOW_UNSANDBOXED));
    }

    private ChromeDriver createDriver(ChromeOptions options) throws PDFGenerationException {
        try {
            String chromedriverPath = resolveChromedriverPath();
            if (chromedriverPath != null) {
                File chromedriver = PathValidationUtils.validateConfiguredFile(chromedriverPath, CHROMEDRIVER_PATH_PROPERTY);
                ChromeDriverService service = new ChromeDriverService.Builder()
                        .usingDriverExecutable(chromedriver)
                        .build();
                try {
                    return new ChromeDriver(service, options);
                } catch (RuntimeException e) {
                    // Selenium starts the caller-owned service before session creation but does not
                    // stop it if the ChromeDriver constructor throws (e.g. a sandboxed launch that
                    // cannot start). Stop it here so a repeatedly-failing host cannot orphan
                    // chromedriver processes.
                    if (service.isRunning()) {
                        service.stop();
                    }
                    throw e;
                }
            }
            // No pinned chromedriver: Selenium Manager resolves one matching the browser. Intended
            // for dev/CI; production deployments should pin eform_pdf_browser_chromedriver_path.
            return new ChromeDriver(options);
        } catch (RuntimeException e) {
            if (!allowUnsandboxed()) {
                // Fail closed: a sandboxed launch that cannot start (kernel without unprivileged
                // user namespaces, or Chromium refusing the sandbox as root) must not degrade to
                // --no-sandbox on its own. Tell the operator how to make containment real, or how
                // to consciously accept container-level isolation instead.
                throw new PDFGenerationException(
                        "Unable to start the sandboxed headless Chromium renderer for eForms. "
                        + "Enable unprivileged user namespaces and run the renderer as a non-root user so "
                        + "Chromium's sandbox can start, or set EFORM_RENDER_ALLOW_UNSANDBOXED=true only when "
                        + "the container itself provides isolation.", e);
            }
            throw new PDFGenerationException("Unable to start the headless Chromium renderer for eForms.", e);
        }
    }

    private static void quitQuietly(ChromeDriver driver) {
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
        } catch (RuntimeException e) {
            logger.debug("Unable to quit browser eForm renderer driver cleanly", e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Stabilization and capture
    // ---------------------------------------------------------------------------------------------

    /**
     * Best-effort network quiescence: poll the performance log until no new events arrive for the
     * quiet window, capped, never failing the render on its own — mirroring the retired script's
     * ignored-on-timeout {@code networkidle} wait. Drained entries are retained for the gates.
     */
    private void awaitNetworkQuiet(ChromeDriver driver, List<LogEntry> performanceEntries, long deadlineNanos)
            throws InterruptedException {
        long quietSinceNanos = System.nanoTime();
        long waitDeadline = Math.min(deadlineNanos, System.nanoTime() + NETWORK_QUIET_MAX_WAIT.toNanos());
        while (System.nanoTime() < waitDeadline) {
            Thread.sleep(250);
            if (drainPerformanceLog(driver, performanceEntries) > 0) {
                quietSinceNanos = System.nanoTime();
            } else if (System.nanoTime() - quietSinceNanos >= NETWORK_QUIET_WINDOW.toNanos()) {
                return;
            }
        }
    }

    private void settle(ChromeDriver driver, long deadlineNanos) throws InterruptedException, PDFGenerationException {
        Thread.sleep(SETTLE_DELAY_MILLIS);
        checkDeadline(deadlineNanos);
        Object settleError = driver.executeAsyncScript(STABILIZE_ASYNC_JS);
        if (settleError != null) {
            throw new PDFGenerationException("Browser rendering failed while stabilizing the eForm page: " + redactUrls(String.valueOf(settleError)));
        }
        checkDeadline(deadlineNanos);
    }

    private void captureRegions(ChromeDriver driver, List<CaptureRegion> regions, Path outputDirectory, long deadlineNanos)
            throws IOException, PDFGenerationException {
        HasCdp cdp = driver;
        for (int index = 0; index < regions.size(); index++) {
            checkDeadline(deadlineNanos);
            CaptureRegion region = regions.get(index);
            Map<String, Object> clip = Map.of(
                    "x", (double) Math.max(0, Math.floor(region.x())),
                    "y", (double) Math.max(0, Math.floor(region.y())),
                    "width", (double) Math.ceil(region.width()),
                    "height", (double) Math.ceil(region.height()),
                    "scale", 1.0d);
            Map<String, Object> result = cdp.executeCdpCommand("Page.captureScreenshot", Map.of(
                    "format", "png",
                    "clip", clip,
                    "captureBeyondViewport", Boolean.TRUE));
            Object data = result.get("data");
            if (!(data instanceof String encoded) || encoded.isEmpty()) {
                throw new PDFGenerationException("Browser rendering returned an empty capture for an eForm page region.");
            }
            Path outputPath = outputDirectory.resolve(String.format("page-%03d.png", index + 1));
            Files.write(outputPath, Base64.getDecoder().decode(encoded));
        }
    }

    static List<CaptureRegion> readRegions(Object rawRegions) throws PDFGenerationException {
        if (!(rawRegions instanceof List<?> rawList)) {
            throw new PDFGenerationException("Browser rendering returned an unexpected page-region result.");
        }
        // The region geometry comes from the (clinic-authored) eForm DOM. Bound it so a malicious or
        // pathological form cannot drive Chromium/JVM memory or temp storage arbitrarily high despite
        // the render semaphore — reject rather than attempt an enormous capture.
        if (rawList.size() > MAX_CAPTURE_REGIONS) {
            throw new PDFGenerationException("Browser rendering produced too many page regions to capture safely.");
        }
        List<CaptureRegion> regions = new ArrayList<>();
        double totalPixels = 0;
        for (Object rawRegion : rawList) {
            if (!(rawRegion instanceof Map<?, ?> rawMap)) {
                throw new PDFGenerationException("Browser rendering returned an unexpected page-region entry.");
            }
            double x = regionValue(rawMap, "x");
            double y = regionValue(rawMap, "y");
            double width = regionValue(rawMap, "width");
            double height = regionValue(rawMap, "height");
            // Fail closed on non-finite geometry (NaN/Infinity): every comparison below is false for
            // NaN, so it would otherwise slip through the size/budget checks and reach the CDP
            // screenshot with NaN coordinates, failing the whole render unpredictably.
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)) {
                throw new PDFGenerationException("Browser rendering returned a non-finite page-region value.");
            }
            if (width <= 0 || height <= 0) {
                continue;
            }
            if (width > MAX_CAPTURE_DIMENSION || height > MAX_CAPTURE_DIMENSION) {
                throw new PDFGenerationException("Browser rendering page region exceeds the maximum capture dimension.");
            }
            // Per-region pixel cap bounds peak decoded-image memory (one region is held at a time in
            // PDF assembly), independent of the cumulative budget below.
            double regionPixels = width * height;
            if (regionPixels > MAX_CAPTURE_REGION_PIXELS) {
                throw new PDFGenerationException("Browser rendering page region exceeds the safe per-page pixel budget.");
            }
            totalPixels += regionPixels;
            if (totalPixels > MAX_CAPTURE_TOTAL_PIXELS) {
                throw new PDFGenerationException("Browser rendering total capture area exceeds the safe pixel budget.");
            }
            regions.add(new CaptureRegion(x, y, width, height));
        }
        return regions;
    }

    private static double regionValue(Map<?, ?> rawMap, String key) throws PDFGenerationException {
        Object value = rawMap.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new PDFGenerationException("Browser rendering returned a non-numeric page-region value.");
    }

    /** Immutable capture rectangle in CSS page coordinates. */
    record CaptureRegion(double x, double y, double width, double height) {
    }

    // ---------------------------------------------------------------------------------------------
    // Render gates (fail-closed): main-document status, loopback-only egress, console errors
    // ---------------------------------------------------------------------------------------------

    private void enforceRenderGates(ChromeDriver driver, List<LogEntry> performanceEntries,
            Integer latchedMainStatus, String baseUrl, int fdid) throws PDFGenerationException {
        NetworkGateScan scan = scanNetworkEvents(
                performanceEntries.stream().map(LogEntry::getMessage).toList(),
                originOf(baseUrl));
        int disallowedRequests = scan.disallowedRequests();
        // Prefer the status latched immediately after navigation — at that point only the top-level
        // document has responded, so it is unambiguously the MAIN document's status. Fall back to the
        // full-scan value only if the latch is missing. This prevents a later same-origin iframe's
        // Document 200 from standing in for a missing/failed main-document response.
        Integer mainDocumentStatus = latchedMainStatus != null ? latchedMainStatus : scan.mainDocumentStatus();

        int severeConsoleEntries = 0;
        try {
            for (LogEntry entry : driver.manage().logs().get(LogType.BROWSER)) {
                if (entry.getLevel().intValue() >= Level.SEVERE.intValue()) {
                    severeConsoleEntries++;
                }
            }
        } catch (RuntimeException e) {
            logger.debug("Browser console log unavailable for eForm render gate", e);
        }

        if (mainDocumentStatus == null || mainDocumentStatus != 200) {
            logger.error("Browser eForm renderer rejected main document: fdid={} status={}", fdid, mainDocumentStatus);
            throw new PDFGenerationException("Browser rendering did not receive a successful eForm page response. status=" + mainDocumentStatus);
        }
        if (disallowedRequests > 0 || severeConsoleEntries > 0) {
            // Counts only — never request URLs or console text, which can carry eForm content.
            logger.error("Browser eForm renderer surfaced page errors: fdid={} disallowedRequests={} severeConsoleEntries={}",
                    fdid, disallowedRequests, severeConsoleEntries);
            throw new PDFGenerationException("Browser rendering surfaced page errors. disallowedRequests="
                    + disallowedRequests + " consoleErrors=" + severeConsoleEntries);
        }
    }

    /** Outcome of replaying Chrome's network events against the allowed loopback origin. */
    record NetworkGateScan(int disallowedRequests, Integer mainDocumentStatus) {
    }

    /**
     * Replays raw CDP performance-log messages: counts egress attempts to any origin other than
     * the allowed loopback origin and records the status of the first main-frame document
     * response. Later {@code Document} events belong to iframes (e.g. signature blocks) and are
     * ignored.
     *
     * <p>Egress is observed across all CDP channels a page can open, not just HTTP: WebSocket
     * ({@code Network.webSocketCreated}) and WebTransport ({@code Network.webTransportCreated})
     * arrive on their own events rather than {@code requestWillBeSent}. A render surface never
     * legitimately opens either, so <em>any</em> such event fails the render unconditionally — the
     * URL is not origin-checked, because a same-origin {@code wss:}/{@code https:} WebTransport
     * would otherwise pass the origin allowlist and slip a live bidirectional channel past the
     * gate. This keeps the "reject every non-allowlisted egress attempt" invariant whole even
     * though the dead proxy already blocks the external ones.</p>
     */
    static NetworkGateScan scanNetworkEvents(List<String> rawEntries, String allowedOrigin) {
        int disallowedRequests = 0;
        Integer mainDocumentStatus = null;
        for (String rawEntry : rawEntries) {
            JsonNode message = parsePerformanceMessage(rawEntry);
            if (message == null) {
                continue;
            }
            String method = message.path("method").asText("");
            JsonNode params = message.path("params");
            if ("Network.requestWillBeSent".equals(method)) {
                String url = params.path("request").path("url").asText("");
                if (isDisallowedRendererRequestUrl(url, allowedOrigin)) {
                    disallowedRequests++;
                }
            } else if ("Network.webSocketCreated".equals(method) || "Network.webTransportCreated".equals(method)) {
                // A render surface never opens a WebSocket or WebTransport. Fail closed on any such
                // channel regardless of URL/origin: a same-origin wss:/https: WebTransport would pass
                // isDisallowedRendererRequestUrl (http(s) to the allowed origin) yet is still a live
                // bidirectional egress channel the dead HTTP proxy does not cover.
                disallowedRequests++;
            } else if (mainDocumentStatus == null
                    && "Network.responseReceived".equals(method)
                    && "Document".equals(params.path("type").asText(""))
                    && allowedOrigin != null
                    && allowedOrigin.equals(originOf(params.path("response").path("url").asText("")))) {
                mainDocumentStatus = params.path("response").path("status").asInt();
            }
        }
        return new NetworkGateScan(disallowedRequests, mainDocumentStatus);
    }

    private int drainPerformanceLog(ChromeDriver driver, List<LogEntry> performanceEntries) {
        try {
            int before = performanceEntries.size();
            for (LogEntry entry : driver.manage().logs().get(LogType.PERFORMANCE)) {
                performanceEntries.add(entry);
            }
            return performanceEntries.size() - before;
        } catch (RuntimeException e) {
            logger.debug("Browser performance log unavailable for eForm render", e);
            return 0;
        }
    }

    private static JsonNode parsePerformanceMessage(String rawEntry) {
        try {
            return OBJECT_MAPPER.readTree(rawEntry).path("message");
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Strict allowlist gate. Permitted requests are exactly the inert pseudo-schemes
     * ({@code data:}/{@code blob:}/{@code about:}) or http(s) to the validated loopback origin.
     * Everything else — {@code file:}, {@code filesystem:}, {@code chrome:}, {@code view-source:},
     * {@code ftp:}, etc. — is disallowed and fails the render. This is defense-in-depth against
     * <em>local file disclosure into the captured PDF</em> (a {@code file://} subresource is
     * already blocked by Chromium's default cross-scheme policy; this gate is the CARLOS-side
     * backstop if that default is ever weakened), not merely an exfiltration control.
     */
    static boolean isDisallowedRendererRequestUrl(String requestUrl, String allowedOrigin) {
        if (requestUrl == null || requestUrl.isEmpty()
                || requestUrl.startsWith("data:") || requestUrl.startsWith("blob:") || requestUrl.startsWith("about:")) {
            return false;
        }
        String scheme = requestUrl.indexOf(':') > 0 ? requestUrl.substring(0, requestUrl.indexOf(':')) : "";
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            // Any non-web scheme (file:, filesystem:, chrome:, view-source:, ...) is fail-closed:
            // the eForm render surface only ever needs http(s) to the loopback app plus inert
            // data:/blob:/about: resources.
            return true;
        }
        String origin = originOf(requestUrl);
        return origin == null || !origin.equals(allowedOrigin);
    }

    static String originOf(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            int port = uri.getPort();
            if (port == -1) {
                port = "https".equals(scheme) ? 443 : 80;
            }
            return scheme + "://" + uri.getHost().toLowerCase(java.util.Locale.ROOT) + ":" + port;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Strips URLs from third-party error text before it reaches logs (PHI-safe diagnostics). */
    static String redactUrls(String text) {
        if (text == null) {
            return null;
        }
        // Strip http(s) plus other schemes and bare filesystem paths that a WebDriver/settle error
        // could embed, so no URL or local path reaches the logs.
        return text
                .replaceAll("(?i)[a-z][a-z0-9+.-]*://[^\\s'\"<>]+", "[redacted-url]")
                .replaceAll("(?<![\\w./])/[\\w./-]{2,}", "[redacted-path]");
    }

    private static void checkDeadline(long deadlineNanos) throws PDFGenerationException {
        // Difference comparison is nanoTime wrap-around safe, unlike a direct `>`.
        if (System.nanoTime() - deadlineNanos > 0) {
            throw new PDFGenerationException("Browser rendering timed out while generating the eForm PDF.");
        }
    }

    static boolean acquireRenderSlot(Semaphore slots, Duration wait) {
        try {
            return slots.tryAcquire(wait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static HttpServletRequest currentRequestOrNull() {
        try {
            return ServletActionContext.getRequest();
        } catch (RuntimeException e) {
            // Renders triggered outside a Struts request thread fall back to configured base URL.
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // URL construction and validation
    // ---------------------------------------------------------------------------------------------

    static String buildAppPath(int fdid, String renderToken) {
        return "/EFormViewForPdfGenerationServlet?fdid=" + fdid
                + "&browserRender=true"
                + "&" + EFormViewForPdfGenerationServlet.RENDER_TOKEN_PARAM + "="
                + URLEncoder.encode(renderToken == null ? "" : renderToken, StandardCharsets.UTF_8);
    }

    static String buildDefaultBaseUrl(String projectHome) {
        if (projectHome == null || projectHome.isBlank()) {
            return "http://127.0.0.1:8080";
        }
        String normalizedProjectHome = projectHome.startsWith("/") ? projectHome.substring(1) : projectHome;
        normalizedProjectHome = normalizedProjectHome.endsWith("/")
                ? normalizedProjectHome.substring(0, normalizedProjectHome.length() - 1)
                : normalizedProjectHome;
        return "http://127.0.0.1:8080/" + normalizedProjectHome;
    }

    static String buildLocalBaseUrl(String scheme, int port, String contextPath) {
        String normalizedScheme = (scheme == null || scheme.isBlank()) ? "http" : scheme.trim();
        String normalizedContextPath = contextPath == null ? "" : contextPath.trim();
        if (!normalizedContextPath.isEmpty() && !normalizedContextPath.startsWith("/")) {
            normalizedContextPath = Path.of("/", normalizedContextPath).toString();
        }
        if (normalizedContextPath.endsWith("/")) {
            normalizedContextPath = normalizedContextPath.substring(0, normalizedContextPath.length() - 1);
        }
        StringBuilder baseUrl = new StringBuilder(normalizedScheme).append("://127.0.0.1");
        if (port > 0 && !isDefaultPort(normalizedScheme, port)) {
            baseUrl.append(":").append(port);
        }
        baseUrl.append(normalizedContextPath);
        return baseUrl.toString();
    }

    static String validateRendererBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            throw new IllegalArgumentException("Renderer base URL must be non-empty");
        }

        URI uri = URI.create(rawBaseUrl.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Renderer base URL must use http or https");
        }
        if (uri.getHost() == null || !isLocalRendererHost(uri.getHost())) {
            throw new IllegalArgumentException("Renderer base URL host must resolve to loopback");
        }
        return rawBaseUrl.trim().replaceAll("/$", "");
    }

    static String validateRendererAppPath(String appPath) {
        if (appPath == null || appPath.isBlank()) {
            throw new IllegalArgumentException("Application path must be non-empty");
        }
        String normalizedPath = appPath.trim();
        if (!normalizedPath.startsWith("/") || normalizedPath.startsWith("//")) {
            throw new IllegalArgumentException("Application path must be root-relative");
        }
        return normalizedPath;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of a loopback host label for internal renderer pinning; not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of a loopback host label for internal renderer pinning; not a security or authorization decision")
    static boolean isLocalRendererHost(String rawHost) {
        String host = rawHost == null ? "" : rawHost.trim().toLowerCase();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.isEmpty()) {
            return false;
        }
        return Set.of("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1").contains(host);
    }

    private String resolveBaseUrl(String projectHome, HttpServletRequest request) {
        String configuredBaseUrl = CarlosProperties.getInstance().getProperty(BASE_URL_PROPERTY);
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            return configuredBaseUrl.trim().replaceAll("/$", "");
        }
        if (request != null) {
            return buildLocalBaseUrl(request.getScheme(), request.getLocalPort(), request.getContextPath());
        }
        return buildDefaultBaseUrl(projectHome);
    }

    private String resolveChromiumPath() {
        String configuredChromiumPath = CarlosProperties.getInstance().getProperty(CHROME_PATH_PROPERTY);
        if (configuredChromiumPath != null && !configuredChromiumPath.isBlank()) {
            return configuredChromiumPath.trim();
        }
        return null;
    }

    private String resolveChromedriverPath() {
        String configuredChromedriverPath = CarlosProperties.getInstance().getProperty(CHROMEDRIVER_PATH_PROPERTY);
        if (configuredChromedriverPath != null && !configuredChromedriverPath.isBlank()) {
            return configuredChromedriverPath.trim();
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Managed temp locations and PDF assembly (unchanged from the previous renderer)
    // ---------------------------------------------------------------------------------------------

    private Path resolveRendererTempRoot() throws PDFGenerationException {
        try {
            return resolveRendererTempRoot(
                    System.getProperty(CATALINA_BASE_PROPERTY),
                    System.getProperty("java.io.tmpdir"));
        } catch (RuntimeException e) {
            throw new PDFGenerationException("Unable to resolve a managed temporary directory for eForm browser PDF generation.", e);
        }
    }

    /**
     * Resolves the managed temp root for renderer artifacts.
     *
     * <p>The rendered PDF is later reused by the fax flow as {@code faxFilePath}, and
     * {@code FaxManagerImpl.validateFilePath}/{@code resolveAndValidateFilePath} only accept files
     * under {@code DOCUMENT_DIR} or a CARLOS application-owned temp subtree
     * ({@link PathValidationUtils#isInApplicationTempDirectory(File)}) — not the entire shared temp
     * root. The roots returned here ({@code <catalina.base>/work/carlos/eform-browser-pdf-temp} and
     * {@code <java.io.tmpdir>/carlos-eform-browser-pdf-temp}) are CARLOS-owned and satisfy that
     * check; do not move renderer output to a non-{@code carlos}-owned temp location (or to
     * {@code BASE_DOCUMENT_DIR}, removed for exactly this reason) that fax path validation rejects.</p>
     */
    static Path resolveRendererTempRoot(String catalinaBase, String javaTmpDir) {
        if (catalinaBase != null && !catalinaBase.isBlank()) {
            File catalinaDir = PathValidationUtils.resolveConfiguredDirectory(catalinaBase.trim(), CATALINA_BASE_PROPERTY);
            return Path.of(catalinaDir.getPath(), "work", "carlos", "eform-browser-pdf-temp");
        }
        File tempDir = PathValidationUtils.validateConfiguredDirectory(javaTmpDir, "java.io.tmpdir");
        return Path.of(tempDir.getPath(), "carlos-eform-browser-pdf-temp");
    }

    private List<Path> listCaptureFiles(Path outputDirectory) throws IOException {
        try (var stream = Files.newDirectoryStream(outputDirectory, "page-*.png")) {
            List<Path> captures = new ArrayList<>();
            for (Path capture : stream) {
                captures.add(capture);
            }
            // Sort by the numeric page index, not lexically, so ordering is correct past 999 pages.
            return captures.stream()
                    .sorted(Comparator.comparingInt(EFormBrowserPdfRenderer::capturePageIndex)
                            .thenComparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static int capturePageIndex(Path capture) {
        String name = capture.getFileName().toString();
        String digits = name.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            // Overflow on an absurdly long numeric name: sort it last rather than crash the sort.
            return Integer.MAX_VALUE;
        }
    }

    static void convertCapturesToPdf(List<Path> captureFiles, Path outputPdfPath) throws PDFGenerationException {
        try (PDDocument document = new PDDocument()) {
            for (Path captureFile : captureFiles) {
                BufferedImage image = ImageIO.read(captureFile.toFile());
                if (image == null) {
                    throw new PDFGenerationException("Unable to read eForm browser capture image: " + captureFile.getFileName());
                }
                float pageWidth = image.getWidth() * CSS_PIXEL_TO_POINTS;
                float pageHeight = image.getHeight() * CSS_PIXEL_TO_POINTS;
                PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
                document.addPage(page);
                PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.drawImage(pdImage, 0, 0, pageWidth, pageHeight);
                }
            }
            document.save(outputPdfPath.toFile());
        } catch (IOException e) {
            throw new PDFGenerationException("Unable to assemble the browser-rendered eForm captures into a PDF.", e);
        }
    }

    static Path createSecureTempDirectory(Path tempRoot, String prefix) throws IOException {
        return createSecureTempPath(tempRoot, true, prefix, null);
    }

    static Path createSecureTempFile(Path tempRoot, String prefix, String suffix) throws IOException {
        return createSecureTempPath(tempRoot, false, prefix, suffix);
    }

    // FindSecBugs PATH_TRAVERSAL_IN: temp artifacts are created only under a validated managed temp root, with caller-controlled filenames disallowed.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "Renderer temp files are created only beneath resolveRendererTempRoot(), which validates configured roots before creating a managed private temp directory.")
    private static Path createSecureTempPath(Path tempRoot, boolean directory, String prefix, String suffix) throws IOException {
        Path managedRoot = Files.createDirectories(tempRoot);
        FileAttribute<?>[] secureAttributes = securePosixAttributes(directory);
        try {
            return directory
                    ? Files.createTempDirectory(managedRoot, prefix, secureAttributes)
                    : Files.createTempFile(managedRoot, prefix, suffix, secureAttributes);
        } catch (UnsupportedOperationException e) {
            throw new IOException("Renderer temp path requires POSIX filesystem permissions under " + managedRoot, e);
        }
    }

    private static FileAttribute<?>[] securePosixAttributes(boolean directory) {
        try {
            Set<PosixFilePermission> permissions = directory
                    ? EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE)
                    : EnumSet.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE);
            return new FileAttribute<?>[] { PosixFilePermissions.asFileAttribute(permissions) };
        } catch (UnsupportedOperationException e) {
            return new FileAttribute<?>[0];
        }
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison only classifies literal protocol names for default-port handling, not any auth decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "Case-insensitive comparison here only classifies literal protocol names for port defaults and is not used for authentication or authorization.")
    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
    }

    private static void deleteQuietly(Path outputPath) {
        if (outputPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(outputPath);
        } catch (IOException e) {
            logger.debug("Unable to delete temporary browser-rendered PDF {}", outputPath, e);
        }
    }

    private static void deleteRecursivelyQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(EFormBrowserPdfRenderer::deleteQuietly);
        } catch (IOException e) {
            logger.debug("Unable to delete temporary browser-rendered capture directory {}", directory, e);
        }
    }

}
