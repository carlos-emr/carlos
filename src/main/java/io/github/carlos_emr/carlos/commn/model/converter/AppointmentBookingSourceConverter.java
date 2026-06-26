package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Appointment.BookingSource;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter implementation for AppointmentBookingSourceConverter, mapping entity attributes to database columns.
 */
@Converter
public class AppointmentBookingSourceConverter extends NullSafeEnumConverter<BookingSource> {
    // Handles the conversion logic for AppointmentBookingSourceConverter to maintain data persistence

    public AppointmentBookingSourceConverter() {
        super(BookingSource.class, null);
    }
}
