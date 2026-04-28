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

import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

/**
 * Shared date parsing helpers for CARLOS EMR utility classes that still expose
 * legacy {@link Date}-based APIs.
 *
 * @since 2026-04-28
 */
public final class DateTimeParseUtils {

    private DateTimeParseUtils() {
    }

    /**
     * Parses text with a {@link DateTimeFormatter} and converts the result to a legacy
     * {@link Date}.
     *
     * <p>When parsed text includes a zone or offset, that instant is preserved. When parsed text
     * omits zone/offset information, the system default zone is applied. Partial date patterns such
     * as {@code yyyy} and {@code yyyy-MM} preserve the old {@code SimpleDateFormat} behavior by
     * defaulting missing month/day fields to January/1.</p>
     *
     * @param text the text to parse
     * @param formatter the formatter to use
     * @return the parsed date
     * @throws ParseException if the text cannot be parsed with the provided formatter
     */
    public static Date parseToDate(String text, DateTimeFormatter formatter) throws ParseException {
        try {
            TemporalAccessor parsed = formatter.parseBest(text,
                    ZonedDateTime::from,
                    OffsetDateTime::from,
                    LocalDateTime::from,
                    LocalDate::from,
                    YearMonth::from,
                    Year::from,
                    Instant::from);

            ZoneId zone = ZoneId.systemDefault();
            Instant instant;
            if (parsed instanceof ZonedDateTime) {
                instant = ((ZonedDateTime) parsed).toInstant();
            } else if (parsed instanceof OffsetDateTime) {
                instant = ((OffsetDateTime) parsed).toInstant();
            } else if (parsed instanceof LocalDateTime) {
                instant = ((LocalDateTime) parsed).atZone(zone).toInstant();
            } else if (parsed instanceof LocalDate) {
                instant = ((LocalDate) parsed).atStartOfDay(zone).toInstant();
            } else if (parsed instanceof YearMonth) {
                instant = ((YearMonth) parsed).atDay(1).atStartOfDay(zone).toInstant();
            } else if (parsed instanceof Year) {
                instant = ((Year) parsed).atMonth(1).atDay(1).atStartOfDay(zone).toInstant();
            } else {
                instant = Instant.from(parsed);
            }

            return Date.from(instant);
        } catch (DateTimeParseException e) {
            int idx = Math.max(0, e.getErrorIndex());
            throw (ParseException) new ParseException(e.getMessage(), idx).initCause(e);
        }
    }
}
