/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.ClinicalReports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Hashtable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Clinical report SQL JDBC boundary")
class ClinicalReportSqlJdbcUnitTest {

    @Test
    @DisplayName("should reject unsafe numerator SQL before JDBC")
    void shouldRejectUnsafeNumeratorSql_beforeJdbc() {
        SQLNumerator numerator = new SQLNumerator();
        numerator.setSQL("select count(*) as count from demographic where demographic_no=${demographic_no}; drop table provider");

        assertThatThrownBy(() -> numerator.evaluate(null, "100"))
                .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(java.sql.SQLException.class);
    }

    @Test
    @DisplayName("should reject unsafe denominator SQL before JDBC")
    void shouldRejectUnsafeDenominatorSql_beforeJdbc() {
        SQLDenominator denominator = new SQLDenominator();
        denominator.setSQL("select demographic_no from demographic where provider_no=${provider_no}; drop table provider");
        Hashtable<String, String> replaceableValues = new Hashtable<>();
        replaceableValues.put("provider_no", "100");
        denominator.setReplaceableValues(replaceableValues);

        assertThatThrownBy(denominator::getDenominatorList)
                .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(java.sql.SQLException.class);
    }

    @Test
    @DisplayName("should parameterize numerator placeholders with regex metacharacters")
    void shouldParameterizeNumeratorPlaceholders_whenKeyContainsRegexMetacharacters() {
        SQLNumerator numerator = new SQLNumerator();
        numerator.processString = "demo+no";
        numerator.setSQL("select count(*) as count from demographic where demographic_no=${demo+no}");

        assertThat(numerator.parameterizedSqlForProcessString())
                .isEqualTo("select count(*) as count from demographic where demographic_no=?");
    }

    @Test
    void shouldFailVisibly_whenDenominatorTimesOut() throws Exception {
        SQLDenominator denominator = new SQLDenominator();
        denominator.setSQL("select demographic_no from demographic");
        try (var queries = org.mockito.Mockito.mockStatic(io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.class)) {
            queries.when(() -> io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.identifierColumn(
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                    .thenThrow(new java.sql.SQLTimeoutException("synthetic timeout"));
            assertThatThrownBy(denominator::getDenominatorList).isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(java.sql.SQLTimeoutException.class);
        }
    }

    @Test
    void shouldUseOnlyFirstMatchingRow_forNumeratorOutput() throws Exception {
        SQLNumerator numerator = new SQLNumerator();
        numerator.setSQL("select demographic_no from demographic where demographic_no=${demographic_no}");
        numerator.parseOutputFields("demographic_no");
        var row = new io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.Row(
                java.util.Map.of("demographic_no", "42"), java.util.List.of("42"));
        var result = new io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.Result(
                java.util.List.of("demographic_no"), java.util.List.of(row));
        try (var queries = org.mockito.Mockito.mockStatic(io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.class)) {
            queries.when(() -> io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.readFirst(
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(result);
            assertThat(numerator.evaluate(null, "42")).isTrue();
            assertThat(numerator.getOutputValues()).containsEntry("demographic_no", "42");
            queries.verify(() -> io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.readFirst(
                    "select demographic_no from demographic where demographic_no=?", "42"));
        }
    }

    @Test
    void shouldAbortInsteadOfExcludingPatient_whenNumeratorTimesOut() throws Exception {
        SQLNumerator numerator = new SQLNumerator();
        numerator.setSQL("select demographic_no from demographic");
        try (var queries = org.mockito.Mockito.mockStatic(io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.class)) {
            queries.when(() -> io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.readFirst(
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object[].class)))
                    .thenThrow(new java.sql.SQLTimeoutException("synthetic timeout"));
            assertThatThrownBy(() -> numerator.evaluate(null, "42")).isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(java.sql.SQLTimeoutException.class);
        }
    }

    @Test
    void shouldUseIdentifierBudgetAndSelectedColumn_forLargeClinicalPopulations() throws Exception {
        SQLDenominator denominator = new SQLDenominator();
        denominator.setSQL("select unrelated, demographic_no as patient from demographic where provider_no=${provider_no}");
        denominator.setResultString("patient");
        Hashtable<String, String> parameters = new Hashtable<>();
        parameters.put("provider_no", "42");
        denominator.setReplaceableValues(parameters);
        var rows = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        var metadata = org.mockito.Mockito.mock(java.sql.ResultSetMetaData.class);
        org.mockito.Mockito.when(rows.getMetaData()).thenReturn(metadata);
        org.mockito.Mockito.when(rows.findColumn("patient")).thenReturn(2);
        org.mockito.Mockito.when(metadata.getColumnType(2)).thenReturn(java.sql.Types.INTEGER);
        var count = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.when(rows.next()).thenAnswer(call -> count.getAndIncrement() < 10_001);
        org.mockito.Mockito.when(rows.getString(2)).thenReturn("  42  ").thenReturn(null).thenReturn("43");
        try (var jdbc = org.mockito.Mockito.mockStatic(io.github.carlos_emr.carlos.db.LegacyJdbcQuery.class)) {
            jdbc.when(() -> io.github.carlos_emr.carlos.db.LegacyJdbcQuery.getBoundedReportResultSet(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100_000),
                    org.mockito.ArgumentMatchers.eq(30), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(rows);
            assertThat(denominator.getDenominatorList()).hasSize(10_001).startsWith("42", "", "43");
            jdbc.verify(() -> io.github.carlos_emr.carlos.db.LegacyJdbcQuery.trustedReportSelectSql(
                    "select unrelated, demographic_no as patient from demographic where provider_no=?"));
            jdbc.verify(() -> io.github.carlos_emr.carlos.db.LegacyJdbcQuery.getBoundedReportResultSet(
                    null, 100_000, 30, new Object[]{"42"}));
        }
        org.mockito.Mockito.verify(rows, org.mockito.Mockito.never()).getString(1);
        org.mockito.Mockito.verify(rows).close();
    }

    @Test
    void shouldRejectOversizedClinicalPopulations_insteadOfTruncatingIds() throws Exception {
        SQLDenominator denominator = new SQLDenominator();
        denominator.setSQL("select demographic_no from demographic");
        var rows = org.mockito.Mockito.mock(java.sql.ResultSet.class);
        var metadata = org.mockito.Mockito.mock(java.sql.ResultSetMetaData.class);
        org.mockito.Mockito.when(rows.getMetaData()).thenReturn(metadata);
        org.mockito.Mockito.when(rows.findColumn("demographic_no")).thenReturn(1);
        org.mockito.Mockito.when(metadata.getColumnType(1)).thenReturn(java.sql.Types.INTEGER);
        org.mockito.Mockito.when(rows.next()).thenReturn(true);
        org.mockito.Mockito.when(rows.getString(1)).thenReturn("42");
        try (var jdbc = org.mockito.Mockito.mockStatic(io.github.carlos_emr.carlos.db.LegacyJdbcQuery.class)) {
            jdbc.when(() -> io.github.carlos_emr.carlos.db.LegacyJdbcQuery.getBoundedReportResultSet(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100_000),
                    org.mockito.ArgumentMatchers.eq(30), org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(rows);
            assertThatThrownBy(denominator::getDenominatorList).isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.LimitExceededException.class);
        }
        org.mockito.Mockito.verify(rows).close();
    }

}
