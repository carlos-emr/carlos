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
 * Unit tests for {@link MiscUtils} general-purpose utility methods.
 *
 * <p>Tests the pure-logic static methods that don't depend on external services.</p>
 *
 * @since 2026-03-31
 */
@DisplayName("MiscUtils Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class MiscUtilsUnitTest {

    @Test
    @DisplayName("should return non-null logger")
    void shouldReturnNonNullLogger() {
        assertThat(MiscUtils.getLogger()).isNotNull();
    }

    @Test
    @DisplayName("should sanitize filename removing path separators")
    void shouldSanitizeFilename_removingPathSeparators() {
        String result = MiscUtils.sanitizeFileName("../../../etc/passwd");
        assertThat(result).doesNotContain("..");
        assertThat(result).doesNotContain("/");
    }

    @Test
    @DisplayName("should sanitize filename preserving extension")
    void shouldSanitizeFilename_preservingExtension() {
        String result = MiscUtils.sanitizeFileName("document.pdf");
        assertThat(result).contains(".pdf");
    }

    @Test
    @DisplayName("should handle null filename in sanitize")
    void shouldHandleNullFilename() {
        String result = MiscUtils.sanitizeFileName(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("should sanitize filename removing special characters")
    void shouldSanitizeFilename_removingSpecialChars() {
        String result = MiscUtils.sanitizeFileName("file<>name|test.pdf");
        assertThat(result).doesNotContain("<");
        assertThat(result).doesNotContain(">");
        assertThat(result).doesNotContain("|");
    }
}
