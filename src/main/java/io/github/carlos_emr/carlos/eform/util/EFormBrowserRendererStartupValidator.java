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
 * Advisory startup readiness check for the eForm browser PDF renderer.
 *
 * <p>The browser renderer is the only path that produces saved-eForm fax/archive PDFs, so the
 * application probes it during startup and warns operators immediately when it is unavailable.
 * A failed probe does not abort Spring context initialization; the application remains available
 * and the renderer failure surfaces again if an eForm print/fax/archive operation is attempted.</p>
 *
 * <p>Set {@value #STARTUP_CHECK_PROPERTY} to {@code off} to skip the probe entirely. Integration-test
 * Spring contexts use this setting so they do not launch Chromium in the test JVM (see
 * {@code src/test/resources/over_ride_config.properties.template}). The historical {@code required}
 * value and the {@code warn} value both run the advisory probe for configuration compatibility.</p>
 *
 * <p>The real probe lives in {@link EFormBrowserPdfService#verifyRendererReady()}; a real browser
 * launch is the only honest readiness signal.</p>
 */
@Component
public class EFormBrowserRendererStartupValidator {

    /** Property selecting whether the advisory startup probe runs; {@code off} skips it. */
    static final String STARTUP_CHECK_PROPERTY = "eform_pdf_browser_startup_check";

    private static final Logger logger = MiscUtils.getLogger();

    private final EFormBrowserPdfService eFormBrowserPdfService;

    public EFormBrowserRendererStartupValidator(EFormBrowserPdfService eFormBrowserPdfService) {
        this.eFormBrowserPdfService = eFormBrowserPdfService;
    }

    /** Probes the renderer once during context startup and warns without aborting on failure. */
    // IMPROPER_UNICODE: equalsIgnoreCase here classifies the literal configuration mode tokens
    // ("off"); a case-insensitive keyword compare, not a security or authorization decision.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of the literal startup-check mode keyword (off); not a security or authorization decision")
    @PostConstruct
    public void verifyRendererReadyAtStartup() {
        try {
            String mode = CarlosProperties.getInstance().getProperty(STARTUP_CHECK_PROPERTY, "warn").trim();
            if ("off".equalsIgnoreCase(mode)) {
                logger.warn("eForm browser renderer startup check is OFF; render failures will surface at first use.");
                return;
            }
            // Cheap config-format validation first. Connectivity to the app's own origin cannot be
            // probed here because Tomcat is not serving yet, so this validates format and the launch
            // probe validates the browser.
            eFormBrowserPdfService.verifyConfiguredBaseUrl();
            // Kept separate from the base-URL check on purpose: the two properties have different
            // operator remediations, and one message covering both is one nobody can act on.
            eFormBrowserPdfService.verifyConfiguredServiceUrl();
            eFormBrowserPdfService.verifyRendererReady();
            logger.info("eForm browser renderer startup check passed.");
        } catch (PDFGenerationException | RuntimeException e) {
            // This lifecycle boundary is deliberately advisory. Catch unexpected runtime failures
            // as well as the probe's declared exception so an ordinary renderer fault cannot abort
            // the Spring context. Do not log the throwable: Selenium failures may contain local
            // paths or URLs, so retain only redacted diagnostic text.
            logger.warn("eForm browser renderer is NOT ready; CARLOS startup will continue, but eForm "
                            + "print/fax/archive will fail until the renderer is fixed. type={} error={}",
                    e.getClass().getName(),
                    RenderLogRedaction.redactUrls(String.valueOf(e.getMessage())));
        }
    }
}
