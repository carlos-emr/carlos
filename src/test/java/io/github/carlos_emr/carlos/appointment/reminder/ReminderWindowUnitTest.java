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
package io.github.carlos_emr.carlos.appointment.reminder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #5887: when an appointment reminder may go. A day's lead and a two-hour cutoff unless a test says otherwise.
 */
@Tag("unit")
@Tag("appointment")
@DisplayName("Appointment reminder window")
class ReminderWindowUnitTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 13, 9, 0);
    private final ReminderWindow window = new ReminderWindow(Duration.ofHours(24), Duration.ofHours(2));

    @Test
    void shouldBeDue_fromTheLeadUntilJustBeforeTheCutoff() {
        assertThat(window.isDue(START, START.minusHours(24), null)).as("the moment the window opens").isTrue();
        assertThat(window.isDue(START, START.minusHours(10), null)).isTrue();
        assertThat(window.isDue(START, START.minusHours(2).minusMinutes(1), null)).isTrue();
    }

    @Test
    void shouldNotBeDue_beforeTheLeadOrOnceTheCutoffIsReached() {
        assertThat(window.isDue(START, START.minusHours(24).minusMinutes(1), null)).isFalse();
        assertThat(window.isDue(START, START.minusHours(2), null)).as("two hours left").isFalse();
        assertThat(window.isDue(START, START.plusMinutes(5), null)).as("already started").isFalse();
    }

    @Test
    void shouldNotBeDue_forTheStartAReminderAlreadyWentFor() {
        assertThat(window.isDue(START, START.minusHours(10), START)).isFalse();
    }

    @Test
    void shouldBeDueAgain_whenTheAppointmentMovedToANewStart() {
        LocalDateTime moved = START.plusDays(1);

        assertThat(window.isDue(moved, moved.minusHours(10), START)).isTrue();
    }

    @Test
    void shouldNeverBeDue_withoutAStartOrAClock() {
        assertThat(window.isDue(null, START, null)).isFalse();
        assertThat(window.isDue(START, null, null)).isFalse();
    }

    @Test
    void shouldReadTheDatesThatCanBeDue_fromTheCutoffToTheLead() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 12, 23, 0);

        assertThat(window.firstDate(now)).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(window.lastDate(now)).isEqualTo(LocalDate.of(2026, 11, 13));
        // The starts themselves, which the due query reads between.
        assertThat(window.earliestStart(now)).isEqualTo(now.plusHours(2));
        assertThat(window.latestStart(now)).isEqualTo(now.plusHours(24));
        assertThat(window.isDue(window.latestStart(now), now, null)).isTrue();
        assertThat(window.isDue(window.earliestStart(now), now, null)).isFalse();
        assertThat(window.isDue(window.earliestStart(now).plusSeconds(1), now, null)).isTrue();
        assertThat(window.opensAt(START)).isEqualTo(START.minusHours(24));
    }

    @Test
    void shouldRefuseACutoffThatIsNotShorterThanTheLead() {
        assertThatThrownBy(() -> new ReminderWindow(Duration.ofHours(2), Duration.ofHours(2)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReminderWindow(Duration.ofHours(-1), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** The lead is a clinic setting (#5887), kept within sane bounds. */
    @ParameterizedTest(name = "[{0}] gives {1} hours")
    @CsvSource(value = {
            "NULL, 24",
            "'', 24",
            "'  ', 24",
            "24, 24",
            "' 48 ', 48",
            "3, 3",
            "168, 168",
            "2, 3",
            "0, 3",
            "-5, 3",
            "169, 168",
            "720, 168",
            "1.5, 24",
            "one day, 24"}, nullValues = "NULL")
    void shouldUseTheClinicsHoursBefore_withinTheirBounds(String configured, int hours) {
        ReminderWindow configuredWindow = ReminderWindow.forHoursBefore(configured);

        assertThat(configuredWindow.lead()).isEqualTo(Duration.ofHours(hours));
        assertThat(configuredWindow.isDue(START, START.minusHours(hours), null)).isTrue();
        assertThat(configuredWindow.isDue(START, START.minusHours(2), null)).isFalse();
    }
}
