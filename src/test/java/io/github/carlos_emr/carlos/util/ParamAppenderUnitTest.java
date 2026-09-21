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
 * Unit tests for {@link ParamAppender} query builder with named parameters.
 *
 * @since 2026-03-31
 */
@DisplayName("ParamAppender Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class ParamAppenderUnitTest {

    @Test
    @DisplayName("should build query with AND clause and parameter")
    void shouldBuildQuery_withAndClauseAndParam() {
        ParamAppender appender = new ParamAppender("SELECT * FROM patient");
        appender.and("name = :name", "name", "John");
        assertThat(appender.toString()).contains("name = :name");
        assertThat(appender.getParams()).containsEntry("name", "John");
    }

    @Test
    @DisplayName("should skip AND clause when param value is null")
    void shouldSkipAnd_whenParamNull() {
        ParamAppender appender = new ParamAppender("SELECT * FROM patient");
        appender.and("name = :name", "name", null);
        assertThat(appender.toString()).doesNotContain("name = :name");
        assertThat(appender.getParams()).isEmpty();
    }

    @Test
    @DisplayName("should build query with OR clause and parameter")
    void shouldBuildQuery_withOrClauseAndParam() {
        ParamAppender appender = new ParamAppender("SELECT * FROM patient WHERE 1=1");
        appender.or("status = :status", "status", "active");
        assertThat(appender.toString()).contains("status = :status");
        assertThat(appender.getParams()).containsEntry("status", "active");
    }

    @Test
    @DisplayName("should skip OR clause when param value is null")
    void shouldSkipOr_whenParamNull() {
        ParamAppender appender = new ParamAppender("SELECT * FROM patient");
        appender.or("status = :status", "status", null);
        assertThat(appender.getParams()).isEmpty();
    }

    @Test
    @DisplayName("should merge params from another appender")
    void shouldMergeParams_fromAnotherAppender() {
        ParamAppender a1 = new ParamAppender();
        a1.addParam("key1", "val1");
        ParamAppender a2 = new ParamAppender();
        a2.addParam("key2", "val2");
        a1.mergeParams(a2);
        assertThat(a1.getParams()).containsEntry("key1", "val1");
        assertThat(a1.getParams()).containsEntry("key2", "val2");
    }

    @Test
    @DisplayName("should add parameter manually")
    void shouldAddParam_manually() {
        ParamAppender appender = new ParamAppender();
        appender.addParam("id", 42);
        assertThat(appender.getParams()).containsEntry("id", 42);
    }
}
