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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import io.github.carlos_emr.carlos.report.data.RptReportFilter;

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

    // ---- #5831: filter predicates come from the stored report filters, never the request ----

    /** Stored filter row in RptReportFilter#getNameList's six-column shape. */
    private static String[] storedFilter(String orderNo, String predicate, String dateFormat) {
        return new String[]{"description " + orderNo, predicate, "position", orderNo, "", dateFormat};
    }

    private static MockedConstruction<RptReportFilter> storedFilters(String[]... rows) {
        return mockConstruction(RptReportFilter.class, (filters, context) ->
                when(filters.getNameList(anyString(), anyInt())).thenReturn(new Vector<>(Arrays.asList(rows))));
    }

    @Test
    @DisplayName("should use the stored predicate and ignore SQL text posted in value_ fields")
    void shouldUseStoredPredicate_whenRequestPostsItsOwnSql() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("filter_2", "on");
        request.addParameter("value_2", "1=1 UNION SELECT user_name FROM security");
        request.addParameter("dateFormat_2", "dd/MM/yyyy");
        try (var filters = storedFilters(storedFilter("2", "demographic.sex=${sex1}", ""))) {
            List<RptFormQuery.CheckedFilter> checked = new RptFormQuery().checkedFilters("7", request);

            assertThat(checked).containsExactly(new RptFormQuery.CheckedFilter("demographic.sex=${sex1}", ""));
        }
    }

    @Test
    @DisplayName("should return only ticked filters, in stored order")
    void shouldReturnTickedFilters_inStoredOrder() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("filter_3", "on");
        request.addParameter("filter_1", "on");
        try (var _ = storedFilters(
                storedFilter("1", "a=${a1}", ""),
                storedFilter("2", "b=${b1}", ""),
                storedFilter("3", "c>=${startDate1}", "yyyy-MM-dd"))) {
            assertThat(new RptFormQuery().checkedFilters("7", request)).containsExactly(
                    new RptFormQuery.CheckedFilter("a=${a1}", ""),
                    new RptFormQuery.CheckedFilter("c>=${startDate1}", "yyyy-MM-dd"));
        }
    }

    @Test
    @DisplayName("should keep the first stored filter when two share an order number, as the browser does")
    void shouldKeepFirstStoredFilter_whenOrderNumbersRepeat() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("filter_1", "on");
        try (var _ = storedFilters(storedFilter("1", "first=1", ""), storedFilter("1", "second=1", ""))) {
            assertThat(new RptFormQuery().checkedFilters("7", request))
                    .containsExactly(new RptFormQuery.CheckedFilter("first=1", ""));
        }
    }

    @Test
    @DisplayName("should refuse a ticked filter the report does not define")
    void shouldRefuseTickedFilter_whenReportDoesNotDefineIt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("filter_1", "on");
        request.addParameter("filter_99", "on");
        request.addParameter("value_99", "1=1");
        try (var _ = storedFilters(storedFilter("1", "a=1", ""))) {
            assertThatThrownBy(() -> new RptFormQuery().checkedFilters("7", request))
                    .isInstanceOf(RptFormQuery.UnknownReportFilterException.class)
                    .hasMessageContaining("reload the report filter page");
        }
    }

    @Test
    @DisplayName("should return no filters when nothing is ticked")
    void shouldReturnNoFilters_whenNothingIsTicked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("value_1", "1=1 UNION SELECT 1");
        try (var filters = storedFilters(storedFilter("1", "a=1", ""))) {
            assertThat(new RptFormQuery().checkedFilters("7", request)).isEmpty();
        }
    }
}
