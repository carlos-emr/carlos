package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Appointment.BookingSource;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter for appointment booking sources.
 * Maps the enumeration representing the origin of an appointment booking
 * (e.g., ONLINE, CLINIC, PHONE) to the corresponding database column.
 */
@Converter
public class AppointmentBookingSourceConverter extends NullSafeEnumConverter<BookingSource> {
    public AppointmentBookingSourceConverter() {
        super(BookingSource.class, null);
    }
}
