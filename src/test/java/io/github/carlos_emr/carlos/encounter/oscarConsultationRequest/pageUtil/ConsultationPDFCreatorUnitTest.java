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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;

import org.openpdf.text.Image;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConsultationPDFCreator#resolveSignatureBytes} — the signature-byte precedence
 * that decides whether a print preview renders the non-mutating override or the persisted signature.
 */
@DisplayName("ConsultationPDFCreator signature byte resolution")
@Tag("unit")
class ConsultationPDFCreatorUnitTest {

    private static final byte[] OVERRIDE_BYTES = new byte[]{1, 2, 3};
    private static final byte[] STORED_BYTES = new byte[]{9, 8, 7};
    private static final int SIGNATURE_SOURCE_WIDTH = 500;
    private static final int SIGNATURE_SOURCE_HEIGHT = 150;
    private static final float SIGNATURE_MAX_WIDTH = 200f;
    private static final float SIGNATURE_MAX_HEIGHT = 60f;

    @Test
    @DisplayName("prefers the non-mutating override and never loads the stored signature")
    void shouldReturnOverrideBytes_whenOverridePresent() {
        DigitalSignatureManager mgr = mock(DigitalSignatureManager.class);

        byte[] result = ConsultationPDFCreator.resolveSignatureBytes(OVERRIDE_BYTES, "5", mgr);

        assertThat(result).containsExactly(OVERRIDE_BYTES);
        verifyNoInteractions(mgr);
    }

    @Test
    @DisplayName("falls back to the stored signature when there is no override")
    void shouldReturnStoredBytes_whenNoOverrideAndValidId() {
        DigitalSignatureManager mgr = mock(DigitalSignatureManager.class);
        DigitalSignature stored = new DigitalSignature();
        stored.setSignatureImage(STORED_BYTES);
        when(mgr.getDigitalSignature(5)).thenReturn(stored);

        byte[] result = ConsultationPDFCreator.resolveSignatureBytes(null, "5", mgr);

        assertThat(result).containsExactly(STORED_BYTES);
        verify(mgr).getDigitalSignature(5);
    }

    @Test
    @DisplayName("trims whitespace around the stored signature id before loading it")
    void shouldReturnStoredBytes_whenStoredIdHasIncidentalWhitespace() {
        DigitalSignatureManager mgr = mock(DigitalSignatureManager.class);
        DigitalSignature stored = new DigitalSignature();
        stored.setSignatureImage(STORED_BYTES);
        when(mgr.getDigitalSignature(5)).thenReturn(stored);

        byte[] result = ConsultationPDFCreator.resolveSignatureBytes(null, " 5 \n", mgr);

        assertThat(result).containsExactly(STORED_BYTES);
        verify(mgr).getDigitalSignature(5);
    }

    @Test
    @DisplayName("returns null when an empty override and a blank id leave nothing to render")
    void shouldReturnNull_whenNoOverrideAndBlankId() {
        DigitalSignatureManager mgr = mock(DigitalSignatureManager.class);

        byte[] result = ConsultationPDFCreator.resolveSignatureBytes(new byte[0], "", mgr);

        assertThat(result).isNull();
        verify(mgr, never()).getDigitalSignature(anyInt());
    }

    @Test
    @DisplayName("does not render a stored signature when preview rendering suppresses signatures")
    void shouldNotRenderSignature_whenSuppressed() {
        assertThat(ConsultationPDFCreator.shouldRenderSignature(true, null, "5")).isFalse();
        assertThat(ConsultationPDFCreator.shouldRenderSignature(true, OVERRIDE_BYTES, "")).isFalse();
    }

    @Test
    @DisplayName("renders either override bytes or a stored signature id when not suppressed")
    void shouldRenderSignature_whenOverrideOrStoredIdPresentAndNotSuppressed() {
        assertThat(ConsultationPDFCreator.shouldRenderSignature(false, OVERRIDE_BYTES, "")).isTrue();
        assertThat(ConsultationPDFCreator.shouldRenderSignature(false, null, "5")).isTrue();
        assertThat(ConsultationPDFCreator.shouldRenderSignature(false, new byte[0], "")).isFalse();
    }

    @Test
    @DisplayName("returns null when the stored signature id contains only whitespace")
    void shouldReturnNull_whenStoredIdWhitespaceOnly() {
        DigitalSignatureManager mgr = mock(DigitalSignatureManager.class);

        byte[] result = ConsultationPDFCreator.resolveSignatureBytes(null, " \n\t ", mgr);

        assertThat(result).isNull();
        verify(mgr, never()).getDigitalSignature(anyInt());
    }

    @Test
    @DisplayName("returns null when the stored signature id is not numeric")
    void shouldReturnNull_whenStoredIdNonNumeric() {
        DigitalSignatureManager mgr = mock(DigitalSignatureManager.class);

        byte[] result = ConsultationPDFCreator.resolveSignatureBytes(null, "abc", mgr);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("sanitizes malformed signature id before logging")
    void shouldSanitizeSignatureImageId_whenMalformedIdLogged() {
        DigitalSignatureManager mgr = mock(DigitalSignatureManager.class);

        try (LogCapture capture = LogCapture.forLogger(ConsultationPDFCreator.class)) {
            byte[] result = ConsultationPDFCreator.resolveSignatureBytes(null, "12\r\nforged-id", mgr);

            assertThat(result).isNull();
            String logged = capture.messages().stream()
                    .filter(message -> message.startsWith("Consultation signature id"))
                    .findFirst()
                    .orElseThrow();
            assertThat(logged).doesNotContain("\r").doesNotContain("\n");
            assertThat(logged).contains("12\\r\\nforged-id");
        }
    }

    @Test
    @DisplayName("formats appointment time without a null minute literal")
    void shouldFormatAppointmentTime_whenMinuteNull() {
        String result = ConsultationPDFCreator.formatAppointmentTime("9", null, "am");

        assertThat(result).isEqualTo("9 am");
    }

    @Test
    @DisplayName("formats appointment time with a minute separator")
    void shouldFormatAppointmentTime_whenMinutePresent() {
        String result = ConsultationPDFCreator.formatAppointmentTime("9", "05", "am");

        assertThat(result).isEqualTo("9:05 am");
    }

    @Test
    @DisplayName("formats blank appointment time when the hour is missing")
    void shouldFormatAppointmentTime_whenHourMissing() {
        String result = ConsultationPDFCreator.formatAppointmentTime(null, "05", "pm");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("formats blank appointment time when all parts are null")
    void shouldFormatAppointmentTime_whenAllPartsNull() {
        String result = ConsultationPDFCreator.formatAppointmentTime(null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns null when the stored signature cannot be found")
    void shouldReturnNull_whenStoredSignatureMissing() {
        DigitalSignatureManager mgr = mock(DigitalSignatureManager.class);
        when(mgr.getDigitalSignature(5)).thenReturn(null);

        byte[] result = ConsultationPDFCreator.resolveSignatureBytes(null, "5", mgr);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("should scale signature image to the compact right-aligned footprint")
    void shouldScaleSignatureImage_toCompactFootprint() throws Exception {
        Image image = ConsultationPDFCreator.createScaledSignatureImage(
                createPng(SIGNATURE_SOURCE_WIDTH, SIGNATURE_SOURCE_HEIGHT));

        assertThat(image.getScaledWidth()).isEqualTo(SIGNATURE_MAX_WIDTH);
        assertThat(image.getScaledHeight()).isEqualTo(SIGNATURE_MAX_HEIGHT);
        assertThat(image.getBorder()).isZero();
    }

    @Test
    @DisplayName("should cap wide signature image at the compact footprint width")
    void shouldCapSignatureImage_whenSourceIsWide() throws Exception {
        Image image = ConsultationPDFCreator.createScaledSignatureImage(createPng(1000, SIGNATURE_SOURCE_HEIGHT));

        assertThat(image.getScaledWidth()).isEqualTo(SIGNATURE_MAX_WIDTH);
        assertThat(image.getScaledHeight()).isEqualTo(30f);
        assertThat(image.getScaledWidth()).isLessThanOrEqualTo(SIGNATURE_MAX_WIDTH);
        assertThat(image.getScaledHeight()).isLessThanOrEqualTo(SIGNATURE_MAX_HEIGHT);
    }

    @Test
    @DisplayName("should cap tall signature image at the compact footprint height")
    void shouldCapSignatureImage_whenSourceIsTall() throws Exception {
        Image image = ConsultationPDFCreator.createScaledSignatureImage(createPng(300, 600));

        assertThat(image.getScaledWidth()).isEqualTo(30f);
        assertThat(image.getScaledHeight()).isEqualTo(SIGNATURE_MAX_HEIGHT);
        assertThat(image.getScaledWidth()).isLessThanOrEqualTo(SIGNATURE_MAX_WIDTH);
        assertThat(image.getScaledHeight()).isLessThanOrEqualTo(SIGNATURE_MAX_HEIGHT);
    }

    @Test
    @DisplayName("should cap accepted provider stamp maximum dimensions within the compact footprint")
    void shouldCapSignatureImage_whenSourceMatchesProviderStampLimit() throws Exception {
        Image image = ConsultationPDFCreator.createScaledSignatureImage(createPng(1000, 400));

        assertThat(image.getScaledWidth()).isEqualTo(150f);
        assertThat(image.getScaledHeight()).isEqualTo(SIGNATURE_MAX_HEIGHT);
        assertThat(image.getScaledWidth()).isLessThanOrEqualTo(SIGNATURE_MAX_WIDTH);
        assertThat(image.getScaledHeight()).isLessThanOrEqualTo(SIGNATURE_MAX_HEIGHT);
    }

    @Test
    @DisplayName("should not upscale small signature image")
    void shouldNotUpscaleSignatureImage_whenSourceIsSmall() throws Exception {
        Image image = ConsultationPDFCreator.createScaledSignatureImage(createPng(100, 30));

        assertThat(image.getScaledWidth()).isEqualTo(40f);
        assertThat(image.getScaledHeight()).isEqualTo(12f);
    }

    private static byte[] createPng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
