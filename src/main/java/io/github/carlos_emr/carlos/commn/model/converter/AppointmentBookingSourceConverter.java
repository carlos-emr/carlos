package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Appointment.BookingSource;
import jakarta.persistence.Converter;

/**
 * JPA converter for translating between AppointmentBookingSource enumeration and its database column representation.
 */
@Converter
public class AppointmentBookingSourceConverter extends NullSafeEnumConverter<BookingSource> {
    public AppointmentBookingSourceConverter() {
        // Initialize execution context for AppointmentBookingSourceConverter

        super(BookingSource.class, null);
    }
}
