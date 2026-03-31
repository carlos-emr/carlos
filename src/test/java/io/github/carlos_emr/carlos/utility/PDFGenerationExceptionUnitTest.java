/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PDFGenerationException} custom exception.
 *
 * @since 2026-03-31
 */
@DisplayName("PDFGenerationException Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class PDFGenerationExceptionUnitTest {

    @Test
    @DisplayName("should create with message")
    void shouldCreate_withMessage() {
        PDFGenerationException ex = new PDFGenerationException("PDF failed");
        assertThat(ex.getMessage()).isEqualTo("PDF failed");
    }

    @Test
    @DisplayName("should create with message and cause")
    void shouldCreate_withMessageAndCause() {
        RuntimeException cause = new RuntimeException("root cause");
        PDFGenerationException ex = new PDFGenerationException("PDF failed", cause);
        assertThat(ex.getMessage()).isEqualTo("PDF failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("should be a RuntimeException")
    void shouldBeRuntimeException() {
        assertThat(new PDFGenerationException("test")).isInstanceOf(RuntimeException.class);
    }
}
