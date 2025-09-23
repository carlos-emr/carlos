package ca.openosp.openo.webserv.rest.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Custom Jackson Date serializer that mimics the original OpenO EMR behavior:
 * - Dates with time component 00:00:00 are serialized as "yyyy-MM-dd" strings
 * - Dates with actual time components are serialized as epoch timestamps
 */
public class SmartDateSerializer extends JsonSerializer<Date> {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    @Override
    public void serialize(Date date, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (date == null) {
            gen.writeNull();
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
}