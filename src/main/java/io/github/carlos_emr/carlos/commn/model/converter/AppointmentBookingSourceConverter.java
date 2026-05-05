package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Appointment.BookingSource;
import jakarta.persistence.Converter;

/**
 * AppointmentBookingSourceConverter provides functionality and data models for the AppointmentBookingSourceConverter domain.
 *
 * <p>This class is part of the CARLOS EMR system.
 *
 * @since 2026
 */
@Converter
public class AppointmentBookingSourceConverter extends NullSafeEnumConverter<BookingSource> {
    public AppointmentBookingSourceConverter() {
        super(BookingSource.class, null);
    }
}
