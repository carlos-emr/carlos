/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.transfer_objects;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.xml.XmlBaselineAssertions;
import java.util.Calendar;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("xml")
@org.junit.jupiter.api.parallel.ResourceLock("java.util.TimeZone")
class AppointmentArchiveTransferXmlUnitTest extends CarlosUnitTestBase {
    @org.junit.jupiter.api.Test
    void shouldKeepLocationLookupMetadataInJson_withoutChangingPublishedXml() throws Exception {
        for (Class<?> type : java.util.List.of(AppointmentTransfer.class, AppointmentArchiveTransfer.class)) {
            Object value = type.getConstructor().newInstance();
            type.getMethod("setLocationCode", Integer.class).invoke(value, 7);
            type.getMethod("setLocation", String.class).invoke(value, "Booking-time location");
            for (var mapper : java.util.List.of(new com.fasterxml.jackson.databind.ObjectMapper(),
                    io.github.carlos_emr.carlos.webserv.rest.util.ProductionRestObjectMappers.oauthSurface(),
                    io.github.carlos_emr.carlos.webserv.rest.util.ProductionRestObjectMappers.sessionSurface())) {
                var json = mapper.valueToTree(value);
                assertThat(json.path("locationCode").intValue()).isEqualTo(7);
                assertThat(json.path("location").textValue()).isEqualTo("Booking-time location");
            }
            var xml = new java.io.StringWriter();
            var context = jakarta.xml.bind.JAXBContext.newInstance(type);
            marshal(context, type, value, xml);
            assertThat(xml.toString()).contains("<location>Booking-time location</location>").doesNotContain("locationCode");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void marshal(jakarta.xml.bind.JAXBContext context, Class type, Object value,
            java.io.StringWriter writer) throws Exception {
        context.createMarshaller().marshal(new jakarta.xml.bind.JAXBElement(
                new javax.xml.namespace.QName("urn:carlos:jaxb-compatibility", "value"), type, value), writer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "populated"})
    void shouldRetainArchivedAppointmentFields_whenReadingAndWritingLegacyXml(String sample) throws Exception {
        var value = XmlBaselineAssertions.readBaseline(AppointmentArchiveTransfer.class, sample);
        // Continue reading the unchanged offset-free MOXy input; writing follows
        // the published beta6 date-time contract, including the calendar's offset.
        var written = new AppointmentArchiveTransfer();
        if ("populated".equals(sample)) {
            for (var setter : AppointmentArchiveTransfer.class.getMethods()) {
                if (!setter.getName().startsWith("set") || setter.getParameterCount() != 1) continue;
                Class<?> type = setter.getParameterTypes()[0];
                Object field;
                if (type == String.class) field = "7";
                else if (type == int.class || type == Integer.class) field = 7;
                else if (Calendar.class.isAssignableFrom(type)) {
                    var calendar = new java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("America/Toronto"));
                    calendar.clear();
                    calendar.set(2026, Calendar.JULY, 15, 13, 14, 15);
                    field = calendar;
                } else if (type == io.github.carlos_emr.carlos.commn.model.Appointment.BookingSource.class) {
                    field = io.github.carlos_emr.carlos.commn.model.Appointment.BookingSource.OSCAR;
                } else throw new AssertionError("New archive field requires an explicit wire oracle: " + setter.getName());
                setter.invoke(written, field);
            }
        }
        XmlBaselineAssertions.assertBaselineWrite(AppointmentArchiveTransfer.class, written, sample);
        if ("populated".equals(sample)) {
            assertThat(value.getId()).isEqualTo(7);
            assertThat(value.getAppointmentId()).isEqualTo(7);
            assertThat(value.getNotes()).isEqualTo("7");
            assertThat(value.getAppointmentStartDateTime().get(Calendar.HOUR_OF_DAY)).isEqualTo(13);
            assertThat(value.getAppointmentStartDateTime().get(Calendar.YEAR)).isEqualTo(2026);
        } else {
            assertThat(value.getAppointmentStartDateTime()).isNull();
            assertThat(value.getDemographicNo()).isZero();
        }
    }
}
