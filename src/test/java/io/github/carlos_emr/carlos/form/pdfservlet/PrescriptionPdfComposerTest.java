/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.form.pdfservlet;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;

/**
 * Unit tests for {@link PrescriptionPdfComposer}.
 */
@DisplayName("PrescriptionPdfComposer Unit Tests")
@Tag("unit")
@Tag("rx")
class PrescriptionPdfComposerTest {

    @Test
    @DisplayName("should reject missing signature image before rendering PDF")
    void shouldRejectMissingSignatureImage_beforeRenderingPdf() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Path missingSignature = Path.of(System.getProperty("java.io.tmpdir"), "missing-rx-signature.jpg");
        request.setParameter("imgFile", missingSignature.toString());

        PrescriptionPdfComposer composer = new PrescriptionPdfComposer();

        assertThatThrownBy(() -> composer.compose(request, new MockServletContext()))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("should reject signature image outside temp directory before rendering PDF")
    void shouldRejectSignatureImageOutsideTempDirectory_beforeRenderingPdf() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Path outsideTempDir = Path.of("target", "test-signatures").toAbsolutePath();
        Files.createDirectories(outsideTempDir);
        Path outsideTempSignature = outsideTempDir.resolve("signature.jpg");
        Files.writeString(outsideTempSignature, "not an image");
        request.setParameter("imgFile", outsideTempSignature.toString());

        PrescriptionPdfComposer composer = new PrescriptionPdfComposer();

        assertThatThrownBy(() -> composer.compose(request, new MockServletContext()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("should coalesce optional patient fields in page frame")
    void shouldCoalesceOptionalPatientFields_inPageFrame() throws Exception {
        PrescriptionPdfComposer composer = new PrescriptionPdfComposer();
        PrescriptionPdfComposer.EndPage endPage = composer.new EndPage(
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null);

        assertThat(readStringField(endPage, "patientName")).isEmpty();
        assertThat(readStringField(endPage, "patientDOB")).isEmpty();
        assertThat(readStringField(endPage, "rxDate")).isEmpty();
    }

    @Test
    @DisplayName("should skip QR code when script id is invalid")
    void shouldSkipQrCode_whenScriptIdIsInvalid() throws Exception {
        Method parseScriptId = PrescriptionPdfComposer.class.getDeclaredMethod("parseScriptId", String.class);
        parseScriptId.setAccessible(true);
        PrescriptionPdfComposer composer = new PrescriptionPdfComposer();

        assertThat(parseScriptId.invoke(composer, "not-a-number")).isNull();
        assertThat(parseScriptId.invoke(composer, "9999999999")).isNull();
        assertThat(parseScriptId.invoke(composer, new Object[] {null})).isNull();
        assertThat(parseScriptId.invoke(composer, "123")).isEqualTo(123);
    }

    private String readStringField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(target);
    }
}
