package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Appointment.BookingSource;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter specifically for mapping the AppointmentBookingSource enum to its database column.
 * Ensures that AppointmentBookingSource values are safely persisted and retrieved, defaulting to a fallback if null.
 */

@Converter
public class AppointmentBookingSourceConverter extends NullSafeEnumConverter<BookingSource> {
    public AppointmentBookingSourceConverter() {
        // Initialize the converter mapping to the target enum class
        super(BookingSource.class, null);
    }
}
