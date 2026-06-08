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
 * Unit tests for {@link LabelValueBean} simple key-value pair.
 *
 * @since 2026-03-31
 */
@DisplayName("LabelValueBean Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class LabelValueBeanUnitTest {

    @Test
    @DisplayName("should create with label and value via constructor")
    void shouldCreate_withLabelAndValue() {
        LabelValueBean bean = new LabelValueBean("Name", "John");
        assertThat(bean.getLabel()).isEqualTo("Name");
        assertThat(bean.getValue()).isEqualTo("John");
    }

    @Test
    @DisplayName("should create empty with default constructor")
    void shouldCreateEmpty() {
        LabelValueBean bean = new LabelValueBean();
        assertThat(bean.getLabel()).isNull();
        assertThat(bean.getValue()).isNull();
    }

    @Test
    @DisplayName("should update label and value via setters")
    void shouldUpdate_viaSetters() {
        LabelValueBean bean = new LabelValueBean();
        bean.setLabel("Age");
        bean.setValue("30");
        assertThat(bean.getLabel()).isEqualTo("Age");
        assertThat(bean.getValue()).isEqualTo("30");
    }
}
