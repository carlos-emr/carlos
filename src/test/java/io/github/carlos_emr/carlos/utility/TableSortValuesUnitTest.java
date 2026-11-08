/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility;

import java.math.BigDecimal;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TableSortValuesUnitTest {
    @Test
    void shouldKeepNumericValuesDistinctFromIdentifiers_forBrowserSorting() {
        assertThat(TableSortValues.type(42)).isEqualTo("number");
        assertThat(TableSortValues.value(42)).isEqualTo("42");
        assertThat(TableSortValues.type(new BigDecimal("123.450"))).isEqualTo("number");
        assertThat(TableSortValues.value(new BigDecimal("123.450"))).isEqualTo("123.450");
        assertThat(TableSortValues.type("00123")).isEqualTo("text");
        assertThat(TableSortValues.value("00123")).isEqualTo("00123");
        assertThat(TableSortValues.type(null)).isEqualTo("text");
        assertThat(TableSortValues.value(null)).isEmpty();
    }

    @Test
    void shouldUseEpochMilliseconds_forDateAndTimestampKeys() {
        for (java.util.Date date : new java.util.Date[]{new java.util.Date(1_700_000_000_000L),
                new java.sql.Timestamp(1_700_000_000_000L)}) {
            assertThat(TableSortValues.type(date)).isEqualTo("date");
            assertThat(TableSortValues.value(date)).isEqualTo("1700000000000");
        }
    }
}
