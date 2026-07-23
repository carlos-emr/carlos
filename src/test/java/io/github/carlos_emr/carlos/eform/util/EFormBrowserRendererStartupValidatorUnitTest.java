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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Behaviour of the hard startup readiness gate for the eForm browser PDF renderer.
 *
 * <p>The renderer is the only path for saved-eForm fax/archive PDFs, so the deployment decision is
 * that CARLOS must refuse to start when the renderer cannot launch ({@code required} mode). These
 * tests pin the three modes plus the success path without launching a real browser: the probe on
 * {@link EFormBrowserPdfService} is mocked, and the {@code eform_pdf_browser_startup_check} property
 * is toggled through the {@link CarlosProperties} singleton and restored afterwards (same idiom as
 * {@code EFormBrowserPdfServiceUnitTest}).</p>
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
    @DisplayName("should fail webapp startup when the renderer probe fails in required mode")
    void shouldThrowIllegalState_whenProbeFailsRequiredMode() throws PDFGenerationException {
        // Default (property absent) resolves to "required": a failed probe must abort context init.
        properties.remove(STARTUP_CHECK_PROPERTY);
        EFormBrowserPdfService service = mock(EFormBrowserPdfService.class);
        doThrow(new PDFGenerationException("Unable to start the headless Chromium renderer for eForms."))
                .when(service).verifyRendererReady();

        EFormBrowserRendererStartupValidator validator = new EFormBrowserRendererStartupValidator(service);

        assertThatThrownBy(validator::verifyRendererReadyOrFailStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("readiness");
    }

    @Test
    @DisplayName("should log and continue when the renderer probe fails in warn mode")
    void shouldContinue_whenProbeFailsWarnMode() throws PDFGenerationException {
        properties.setProperty(STARTUP_CHECK_PROPERTY, "warn");
        EFormBrowserPdfService service = mock(EFormBrowserPdfService.class);
        doThrow(new PDFGenerationException("renderer down")).when(service).verifyRendererReady();

        EFormBrowserRendererStartupValidator validator = new EFormBrowserRendererStartupValidator(service);

        assertThatCode(validator::verifyRendererReadyOrFailStartup).doesNotThrowAnyException();
        verify(service).verifyRendererReady();
    }

    @Test
    @DisplayName("should skip the probe entirely in off mode")
    void shouldSkipProbe_forOffMode() {
        properties.setProperty(STARTUP_CHECK_PROPERTY, "off");
        EFormBrowserPdfService service = mock(EFormBrowserPdfService.class);

        EFormBrowserRendererStartupValidator validator = new EFormBrowserRendererStartupValidator(service);

        assertThatCode(validator::verifyRendererReadyOrFailStartup).doesNotThrowAnyException();
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("should pass startup silently when the probe succeeds")
    void shouldPassStartup_whenProbeSucceeds() throws PDFGenerationException {
        // Default (property absent) resolves to "required"; a clean probe must let startup proceed.
        properties.remove(STARTUP_CHECK_PROPERTY);
        EFormBrowserPdfService service = mock(EFormBrowserPdfService.class);
        doNothing().when(service).verifyRendererReady();

        EFormBrowserRendererStartupValidator validator = new EFormBrowserRendererStartupValidator(service);

        assertThatCode(validator::verifyRendererReadyOrFailStartup).doesNotThrowAnyException();
        verify(service).verifyRendererReady();
    }
}
