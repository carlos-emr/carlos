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
package io.github.carlos_emr.carlos.report.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.report.data.ParameterizedSql;

/**
 * Unit tests for {@link RptFormQuery#getQueryWhereParameterized(java.util.List)}
 * — verifies that the combined WHERE clause preserves bind-parameter ordering
 * across multiple fragments. Ordering is security-relevant: a parent/child
 * reordering bug would bind the wrong value to the wrong placeholder.
 */
@Tag("unit")
@Tag("report")
class RptFormQueryTest {

    @Test
    @DisplayName("should accept report table names with schema")
    void shouldAcceptTableNames_withSchema() {
        RptFormQuery.validateTableName("schema.formBCAR");
    }

    @Test
    @DisplayName("should reject report table aliases because columns are qualified later")
    void shouldRejectTableAliases_whenColumnsAreQualifiedLater() {
        assertThatThrownBy(() -> RptFormQuery.validateTableName("formBCAR f"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid table name");
    }

    @Test
    @DisplayName("should reject injected report table names")
    void shouldRejectInjectedTableNames_whenTableNameContainsSql() {
        assertThatThrownBy(() -> RptFormQuery.validateTableName("formBCAR f, demographic d OR 1=1"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid table name");
    }

    @Test
    @DisplayName("should reject report table names with more than one dot")
    void shouldRejectTableNames_whenNameHasMultipleDots() {
        // The table value is used to qualify columns later, so anything beyond a
        // single schema-qualified name would produce invalid qualified-column SQL.
        assertThatThrownBy(() -> RptFormQuery.validateTableName("db.schema.formBCAR"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid table name");
    }

    @Test
    @DisplayName("should accept report table names with surrounding whitespace")
    void shouldAcceptTableNames_whenNameHasSurroundingWhitespace() {
        // The prior regex validated tableName.trim(), so padded legacy configs
        // must keep validating to avoid rejecting existing reports.
        RptFormQuery.validateTableName("  formBCAR  ");
        RptFormQuery.validateTableName("  schema.formBCAR  ");
    }

    @Test
    @DisplayName("should reject multi-dot report table names even when whitespace-padded")
    void shouldRejectTableNames_whenPaddedNameHasMultipleDots() {
        assertThatThrownBy(() -> RptFormQuery.validateTableName("  db.schema.formBCAR  "))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid table name");
    }

    @Test
    @DisplayName("should return empty ParameterizedSql when fragment list is empty")
    void shouldReturnEmpty_whenFragmentListEmpty() {
        ParameterizedSql result = RptFormQuery.getQueryWhereParameterized(Collections.emptyList());

        assertThat(result.getSql()).isEmpty();
        assertThat(result.getParams()).isEmpty();
    }

    @Test
    @DisplayName("should return single fragment unchanged when only one fragment provided")
    void shouldReturnSingleFragment_unchanged() {
        ParameterizedSql frag = new ParameterizedSql("a=?", Arrays.asList("alpha"));

        ParameterizedSql result = RptFormQuery.getQueryWhereParameterized(
                Collections.singletonList(frag));

        assertThat(result.getSql()).isEqualTo("a=?");
        assertThat(result.getParams()).containsExactly("alpha");
    }

    @Test
    @DisplayName("should join multiple fragments with AND and preserve param order across fragments")
    void shouldJoinFragments_andPreserveParamOrder() {
        ParameterizedSql f1 = new ParameterizedSql("a=?", Arrays.asList("alpha"));
        ParameterizedSql f2 = new ParameterizedSql("b=? and c=?", Arrays.asList("bravo", "charlie"));
        ParameterizedSql f3 = new ParameterizedSql("d=?", Arrays.asList("delta"));

        ParameterizedSql result = RptFormQuery.getQueryWhereParameterized(Arrays.asList(f1, f2, f3));

        // SQL: fragments joined by " and "
        assertThat(result.getSql()).isEqualTo("a=? and b=? and c=? and d=?");
        // Params: flattened left-to-right, fragment order preserved
        assertThat(result.getParams()).containsExactly("alpha", "bravo", "charlie", "delta");
    }

    @Test
    @DisplayName("should satisfy ParameterizedSql invariant: ?-count equals param count")
    void shouldSatisfyPlaceholderCountInvariant_whenJoining() {
        ParameterizedSql f1 = new ParameterizedSql("a=?", Arrays.asList(1));
        ParameterizedSql f2 = new ParameterizedSql("b in (?,?,?)", Arrays.asList(2, 3, 4));

        ParameterizedSql result = RptFormQuery.getQueryWhereParameterized(Arrays.asList(f1, f2));

        long qCount = result.getSql().chars().filter(c -> c == '?').count();
        assertThat(qCount).isEqualTo(result.getParams().size());
    }

    @Test
    @DisplayName("should preserve binding ordering for fragments containing mixed quoted and unquoted placeholders")
    void shouldPreserveOrdering_forMixedQuotedUnquoted() {
        // Simulates what getWhereValueClauseParameterized would produce:
        // 'last_name=?' gets the string, 'age>?' gets the numeric
        ParameterizedSql lastNameFrag = new ParameterizedSql(
                "demographic.last_name=?", Arrays.asList("Smith"));
        ParameterizedSql ageFrag = new ParameterizedSql(
                "demographic.age>?", Arrays.asList("42"));

        ParameterizedSql result = RptFormQuery.getQueryWhereParameterized(
                Arrays.asList(lastNameFrag, ageFrag));

        assertThat(result.getSql()).isEqualTo("demographic.last_name=? and demographic.age>?");
        List<Object> params = result.getParams();
        assertThat(params).hasSize(2);
        assertThat(params.get(0)).isEqualTo("Smith"); // parent placeholder first
        assertThat(params.get(1)).isEqualTo("42");    // child placeholder second
    }

    @Test
    @DisplayName("should skip blank fragments to avoid stray conjunctions")
    void shouldSkipBlankFragments_toAvoidStrayConjunctions() {
        ParameterizedSql blank = new ParameterizedSql("", Collections.emptyList());
        ParameterizedSql f1 = new ParameterizedSql("a=?", Arrays.asList("alpha"));
        ParameterizedSql f2 = new ParameterizedSql("b=?", Arrays.asList("bravo"));

        // Blank fragment in between non-empty ones — should be skipped
        ParameterizedSql result = RptFormQuery.getQueryWhereParameterized(
                Arrays.asList(blank, f1, blank, f2, blank));

        assertThat(result.getSql()).isEqualTo("a=? and b=?");
        assertThat(result.getParams()).containsExactly("alpha", "bravo");
    }

    @Test
    @DisplayName("should return empty when all fragments are blank")
    void shouldReturnEmpty_whenAllFragmentsBlank() {
        ParameterizedSql blank1 = new ParameterizedSql("", Collections.emptyList());
        ParameterizedSql blank2 = new ParameterizedSql("  ", Collections.emptyList());

        ParameterizedSql result = RptFormQuery.getQueryWhereParameterized(
                Arrays.asList(blank1, blank2));

        assertThat(result.getSql()).isEmpty();
        assertThat(result.getParams()).isEmpty();
    }
}
