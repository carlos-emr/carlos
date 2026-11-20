/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@Tag("unit")
@Tag("xml")
class CalendarDateTimeXmlAdapterUnitTest extends CarlosUnitTestBase {
    private final CalendarDateTimeXmlAdapter adapter = new CalendarDateTimeXmlAdapter();

    @ParameterizedTest
    @CsvSource({"America/Toronto,6,1980-07-15T00:00:00-04:00", "America/Toronto,0,1980-01-15T00:00:00-05:00",
            "America/Vancouver,6,1980-07-15T00:00:00-07:00", "America/Vancouver,0,1980-01-15T00:00:00-08:00",
            "UTC,6,1980-07-15T00:00:00Z", "Asia/Kolkata,6,1980-07-15T00:00:00+05:30"})
    void shouldWritePublishedDateTime_whenCalendarContainsOnlyDateFields(String zone, int month, String expected) {
        var value = new GregorianCalendar(TimeZone.getTimeZone(zone));
        value.clear();
        value.set(1980, month, 15);
        boolean hadTime = value.isSet(Calendar.HOUR_OF_DAY);
        boolean hadZone = value.isSet(Calendar.ZONE_OFFSET);

        String actual = adapter.marshal(value);

        assertThat(actual).isEqualTo(expected);
        assertThat(DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(actual).getXMLSchemaType())
                .isEqualTo(DatatypeConstants.DATETIME);
        assertThat(value.isSet(Calendar.HOUR_OF_DAY)).isEqualTo(hadTime);
        assertThat(value.isSet(Calendar.ZONE_OFFSET)).isEqualTo(hadZone);
    }

    @Test
    void shouldRetainInstantAndTrimFraction_whenCalendarHasTimeFields() {
        var value = new GregorianCalendar(TimeZone.getTimeZone("America/Toronto"));
        value.clear();
        value.set(2026, Calendar.JULY, 15, 13, 14, 15);
        value.set(Calendar.MILLISECOND, 120);

        String actual = adapter.marshal(value);

        assertThat(actual).isEqualTo("2026-07-15T13:14:15.12-04:00");
        assertThat(adapter.unmarshal(actual).toInstant()).isEqualTo(value.toInstant());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n "})
    void shouldKeepOptionalCalendarAbsent_whenInputIsBlank(String value) {
        assertThat(adapter.unmarshal(value)).isNull();
        assertThat(adapter.marshal(null)).isNull();
    }

    @Test
    void shouldReadLegacyDateAndOffset_whenInputUsesEitherAcceptedShape() {
        Calendar date = adapter.unmarshal("1980-07-15");
        assertThat(date.get(Calendar.YEAR)).isEqualTo(1980);
        assertThat(date.get(Calendar.MONTH)).isEqualTo(Calendar.JULY);
        assertThat(date.get(Calendar.DAY_OF_MONTH)).isEqualTo(15);
        assertThat(adapter.unmarshal(" \t2026-07-15T13:14:15-04:00 \n").toInstant())
                .isEqualTo(java.time.Instant.parse("2026-07-15T17:14:15Z"));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.unmarshal("not-a-date"));
    }
}
