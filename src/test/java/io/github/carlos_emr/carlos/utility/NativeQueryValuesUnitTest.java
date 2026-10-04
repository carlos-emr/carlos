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
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.util.NativeQueryValues;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.TimeZone;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class NativeQueryValuesUnitTest {
    @Test
    @ResourceLock("java.util.TimeZone")
    void shouldPreserveTimestampTextWithoutZoneConversion_whenDatabaseValueFallsInDstGap() {
        var original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto"));
            assertThat(NativeQueryValues.asString(LocalDateTime.of(2026, 3, 8, 2, 30)))
                    .isEqualTo("2026-03-08 02:30:00.0");
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void shouldKeepFractionalSeconds_whenReadingNativeOrJdbcTimestamps() {
        var date = LocalDateTime.of(2026, 3, 4, 12, 34, 56, 123456000);
        assertThat(NativeQueryValues.asString(date)).isEqualTo("2026-03-04 12:34:56.123456");
        assertThat(NativeQueryValues.asString(Timestamp.valueOf(date))).isEqualTo("2026-03-04 12:34:56.123456");
        assertThat(((Timestamp) NativeQueryValues.asDate(date)).getNanos()).isEqualTo(123456000);
    }

    @Test
    void shouldPreserveNullAndRejectUnexpectedDateTypes_whenReadingNativeScalars() {
        assertThat(NativeQueryValues.asString(null)).isNull();
        assertThat(NativeQueryValues.asDate(null)).isNull();
        assertThat(NativeQueryValues.asString('F')).isEqualTo("F");
        assertThat(NativeQueryValues.asString(3L)).isEqualTo("3");
        assertThatThrownBy(() -> NativeQueryValues.asDate(42)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("java.lang.Integer");
    }
}
