/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

package io.github.carlos_emr.carlos.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StatementClosingResultSet}.
 *
 * <p>Verifies that closing the wrapped {@link ResultSet} closes the parent
 * {@link Statement}, fixing the resource leak in {@link DBHandler}.</p>
 *
 * @since 2026-04-07
 * @see StatementClosingResultSet
 * @see DBHandler
 */
@Tag("unit")
@Tag("fast")
class StatementClosingResultSetUnitTest {

    /**
     * The primary fix: closing the ResultSet wrapper must also close the Statement.
     */
    @Test
    @DisplayName("should close statement when result set is closed")
    void shouldCloseStatement_whenResultSetIsClosed() throws SQLException {
        // Given
        ResultSet mockRs = Mockito.mock(ResultSet.class);
        Statement mockStmt = Mockito.mock(Statement.class);
        ResultSet wrapped = StatementClosingResultSet.wrap(mockRs, mockStmt);

        // When
        wrapped.close();

        // Then — both resources must have been closed
        verify(mockRs).close();
        verify(mockStmt).close();
    }

    /**
     * Statement must be closed even when ResultSet.close() throws.
     */
    @Test
    @DisplayName("should close statement even when result set close throws")
    void shouldCloseStatement_whenResultSetCloseThrows() throws SQLException {
        // Given
        ResultSet mockRs = Mockito.mock(ResultSet.class);
        Statement mockStmt = Mockito.mock(Statement.class);
        doThrow(new SQLException("simulated rs close failure")).when(mockRs).close();
        ResultSet wrapped = StatementClosingResultSet.wrap(mockRs, mockStmt);

        // When / Then — the SQLException from rs.close() is propagated…
        assertThatThrownBy(wrapped::close).isInstanceOf(SQLException.class);
        // …but the statement was still closed
        verify(mockStmt).close();
    }

    /**
     * Non-close method calls must be forwarded to the delegate ResultSet.
     */
    @Test
    @DisplayName("should delegate next() to the wrapped result set")
    void shouldDelegateNext_toWrappedResultSet() throws SQLException {
        // Given
        ResultSet mockRs = Mockito.mock(ResultSet.class);
        Statement mockStmt = Mockito.mock(Statement.class);
        when(mockRs.next()).thenReturn(true);
        ResultSet wrapped = StatementClosingResultSet.wrap(mockRs, mockStmt);

        // When
        boolean hasNext = wrapped.next();

        // Then
        assertThat(hasNext).isTrue();
        verify(mockRs).next();
        verifyNoInteractions(mockStmt);
    }

    /**
     * Column value getters must be forwarded transparently.
     */
    @Test
    @DisplayName("should delegate getString(int) to the wrapped result set")
    void shouldDelegateGetString_toWrappedResultSet() throws SQLException {
        // Given
        ResultSet mockRs = Mockito.mock(ResultSet.class);
        Statement mockStmt = Mockito.mock(Statement.class);
        when(mockRs.getString(1)).thenReturn("hello");
        ResultSet wrapped = StatementClosingResultSet.wrap(mockRs, mockStmt);

        // When
        String value = wrapped.getString(1);

        // Then
        assertThat(value).isEqualTo("hello");
        verify(mockRs).getString(1);
    }

    /**
     * SQLExceptions thrown by delegate methods must propagate to the caller
     * without being wrapped in an InvocationTargetException.
     */
    @Test
    @DisplayName("should propagate SQLException from delegate method directly")
    void shouldPropagateSQLException_fromDelegateMethod() throws SQLException {
        // Given
        ResultSet mockRs = Mockito.mock(ResultSet.class);
        Statement mockStmt = Mockito.mock(Statement.class);
        when(mockRs.next()).thenThrow(new SQLException("cursor error"));
        ResultSet wrapped = StatementClosingResultSet.wrap(mockRs, mockStmt);

        // When / Then — caller sees a raw SQLException, not an InvocationTargetException
        assertThatThrownBy(wrapped::next)
            .isInstanceOf(SQLException.class)
            .hasMessage("cursor error");
    }

    /**
     * Null ResultSet must be rejected early with a clear error message.
     */
    @Test
    @DisplayName("should throw IllegalArgumentException when result set is null")
    void shouldThrowIllegalArgumentException_whenResultSetIsNull() {
        // Given
        Statement mockStmt = Mockito.mock(Statement.class);

        // When / Then
        assertThatThrownBy(() -> StatementClosingResultSet.wrap(null, mockStmt))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ResultSet");
    }

    /**
     * Null Statement must be rejected early with a clear error message.
     */
    @Test
    @DisplayName("should throw IllegalArgumentException when statement is null")
    void shouldThrowIllegalArgumentException_whenStatementIsNull() {
        // Given
        ResultSet mockRs = Mockito.mock(ResultSet.class);

        // When / Then
        assertThatThrownBy(() -> StatementClosingResultSet.wrap(mockRs, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Statement");
    }

    /**
     * When both close() calls throw, the statement exception must be
     * attached as a suppressed exception on the result-set exception.
     */
    @Test
    @DisplayName("should attach statement close exception as suppressed when both close() calls throw")
    void shouldAddSuppressedException_whenBothCloseCallsThrow() throws SQLException {
        // Given
        ResultSet mockRs = Mockito.mock(ResultSet.class);
        Statement mockStmt = Mockito.mock(Statement.class);
        SQLException rsException = new SQLException("rs close error");
        SQLException stmtException = new SQLException("stmt close error");
        doThrow(rsException).when(mockRs).close();
        doThrow(stmtException).when(mockStmt).close();
        ResultSet wrapped = StatementClosingResultSet.wrap(mockRs, mockStmt);

        // When / Then — primary exception is from rs, stmt exception is suppressed
        assertThatThrownBy(wrapped::close)
            .isInstanceOf(SQLException.class)
            .hasMessage("rs close error")
            .satisfies(ex -> assertThat(ex.getSuppressed())
                .hasSize(1)
                .allSatisfy(suppressed -> assertThat(suppressed).hasMessage("stmt close error")));
    }
}
