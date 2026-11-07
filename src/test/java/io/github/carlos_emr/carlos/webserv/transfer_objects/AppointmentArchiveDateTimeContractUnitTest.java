/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.transfer_objects;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("xml")
class AppointmentArchiveDateTimeContractUnitTest extends CarlosUnitTestBase {
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Response {
        public AppointmentArchiveTransfer archive;
    }

    @ParameterizedTest
    @CsvSource({"America/Toronto,6,true,2026-07-15T13:14:15-04:00",
            "America/Toronto,0,true,2026-01-15T13:14:15-05:00",
            "America/Vancouver,6,true,2026-07-15T13:14:15-07:00",
            "Asia/Kolkata,6,true,2026-07-15T13:14:15+05:30",
            "UTC,6,true,2026-07-15T13:14:15Z",
            "America/Toronto,6,false,2026-07-15T00:00:00-04:00"})
    void shouldWritePublishedOffset_whenCalendarZoneFieldsWereNeverSet(
            String zone, int month, boolean time, String expected) throws Exception {
        var calendar = new GregorianCalendar(TimeZone.getTimeZone(zone));
        calendar.clear();
        calendar.set(2026, month, 15);
        if (time) {
            calendar.set(Calendar.HOUR_OF_DAY, 13);
            calendar.set(Calendar.MINUTE, 14);
            calendar.set(Calendar.SECOND, 15);
        }
        String original = calendar.toString();
        var archive = new AppointmentArchiveTransfer();
        archive.setAppointmentStartDateTime(calendar);
        archive.setAppointmentEndDateTime(calendar);
        archive.setCreateDateTime(calendar);
        archive.setUpdateDateTime(calendar);
        var context = JAXBContext.newInstance(AppointmentArchiveTransfer.class, Response.class);
        var response = new Response();
        response.archive = archive;

        for (String xml : List.of(write(context, AppointmentArchiveTransfer.class, archive),
                write(context, Response.class, response))) {
            for (String property : List.of("appointmentStartDateTime", "appointmentEndDateTime",
                    "createDateTime", "updateDateTime")) {
                assertThat(xml).contains("<" + property + ">" + expected + "</" + property + ">");
            }
        }
        assertThat(calendar.toString()).isEqualTo(original);
        String direct = write(context, AppointmentArchiveTransfer.class, archive);
        var read = context.createUnmarshaller().unmarshal(new StreamSource(new StringReader(direct)),
                AppointmentArchiveTransfer.class).getValue();
        assertThat(read.getAppointmentStartDateTime().toInstant())
                .isEqualTo(java.time.OffsetDateTime.parse(expected).toInstant());
        assertThat(calendar.toString()).isEqualTo(original);
    }

    @Test
    void shouldRetainOptionalDates_whenLegacyInputOmitsOrClearsThem() throws Exception {
        var context = JAXBContext.newInstance(AppointmentArchiveTransfer.class);
        var reader = context.createUnmarshaller();
        reader.setEventHandler(event -> false);
        var value = reader.unmarshal(new StreamSource(new StringReader(
                "<value><createDateTime> \t </createDateTime><updateDateTime/></value>")),
                AppointmentArchiveTransfer.class).getValue();
        assertThat(value.getAppointmentStartDateTime()).isNull();
        assertThat(value.getAppointmentEndDateTime()).isNull();
        assertThat(value.getCreateDateTime()).isNull();
        assertThat(value.getUpdateDateTime()).isNull();
        assertThat(write(context, AppointmentArchiveTransfer.class, value))
                .doesNotContain("appointmentStartDateTime", "appointmentEndDateTime", "createDateTime", "updateDateTime");
    }

    private static <T> String write(JAXBContext context, Class<T> type, T value) throws Exception {
        var output = new StringWriter();
        context.createMarshaller().marshal(new JAXBElement<>(new QName("value"), type, value), output);
        return output.toString();
    }
}
