/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.MSP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

@Tag("unit")
@DisplayName("dbExtract resource cleanup")
class DbExtractUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should close prepared statement when primary query execution fails")
    void shouldClosePreparedStatement_whenPrimaryQueryExecutionFails() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        SQLException failure = new SQLException("query failed");
        when(connection.prepareStatement("select * from billing where provider_ohip_no=?"))
                .thenReturn(statement);
        when(statement.executeQuery()).thenThrow(failure);
        dbExtract extract = new dbExtract();
        setField(extract, "con", connection);

        assertThatThrownBy(() -> extract.executeQuery(
                "select * from billing where provider_ohip_no=?", "123456"))
                .isSameAs(failure);

        verify(statement).setObject(1, "123456");
        verify(statement).close();
        assertThat(getField(extract, "stmt")).isNull();
    }

    @Test
    @DisplayName("should close prepared statement when secondary query execution fails")
    void shouldClosePreparedStatement_whenSecondaryQueryExecutionFails() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        SQLException failure = new SQLException("query failed");
        when(connection.prepareStatement("select * from billingmaster where billing_no=?"))
                .thenReturn(statement);
        when(statement.executeQuery()).thenThrow(failure);
        dbExtract extract = new dbExtract();
        setField(extract, "con", connection);

        assertThatThrownBy(() -> extract.executeQuery2(
                "select * from billingmaster where billing_no=?", "42"))
                .isSameAs(failure);

        verify(statement).setObject(1, "42");
        verify(statement).close();
        assertThat(getField(extract, "stmt2")).isNull();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
