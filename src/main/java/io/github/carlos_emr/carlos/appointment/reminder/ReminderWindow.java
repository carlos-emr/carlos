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

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * When an appointment reminder is due (#5887). A reminder for an appointment starting at {@code start} may be
 * sent from {@code start - lead} until {@code start - cutoff}, once per start. The start a reminder went for is
 * kept, so an appointment moved to a new start is due again whichever path moved it: no edit hook is needed.
 *
 * @since 2026-11-12
 */
public final class ReminderWindow {

    /** The clinic setting for how many hours before the start a reminder goes (Ben, 12 Nov: a clinic setting). */
    public static final String HOURS_BEFORE_PROPERTY = "appointment_reminder.email.hours_before";
    public static final int DEFAULT_HOURS_BEFORE = 24;
    /** More than the cutoff, so every reminder has an hour to go out in. */
    public static final int MIN_HOURS_BEFORE = 3;
    /** A week: further ahead, the patient is likely to forget the reminder too. */
    public static final int MAX_HOURS_BEFORE = 168;

    public static final Duration DEFAULT_LEAD = Duration.ofHours(DEFAULT_HOURS_BEFORE);
    /** Not when less than two hours remain. */
    public static final Duration DEFAULT_CUTOFF = Duration.ofHours(2);

    private static final Logger logger = MiscUtils.getLogger();

    private final Duration lead;
    private final Duration cutoff;

    /**
     * The window for the clinic's {@value #HOURS_BEFORE_PROPERTY} setting, as written in the properties file.
     * Missing or not a whole number: {@value #DEFAULT_HOURS_BEFORE}. Outside {@value #MIN_HOURS_BEFORE} to
     * {@value #MAX_HOURS_BEFORE}: the nearest bound. Both are logged, so a typo is visible.
     */
    public static ReminderWindow forHoursBefore(String configured) {
        int hours = DEFAULT_HOURS_BEFORE;
        if (configured != null && !configured.isBlank()) {
            try {
                hours = Integer.parseInt(configured.trim());
            } catch (NumberFormatException e) {
                logger.warn("{} is not a whole number of hours; using {}", HOURS_BEFORE_PROPERTY, DEFAULT_HOURS_BEFORE);
            }
        }
        int bounded = Math.max(MIN_HOURS_BEFORE, Math.min(MAX_HOURS_BEFORE, hours));
        if (bounded != hours) {
            logger.warn("{}={} is outside {} to {}; using {}", HOURS_BEFORE_PROPERTY, hours, MIN_HOURS_BEFORE,
                    MAX_HOURS_BEFORE, bounded);
        }
        return new ReminderWindow(Duration.ofHours(bounded), DEFAULT_CUTOFF);
    }

    public Duration lead() {
        return lead;
    }

    public ReminderWindow(Duration lead, Duration cutoff) {
        this.lead = Objects.requireNonNull(lead, "lead");
        this.cutoff = Objects.requireNonNull(cutoff, "cutoff");
        if (lead.isNegative() || cutoff.isNegative() || cutoff.compareTo(lead) >= 0) {
            throw new IllegalArgumentException("The cutoff must be shorter than the lead time, and neither negative");
        }
    }

    /**
     * @param start   the appointment's start; {@code null} is never due
     * @param now     the current time, in the same zone as {@code start}
     * @param sentFor the start the last reminder went (or was attempted) for, or {@code null}
     * @return {@code true} when a reminder for {@code start} may be sent now
     */
    public boolean isDue(LocalDateTime start, LocalDateTime now, LocalDateTime sentFor) {
        if (start == null || now == null || start.equals(sentFor)) {
            return false;
        }
        return !now.isBefore(start.minus(lead)) && now.isBefore(start.minus(cutoff));
    }

    /** The first moment a reminder for {@code start} may go. */
    public LocalDateTime opensAt(LocalDateTime start) {
        return start.minus(lead);
    }

    /** The earliest appointment date that can be due at {@code now}. */
    public LocalDate firstDate(LocalDateTime now) {
        return earliestStart(now).toLocalDate();
    }

    /** The latest appointment date that can be due at {@code now}. */
    public LocalDate lastDate(LocalDateTime now) {
        return latestStart(now).toLocalDate();
    }

    /** Starts after this are due at {@code now} (as late as {@link #latestStart}): {@code now} plus the cutoff. */
    public LocalDateTime earliestStart(LocalDateTime now) {
        return now.plus(cutoff);
    }

    /** The latest start that is due at {@code now}: {@code now} plus the lead time. */
    public LocalDateTime latestStart(LocalDateTime now) {
        return now.plus(lead);
    }
}
