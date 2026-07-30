package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Appointment.BookingSource;
import jakarta.persistence.Converter;

/**
 * Converter class for AppointmentBookingSource to handle database transformations.
 *
 * This class is part of the CARLOS EMR system.
 */


@Converter
public class AppointmentBookingSourceConverter extends NullSafeEnumConverter<BookingSource> {
    public AppointmentBookingSourceConverter() {
        super(BookingSource.class, null);
    }
}
