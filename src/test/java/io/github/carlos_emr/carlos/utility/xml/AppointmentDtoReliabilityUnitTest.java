/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
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
    void shouldLeaveTransferEndUnchanged_whenCopyingRepeatedly() {
        var transfer = new AppointmentTransfer();
        Calendar start = new GregorianCalendar(2026, Calendar.JULY, 15, 9, 30, 0);
        Calendar end = new GregorianCalendar(2026, Calendar.JULY, 15, 9, 45, 0);
        long originalEnd = end.getTimeInMillis();
        transfer.setAppointmentStartDateTime(start);
        transfer.setAppointmentEndDateTime(end);

        var first = transfer.copyTo(new io.github.carlos_emr.carlos.commn.model.Appointment());
        var second = transfer.copyTo(new io.github.carlos_emr.carlos.commn.model.Appointment());

        assertThat(first.getEndTime().getTime()).isEqualTo(originalEnd - 1);
        assertThat(second.getEndTime()).isEqualTo(first.getEndTime());
        assertThat(end.getTimeInMillis()).as("caller's calendar").isEqualTo(originalEnd);
        assertThat(transfer.getAppointmentEndDateTime().getTimeInMillis()).isEqualTo(originalEnd);
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
