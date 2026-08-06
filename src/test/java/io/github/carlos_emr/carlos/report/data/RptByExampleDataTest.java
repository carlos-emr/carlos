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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

@Tag("unit")
@Tag("report")
@Tag("security")
class RptByExampleDataTest {
    private Connection connection;
    private PreparedStatement statement;
    private ResultSet resultSet;
    private ResultSetMetaData metadata;
    private RptByExampleData reportData;
    private Properties properties;

    @BeforeEach
    void setUp() throws SQLException {
        connection = mock(Connection.class);
        statement = mock(PreparedStatement.class);
        resultSet = mock(ResultSet.class);
        metadata = mock(ResultSetMetaData.class);
        reportData = new RptByExampleData(() -> connection);
        properties = new Properties();
        properties.setProperty("db_name", "oscar_mcmaster?useUnicode=true");

        when(connection.isReadOnly()).thenReturn(true);
        when(connection.prepareStatement("select demographic_no from demographic",
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnLabel(1)).thenReturn("demographic_no");
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getCharacterStream(1)).thenAnswer(ignored -> new StringReader("42"));
    }

    @Test
    @DisplayName("executes once using a bounded read-only JDBC statement and restores connection state")
    void shouldExecuteBoundedReadOnlyQuery_whenConnectionStartsReadOnly() throws SQLException {
        RptByExampleData.QueryResult result = reportData.execute(
                "select demographic_no from demographic", properties, "999998");

        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.html()).contains("demographic_no").contains("42");
        verify(statement).setMaxRows(RptByExampleData.MAX_ROWS + 1);
        verify(statement).setQueryTimeout(RptByExampleData.QUERY_TIMEOUT_SECONDS);

        InOrder order = inOrder(connection, statement, resultSet);
        order.verify(connection).setReadOnly(true);
        order.verify(statement).executeQuery();
        order.verify(resultSet).close();
        order.verify(statement).close();
        order.verify(connection).setReadOnly(true);
        order.verify(connection).close();
    }

    @Test
    @DisplayName("restores a writable connection after a successful query")
    void shouldRestoreWritableConnection_whenQuerySucceeds() throws SQLException {
        when(connection.isReadOnly()).thenReturn(false);

        reportData.execute("select demographic_no from demographic", properties, "999998");

        InOrder order = inOrder(connection, statement, resultSet);
        order.verify(connection).setReadOnly(true);
        order.verify(statement).executeQuery();
        order.verify(resultSet).close();
        order.verify(statement).close();
        order.verify(connection).setReadOnly(false);
        order.verify(connection).close();
    }

    @Test
    @DisplayName("preserves a timeout when restoring connection state also fails")
    void shouldPreserveTimeout_whenReadOnlyRestoreFails() throws SQLException {
        SQLTimeoutException timeout = new SQLTimeoutException("timed out");
        SQLException restoreFailure = new SQLException("restore failed");
        when(connection.isReadOnly()).thenReturn(false);
        when(statement.executeQuery()).thenThrow(timeout);
        doThrow(restoreFailure).when(connection).setReadOnly(false);

        assertThatThrownBy(() -> reportData.execute(
                "select demographic_no from demographic", properties, "999998"))
                .isSameAs(timeout)
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(restoreFailure));

        verify(connection).setReadOnly(false);
        verify(connection).close();
    }

    @Test
    @DisplayName("preserves query failure when restoring an originally read-only connection also fails")
    void shouldPreserveQueryFailure_whenOriginallyReadOnlyRestoreFails() throws SQLException {
        SQLException queryFailure = new SQLException("query failed");
        SQLException restoreFailure = new SQLException("restore failed");
        when(statement.executeQuery()).thenThrow(queryFailure);
        org.mockito.Mockito.doNothing().doThrow(restoreFailure).when(connection).setReadOnly(true);

        assertThatThrownBy(() -> reportData.execute(
                "select demographic_no from demographic", properties, "999998"))
                .isSameAs(queryFailure)
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).containsExactly(restoreFailure));

        verify(connection, org.mockito.Mockito.times(2)).setReadOnly(true);
        verify(connection).close();
    }

    @Test
    @DisplayName("renders duplicate column labels using their positional values")
    void shouldRenderPositionalValues_whenColumnLabelsAreDuplicated() throws SQLException {
        when(metadata.getColumnCount()).thenReturn(2);
        when(metadata.getColumnLabel(1)).thenReturn("id");
        when(metadata.getColumnLabel(2)).thenReturn("id");
        when(resultSet.getCharacterStream(1)).thenAnswer(ignored -> new StringReader("first"));
        when(resultSet.getCharacterStream(2)).thenAnswer(ignored -> new StringReader("second"));

        RptResultStruct.StructuredResult result = RptResultStruct.getStructureWithCount(resultSet);

        assertThat(result.html()).contains("first").contains("second");
        assertThat(result.rowCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("truncates encoded result output at the configured character budget")
    void shouldTruncateEncodedOutput_whenCellExceedsCharacterBudget() throws SQLException {
        when(resultSet.getCharacterStream(1)).thenAnswer(ignored -> new StringReader("<".repeat(1_000)));

        RptResultStruct.StructuredResult result = RptResultStruct.getStructureWithCount(resultSet, 128);

        assertThat(result.truncated()).isTrue();
        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.html()).hasSizeLessThanOrEqualTo(128).endsWith("</td></tr></table>");
        assertThat(result.html()).contains("&lt;");
        String cell = result.html().substring(result.html().indexOf("<td>") + 4, result.html().indexOf("</td>"));
        assertThat(cell).matches("(?:&lt;)*…");
    }

    @Test
    @DisplayName("reports omitted rows when the result exceeds the rendering row limit")
    void shouldReportRowLimit_whenResultContainsAnotherRow() throws SQLException {
        when(resultSet.next()).thenReturn(true, true);

        RptResultStruct.StructuredResult result = RptResultStruct.getStructureWithCount(resultSet, 1_000, 1);

        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.rowLimitReached()).isTrue();
        assertThat(result.html()).contains("42");
    }

    @Test
    @DisplayName("does not report omitted rows when the result exactly reaches the row limit")
    void shouldNotReportRowLimit_whenResultExactlyMatchesLimit() throws SQLException {
        RptResultStruct.StructuredResult result = RptResultStruct.getStructureWithCount(resultSet, 1_000, 1);

        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.rowLimitReached()).isFalse();
    }

    @Test
    @DisplayName("rejects unsafe SQL before acquiring a database connection")
    void shouldRejectBeforeConnecting_whenSqlIsUnsafe() throws SQLException {
        assertThatThrownBy(() -> reportData.execute("delete from demographic", properties, "999998"))
                .isInstanceOf(QueryByExampleValidationException.class);

        verify(connection, never()).prepareStatement(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }
}
