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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.imageio.ImageIO;

import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.io.MemoryUsageSetting;
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
import org.openqa.selenium.remote.http.ClientConfig;
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
 * loopback to {@link EFormBrowserRenderPageServlet} using a render-scoped token from
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
public class EFormBrowserPdfService {

    private static final Logger logger = MiscUtils.getLogger();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** URI scheme constant reused by the scheme gates and default-port logic (SonarCloud S1192). */
    private static final String SCHEME_HTTPS = "https";

    private static final Duration RENDER_TIMEOUT = Duration.ofSeconds(90);
    // Package-private so the unit test can pin the "client read timeout stays above the in-band
    // timeouts" invariant; production code treats them as private.
    static final Duration PAGE_LOAD_TIMEOUT = Duration.ofSeconds(30);
    static final Duration SCRIPT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration NETWORK_QUIET_WINDOW = Duration.ofMillis(500);
    private static final Duration NETWORK_QUIET_MAX_WAIT = Duration.ofSeconds(10);
    private static final long SETTLE_DELAY_MILLIS = 1500;

    /**
     * Client-side HTTP read timeout for every WebDriver/CDP command sent to chromedriver
     * (screenshots, script execution, quit). Aligned to the render budget — and deliberately 3x
     * the 30s in-band {@link #PAGE_LOAD_TIMEOUT}/{@link #SCRIPT_TIMEOUT} so legitimate slow pages
     * always hit the in-band timeout first — this replaces Selenium's ~180s default, which let a
     * wedged Chromium hold one of the {@link #MAX_CONCURRENT_RENDERS} global render slots for
     * ~6 minutes (blocked command + blocked quit) and starve all rendering.
     */
    static final Duration WEBDRIVER_COMMAND_READ_TIMEOUT = RENDER_TIMEOUT;
    static final Duration WEBDRIVER_CONNECTION_TIMEOUT = Duration.ofSeconds(10);

    /** Bounded well below Tomcat's worker pool so renders can never saturate request threads. */
    private static final int MAX_CONCURRENT_RENDERS = 2;
    private static final Duration RENDER_SLOT_WAIT = Duration.ofSeconds(30);
    private static final Semaphore RENDER_SLOTS = new Semaphore(MAX_CONCURRENT_RENDERS, true);

    /**
     * Filename prefix shared by every renderer artifact — the per-render capture directory and the
     * output PDF. The {@link RenderedEformPdf} guard keys on it (plus a {@code .pdf} suffix) so the
     * AutoCloseable can only ever delete this renderer's own output, and the stale-artifact sweep
     * keys on it too.
     */
    static final String RENDER_ARTIFACT_PREFIX = "eform-browser-render-";

    private static final String BASE_URL_PROPERTY = "eform_pdf_browser_base_url";
    private static final String CHROME_PATH_PROPERTY = "eform_pdf_browser_chromium_path";
    private static final String CHROMEDRIVER_PATH_PROPERTY = "eform_pdf_browser_chromedriver_path";
    private static final String CATALINA_BASE_PROPERTY = "catalina.base";
    private static final String ENV_ALLOW_UNSANDBOXED = "EFORM_RENDER_ALLOW_UNSANDBOXED";

    /**
     * Dead proxy plus the exact-origin bypass built by {@link #proxyBypassListFor}: only the
     * application's own render origin escapes the dead proxy, so a request to any other host —
     * or any other loopback host/port — is blocked before it is ever sent. This holds ONLY
     * because the bypass list carries {@code <-loopback>}: Chromium's implicit rules otherwise
     * exempt all loopback traffic from proxying entirely. Together with the performance-log
     * gate, egress attempts are stopped at two independent layers: pre-send (proxy) and
     * post-hoc (event replay).
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
    // vs. any real eForm page yet far below a single 20000×20000 (1.6 GB) region. This is now also
    // the effective ceiling on *retained* JVM memory across the whole render: convertCapturesToPdf
    // uses a file-backed PDDocument stream cache, so the Flate-compressed page-image streams that
    // would otherwise accumulate on-heap up to MAX_CAPTURE_TOTAL_PIXELS (times MAX_CONCURRENT_RENDERS
    // concurrent renders) are spilled to a scratch file under the managed render workspace instead.
    private static final double MAX_CAPTURE_REGION_PIXELS = 64_000_000d;
    private static final double MAX_CAPTURE_TOTAL_PIXELS = 300_000_000d;

    // ---------------------------------------------------------------------------------------------
    // Browser-side JS. Each script owns one capture guarantee: STABILIZE_ASYNC_JS waits until
    // fonts and images have settled, PREPARE_CAPTURE_JS applies print-cleanup styling and page
    // flattening, and COMPUTE_REGIONS_JS derives the page capture rectangles from the form's DOM.
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
     * A rendered eForm PDF whose on-disk lifetime the holder owns: {@link #close()} deletes the
     * file (quietly; the 24h stale-output sweep remains the backstop for holders that die before
     * closing). Hold it in try-with-resources when the bytes are consumed in-request; call
     * {@link #path()} and skip close only when ownership genuinely transfers onward (e.g. the fax
     * flow promoting the file into the document store).
     *
     * <p>Guard scope: the compact constructor enforces the renderer output <em>name</em> (the
     * delete-safety invariant for {@link #close()}); content validity is enforced at the render
     * success gate ({@code hasPdfMagicBytes}), and containment under the managed temp root is
     * structural — every production path is created via {@code createSecureTempFile} under
     * {@code resolveRendererTempRoot()}.</p>
     */
    public record RenderedEformPdf(Path path) implements AutoCloseable {
        /**
         * Rejects any path that is not this renderer's own output. {@link #close()} deletes the
         * wrapped file, so constraining the wrapper to a non-null {@code eform-browser-render-*.pdf}
         * filename makes "close() deletes only renderer output" self-enforcing — a stray
         * {@code new RenderedEformPdf(Path.of("/etc/passwd"))} can never turn this AutoCloseable into
         * an arbitrary-file delete.
         *
         * @throws NullPointerException if {@code path} is null
         * @throws IllegalArgumentException if the filename is not a renderer output name
         */
        public RenderedEformPdf {
            Objects.requireNonNull(path, "rendered eForm PDF path must not be null");
            Path fileNamePath = path.getFileName();
            String fileName = fileNamePath == null ? "" : fileNamePath.toString();
            if (!fileName.startsWith(RENDER_ARTIFACT_PREFIX) || !fileName.endsWith(".pdf")) {
                // The filename is a managed temp name, not PHI; naming it aids diagnosis of a
                // mis-wired caller.
                throw new IllegalArgumentException(
                        "RenderedEformPdf must wrap renderer output (" + RENDER_ARTIFACT_PREFIX
                        + "*.pdf); refusing: " + fileName);
            }
        }

        @Override
        public void close() {
            deleteQuietly(path);
        }
    }

    /**
     * Renders a saved eForm by loading the token-authorized local servlet route in headless
     * Chromium, capturing page regions, and assembling those captures into a PDF.
     *
     * <p>Callers MUST have passed an {@code _eform} privilege check (today:
     * {@code EformDataManagerImpl.createEformPDF}) before calling; this service mints the render
     * grant without a privilege check of its own.
     *
     * @param fdid saved eForm data identifier
     * @param providerId provider number the render surface is scoped to; carried inside the
     *        render grant, never on the URL
     * @return handle to a readable temporary PDF; the holder owns cleanup via
     *         {@link RenderedEformPdf#close()}
     * @throws PDFGenerationException when no render slot is available, the browser cannot start,
     *         the page fails its gates (bad status, blocked egress, console errors), the render
     *         times out, or no readable PDF is produced
     */
    public RenderedEformPdf renderSavedEformPdf(int fdid, String providerId) throws PDFGenerationException {
        // Resolve the managed temp root and sweep stale renderer artifacts BEFORE competing for a
        // render slot. The sweep is best-effort housekeeping (a filesystem walk of the shared root);
        // running it while holding one of the scarce MAX_CONCURRENT_RENDERS slots would charge its
        // latency to every render and needlessly narrow throughput under load. A resolve failure here
        // is a real config error and must surface before a slot is taken.
        Path tempRoot = resolveRendererTempRoot();
        sweepStaleRendererRoots(tempRoot);

        SlotAcquisition acquisition = acquireRenderSlot(RENDER_SLOTS, RENDER_SLOT_WAIT);
        if (acquisition == SlotAcquisition.TIMED_OUT) {
            // Load-shed: all render slots were busy for the full wait. Log so a maintainer can see the
            // renderer is saturated (fdid only — no PHI, no render URL/token).
            logger.warn("Browser eForm renderer at capacity ({} concurrent slots); rejecting render for fdid={}",
                    MAX_CONCURRENT_RENDERS, fdid);
            throw new PDFGenerationException("Browser rendering is at capacity; please retry shortly.");
        }
        if (acquisition == SlotAcquisition.INTERRUPTED) {
            // Distinct from capacity: the waiting thread was interrupted (JVM/app shutdown), so no
            // slot was ever taken (nothing to release) and the render never started. Retry advice
            // would be misleading here, so the message deliberately omits it.
            logger.warn("Browser eForm render aborted: waiting thread interrupted (shutdown?): fdid={}", fdid);
            throw new PDFGenerationException("Browser rendering was aborted before it started.");
        }
        try {
            return new RenderedEformPdf(renderWithSlot(fdid, providerId, tempRoot));
        } finally {
            RENDER_SLOTS.release();
        }
    }

    private Path renderWithSlot(int fdid, String providerId, Path tempRoot) throws PDFGenerationException {
        HttpServletRequest currentRequest = currentRequestOrNull();
        String projectHome = CarlosProperties.getInstance().getProperty("project_home", "");
        // Declared before the try (validated to non-null inside it) so the catch-block diagnostics can
        // reference it AND so an invalid base-URL configuration is reported as a checked
        // PDFGenerationException rather than letting an IllegalArgumentException escape this method's
        // throws contract — see the narrow try/catch immediately below.
        String baseUrl = null;
        // Scoped to ONLY this validation call, deliberately OUTSIDE the main try/finally below:
        // an IllegalArgumentException thrown later in the render (e.g.
        // Base64.getDecoder().decode(...) on a corrupt CDP screenshot payload inside
        // captureRegions) must never be misattributed to base-URL configuration. Keeping this
        // catch lexically scoped to only the validateRendererBaseUrl(...) call means any other
        // IllegalArgumentException raised further down falls through to the main try's
        // catch (RuntimeException e) below instead, which reports the honest generic
        // "Browser rendering failed..." diagnosis rather than a false configuration diagnosis.
        try {
            baseUrl = validateRendererBaseUrl(resolveBaseUrl(projectHome, currentRequest));
        } catch (IllegalArgumentException e) {
            String reason = RenderLogRedaction.redactUrls(String.valueOf(e.getMessage()));
            logger.error("Browser eForm renderer rejected its base-URL configuration: {}", reason);
            throw new PDFGenerationException("Browser renderer base URL configuration is invalid: " + reason);
        }
        Path outputDirectory = null;
        Path outputPdfPath = null;
        ChromeDriver driver = null;
        ChromeDriverService driverService = null;
        boolean success = false;
        long startNanos = System.nanoTime();
        long deadlineNanos = startNanos + RENDER_TIMEOUT.toNanos();

        // The grant is render-scoped: the lease is opened as the first resource of this block so its
        // close() invalidates the token at end of render — success, failure, or a throw before the
        // browser ever redeems it — instead of letting it linger in the bounded token cache for its
        // full TTL. try-with-resources guarantees close() runs before the catch/finally below.
        try (var renderLease = EFormRenderTokenService.getInstance().lease(fdid, providerId)) {
            String appPath = validateRendererAppPath(buildAppPath(fdid, renderLease.token()));
            logger.info("Browser eForm renderer starting: fdid={} baseUrl={}", fdid, baseUrl);

            String allowedOrigin = originOf(baseUrl);
            if (allowedOrigin == null) {
                throw new PDFGenerationException("Browser renderer configuration is invalid for the resolved local eForm URL.");
            }
            outputDirectory = createSecureTempDirectory(tempRoot, RENDER_ARTIFACT_PREFIX);
            outputPdfPath = createSecureTempFile(tempRoot, RENDER_ARTIFACT_PREFIX, ".pdf");

            boolean allowUnsandboxed = allowUnsandboxed();
            if (allowUnsandboxed) {
                logger.warn("Browser eForm renderer running WITHOUT Chromium's OS-level sandbox "
                        + "(EFORM_RENDER_ALLOW_UNSANDBOXED=true); OS-level containment is delegated to the container boundary.");
            }
            RendererBrowser browser = createDriver(buildChromeOptions(resolveChromiumPath(), allowUnsandboxed, allowedOrigin));
            driver = browser.driver();
            driverService = browser.service();
            logger.debug("Browser eForm renderer driver started for fdid={} (OS sandbox {})",
                    fdid, allowUnsandboxed ? "disabled" : "enabled");
            driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT).scriptTimeout(SCRIPT_TIMEOUT);
            ((HasCdp) driver).executeCdpCommand("Emulation.setEmulatedMedia", Map.of("media", "screen"));

            List<LogEntry> performanceEntries = new ArrayList<>();
            // Navigate the sessionless render browser to the loopback render page. Do NOT log the full
            // URL: it carries the fdid and the render token; log the origin only.
            logger.debug("Browser eForm renderer navigating to render page: fdid={} origin={}", fdid, allowedOrigin);
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
            logger.debug("Browser eForm renderer computed {} page region(s) to capture: fdid={}", regions.size(), fdid);

            captureRegions(driver, regions, outputDirectory, deadlineNanos);
            drainPerformanceLog(driver, performanceEntries);
            enforceRenderGates(driver, performanceEntries, latchedMainStatus, baseUrl, fdid);

            List<Path> captureFiles = listCaptureFiles(outputDirectory);
            if (captureFiles.isEmpty()) {
                throw new PDFGenerationException("Browser rendering completed without producing any page captures.");
            }
            // outputDirectory is the per-render workspace (created 0700, recursively deleted in the
            // finally below), so routing the PDF assembly stream cache there keeps scratch storage
            // inside the same managed, single-render-scoped lifecycle as the page capture PNGs.
            convertCapturesToPdf(captureFiles, outputPdfPath, outputDirectory);
            // Capture the size once, before declaring success: a second Files.size inside the
            // success log could race an external sweep and turn a completed render into a
            // misreported failure with the finished PDF orphaned.
            long outputPdfBytes = Files.isReadable(outputPdfPath) ? Files.size(outputPdfPath) : 0;
            // Magic-byte check per the direct-response guidance: the fax/eDoc pipeline must never
            // receive a nonempty-but-not-PDF output (a crashed assembly, a stray file).
            if (outputPdfBytes == 0 || !hasPdfMagicBytes(outputPdfPath)) {
                throw new PDFGenerationException("Browser rendering completed without producing a readable eForm PDF.");
            }
            success = true;
            // Success record: fdid, region count, output size and elapsed time give operators an
            // end-to-end render trace. No PHI, no render URL/token — origin/counts/bytes only.
            logger.info("Browser eForm renderer completed: fdid={} pages={} bytes={} elapsedMs={}",
                    fdid, regions.size(), outputPdfBytes,
                    (System.nanoTime() - startNanos) / 1_000_000L);
            return outputPdfPath;
        } catch (PDFGenerationException e) {
            logger.error("Browser eForm renderer failed: fdid={} baseUrl={} reason={}", fdid, baseUrl, RenderLogRedaction.redactUrls(e.getMessage()));
            throw e;
        } catch (IOException e) {
            // Redact: an IOException from temp-file/capture handling can carry a path; keep the type and
            // a redacted message rather than the raw throwable, consistent with the RuntimeException path.
            logger.error("Browser eForm renderer I/O failure: fdid={} baseUrl={} type={} error={}",
                    fdid, baseUrl, e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
            throw new PDFGenerationException("Unable to prepare files for the browser PDF renderer.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PDFGenerationException("Browser rendering was interrupted while generating the eForm PDF.", e);
        } catch (RuntimeException e) {
            // Deliberately no catch (IllegalArgumentException e) here: that would re-widen this
            // handler back over the whole render body and risk mislabeling an unrelated IAE (e.g.
            // Base64.getDecoder().decode(...) on a corrupt CDP screenshot payload inside
            // captureRegions) as a base-URL configuration failure. The only IAE this method
            // converts to a configuration diagnosis is the one from validateRendererBaseUrl(...)
            // above, in its own narrowly-scoped try/catch before this try block even starts. Any
            // other IllegalArgumentException (a RuntimeException subtype) is caught here and gets
            // the honest generic diagnosis below.
            //
            // WebDriver exception messages can embed the loopback render URL (which carries the fdid
            // and render token). Log a redacted message at error, and keep the full exception for
            // troubleshooting at debug only. Deliberately do NOT chain the raw exception as the
            // cause: a downstream handler that logs the throwable (FaxDocumentManagerImpl) would
            // otherwise re-emit the unredacted URL, defeating the renderer's PHI-safe logging.
            // Type + frame-only stack summary at ERROR: a message-less exception (e.g. an NPE) used
            // to log as the undiagnosable "error=null" with the type buried at DEBUG. Frames carry
            // no URLs or PHI, so the summary is safe where the raw throwable is not.
            logger.error("Browser eForm renderer failed: fdid={} baseUrl={} type={} error={} at={}",
                    fdid, baseUrl, e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())), RenderLogRedaction.stackSummary(e));
            throw new PDFGenerationException("Browser rendering failed while generating the eForm PDF.");
        } finally {
            // The render grant was already invalidated by the RenderLease's close() (the lease is the
            // first resource of the try above, so it closes before this finally runs).
            quitQuietly(driver);
            // Belt-and-braces after quit: if the quit command timed out against a wedged Chromium,
            // stopping the caller-owned chromedriver service is what actually tears the processes
            // down before the render slot is released.
            stopServiceQuietly(driverService);
            if (!success) {
                deleteQuietly(outputPdfPath);
            }
            deleteRecursivelyQuietly(outputDirectory);
        }
    }

    /**
     * Real readiness probe for the hard startup gate: launches the pinned headless Chromium exactly
     * as a render would (same binary/sandbox/option resolution), navigates to {@code about:blank},
     * then tears the browser and its chromedriver back down. A real launch is the only honest
     * readiness signal — a mere binary-exists check cannot detect a sandbox that refuses to start
     * or a chromedriver/browser version mismatch, both of which surface only once a session is
     * actually created.
     *
     * <p>{@code about:blank} loads no CARLOS page and carries no fdid, render token, or PHI, so the
     * probe exercises the browser launch path without touching patient data.</p>
     *
     * @throws PDFGenerationException if the renderer cannot launch or fails the navigation probe;
     *         the message carries operator remediation guidance and is redacted (PHI-safe)
     */
    public void verifyRendererReady() throws PDFGenerationException {
        RendererBrowser browser = createDriver(
                buildChromeOptions(resolveChromiumPath(), allowUnsandboxed(), "http://127.0.0.1"));
        ChromeDriver driver = browser.driver();
        ChromeDriverService driverService = browser.service();
        try {
            driver.get("about:blank");
        } catch (RuntimeException e) {
            // A WebDriver failure message can embed a local filesystem path; keep the type plus a
            // redacted message and deliberately do NOT chain the raw throwable (a downstream logger
            // would otherwise re-emit the unredacted text), consistent with the render path's
            // PHI-safe logging.
            throw new PDFGenerationException(
                    "The eForm browser renderer started but failed a basic navigation readiness probe: "
                    + e.getClass().getName() + " " + RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
        } finally {
            quitQuietly(driver);
            // Belt-and-braces after quit (mirrors renderWithSlot): stopping the caller-owned
            // chromedriver service is what actually tears the processes down if quit() timed out.
            stopServiceQuietly(driverService);
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
     * Builds the proxy bypass for the validated loopback origin: exactly the render origin's
     * {@code host:port}, plus the {@code <-loopback>} sentinel that disables Chromium's
     * <em>implicit</em> bypass rules. Without the sentinel Chromium exempts every loopback host
     * and port from the proxy regardless of this list — the previous explicit alias entries were
     * advisory only, and a malicious form could dispatch one-shot requests at
     * {@code http://localhost:<port>} (or any other loopback port) with only the post-hoc
     * network gate noticing after the request had been sent. With it, only the exact origin the
     * render navigates escapes the dead proxy, so those requests are now blocked pre-dispatch.
     * INVARIANT: never remove {@code <-loopback>} as a "simplification" — it is what makes the
     * dead proxy apply to loopback at all.
     */
    // IMPROPER_UNICODE: equalsIgnoreCase here classifies the literal URI scheme ("https") to pick a
    // default port; a case-insensitive protocol-name compare, not an authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of the literal URI scheme to choose a default port; not a security or authorization decision")
    static String proxyBypassListFor(String allowedOrigin) {
        URI uri = URI.create(allowedOrigin);
        int port = uri.getPort();
        if (port == -1) {
            port = SCHEME_HTTPS.equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }
        // URI.getHost() keeps IPv6 brackets ("[::1]"), so the entry is emitted as-is.
        // ORDER IS LOAD-BEARING (verified empirically against Chromium 148): the sentinel must
        // precede the explicit entry — "entry;<-loopback>" subtracts the earlier explicit
        // loopback entry too and dead-proxies the render origin itself, failing every render.
        return "<-loopback>;" + uri.getHost() + ":" + port;
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

    /**
     * A started renderer browser plus the caller-owned chromedriver service behind it.
     * {@code service} is non-null on both the pinned and Selenium-Manager paths; holding it lets
     * the render {@code finally} stop the chromedriver process even when {@code quit()} times out
     * against a wedged Chromium, so a hung render can never orphan a driver process.
     */
    record RendererBrowser(ChromeDriver driver, ChromeDriverService service) {
    }

    /**
     * Per-command HTTP client configuration for the chromedriver connection. Bounds every blocking
     * WebDriver/CDP call at {@link #WEBDRIVER_COMMAND_READ_TIMEOUT} instead of Selenium's ~180s
     * default so a wedged Chromium cannot hold a render slot for minutes past the render budget.
     */
    static ClientConfig rendererClientConfig() {
        return ClientConfig.defaultConfig()
                .readTimeout(WEBDRIVER_COMMAND_READ_TIMEOUT)
                .connectionTimeout(WEBDRIVER_CONNECTION_TIMEOUT);
    }

    private RendererBrowser createDriver(ChromeOptions options) throws PDFGenerationException {
        // Validate the configured chromedriver path (if any) BEFORE the sandbox-guarded start below,
        // so a bad eform_pdf_browser_chromedriver_path surfaces as a config-specific error instead of
        // being caught by the broad catch and misreported as a Chromium sandbox failure that sends
        // operators to change user namespaces.
        File chromedriver = null;
        String chromedriverPath = resolveChromedriverPath();
        if (chromedriverPath != null) {
            try {
                chromedriver = PathValidationUtils.validateConfiguredFile(chromedriverPath, CHROMEDRIVER_PATH_PROPERTY);
            } catch (RuntimeException e) {
                throw new PDFGenerationException(
                        "The configured " + CHROMEDRIVER_PATH_PROPERTY + " does not point to a usable chromedriver "
                        + "executable. Fix the property, or unset it to let Selenium Manager resolve a matching "
                        + "chromedriver.", e);
            }
        }
        // Pre-validate the configured Chromium binary path (if any) with the same up-front,
        // config-specific treatment as the chromedriver path above. A bad eform_pdf_browser_chromium_path
        // must surface as a clear configuration error naming the property — not get swallowed by the
        // broad sandbox-guarded catch below and misreported as a kernel/user-namespace problem that
        // sends operators chasing the wrong fix.
        String chromiumPath = resolveChromiumPath();
        if (chromiumPath != null) {
            try {
                PathValidationUtils.validateConfiguredFile(chromiumPath, CHROME_PATH_PROPERTY);
            } catch (RuntimeException e) {
                throw new PDFGenerationException(
                        "The configured " + CHROME_PATH_PROPERTY + " does not point to a usable Chromium "
                        + "binary. Fix the property, or unset it to let Selenium resolve the browser.", e);
            }
        }
        try {
            if (chromedriver != null) {
                ChromeDriverService service = new ChromeDriverService.Builder()
                        .usingDriverExecutable(chromedriver)
                        .build();
                try {
                    return new RendererBrowser(new ChromeDriver(service, options, rendererClientConfig()), service);
                } catch (RuntimeException e) {
                    // Selenium starts the caller-owned service before session creation but does not
                    // stop it if the ChromeDriver constructor throws (e.g. a sandboxed launch that
                    // cannot start). Stop quietly so a teardown failure can never REPLACE the real
                    // launch failure — the redacted detail log below is the diagnostic record.
                    stopServiceQuietly(service);
                    throw e;
                }
            }
            // No pinned chromedriver: Selenium Manager resolves one when the driver starts (the
            // DriverFinder consults it for an executable-less service). Build a caller-owned
            // service anyway so the render finally can stop the chromedriver process even when
            // quit() times out against a wedged Chromium — without the handle, that leak (a
            // browser holding a rendered PHI page) was invisible below DEBUG and unkillable.
            // Intended for dev/CI; production deployments should still pin
            // eform_pdf_browser_chromedriver_path.
            ChromeDriverService managerResolvedService = new ChromeDriverService.Builder().build();
            try {
                return new RendererBrowser(
                        new ChromeDriver(managerResolvedService, options, rendererClientConfig()), managerResolvedService);
            } catch (RuntimeException e) {
                // Mirror the pinned path: Selenium starts the caller-owned service before session
                // creation but does not stop it if the ChromeDriver constructor throws. Stop
                // quietly so a teardown failure can never REPLACE the real launch failure.
                stopServiceQuietly(managerResolvedService);
                throw e;
            }
        } catch (RuntimeException e) {
            // The redacted detail line below is the ONLY place the underlying startup failure (a
            // version mismatch, a missing shared library, a sandbox that cannot start) surfaces:
            // it is deliberately NOT chained into the PDFGenerationException (a downstream logger
            // that logs the throwable could re-emit a path embedded in its message), matching the
            // render path and verifyRendererReady. Log the type, redacted message, and a
            // frame-only stack summary here so an operator can actually diagnose the failure.
            logger.error("Chromium startup failure detail: type={} error={} at={}",
                    e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())), RenderLogRedaction.stackSummary(e));
            throw chromiumStartupFailure(allowUnsandboxed());
        }
    }

    /**
     * Builds the operator-facing Chromium launch-failure exception. Never carries a cause: the
     * raw WebDriver throwable can embed local filesystem paths in its message, and a downstream
     * handler that logs this exception's chain would re-emit them unredacted — the redacted
     * "Chromium startup failure detail" log line at the catch site is the diagnostic record.
     */
    static PDFGenerationException chromiumStartupFailure(boolean allowUnsandboxed) {
        if (!allowUnsandboxed) {
            // Fail closed: a sandboxed launch that cannot start must not degrade to --no-sandbox
            // on its own. The message admits the non-sandbox causes too (bad/missing browser or
            // driver) so a misconfigured install is not misread as purely a namespace problem.
            return new PDFGenerationException(
                    "Unable to start the sandboxed headless Chromium renderer for eForms. "
                    + "Common causes: missing or incompatible Chromium/chromedriver, or a kernel "
                    + "without unprivileged user namespaces. If the browser installation is correct, "
                    + "enable unprivileged user namespaces and run as a non-root user, or set "
                    + "EFORM_RENDER_ALLOW_UNSANDBOXED=true only when the container itself provides isolation.");
        }
        return new PDFGenerationException("Unable to start the headless Chromium renderer for eForms.");
    }

    private static void quitQuietly(ChromeDriver driver) {
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
        } catch (RuntimeException e) {
            // Never pass the raw WebDriver throwable: its message/stack can embed the loopback render
            // URL (fdid + render token). Log the type and a redacted message only.
            logger.debug("Unable to quit browser eForm renderer driver cleanly: type={} error={}",
                    e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
        }
    }

    /**
     * Stops the caller-owned chromedriver service if {@code quit()} left it running (e.g. the quit
     * command timed out against a wedged Chromium). Killing the service process is what guarantees
     * the browser it launched is torn down before the render slot is released.
     */
    private static void stopServiceQuietly(ChromeDriverService service) {
        if (service == null || !service.isRunning()) {
            return;
        }
        try {
            service.stop();
        } catch (RuntimeException e) {
            // WARN, not DEBUG: this backstop exists precisely because a wedged Chromium can
            // survive quit(), and a leaked chromedriver+browser (holding a rendered PHI page in
            // memory) must be visible at default log levels.
            // Same redaction rule as quitQuietly: never the raw throwable.
            logger.warn("Unable to stop browser eForm renderer chromedriver service cleanly: type={} error={}",
                    e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Stabilization and capture
    // ---------------------------------------------------------------------------------------------

    /**
     * Best-effort network quiescence: poll the performance log until no new events arrive for the
     * quiet window, capped, never failing the render on its own (a quiet-window timeout is
     * ignored). Drained entries are retained for the gates; a drain FAULT does fail the render —
     * see {@link #drainPerformanceLog}.
     */
    private void awaitNetworkQuiet(ChromeDriver driver, List<LogEntry> performanceEntries, long deadlineNanos)
            throws InterruptedException, PDFGenerationException {
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
            throw new PDFGenerationException("Browser rendering failed while stabilizing the eForm page: " + RenderLogRedaction.redactUrls(String.valueOf(settleError)));
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
        logger.debug("Browser eForm renderer captured {} page image(s)", regions.size());
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

    // Package-private for the unit test that pins the console-log-unavailable fail-closed branch.
    void enforceRenderGates(ChromeDriver driver, List<LogEntry> performanceEntries,
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
                if (entry.getLevel().intValue() >= Level.SEVERE.intValue()
                        && !isResourceLoadConsoleEntry(entry.getMessage())
                        && !isPolicyContainmentConsoleEntry(entry.getMessage())) {
                    severeConsoleEntries++;
                }
            }
        } catch (RuntimeException e) {
            // Fail closed: we explicitly enable BROWSER logging in goog:loggingPrefs, so retrieval
            // failing is a WebDriver fault, not a capability gap — and a silently-empty console
            // gate was the only defense against faxing a form whose background never painted.
            // Redacted (no raw WebDriver throwable — it can embed the render URL/token).
            logger.error("Browser console log unavailable for eForm render gate: fdid={} type={} error={}",
                    fdid, e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
            throw new PDFGenerationException(
                    "Browser rendering could not verify the page's console error state.");
        }

        if (scan.parseFailures() > 0) {
            // Same philosophy as the drain-fault gate in drainPerformanceLog: an unparseable
            // network event is egress evidence the replay could not account for, and silently
            // skipping it would let a broken render pass every gate on truncated evidence.
            // Counts only — never the raw entries, which can carry URLs.
            logger.error("Browser eForm renderer could not parse {} network event(s): fdid={}",
                    scan.parseFailures(), fdid);
            throw new PDFGenerationException("Browser rendering could not verify the page's network activity.");
        }
        if (mainDocumentStatus == null || mainDocumentStatus != 200) {
            logger.error("Browser eForm renderer rejected main document: fdid={} status={}", fdid, mainDocumentStatus);
            throw new PDFGenerationException("Browser rendering did not receive a successful eForm page response. status=" + mainDocumentStatus);
        }
        if (disallowedRequests > 0 || severeConsoleEntries > 0 || scan.failedSubresources() > 0) {
            // Counts only — never request URLs or console text, which can carry eForm content.
            logger.error("Browser eForm renderer surfaced page errors: fdid={} disallowedRequests={} severeConsoleEntries={} failedSubresources={}",
                    fdid, disallowedRequests, severeConsoleEntries, scan.failedSubresources());
            throw new PDFGenerationException("Browser rendering surfaced page errors. disallowedRequests="
                    + disallowedRequests + " consoleErrors=" + severeConsoleEntries
                    + " failedSubresources=" + scan.failedSubresources());
        }
    }

    /**
     * True for Chrome console entries reporting a resource load failure ("Failed to load
     * resource: ..."). Resource failures are gated <em>type-aware</em> by the network scan
     * ({@link NetworkGateScan#failedSubresources()} — render-critical types fail the render,
     * speculative loads such as favicons deliberately do not), so counting them in the console
     * gate too made every render fail on the origin-root {@code /favicon.ico} 404 that headless
     * Chrome's own speculative fetch produces. The console gate's remaining job is what the
     * network events cannot see: JavaScript errors on the render surface. Only the message
     * pattern is inspected; console text is still never logged.
     */
    static boolean isResourceLoadConsoleEntry(String message) {
        // Substring match on Chrome's emission (phrase + colon). Residual risk, mirroring the CSP
        // matcher below: a form's own console.error that happens to contain the exact phrase is
        // reclassified as a resource-load entry, suppressing only that form's JS-error signal —
        // resource failures stay gated type-aware by the network scan and egress stays gated by
        // the dead proxy plus event replay, so nothing is bypassed.
        return message != null && message.contains("Failed to load resource:");
    }

    /**
     * True for Chrome console entries reporting that the render surface's own Content-Security-
     * Policy blocked something ("... violates the following Content Security Policy directive
     * ..."). A CSP block is the containment WORKING — the offending content was refused, which is
     * fail-safe by construction — and the normal in-app eForm viewer emits the identical notices
     * for the same stored content while displaying the form fine. Failing the render on them
     * would turn the surface's own defense into a denial of service for legacy forms carrying
     * embedded objects. Actual egress attempts remain gated by the dead proxy and the network
     * event replay regardless of what the console says.
     */
    static boolean isPolicyContainmentConsoleEntry(String message) {
        // Anchored to Chrome's full violation phrase, not a bare "Content Security Policy"
        // substring: a form's own console.error would need to reproduce the exact enforcement
        // wording to be reclassified, which only suppresses that form's own gate signal and
        // bypasses nothing (egress remains gated by the dead proxy and network-event replay).
        return message != null && message.contains("violates the following Content Security Policy directive");
    }

    /**
     * Outcome of replaying Chrome's network events against the allowed loopback origin.
     * {@code parseFailures} counts entries the replay could not parse — evidence the egress gate
     * could not account for, which {@link #enforceRenderGates} fails closed on.
     */
    record NetworkGateScan(int disallowedRequests, Integer mainDocumentStatus, int failedSubresources,
            int parseFailures) {
    }

    /**
     * CDP resource types whose failure visibly breaks the rendered form (a blank background, a
     * missing signature iframe, an unstyled or script-broken page). Speculative loads Chrome makes
     * on its own — favicons and other {@code Other}-typed requests, prefetches, pings — are
     * deliberately excluded so they cannot fail a render whose visible content is intact.
     */
    private static final Set<String> RENDER_CRITICAL_RESOURCE_TYPES =
            Set.of("Document", "Image", "Script", "Stylesheet", "Font", "Media", "XHR", "Fetch");

    /**
     * Replays raw CDP performance-log messages: counts egress attempts to any origin other than
     * the allowed loopback origin, records the status of the first main-frame document response,
     * and counts failed render-critical subresources. Later {@code Document} events belong to
     * iframes (e.g. signature blocks) and are checked as subresources.
     *
     * <p>Egress is observed across all CDP channels a page can open, not just HTTP: WebSocket
     * ({@code Network.webSocketCreated}) and WebTransport ({@code Network.webTransportCreated})
     * arrive on their own events rather than {@code requestWillBeSent}. A render surface never
     * legitimately opens either, so <em>any</em> such event fails the render unconditionally — the
     * URL is not origin-checked, because a same-origin {@code wss:}/{@code https:} WebTransport
     * would otherwise pass the origin allowlist and slip a live bidirectional channel past the
     * gate. This keeps the "reject every non-allowlisted egress attempt" invariant whole even
     * though the dead proxy already blocks the external ones.</p>
     *
     * <p>Subresource failures are observed on both CDP legs: an HTTP error page arrives as
     * {@code Network.responseReceived} with status &ge; 400 (a 404'd form background is otherwise
     * visible only as a SEVERE console entry), while connection-level failures arrive as
     * {@code Network.loadingFailed} ({@code canceled=true} events are benign navigation aborts).
     * Both are restricted to {@link #RENDER_CRITICAL_RESOURCE_TYPES} so Chrome's own speculative
     * requests cannot fail a visually-intact render.</p>
     */
    static NetworkGateScan scanNetworkEvents(List<String> rawEntries, String allowedOrigin) {
        int disallowedRequests = 0;
        Integer mainDocumentStatus = null;
        int failedSubresources = 0;
        int parseFailures = 0;
        for (String rawEntry : rawEntries) {
            JsonNode message = parsePerformanceMessage(rawEntry);
            if (message == null) {
                // Count, never log the raw entry (it can carry URLs): a skipped entry is network
                // evidence the gate did not see, and the gate fails closed on a nonzero count.
                parseFailures++;
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
            } else if ("Network.responseReceived".equals(method)) {
                String resourceType = params.path("type").asText("");
                if (mainDocumentStatus == null
                        && "Document".equals(resourceType)
                        && allowedOrigin != null
                        && allowedOrigin.equals(originOf(params.path("response").path("url").asText("")))) {
                    mainDocumentStatus = params.path("response").path("status").asInt();
                } else if (RENDER_CRITICAL_RESOURCE_TYPES.contains(resourceType)
                        && params.path("response").path("status").asInt() >= 400) {
                    failedSubresources++;
                }
            } else if ("Network.loadingFailed".equals(method)
                    && RENDER_CRITICAL_RESOURCE_TYPES.contains(params.path("type").asText(""))
                    && !params.path("canceled").asBoolean(false)) {
                failedSubresources++;
            }
        }
        return new NetworkGateScan(disallowedRequests, mainDocumentStatus, failedSubresources, parseFailures);
    }

    // Package-private for the unit test that pins the fail-closed behavior.
    int drainPerformanceLog(ChromeDriver driver, List<LogEntry> performanceEntries) throws PDFGenerationException {
        try {
            int before = performanceEntries.size();
            for (LogEntry entry : driver.manage().logs().get(LogType.PERFORMANCE)) {
                performanceEntries.add(entry);
            }
            return performanceEntries.size() - before;
        } catch (RuntimeException e) {
            // Fail closed, mirroring the console gate: the performance log is the ONLY detector
            // for failed render-critical subresources (the console gate delegates resource
            // failures here), so a drain fault silently returning 0 would let a broken render
            // pass every gate on truncated evidence.
            // Redacted (no raw WebDriver throwable — it can embed the render URL/token).
            logger.error("Browser performance log unavailable for eForm render: type={} error={}",
                    e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
            throw new PDFGenerationException("Browser rendering could not verify the page's network activity.");
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
    // IMPROPER_UNICODE: equalsIgnoreCase compares the literal request scheme against "http"/"https"
    // to fail-close non-web schemes; a case-insensitive protocol-name compare, not identity folding.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of the literal request scheme against http/https for the fail-closed scheme gate; not user identity folding")
    static boolean isDisallowedRendererRequestUrl(String requestUrl, String allowedOrigin) {
        if (requestUrl == null || requestUrl.isEmpty()
                || requestUrl.startsWith("data:") || requestUrl.startsWith("blob:") || requestUrl.startsWith("about:")) {
            return false;
        }
        String scheme = requestUrl.indexOf(':') > 0 ? requestUrl.substring(0, requestUrl.indexOf(':')) : "";
        if (!"http".equalsIgnoreCase(scheme) && !SCHEME_HTTPS.equalsIgnoreCase(scheme)) {
            // Any non-web scheme (file:, filesystem:, chrome:, view-source:, ...) is fail-closed:
            // the eForm render surface only ever needs http(s) to the loopback app plus inert
            // data:/blob:/about: resources.
            return true;
        }
        String origin = originOf(requestUrl);
        return origin == null || !origin.equals(allowedOrigin);
    }

    // IMPROPER_UNICODE: toLowerCase(Locale.ROOT) normalizes the URI scheme and host for an
    // origin-equality compare against the pinned loopback base; canonicalizing protocol/host labels,
    // not folding user identity.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "Locale.ROOT lower-casing normalizes the URI scheme/host for loopback origin comparison; not user identity folding")
    static String originOf(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            String scheme = uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            int port = uri.getPort();
            if (port == -1) {
                port = SCHEME_HTTPS.equals(scheme) ? 443 : 80;
            }
            return scheme + "://" + uri.getHost().toLowerCase(java.util.Locale.ROOT) + ":" + port;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void checkDeadline(long deadlineNanos) throws PDFGenerationException {
        // Difference comparison is nanoTime wrap-around safe, unlike a direct `>`.
        if (System.nanoTime() - deadlineNanos > 0) {
            throw new PDFGenerationException("Browser rendering timed out while generating the eForm PDF.");
        }
    }

    /**
     * Outcome of competing for one of the bounded render slots: {@code ACQUIRED} within the wait,
     * {@code TIMED_OUT} with every slot busy for the full wait, or {@code INTERRUPTED} when the
     * waiting thread was interrupted (shutdown) before a slot was taken. Distinguishing the last two
     * lets the caller give correct operator guidance — capacity load-shed (retry) versus an aborted
     * render (no retry) — rather than collapsing both into a single boolean {@code false}.
     */
    enum SlotAcquisition { ACQUIRED, TIMED_OUT, INTERRUPTED }

    static SlotAcquisition acquireRenderSlot(Semaphore slots, Duration wait) {
        try {
            return slots.tryAcquire(wait.toMillis(), TimeUnit.MILLISECONDS)
                    ? SlotAcquisition.ACQUIRED : SlotAcquisition.TIMED_OUT;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SlotAcquisition.INTERRUPTED;
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

    static String buildAppPath(int fdid, EFormRenderTokenService.RenderToken renderToken) {
        // A null token here would silently build a tokenless renderer URL that the render-page
        // servlet then rejects (403) — a confusing failure far from the real cause. The render URL
        // is only ever built from a freshly issued grant, so require it up front.
        Objects.requireNonNull(renderToken, "render token must be issued before building the renderer URL");
        return "/EFormViewForPdfGenerationServlet?fdid=" + fdid
                + "&browserRender=true"
                + "&" + EFormBrowserRenderPageServlet.RENDER_TOKEN_PARAM + "="
                + URLEncoder.encode(renderToken.queryValue(), StandardCharsets.UTF_8);
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

    // IMPROPER_UNICODE: equalsIgnoreCase checks the literal configured base-URL scheme is http/https;
    // a case-insensitive protocol-name compare, not user identity folding.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of the literal configured base-URL scheme against http/https; not user identity folding")
    static String validateRendererBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            throw new IllegalArgumentException("Renderer base URL must be non-empty");
        }

        URI uri = URI.create(rawBaseUrl.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !SCHEME_HTTPS.equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Renderer base URL must use http or https");
        }
        if (uri.getHost() == null || !isLocalRendererHost(uri.getHost())) {
            throw new IllegalArgumentException("Renderer base URL host must resolve to loopback");
        }
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            // Servlet paths are appended verbatim to this base; a query/fragment would swallow
            // them and fail every render far from the misconfiguration. Reject at the source.
            throw new IllegalArgumentException("Renderer base URL must not contain user-info, query, or fragment components");
        }
        return rawBaseUrl.trim().replaceAll("/$", "");
    }

    /**
     * Startup-time format validation of {@code eform_pdf_browser_base_url}. A no-op when the
     * property is unset (request-derived URLs cannot be validated before Tomcat serves). Lets the
     * required-mode startup gate refuse to deploy on a malformed configured base URL — the
     * deployment decision for that gate is fail-at-deploy, never fail-at-first-fax.
     *
     * @throws PDFGenerationException when the configured value fails
     *         {@link #validateRendererBaseUrl}; the message names the property
     */
    void verifyConfiguredBaseUrl() throws PDFGenerationException {
        String configured = CarlosProperties.getInstance().getProperty(BASE_URL_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return;
        }
        try {
            validateRendererBaseUrl(configured);
        } catch (IllegalArgumentException e) {
            throw new PDFGenerationException("The configured " + BASE_URL_PROPERTY + " is invalid: " + e.getMessage());
        }
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
            return buildLocalBaseUrl(deriveLoopbackScheme(request), request.getLocalPort(), request.getContextPath());
        }
        return buildDefaultBaseUrl(projectHome);
    }

    /**
     * Scheme for the renderer's loopback hop. A proxy-rewritten request (RemoteIpValve /
     * X-Forwarded-Proto) reports https while the local connector speaks plaintext; that state is
     * detectable as scheme=https with serverPort != localPort (Tomcat-terminated TLS keeps them
     * equal). Without the downgrade the derived base is https://127.0.0.1:<httpPort>, which fails
     * every render. eform_pdf_browser_base_url overrides this derivation entirely.
     */
    static String deriveLoopbackScheme(HttpServletRequest request) {
        String scheme = request.getScheme();
        if (SCHEME_HTTPS.equalsIgnoreCase(scheme) && request.getServerPort() != request.getLocalPort()) {
            logger.info("Renderer base URL: proxied TLS detected (serverPort={} localPort={}); using http for the loopback hop. Set {} to override.",
                    request.getServerPort(), request.getLocalPort(), BASE_URL_PROPERTY);
            return "http";
        }
        return scheme;
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
                    .sorted(Comparator.comparingInt(EFormBrowserPdfService::capturePageIndex)
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

    static void convertCapturesToPdf(List<Path> captureFiles, Path outputPdfPath, Path scratchDirectory) throws PDFGenerationException {
        // File-backed stream cache: Flate-compressed page-image streams otherwise accumulate
        // on-heap in the PDDocument until save() — up to the full MAX_CAPTURE_TOTAL_PIXELS budget
        // per render, times MAX_CONCURRENT_RENDERS. Spilling to a scratch file under the managed
        // render workspace bounds retained JVM memory to one decoded region regardless of form size.
        try (PDDocument document = new PDDocument(
                MemoryUsageSetting.setupTempFileOnly().setTempDir(scratchDirectory.toFile()).streamCache)) {
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
            logger.debug("Assembled {} eForm capture(s) into a {}-page PDF", captureFiles.size(), document.getNumberOfPages());
        } catch (IOException e) {
            throw new PDFGenerationException("Unable to assemble the browser-rendered eForm captures into a PDF.", e);
        }
    }

    /**
     * True when {@code path} starts with the {@code %PDF} magic bytes. Read failures (including a
     * missing file) return false — at the success gate, "cannot prove it is a PDF" and "is not a
     * PDF" both mean the render failed.
     */
    static boolean hasPdfMagicBytes(Path path) {
        byte[] header = new byte[4];
        try (InputStream in = Files.newInputStream(path)) {
            return in.readNBytes(header, 0, 4) == 4
                    && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F';
        } catch (IOException e) {
            // Fail closed, but say why: a permissions/mount fault on the renderer temp root would
            // otherwise be indistinguishable from a garbage capture and send the operator chasing
            // the Chromium pipeline. Redacted message per this file's convention.
            logger.warn("Could not read rendered PDF header at the success gate: type={} error={}",
                    e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
            return false;
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
        // Reject a pre-seeded symlink at the managed root: a local attacker who wins the race to
        // create the predictable renderer temp root as a symlink could otherwise redirect the
        // per-render child (and its rendered PDF) outside the CARLOS-owned tree. The child itself is
        // already created atomically with a random name and 0700 via createTempDirectory below.
        if (Files.isSymbolicLink(managedRoot)) {
            throw new IOException("Renderer temp root must be a real directory, not a symbolic link: " + managedRoot);
        }
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
                || (SCHEME_HTTPS.equalsIgnoreCase(scheme) && port == 443);
    }

    private static void deleteQuietly(Path outputPath) {
        if (outputPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(outputPath);
        } catch (IOException e) {
            // WARN, not DEBUG: an undeletable rendered PDF is PHI accumulating on disk, and at
            // default log levels an operator must learn the sweep is papering over a real
            // filesystem/permission problem. The path is a managed temp path, not PHI.
            logger.warn("Unable to delete temporary browser-rendered PDF {}", outputPath, e);
        }
    }

    private static void deleteRecursivelyQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        // Per-entry failures stay at DEBUG so one undeletable tree cannot spam WARN per file;
        // the aggregate below surfaces the problem once at WARN.
        int failedEntries = 0;
        try (var stream = Files.walk(directory)) {
            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(entry);
                } catch (IOException e) {
                    failedEntries++;
                    logger.debug("Unable to delete renderer capture entry {}", entry, e);
                }
            }
        } catch (IOException e) {
            logger.warn("Unable to delete temporary browser-rendered capture directory {}", directory, e);
        }
        if (failedEntries > 0) {
            logger.warn("Unable to delete {} entries under renderer capture directory {}; PHI capture images may be accumulating",
                    failedEntries, directory);
        }
    }

    /**
     * Age after which an orphaned renderer capture directory under the shared managed root is swept.
     * Capture dirs are always detached from the caller (deleted in the per-render {@code finally}), so a
     * short window safely reclaims ones left by a JVM/browser killed mid-render.
     */
    private static final Duration STALE_RENDERER_DIR_TTL = Duration.ofHours(1);

    /**
     * Age after which an orphaned renderer output {@code .pdf} is swept. The output is RETURNED to the
     * caller, which owns cleanup, so this window is deliberately far larger than any possible request
     * lifetime (a single render is capped at {@link #RENDER_TIMEOUT} = 90s and callers consume the PDF
     * synchronously within the same request). At 24h the age sweep cannot intersect a still-in-use
     * output — even a long multi-attachment workflow — while still bounding disk use if a
     * caller dies mid-consumption and never cleans up.
     */
    private static final Duration STALE_RENDERER_OUTPUT_TTL = Duration.ofHours(24);

    /**
     * Best-effort sweep of renderer artifacts (both {@code eform-browser-render-*} capture dirs and
     * their {@code .pdf} output files) left under the shared managed root by a JVM or browser that was
     * killed before the per-render {@code finally} cleanup ran, or by a caller that failed before it
     * consumed a returned output. Directories are reclaimed after {@link #STALE_RENDERER_DIR_TTL} and
     * caller-owned output PDFs only after the much longer {@link #STALE_RENDERER_OUTPUT_TTL}, so an
     * in-flight render — and a returned, not-yet-consumed output — is never touched. Never throws — a
     * sweep failure must not fail the render.
     */
    static void sweepStaleRendererRoots(Path managedRoot) {
        if (managedRoot == null || !Files.isDirectory(managedRoot)) {
            return;
        }
        long now = System.currentTimeMillis();
        long dirCutoffMillis = now - STALE_RENDERER_DIR_TTL.toMillis();
        long outputCutoffMillis = now - STALE_RENDERER_OUTPUT_TTL.toMillis();
        // The render's output .pdf sits directly under the managed root with the same prefix and is
        // RETURNED by renderSavedEformPdf for caller-owned cleanup, so it is age-gated on the long
        // output window that no live request can reach (fresh outputs are never swept, an
        // in-use output cannot be reached, and growth stays bounded). Capture dirs are always caller-detached, so a short
        // window reclaims them. Catch unchecked failures too (e.g. DirectoryIteratorException) so a
        // traversal/permission error can never turn this best-effort cleanup into a render prerequisite.
        int reclaimed = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(managedRoot, RENDER_ARTIFACT_PREFIX + "*")) {
            for (Path entry : entries) {
                boolean isDirectory;
                long modifiedMillis;
                try {
                    modifiedMillis = Files.getLastModifiedTime(entry).toMillis();
                    isDirectory = Files.isDirectory(entry);
                } catch (IOException e) {
                    continue; // can't stat it; leave it for a later sweep
                }
                if (isDirectory) {
                    if (modifiedMillis < dirCutoffMillis) {
                        deleteRecursivelyQuietly(entry);
                        reclaimed++;
                    }
                } else if (entry.getFileName().toString().endsWith(".pdf") && modifiedMillis < outputCutoffMillis) {
                    deleteQuietly(entry); // orphaned output reclaimed only long past any request lifetime
                    reclaimed++;
                }
            }
        } catch (IOException | RuntimeException e) {
            // WARN: a sweep that cannot run leaves orphaned PHI captures on disk indefinitely.
            logger.warn("Unable to sweep stale renderer temp roots under {}", managedRoot, e);
        }
        if (reclaimed > 0) {
            logger.debug("Swept {} stale renderer artifact(s) under the managed temp root", reclaimed);
        }
    }

}
