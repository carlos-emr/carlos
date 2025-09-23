package ca.openosp.openo.webserv.rest.util;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Custom Jackson Date serializer that mimics the original OpenO EMR behavior:
 * - Dates with time component 00:00:00 are serialized as "yyyy-MM-dd" strings
 * - Dates with actual time components are serialized as epoch timestamps
 * - Respects @JsonFormat annotations (epoch format takes precedence)
 */
public class SmartDateSerializer extends JsonSerializer<Date> implements ContextualSerializer {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private final boolean forceEpoch;

    public SmartDateSerializer() {
        this.forceEpoch = false;
    }

    public SmartDateSerializer(boolean forceEpoch) {
        this.forceEpoch = forceEpoch;
    }

    @Override
    public void serialize(Date date, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (date == null) {
            gen.writeNull();
            return;
        }

        // If forceEpoch is true (from @JsonFormat annotation), always serialize as epoch
        if (forceEpoch) {
            gen.writeNumber(date.getTime());
            return;
        }

        // Check if the date has a time component (not midnight)
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int second = cal.get(Calendar.SECOND);
        int millisecond = cal.get(Calendar.MILLISECOND);

        // If time is exactly midnight (00:00:00.000), treat as date-only and serialize as string
        if (hour == 0 && minute == 0 && second == 0 && millisecond == 0) {
            gen.writeString(DATE_FORMAT.format(date));
        } else {
            // Otherwise, serialize as epoch timestamp
            gen.writeNumber(date.getTime());
        }
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property) {
        if (property != null) {
            JsonFormat.Value format = prov.getAnnotationIntrospector().findFormat(property.getMember());
            if (format != null && format.getShape() == JsonFormat.Shape.NUMBER) {
                return new SmartDateSerializer(true);
            }
        }
        return this;
    }
}