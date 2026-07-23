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

import jakarta.annotation.PostConstruct;

import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

/**
 * Startup readiness gate for the eForm browser PDF renderer.
 *
 * <p>The browser renderer is the ONLY path that produces saved-eForm fax/archive PDFs — there is no
 * legacy fallback — so a webapp whose renderer cannot launch would silently break every eForm
 * print/fax/archive workflow. The deployment decision is therefore to fail closed: in the default
 * {@code required} mode a failed readiness probe throws from {@code @PostConstruct}, which aborts
 * Spring context initialization so Tomcat refuses to deploy the webapp rather than run it broken.</p>
 *
 * <p>Modes are selected by the {@value #STARTUP_CHECK_PROPERTY} property:</p>
 * <ul>
 *   <li>{@code required} (default) — probe; abort startup with {@link IllegalStateException} on failure.</li>
 *   <li>{@code warn} — probe; log an error and continue so the failure surfaces at first render.</li>
 *   <li>{@code off} — skip the probe entirely. Integration-test Spring contexts set this so the
 *       gate never launches Chromium in the test JVM (see
 *       {@code src/test/resources/over_ride_config.properties}).</li>
 * </ul>
 *
 * <p>The real probe lives in {@link EFormBrowserPdfService#verifyRendererReady()}; a real browser
 * launch is the only honest readiness signal.</p>
 */
@Component
public class EFormBrowserRendererStartupValidator {

    /** Property selecting the gate mode: {@code required} (default), {@code warn}, or {@code off}. */
    static final String STARTUP_CHECK_PROPERTY = "eform_pdf_browser_startup_check";

    private static final Logger logger = MiscUtils.getLogger();

    private final EFormBrowserPdfService eFormBrowserPdfService;

    public EFormBrowserRendererStartupValidator(EFormBrowserPdfService eFormBrowserPdfService) {
        this.eFormBrowserPdfService = eFormBrowserPdfService;
    }

    /**
     * Probes the renderer once during context startup and enforces the configured mode. Runs in
     * {@code @PostConstruct} so a {@code required}-mode failure aborts context initialization
     * (Tomcat then refuses to deploy the webapp).
     *
     * @throws IllegalStateException in {@code required} mode when the renderer probe fails
     */
    // IMPROPER_UNICODE: equalsIgnoreCase here classifies the literal configuration mode tokens
    // ("off"/"warn"); a case-insensitive keyword compare, not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of the literal startup-check mode keyword (off/warn); not a security or authorization decision")
    @PostConstruct
    public void verifyRendererReadyOrFailStartup() {
        String mode = CarlosProperties.getInstance().getProperty(STARTUP_CHECK_PROPERTY, "required").trim();
        if ("off".equalsIgnoreCase(mode)) {
            logger.warn("eForm browser renderer startup check is OFF; render failures will surface at first use.");
            return;
        }
        try {
            eFormBrowserPdfService.verifyRendererReady();
            logger.info("eForm browser renderer startup check passed.");
        } catch (PDFGenerationException e) {
            if ("warn".equalsIgnoreCase(mode)) {
                logger.error("eForm browser renderer is NOT ready ({}); eForm print/fax/archive will fail until fixed: {}",
                        STARTUP_CHECK_PROPERTY + "=warn", e.getMessage());
                return;
            }
            // required (default): the renderer is the only eForm PDF path — refuse to start a
            // webapp whose fax/archive workflows would silently be broken.
            throw new IllegalStateException(
                    "CARLOS startup aborted: the eForm browser PDF renderer failed its readiness check. "
                    + e.getMessage() + " Install Chromium and a matching chromedriver "
                    + "(eform_pdf_browser_chromium_path / eform_pdf_browser_chromedriver_path), or configure "
                    + STARTUP_CHECK_PROPERTY + "=warn|off to defer.", e);
        }
    }
}
