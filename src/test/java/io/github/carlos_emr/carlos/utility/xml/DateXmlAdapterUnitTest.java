/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;
import javax.xml.datatype.DatatypeFactory;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("xml")
@ResourceLock("java.util.TimeZone")
class DateXmlAdapterUnitTest extends CarlosUnitTestBase {
    private final DateXmlAdapter adapter = new DateXmlAdapter();

    @ParameterizedTest
    @CsvSource({"UTC,2026-01-01T12:34:56.12Z", "America/Toronto,2026-01-01T07:34:56.12-05:00",
                "Asia/Tokyo,2026-01-01T21:34:56.12+09:00"})
    void shouldPreserveDefaultZoneAndTrimFraction_whenMarshallingDate(String zone, String expected) {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            Date value = Date.from(Instant.parse("2026-01-01T12:34:56.120Z"));
            var actual = adapter.marshal(value);
            assertThat(actual.toXMLFormat()).isEqualTo(expected);
            assertThat(adapter.unmarshal(actual)).isEqualTo(value);
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void shouldPreserveExplicitOffsetAndNull_whenUnmarshalling() {
        assertThat(adapter.marshal(null)).isNull();
        assertThat(adapter.unmarshal(null)).isNull();
        var lexical = DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar("2026-01-01T07:34:56-05:00");
        assertThat(adapter.unmarshal(lexical).toInstant()).isEqualTo(Instant.parse("2026-01-01T12:34:56Z"));
    }
}
