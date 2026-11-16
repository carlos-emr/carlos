/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.report.data;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises detached metadata, null and whitespace compatibility, budgets, and cleanup on failure. */
@Tag("unit")
class ConfiguredReportQueryUnitTest {
    @Test
    void shouldPreserveLabelsNullsAndRawText_whenDetachingReportRows() throws Exception {
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(2);
        when(metadata.getColumnName(1)).thenReturn("demographic_no");
        when(metadata.getColumnLabel(1)).thenReturn("patient");
        when(metadata.getTableName(1)).thenReturn("demographic");
        when(metadata.getColumnName(2)).thenReturn("value");
        when(metadata.getColumnLabel(2)).thenReturn("value");
        when(rows.next()).thenReturn(true, false);
        when(rows.getString(1)).thenReturn(" 42 ");
        when(rows.getString(2)).thenReturn(null);
        try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), eq(10_001), anyLong(), any(Object[].class))).thenReturn(rows);
            var result = ConfiguredReportQuery.read("select demographic_no, value from demographic where demographic_no=?", 42);
            assertThat(result.columnNames()).containsExactly("demographic_no", "value");
            var row = result.rows().getFirst();
            assertThat(row.value("patient")).isEqualTo(" 42 ");
            assertThat(row.integer("demographic.demographic_no")).isEqualTo(42);
            assertThat(row.text("PATIENT")).isEqualTo("42");
            assertThat(row.value("value")).isNull();
            assertThat(row.text("value")).isEmpty();
            assertThatThrownBy(() -> row.value("missing")).isInstanceOf(SQLException.class);
            var columns = row.columns();
            assertThatThrownBy(() -> columns.put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        }
        verify(rows).close();
    }

    @Test
    void shouldPreserveFirstSqlNull_whenColumnNamesAndLabelsRepeat() throws Exception {
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(2);
        when(metadata.getColumnName(1)).thenReturn("value");
        when(metadata.getColumnName(2)).thenReturn("VALUE");
        when(metadata.getColumnLabel(1)).thenReturn("shared");
        when(metadata.getColumnLabel(2)).thenReturn("SHARED");
        when(metadata.getTableName(1)).thenReturn("report");
        when(metadata.getTableName(2)).thenReturn("REPORT");
        when(rows.next()).thenReturn(true, false);
        when(rows.getString(1)).thenReturn(null);
        when(rows.getString(2)).thenReturn("later");
        try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), eq(10_001), anyLong(), any(Object[].class))).thenReturn(rows);
            var row = ConfiguredReportQuery.read("select value, value from report").rows().getFirst();
            assertThat(row.value("shared")).isNull();
            assertThat(row.value("value")).isNull();
            assertThat(row.value("report.value")).isNull();
            assertThat(row.values()).containsExactly(null, "later");
        }
        verify(rows).close();
    }

    @Test
    void shouldCloseResultsAndRejectPartialOutput_whenRowBudgetIsExceeded() throws Exception {
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(0);
        when(rows.next()).thenReturn(true);
        try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), eq(10_001), anyLong(), any(Object[].class))).thenReturn(rows);
            assertThatThrownBy(() -> ConfiguredReportQuery.read("select 1"))
                    .isInstanceOfSatisfying(ConfiguredReportQuery.LimitExceededException.class, e -> {
                        assertThat(e.limit()).isEqualTo(ConfiguredReportQuery.Limit.ROWS);
                        assertThat(e.value()).isEqualTo(10_000);
                        // The refusal names its limit and tells the user an administrator can raise it.
                        assertThat((Throwable) e).hasMessage("This report has more than 10000 rows, the most a report may return. "
                                + "Narrow the report filters and run it again. An administrator can raise this limit "
                                + "with the setting configured_report.max_rows in carlos.properties.");
                    });
        }
        verify(rows).close();
    }

    @Test
    void shouldCloseResultsAndRejectPartialOutput_whenCharacterBudgetIsExceeded() throws Exception {
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnName(1)).thenReturn("value");
        when(metadata.getColumnLabel(1)).thenReturn("value");
        when(rows.next()).thenReturn(true);
        when(rows.getString(1)).thenReturn("a".repeat(ConfiguredReportQuery.MAX_OUTPUT_CHARACTERS + 1));
        try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), eq(10_001), anyLong(), any(Object[].class))).thenReturn(rows);
            assertThatThrownBy(() -> ConfiguredReportQuery.read("select value from report"))
                    .isInstanceOfSatisfying(ConfiguredReportQuery.LimitExceededException.class, e -> {
                        assertThat(e.limit()).isEqualTo(ConfiguredReportQuery.Limit.OUTPUT);
                        assertThat(e.value()).isEqualTo(ConfiguredReportQuery.MAX_OUTPUT_CHARACTERS);
                        assertThat((Throwable) e).hasMessageContaining("more than 5242880 characters of text")
                                .hasMessageContaining("configured_report.max_output_chars");
                    });
        }
        verify(rows).close();
    }

    @Test
    void shouldReadOnlyFirstMatch_forNumeratorOutput() throws Exception {
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnName(1)).thenReturn("value");
        when(metadata.getColumnLabel(1)).thenReturn("value");
        when(rows.next()).thenReturn(true, false); // The JDBC ceiling exposes exactly one row.
        when(rows.getString(1)).thenReturn("first match");
        try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), eq(1), anyLong(), any(Object[].class))).thenReturn(rows);
            assertThat(ConfiguredReportQuery.readFirst("select value from report").rows())
                    .singleElement().satisfies(row -> assertThat(row.values()).containsExactly("first match"));
        }
        verify(rows, times(2)).next();
        verify(rows).close();
    }

    @Test
    void shouldRejectInvalidOrOverflowingIdentifiers_withSqlExceptions() throws Exception {
        for (String value : List.of("2147483648", "-2147483649", "1.5", "not a number")) {
            var row = new ConfiguredReportQuery.Row(java.util.Map.of("id", value), List.of(value));
            assertThatThrownBy(() -> row.integer("id")).isInstanceOf(SQLException.class);
            assertThatThrownBy(row::firstInteger).isInstanceOf(SQLException.class);
        }
        var row = new ConfiguredReportQuery.Row(java.util.Map.of("id", "2147483647"), List.of("2147483647"));
        assertThat(row.firstInteger()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void shouldAllowLargeInternalIdentifierSets_beyondRenderedRowLimit() throws Exception {
        ResultSet rows = mock(ResultSet.class);
        var count = new java.util.concurrent.atomic.AtomicInteger();
        when(rows.next()).thenAnswer(call -> count.getAndIncrement() < 10_001);
        when(rows.getInt(1)).thenReturn(7);
        try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), eq(100_000), eq(30), any(Object[].class))).thenReturn(rows);
            assertThat(ConfiguredReportQuery.identifiers("select id from report")).hasSize(10_001).containsOnly(7);
        }
        verify(rows).close();
    }

    @Test
    void shouldRejectIdentifierOverflow_withoutReturningTruncatedPopulation() throws Exception {
        ResultSet rows = mock(ResultSet.class);
        when(rows.next()).thenReturn(true);
        when(rows.getInt(1)).thenReturn(7);
        try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), eq(100_000), eq(30), any(Object[].class))).thenReturn(rows);
            assertThatThrownBy(() -> ConfiguredReportQuery.identifiers("select id from report"))
                    .isInstanceOfSatisfying(ConfiguredReportQuery.LimitExceededException.class, e -> {
                        assertThat(e.limit()).isEqualTo(ConfiguredReportQuery.Limit.IDENTIFIERS);
                        assertThat(e.value()).isEqualTo(100_000);
                        assertThat((Throwable) e).hasMessageContaining("more than 100000 patients or records")
                                .hasMessageContaining("configured_report.max_identifier_rows");
                    });
        }
        verify(rows, times(ConfiguredReportQuery.MAX_IDENTIFIER_ROWS)).getInt(1);
        verify(rows).close();
    }

    @Test
    void shouldReadMetadataWithoutValues_whenFirstRowWouldExceedOutputBudget() throws Exception {
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(2);
        when(metadata.getColumnName(1)).thenReturn("demographic_no");
        when(metadata.getColumnName(2)).thenReturn("large_value");
        try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), eq(1), anyLong(), any(Object[].class))).thenReturn(rows);
            assertThat(ConfiguredReportQuery.columnNames("select * from formAlpha where 1=0"))
                    .containsExactly("demographic_no", "large_value");
        }
        verify(rows).next();
        verify(rows, never()).getString(anyInt());
        verify(rows, never()).getCharacterStream(anyInt());
        verify(rows).close();
    }

    @Test
    void shouldStopTextReadAtBudget_whenCellIsLargerThanOutputLimit() throws Exception {
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnName(1)).thenReturn("value");
        when(metadata.getColumnLabel(1)).thenReturn("value");
        when(metadata.getColumnType(1)).thenReturn(java.sql.Types.LONGVARCHAR);
        when(rows.next()).thenReturn(true);
        var consumed = new java.util.concurrent.atomic.AtomicInteger();
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        java.io.Reader endless = new java.io.Reader() {
            @Override
            public int read(char[] buffer, int offset, int length) {
                java.util.Arrays.fill(buffer, offset, offset + length, 'x');
                consumed.addAndGet(length);
                return length;
            }

            @Override
            public void close() { closed.set(true); }
        };
        when(rows.getCharacterStream(1)).thenReturn(endless);
        try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), eq(10_001), anyLong(), any(Object[].class))).thenReturn(rows);
            assertThatThrownBy(() -> ConfiguredReportQuery.read("select value from report"))
                    .isInstanceOfSatisfying(ConfiguredReportQuery.LimitExceededException.class,
                            e -> assertThat(e.limit()).isEqualTo(ConfiguredReportQuery.Limit.OUTPUT));
        }
        assertThat(consumed.get()).isLessThanOrEqualTo(ConfiguredReportQuery.MAX_OUTPUT_CHARACTERS + 2048);
        assertThat(closed).isTrue();
        verify(rows, never()).getString(anyInt());
        verify(rows).close();
    }

    @Test
    void shouldPreserveTextNullsAndWhitespace_withStreamedValues() throws Exception {
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnName(1)).thenReturn("value");
        when(metadata.getColumnLabel(1)).thenReturn("value");
        when(metadata.getColumnType(1)).thenReturn(java.sql.Types.VARCHAR);
        when(rows.next()).thenReturn(true, true, false);
        when(rows.getCharacterStream(1)).thenReturn(new java.io.StringReader("  value  "), (java.io.Reader) null);
        try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), eq(10_001), anyLong(), any(Object[].class))).thenReturn(rows);
            var result = ConfiguredReportQuery.read("select value from report");
            assertThat(result.rows().get(0).value("value")).isEqualTo("  value  ");
            assertThat(result.rows().get(1).value("value")).isNull();
        }
        verify(rows).close();
    }

    @Test
    void shouldSurfaceStreamFailure_insteadOfReturningPartialValues() throws Exception {
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnName(1)).thenReturn("value");
        when(metadata.getColumnType(1)).thenReturn(java.sql.Types.VARCHAR);
        when(rows.next()).thenReturn(true);
        var reader = mock(java.io.Reader.class);
        when(reader.read(any(char[].class))).thenThrow(new java.io.IOException("synthetic read failure"));
        when(rows.getCharacterStream(1)).thenReturn(reader);
        try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), eq(10_001), anyLong(), any(Object[].class))).thenReturn(rows);
            assertThatThrownBy(() -> ConfiguredReportQuery.read("select value from report"))
                    .isInstanceOf(SQLException.class).hasCauseInstanceOf(java.io.IOException.class);
        }
        verify(reader).close();
        verify(rows).close();
    }

    // ---- Configurable limits (configured_report.* in carlos.properties) ----

    private static final List<String> LIMIT_SETTINGS = List.of("configured_report.max_rows",
            "configured_report.max_identifier_rows", "configured_report.timeout_seconds",
            "configured_report.max_output_chars", "configured_report.max_csv_mb");

    @AfterEach
    void removeLimitSettings() {
        LIMIT_SETTINGS.forEach(CarlosProperties.getInstance()::remove);
    }

    private static void configure(String setting, String value) {
        CarlosProperties.getInstance().setProperty(setting, value);
    }

    @Test
    void shouldKeepPublishedLimits_whenNoSettingIsConfigured() {
        // The shipped carlos.properties only documents the settings, commented out.
        assertThat(ConfiguredReportQuery.maxRows()).isEqualTo(10_000);
        assertThat(ConfiguredReportQuery.maxIdentifierRows()).isEqualTo(100_000);
        assertThat(ConfiguredReportQuery.timeoutSeconds()).isEqualTo(30);
        assertThat(ConfiguredReportQuery.maxOutputCharacters()).isEqualTo(5 * 1024 * 1024);
        assertThat(ConfiguredReportQuery.Limit.CSV.configured()).isEqualTo(5);
    }

    @Test
    void shouldUseEachConfiguredLimit_atTheNextQuery() {
        configure("configured_report.max_rows", "25000");
        configure("configured_report.max_identifier_rows", "250000");
        configure("configured_report.timeout_seconds", "120");
        configure("configured_report.max_output_chars", "20000000");
        configure("configured_report.max_csv_mb", "20");
        assertThat(ConfiguredReportQuery.maxRows()).isEqualTo(25_000);
        assertThat(ConfiguredReportQuery.maxIdentifierRows()).isEqualTo(250_000);
        assertThat(ConfiguredReportQuery.timeoutSeconds()).isEqualTo(120);
        assertThat(ConfiguredReportQuery.maxOutputCharacters()).isEqualTo(20_000_000);
        assertThat(ConfiguredReportQuery.Limit.CSV.configured()).isEqualTo(20);
        // Read at use time: an edit (applied by a restart in production) changes the next query.
        configure("configured_report.max_rows", "30000");
        assertThat(ConfiguredReportQuery.maxRows()).isEqualTo(30_000);
    }

    @Test
    void shouldKeepDefaultsOrCapAtMaximums_whenSettingsAreNotAllowed() {
        configure("configured_report.max_rows", "many");
        configure("configured_report.max_identifier_rows", "0");
        configure("configured_report.timeout_seconds", "-30");
        configure("configured_report.max_output_chars", "999999999999");
        configure("configured_report.max_csv_mb", "51");
        assertThat(ConfiguredReportQuery.maxRows()).isEqualTo(10_000);
        assertThat(ConfiguredReportQuery.maxIdentifierRows()).isEqualTo(100_000);
        assertThat(ConfiguredReportQuery.timeoutSeconds()).isEqualTo(30);
        assertThat(ConfiguredReportQuery.maxOutputCharacters()).isEqualTo(50 * 1024 * 1024);
        assertThat(ConfiguredReportQuery.Limit.CSV.configured()).isEqualTo(50);
    }

    @Test
    void shouldReturnRowsPastTheDefault_whenRowLimitIsRaised() throws Exception {
        configure("configured_report.max_rows", "12000");
        configure("configured_report.timeout_seconds", "90");
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(0);
        var count = new java.util.concurrent.atomic.AtomicInteger();
        when(rows.next()).thenAnswer(call -> count.getAndIncrement() < 11_000);
        long before = System.nanoTime();
        var deadline = new java.util.concurrent.atomic.AtomicLong();
        try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            // One overflow row past the configured limit, and the configured deadline.
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), eq(12_001), anyLong(), any(Object[].class)))
                    .thenAnswer(call -> {
                        deadline.set(call.getArgument(2));
                        return rows;
                    });
            assertThat(ConfiguredReportQuery.read("select 1").rows()).hasSize(11_000);
        }
        // The configured 90 seconds, not the default 30 (the upper bound only allows for a slow machine).
        assertThat(deadline.get() - before).isBetween(java.util.concurrent.TimeUnit.SECONDS.toNanos(90),
                java.util.concurrent.TimeUnit.SECONDS.toNanos(150));
        verify(rows).close();
    }

    @Test
    void shouldRefuseAtTheLoweredRowLimit_withThatLimitInTheMessage() throws Exception {
        configure("configured_report.max_rows", "2");
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(0);
        when(rows.next()).thenReturn(true);
        try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), eq(3), anyLong(), any(Object[].class))).thenReturn(rows);
            assertThatThrownBy(() -> ConfiguredReportQuery.read("select 1"))
                    .isInstanceOfSatisfying(ConfiguredReportQuery.LimitExceededException.class, e -> {
                        assertThat(e.limit()).isEqualTo(ConfiguredReportQuery.Limit.ROWS);
                        assertThat(e.value()).isEqualTo(2);
                        assertThat((Throwable) e).hasMessageStartingWith("This report has more than 2 rows");
                    });
        }
        verify(rows).close();
    }

    @Test
    void shouldUseConfiguredIdentifierLimitAndTimeout_forInternalCohorts() throws Exception {
        configure("configured_report.max_identifier_rows", "150000");
        configure("configured_report.timeout_seconds", "60");
        ResultSet rows = mock(ResultSet.class);
        var count = new java.util.concurrent.atomic.AtomicInteger();
        when(rows.next()).thenAnswer(call -> count.getAndIncrement() < 120_000);
        when(rows.getInt(1)).thenReturn(7);
        try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), eq(150_000), eq(60), any(Object[].class))).thenReturn(rows);
            // 120,000 identifiers: refused under the default 100,000, complete under the raised limit.
            assertThat(ConfiguredReportQuery.identifiers("select id from report")).hasSize(120_000);
        }
        verify(rows).close();
    }

    @Test
    void shouldAcceptTextPastTheDefault_whenOutputLimitIsRaised() throws Exception {
        configure("configured_report.max_output_chars", "6000000");
        ResultSet rows = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnName(1)).thenReturn("value");
        when(metadata.getColumnLabel(1)).thenReturn("value");
        when(rows.next()).thenReturn(true, false);
        when(rows.getString(1)).thenReturn("a".repeat(ConfiguredReportQuery.MAX_OUTPUT_CHARACTERS + 1));
        try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), eq(10_001), anyLong(), any(Object[].class))).thenReturn(rows);
            assertThat(ConfiguredReportQuery.read("select value from report").rows().getFirst().value("value"))
                    .hasSize(ConfiguredReportQuery.MAX_OUTPUT_CHARACTERS + 1);
        }
        verify(rows).close();
    }

    @Test
    void shouldNameTheTimeLimit_whenTheQueryDeadlinePasses() throws Exception {
        configure("configured_report.timeout_seconds", "45");
        try (var jdbc = mockStatic(LegacyJdbcQuery.class)) {
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), anyInt(), anyLong(), any(Object[].class)))
                    .thenThrow(new java.sql.SQLTimeoutException("Report query deadline exceeded"));
            jdbc.when(() -> LegacyJdbcQuery.getBoundedReportResultSet(any(), anyInt(), anyInt(), any(Object[].class)))
                    .thenThrow(new java.sql.SQLTimeoutException("Report query deadline exceeded"));
            for (org.assertj.core.api.ThrowableAssert.ThrowingCallable query : List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
                    () -> ConfiguredReportQuery.read("select 1"),
                    () -> ConfiguredReportQuery.readFirst("select 1"),
                    () -> ConfiguredReportQuery.identifiers("select 1"),
                    () -> ConfiguredReportQuery.identifierColumn("select 1", "id"),
                    () -> ConfiguredReportQuery.columnNames("select 1"))) {
                assertThatThrownBy(query)
                        .isInstanceOfSatisfying(ConfiguredReportQuery.LimitExceededException.class, e -> {
                            assertThat(e.limit()).isEqualTo(ConfiguredReportQuery.Limit.TIME);
                            assertThat(e.value()).isEqualTo(45);
                            assertThat((Throwable) e).hasMessageStartingWith("This report did not finish within 45 seconds")
                                    .hasMessageContaining("configured_report.timeout_seconds")
                                    .hasCauseInstanceOf(java.sql.SQLTimeoutException.class);
                        });
            }
        }
    }

    @Test
    void shouldNotPromiseARaise_whenTheLimitIsAtItsMaximum() {
        assertThat(ConfiguredReportQuery.Limit.ROWS.raisableSetting(10_000)).isEqualTo("configured_report.max_rows");
        assertThat(ConfiguredReportQuery.Limit.ROWS.raisableSetting(100_000)).isNull();
        assertThat(ConfiguredReportQuery.Limit.ROWS.english(100_000)).doesNotContain("administrator");
        assertThat(ConfiguredReportQuery.Limit.CSV.english(5))
                .startsWith("This report is larger than 5 MB, the most a report download may hold.")
                .endsWith("with the setting configured_report.max_csv_mb in carlos.properties.");
    }

}
