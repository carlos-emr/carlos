/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link StringBuilderUtils} string search utility.
 *
 * @since 2026-03-31
 */
@DisplayName("StringBuilderUtils Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class StringBuilderUtilsUnitTest {

    @Test
    @DisplayName("should find target ignoring case")
    void shouldFindTarget_ignoringCase() {
        StringBuilder sb = new StringBuilder("Hello World");
        int idx = StringBuilderUtils.indexOfIgnoreCase(sb, "world", 0);
        assertThat(idx).isEqualTo(6);
    }

    @Test
    @DisplayName("should return -1 when target not found")
    void shouldReturnNegativeOne_whenNotFound() {
        StringBuilder sb = new StringBuilder("Hello World");
        int idx = StringBuilderUtils.indexOfIgnoreCase(sb, "xyz", 0);
        assertThat(idx).isEqualTo(-1);
    }

    @Test
    @DisplayName("should find target from specified start position")
    void shouldFindTarget_fromStartPosition() {
        StringBuilder sb = new StringBuilder("abcabc");
        int idx = StringBuilderUtils.indexOfIgnoreCase(sb, "abc", 1);
        assertThat(idx).isEqualTo(3);
    }

    @Test
    @DisplayName("should find target at beginning")
    void shouldFindTarget_atBeginning() {
        StringBuilder sb = new StringBuilder("Hello");
        int idx = StringBuilderUtils.indexOfIgnoreCase(sb, "HELLO", 0);
        assertThat(idx).isEqualTo(0);
    }
}
