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
 * Unit tests for {@link Appender} string builder with separator.
 *
 * @since 2026-03-31
 */
@DisplayName("Appender Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class AppenderUnitTest {

    @Test
    @DisplayName("should append strings with default separator")
    void shouldAppend_withDefaultSeparator() {
        Appender appender = new Appender();
        appender.append("hello");
        appender.append("world");
        assertThat(appender.toString()).isEqualTo("hello world");
    }

    @Test
    @DisplayName("should use custom separator")
    void shouldUseCustomSeparator() {
        Appender appender = new Appender(", ");
        appender.append("a");
        appender.append("b");
        appender.append("c");
        assertThat(appender.toString()).isEqualTo("a, b, c");
    }

    @Test
    @DisplayName("should initialize with content")
    void shouldInitialize_withContent() {
        Appender appender = new Appender(", ", "start");
        assertThat(appender.toString()).isEqualTo("start");
    }

    @Test
    @DisplayName("should return false and not append when null encountered in appendNonEmpty")
    void shouldReturnFalse_whenNullEncountered() {
        Appender appender = new Appender();
        boolean result = appender.appendNonEmpty("hello", null, "world");
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should append all non-null objects with appendNonEmpty")
    void shouldAppendAll_whenNoNulls() {
        Appender appender = new Appender();
        boolean result = appender.appendNonEmpty("hello", " ", "world");
        assertThat(result).isTrue();
        assertThat(appender.toString()).contains("hello world");
    }

    @Test
    @DisplayName("should report correct length")
    void shouldReportCorrectLength() {
        Appender appender = new Appender();
        assertThat(appender.length()).isZero();
        appender.append("test");
        assertThat(appender.length()).isGreaterThan(0);
    }

    @Test
    @DisplayName("should get and set separator")
    void shouldGetAndSetSeparator() {
        Appender appender = new Appender();
        assertThat(appender.getSeparator()).isEqualTo(Appender.DEFAULT_SEPARATOR);
        appender.setSeparator(";");
        assertThat(appender.getSeparator()).isEqualTo(";");
    }

    @Test
    @DisplayName("should append another Appender")
    void shouldAppendAnotherAppender() {
        Appender a1 = new Appender(", ");
        a1.append("a");
        Appender a2 = new Appender(", ");
        a2.append("b");
        a1.append(a2);
        assertThat(a1.toString()).contains("a");
        assertThat(a1.toString()).contains("b");
    }
}
