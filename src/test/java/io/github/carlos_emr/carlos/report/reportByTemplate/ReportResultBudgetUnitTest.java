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
    void shouldUseConfiguredLimits_withinTheirMaximums() {
        // #5849 (D261): administrators may raise or lower these in carlos.properties.
        Map<String, String> settings = Map.of(
                "report_by_template.max_rows", "50000",
                "report_by_template.timeout_seconds", "120",
                "report_by_template.max_html_mb", "20",
                "report_by_template.max_columns", "1000",
                "report_by_template.max_csv_mb", "2");
        assertThat(ReportResultBudget.Limits.from(settings::get))
                .isEqualTo(new ReportResultBudget.Limits(50_000, 120, 20 * MIB, 1000, 2 * MIB));
    }

    @Test
    void shouldCapAtMaximumsAndKeepDefaults_forValuesOutOfRange() {
        Map<String, String> settings = Map.of(
                "report_by_template.max_rows", "999999",
                "report_by_template.timeout_seconds", "0",
                "report_by_template.max_html_mb", "a lot",
                "report_by_template.max_columns", " ",
                "report_by_template.max_csv_mb", "9");
        // Rows capped at 100,000; the zero, the word and the blank keep their defaults; the CSV limit
        // cannot be raised above its 5 MiB ceiling (D260).
        assertThat(ReportResultBudget.Limits.from(settings::get))
                .isEqualTo(new ReportResultBudget.Limits(100_000, 30, 10 * MIB, 256, 5 * MIB));
        assertThat(ReportResultBudget.Limits.from(key -> null)).isEqualTo(ReportResultBudget.Limits.defaults());
    }

    @Test
    void shouldRefuseAtTheConfiguredRowLimit_withThatLimitInTheMessage() throws Exception {
        var limits = new ReportResultBudget.Limits(5, 30, 10 * MIB, 256, 5 * MIB);
        assertThatThrownBy(() -> new ReportResultBudget(limits).render(rows(6, "a"), null))
                .isInstanceOfSatisfying(ReportResultBudget.LimitExceededException.class, e -> {
                    assertThat(e.limit()).isEqualTo(ReportResultBudget.Limit.ROWS);
                    assertThat(e.value()).isEqualTo(5);
                });
        assertThat(ReportResultBudget.Limit.ROWS.english(5)).startsWith("This report has more than 5 rows");
    }

    @Test
    void shouldHoldBackOnlyTheLargeCsv_andFreeItsBytesForTheNextQuery() throws Exception {
        var limits = new ReportResultBudget.Limits(10_000, 30, 10 * MIB, 256, 100);
        ReportResultBudget budget = new ReportResultBudget(limits);
        ReportResultBudget.Rendered large = budget.render(rows(10, "a".repeat(50)), null);
        assertThat(large.csvWithheld()).isTrue();
        assertThat(large.csv()).isEmpty();
        assertThat(large.html()).contains("a".repeat(50));
        ReportResultBudget.Rendered small = budget.render(rows(1, "b"), null);
        assertThat(small.csvWithheld()).isFalse();
        assertThat(small.csv()).contains("b");
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
