/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies serialization leaves the caller's lazy calendar fields untouched.
 * @since 2026-11-07
 */
@Tag("unit")
@Tag("xml")
class CalendarXmlStateUnitTest extends CarlosUnitTestBase {
    /** Resolving lazy or lenient fields must affect only the adapter's private calendar copy. */
    @ParameterizedTest
    @CsvSource({"15,false,2026-07-15", "15,true,2026-07-15T13:14:15", "32,true,2026-08-01T13:14:15"})
    void shouldLeaveLazyCalendarUnchanged_whenWritingLegacyXml(int day, boolean time, String expected) {
        var calendar = new GregorianCalendar(TimeZone.getTimeZone("America/Toronto"));
        calendar.clear();
        calendar.set(2026, Calendar.JULY, day);
        if (time) {
            calendar.set(Calendar.HOUR_OF_DAY, 13);
            calendar.set(Calendar.MINUTE, 14);
            calendar.set(Calendar.SECOND, 15);
        }
        // Unlike equals/get/getTime, toString observes Calendar's lazy state without completing it.
        String original = calendar.toString();
        var adapter = new CalendarXmlAdapter();

        assertThat(adapter.marshal(calendar)).isEqualTo(expected);
        assertThat(calendar.toString()).isEqualTo(original);
        assertThat(adapter.marshal(calendar)).isEqualTo(expected);
        assertThat(calendar.toString()).isEqualTo(original);
    }
}
