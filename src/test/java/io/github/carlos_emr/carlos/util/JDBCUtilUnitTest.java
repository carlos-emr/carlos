/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

@Tag("unit")
@DisplayName("JDBC XML utilities")
class JDBCUtilUnitTest {

    @Test
    @DisplayName("should leave result set closing to the caller")
    void shouldLeaveResultSetClosing_toCaller() throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        ResultSetMetaData metadata = org.mockito.Mockito.mock(ResultSetMetaData.class);

        when(rs.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnName(1)).thenReturn("name");
        when(rs.next()).thenReturn(true, false);
        when(rs.getString(1)).thenReturn("Alice");

        Document document = JDBCUtil.toDocument(rs);

        assertThat(document.getElementsByTagName("name").item(0).getTextContent()).isEqualTo("Alice");
        verify(rs, never()).close();
    }
}
