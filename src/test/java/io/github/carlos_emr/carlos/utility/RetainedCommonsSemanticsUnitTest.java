/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Counterexamples that prohibit superficially similar JDK replacements under #5483. */
@Tag("unit")
class RetainedCommonsSemanticsUnitTest {
    @Test
    void shouldMatchFixedMillisecondConstants_forAllMigratedDurations() {
        assertThat(Duration.ofDays(1).toMillis()).isEqualTo(DateUtils.MILLIS_PER_DAY);
        assertThat(Duration.ofHours(1).toMillis()).isEqualTo(DateUtils.MILLIS_PER_HOUR);
    }

    @Test
    void shouldRetainCalendarCutover_forHistoricalDates() {
        GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT);
        calendar.clear();
        calendar.set(1500, Calendar.JANUARY, 1);
        String legacy = DateFormatUtils.format(calendar, "yyyy-MM-dd", TimeZone.getTimeZone("UTC"));
        String modern = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(calendar.toInstant());
        assertThat(legacy).isEqualTo("1500-01-01");
        assertThat(modern).isNotEqualTo(legacy);
    }

    @Test
    void shouldRetainCalendarChronology_forNonGregorianCalendars() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.forLanguageTag("th-TH"));
        calendar.setTimeInMillis(0);
        assertThat(DateFormatUtils.format(calendar, "yyyy-MM-dd", TimeZone.getTimeZone("UTC")))
                .isEqualTo("2513-01-01");
        assertThat(DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(calendar.toInstant()))
                .isEqualTo("1970-01-01");
    }

    @Test
    void shouldSwallowOnlyCloseIoFailure_forRetainedQuietCloseSites() {
        Closeable closeable = () -> { throw new IOException("synthetic close failure"); };
        IOUtils.closeQuietly(closeable);
        assertThatThrownBy(() -> {
            try (closeable) { /* plain try-with-resources changes this outcome */ }
        }).isInstanceOf(IOException.class);
        RuntimeException failure = new IllegalStateException("synthetic runtime failure");
        assertThatThrownBy(() -> IOUtils.closeQuietly(() -> { throw failure; })).isSameAs(failure);
    }
}
