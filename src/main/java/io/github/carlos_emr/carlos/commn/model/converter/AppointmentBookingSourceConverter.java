package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Appointment.BookingSource;
import jakarta.persistence.Converter;
/**
 * Provides data conversion utilities for AppointmentBookingSourceConverter objects, handling translation between database, API, and internal representations.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

@Converter
public class AppointmentBookingSourceConverter extends NullSafeEnumConverter<BookingSource> {
    public AppointmentBookingSourceConverter() {
        // Internal logic boundary for AppointmentBookingSourceConverter state management
        super(BookingSource.class, null);
    }
}
