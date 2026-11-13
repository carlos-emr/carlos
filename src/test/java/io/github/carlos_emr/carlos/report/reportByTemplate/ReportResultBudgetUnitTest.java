/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.io.Reader;
import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("Template report consumption budgets")
class ReportResultBudgetUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldStopAtRowSentinel_withoutReadingExtraCells() throws Exception {
        ResultSet rs = rows(ReportResultBudget.MAX_ROWS + 500, "a");
        assertThatThrownBy(() -> new ReportResultBudget().render(rs, null))
                .isInstanceOfSatisfying(ReportResultBudget.LimitExceededException.class, e -> {
                    assertThat(e.limit()).isEqualTo(ReportResultBudget.Limit.ROWS);
                    assertThat(e.value()).isEqualTo(ReportResultBudget.MAX_ROWS);
                });
        verify(rs, times(ReportResultBudget.MAX_ROWS + 1)).next();
        verify(rs, times(ReportResultBudget.MAX_ROWS)).getCharacterStream(1);
        verify(rs, never()).beforeFirst();
        verify(rs, never()).getString(1);
    }

    @Test
    void shouldShareRowBudget_acrossSequencedQueries() throws Exception {
        ReportResultBudget budget = new ReportResultBudget();
        budget.render(rows(ReportResultBudget.MAX_ROWS, "a"), null);
        assertThatThrownBy(() -> budget.render(rows(1, "b"), null))
                .isInstanceOf(ReportResultBudget.LimitExceededException.class);
    }

    @Test
    @DisplayName("should hold back the CSV and still stop the report at the HTML limit (#5849, D260)")
    void shouldHoldBackCsvButStillBoundHtml_whenEscapingGrowsBoth() throws Exception {
        // #5849 (D260): the CSV goes over 5 MiB first and is held back; the HTML, which escapes more, still
        // stops the report at its own 10 MiB.
        ResultSet rs = rows(1000, "é\"".repeat(16_000));
        assertThatThrownBy(() -> new ReportResultBudget().render(rs, null))
                .isInstanceOfSatisfying(ReportResultBudget.LimitExceededException.class, e -> {
                    assertThat(e.limit()).isEqualTo(ReportResultBudget.Limit.HTML);
                    assertThat(e.value()).isEqualTo(10);
                });
        verify(rs, atMost(100)).next();
    }

    @Test
    @DisplayName("should keep exactly 5 MiB of CSV and hold back one byte more (#5849, D260)")
    void shouldKeepExactlyFiveMiBCsv_andHoldBackOneByteMore() throws Exception {
        int fullRows = 79;
        int finalCellLength = SQLReporter.MAX_CSV_EXPORT_LENGTH - 3 - fullRows * (65_536 + 2) - 2;
        for (int excess : new int[]{0, 1}) {
            ResultSet rs = rows(fullRows + 1, "a");
            AtomicInteger cell = new AtomicInteger();
            when(rs.getCharacterStream(1)).thenAnswer(ignored -> new StringReader(
                    "a".repeat(cell.getAndIncrement() < fullRows ? 65_536 : finalCellLength + excess)));
            ReportResultBudget.Rendered result = new ReportResultBudget().render(rs, null);
            assertThat(result.html()).startsWith("<table");
            if (excess == 0) {
                assertThat(result.csvWithheld()).isFalse();
                assertThat(result.csv().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .hasSize(SQLReporter.MAX_CSV_EXPORT_LENGTH);
            } else {
                // #5849 (D260): the report is still shown; only its CSV is held back.
                assertThat(result.csvWithheld()).isTrue();
                assertThat(result.csv()).isEmpty();
            }
        }
    }

    @Test
    void shouldBoundEncodedHtml_beforeCsvLimit() throws Exception {
        ResultSet rs = rows(1000, "<".repeat(65_536));
        assertThatThrownBy(() -> new ReportResultBudget().render(rs, null))
                .isInstanceOfSatisfying(ReportResultBudget.LimitExceededException.class, e -> {
                    assertThat(e.limit()).isEqualTo(ReportResultBudget.Limit.HTML);
                    assertThat(e.value()).isEqualTo(10);
                });
        verify(rs, atMost(41)).next();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {java.sql.Types.VARCHAR, java.sql.Types.SQLXML,
            java.sql.Types.OTHER, java.sql.Types.JAVA_OBJECT, java.sql.Types.ARRAY, java.sql.Types.STRUCT})
    void shouldStopHugeCellReader_beforeMaterializingCell(int type) throws Exception {
        ResultSet rs = rows(1, "unused");
        when(rs.getMetaData().getColumnType(1)).thenReturn(type);
        AtomicInteger consumed = new AtomicInteger();
        Reader enormous = new Reader() {
            @Override
            public int read(char[] buffer, int offset, int length) {
                int remaining = ReportResultBudget.MAX_CELL_CHARACTERS + 4096 - consumed.get();
                if (remaining == 0) {
                    return -1;
                }
                int count = Math.min(length, remaining);
                java.util.Arrays.fill(buffer, offset, offset + count, 'a');
                consumed.addAndGet(count);
                return count;
            }

            @Override
            public void close() {
                // The synthetic reader has no external resources to release.
            }
        };
        when(rs.getCharacterStream(1)).thenReturn(enormous);
        assertThatThrownBy(() -> new ReportResultBudget().render(rs, null))
                .isInstanceOfSatisfying(ReportResultBudget.LimitExceededException.class, e -> {
                    assertThat(e.limit()).isEqualTo(ReportResultBudget.Limit.CELL);
                    assertThat(e.value()).isEqualTo(ReportResultBudget.MAX_CELL_CHARACTERS);
                });
        assertThat(consumed.get()).isLessThanOrEqualTo(ReportResultBudget.MAX_CELL_CHARACTERS + 2048);
        verify(rs, never()).getString(1);
    }

    @Test
    void shouldCheckDeadline_duringCellConsumption() throws Exception {
        AtomicLong time = new AtomicLong();
        ReportResultBudget budget = new ReportResultBudget(time::get);
        ResultSet rs = rows(1, "a");
        when(rs.getCharacterStream(1)).thenAnswer(ignored -> {
            time.set(TimeUnit.SECONDS.toNanos(ReportResultBudget.TIMEOUT_SECONDS));
            return new StringReader("a");
        });
        assertThatThrownBy(() -> budget.render(rs, null))
                .isInstanceOfSatisfying(ReportResultBudget.LimitExceededException.class, e -> {
                    assertThat(e.limit()).isEqualTo(ReportResultBudget.Limit.TIME);
                    assertThat(e.value()).isEqualTo(ReportResultBudget.TIMEOUT_SECONDS);
                });
        verify(rs, times(1)).next();
    }

    @Test
    void shouldBoundEmptyQueries_andUseRemainingDeadline() throws Exception {
        AtomicLong time = new AtomicLong();
        ReportResultBudget budget = new ReportResultBudget(time::get);
        time.set(TimeUnit.SECONDS.toNanos(12));
        assertThat(budget.deadlineNanos() - time.get()).isEqualTo(TimeUnit.SECONDS.toNanos(18));
        for (int query = 0; query < ReportResultBudget.MAX_QUERIES; query++) {
            budget.startQuery();
            assertThat(budget.render(rows(0, ""), null).csv()).isEmpty();
        }
        assertThatThrownBy(budget::startQuery)
                .isInstanceOfSatisfying(ReportResultBudget.LimitExceededException.class, e -> {
                    assertThat(e.limit()).isEqualTo(ReportResultBudget.Limit.QUERIES);
                    assertThat(e.value()).isEqualTo(ReportResultBudget.MAX_QUERIES);
                });
    }

    @Test
    void shouldRejectWideMetadata_beforeReadingCells() throws Exception {
        for (int count : new int[]{0, 1}) {
            ResultSet rs = rows(count, "a");
            when(rs.getMetaData().getColumnCount()).thenReturn(ReportResultBudget.MAX_COLUMNS + 1);
            assertThatThrownBy(() -> new ReportResultBudget().render(rs, null))
                    .isInstanceOfSatisfying(ReportResultBudget.LimitExceededException.class, e -> {
                    assertThat(e.limit()).isEqualTo(ReportResultBudget.Limit.COLUMNS);
                    assertThat(e.value()).isEqualTo(ReportResultBudget.MAX_COLUMNS);
                });
            verify(rs, never()).getCharacterStream(1);
        }
    }

    private static final int MIB = 1024 * 1024;

    @Test
    @DisplayName("should use the carlos.properties limits within their maximums (#5849, D261)")
    void shouldUseConfiguredLimits_withinTheirMaximums() {
        // #5849 (D261): administrators may raise or lower these in carlos.properties.
        Map<String, String> settings = Map.of(
                "report_by_template.max_rows", "50000",
                "report_by_template.timeout_seconds", "120",
                "report_by_template.max_html_mb", "20",
                "report_by_template.max_columns", "1000",
                "report_by_template.max_csv_mb", "2",
                "report_by_template.max_cell_chars", "200000");
        assertThat(ReportResultBudget.Limits.from(settings::get))
                .isEqualTo(new ReportResultBudget.Limits(50_000, 120, 20 * MIB, 1000, 2 * MIB, 200_000));
    }

    @Test
    @DisplayName("should cap values above a maximum and keep the default for values that are not allowed (#5849)")
    void shouldCapAtMaximumsAndKeepDefaults_forValuesOutOfRange() {
        Map<String, String> settings = Map.of(
                "report_by_template.max_rows", "999999",
                "report_by_template.timeout_seconds", "0",
                "report_by_template.max_html_mb", "a lot",
                "report_by_template.max_columns", " ",
                "report_by_template.max_csv_mb", "51");
        // Rows capped at 100,000; the zero, the word and the blank keep their defaults; the CSV limit is
        // capped at its 50 MiB maximum.
        assertThat(ReportResultBudget.Limits.from(settings::get))
                .isEqualTo(new ReportResultBudget.Limits(100_000, 30, 10 * MIB, 256, 50 * MIB, 65_536));
        assertThat(ReportResultBudget.Limits.from(key -> null)).isEqualTo(ReportResultBudget.Limits.defaults());
    }

    @Test
    @DisplayName("should let an administrator raise the CSV limit now that downloads are built on the server")
    void shouldRaiseCsvLimit_upToItsMaximum() {
        // Before the downloads were built on the server the CSV was posted back through the web firewall,
        // so the limit could only be lowered below 5 MiB.
        assertThat(ReportResultBudget.Limits.from(Map.of("report_by_template.max_csv_mb", "9")::get).maxCsvBytes())
                .isEqualTo(9 * MIB);
        assertThat(ReportResultBudget.Limits.from(Map.of("report_by_template.max_csv_mb", "50")::get).maxCsvBytes())
                .isEqualTo(50 * MIB);
        // The note above a held-back CSV names the setting only while it can still be raised.
        assertThat(ReportResultBudget.raisableCsvSetting(SQLReporter.MAX_CSV_EXPORT_LENGTH))
                .isEqualTo("report_by_template.max_csv_mb");
        assertThat(ReportResultBudget.raisableCsvSetting(49 * MIB)).isEqualTo("report_by_template.max_csv_mb");
        assertThat(ReportResultBudget.raisableCsvSetting(50 * MIB)).isNull();
    }

    @Test
    @DisplayName("should cap numbers too large for an int and keep the default for negative or decimal values (#5849)")
    void shouldCapOverflowingNumbers_andKeepDefaultsForNegativeOrDecimalValues() {
        Map<String, String> settings = Map.of(
                "report_by_template.max_rows", "99999999999999999999999",
                "report_by_template.timeout_seconds", "-5",
                "report_by_template.max_html_mb", "9999999999",
                "report_by_template.max_columns", "2.5",
                "report_by_template.max_csv_mb", "-99999999999999999999999",
                "report_by_template.max_cell_chars", "1048577");
        assertThat(ReportResultBudget.Limits.from(settings::get))
                .isEqualTo(new ReportResultBudget.Limits(100_000, 30, 50 * MIB, 256, 5 * MIB, 1024 * 1024));
    }

    @Test
    @DisplayName("should refuse at the configured row limit and name that limit (#5849)")
    void shouldRefuseAtTheConfiguredRowLimit_withThatLimitInTheMessage() throws Exception {
        var limits = new ReportResultBudget.Limits(5, 30, 10 * MIB, 256, 5 * MIB, 65_536);
        assertThatThrownBy(() -> new ReportResultBudget(limits).render(rows(6, "a"), null))
                .isInstanceOfSatisfying(ReportResultBudget.LimitExceededException.class, e -> {
                    assertThat(e.limit()).isEqualTo(ReportResultBudget.Limit.ROWS);
                    assertThat(e.value()).isEqualTo(5);
                });
        assertThat(ReportResultBudget.Limit.ROWS.english(5)).startsWith("This report has more than 5 rows");
    }

    @Test
    @DisplayName("should keep the limits that applied before any setting existed as the defaults")
    void shouldKeepPublishedLimits_asDefaults() {
        var published = new ReportResultBudget.Limits(10_000, 30, 10 * MIB, 256, 5 * MIB, 65_536);
        assertThat(ReportResultBudget.Limits.defaults()).isEqualTo(published);
        // The shipped carlos.properties only documents the settings (commented out), so a server
        // whose operator set nothing runs under the same limits as before.
        assertThat(ReportResultBudget.Limits.configured()).isEqualTo(published);
    }

    @Test
    @DisplayName("should apply the configured cell limit to each value and name it in the refusal")
    void shouldApplyConfiguredCellLimit_toEachValue() throws Exception {
        var lowered = new ReportResultBudget.Limits(10_000, 30, 10 * MIB, 256, 5 * MIB, 10);
        assertThatThrownBy(() -> new ReportResultBudget(lowered).render(rows(1, "a".repeat(11)), null))
                .isInstanceOfSatisfying(ReportResultBudget.LimitExceededException.class, e -> {
                    assertThat(e.limit()).isEqualTo(ReportResultBudget.Limit.CELL);
                    assertThat(e.value()).isEqualTo(10);
                    assertThat(e).hasMessageContaining("longer than 10 characters")
                            .hasMessageContaining("report_by_template.max_cell_chars");
                });
        // 70,000 characters: over the default 65,536, accepted once an operator raises the limit.
        String long70k = "a".repeat(70_000);
        assertThatThrownBy(() -> new ReportResultBudget().render(rows(1, long70k), null))
                .isInstanceOfSatisfying(ReportResultBudget.LimitExceededException.class, e -> {
                    assertThat(e.limit()).isEqualTo(ReportResultBudget.Limit.CELL);
                    assertThat(e.value()).isEqualTo(65_536);
                });
        var raised = new ReportResultBudget.Limits(10_000, 30, 10 * MIB, 256, 5 * MIB, 100_000);
        assertThat(new ReportResultBudget(raised).render(rows(1, long70k), null).html()).contains(long70k);
    }

    @Test
    @DisplayName("should raise the row, column, page and time limits when an operator configures them")
    void shouldLiftEachRefusal_whenItsSettingIsRaised() throws Exception {
        // 10,001 rows: one over the default, inside a raised row limit.
        Map<String, String> settings = Map.of("report_by_template.max_rows", "20000");
        assertThat(new ReportResultBudget(ReportResultBudget.Limits.from(settings::get))
                .render(rows(10_001, "a"), null).html()).startsWith("<table");
        // 257 columns: one over the default, inside a raised column limit.
        ResultSet wide = rows(1, "a");
        when(wide.getMetaData().getColumnCount()).thenReturn(257);
        when(wide.getMetaData().getColumnType(org.mockito.ArgumentMatchers.anyInt())).thenReturn(java.sql.Types.VARCHAR);
        when(wide.getMetaData().getColumnLabel(org.mockito.ArgumentMatchers.anyInt())).thenReturn("h");
        when(wide.getCharacterStream(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(ignored -> new StringReader("a"));
        assertThat(new ReportResultBudget(ReportResultBudget.Limits.from(
                Map.of("report_by_template.max_columns", "300")::get)).render(wide, null).html()).startsWith("<table");
        // About 13 MiB of page (200 rows of 64 KiB): over the default 10 MiB, inside a raised 20 MiB.
        String cell = "a".repeat(65_536);
        assertThatThrownBy(() -> new ReportResultBudget().render(rows(200, cell), null))
                .isInstanceOfSatisfying(ReportResultBudget.LimitExceededException.class,
                        e -> assertThat(e.limit()).isEqualTo(ReportResultBudget.Limit.HTML));
        ReportResultBudget.Rendered large = new ReportResultBudget(ReportResultBudget.Limits.from(
                Map.of("report_by_template.max_html_mb", "20")::get)).render(rows(200, cell), null);
        assertThat(large.html()).startsWith("<table");
        assertThat(large.csvWithheld()).isTrue();
        // 40 seconds on the clock: past the default 30, inside a raised 60.
        AtomicLong time = new AtomicLong();
        ReportResultBudget patient = new ReportResultBudget(ReportResultBudget.Limits.from(
                Map.of("report_by_template.timeout_seconds", "60")::get), time::get);
        time.set(TimeUnit.SECONDS.toNanos(40));
        assertThat(patient.render(rows(1, "a"), null).html()).startsWith("<table");
        assertThat(patient.deadlineNanos()).isEqualTo(TimeUnit.SECONDS.toNanos(60));
    }

    @Test
    @DisplayName("should say an administrator can raise a limit only while it is below its maximum")
    void shouldNameTheSettingToRaise_whileTheLimitIsBelowItsMaximum() {
        assertThat(ReportResultBudget.Limit.ROWS.english(10_000))
                .startsWith("This report has more than 10000 rows")
                .endsWith("An administrator can raise this limit with the setting report_by_template.max_rows in carlos.properties.");
        assertThat(ReportResultBudget.Limit.ROWS.raisableSetting(10_000)).isEqualTo("report_by_template.max_rows");
        assertThat(ReportResultBudget.Limit.COLUMNS.raisableSetting(256)).isEqualTo("report_by_template.max_columns");
        assertThat(ReportResultBudget.Limit.CELL.raisableSetting(65_536)).isEqualTo("report_by_template.max_cell_chars");
        assertThat(ReportResultBudget.Limit.HTML.raisableSetting(10)).isEqualTo("report_by_template.max_html_mb");
        assertThat(ReportResultBudget.Limit.TIME.raisableSetting(30)).isEqualTo("report_by_template.timeout_seconds");
        // At its maximum the page must not promise a raise that cannot be made; the query limit is fixed.
        assertThat(ReportResultBudget.Limit.ROWS.raisableSetting(100_000)).isNull();
        assertThat(ReportResultBudget.Limit.ROWS.english(100_000)).doesNotContain("administrator");
        assertThat(ReportResultBudget.Limit.HTML.raisableSetting(50)).isNull();
        assertThat(ReportResultBudget.Limit.TIME.raisableSetting(300)).isNull();
        assertThat(ReportResultBudget.Limit.QUERIES.raisableSetting(32)).isNull();
        assertThat(ReportResultBudget.Limit.QUERIES.english(32)).doesNotContain("administrator");
    }

    @Test
    @DisplayName("should apply the CSV limit to each query of a sequence and hold back only the large one (#5849)")
    void shouldApplyCsvLimitToEachQuery_andHoldBackOnlyTheLargeOne() throws Exception {
        // Each query's export form posts only its own CSV, so each query has the whole CSV limit.
        var limits = new ReportResultBudget.Limits(10_000, 30, 10 * MIB, 256, 100, 65_536);
        ReportResultBudget budget = new ReportResultBudget(limits);
        // "h" CRLF, then 80 characters and CRLF: 85 bytes each, so two of them would not fit one shared limit.
        for (String value : new String[]{"a".repeat(80), "b".repeat(80)}) {
            ReportResultBudget.Rendered kept = budget.render(rows(1, value), null);
            assertThat(kept.csvWithheld()).isFalse();
            assertThat(kept.csv()).isEqualTo("h\r\n" + value + "\r\n");
        }
        ReportResultBudget.Rendered large = budget.render(rows(10, "c".repeat(50)), null);
        assertThat(large.csvWithheld()).isTrue();
        assertThat(large.csv()).isEmpty();
        assertThat(large.html()).contains("c".repeat(50));
        // 95 bytes: a query after a held-back one still has the whole limit.
        ReportResultBudget.Rendered after = budget.render(rows(1, "d".repeat(90)), null);
        assertThat(after.csvWithheld()).isFalse();
        assertThat(after.csv()).hasSize(95);
    }

    private ResultSet rows(int count, String value) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(rs.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnType(1)).thenReturn(java.sql.Types.VARCHAR);
        when(metadata.getColumnLabel(1)).thenReturn("h");
        AtomicInteger row = new AtomicInteger();
        when(rs.next()).thenAnswer(ignored -> row.getAndIncrement() < count);
        when(rs.getCharacterStream(1)).thenAnswer(ignored -> new StringReader(value));
        return rs;
    }
}
