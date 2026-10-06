/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Date;
import java.sql.Timestamp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

@Tag("unit")
@Tag("database")
@DisplayName("DBPreparedHandlerParam")
class DBPreparedHandlerParamUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should defensively copy date values")
    void shouldDefensivelyCopy_dateValues() {
        Date expected = Date.valueOf("2026-05-21");
        Date original = Date.valueOf("2026-05-21");

        DBPreparedHandlerParam param = new DBPreparedHandlerParam(original);
        original.setTime(Date.valueOf("2026-05-22").getTime());

        Date accessorValue = param.getDateValue();
        Object jdbcValue = param.jdbcValue();

        assertThat(accessorValue).isEqualTo(expected).isNotSameAs(original);
        assertThat(jdbcValue).isEqualTo(expected).isNotSameAs(original).isNotSameAs(accessorValue);

        accessorValue.setTime(Date.valueOf("2026-05-23").getTime());
        ((Date) jdbcValue).setTime(Date.valueOf("2026-05-24").getTime());

        assertThat(param.getDateValue()).isEqualTo(expected);
        assertThat(param.jdbcValue()).isEqualTo(expected);
    }

    @Test
    @DisplayName("should defensively copy timestamp values")
    void shouldDefensivelyCopy_timestampValues() {
        Timestamp expected = Timestamp.valueOf("2026-05-21 10:15:30.123456789");
        Timestamp original = Timestamp.valueOf("2026-05-21 10:15:30.123456789");

        DBPreparedHandlerParam param = new DBPreparedHandlerParam(original);
        original.setTime(Timestamp.valueOf("2026-05-22 10:15:30.987654321").getTime());
        original.setNanos(987654321);

        Timestamp accessorValue = param.getTimestampValue();
        Object jdbcValue = param.jdbcValue();

        assertThat(accessorValue).isEqualTo(expected).isNotSameAs(original);
        assertThat(accessorValue.getNanos()).isEqualTo(123456789);
        assertThat(jdbcValue).isEqualTo(expected).isNotSameAs(original).isNotSameAs(accessorValue);
        assertThat(((Timestamp) jdbcValue).getNanos()).isEqualTo(123456789);

        accessorValue.setNanos(234567890);
        ((Timestamp) jdbcValue).setNanos(345678901);

        assertThat(param.getTimestampValue()).isEqualTo(expected);
        assertThat(param.jdbcValue()).isEqualTo(expected);
    }
}
