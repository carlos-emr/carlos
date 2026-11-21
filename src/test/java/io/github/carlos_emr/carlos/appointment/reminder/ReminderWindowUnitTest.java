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
import java.time.LocalTime;

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

    /** #884: texts keep quiet hours, 21:00 to 08:00, which wrap past midnight. */
    @Test
    void shouldKeepQuietHours_thatWrapPastMidnight() {
        ReminderWindow sms = ReminderWindow.forSmsHoursBefore(null);

        assertThat(sms.isQuiet(LocalTime.of(21, 0))).isTrue();
        assertThat(sms.isQuiet(LocalTime.of(23, 59))).isTrue();
        assertThat(sms.isQuiet(LocalTime.of(0, 0))).isTrue();
        assertThat(sms.isQuiet(LocalTime.of(7, 59))).isTrue();
        assertThat(sms.isQuiet(LocalTime.of(8, 0))).isFalse();
        assertThat(sms.isQuiet(LocalTime.of(20, 59))).isFalse();
        assertThat(new ReminderWindow(Duration.ofHours(24), Duration.ofHours(2)).isQuiet(LocalTime.MIDNIGHT))
                .as("email has none").isFalse();
    }

    @Test
    void shouldHoldATextDueAtNight_untilEightIfStillDueThen() {
        ReminderWindow sms = ReminderWindow.forSmsHoursBefore("24");
        LocalDateTime start = LocalDateTime.of(2026, 11, 18, 23, 30);

        assertThat(sms.isDue(start, start.minusHours(24), null)).as("23:30 the day before").isFalse();
        assertThat(sms.isDue(start, LocalDateTime.of(2026, 11, 18, 8, 0), null)).isTrue();
        assertThat(sms.opensAt(start)).isEqualTo(LocalDateTime.of(2026, 11, 18, 8, 0));
        // Opening inside quiet hours after midnight: it opens at 08:00 the same morning.
        assertThat(sms.opensAt(LocalDateTime.of(2026, 11, 19, 3, 0))).isEqualTo(LocalDateTime.of(2026, 11, 18, 8, 0));
        // Opening outside them: unchanged.
        assertThat(sms.opensAt(LocalDateTime.of(2026, 11, 19, 12, 0))).isEqualTo(LocalDateTime.of(2026, 11, 18, 12, 0));
    }

    @Test
    void shouldReadTheTextLeadTime_andRefuseHalfAQuietPeriod() {
        assertThat(ReminderWindow.forSmsHoursBefore("48").lead()).isEqualTo(Duration.ofHours(48));
        assertThat(ReminderWindow.forSmsHoursBefore("1").lead()).isEqualTo(Duration.ofHours(ReminderWindow.MIN_HOURS_BEFORE));
        assertThat(ReminderWindow.forSmsHoursBefore("FAKE").lead()).isEqualTo(Duration.ofHours(24));
        assertThat(ReminderWindow.forSmsHoursBefore(null).cutoff()).isEqualTo(Duration.ofHours(2));
        assertThatThrownBy(() -> new ReminderWindow(Duration.ofHours(24), Duration.ofHours(2), LocalTime.NOON, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReminderWindow(Duration.ofHours(24), Duration.ofHours(2), LocalTime.NOON, LocalTime.NOON))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** #884 S2: the line under the box says when the text will go, quiet hours included. */
    @Test
    void shouldFindTheNextSendTime_aroundTheQuietHours() {
        ReminderWindow sms = ReminderWindow.forSmsHoursBefore("24");
        LocalDateTime tomorrowAfternoon = LocalDateTime.of(2026, 11, 19, 14, 0);

        assertThat(sms.nextSendTime(tomorrowAfternoon, LocalDateTime.of(2026, 11, 18, 22, 0)))
                .as("ticked at night: 08:00").contains(LocalDateTime.of(2026, 11, 19, 8, 0));
        assertThat(sms.nextSendTime(tomorrowAfternoon, LocalDateTime.of(2026, 11, 18, 15, 0)))
                .as("due now").contains(LocalDateTime.of(2026, 11, 18, 15, 0));
        assertThat(sms.nextSendTime(LocalDateTime.of(2026, 11, 21, 14, 0), LocalDateTime.of(2026, 11, 18, 15, 0)))
                .as("before the window opens").contains(LocalDateTime.of(2026, 11, 20, 14, 0));
        assertThat(sms.nextSendTime(tomorrowAfternoon, LocalDateTime.of(2026, 11, 19, 12, 30)))
                .as("after the cutoff").isEmpty();
        // A 3-hour lead for 09:00 opens at 06:00, inside the quiet hours, and the cutoff is 07:00: never sent.
        assertThat(ReminderWindow.forSmsHoursBefore("3").nextSendTime(LocalDateTime.of(2026, 11, 19, 9, 0),
                LocalDateTime.of(2026, 11, 18, 22, 0))).isEmpty();
    }

    /** #884 S1: a text the SMS queue holds up must not go out at night. */
    @Test
    void shouldLimitAText_toTheNextQuietHoursOrTheCutoff() {
        ReminderWindow sms = ReminderWindow.forSmsHoursBefore("24");
        LocalDateTime tomorrowAfternoon = LocalDateTime.of(2026, 11, 19, 14, 0);

        assertThat(sms.sendBy(tomorrowAfternoon, LocalDateTime.of(2026, 11, 18, 15, 0)))
                .as("handed over the day before: 21:00 that night").isEqualTo(LocalDateTime.of(2026, 11, 18, 21, 0));
        assertThat(sms.sendBy(tomorrowAfternoon, LocalDateTime.of(2026, 11, 19, 9, 0)))
                .as("the same morning: the cutoff").isEqualTo(LocalDateTime.of(2026, 11, 19, 12, 0));
        assertThat(sms.sendBy(LocalDateTime.of(2026, 11, 20, 23, 30), LocalDateTime.of(2026, 11, 18, 23, 0)))
                .as("seen after 21:00: the next evening's").isEqualTo(LocalDateTime.of(2026, 11, 19, 21, 0));
        assertThat(new ReminderWindow(Duration.ofHours(24), Duration.ofHours(2))
                .sendBy(tomorrowAfternoon, LocalDateTime.of(2026, 11, 18, 15, 0)))
                .as("no quiet hours: the cutoff").isEqualTo(LocalDateTime.of(2026, 11, 19, 12, 0));
    }


    /**
     * #884 (review): no text is handed over in the 10 minutes before the quiet hours. Its limit would be that close,
     * and a text the queue could not send by 21:00 would expire; it waits for 08:00 instead, time allowing.
     */
    @Test
    void shouldPauseTheHandover_justBeforeTheQuietHours() {
        ReminderWindow sms = ReminderWindow.forSmsHoursBefore("24");
        LocalDateTime tomorrowAfternoon = LocalDateTime.of(2026, 11, 19, 14, 0);

        assertThat(sms.isDue(tomorrowAfternoon, LocalDateTime.of(2026, 11, 18, 20, 49), null)).isTrue();
        assertThat(sms.isDue(tomorrowAfternoon, LocalDateTime.of(2026, 11, 18, 20, 50), null)).isFalse();
        assertThat(sms.nextSendTime(tomorrowAfternoon, LocalDateTime.of(2026, 11, 18, 20, 55)))
                .contains(LocalDateTime.of(2026, 11, 19, 8, 0));
        assertThat(sms.isQuiet(LocalTime.of(20, 55))).as("the quiet hours themselves are unchanged").isFalse();
        // Without quiet hours (email) nothing pauses.
        assertThat(new ReminderWindow(Duration.ofHours(24), Duration.ofHours(2))
                .isDue(tomorrowAfternoon, LocalDateTime.of(2026, 11, 18, 20, 55), null)).isTrue();
        // A margin that wraps past midnight, for quiet hours starting at 00:05.
        ReminderWindow early = new ReminderWindow(Duration.ofHours(24), Duration.ofHours(2), LocalTime.of(0, 5),
                LocalTime.of(6, 0), Duration.ofMinutes(10));
        assertThat(early.isDue(tomorrowAfternoon, LocalDateTime.of(2026, 11, 18, 23, 58), null)).isFalse();
        assertThat(early.isDue(tomorrowAfternoon, LocalDateTime.of(2026, 11, 18, 23, 50), null)).isTrue();
    }
}
