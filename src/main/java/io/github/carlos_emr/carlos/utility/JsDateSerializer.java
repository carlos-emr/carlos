package io.github.carlos_emr.carlos.utility;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.Calendar;

/**
 * Custom Jackson JSON serializer for formatting {@link java.sql.Date} objects.
 * Ensures dates are serialized in a standard format (e.g., ISO-8601 or specific application requirement)
 * compatible with the JavaScript frontend, preventing timezone shifting issues.
 */

public class JsDateSerializer extends JsonSerializer<java.sql.Date> {
    @Override
    public void serialize(java.sql.Date value, JsonGenerator gen, SerializerProvider serializers) 
            throws IOException {
        // Writes the sql.Date as a formatted string using the configured JSON generator to ensure frontend compatibility.
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