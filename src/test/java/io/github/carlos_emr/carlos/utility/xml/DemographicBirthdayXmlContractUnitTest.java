/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicExt;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.SchedulingResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationRequestSearchResult;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationResponseSearchResult;
import io.github.carlos_emr.carlos.webserv.rest.util.ProductionRestObjectMappers;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.TimeZone;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xml.sax.InputSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("xml")
@ResourceLock("java.util.TimeZone")
class DemographicBirthdayXmlContractUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @CsvSource({"America/Toronto,07,1980-07-15T00:00:00-04:00", "America/Toronto,01,1980-01-15T00:00:00-05:00",
            "America/Vancouver,07,1980-07-15T00:00:00-07:00", "America/Vancouver,01,1980-01-15T00:00:00-08:00",
            "UTC,07,1980-07-15T00:00:00Z", "Asia/Kolkata,07,1980-07-15T00:00:00+05:30"})
    void shouldWritePublishedBirthdayDateTime_forDirectAndNestedPatients(String zone, String month, String expected)
            throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            assertBirthday(Demographic.class, patient(new Demographic(), month), expected);
            var extension = new DemographicExt();
            extension.setDemographic(patient(new Demographic(), month));
            assertBirthday(DemographicExt.class, extension, expected);
            var appointment = new AppointmentTo1();
            appointment.setDemographic(patient(new Demographic(), month));
            assertBirthday(AppointmentTo1.class, appointment, expected);
            var request = new ConsultationRequestSearchResult();
            request.setDemographic(patient(new Demographic(), month));
            assertBirthday(ConsultationRequestSearchResult.class, request, expected);
            var response = new ConsultationResponseSearchResult();
            response.setDemographic(patient(new Demographic(), month));
            assertBirthday(ConsultationResponseSearchResult.class, response, expected);
            var schedule = new SchedulingResponse();
            schedule.setAppointment(appointment);
            schedule.setAppointments(List.of(appointment));
            assertBirthday(SchedulingResponse.class, schedule, expected, 2);
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void shouldKeepNumericBirthdayJson_onBothProductionRestSurfaces() throws Exception {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto"));
            var patient = patient(new Demographic(), "07");
            long expected = patient.getBirthDay().getTimeInMillis();
            for (var mapper : List.of(ProductionRestObjectMappers.oauthSurface(), ProductionRestObjectMappers.sessionSurface())) {
                var birthday = mapper.valueToTree(patient).path("birthDay");
                assertThat(birthday.isIntegralNumber()).isTrue();
                assertThat(birthday.longValue()).isEqualTo(expected);
                var read = mapper.readValue("{\"birthDay\":" + expected + "}", Demographic.class);
                assertThat(read.getBirthDay().getTimeInMillis()).isEqualTo(expected);
                var withoutBirthday = new Demographic();
                withoutBirthday.setFirstName("FAKE");
                withoutBirthday.setLastName("NO_BIRTHDAY");
                assertThat(mapper.valueToTree(withoutBirthday).path("birthDay").isNull()).isTrue();
            }
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    @Test
    void shouldReadLegacyDateOnlyBirthday_whenAnUnchangedClientSubmitsIt() throws Exception {
        var context = JAXBContext.newInstance(Demographic.class);
        var reader = context.createUnmarshaller();
        reader.setEventHandler(event -> false);
        var patient = reader.unmarshal(new StreamSource(new StringReader(
                "<value><birthDay>1980-07-15</birthDay></value>")), Demographic.class).getValue();

        assertThat(patient.getYearOfBirth()).isEqualTo("1980");
        assertThat(patient.getMonthOfBirth()).isEqualTo("07");
        assertThat(patient.getDateOfBirth()).isEqualTo("15");
    }

    @Test
    void shouldOmitBirthday_whenDateComponentsAreAbsent() throws Exception {
        var context = JAXBContext.newInstance(Demographic.class);
        var patient = new Demographic();
        var output = new StringWriter();
        context.createMarshaller().marshal(new JAXBElement<>(new QName("value"), Demographic.class, patient), output);

        assertThat(output.toString()).doesNotContain("<birthDay>");
    }

    private static <T extends Demographic> T patient(T patient, String month) {
        patient.setFirstName("FAKE");
        patient.setLastName("BIRTHDAY_CONTRACT");
        patient.setYearOfBirth("1980");
        patient.setMonthOfBirth(month);
        patient.setDateOfBirth("15");
        return patient;
    }

    private static <T> void assertBirthday(Class<T> type, T value, String expected) throws Exception {
        assertBirthday(type, value, expected, 1);
    }

    private static <T> void assertBirthday(Class<T> type, T value, String expected, int count) throws Exception {
        var context = JAXBContext.newInstance(type);
        var output = new StringWriter();
        context.createMarshaller().marshal(new JAXBElement<>(new QName("value"), type, value), output);
        var document = XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder()
                .parse(new InputSource(new StringReader(output.toString())));
        var birthdays = document.getElementsByTagName("birthDay");
        assertThat(birthdays.getLength()).isEqualTo(count);
        for (int i = 0; i < count; i++) {
            String birthday = birthdays.item(i).getTextContent();
            assertThat(birthday).isEqualTo(expected);
            assertThat(DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(birthday).getXMLSchemaType())
                    .isEqualTo(DatatypeConstants.DATETIME);
        }
    }
}
