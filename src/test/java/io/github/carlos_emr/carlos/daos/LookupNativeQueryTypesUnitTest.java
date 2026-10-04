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
package io.github.carlos_emr.carlos.daos;

import io.github.carlos_emr.carlos.model.FieldDefValue;
import io.github.carlos_emr.carlos.model.LookupTableDefValue;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
class LookupNativeQueryTypesUnitTest extends CarlosUnitTestBase {
    static Stream<Arguments> dates() {
        return Stream.of(Arguments.of(LocalDate.of(2026, 3, 4), "00:00:00"),
                Arguments.of(java.sql.Date.valueOf("2026-03-04"), "00:00:00"),
                Arguments.of(LocalDateTime.of(2026, 3, 4, 12, 34, 56), "12:34:56"),
                Arguments.of(Timestamp.valueOf("2026-03-04 12:34:56"), "12:34:56"),
                Arguments.of("2026-03-04 12:34:56", "12:34:56"), Arguments.of(null, ""));
    }

    @ParameterizedTest
    @MethodSource("dates")
    void shouldKeepDatesInBothFieldViews_whenNativeJdbcOrLegacyTextReturned(Object date, String time) {
        var dao = spy(new LookupDaoImpl());
        var manager = mock(EntityManager.class);
        var query = mock(Query.class);
        ReflectionTestUtils.setField(dao, "entityManager", manager);
        when(manager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.singletonList(new Object[] {"A", date}));
        var code = new FieldDefValue();
        code.setFieldSQL("code");
        code.setGenericIdx(1);
        var field = new FieldDefValue();
        field.setFieldSQL("recorded_at");
        field.setFieldType("D");
        field.setEditable(true);
        doReturn(List.of(code, field)).when(dao).LoadFieldDefList("NATIVE");
        var definition = new LookupTableDefValue();
        definition.setTableId("NATIVE");
        definition.setTableName("native_lookup_test");

        List<FieldDefValue> fields = dao.GetCodeFieldValues(definition, "A");
        assertThat(fields.get(1).getVal()).isEqualTo(date == null ? "" : "2026/03/04");
        field.setEditable(false);
        fields = dao.GetCodeFieldValues(definition, "A");
        String expected = date == null ? "" : "2026/03/04 " + time;
        assertThat(fields.get(1).getVal()).isEqualTo(expected);
        List<List> all = dao.GetCodeFieldValues(definition);
        assertThat(all).hasSize(1);
        assertThat(((FieldDefValue) all.getFirst().get(1)).getVal()).isEqualTo(expected);
    }
}
