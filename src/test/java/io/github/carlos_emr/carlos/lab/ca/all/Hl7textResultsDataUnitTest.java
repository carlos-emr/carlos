/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
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
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

@Tag("unit")
@Tag("lab")
@DisplayName("HL7 lab results filed as measurements")
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

    @Test
    void shouldFileWholeValue_whenFilteredValueFillsDataField() {
        String longest = "1".repeat(Measurement.DATA_FIELD_MAX_LENGTH);

        Measurement measurement = fileResult(longest, ".*", "2026-10-09 11:20:29", LocalDateTime.parse("2026-10-13T10:05:19"));

        assertThat(measurement).isNotNull();
        assertThat(measurement.getDataField()).isEqualTo(longest);
    }

    @Test
    void shouldFileWholeValue_whenSupplementaryCharactersFitDataField() {
        // 255 characters outside the Basic Multilingual Plane are 510 UTF-16 units; the column counts characters.
        String longest = "\uD835\uDFCF".repeat(Measurement.DATA_FIELD_MAX_LENGTH);

        Measurement measurement = fileResult(longest, ".*", "2026-10-09 11:20:29", LocalDateTime.parse("2026-10-13T10:05:19"));

        assertThat(measurement).isNotNull();
        assertThat(measurement.getDataField()).isEqualTo(longest);
    }

    @Test
    void shouldSkipMeasurement_whenFilteredValueIsLongerThanDataField() {
        // A packaged install (MariaDB sql_mode="", jdbcCompliantTruncation=false in the JDBC URL) would cut a longer
        // value to the column silently; a strict server would roll back the whole lab instead (#4613).
        String tooLong = "1".repeat(Measurement.DATA_FIELD_MAX_LENGTH + 1);

        try (LogCapture log = LogCapture.forLogger(Hl7textResultsData.class)) {
            assertThat(fileResult(tooLong, ".*", "2026-10-09 11:20:29", LocalDateTime.parse("2026-10-13T10:05:19")))
                    .isNull();

            // The warning says why and where, and never carries the result itself.
            assertThat(log.messages()).anySatisfy(message -> assertThat(message)
                    .contains((Measurement.DATA_FIELD_MAX_LENGTH + 1) + " characters", "lab 4616", "OBX 0-0")
                    .doesNotContain("1111111111"));
        }
        verify(measurementDao, never()).persist(any(Measurement.class));
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
        Measurement measurement = fileResult("6.1", ".*", observationTimestamp, entryTimestamp);
        assertThat(measurement).isNotNull();
        return measurement;
    }

    /**
     * Files one A1C result through {@link Hl7textResultsData#populateMeasurementsTable} with the given
     * {@code HL7_LAB_MEASUREMENT_FILTER}, and returns the measurement it persisted, or {@code null} when it
     * persisted none.
     */
    private Measurement fileResult(String result, String filter, String observationTimestamp,
            LocalDateTime entryTimestamp) {
        MessageHandler handler = mock(MessageHandler.class);
        when(handler.getOBRCount()).thenReturn(1);
        when(handler.getOBXCount(0)).thenReturn(1);
        when(handler.getOBXResult(0, 0)).thenReturn(result);
        when(handler.getOBXName(0, 0)).thenReturn("Hemoglobin A1C");
        when(handler.getOBXIdentifier(0, 0)).thenReturn("3767");
        when(handler.getOBXReferenceRange(0, 0)).thenReturn("");
        when(handler.getTimeStamp(0, 0)).thenReturn(observationTimestamp);

        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty("HL7_LAB_MEASUREMENT_FILTER", ".*")).thenReturn(filter);
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
        verify(measurementDao, atMost(1)).persist(measurement.capture());
        if (measurement.getAllValues().isEmpty()) {
            return null;
        }
        assertThat(measurement.getValue().getType()).isEqualTo("A1C");
        return measurement.getValue();
    }

    /**
     * The {@code HL7_LAB_MEASUREMENT_FILTER} the package ships (issue #4613). Its value is both the filter and the
     * extractor: {@code populateMeasurementsTable} stores {@code matcher.group()}, so whatever the shipped
     * expression matches IS the measurement value the flowsheets, the Measurements module and the Health Tracker
     * show. The expression is read from the {@code carlos.properties} on the classpath, the file the WAR and the
     * Debian skeleton carry, through {@link Properties} so its backslash escapes are unfolded exactly as at runtime.
     */
    @Nested
    @DisplayName("with the shipped HL7_LAB_MEASUREMENT_FILTER")
    class ShippedFilter {

        private static final LocalDateTime ENTERED = LocalDateTime.parse("2026-10-13T10:05:19");
        private static final String OBSERVED = "2026-10-09 11:20:29";

        @ParameterizedTest(name = "[{0}] is filed as [{1}]")
        @CsvSource(delimiter = '|', value = {
                // The values the issue lists: every one of them was cut to its first four characters.
                "0.095        | 0.095",
                "10.55        | 10.55",
                "112.5        | 112.5",
                "12345        | 12345",
                "<0.05        | <0.05",
                ">100.5       | >100.5",
                "1,250        | 1,250",
                "0.078        | 0.078",
                "123.4 mmol   | 123.4",
                // Ranges, titres and dates of five characters or more were cut the same way.
                "10-20        | 10-20",
                "1:160        | 1:160",
                "-12.5        | -12.5",
                "12/05/2026   | 12/05/2026",
                // A comparator the lab separates from its number with a space kept only the comparator.
                "< 5          | < 5",
                "- 3          | - 3",
                // Values that were already filed whole stay exactly as they were.
                "100 mmol     | 100",
                "1250         | 1250",
                "139          | 139",
                "4.15         | 4.15",
                "-2.5         | -2.5",
                "0-2          | 0-2",
                "5-10         | 5-10",
                "1:40         | 1:40",
                ">=60         | >=60",
                "2+           | 2+",
                "+            | +",
                "+++          | +++",
                "-            | -",
                "?            | ?",
                "++ (moderate)| ++",
                "2026-10-09   | 2026-10-09",
                "negative     | negative",
                "112.5<br />  | 112.5",
                // A leading break marker is stripped before the filter runs, and so is the space it leaves.
                "<br /> -5.2  | -5.2",
                "<br />112.5  | 112.5",
                "<br />negative | negative",
                // A signed qualitative result keeps its sign, as it did before (#4613 review).
                "+ve          | +",
                "-ve          | -"})
        void shouldFileValueTheLabSent_whenShippedFilterMatchesResult(String result, String expected) throws IOException {
            Measurement measurement = fileResult(result, shippedFilter(), OBSERVED, ENTERED);

            assertThat(measurement).as("a measurement is filed for %s", result).isNotNull();
            assertThat(measurement.getDataField()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{0}] is not filed")
        @CsvSource(delimiter = '|', value = {"see report", "Comment to follow", "(see below)"})
        void shouldNotFileMeasurement_whenShippedFilterRejectsResult(String result) throws IOException {
            assertThat(fileResult(result, shippedFilter(), OBSERVED, ENTERED)).isNull();
            verify(measurementDao, never()).persist(any(Measurement.class));
        }

        @Test
        void shouldSkipMeasurement_whenShippedFilterMatchesRunLongerThanDataField() throws IOException {
            // Five lines of 80 dashes with their <br /> markers removed: no longer cut to "----", and too long to file.
            String separator = "-".repeat(80) + "<br />";

            assertThat(fileResult(separator.repeat(5), shippedFilter(), OBSERVED, ENTERED)).isNull();
            verify(measurementDao, never()).persist(any(Measurement.class));
        }

        private String shippedFilter() throws IOException {
            Properties properties = new Properties();
            try (InputStream in = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream("carlos.properties")) {
                assertThat(in).as("carlos.properties on the classpath").isNotNull();
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
            }
            String filter = properties.getProperty("HL7_LAB_MEASUREMENT_FILTER");
            assertThat(filter).as("the shipped HL7_LAB_MEASUREMENT_FILTER").isNotBlank();
            return filter;
        }
    }
}
