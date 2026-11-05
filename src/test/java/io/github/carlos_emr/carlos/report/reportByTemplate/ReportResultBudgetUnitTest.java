/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.io.Reader;
import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
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
class ReportResultBudgetUnitTest {
    @Test
    void shouldStopAtRowSentinel_withoutReadingExtraCells() throws Exception {
        ResultSet rs = rows(ReportResultBudget.MAX_ROWS + 500, "a");
        assertThatThrownBy(() -> new ReportResultBudget().render(rs, null))
                .isInstanceOf(ReportResultBudget.LimitExceededException.class)
                .hasMessage(SQLReporter.REPORT_LIMIT_MESSAGE);
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
    void shouldCountUtf8AndCsvEscaping_beforePublishingOversizedOutput() throws Exception {
        ResultSet rs = rows(1000, "é\"".repeat(16_000));
        assertThatThrownBy(() -> new ReportResultBudget().render(rs, null))
                .isInstanceOf(ReportResultBudget.LimitExceededException.class)
                .hasMessage(SQLReporter.CSV_EXPORT_LIMIT_MESSAGE);
        verify(rs, atMost(90)).next();
    }

    @Test
    void shouldAcceptExactlyFiveMiBCsv_andRejectOneByteMore() throws Exception {
        int fullRows = 79;
        int finalCellLength = SQLReporter.MAX_CSV_EXPORT_LENGTH - 3 - fullRows * (65_536 + 2) - 2;
        for (int excess : new int[]{0, 1}) {
            ResultSet rs = rows(fullRows + 1, "a");
            AtomicInteger cell = new AtomicInteger();
            when(rs.getCharacterStream(1)).thenAnswer(ignored -> new StringReader(
                    "a".repeat(cell.getAndIncrement() < fullRows ? 65_536 : finalCellLength + excess)));
            if (excess == 0) {
                String[] result = new ReportResultBudget().render(rs, null);
                assertThat(result[1].getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .hasSize(SQLReporter.MAX_CSV_EXPORT_LENGTH);
            } else {
                assertThatThrownBy(() -> new ReportResultBudget().render(rs, null))
                        .isInstanceOf(ReportResultBudget.LimitExceededException.class)
                        .hasMessage(SQLReporter.CSV_EXPORT_LIMIT_MESSAGE);
            }
        }
    }

    @Test
    void shouldBoundEncodedHtml_beforeCsvLimit() throws Exception {
        ResultSet rs = rows(1000, "<".repeat(65_536));
        assertThatThrownBy(() -> new ReportResultBudget().render(rs, null))
                .isInstanceOf(ReportResultBudget.LimitExceededException.class)
                .hasMessage(SQLReporter.REPORT_LIMIT_MESSAGE);
        verify(rs, atMost(41)).next();
    }

    @Test
    void shouldStopHugeCellReader_beforeMaterializingCell() throws Exception {
        ResultSet rs = rows(1, "unused");
        AtomicInteger consumed = new AtomicInteger();
        Reader enormous = new Reader() {
            @Override
            public int read(char[] buffer, int offset, int length) {
                java.util.Arrays.fill(buffer, offset, offset + length, 'a');
                consumed.addAndGet(length);
                return length;
            }

            @Override
            public void close() {
                // The synthetic reader has no external resources to release.
            }
        };
        when(rs.getCharacterStream(1)).thenReturn(enormous);
        assertThatThrownBy(() -> new ReportResultBudget().render(rs, null))
                .isInstanceOf(ReportResultBudget.LimitExceededException.class);
        assertThat(consumed.get()).isLessThanOrEqualTo(ReportResultBudget.MAX_CELL_CHARACTERS + 2048);
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
                .isInstanceOf(ReportResultBudget.LimitExceededException.class);
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
            assertThat(budget.render(rows(0, ""), null)[1]).isEmpty();
        }
        assertThatThrownBy(budget::startQuery).isInstanceOf(ReportResultBudget.LimitExceededException.class);
    }

    @Test
    void shouldRejectWideMetadata_beforeReadingCells() throws Exception {
        for (int count : new int[]{0, 1}) {
            ResultSet rs = rows(count, "a");
            when(rs.getMetaData().getColumnCount()).thenReturn(ReportResultBudget.MAX_COLUMNS + 1);
            assertThatThrownBy(() -> new ReportResultBudget().render(rs, null))
                    .isInstanceOf(ReportResultBudget.LimitExceededException.class);
            verify(rs, never()).getCharacterStream(1);
        }
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
