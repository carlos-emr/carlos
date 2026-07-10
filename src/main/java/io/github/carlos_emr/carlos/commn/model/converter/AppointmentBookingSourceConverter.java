package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Appointment.BookingSource;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for appointment booking sources.
 * Translates between the database representation and the BookingSource enum to track origin of appointments.
 *
 * @since 2026-07-09
 */

@Converter
public class AppointmentBookingSourceConverter extends NullSafeEnumConverter<BookingSource> {
    public AppointmentBookingSourceConverter() {
        super(BookingSource.class, null);
    }
}
