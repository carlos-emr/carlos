package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Appointment.BookingSource;
import jakarta.persistence.Converter;


/**
 * JPA AttributeConverter mapping the AppointmentBookingSource enumeration for database persistence.
 */
@Converter
public class AppointmentBookingSourceConverter extends NullSafeEnumConverter<BookingSource> {
    public AppointmentBookingSourceConverter() {
        super(BookingSource.class, null);
    }
}
