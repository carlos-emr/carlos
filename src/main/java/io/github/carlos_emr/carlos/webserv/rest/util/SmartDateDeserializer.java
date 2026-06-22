package io.github.carlos_emr.carlos.webserv.rest.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
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
 */
public class SmartDateDeserializer extends JsonDeserializer<Date> {

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));
    private static final ThreadLocal<SimpleDateFormat> TIME_FORMAT = ThreadLocal.withInitial(() -> new SimpleDateFormat("HH:mm:ss"));

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
                return parse(TIME_FORMAT.get(), text, p);
            }

            // Date-only "yyyy-MM-dd".
            if (text.length() == 10 && text.charAt(4) == '-') {
                return parse(DATE_FORMAT.get(), text, p);
            }

            // Numeric epoch encoded as a string.
            try {
                return new Date(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                // fall through to ctxt's default handling below
            }
        }

        // Anything else: defer to the context's standard Date coercion (handles ISO-8601).
        return (Date) ctxt.handleUnexpectedToken(Date.class, p);
    }

    private Date parse(SimpleDateFormat format, String text, JsonParser p) throws IOException {
        try {
            return format.parse(text);
        } catch (ParseException e) {
            throw new IOException("Unparseable date: \"" + text + "\"", e);
        }
    }
}
