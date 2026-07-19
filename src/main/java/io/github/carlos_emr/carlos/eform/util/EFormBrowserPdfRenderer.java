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
 * loopback to {@link EFormViewForPdfGenerationServlet} using a single-use render token from
 * {@link EFormRenderTokenService}, captures stabilized page regions via CDP screenshots, and
 * assembles the captures into a PDF for fax and eDoc workflows.</p>
 *
 * <p>Security invariants (change together or not at all):</p>
 * <ul>
 *   <li>Browser egress is locked to loopback by a dead proxy + loopback bypass list, and any
 *       observed non-loopback request fails the render. {@code acceptInsecureCerts} is safe only
 *       because of this lockdown — it can never be leveraged against an external host.</li>
 *   <li>The browser holds no HTTP session or cookies; authorization is a one-shot fdid-bound
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
    private static final String ENV_ENABLE_SANDBOX = "EFORM_RENDER_ENABLE_CHROMIUM_SANDBOX";

    /**
     * Dead proxy plus loopback bypass: every non-loopback fetch is routed into a closed port and
     * fails, while loopback traffic goes direct. Together with the performance-log gate this
     * reproduces the previous renderer's abort-non-local-request behavior at two layers.
     */
    static final String DEAD_PROXY = "http://127.0.0.1:1";
    static final String PROXY_BYPASS_LOOPBACK = "127.0.0.1;localhost;[::1]";

    private static final float CSS_PIXEL_TO_POINTS = 72f / 96f;
    private static final int VIEWPORT_WIDTH = 1800;
    private static final int VIEWPORT_HEIGHT = 3200;

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
     *        single-use render grant, never on the URL
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
            outputDirectory = createSecureTempDirectory(tempRoot, "eform-browser-render-");
            outputPdfPath = createSecureTempFile(tempRoot, "eform-browser-render-", ".pdf");

            driver = createDriver(buildChromeOptions(resolveChromiumPath(), sandboxDisabled()));
            driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT).scriptTimeout(SCRIPT_TIMEOUT);
            ((HasCdp) driver).executeCdpCommand("Emulation.setEmulatedMedia", Map.of("media", "screen"));

            List<LogEntry> performanceEntries = new ArrayList<>();
            driver.get(baseUrl + appPath);
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
            enforceRenderGates(driver, performanceEntries, baseUrl, fdid);

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
            // The grant is consume-once; discard it if the browser never redeemed it.
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
    static ChromeOptions buildChromeOptions(String chromiumBinary, boolean disableSandbox) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new",
                "--disable-dev-shm-usage",
                "--window-size=" + VIEWPORT_WIDTH + "," + VIEWPORT_HEIGHT,
                "--force-device-scale-factor=1",
                "--hide-scrollbars",
                "--proxy-server=" + DEAD_PROXY,
                "--proxy-bypass-list=" + PROXY_BYPASS_LOOPBACK,
                "--disable-background-networking",
                "--disable-extensions",
                "--no-first-run",
                "--no-default-browser-check");
        if (disableSandbox) {
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

    static boolean sandboxDisabled() {
        return !"true".equals(System.getenv(ENV_ENABLE_SANDBOX));
    }

    private ChromeDriver createDriver(ChromeOptions options) throws PDFGenerationException {
        try {
            String chromedriverPath = resolveChromedriverPath();
            if (chromedriverPath != null) {
                File chromedriver = PathValidationUtils.validateConfiguredFile(chromedriverPath, CHROMEDRIVER_PATH_PROPERTY);
                ChromeDriverService service = new ChromeDriverService.Builder()
                        .usingDriverExecutable(chromedriver)
                        .build();
                return new ChromeDriver(service, options);
            }
            // No pinned chromedriver: Selenium Manager resolves one matching the browser. Intended
            // for dev/CI; production deployments should pin eform_pdf_browser_chromedriver_path.
            return new ChromeDriver(options);
        } catch (RuntimeException e) {
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
        List<CaptureRegion> regions = new ArrayList<>();
        for (Object rawRegion : rawList) {
            if (!(rawRegion instanceof Map<?, ?> rawMap)) {
                throw new PDFGenerationException("Browser rendering returned an unexpected page-region entry.");
            }
            double x = regionValue(rawMap, "x");
            double y = regionValue(rawMap, "y");
            double width = regionValue(rawMap, "width");
            double height = regionValue(rawMap, "height");
            if (width > 0 && height > 0) {
                regions.add(new CaptureRegion(x, y, width, height));
            }
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

    private void enforceRenderGates(ChromeDriver driver, List<LogEntry> performanceEntries, String baseUrl, int fdid)
            throws PDFGenerationException {
        String allowedOrigin = originOf(baseUrl);
        int disallowedRequests = 0;
        Integer mainDocumentStatus = null;

        for (LogEntry entry : performanceEntries) {
            JsonNode message = parsePerformanceMessage(entry.getMessage());
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
            } else if ("Network.responseReceived".equals(method)
                    && "Document".equals(params.path("type").asText(""))
                    && params.path("response").path("url").asText("").startsWith(allowedOrigin)) {
                mainDocumentStatus = params.path("response").path("status").asInt();
            }
        }

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

    static boolean isDisallowedRendererRequestUrl(String requestUrl, String allowedOrigin) {
        if (requestUrl == null || requestUrl.isEmpty()
                || requestUrl.startsWith("data:") || requestUrl.startsWith("blob:") || requestUrl.startsWith("about:")) {
            return false;
        }
        String scheme = requestUrl.indexOf(':') > 0 ? requestUrl.substring(0, requestUrl.indexOf(':')) : "";
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            // Non-web schemes the browser may touch internally (chrome-extension:, etc.) are
            // unreachable as exfiltration channels behind the dead proxy; only web URLs matter.
            return false;
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
        return text.replaceAll("(?i)https?://[^\\s'\"<>]+", "[redacted-url]");
    }

    private static void checkDeadline(long deadlineNanos) throws PDFGenerationException {
        if (System.nanoTime() > deadlineNanos) {
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
     * under {@code DOCUMENT_DIR} or {@link PathValidationUtils#isInAllowedTempDirectory(File)}
     * (java.io.tmpdir and the Tomcat work directories). The renderer therefore must keep its
     * output inside those already-whitelisted temp locations; do not add roots (such as
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
            return captures.stream()
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
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
