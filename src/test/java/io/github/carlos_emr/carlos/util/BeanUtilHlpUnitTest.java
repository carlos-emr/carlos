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
 * Unit tests for {@link BeanUtilHlp} bean property accessor.
 *
 * @since 2026-03-31
 */
@DisplayName("BeanUtilHlp Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class BeanUtilHlpUnitTest {

    static class TestBean {
        private String name = "John";
        public String getName() { return name; }
    }

    @Test
    @DisplayName("should get property value from bean")
    void shouldGetPropertyValue() {
        BeanUtilHlp helper = new BeanUtilHlp();
        String result = helper.getPropertyValue(new TestBean(), "name");
        assertThat(result).isEqualTo("John");
    }

    @Test
    @DisplayName("should return empty for non-existent property")
    void shouldReturnEmpty_forNonExistent() {
        BeanUtilHlp helper = new BeanUtilHlp();
        String result = helper.getPropertyValue(new TestBean(), "nonExistent");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should handle null bean gracefully")
    void shouldHandleNullBean() {
        BeanUtilHlp helper = new BeanUtilHlp();
        String result = helper.getPropertyValue(null, "name");
        assertThat(result).isEmpty();
    }
}
