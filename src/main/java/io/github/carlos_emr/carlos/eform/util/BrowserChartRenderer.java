/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.MeasurementChart;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.apache.logging.log4j.Logger;

/**
 * Renders the bundled Chart.js to the existing PNG/JPEG endpoint contract, including print views.
 *
 * <p>Each image is drawn in a fresh headless Chromium session from the renderer service shared
 * with eForm PDFs, so a chart costs a browser start. Charts have one browser of their own and take it
 * first, so a chart never waits behind eForm PDFs (#5853). When it is busy they share the two-browser
 * budget with PDFs, and class-aware admission keeps either work class from repeatedly overtaking the other.
 * The bundled scripts are read from the WAR once and reused; chart requests queue for a slot for
 * {@link EFormBrowserPdfService}'s slot wait rather than failing while the slots are merely busy.
 */
public final class BrowserChartRenderer {
    private static final Logger logger = MiscUtils.getLogger();
    private static final String LIBRARY = "/library/chartjs/chart.umd.min.js";
    private static final String DRAW_SCRIPT = "/library/chartjs/carlos-chart-draw.js";

    private static volatile String bundledScripts;
    /**
     * The tallest image: ChartMeds grows 30px per drug past twenty (#6165), and JFreeChart drew any count, so allow
     * hundreds of rows while staying inside Chromium's canvas limits.
     */
    static final int MAX_HEIGHT = 16_000;

    private BrowserChartRenderer() { }

    /** Drops the cached scripts so the next render reads them from the WAR again (tests). */
    static void forgetBundledScripts() { bundledScripts = null; }

    public static void write(ServletContext context, OutputStream output, MeasurementChart chart,
                             int width, int height, String format) throws IOException {
        if (chart == null || width < 1 || width > 1800 || height < 1 || height > MAX_HEIGHT
                || !("png".equals(format) || "jpeg".equals(format))) {
            throw new IllegalArgumentException("Invalid chart image request");
        }
        RenderAdmission.Lease lease = null;
        ChartRenderFailure.Reason phase = ChartRenderFailure.Reason.UNAVAILABLE;
        EFormBrowserPdfService.RendererBrowser session = null;
        try {
            lease = EFormBrowserPdfService.ADMISSION.acquire(RenderAdmission.Kind.CHART, Duration.ofSeconds(30));
            if (lease == null) throw new ChartRenderFailure(ChartRenderFailure.Reason.BUSY, "Chart renderer is busy; please retry shortly.");
            String scripts = bundledScripts(context);
            BrowserOptions options = EFormBrowserPdfService.buildBrowserOptions(
                    EFormBrowserPdfService.resolveChromiumPath(), !EFormBrowserPdfService.sandboxEnabled(), "http://127.0.0.1");
            session = new EFormBrowserPdfService().createDriver(options);
            phase = ChartRenderFailure.Reason.DRAW_FAILED;
            RenderBrowser browser = session.driver();
            browser.setTimeouts(Duration.ofSeconds(30), Duration.ofSeconds(30));
            browser.get("about:blank");
            browser.executeScript("const s=document.createElement('script'); s.textContent=arguments[0]; document.head.append(s);", scripts);
            Object drawn = browser.executeScript("return window.carlosDrawChart(arguments[0], arguments[1], arguments[2]).length > 0;",
                    chart.toConfig(), width, height);
            if (!Boolean.TRUE.equals(drawn)) throw new ChartRenderFailure(ChartRenderFailure.Reason.DRAW_FAILED, "Chart image is unavailable");
            Map<String, Object> capture = new java.util.LinkedHashMap<>();
            capture.put("format", format);
            capture.put("captureBeyondViewport", true);
            capture.put("clip", Map.of("x", 0, "y", 0, "width", width, "height", height, "scale", 1));
            // ChartUtils.writeChartAsJPEG was called with quality 1.0; Chromium's default is 80.
            if ("jpeg".equals(format)) capture.put("quality", 100);
            Map<String, Object> screenshot = browser.executeCdpCommand("Page.captureScreenshot", capture);
            Object data = screenshot.get("data");
            if (!(data instanceof String encoded) || encoded.isEmpty()) throw new ChartRenderFailure(ChartRenderFailure.Reason.DRAW_FAILED, "Chart image is unavailable");
            output.write(Base64.getDecoder().decode(encoded));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new ChartRenderFailure(ChartRenderFailure.Reason.INTERRUPTED, "Chart rendering interrupted");
        } catch (RuntimeException | io.github.carlos_emr.carlos.utility.PDFGenerationException failure) {
            // Browser errors can include clinical values; expose neither the reply nor its cause.
            logger.warn("Chart rendering failed: reason={}, exception={}", phase, failure.getClass().getName());
            throw new ChartRenderFailure(phase, "Chart rendering failed");
        } finally {
            EFormBrowserPdfService.teardownQuietly(session);
            if (lease != null) lease.close();
        }
    }

    private static String bundledScripts(ServletContext context) throws IOException {
        String scripts = bundledScripts;
        if (scripts == null) {
            scripts = read(context, LIBRARY) + "\n" + read(context, DRAW_SCRIPT);
            bundledScripts = scripts;
        }
        return scripts;
    }

    private static String read(ServletContext context, String path) throws IOException {
        try (InputStream source = context.getResourceAsStream(path)) {
            if (source == null) throw new ChartRenderFailure(ChartRenderFailure.Reason.DRAW_FAILED, "Bundled chart script is unavailable");
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
