package io.github.carlos_emr.carlos.utility;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.Calendar;

/**
 * Jackson JSON serializer that converts {@link java.sql.Date} values into a JavaScript-friendly
 * object format with individual date/time component fields.
 *
 * <p>Produces JSON objects with fields: {@code year}, {@code month} (1-based), {@code day},
 * {@code hours}, {@code minutes}, {@code seconds}, and {@code milliseconds}.
 *
 * @since 2026-03-17
 */
public class JsDateSerializer extends JsonSerializer<java.sql.Date> {

    /**
     * Serializes a {@link java.sql.Date} into a JSON object with individual date/time fields.
     *
     * @param value       java.sql.Date the date value to serialize
     * @param gen         JsonGenerator the JSON output generator
     * @param serializers SerializerProvider the serializer provider
     * @throws IOException if JSON writing fails
     */
    @Override
    public void serialize(java.sql.Date value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        
        Calendar cal = Calendar.getInstance();
        cal.setTime(value);
        
        gen.writeStartObject();
        gen.writeNumberField("minutes", cal.get(Calendar.MINUTE));
        gen.writeNumberField("seconds", cal.get(Calendar.SECOND));
        gen.writeNumberField("hours", cal.get(Calendar.HOUR_OF_DAY));
        gen.writeNumberField("month", cal.get(Calendar.MONTH) + 1); // Calendar months are 0-based
        gen.writeNumberField("year", cal.get(Calendar.YEAR));
        gen.writeNumberField("day", cal.get(Calendar.DAY_OF_MONTH));
        gen.writeNumberField("milliseconds", cal.get(Calendar.MILLISECOND));
        gen.writeEndObject();
    }
}