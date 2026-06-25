package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Appointment.BookingSource;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for the BookingSource enum type.
 * Ensures safe conversion between database values and the corresponding enumeration,
 * particularly handling null or empty string edge cases gracefully.
 */
@Converter
public class AppointmentBookingSourceConverter extends NullSafeEnumConverter<BookingSource> {
    // Initialize the null-safe converter with the specific BookingSource class.
    public AppointmentBookingSourceConverter() {
        super(BookingSource.class, null);
    }
}
