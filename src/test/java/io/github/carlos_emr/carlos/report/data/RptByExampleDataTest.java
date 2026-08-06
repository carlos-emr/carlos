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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
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

        when(connection.isReadOnly()).thenReturn(false);
        when(connection.prepareStatement("select demographic_no from demographic",
                ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnName(1)).thenReturn("demographic_no");
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("demographic_no")).thenReturn("42");
    }

    @Test
    @DisplayName("executes once using a bounded read-only JDBC statement and restores connection state")
    void shouldExecuteBoundedReadOnlyQueryAndRestoreConnection() throws SQLException {
        RptByExampleData.QueryResult result = reportData.execute(
                "select demographic_no from demographic", properties, "999998");

        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.html()).contains("demographic_no").contains("42");
        verify(statement).setMaxRows(RptByExampleData.MAX_ROWS);
        verify(statement).setQueryTimeout(RptByExampleData.QUERY_TIMEOUT_SECONDS);

        InOrder order = inOrder(connection, statement, resultSet);
        order.verify(connection).setReadOnly(true);
        order.verify(statement).executeQuery();
        order.verify(resultSet).close();
        order.verify(statement).close();
        order.verify(connection).setReadOnly(false);
        order.verify(connection).close();
    }

    @Test
    @DisplayName("rejects unsafe SQL before acquiring a database connection")
    void shouldRejectBeforeConnecting() throws SQLException {
        assertThatThrownBy(() -> reportData.execute("delete from demographic", properties, "999998"))
                .isInstanceOf(QueryByExampleValidationException.class);

        verify(connection, never()).prepareStatement(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }
}
