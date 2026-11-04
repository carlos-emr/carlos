/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.data;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class RptResultStructUnitTest {
    @Test
    void shouldReadByPosition_whenColumnsShareAnAlias() throws Exception {
        var rows = mock(ResultSet.class);
        var metadata = mock(ResultSetMetaData.class);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(3);
        when(metadata.getColumnLabel(1)).thenReturn("name");
        when(metadata.getColumnLabel(2)).thenReturn("name");
        when(metadata.getColumnLabel(3)).thenReturn("empty");
        when(rows.next()).thenReturn(true, false);
        when(rows.getString(1)).thenReturn("First");
        when(rows.getString(2)).thenReturn("<Second>");
        assertThat(RptResultStruct.getStructure2(rows))
                .contains("<td>First</td><td>&lt;Second&gt;</td><td></td>")
                .contains("<th class=\"reportHeader\">name</th>");
        verify(rows, never()).getString(anyString());
    }
}
