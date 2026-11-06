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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Logger;

/**
 * Renders the bundled Chart.js to the existing PNG/JPEG endpoint contract, including print views.
 *
 * <p>Each image is drawn in a fresh headless Chromium session from the renderer service shared
 * with eForm PDFs, so a chart costs a browser start and competes for the same two render slots.
 * The bundled scripts are read from the WAR once and reused; chart requests queue for a slot for
 * {@link EFormBrowserPdfService}'s slot wait rather than failing while the slots are merely busy.
 */
public final class BrowserChartRenderer {
    private static final Logger logger = MiscUtils.getLogger();
    private static final String LIBRARY = "/library/chartjs/chart.umd.min.js";
    private static final String DRAW_SCRIPT = "/library/chartjs/carlos-chart-draw.js";
    private static final Semaphore SLOTS = EFormBrowserPdfService.RENDER_SLOTS;
    /**
     * Chart requests are short, so many may queue for a slot; the bound only keeps a stalled
     * renderer from holding every servlet thread.
     */
    private static final Semaphore WAITERS = new Semaphore(16);
    private static volatile String bundledScripts;

    private BrowserChartRenderer() { }

    /** Drops the cached scripts so the next render reads them from the WAR again (tests). */
    static void forgetBundledScripts() { bundledScripts = null; }

    public static void write(ServletContext context, OutputStream output, MeasurementChart chart,
                             int width, int height, String format) throws IOException {
        if (chart == null || width < 1 || width > 1800 || height < 1 || height > 3200
                || !("png".equals(format) || "jpeg".equals(format))) {
            throw new IllegalArgumentException("Invalid chart image request");
        }
        boolean acquired = false;
        EFormBrowserPdfService.RendererBrowser session = null;
        try {
            if (!WAITERS.tryAcquire()) throw new IOException("Chart renderer is busy");
            try {
                acquired = SLOTS.tryAcquire(30, TimeUnit.SECONDS);
            } finally {
                WAITERS.release();
            }
            if (!acquired) throw new IOException("Chart renderer is busy");
            String scripts = bundledScripts(context);
            BrowserOptions options = EFormBrowserPdfService.buildBrowserOptions(
                    EFormBrowserPdfService.resolveChromiumPath(), !EFormBrowserPdfService.sandboxEnabled(), "http://127.0.0.1");
            session = new EFormBrowserPdfService().createDriver(options);
            RenderBrowser browser = session.driver();
            browser.setTimeouts(Duration.ofSeconds(30), Duration.ofSeconds(30));
            browser.get("about:blank");
            browser.executeScript("const s=document.createElement('script'); s.textContent=arguments[0]; document.head.append(s);", scripts);
            Object drawn = browser.executeScript("return window.carlosDrawChart(arguments[0], arguments[1], arguments[2]).length > 0;",
                    chart.toConfig(), width, height);
            if (!Boolean.TRUE.equals(drawn)) throw new IOException("Chart image is unavailable");
            Map<String, Object> screenshot = browser.executeCdpCommand("Page.captureScreenshot", Map.of(
                    "format", format, "captureBeyondViewport", true,
                    "clip", Map.of("x", 0, "y", 0, "width", width, "height", height, "scale", 1)));
            Object data = screenshot.get("data");
            if (!(data instanceof String encoded) || encoded.isEmpty()) throw new IOException("Chart image is unavailable");
            output.write(Base64.getDecoder().decode(encoded));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new IOException("Chart rendering interrupted");
        } catch (RuntimeException | io.github.carlos_emr.carlos.utility.PDFGenerationException failure) {
            // Browser errors can include clinical values; expose neither the reply nor its cause.
            logger.warn("Chart rendering failed ({})", failure.getClass().getName());
            throw new IOException("Chart rendering failed");
        } finally {
            EFormBrowserPdfService.teardownQuietly(session);
            if (acquired) SLOTS.release();
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
            if (source == null) throw new IOException("Bundled chart script is unavailable: " + path);
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
