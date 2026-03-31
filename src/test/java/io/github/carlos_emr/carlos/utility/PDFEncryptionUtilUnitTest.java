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
 * Unit tests for {@link PDFEncryptionUtil} PDF encryption constants and configuration.
 *
 * @since 2026-03-31
 */
@DisplayName("PDFEncryptionUtil Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility") @Tag("security")
class PDFEncryptionUtilUnitTest {

    @Test
    @DisplayName("should have encryptPDF method accessible")
    void shouldHaveEncryptPDFMethod() {
        // Verify the class loads and the static method exists
        assertThatCode(() -> PDFEncryptionUtil.class.getMethod("encryptPDF", java.nio.file.Path.class, String.class))
                .doesNotThrowAnyException();
    }
}
