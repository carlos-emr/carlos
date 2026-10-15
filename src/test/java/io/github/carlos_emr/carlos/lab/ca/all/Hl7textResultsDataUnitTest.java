/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementMapDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementMap;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

@Tag("unit")
@Tag("lab")
@DisplayName("HL7 measurement timestamps")
@Isolated("Temporarily replaces cached HL7 measurement DAOs")
class Hl7textResultsDataUnitTest extends CarlosUnitTestBase {
    private final Map<Field, Object> originalDaos = new LinkedHashMap<>();
    private MeasurementDao measurementDao;

    @BeforeEach
    void setUpDaos() throws Exception {
        // Register all beans before the class initializes, then replace any cached by earlier tests.
        for (Field field : Hl7textResultsData.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getName().endsWith("Dao")) {
                createAndRegisterMock(field.getType());
            }
        }
        for (Field field : Hl7textResultsData.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getName().endsWith("Dao")) {
                field.setAccessible(true);
                originalDaos.put(field, field.get(null));
                field.set(null, mockedBeans.get(field.getType()));
            }
        }

        measurementDao = (MeasurementDao) mockedBeans.get(MeasurementDao.class);
        doAnswer(invocation -> {
            Measurement measurement = invocation.getArgument(0);
            ReflectionTestUtils.setField(measurement, "id", 1);
            return null;
        }).when(measurementDao).persist(any(Measurement.class));

        MeasurementMap mapping = new MeasurementMap();
        mapping.setIdentCode("A1C");
        MeasurementType type = new MeasurementType();
        type.setMeasuringInstruction("Lab");
        MeasurementMapDao mappingDao = (MeasurementMapDao) mockedBeans.get(MeasurementMapDao.class);
        when(mappingDao.findMeasurements("FLOWSHEET", "3767", "%"))
                .thenReturn(Collections.singletonList(new Object[]{null, mapping, type}));
    }

    @AfterEach
    void restoreDaos() throws Exception {
        for (Map.Entry<Field, Object> entry : originalDaos.entrySet()) {
            entry.getKey().set(null, entry.getValue());
        }
    }

    @ParameterizedTest(name = "dateObserved preserves {0}")
    @MethodSource("timesOfDay")
    void shouldPreserveObservationTime(String time) {
        LocalDateTime observed = LocalDateTime.parse("2026-10-09T" + time);
        LocalDateTime entered = LocalDateTime.parse("2026-10-13T10:05:19");

        Measurement measurement = populateMeasurement("2026-10-09 " + time, entered);

        assertThat(measurement.getDateObserved()).isEqualTo(inDefaultTimeZone(observed));
        assertThat(measurement.getCreateDate()).isEqualTo(inDefaultTimeZone(entered));
    }

    @ParameterizedTest(name = "dateEntered preserves {0}")
    @MethodSource("timesOfDay")
    void shouldPreserveEntryTime(String time) {
        LocalDateTime observed = LocalDateTime.parse("2026-10-09T11:20:29");
        LocalDateTime entered = LocalDateTime.parse("2026-10-13T" + time);

        Measurement measurement = populateMeasurement("2026-10-09 11:20:29", entered);

        assertThat(measurement.getCreateDate()).isEqualTo(inDefaultTimeZone(entered));
        assertThat(measurement.getDateObserved()).isEqualTo(inDefaultTimeZone(observed));
    }

    @ParameterizedTest(name = "dateObserved of {0} is filed as {1}")
    @MethodSource("observationTimesWithoutSeconds")
    void shouldFileObservationTime_whenSecondsOrTimeOmitted(String timestamp, String expected) {
        LocalDateTime entered = LocalDateTime.parse("2026-10-13T10:05:19");

        Measurement measurement = populateMeasurement(timestamp, entered);

        assertThat(measurement.getDateObserved()).isEqualTo(inDefaultTimeZone(LocalDateTime.parse(expected)));
    }

    @Test
    void shouldFileAsObservedNow_whenObservationTimeUnparseable() {
        LocalDateTime entered = LocalDateTime.parse("2026-10-13T10:05:19");
        Date before = new Date();

        Measurement measurement = populateMeasurement("not-a-date", entered);

        assertThat(measurement.getDateObserved()).isBetween(before, new Date(System.currentTimeMillis() + 1000));
    }

    @ParameterizedTest(name = "unparseable observation time {0} is not parsed")
    @ValueSource(strings = {"2026-08-20 09:45:00 UTC", "2026-02-30 09:45", "2026-08-20 25:00", "garbage", ""})
    void shouldRejectObservationTime_whenMalformedOrTrailingText(String timestamp) {
        assertThat(Hl7textResultsData.parseObservationDate(timestamp)).isNull();
    }

    private static Stream<Arguments> observationTimesWithoutSeconds() {
        return Stream.of(
                Arguments.of("2026-08-20 09:45:00", "2026-08-20T09:45:00"),
                Arguments.of("2026-08-20 09:45", "2026-08-20T09:45:00"),
                Arguments.of("2026-08-20 12:15", "2026-08-20T12:15:00"),
                Arguments.of("2026-08-20 15:30", "2026-08-20T15:30:00"),
                Arguments.of("2026-08-20 09", "2026-08-20T09:00:00"),
                Arguments.of("2026-08-20", "2026-08-20T00:00:00"));
    }

    private static Stream<String> timesOfDay() {
        return Stream.of("00:30", "11:59", "12:00", "12:15", "12:59", "13:00", "15:45", "23:59")
                .flatMap(time -> Stream.of(time + ":00", time + ":37"));
    }

    private static Date inDefaultTimeZone(LocalDateTime timestamp) {
        return Date.from(timestamp.atZone(ZoneId.systemDefault()).toInstant());
    }

    private Measurement populateMeasurement(String observationTimestamp, LocalDateTime entryTimestamp) {
        MessageHandler handler = mock(MessageHandler.class);
        when(handler.getOBRCount()).thenReturn(1);
        when(handler.getOBXCount(0)).thenReturn(1);
        when(handler.getOBXResult(0, 0)).thenReturn("6.1");
        when(handler.getOBXName(0, 0)).thenReturn("Hemoglobin A1C");
        when(handler.getOBXIdentifier(0, 0)).thenReturn("3767");
        when(handler.getOBXReferenceRange(0, 0)).thenReturn("");
        when(handler.getTimeStamp(0, 0)).thenReturn(observationTimestamp);

        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("HL7_LAB_MEASUREMENT_FILTER", ".*")).thenReturn(".*");
        when(properties.getProperty("MAP_BY_IDENTIFIER_AND_NAME", "false")).thenReturn("false");
        Calendar entryCalendar = GregorianCalendar.from(entryTimestamp.atZone(ZoneId.systemDefault()));

        try (MockedStatic<Factory> factory = mockStatic(Factory.class);
             MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class);
             MockedStatic<Calendar> clock = mockStatic(Calendar.class, CALLS_REAL_METHODS)) {
            factory.when(() -> Factory.getHandler("4616")).thenReturn(handler);
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            clock.when(Calendar::getInstance).thenReturn(entryCalendar);

            Hl7textResultsData.populateMeasurementsTable("4616", "42");
        }

        ArgumentCaptor<Measurement> measurement = ArgumentCaptor.forClass(Measurement.class);
        verify(measurementDao).persist(measurement.capture());
        assertThat(measurement.getValue().getType()).isEqualTo("A1C");
        return measurement.getValue();
    }
}
