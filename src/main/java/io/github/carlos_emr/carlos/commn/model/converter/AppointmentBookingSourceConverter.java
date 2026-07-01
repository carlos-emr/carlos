package io.github.carlos_emr.carlos.commn.model.converter;

import io.github.carlos_emr.carlos.commn.model.Appointment.BookingSource;
import jakarta.persistence.Converter;


/**
 * JPA attribute converter for appointment booking sources.
 * <p>
 * Translates the enum defining the origin of an appointment (e.g., online, clinic)
 * to its persistent state in the CARLOS EMR database.
 * </p>
 */
@Converter
public class AppointmentBookingSourceConverter extends NullSafeEnumConverter<BookingSource> {
    public AppointmentBookingSourceConverter() {
        // Convert appointment source enum to string for persistence to maintain audit accuracy.
        super(BookingSource.class, null);
    }
}
