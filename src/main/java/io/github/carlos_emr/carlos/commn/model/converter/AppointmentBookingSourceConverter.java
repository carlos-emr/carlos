package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Appointment.BookingSource;
import jakarta.persistence.Converter;
/**
 * JPA attribute converter for appointment booking sources.
 *
 * <p>Tracks whether an appointment was booked internally, via a patient portal,
 * or through an external integration.</p>
 */

@Converter
public class AppointmentBookingSourceConverter extends NullSafeEnumConverter<BookingSource> {
    // Portal bookings must be clearly distinguished from clinic-created appointments
    public AppointmentBookingSourceConverter() {
        super(BookingSource.class, null);
    }
}
