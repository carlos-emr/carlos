package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Appointment.BookingSource;
import jakarta.persistence.Converter;

/**
 * JPA attribute converter mapping the origin of an appointment booking.
 * Differentiates between web portal, front desk, and automated integrations.
 */
@Converter
public class AppointmentBookingSourceConverter extends NullSafeEnumConverter<BookingSource> {
    public AppointmentBookingSourceConverter() {
        // Translate the origin context of the booking to track portal vs manual entries
        super(BookingSource.class, null);
    }
}
