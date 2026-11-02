/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.utility.xml;

import java.util.Calendar;
import java.util.GregorianCalendar;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentExtTo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.AppointmentTransfer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@Tag("unit")
class AppointmentDtoReliabilityUnitTest {
    @Test
    void shouldRejectNullCopyTarget_beforeChangingAppointmentDates() {
        var transfer = new AppointmentTransfer();
        Calendar end = new GregorianCalendar(2026, Calendar.JULY, 15, 13, 14, 15);
        long originalEnd = end.getTimeInMillis();
        transfer.setAppointmentStartDateTime((Calendar) end.clone());
        transfer.setAppointmentEndDateTime(end);

        assertThatIllegalArgumentException().isThrownBy(() -> transfer.copyTo(null))
                .withMessage("Target must not be null");
        assertThat(end.getTimeInMillis()).isEqualTo(originalEnd);
    }

    @Test
    void shouldDescribeAppointment_whenDatesAreAbsent() {
        var appointment = new AppointmentExtTo();
        appointment.setAppointmentNo(7);
        assertThat(appointment.toString()).startsWith("AppointmentExtTo2 [appointmentNo=7,")
                .contains(", appointmentDate=null, startTime=null,").endsWith("]");
    }

    @Test
    void shouldRetainDescriptionFields_whenDatesArePresent() {
        var appointment = new AppointmentExtTo();
        appointment.setAppointmentNo(7);
        var date = new GregorianCalendar(2026, Calendar.JULY, 15, 13, 14, 15).getTime();
        appointment.setAppointmentDate(date);
        appointment.setStartTime(date);
        assertThat(appointment.toString()).startsWith("AppointmentExtTo2 [appointmentNo=7,")
                .contains(", appointmentDate=2026-07-15, startTime=2026-07-15,").endsWith("]");
    }
}
