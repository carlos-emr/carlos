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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.Logger;
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
import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

/**
 * Browser-backed eForm PDF renderer driven entirely from the JVM.
 *
 * <p>Selenium launches a pinned headless Chromium (no Node.js runtime anywhere), navigates over
 * loopback to {@link EFormBrowserRenderPageServlet} using a render-scoped token from
 * {@link EFormRenderTokenService}, and prints the stabilized page to a native, text-layer PDF via
 * CDP {@code Page.printToPDF} (sizing each {@code @page} to the authored page geometry) for fax and
 * eDoc workflows.</p>
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
     * (print-to-PDF, script execution, quit). Aligned to the render budget — and deliberately 3x
     * the 30s in-band {@link #PAGE_LOAD_TIMEOUT}/{@link #SCRIPT_TIMEOUT} so legitimate slow pages
     * always hit the in-band timeout first — this replaces Selenium's ~180s default, which let a
     * wedged Chromium hold one of the {@link #MAX_CONCURRENT_RENDERS} global render slots for
     * ~6 minutes (blocked command + blocked quit) and starve all rendering.
     */
    static final Duration WEBDRIVER_COMMAND_READ_TIMEOUT = RENDER_TIMEOUT;
    static final Duration WEBDRIVER_CONNECTION_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Dedicated fast bound on browser-session creation (the "new session" command that launches
     * Chromium). A doomed launch — a missing/incompatible Chromium or chromedriver, or (when the OS
     * sandbox is opted in) a sandbox that cannot start — otherwise blocks on chromedriver's internal
     * browser-start timeout (~60s), and the fax-with-attachments flow renders serially, so each doomed
     * render stacks that wait into the multi-minute "faxing page never loads" the tester reported. A
     * real Chromium launch completes in a few seconds; this budget fails a doomed one fast and frees
     * the render slot. Kept independent of {@link #WEBDRIVER_COMMAND_READ_TIMEOUT} because a legitimate
     * render command (navigation up to {@link #PAGE_LOAD_TIMEOUT}) needs the longer per-command read.
     */
    static final Duration DRIVER_START_TIMEOUT = Duration.ofSeconds(30);

    /** Bounded well below Tomcat's worker pool so renders can never saturate request threads. */
    private static final int MAX_CONCURRENT_RENDERS = 2;
    private static final Duration RENDER_SLOT_WAIT = Duration.ofSeconds(30);
    private static final Semaphore RENDER_SLOTS = new Semaphore(MAX_CONCURRENT_RENDERS, true);

    /**
     * Filename prefix of the renderer's output PDF. The {@link RenderedEformPdf} guard keys on it
     * (plus a {@code .pdf} suffix) so the AutoCloseable can only ever delete this renderer's own
     * output, and the stale-artifact sweep keys on it too. The native print path creates only this
     * PDF file per render; the sweep still recognises same-prefixed <em>directories</em> because a
     * prior (raster) build created a per-render capture directory under this prefix — those are
     * cleaned up as an upgrade-time backstop (see {@link #sweepStaleRendererRoots}).
     */
    static final String RENDER_ARTIFACT_PREFIX = "eform-browser-render-";

    private static final String BASE_URL_PROPERTY = "eform_pdf_browser_base_url";
    private static final String CHROME_PATH_PROPERTY = "eform_pdf_browser_chromium_path";
    private static final String CHROMEDRIVER_PATH_PROPERTY = "eform_pdf_browser_chromedriver_path";
    private static final String CATALINA_BASE_PROPERTY = "catalina.base";
    /**
     * When {@code true}, restores the original fail-closed posture in which any observed off-origin
     * HTTP request, failed render-critical subresource, or severe page-script console error aborts
     * the whole render. Default ({@code false}) treats those three as <em>advisory</em>: they are
     * logged but the render still produces a PDF of what painted. The legacy eForm corpus routinely
     * references off-origin assets (fonts/CDN libs/images), 404s optional helper scripts
     * ({@code faxControl.js}, {@code onBodyLoad_*.js}, {@code jSignature.min.js}), and emits benign
     * JavaScript errors — none of which blank the form, and all of which the in-app eForm viewer
     * already tolerates while displaying the same stored content. Physical egress containment is
     * unaffected by this switch: the dead proxy still blocks every off-origin HTTP request, the
     * WebSocket/WebTransport gate and the same-origin main-document requirement stay hard-fail
     * regardless, the filesystem stays locked, and the render token stays single-use and fdid-bound.
     */
    private static final String STRICT_NETWORK_GATE_PROPERTY = "eform_pdf_browser_strict_network_gate";
    private static final String ENV_REQUIRE_SANDBOX = "EFORM_RENDER_SANDBOX";
    // Log the "running unsandboxed" notice once per JVM rather than on every render, since unsandboxed
    // is now the default and a per-render WARN would be noise.
    private static final java.util.concurrent.atomic.AtomicBoolean UNSANDBOXED_NOTICE_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

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

    private static final int VIEWPORT_WIDTH = 1800;
    private static final int VIEWPORT_HEIGHT = 3200;

    // DOM-controlled page-geometry caps (fail-closed): a clinic-authored form cannot drive an
    // unbounded number of CSS @page sizes, or a pathological single-page dimension, into Chromium's
    // native print pipeline. Generous vs. any real multi-page eForm. (Native Page.printToPDF returns
    // a compressed PDF straight from Chromium, so unlike the former raster path there is no decoded
    // per-page image retained on the JVM heap — these caps bound the injected @page CSS, not memory.)
    private static final int MAX_PAGE_COUNT = 200;
    private static final double MAX_PAGE_DIMENSION = 20_000;

    // ---------------------------------------------------------------------------------------------
    // Browser-side JS. Each script owns one print guarantee: STABILIZE_ASYNC_JS waits until fonts
    // and images have settled, PREPARE_PRINT_JS injects the baseline print stylesheet (zero page
    // margin, exact color/background reproduction, non-print chrome hidden), COMPUTE_PAGE_GEOMETRY_JS
    // measures each authored page div's content box so the JVM can size the CSS @page boxes, and
    // INJECT_PAGE_SIZE_CSS_JS applies those computed @page sizes. Chromium's native Page.printToPDF
    // then emits a real text-layer PDF (the former raster path screenshotted each region and glued
    // the PNGs with PDFBox, which produced an image-only, unsearchable document).
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

    /**
     * Baseline print stylesheet injected before printing. This is a <em>safety net</em>: a
     * well-authored eForm already carries its own {@code @media print} rules (the corpus fixtures
     * toggle {@code .DoNotPrint}/{@code .PrintOnly} themselves), so this only guarantees zero page
     * margin, exact color/background reproduction (the page-background <img> elements are content
     * and print regardless; {@code print-color-adjust} matters only for CSS {@code background-*}
     * decorations), and that the non-print viewer chrome is hidden for forms that lack their own
     * print CSS. It deliberately does NOT set {@code width: max-content} or {@code overflow: visible}
     * (those were raster screenshot hacks); native print lays the form out at its natural width.
     */
    static final String PREPARE_PRINT_JS =
            "const existingCleanupStyle = document.getElementById('eform-browser-pdf-render-cleanup');\n"
            + "if (!existingCleanupStyle) {\n"
            + "  const cleanupStyle = document.createElement('style');\n"
            + "  cleanupStyle.id = 'eform-browser-pdf-render-cleanup';\n"
            + "  cleanupStyle.textContent = `\n"
            + "    @page {\n"
            + "      margin: 0;\n"
            + "    }\n"
            + "    * {\n"
            + "      -webkit-print-color-adjust: exact !important;\n"
            + "      print-color-adjust: exact !important;\n"
            + "    }\n"
            + "    .DoNotPrint,\n"
            + "    #BottomButtons,\n"
            + "    #BaseSelect,\n"
            + "    #SupplementalInfo,\n"
            + "    #labDetail {\n"
            + "      display: none !important;\n"
            + "      visibility: hidden !important;\n"
            + "    }\n"
            + "    .carlos-render-nonpage {\n"
            + "      display: none !important;\n"
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
            + "}\n"
            + "const html = document.documentElement;\n"
            + "if (html) {\n"
            + "  html.style.margin = '0';\n"
            + "  html.style.padding = '0';\n"
            + "  html.style.background = 'white';\n"
            + "}";

    /**
     * Page-geometry measurement and page-content isolation. For each authored {@code pageN} div this
     * returns the LARGER of the div's own border box and its visible-descendant union, per dimension,
     * tagged with the div id — the printed page must hold both the div's full flow extent (or the div
     * spills a mostly-blank page after it) and any content overflowing the div (or that content is
     * clipped). The union alone under-measures a div taller than its contents; the border box alone
     * under-measures a degenerate div (e.g. a placeholder background) its content overflows.
     *
     * <p>When page divs exist, this also marks every <em>in-flow</em> {@code body} child that neither
     * is nor contains a page div with the {@code carlos-render-nonpage} class (hidden by the baseline
     * print stylesheet): corpus forms carry interstitial/trailing flow content that the legacy region
     * capture never photographed but that native print would paginate into stray extra pages — and
     * interstitial in-flow content structurally CANNOT stay in flow, because it shifts every
     * subsequent authored page off its page boundary (the checkbox-misalignment bug). Invisible
     * layout junk (spacer divs, empty paragraphs) is excluded silently; SUBSTANTIVE content (real
     * text or visual elements, e.g. a license notice) is counted and measured so the render logs an
     * operator WARN that authored content was excluded from the printed PDF (the on-screen eForm
     * still shows it). Absolutely/fixed-positioned siblings are left visible — they are out of flow
     * (cost no pagination space), and some corpus forms overlay inputs onto pages from outside the
     * page divs.</p>
     *
     * <p>Returns {@code {pages: [{id,width,height}, ...], excludedCount, excludedHeight}};
     * {@code pages} is empty for a free-flow form (e.g. the Rich Text Letter) that authored no
     * {@code pageN} divs (no marking happens in that case — the whole document prints).</p>
     */
    static final String COMPUTE_PAGE_GEOMETRY_JS =
            "const rectOf = (el) => {\n"
            + "  const r = el.getBoundingClientRect();\n"
            + "  return {\n"
            + "    left: r.left + window.scrollX,\n"
            + "    top: r.top + window.scrollY,\n"
            + "    right: r.right + window.scrollX,\n"
            + "    bottom: r.bottom + window.scrollY,\n"
            + "    width: r.width,\n"
            + "    height: r.height,\n"
            + "  };\n"
            + "};\n"
            + "const isVisible = (el) => {\n"
            + "  const style = window.getComputedStyle(el);\n"
            + "  return style.display !== 'none' && style.visibility !== 'hidden' && style.position !== 'fixed';\n"
            + "};\n"
            + "const contentBox = (pageNode) => {\n"
            + "  let left = Number.POSITIVE_INFINITY;\n"
            + "  let top = Number.POSITIVE_INFINITY;\n"
            + "  let right = 0;\n"
            + "  let bottom = 0;\n"
            + "  for (const el of pageNode.querySelectorAll('*')) {\n"
            + "    if (!isVisible(el)) {\n"
            + "      continue;\n"
            + "    }\n"
            + "    const rect = rectOf(el);\n"
            + "    if (rect.width <= 0 || rect.height <= 0) {\n"
            + "      continue;\n"
            + "    }\n"
            + "    left = Math.min(left, rect.left);\n"
            + "    top = Math.min(top, rect.top);\n"
            + "    right = Math.max(right, rect.right);\n"
            + "    bottom = Math.max(bottom, rect.bottom);\n"
            + "  }\n"
            + "  if (!Number.isFinite(left) || right <= left || bottom <= top) {\n"
            + "    return { width: 0, height: 0 };\n"
            + "  }\n"
            + "  return { width: right - left, height: bottom - top };\n"
            + "};\n"
            + "const pageNodes = Array.from(document.body ? document.body.querySelectorAll('*') : [])\n"
            + "  .filter((el) => /^page\\d+$/i.test(el.id));\n"
            + "let excludedCount = 0;\n"
            + "let excludedHeight = 0;\n"
            + "if (pageNodes.length > 0 && document.body) {\n"
            + "  for (const child of Array.from(document.body.children)) {\n"
            + "    if (child.tagName === 'SCRIPT' || child.tagName === 'STYLE') {\n"
            + "      continue;\n"
            + "    }\n"
            + "    const isOrHasPage = pageNodes.some((pageNode) => child === pageNode || child.contains(pageNode));\n"
            + "    if (isOrHasPage) {\n"
            + "      continue;\n"
            + "    }\n"
            + "    const position = window.getComputedStyle(child).position;\n"
            + "    if (position !== 'absolute' && position !== 'fixed') {\n"
            + "      child.classList.add('carlos-render-nonpage');\n"
            // Substantive = visible, taller than a spacer, and carrying real text or a visual
            // element. Invisible layout junk (whitespace divs, empty paragraphs, <br> runs) is
            // excluded silently; substantive authored content is counted so the JVM can WARN that
            // it was excluded from the printed PDF (legacy region-capture parity).
            + "      const rect = child.getBoundingClientRect();\n"
            + "      const substantive = isVisible(child) && rect.height > 4 && (\n"
            + "        (child.textContent || '').trim().length > 0\n"
            + "        || child.querySelector('img, canvas, svg, video, input, textarea, select') !== null);\n"
            + "      if (substantive) {\n"
            + "        excludedCount += 1;\n"
            + "        excludedHeight += rect.height;\n"
            + "      }\n"
            + "    }\n"
            + "  }\n"
            + "}\n"
            + "return {\n"
            + "  pages: pageNodes.map((pageNode) => {\n"
            + "    const own = rectOf(pageNode);\n"
            + "    const box = contentBox(pageNode);\n"
            + "    return {\n"
            // Width hugs the CONTENT union (the background image / field extent), exactly as the
            // legacy region capture did: a plain block page div stretches to the full viewport
            // width, and printing that stretched box would emit pages with a giant blank right
            // margin. Height instead takes the LARGER of the div's flow extent and its content:
            // vertical under-measurement is what spills blank pages or clips fields.
            + "      id: pageNode.id,\n"
            + "      width: box.width > 0 ? box.width : own.width,\n"
            + "      height: Math.max(own.height, box.height),\n"
            + "    };\n"
            + "  }),\n"
            + "  excludedCount: excludedCount,\n"
            + "  excludedHeight: excludedHeight,\n"
            + "};";

    /**
     * Applies the JVM-computed {@code @page} sizing CSS (argument 0) into a dedicated style element,
     * so Chromium's {@code Page.printToPDF} with {@code preferCSSPageSize:true} sizes each printed
     * page to its authored page div. Kept as its own element (never merged into the baseline cleanup
     * style) so the sizing is idempotent and the two concerns stay independently pinned by tests.
     */
    static final String INJECT_PAGE_SIZE_CSS_JS =
            "const css = arguments[0];\n"
            + "let style = document.getElementById('eform-browser-pdf-page-size');\n"
            + "if (!style) {\n"
            + "  style = document.createElement('style');\n"
            + "  style.id = 'eform-browser-pdf-page-size';\n"
            + "  document.head.appendChild(style);\n"
            + "}\n"
            + "style.textContent = css;";

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
     * Chromium and printing the stabilized page to a native, text-layer PDF via CDP
     * {@code Page.printToPDF} (with each {@code @page} sized to the authored page geometry).
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
        return renderSavedEformPdf(fdid, providerId, false);
    }

    /**
     * Overload that optionally accepts a visually-incomplete render. When {@code allowMissingContent}
     * is {@code true}, a failure of the eForm's own same-origin (CARLOS) visual assets — a signature
     * block, form image, or stylesheet — no longer fails the render; instead it is logged and the
     * captured (incomplete) PDF is produced. This backs the "render anyway" clinician choice after the
     * default render has reported {@link EformContentUnavailableException}. It never relaxes the
     * always-hard gates: a main document that never loaded and an attempted live egress channel still
     * fail closed regardless of this flag.
     *
     * @param fdid saved eForm data identifier
     * @param providerId provider number the render surface is scoped to
     * @param allowMissingContent {@code true} to accept an incomplete render past missing same-origin
     *        visual assets; {@code false} (default) to fail closed with
     *        {@link EformContentUnavailableException}
     * @return handle to a readable temporary PDF
     * @throws EformContentUnavailableException when {@code allowMissingContent} is {@code false} and
     *         the eForm's own visual content failed to load (user-recoverable)
     * @throws PDFGenerationException for every other render failure
     */
    public RenderedEformPdf renderSavedEformPdf(int fdid, String providerId, boolean allowMissingContent) throws PDFGenerationException {
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
            return new RenderedEformPdf(renderWithSlot(fdid, providerId, tempRoot, allowMissingContent));
        } finally {
            RENDER_SLOTS.release();
        }
    }

    private Path renderWithSlot(int fdid, String providerId, Path tempRoot, boolean allowMissingContent) throws PDFGenerationException {
        HttpServletRequest currentRequest = currentRequestOrNull();
        String projectHome = CarlosProperties.getInstance().getProperty("project_home", "");
        // Declared before the try (validated to non-null inside it) so the catch-block diagnostics can
        // reference it AND so an invalid base-URL configuration is reported as a checked
        // PDFGenerationException rather than letting an IllegalArgumentException escape this method's
        // throws contract — see the narrow try/catch immediately below.
        String baseUrl = null;
        // Scoped to ONLY this validation call, deliberately OUTSIDE the main try/finally below:
        // an IllegalArgumentException thrown later in the render (e.g.
        // Base64.getDecoder().decode(...) on a corrupt Page.printToPDF payload inside
        // printToPdf) must never be misattributed to base-URL configuration. Keeping this
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
            outputPdfPath = createSecureTempFile(tempRoot, RENDER_ARTIFACT_PREFIX, ".pdf");

            boolean unsandboxed = !sandboxEnabled();
            if (unsandboxed && UNSANDBOXED_NOTICE_LOGGED.compareAndSet(false, true)) {
                logger.warn("Browser eForm renderer running WITHOUT Chromium's OS-level sandbox (default; "
                        + "set EFORM_RENDER_SANDBOX=true on a non-root deployment with unprivileged user "
                        + "namespaces to enable it). OS-level containment is delegated to the container boundary.");
            }
            RendererBrowser browser = createDriver(buildChromeOptions(resolveChromiumPath(), unsandboxed, allowedOrigin));
            driver = browser.driver();
            driverService = browser.service();
            logger.debug("Browser eForm renderer driver started for fdid={} (OS sandbox {})",
                    fdid, unsandboxed ? "disabled" : "enabled");
            driver.manage().timeouts().pageLoadTimeout(PAGE_LOAD_TIMEOUT).scriptTimeout(SCRIPT_TIMEOUT);
            // Emulate PRINT media (not screen): the page then settles and is measured in the exact
            // layout Page.printToPDF will emit, and each form's own {@code @media print} rules (e.g.
            // the corpus fixture's PrintOnly/DoNotPrint toggles) take effect for the captured PDF.
            ((HasCdp) driver).executeCdpCommand("Emulation.setEmulatedMedia", Map.of("media", "print"));

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
            js.executeScript(PREPARE_PRINT_JS);
            // Measure each authored page div's content box, then size the CSS @page boxes to match so
            // native print reproduces the legacy per-page geometry. An empty list is valid: a free-flow
            // form (the Rich Text Letter) authored no pageN divs, so we inject no @page size and let the
            // form's own @page rules or Chromium's default paper drive natural pagination.
            PageGeometry geometry = readPageGeometry(js.executeScript(COMPUTE_PAGE_GEOMETRY_JS));
            List<PageSize> pageSizes = geometry.pages();
            if (!pageSizes.isEmpty()) {
                js.executeScript(INJECT_PAGE_SIZE_CSS_JS, buildPageSizeCss(pageSizes));
            }
            if (geometry.excludedCount() > 0) {
                // Authored in-flow content outside the pageN divs was excluded from the printed PDF
                // (legacy region-capture parity — interstitial flow content would shift every later
                // authored page off its boundary). Surface it: the form author intended that content,
                // and an operator/form designer must be able to see WHY it is absent from the PDF.
                // Counts and extent only — never element text, which can carry eForm content.
                logger.warn("Browser eForm renderer excluded {} substantive in-flow element(s) (~{}px tall) "
                        + "outside the authored page divs from the printed PDF (legacy region-capture parity; "
                        + "the on-screen eForm still shows them): fdid={}",
                        geometry.excludedCount(), Math.round(geometry.excludedHeight()), fdid);
            }
            logger.debug("Browser eForm renderer measured {} authored page size(s): fdid={}", pageSizes.size(), fdid);

            printToPdf(driver, outputPdfPath, deadlineNanos);
            drainPerformanceLog(driver, performanceEntries);
            enforceRenderGates(driver, performanceEntries, latchedMainStatus, baseUrl, fdid, allowMissingContent);

            // Capture the size once, before declaring success: a second Files.size inside the
            // success log could race an external sweep and turn a completed render into a
            // misreported failure with the finished PDF orphaned.
            long outputPdfBytes = Files.isReadable(outputPdfPath) ? Files.size(outputPdfPath) : 0;
            // Magic-byte check per the direct-response guidance: the fax/eDoc pipeline must never
            // receive a nonempty-but-not-PDF output (a crashed print, a stray file).
            if (outputPdfBytes == 0 || !hasPdfMagicBytes(outputPdfPath)) {
                throw new PDFGenerationException("Browser rendering completed without producing a readable eForm PDF.");
            }
            success = true;
            // Success record: fdid, page count, output size and elapsed time give operators an
            // end-to-end render trace. No PHI, no render URL/token — origin/counts/bytes only.
            logger.info("Browser eForm renderer completed: fdid={} pages={} bytes={} elapsedMs={}",
                    fdid, pageSizes.size(), outputPdfBytes,
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
            // Base64.getDecoder().decode(...) on a corrupt Page.printToPDF payload inside
            // printToPdf) as a base-URL configuration failure. The only IAE this method
            // converts to a configuration diagnosis is the one from validateRendererBaseUrl(...)
            // above, in its own narrowly-scoped try/catch before this try block even starts. Any
            // other IllegalArgumentException (a RuntimeException subtype) is caught here and gets
            // the honest generic diagnosis below.
            //
            // WebDriver exception messages can embed the loopback render URL (which carries the fdid
            // and render token). Deliberately do NOT chain the raw exception as the cause: a
            // downstream handler that logs the throwable (FaxDocumentManagerImpl) would otherwise
            // re-emit the unredacted URL, defeating the renderer's PHI-safe logging. Instead we log,
            // here and only here, a fully-redacted picture: the type, the URL/path-redacted message,
            // a type+frame-only stack summary (a message-less exception such as an NPE used to log as
            // the undiagnosable "error=null"), and the redacted caused-by chain so a wrapped Selenium
            // root cause (WebDriverException around a TimeoutException/ConnectException) is not lost.
            logger.error("Browser eForm renderer failed: fdid={} baseUrl={} type={} error={} at={} causedBy={}",
                    fdid, baseUrl, e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())),
                    RenderLogRedaction.stackSummary(e), RenderLogRedaction.causeChain(e));
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
                buildChromeOptions(resolveChromiumPath(), !sandboxEnabled(), "http://127.0.0.1"));
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
    static ChromeOptions buildChromeOptions(String chromiumBinary, boolean unsandboxed, String allowedOrigin) {
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
        if (unsandboxed) {
            // Default posture: OS-level containment is delegated to the container boundary. The operator
            // can restore Chromium's OS sandbox with EFORM_RENDER_SANDBOX=true — see sandboxEnabled().
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
     * Whether the operator has opted into Chromium's OS-level sandbox with
     * {@code EFORM_RENDER_SANDBOX=true}.
     *
     * <p>Default is <strong>unsandboxed</strong> ({@code --no-sandbox}) so the renderer starts out of
     * the box on the common deployment shape (Tomcat as root / a container without unprivileged user
     * namespaces), where Chromium's sandbox cannot initialize and a sandboxed launch would otherwise
     * fail every render. In that default posture OS-level containment is delegated to the container
     * boundary; all the <em>other</em> renderer controls (loopback-only egress via the dead proxy,
     * {@code --disable-file-system}, WebRTC UDP lockdown, DevTools-over-pipe, and the sessionless
     * render token) remain active regardless.</p>
     *
     * <p>Hardened deployments that run the renderer as a non-root user with unprivileged user
     * namespaces enabled should set {@code EFORM_RENDER_SANDBOX=true} to keep the OS sandbox; when
     * set, a sandboxed launch that cannot start fails closed (see {@link #createDriver}) rather than
     * silently degrading to {@code --no-sandbox}.</p>
     */
    static boolean sandboxEnabled() {
        return "true".equals(System.getenv(ENV_REQUIRE_SANDBOX));
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
                return new RendererBrowser(startSessionWithinBudget(service, options), service);
            }
            // No pinned chromedriver: Selenium Manager resolves one when the driver starts (the
            // DriverFinder consults it for an executable-less service). Build a caller-owned
            // service anyway so the render finally can stop the chromedriver process even when
            // quit() times out against a wedged Chromium — without the handle, that leak (a
            // browser holding a rendered PHI page) was invisible below DEBUG and unkillable.
            // Intended for dev/CI; production deployments should still pin
            // eform_pdf_browser_chromedriver_path.
            ChromeDriverService managerResolvedService = new ChromeDriverService.Builder().build();
            return new RendererBrowser(startSessionWithinBudget(managerResolvedService, options), managerResolvedService);
        } catch (RuntimeException e) {
            // The redacted detail line below is the ONLY place the underlying startup failure (a
            // version mismatch, a missing shared library, a sandbox that cannot start) surfaces:
            // it is deliberately NOT chained into the PDFGenerationException (a downstream logger
            // that logs the throwable could re-emit a path embedded in its message), matching the
            // render path and verifyRendererReady. Log the type, redacted message, and a
            // frame-only stack summary here so an operator can actually diagnose the failure.
            logger.error("Chromium startup failure detail: type={} error={} at={}",
                    e.getClass().getName(), RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())), RenderLogRedaction.stackSummary(e));
            throw chromiumStartupFailure(!sandboxEnabled());
        }
    }

    /**
     * Creates the Chromium session on a bounded background thread so a doomed launch fails within
     * {@link #DRIVER_START_TIMEOUT} instead of blocking on chromedriver's internal ~60s browser-start
     * timeout. On timeout the pending session is cancelled and the caller-owned {@code service} is
     * stopped — killing chromedriver, which unblocks the doomed constructor so it cannot leak a
     * browser process. The service is likewise stopped on a synchronous launch failure, so the caller
     * never has to (mirrors the previous inline cleanup). The completed {@link ChromeDriver} is handed
     * back to the render thread through {@link Future#get}, which establishes the needed happens-before.
     */
    private ChromeDriver startSessionWithinBudget(ChromeDriverService service, ChromeOptions options) {
        // The executor is closed in the finally below via shutdownNow(). shutdownNow() is deliberate,
        // NOT try-with-resources close(): close() awaits task termination, which would re-block the
        // request thread on a wedged ChromeDriver constructor — the exact hang this watchdog exists to
        // bound. The worker is a daemon, so a still-running cancelled task never keeps the JVM alive.
        ExecutorService starter = Executors.newSingleThreadExecutor(runnable -> { // NOSONAR java:S2095 - closed via shutdownNow() in finally; try-with-resources close() would block the watchdog
            Thread thread = new Thread(runnable, "eform-render-driver-start");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<ChromeDriver> pending = starter.submit(
                    () -> new ChromeDriver(service, options, rendererClientConfig()));
            try {
                return pending.get(DRIVER_START_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeout) {
                pending.cancel(true);
                stopServiceQuietly(service);
                throw new IllegalStateException(
                        "Chromium session creation exceeded the " + DRIVER_START_TIMEOUT.toSeconds()
                        + "s startup budget", timeout);
            } catch (ExecutionException failure) {
                stopServiceQuietly(service);
                Throwable cause = failure.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException("Chromium session creation failed", cause);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                pending.cancel(true);
                stopServiceQuietly(service);
                throw new IllegalStateException("Interrupted while starting the Chromium renderer", interrupted);
            }
        } finally {
            starter.shutdownNow();
        }
    }

    /**
     * Builds the operator-facing Chromium launch-failure exception. Never carries a cause: the
     * raw WebDriver throwable can embed local filesystem paths in its message, and a downstream
     * handler that logs this exception's chain would re-emit them unredacted — the redacted
     * "Chromium startup failure detail" log line at the catch site is the diagnostic record.
     */
    static PDFGenerationException chromiumStartupFailure(boolean unsandboxed) {
        if (!unsandboxed) {
            // The operator opted into Chromium's OS sandbox (EFORM_RENDER_SANDBOX=true) but it could not
            // start; fail closed rather than silently degrading to --no-sandbox. The message admits the
            // non-sandbox causes too (bad/missing browser or driver) so a misconfigured install is not
            // misread as purely a namespace problem.
            return new PDFGenerationException(
                    "Unable to start the sandboxed headless Chromium renderer for eForms. "
                    + "Common causes: missing or incompatible Chromium/chromedriver, or a kernel "
                    + "without unprivileged user namespaces. If the browser installation is correct, "
                    + "enable unprivileged user namespaces and run as a non-root user, or unset "
                    + "EFORM_RENDER_SANDBOX to run with --no-sandbox where the container provides isolation.");
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
            // settleError is page-controlled: a hostile eForm can throw an Error whose message carries
            // arbitrary text — potentially PHI read from the form's own rendered fields — and
            // redactUrls() only strips URLs/paths, not free text. Never propagate or log its content;
            // use a fixed message (which then flows to callers/UI/logs safely) and record only that a
            // stabilization error occurred.
            logger.warn("eForm page stabilization reported an error (content suppressed as potential PHI)");
            throw new PDFGenerationException("Browser rendering failed while stabilizing the eForm page.");
        }
        checkDeadline(deadlineNanos);
    }

    /**
     * Emits the fully-settled render surface as a native, text-layer PDF via Chromium's
     * {@code Page.printToPDF} and writes it to {@code outputPdfPath}. All margins are zeroed and
     * {@code preferCSSPageSize} is set so the injected {@code @page} sizes (see
     * {@link #buildPageSizeCss}) — or the form's own {@code @page} rules — drive geometry, and
     * {@code scale} is pinned to 1 so Chromium never rescales the form to fit a default paper.
     * {@code printBackground} is on so the page-background {@code <img>} content and any CSS
     * backgrounds print. {@code ReturnAsBase64} hands the PDF back inline in the command result (the
     * same transport as the former screenshot path), so there is no CDP stream to read back.
     */
    private void printToPdf(ChromeDriver driver, Path outputPdfPath, long deadlineNanos)
            throws IOException, PDFGenerationException {
        checkDeadline(deadlineNanos);
        Map<String, Object> result = ((HasCdp) driver).executeCdpCommand("Page.printToPDF", Map.of(
                "preferCSSPageSize", Boolean.TRUE,
                "printBackground", Boolean.TRUE,
                "scale", 1.0d,
                "marginTop", 0.0d,
                "marginBottom", 0.0d,
                "marginLeft", 0.0d,
                "marginRight", 0.0d,
                "transferMode", "ReturnAsBase64"));
        Object data = result.get("data");
        if (!(data instanceof String encoded) || encoded.isEmpty()) {
            throw new PDFGenerationException("Browser rendering returned an empty PDF for the eForm.");
        }
        Files.write(outputPdfPath, Base64.getDecoder().decode(encoded));
        logger.debug("Browser eForm renderer printed the eForm to a native PDF");
    }

    /**
     * Validates the full result of {@link #COMPUTE_PAGE_GEOMETRY_JS}: the bounded page sizes plus the
     * excluded-content diagnostics. The exclusion counters are advisory operator telemetry, so they
     * are CLAMPED rather than fail-closed: a malformed/non-finite/negative value degrades to zero
     * (no WARN) instead of aborting a render whose page geometry is perfectly valid.
     */
    static PageGeometry readPageGeometry(Object rawGeometry) throws PDFGenerationException {
        if (!(rawGeometry instanceof Map<?, ?> rawMap)) {
            throw new PDFGenerationException("Browser rendering returned an unexpected page-geometry result.");
        }
        List<PageSize> pages = readPageSizes(rawMap.get("pages"));
        int excludedCount = 0;
        double excludedHeight = 0;
        if (rawMap.get("excludedCount") instanceof Number count && rawMap.get("excludedHeight") instanceof Number height) {
            double rawCount = count.doubleValue();
            double rawHeight = height.doubleValue();
            if (Double.isFinite(rawCount) && rawCount > 0 && Double.isFinite(rawHeight) && rawHeight >= 0) {
                excludedCount = (int) Math.min(rawCount, Integer.MAX_VALUE);
                excludedHeight = rawHeight;
            }
        }
        return new PageGeometry(pages, excludedCount, excludedHeight);
    }

    /**
     * Validates the raw page-geometry list from {@link #COMPUTE_PAGE_GEOMETRY_JS} into bounded
     * {@link PageSize} values. The geometry comes from the (clinic-authored) eForm DOM, so it is
     * fail-closed: too many pages, a non-finite dimension, or a dimension past
     * {@link #MAX_PAGE_DIMENSION} aborts the render rather than feeding a pathological {@code @page}
     * size into Chromium. A zero/negative measured box is skipped (that page falls back to Chromium's
     * default paper). An empty result is legal — a free-flow form authored no {@code pageN} divs.
     */
    static List<PageSize> readPageSizes(Object rawSizes) throws PDFGenerationException {
        if (!(rawSizes instanceof List<?> rawList)) {
            throw new PDFGenerationException("Browser rendering returned an unexpected page-geometry result.");
        }
        if (rawList.size() > MAX_PAGE_COUNT) {
            throw new PDFGenerationException("Browser rendering produced too many pages to size safely.");
        }
        List<PageSize> sizes = new ArrayList<>();
        for (Object rawSize : rawList) {
            if (!(rawSize instanceof Map<?, ?> rawMap)) {
                throw new PDFGenerationException("Browser rendering returned an unexpected page-geometry entry.");
            }
            String id = String.valueOf(rawMap.get("id"));
            double width = numberValue(rawMap, "width");
            double height = numberValue(rawMap, "height");
            // Fail closed on non-finite geometry (NaN/Infinity): every comparison below is false for
            // NaN, so it would otherwise slip through the bounds checks and reach the injected @page
            // CSS as an invalid size, failing the whole render unpredictably.
            if (!Double.isFinite(width) || !Double.isFinite(height)) {
                throw new PDFGenerationException("Browser rendering returned a non-finite page dimension.");
            }
            if (width <= 0 || height <= 0) {
                continue;
            }
            if (width > MAX_PAGE_DIMENSION || height > MAX_PAGE_DIMENSION) {
                throw new PDFGenerationException("Browser rendering page exceeds the maximum page dimension.");
            }
            sizes.add(new PageSize(id, width, height));
        }
        return sizes;
    }

    private static double numberValue(Map<?, ?> rawMap, String key) throws PDFGenerationException {
        Object value = rawMap.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new PDFGenerationException("Browser rendering returned a non-numeric page dimension.");
    }

    /**
     * Builds the {@code @page} sizing and pagination CSS for {@link #INJECT_PAGE_SIZE_CSS_JS}. When
     * every page shares a size (the common single-scan-geometry form) one anonymous
     * {@code @page { size }} rule covers them all; when sizes differ (e.g. a portrait page followed
     * by a landscape one) a CSS named page is emitted per page div and bound to it by id, so each
     * printed page keeps its authored geometry.
     *
     * <p>Every page div additionally gets an explicit pagination contract, because the legacy corpus
     * authors <em>no</em> page-break CSS at all (the raster path photographed regions and never
     * needed any): its {@code height} is pinned to the printed page height so the div's flow extent
     * can never exceed one page (a fractional-height background otherwise spills a mostly-blank
     * page and shifts every later field off its background — the "extra blank pages between pages,
     * checkboxes misaligned" corpus regression), {@code overflow: hidden} clips content past the
     * page box exactly as the region capture did, {@code margin: 0} removes inter-page gaps, and
     * {@code break-after: page} forces each div onto its own printed page. The LAST div instead gets
     * {@code break-after: auto} so a form whose final div carries an authored inline
     * {@code page-break-after: always} does not emit a trailing blank page ({@code !important} in an
     * author stylesheet outranks a non-important inline declaration, so these rules win over inline
     * authored styles in both directions).</p>
     *
     * <p>Sizes are px (Chromium converts to the PDF's points at 96dpi), matching the legacy raster
     * path's {@code px * 72/96} page boxes. Returns empty CSS for an empty list (never injected).</p>
     */
    static String buildPageSizeCss(List<PageSize> pages) {
        if (pages.isEmpty()) {
            return "";
        }
        long firstWidth = (long) Math.ceil(pages.get(0).width());
        long firstHeight = (long) Math.ceil(pages.get(0).height());
        boolean uniform = pages.stream().allMatch(page ->
                (long) Math.ceil(page.width()) == firstWidth && (long) Math.ceil(page.height()) == firstHeight);
        StringBuilder css = new StringBuilder();
        if (uniform) {
            css.append("@page { size: ").append(cssPx(pages.get(0).width())).append(' ')
                    .append(cssPx(pages.get(0).height())).append("; margin: 0; }\n");
        } else {
            for (int index = 0; index < pages.size(); index++) {
                PageSize page = pages.get(index);
                css.append("@page carlosPage").append(index + 1).append(" { size: ")
                        .append(cssPx(page.width())).append(' ').append(cssPx(page.height()))
                        .append("; margin: 0; }\n");
            }
        }
        for (int index = 0; index < pages.size(); index++) {
            PageSize page = pages.get(index);
            boolean last = index == pages.size() - 1;
            // The id came through the /^page\d+$/i geometry filter, so it is a safe CSS id selector.
            css.append('#').append(page.id()).append(" {");
            if (!uniform) {
                css.append(" page: carlosPage").append(index + 1).append(';');
            }
            css.append(" height: ").append(cssPx(page.height())).append(" !important;")
                    .append(" margin: 0 !important;")
                    .append(" overflow: hidden !important;")
                    .append(" break-inside: avoid !important;")
                    .append(" break-after: ").append(last ? "auto" : "page").append(" !important; }\n");
        }
        return css.toString();
    }

    /** CSS px length rounded up to a whole pixel, so a fractional content box never clips content. */
    private static String cssPx(double value) {
        return ((long) Math.ceil(value)) + "px";
    }

    /** Immutable authored page size in CSS px, tagged with the source {@code pageN} div id. */
    record PageSize(String id, double width, double height) {
    }

    /**
     * Full page-geometry measurement: the authored page sizes plus advisory diagnostics about
     * substantive in-flow content found outside the page divs and excluded from the printed PDF
     * (see {@link #COMPUTE_PAGE_GEOMETRY_JS}). {@code excludedCount}/{@code excludedHeight} drive
     * the operator WARN only — they never affect the print itself.
     */
    record PageGeometry(List<PageSize> pages, int excludedCount, double excludedHeight) {
    }

    // ---------------------------------------------------------------------------------------------
    // Render gates (fail-closed): main-document status, loopback-only egress, console errors
    // ---------------------------------------------------------------------------------------------

    // Package-private for the unit test that pins the console-log-unavailable fail-closed branch.
    void enforceRenderGates(ChromeDriver driver, List<LogEntry> performanceEntries,
            Integer latchedMainStatus, String baseUrl, int fdid) throws PDFGenerationException {
        enforceRenderGates(driver, performanceEntries, latchedMainStatus, baseUrl, fdid, false);
    }

    /**
     * @param allowMissingContent when {@code true}, a failure of the eForm's own same-origin visual
     *        assets is logged and tolerated instead of throwing {@link EformContentUnavailableException}
     *        — the "render anyway" path. The always-hard gates (main document, live egress channels,
     *        unparseable evidence) are unaffected.
     */
    void enforceRenderGates(ChromeDriver driver, List<LogEntry> performanceEntries,
            Integer latchedMainStatus, String baseUrl, int fdid, boolean allowMissingContent) throws PDFGenerationException {
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
        // Hard fail-closed on live bidirectional channels regardless of the strict-gate switch: a
        // render surface never legitimately opens a WebSocket or WebTransport, and these bypass the
        // dead HTTP proxy, so any such attempt is treated as an egress channel and aborts the render.
        if (scan.liveChannelAttempts() > 0) {
            logger.error("Browser eForm renderer observed a live egress channel: fdid={} liveChannelAttempts={}",
                    fdid, scan.liveChannelAttempts());
            throw new PDFGenerationException(
                    "Browser rendering attempted a live egress channel. liveChannelAttempts=" + scan.liveChannelAttempts());
        }
        // The render's OWN same-origin (CARLOS) visual content failed to load — a missing signature
        // block, form image, or stylesheet served by our EMR means the produced PDF may be visually
        // incomplete. POLICY: this is ADVISORY, never a hard failure — only security gates (main
        // document, live egress channels, unverifiable network evidence) abort a render. The legacy
        // corpus routinely references optional per-provider assets (signature stamps, letterheads)
        // that legitimately 404 for most users; failing the render on them dead-ended routine
        // downloads. The WARN below is the operator's signal that a form's own content is missing.
        // Counts only — never request URLs, which can carry eForm content. (allowMissingContent is
        // retained for API compatibility; both paths log-and-continue now.)
        if (scan.failedCriticalSubresources() > 0) {
            logger.warn("Browser eForm renderer producing a possibly INCOMPLETE eForm ({}): fdid={} failedCriticalSubresources={}",
                    allowMissingContent ? "per render-anyway choice" : "missing same-origin content is advisory",
                    fdid, scan.failedCriticalSubresources());
        }
        if (disallowedRequests > 0 || severeConsoleEntries > 0 || scan.failedSubresources() > 0) {
            // Off-origin HTTP requests, failed render-critical subresources, and severe page-script
            // console errors are advisory by default (see STRICT_NETWORK_GATE_PROPERTY): the legacy
            // eForm corpus references off-origin assets, 404s optional helper scripts/images, and
            // emits benign JS errors — none of which blank the form, and off-origin HTTP is already
            // physically blocked by the dead proxy. Failing the render on them denied the fax for
            // every form that was not perfectly self-contained. Counts only — never request URLs or
            // console text, which can carry eForm content.
            if (strictNetworkGateEnabled()) {
                logger.error("Browser eForm renderer surfaced page errors (strict gate): fdid={} disallowedRequests={} severeConsoleEntries={} failedSubresources={}",
                        fdid, disallowedRequests, severeConsoleEntries, scan.failedSubresources());
                throw new PDFGenerationException("Browser rendering surfaced page errors. disallowedRequests="
                        + disallowedRequests + " consoleErrors=" + severeConsoleEntries
                        + " failedSubresources=" + scan.failedSubresources());
            }
            logger.warn("Browser eForm renderer tolerated non-fatal page issues: fdid={} disallowedRequests={} severeConsoleEntries={} failedSubresources={}"
                    + " (off-origin egress is contained by the dead proxy; set {}=true to fail closed instead)",
                    fdid, disallowedRequests, severeConsoleEntries, scan.failedSubresources(), STRICT_NETWORK_GATE_PROPERTY);
        }
    }

    /**
     * Whether the strict fail-closed network gate is enabled. Defaults to {@code false} (advisory);
     * see {@link #STRICT_NETWORK_GATE_PROPERTY}. Never affects the always-on hard gates
     * (WebSocket/WebTransport, main-document status, unparseable network evidence).
     */
    private static boolean strictNetworkGateEnabled() {
        return Boolean.parseBoolean(
                CarlosProperties.getInstance().getProperty(STRICT_NETWORK_GATE_PROPERTY, "false").trim());
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
     * {@code liveChannelAttempts} counts WebSocket/WebTransport creations, which are an always-on
     * hard fail-closed signal (they bypass the dead HTTP proxy).
     * {@code failedCriticalSubresources} counts failures of the render's <em>own</em> same-origin
     * visual/structural content (a signature block, a form image, a stylesheet served by CARLOS) —
     * an always-on hard fail-closed signal, because the rendered PDF is then genuinely wrong.
     * {@code disallowedRequests} (off-origin HTTP, already blocked by the dead proxy) and
     * {@code failedSubresources} (off-origin or non-visual same-origin failures such as helper
     * scripts that do not paint) are advisory by default and only fail the render under the strict
     * network gate (see {@link #STRICT_NETWORK_GATE_PROPERTY}).
     */
    record NetworkGateScan(int disallowedRequests, Integer mainDocumentStatus, int failedSubresources,
            int parseFailures, int liveChannelAttempts, int failedCriticalSubresources) {
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
     * The subset of {@link #RENDER_CRITICAL_RESOURCE_TYPES} whose bytes are actually painted into
     * the captured PDF — the form's own visual/structural content: images (including the signature
     * block), secondary {@code Document} iframes (e.g. the signature frame), stylesheets, and fonts.
     * A failure of one of these <em>from the render's own same-origin (CARLOS) endpoints</em> is our
     * EMR failing to serve the form's declared content, so it hard-fails the render. Non-visual types
     * ({@code Script}, {@code XHR}, {@code Fetch}, {@code Media}) do not paint — a legacy helper
     * script that 404s (e.g. {@code faxControl.js}) leaves the already-painted form intact — and are
     * treated as advisory, as are all off-origin failures.
     */
    private static final Set<String> RENDER_CRITICAL_VISUAL_TYPES =
            Set.of("Document", "Image", "Stylesheet", "Font");

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
     * requests cannot fail a visually-intact render. Failures are further split by origin and type:
     * a same-origin (CARLOS) failure of a painted {@link #RENDER_CRITICAL_VISUAL_TYPES visual type}
     * is <em>critical</em> (our EMR failed to serve the form's own content — a hard fail), while
     * off-origin failures and non-visual same-origin failures (helper scripts that never paint) are
     * <em>advisory</em>. {@code loadingFailed} carries no URL, so it is treated as advisory.</p>
     */
    static NetworkGateScan scanNetworkEvents(List<String> rawEntries, String allowedOrigin) {
        int disallowedRequests = 0;
        Integer mainDocumentStatus = null;
        String mainDocumentUrl = null;
        int failedSubresources = 0;
        int parseFailures = 0;
        int liveChannelAttempts = 0;
        int failedCriticalSubresources = 0;
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
                // A render surface never opens a WebSocket or WebTransport. Counted separately from
                // off-origin HTTP because this is an unconditional hard fail-closed signal regardless
                // of URL/origin: a same-origin wss:/https: WebTransport would pass
                // isDisallowedRendererRequestUrl (http(s) to the allowed origin) yet is still a live
                // bidirectional egress channel the dead HTTP proxy does not cover. Off-origin HTTP,
                // by contrast, is already blocked by the dead proxy and is only advisory.
                liveChannelAttempts++;
            } else if ("Network.responseReceived".equals(method)) {
                String resourceType = params.path("type").asText("");
                String responseUrl = params.path("response").path("url").asText("");
                boolean sameOrigin = allowedOrigin != null && allowedOrigin.equals(originOf(responseUrl));
                if (mainDocumentStatus == null && "Document".equals(resourceType) && sameOrigin) {
                    mainDocumentStatus = params.path("response").path("status").asInt();
                    mainDocumentUrl = responseUrl;
                } else if (RENDER_CRITICAL_RESOURCE_TYPES.contains(resourceType)
                        && params.path("response").path("status").asInt() >= 400) {
                    // A same-origin failure of a painted type (signature/image/stylesheet/font served
                    // by CARLOS) is our EMR failing to serve the form's own content → missing-content
                    // signal (user-promptable). Off-origin failures and non-visual same-origin
                    // failures (helper scripts that do not paint) are advisory: they do not blank the
                    // already-rendered form. A subresource fetch of the MAIN DOCUMENT URL itself is
                    // also advisory: it is the legacy empty-src placeholder idiom (<img src=""> — a
                    // JS-populated signature stamp) resolving to the page's own URL, not real form
                    // content failing to load; the single-use render token has already been consumed
                    // by the page navigation, so this self-fetch can never succeed and never paints.
                    if (sameOrigin && RENDER_CRITICAL_VISUAL_TYPES.contains(resourceType)
                            && !responseUrl.equals(mainDocumentUrl)) {
                        failedCriticalSubresources++;
                    } else {
                        failedSubresources++;
                    }
                }
            } else if ("Network.loadingFailed".equals(method)
                    && RENDER_CRITICAL_RESOURCE_TYPES.contains(params.path("type").asText(""))
                    && !params.path("canceled").asBoolean(false)) {
                // loadingFailed carries no URL, so origin cannot be attributed; a connection-level
                // failure is almost always the dead proxy refusing an off-origin request, so it is
                // advisory. A genuinely missing same-origin asset instead arrives as an HTTP 4xx/5xx
                // responseReceived above, where it is correctly classified as critical.
                failedSubresources++;
            }
        }
        return new NetworkGateScan(disallowedRequests, mainDocumentStatus, failedSubresources,
                parseFailures, liveChannelAttempts, failedCriticalSubresources);
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
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of the literal request scheme against https to detect a proxied-TLS loopback hop; not a security or authorization decision on user identity.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of the literal request scheme against https to detect a proxied-TLS loopback hop; not a security or authorization decision on user identity")
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

    // FindSecBugs PATH_TRAVERSAL_IN: the renderer output PDF is created only under a validated managed temp root, with caller-controlled filenames disallowed.
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "Renderer temp files are created only beneath resolveRendererTempRoot(), which validates configured roots before creating a managed private temp file.")
    static Path createSecureTempFile(Path tempRoot, String prefix, String suffix) throws IOException {
        Path managedRoot = Files.createDirectories(tempRoot);
        // Reject a pre-seeded symlink at the managed root: a local attacker who wins the race to
        // create the predictable renderer temp root as a symlink could otherwise redirect the
        // rendered PDF outside the CARLOS-owned tree. The file itself is created atomically with a
        // random name and 0600 via createTempFile below.
        if (Files.isSymbolicLink(managedRoot)) {
            throw new IOException("Renderer temp root must be a real directory, not a symbolic link: " + managedRoot);
        }
        FileAttribute<?>[] secureAttributes = securePosixAttributes();
        try {
            return Files.createTempFile(managedRoot, prefix, suffix, secureAttributes);
        } catch (UnsupportedOperationException e) {
            throw new IOException("Renderer temp path requires POSIX filesystem permissions under " + managedRoot, e);
        }
    }

    private static FileAttribute<?>[] securePosixAttributes() {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
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

    /** @return {@code true} when the path no longer exists after the attempt (deleted or already gone). */
    private static boolean deleteQuietly(Path outputPath) {
        if (outputPath == null) {
            return false;
        }
        try {
            Files.deleteIfExists(outputPath);
        } catch (IOException e) {
            // WARN, not DEBUG: an undeletable rendered PDF is PHI accumulating on disk, and at
            // default log levels an operator must learn the sweep is papering over a real
            // filesystem/permission problem. The path is a managed temp path, not PHI.
            logger.warn("Unable to delete temporary browser-rendered PDF {}", outputPath, e);
        }
        return !Files.exists(outputPath);
    }

    /** @return {@code true} only when every entry was removed and the directory itself no longer exists. */
    private static boolean deleteRecursivelyQuietly(Path directory) {
        if (directory == null) {
            return false;
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
            return false;
        }
        if (failedEntries > 0) {
            logger.warn("Unable to delete {} entries under renderer capture directory {}; PHI capture images may be accumulating",
                    failedEntries, directory);
            return false;
        }
        return !Files.exists(directory);
    }

    /**
     * Age after which an orphaned same-prefixed renderer <em>directory</em> under the shared managed
     * root is swept. The native print path creates no per-render directory; this reclaims capture
     * directories left by a prior (raster) build that was killed mid-render — an upgrade-time
     * backstop. Such dirs are always caller-detached, so a short window reclaims them safely.
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
     * Best-effort sweep of renderer artifacts under the shared managed root: the {@code .pdf} output
     * files this native print path creates (left behind by a caller that failed before consuming a
     * returned output), plus any same-prefixed {@code eform-browser-render-*} <em>capture directory</em>
     * left by a prior (raster) build that was killed mid-render (an upgrade-time backstop). Directories
     * are reclaimed after {@link #STALE_RENDERER_DIR_TTL} and caller-owned output PDFs only after the
     * much longer {@link #STALE_RENDERER_OUTPUT_TTL}, so an in-flight render — and a returned,
     * not-yet-consumed output — is never touched. Never throws — a sweep failure must not fail the
     * render.
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
                    // Count only a confirmed reclamation: deleteRecursivelyQuietly returns false when
                    // any entry survived, so the metric can't claim cleanup that didn't happen.
                    if (modifiedMillis < dirCutoffMillis && deleteRecursivelyQuietly(entry)) {
                        reclaimed++;
                    }
                } else if (entry.getFileName().toString().endsWith(".pdf") && modifiedMillis < outputCutoffMillis
                        && deleteQuietly(entry)) { // orphaned output reclaimed only long past any request lifetime
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
