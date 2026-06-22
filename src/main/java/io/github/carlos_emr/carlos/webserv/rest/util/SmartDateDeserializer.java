package io.github.carlos_emr.carlos.webserv.rest.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.util.StdDateFormat;

import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
 * it existed — epoch-millis-as-string and ISO-8601 — which is handled via the
 * {@link StdDateFormat} fallback.</p>
 *
 * <p>The {@code "HH:mm:ss"} and {@code "yyyy-MM-dd"} shapes are parsed in the JVM default
 * time zone to mirror {@code SmartDateSerializer}, which formats them with a default-zone
 * {@code SimpleDateFormat}; this keeps those two shapes exactly round-trippable. The
 * formatters are immutable {@link DateTimeFormatter} instances rather than
 * {@code ThreadLocal<SimpleDateFormat>} to avoid classloader leaks on servlet redeploy.</p>
 */
public class SmartDateDeserializer extends JsonDeserializer<Date> {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public Date deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();

        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        // Epoch-millis timestamp, as written for date+time values.
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
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
            // default Date deserializer (StdDateFormat) accepted before this module existed.
            try {
                return new StdDateFormat().parse(text);
            } catch (ParseException e) {
                throw new IOException("Unparseable date: \"" + text + "\"", e);
            }
        }

        // Anything else (object/array/boolean): defer to the context's standard error handling.
        return (Date) ctxt.handleUnexpectedToken(Date.class, p);
    }

    private static Date parseTime(String text) throws IOException {
        try {
            LocalTime time = LocalTime.parse(text, TIME_FORMATTER);
            return Date.from(time.atDate(LocalDate.of(1970, 1, 1)).atZone(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            throw new IOException("Unparseable time: \"" + text + "\"", e);
        }
    }

    private static Date parseDate(String text) throws IOException {
        try {
            LocalDate date = LocalDate.parse(text, DATE_FORMATTER);
            return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            throw new IOException("Unparseable date: \"" + text + "\"", e);
        }
    }
}
