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

import java.io.StringWriter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import javax.xml.namespace.QName;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.webserv.transfer_objects.AppointmentTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.CalendarScheduleCodePairTransfer;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Calendar lexical forms of the SOAP contract.
 *
 * <p>The expected strings for the transfer objects were captured by marshalling the unmodified
 * release classes (no package-level adapters) through EclipseLink MOXy 5.0.0; Glassfish 4.0.9
 * without an adapter writes the same strings for those shapes. They are the wire format SOAP
 * clients have always received, so a change here is a contract change.
 */
@Tag("unit")
@Tag("xml")
@org.junit.jupiter.api.parallel.ResourceLock("java.util.TimeZone")
class CalendarXmlAdapterUnitTest {
    private static final TimeZone TORONTO = TimeZone.getTimeZone("America/Toronto");
    private static TimeZone previousDefault;

    @BeforeAll
    static void useTorontoAsLocalZone() {
        previousDefault = TimeZone.getDefault();
        TimeZone.setDefault(TORONTO);
    }

    @AfterAll
    static void restoreLocalZone() {
        TimeZone.setDefault(previousDefault);
    }

    @Test
    void shouldWriteDateOnly_whenNoTimeFieldIsSet() {
        GregorianCalendar birthday = new GregorianCalendar();
        birthday.clear();
        birthday.set(1980, Calendar.JULY, 15);
        birthday.getTimeInMillis();
        assertThat(new CalendarXmlAdapter().marshal(birthday)).isEqualTo("1980-07-15");
    }

    @Test
    void shouldOmitOffset_whenZoneFieldsAreNotSet() {
        GregorianCalendar cleared = new GregorianCalendar(TORONTO);
        cleared.clear();
        cleared.set(2026, Calendar.JULY, 15, 13, 14, 15);
        assertThat(new CalendarXmlAdapter().marshal(cleared)).isEqualTo("2026-07-15T13:14:15");
    }

    @Test
    void shouldKeepOffsetAndMidnightTime_whenAllFieldsAreSet() {
        CalendarXmlAdapter adapter = new CalendarXmlAdapter();
        assertThat(adapter.marshal(at(2026, Calendar.JULY, 15, 9, 30, 0, 0))).isEqualTo("2026-07-15T09:30:00-04:00");
        assertThat(adapter.marshal(at(2026, Calendar.JULY, 15, 0, 0, 0, 0))).isEqualTo("2026-07-15T00:00:00-04:00");
        assertThat(adapter.marshal(at(2026, Calendar.JANUARY, 15, 0, 0, 0, 0))).isEqualTo("2026-01-15T00:00:00-05:00");
        assertThat(adapter.marshal(at(2026, Calendar.JULY, 15, 0, 0, 0, 120))).isEqualTo("2026-07-15T00:00:00.12-04:00");
        GregorianCalendar gmt = at(2026, Calendar.JULY, 15, 20, 0, 0, 0);
        assertThat(adapter.marshal(AppointmentTransfer.setToGMTIfRequired(gmt, true))).isEqualTo("2026-07-16T00:00:00Z");
    }

    @Test
    void shouldRoundTrip_throughUnmarshal() {
        CalendarXmlAdapter adapter = new CalendarXmlAdapter();
        for (String value : new String[]{"2026-07-15T09:30:00-04:00", "2026-07-16T00:00:00Z", "2026-07-15T13:14:15", "1980-07-15"}) {
            Calendar read = adapter.unmarshal(value);
            assertThat(read).isNotNull();
            assertThat(adapter.marshal(read)).as(value).isEqualTo(value.equals("1980-07-15")
                    ? "1980-07-15T00:00:00-04:00" : value.equals("2026-07-15T13:14:15")
                    ? "2026-07-15T13:14:15-04:00" : value);
        }
        assertThat(adapter.unmarshal(null)).isNull();
        assertThat(adapter.marshal(null)).isNull();
    }

    @Test
    void shouldPreserveAppointmentDateTimes_asMoxyWroteThem() throws Exception {
        JAXBContext context = JAXBContext.newInstance(AppointmentTransfer.class);
        String local = xml(context, AppointmentTransfer.toTransfer(appointment(9, 30), false));
        assertThat(local).contains("<appointmentStartDateTime>2026-07-15T09:30:00-04:00</appointmentStartDateTime>",
                "<appointmentEndDateTime>2026-07-15T09:45:00-04:00</appointmentEndDateTime>",
                "<createDateTime>2026-07-15T00:00:00-04:00</createDateTime>",
                "<updateDateTime>2026-07-15T08:05:09-04:00</updateDateTime>");

        String gmt = xml(context, AppointmentTransfer.toTransfer(appointment(9, 30), true));
        assertThat(gmt).contains("<appointmentStartDateTime>2026-07-15T13:30:00Z</appointmentStartDateTime>",
                "<appointmentEndDateTime>2026-07-15T13:45:00Z</appointmentEndDateTime>",
                "<createDateTime>2026-07-15T04:00:00Z</createDateTime>",
                "<updateDateTime>2026-07-15T12:05:09Z</updateDateTime>");

        String midnight = xml(context, AppointmentTransfer.toTransfer(appointment(0, 0), false));
        assertThat(midnight).contains("<appointmentStartDateTime>2026-07-15T00:00:00-04:00</appointmentStartDateTime>",
                "<appointmentEndDateTime>2026-07-15T00:15:00-04:00</appointmentEndDateTime>");
    }

    @Test
    void shouldPreserveScheduleSlotDateTimes_asMoxyWroteThem() throws Exception {
        JAXBContext context = JAXBContext.newInstance(CalendarScheduleCodePairTransfer.class);
        Map<Calendar, Character> slots = new TreeMap<>();
        slots.put(at(2026, Calendar.JULY, 15, 0, 0, 0, 0), 'A');
        slots.put(at(2026, Calendar.JULY, 15, 8, 0, 0, 0), 'B');
        CalendarScheduleCodePairTransfer[] transfers = CalendarScheduleCodePairTransfer.toTransfer(slots);
        assertThat(xml(context, transfers[0])).contains("<date>2026-07-15T00:00:00-04:00</date>");
        assertThat(xml(context, transfers[1])).contains("<date>2026-07-15T08:00:00-04:00</date>");
    }

    private static GregorianCalendar at(int year, int month, int day, int hour, int minute, int second, int millis) {
        GregorianCalendar source = new GregorianCalendar(TORONTO);
        source.set(year, month, day, hour, minute, second);
        source.set(Calendar.MILLISECOND, millis);
        // The production path: DateUtils.toGregorianCalendar copies an instant with setTime.
        GregorianCalendar calendar = new GregorianCalendar(TORONTO);
        calendar.setTime(source.getTime());
        return calendar;
    }

    private static Appointment appointment(int hour, int minute) {
        Appointment appointment = new Appointment();
        appointment.setId(7);
        appointment.setProviderNo("999998");
        appointment.setDemographicNo(1);
        appointment.setAppointmentDate(new GregorianCalendar(2026, Calendar.JULY, 15).getTime());
        appointment.setStartTime(new GregorianCalendar(1970, Calendar.JANUARY, 1, hour, minute, 0).getTime());
        appointment.setEndTime(new GregorianCalendar(1970, Calendar.JANUARY, 1, hour, minute + 15, 0).getTime());
        appointment.setCreateDateTime(new GregorianCalendar(2026, Calendar.JULY, 15, 0, 0, 0).getTime());
        appointment.setUpdateDateTime(new GregorianCalendar(2026, Calendar.JULY, 15, 8, 5, 9).getTime());
        return appointment;
    }

    @SuppressWarnings("unchecked")
    private static String xml(JAXBContext context, Object value) throws Exception {
        Marshaller marshaller = context.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
        StringWriter writer = new StringWriter();
        marshaller.marshal(new JAXBElement<>(new QName("value"), (Class<Object>) value.getClass(), value), writer);
        return writer.toString();
    }
}
