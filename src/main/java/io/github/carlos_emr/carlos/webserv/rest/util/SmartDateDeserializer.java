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
package io.github.carlos_emr.carlos.webserv.rest.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Date;

/**
 * Inverse of {@link SmartDateSerializer}: parses the JSON shapes that serializer
 * emits back into {@link Date} so a value can round-trip through the REST API.
 *
 * <p>{@code SmartDateSerializer} writes a {@code Date} as one of:
 * <ul>
 *   <li>{@code "HH:mm:ss"} string for time-only values (epoch date 1970-01-01),</li>
 *   <li>{@code "yyyy-MM-dd"} string for date-only values (midnight), or</li>
 *   <li>a numeric epoch-millis timestamp otherwise.</li>
 * </ul>
 * Jackson's default {@code Date} deserializer only understands the numeric and
 * ISO-8601 forms, so without this deserializer a client that POSTs back the exact
 * payload the API just returned (e.g. {@code updateAppointment} echoing a
 * {@code "HH:mm:ss"} startTime) fails with {@code InvalidFormatException}.</p>
 *
 * <p>Because this is registered on the shared REST {@code ObjectMapper}, it must also
 * remain backward compatible with what Jackson's default deserializer accepted before
 * it existed — epoch-millis-as-string and ISO-8601 — which is handled by delegating to the
 * mapper's configured {@code DateFormat} (Jackson's {@code StdDateFormat} by default).</p>
 *
 * <p>The {@code "HH:mm:ss"} and {@code "yyyy-MM-dd"} shapes are parsed in the JVM default
 * time zone to mirror {@code SmartDateSerializer}, which formats them with a default-zone
 * {@code SimpleDateFormat}; this keeps those two shapes exactly round-trippable. The
 * formatters are immutable {@link DateTimeFormatter} instances rather than
 * {@code ThreadLocal<SimpleDateFormat>} to avoid classloader leaks on servlet redeploy.</p>
 */
public class SmartDateDeserializer extends JsonDeserializer<Date> {

    // STRICT resolution rejects impossible values (e.g. 2026-02-30, 25:00:00) instead of the
    // default SMART style, which silently clamps them (2026-02-30 -> 2026-02-28). The date pattern
    // uses proleptic-year "uuuu" because STRICT resolution requires it (year-of-era "yyyy" would
    // demand an era field); for the positive years this API handles, it matches the serializer's
    // "yyyy-MM-dd" output exactly.
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    @Override
    public Date deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();

        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        // Epoch-millis timestamp, as written for date+time values. Only integer tokens are
        // accepted; a fractional number is not a valid epoch-millis value and is rejected below
        // (via handleUnexpectedToken) rather than silently truncated to a long.
        if (token == JsonToken.VALUE_NUMBER_INT) {
            return new Date(p.getLongValue());
        }

        if (token == JsonToken.VALUE_STRING) {
            String text = p.getText() == null ? "" : p.getText().trim();
            if (text.isEmpty()) {
                return null;
            }

            // Time-only "HH:mm:ss" (8 chars, second char a colon) → epoch-date + time,
            // matching how SmartDateSerializer round-trips TemporalType.TIME columns.
            if (text.length() == 8 && text.charAt(2) == ':') {
                return parseTime(text);
            }

            // Date-only "yyyy-MM-dd" → local midnight, matching the serializer's DATE output.
            if (text.length() == 10 && text.charAt(4) == '-') {
                return parseDate(text);
            }

            // Backward compatibility: epoch-millis-as-string and ISO-8601 — the shapes Jackson's
            // default Date deserializer accepted before this module existed. Use the mapper's
            // configured DateFormat (StdDateFormat by default) so any mapper-level date-format or
            // timezone configuration is honored; clone it because DateFormat is not thread-safe.
            DateFormat dateFormat = (DateFormat) ctxt.getConfig().getDateFormat().clone();
            try {
                return dateFormat.parse(text);
            } catch (ParseException e) {
                // Do not echo the raw request value in the message — it is untrusted and may be
                // PHI-adjacent in a healthcare API. The chained cause carries parser detail.
                throw new IOException("Unparseable date value", e);
            }
        }

        // Anything else (object/array/boolean): defer to the context's standard error handling.
        return (Date) ctxt.handleUnexpectedToken(Date.class, p);
    }

    private static Date parseTime(String text) throws IOException {
        try {
            LocalTime time = LocalTime.parse(text, TIME_FORMATTER);
            return Date.from(time.atDate(LocalDate.of(1970, Month.JANUARY, 1)).atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            // Do not echo the raw request value (untrusted / possibly PHI-adjacent); the chained
            // cause retains parser detail for diagnosis.
            throw new IOException("Unparseable time value", e);
        }
    }

    private static Date parseDate(String text) throws IOException {
        try {
            LocalDate date = LocalDate.parse(text, DATE_FORMATTER);
            return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            // Do not echo the raw request value (untrusted / possibly PHI-adjacent); the chained
            // cause retains parser detail for diagnosis.
            throw new IOException("Unparseable date value", e);
        }
    }
}
