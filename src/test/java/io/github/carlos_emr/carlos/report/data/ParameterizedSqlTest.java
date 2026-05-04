/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ParameterizedSql} enforcing the constructor invariants:
 * non-null SQL and placeholder/param count match.
 */
@Tag("unit")
@Tag("report")
class ParameterizedSqlTest {

    @Test
    @DisplayName("should construct with matching placeholder and param counts")
    void shouldConstruct_whenPlaceholderAndParamCountsMatch() {
        ParameterizedSql psql = new ParameterizedSql(
                "select * from t where a=? and b=?", Arrays.asList("x", 42));

        assertThat(psql.getSql()).isEqualTo("select * from t where a=? and b=?");
        assertThat(psql.getParams()).containsExactly("x", 42);
    }

    @Test
    @DisplayName("should construct empty when SQL has no placeholders and params is empty")
    void shouldConstruct_withNoPlaceholdersAndNoParams() {
        ParameterizedSql psql = new ParameterizedSql("select 1", Collections.emptyList());

        assertThat(psql.getSql()).isEqualTo("select 1");
        assertThat(psql.getParams()).isEmpty();
    }

    @Test
    @DisplayName("should throw NullPointerException when SQL is null")
    void shouldThrow_whenSqlIsNull() {
        assertThatThrownBy(() -> new ParameterizedSql(null, Collections.emptyList()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when placeholders exceed params")
    void shouldThrow_whenPlaceholdersExceedParams() {
        assertThatThrownBy(() -> new ParameterizedSql(
                "select * from t where a=? and b=?", Arrays.asList("only-one")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Placeholder/param count mismatch");
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when params exceed placeholders")
    void shouldThrow_whenParamsExceedPlaceholders() {
        assertThatThrownBy(() -> new ParameterizedSql(
                "select 1", Arrays.asList("extra")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should treat null params list as empty")
    void shouldTreatNullParams_asEmpty() {
        ParameterizedSql psql = new ParameterizedSql("select 1", null);

        assertThat(psql.getParams()).isEmpty();
    }

    @Test
    @DisplayName("should return defensively-copied params, not reflecting external mutation")
    void shouldReturnDefensiveCopy_ofParams() {
        java.util.ArrayList<Object> src = new java.util.ArrayList<>();
        src.add("a");
        ParameterizedSql psql = new ParameterizedSql("select ?", src);

        src.add("mutated");

        assertThat(psql.getParams()).containsExactly("a");
    }

    @Test
    @DisplayName("should return unmodifiable view from getParams")
    void shouldReturnUnmodifiableView_fromGetParams() {
        ParameterizedSql psql = new ParameterizedSql("select ?", Arrays.asList("a"));

        List<Object> view = psql.getParams();

        assertThatThrownBy(() -> view.add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("should return fresh array from getParamsArray")
    void shouldReturnFreshArray_fromGetParamsArray() {
        ParameterizedSql psql = new ParameterizedSql("select ?", Arrays.asList("a"));

        Object[] arr1 = psql.getParamsArray();
        Object[] arr2 = psql.getParamsArray();

        assertThat(arr1).containsExactly("a");
        assertThat(arr2).containsExactly("a");
        assertThat(arr1).isNotSameAs(arr2);
    }
}
