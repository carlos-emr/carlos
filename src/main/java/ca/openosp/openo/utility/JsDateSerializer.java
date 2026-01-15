package ca.openosp.openo.utility;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.Calendar;

public class JsDateSerializer extends JsonSerializer<java.sql.Date> {
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