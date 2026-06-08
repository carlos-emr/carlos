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
 * Unit tests for {@link UtilDict} properties dictionary with defaults.
 *
 * @since 2026-03-31
 */
@DisplayName("UtilDict Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class UtilDictUnitTest {

    @Test
    @DisplayName("should return value when key exists")
    void shouldReturnValue_whenKeyExists() {
        UtilDict dict = new UtilDict();
        dict.setDef("name", "John");
        assertThat(dict.getDef("name")).isEqualTo("John");
    }

    @Test
    @DisplayName("should return empty string when key missing")
    void shouldReturnEmpty_whenKeyMissing() {
        UtilDict dict = new UtilDict();
        assertThat(dict.getDef("nonexistent")).isEmpty();
    }

    @Test
    @DisplayName("should return default when key missing")
    void shouldReturnDefault_whenKeyMissing() {
        UtilDict dict = new UtilDict();
        assertThat(dict.getDef("missing", "fallback")).isEqualTo("fallback");
    }

    @Test
    @DisplayName("should truncate with getShortDef")
    void shouldTruncate_withShortDef() {
        UtilDict dict = new UtilDict();
        dict.setDef("long", "abcdefghijklmnop");
        String result = dict.getShortDef("long", "", 5);
        assertThat(result.length()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("should set from string arrays")
    void shouldSet_fromStringArrays() {
        UtilDict dict = new UtilDict();
        dict.setDef(new String[]{"a", "b"}, new String[]{"1", "2"});
        assertThat(dict.getDef("a")).isEqualTo("1");
        assertThat(dict.getDef("b")).isEqualTo("2");
    }

    @Test
    @DisplayName("should set from 2D array")
    void shouldSet_from2DArray() {
        UtilDict dict = new UtilDict();
        dict.setDef(new String[][]{{"key1", "val1"}, {"key2", "val2"}});
        assertThat(dict.getDef("key1")).isEqualTo("val1");
        assertThat(dict.getDef("key2")).isEqualTo("val2");
    }
}
