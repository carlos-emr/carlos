/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link QrCodeUtils} QR code generation utilities.
 *
 * @since 2026-03-31
 */
@DisplayName("QrCodeUtils Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class QrCodeUtilsUnitTest {

    @Test
    @DisplayName("should generate non-null PNG bytes for valid content")
    void shouldGeneratePng_forValidContent() throws Exception {
        byte[] png = QrCodeUtils.toSingleQrCodePng("https://example.com", ErrorCorrectionLevel.M, 8);
        assertThat(png).isNotNull();
        assertThat(png.length).isGreaterThan(0);
    }

    @Test
    @DisplayName("should generate PNG with different error correction levels")
    void shouldGeneratePng_withDifferentErrorLevels() throws Exception {
        byte[] pngL = QrCodeUtils.toSingleQrCodePng("test", ErrorCorrectionLevel.L, 4);
        byte[] pngH = QrCodeUtils.toSingleQrCodePng("test", ErrorCorrectionLevel.H, 4);
        assertThat(pngL).isNotNull();
        assertThat(pngH).isNotNull();
        // Higher error correction produces larger images
        assertThat(pngH.length).isGreaterThanOrEqualTo(pngL.length);
    }

    @Test
    @DisplayName("should generate valid PNG header bytes")
    void shouldGenerateValidPngHeader() throws Exception {
        byte[] png = QrCodeUtils.toSingleQrCodePng("test", ErrorCorrectionLevel.M, 4);
        // PNG magic number: 0x89 0x50 0x4E 0x47
        assertThat(png[0] & 0xFF).isEqualTo(0x89);
        assertThat(png[1]).isEqualTo((byte) 'P');
        assertThat(png[2]).isEqualTo((byte) 'N');
        assertThat(png[3]).isEqualTo((byte) 'G');
    }

    @Test
    @DisplayName("should handle empty content")
    void shouldHandleEmptyContent() throws Exception {
        byte[] png = QrCodeUtils.toSingleQrCodePng("", ErrorCorrectionLevel.M, 4);
        assertThat(png).isNotNull();
    }
}
