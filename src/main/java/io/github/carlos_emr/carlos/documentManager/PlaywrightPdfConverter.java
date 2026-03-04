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
package io.github.carlos_emr.carlos.documentManager;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.WaitUntilState;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;

import java.io.OutputStream;
import java.util.List;

/**
 * Converts HTML to PDF using headless Chromium via Playwright Java.
 *
 * <p>Replaces the deprecated wkhtmltopdf-based {@code InternalEDocConverter} with
 * Chromium's native print-to-PDF engine, providing full HTML5, CSS3 (including
 * {@code @media print}, flexbox, grid), and JavaScript execution support.</p>
 *
 * <p>Uses a singleton browser instance that is launched once and reused across all
 * PDF conversions. Each conversion creates an isolated {@link BrowserContext} that
 * is closed after the PDF is generated, preventing resource leaks.</p>
 *
 * <p>Network isolation: all external network requests are blocked. Only {@code file://}
 * and {@code data:} URIs are permitted for loading local resources.</p>
 *
 * @since 2026-03-04
 */
public class PlaywrightPdfConverter implements EDocConverterInterface {

    private static final Logger logger = MiscUtils.getLogger();

    private static volatile Playwright playwright;
    private static volatile Browser browser;
    private static final Object lock = new Object();

    private static final int PAGE_TIMEOUT_MS = 10_000;

    /**
     * Converts an HTML string to PDF bytes written to the given output stream.
     *
     * <p>Creates an isolated browser context per conversion. JavaScript is executed
     * and the page waits for network idle before generating the PDF. All external
     * network requests are aborted for security.</p>
     *
     * @param html String the complete HTML document to convert
     * @param os   OutputStream where the generated PDF bytes are written
     * @throws Exception if Chromium fails to launch or PDF generation fails
     */
    @Override
    public void convert(String html, OutputStream os) throws Exception {
        Browser b = getBrowser();
        try (BrowserContext context = b.newContext()) {
            // Block all external network requests for security
            context.route("**", route -> {
                String url = route.request().url();
                if (url.startsWith("file:") || url.startsWith("data:")) {
                    route.resume();
                } else {
                    route.abort();
                }
            });

            Page page = context.newPage();
            page.setDefaultTimeout(PAGE_TIMEOUT_MS);
            page.setContent(html, new Page.SetContentOptions()
                    .setWaitUntil(WaitUntilState.LOAD));

            byte[] pdfBytes = page.pdf(new Page.PdfOptions()
                    .setFormat("Letter")
                    .setMargin(new Margin()
                            .setTop("10mm")
                            .setLeft("8mm")
                            .setRight("8mm"))
                    .setPrintBackground(true));

            os.write(pdfBytes);
        }
    }

    /**
     * Returns the singleton browser instance, launching it on first call.
     *
     * @return Browser the shared headless Chromium browser instance
     */
    private Browser getBrowser() {
        if (browser == null || !browser.isConnected()) {
            synchronized (lock) {
                if (browser == null || !browser.isConnected()) {
                    logger.info("Launching Playwright headless Chromium for PDF generation");
                    playwright = Playwright.create();
                    browser = playwright.chromium().launch(
                            new BrowserType.LaunchOptions()
                                    .setHeadless(true)
                                    .setArgs(List.of(
                                            "--no-sandbox",
                                            "--disable-gpu",
                                            "--disable-dev-shm-usage")));
                    logger.info("Playwright Chromium browser launched successfully");
                }
            }
        }
        return browser;
    }

    /**
     * Shuts down the shared browser and Playwright instances.
     * Call this on application shutdown to release Chromium resources.
     */
    public static void shutdown() {
        synchronized (lock) {
            if (browser != null) {
                try {
                    browser.close();
                } catch (Exception e) {
                    MiscUtils.getLogger().warn("Error closing Playwright browser", e);
                }
                browser = null;
            }
            if (playwright != null) {
                try {
                    playwright.close();
                } catch (Exception e) {
                    MiscUtils.getLogger().warn("Error closing Playwright instance", e);
                }
                playwright = null;
            }
        }
    }
}
