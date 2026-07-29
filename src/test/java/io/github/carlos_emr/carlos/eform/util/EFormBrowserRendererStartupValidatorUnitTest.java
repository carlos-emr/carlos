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

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Behaviour of the advisory startup readiness check for the eForm browser PDF renderer.
 *
 * <p>These tests pin the compatible configuration values and success/failure paths without launching
 * a real browser: the probe on {@link EFormBrowserPdfService} is mocked, and the
 * {@code eform_pdf_browser_startup_check} property is toggled through the {@link CarlosProperties}
 * singleton and restored afterwards (same idiom as {@code EFormBrowserPdfServiceUnitTest}).</p>
 */
@DisplayName("EFormBrowserRendererStartupValidator unit tests")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class EFormBrowserRendererStartupValidatorUnitTest {

    private static final String STARTUP_CHECK_PROPERTY =
            EFormBrowserRendererStartupValidator.STARTUP_CHECK_PROPERTY;

    private final CarlosProperties properties = CarlosProperties.getInstance();
    private String originalMode;

    @BeforeEach
    void captureOriginalMode() {
        originalMode = properties.getProperty(STARTUP_CHECK_PROPERTY);
    }

    @AfterEach
    void restoreOriginalMode() {
        if (originalMode == null) {
            properties.remove(STARTUP_CHECK_PROPERTY);
        } else {
            properties.setProperty(STARTUP_CHECK_PROPERTY, originalMode);
        }
    }

    @Test
    @DisplayName("should warn and continue startup when the renderer probe fails in the default mode")
    void shouldWarnAndContinue_whenProbeFailsDefaultMode() throws PDFGenerationException {
        properties.remove(STARTUP_CHECK_PROPERTY);
        EFormBrowserPdfService service = mock(EFormBrowserPdfService.class);
        doThrow(new PDFGenerationException("Unable to start the headless Chromium renderer for eForms."))
                .when(service).verifyRendererReady();

        EFormBrowserRendererStartupValidator validator = new EFormBrowserRendererStartupValidator(service);

        try (LogCapture logs = LogCapture.forLogger(EFormBrowserRendererStartupValidator.class)) {
            assertThatCode(validator::verifyRendererReadyAtStartup).doesNotThrowAnyException();
            assertStartupWarning(logs);
        }
        verify(service).verifyRendererReady();
    }

    @Test
    @DisplayName("should log and continue when the renderer probe fails in warn mode")
    void shouldContinue_whenProbeFailsWarnMode() throws PDFGenerationException {
        properties.setProperty(STARTUP_CHECK_PROPERTY, "warn");
        EFormBrowserPdfService service = mock(EFormBrowserPdfService.class);
        doThrow(new PDFGenerationException("renderer down")).when(service).verifyRendererReady();

        EFormBrowserRendererStartupValidator validator = new EFormBrowserRendererStartupValidator(service);

        assertThatCode(validator::verifyRendererReadyAtStartup).doesNotThrowAnyException();
        verify(service).verifyRendererReady();
    }

    @Test
    @DisplayName("should keep required as a legacy advisory mode")
    void shouldWarnAndContinue_whenProbeFailsRequiredMode() throws PDFGenerationException {
        properties.setProperty(STARTUP_CHECK_PROPERTY, "required");
        EFormBrowserPdfService service = mock(EFormBrowserPdfService.class);
        doThrow(new PDFGenerationException("renderer down")).when(service).verifyRendererReady();

        EFormBrowserRendererStartupValidator validator = new EFormBrowserRendererStartupValidator(service);

        try (LogCapture logs = LogCapture.forLogger(EFormBrowserRendererStartupValidator.class)) {
            assertThatCode(validator::verifyRendererReadyAtStartup).doesNotThrowAnyException();
            assertStartupWarning(logs);
        }
        verify(service).verifyRendererReady();
    }

    @Test
    @DisplayName("should skip the probe entirely in off mode")
    void shouldSkipProbe_forOffMode() {
        properties.setProperty(STARTUP_CHECK_PROPERTY, "off");
        EFormBrowserPdfService service = mock(EFormBrowserPdfService.class);

        EFormBrowserRendererStartupValidator validator = new EFormBrowserRendererStartupValidator(service);

        assertThatCode(validator::verifyRendererReadyAtStartup).doesNotThrowAnyException();
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("should warn and continue startup when the configured base URL is invalid")
    void shouldWarnAndContinue_whenConfiguredBaseUrlInvalid() throws PDFGenerationException {
        properties.remove(STARTUP_CHECK_PROPERTY);
        EFormBrowserPdfService service = mock(EFormBrowserPdfService.class);
        doThrow(new PDFGenerationException("The configured eform_pdf_browser_base_url is invalid"))
                .when(service).verifyConfiguredBaseUrl();

        EFormBrowserRendererStartupValidator validator = new EFormBrowserRendererStartupValidator(service);

        try (LogCapture logs = LogCapture.forLogger(EFormBrowserRendererStartupValidator.class)) {
            assertThatCode(validator::verifyRendererReadyAtStartup).doesNotThrowAnyException();
            assertStartupWarning(logs);
        }
        // Config validation runs before the (slower) browser launch probe.
        verify(service, never()).verifyRendererReady();
    }

    @Test
    @DisplayName("should warn and continue startup when the probe leaks a runtime failure")
    void shouldWarnAndContinue_whenProbeLeaksRuntimeFailure() throws PDFGenerationException {
        properties.remove(STARTUP_CHECK_PROPERTY);
        EFormBrowserPdfService service = mock(EFormBrowserPdfService.class);
        doThrow(new IllegalStateException("unexpected Selenium failure")).when(service).verifyRendererReady();

        EFormBrowserRendererStartupValidator validator = new EFormBrowserRendererStartupValidator(service);

        try (LogCapture logs = LogCapture.forLogger(EFormBrowserRendererStartupValidator.class)) {
            assertThatCode(validator::verifyRendererReadyAtStartup).doesNotThrowAnyException();
            assertStartupWarning(logs);
        }
    }

    @Test
    @DisplayName("should pass startup when the probe succeeds")
    void shouldPassStartup_whenProbeSucceeds() throws PDFGenerationException {
        properties.remove(STARTUP_CHECK_PROPERTY);
        EFormBrowserPdfService service = mock(EFormBrowserPdfService.class);
        doNothing().when(service).verifyRendererReady();

        EFormBrowserRendererStartupValidator validator = new EFormBrowserRendererStartupValidator(service);

        assertThatCode(validator::verifyRendererReadyAtStartup).doesNotThrowAnyException();
        verify(service).verifyRendererReady();
    }

    private static void assertStartupWarning(LogCapture logs) {
        assertThat(logs.events()).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getMessage().getFormattedMessage())
                    .contains("startup will continue")
                    .contains("print/fax/archive will fail");
        });
    }
}
