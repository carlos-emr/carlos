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
package io.github.carlos_emr.carlos.casemgmt.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

/**
 * Whole calendar days selected in the chart print dialog, with an exclusive upper bound.
 * The next local midnight includes the entire final day even across daylight-saving changes.
 *
 * @param startInclusive first instant to include
 * @param endExclusive first instant to exclude
 * @since 2026-10-03
 */
// Calendar/Date are compatibility boundaries for existing print callers and DAO entities;
// all day-boundary calculations use java.time.
@SuppressWarnings("java:S2143")
public record ChartPrintDateRange(Instant startInclusive, Instant endExclusive) {
    /**
     * Builds the interval in the calendars' time zones without mutating either calendar.
     *
     * @param start first selected calendar day
     * @param end last selected calendar day
     * @return the complete, inclusive range of calendar days
     * @throws IllegalArgumentException if the end precedes the start
     */
    public static ChartPrintDateRange from(Calendar start, Calendar end) {
        Objects.requireNonNull(start, "Print start date is required");
        Objects.requireNonNull(end, "Print end date is required");
        ZoneId zone = start.getTimeZone().toZoneId();
        LocalDate first = start.toInstant().atZone(zone).toLocalDate();
        LocalDate last = end.toInstant().atZone(end.getTimeZone().toZoneId()).toLocalDate();
        if (last.isBefore(first)) {
            throw new IllegalArgumentException("Print end date precedes start date");
        }
        return new ChartPrintDateRange(first.atStartOfDay(zone).toInstant(),
                last.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /**
     * Tests an observation timestamp against the selected days.
     *
     * @param observation observation timestamp, or null for an undated note
     * @return true if the observation falls within the selected days
     */
    public boolean contains(Date observation) {
        if (observation == null) return false;
        Instant instant = observation.toInstant();
        return !instant.isBefore(startInclusive) && instant.isBefore(endExclusive);
    }
}
