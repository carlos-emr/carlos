/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import java.util.List;
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
import io.github.carlos_emr.carlos.commn.dao.MeasurementsDeletedDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementMapDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementMap;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.MeasurementsExt;
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

    @Test
    void shouldKeepConfiguredFilterSelection_whenPreservingLiteralCase() {
        LocalDateTime entered = LocalDateTime.parse("2026-10-13T10:05:19");
        assertThat(fileResult("Negative", "^NEGATIVE$", "2026-10-09 11:20:29", entered)).isNull();
        assertThat(fileResult("Negative", "^[^A-Z]+$", "2026-10-09 11:20:29", entered).getDataField())
                .isEqualTo("Negative");
    }

    @Test
    void shouldPreserveUnicodeLiteralBoundaries_whenLowercaseMappingExpands() {
        LocalDateTime entered = LocalDateTime.parse("2026-10-13T10:05:19");
        assertThat(fileResult("İNegative", "negative", "2026-10-09 11:20:29", entered).getDataField())
                .isEqualTo("Negative");
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

    @Test
    @DisplayName("should refuse before any measurement changes when the report cannot be read")
    void shouldRefuseBeforeChangingMeasurements_whenTheReportCannotBeRead() {
        try (MockedStatic<Factory> factory = mockStatic(Factory.class)) {
            factory.when(() -> Factory.getHandler("4616")).thenReturn(null);

            assertThatThrownBy(() -> Hl7textResultsData.populateMeasurementsTable("4616", "42"))
                    .isInstanceOf(IllegalStateException.class);
        }
        verifyNoInteractions(measurementDao, mockedBeans.get(MeasurementsDeletedDao.class));
    }

    private static Date inDefaultTimeZone(LocalDateTime timestamp) {
        return Date.from(timestamp.atZone(ZoneId.systemDefault()).toInstant());
    }

    private Measurement populateMeasurement(String observationTimestamp, LocalDateTime entryTimestamp) {
        Measurement measurement = fileResult("6.1", ".*", observationTimestamp, entryTimestamp);
        assertThat(measurement).isNotNull();
        return measurement;
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @ValueSource(strings = {"DNR"})
    void shouldLeaveMeasurementsAbsent_whenTheSourceHasNoReportableValue(String result) {
        assertThat(fileResult(result, ".*", "2026-03-10 12:00:00", LocalDateTime.parse("2026-03-10T14:00:00"))).isNull();
        verify(measurementDao, never()).persist(any(Measurement.class));
    }

    @Test
    void shouldKeepTheLegacyPreviousVersionScope_forANonCmlSource() {
        try (var versions = mockStatic(Hl7textResultsData.class, CALLS_REAL_METHODS)) {
            versions.when(() -> Hl7textResultsData.getMatchingLabs("4616")).thenReturn("1111,2222,4616");
            assertThat(fileResult("6.1", ".*", "2026-03-10 12:00:00", LocalDateTime.parse("2026-03-10T14:00:00")))
                    .isNotNull();
        }
        verify(measurementDao).findByValue("lab_no", "2222");
        verify(measurementDao, never()).findByValue("lab_no", "1111");
    }

    @Test
    void shouldPreserveTheOriginalTail_whenSeveralCharactersFollowACaseExpansion() {
        assertThat(fileResult("İAB", "b$", "2026-03-10 12:00:00", LocalDateTime.parse("2026-03-10T14:00:00")))
                .extracting(Measurement::getDataField).isEqualTo("B");
    }

    @Test
    void shouldRefuseUnmappableFilterBoundaries_withoutLoggingWhenNoContextWasProvided() throws Exception {
        var value = Hl7textResultsData.class.getDeclaredMethod("projectableValue", String.class, String.class);
        value.setAccessible(true);
        CarlosProperties properties = mock(CarlosProperties.class);
        try (var settings = mockStatic(CarlosProperties.class); var logs = LogCapture.forLogger(Hl7textResultsData.class)) {
            settings.when(CarlosProperties::getInstance).thenReturn(properties);
            when(properties.getProperty("HL7_LAB_MEASUREMENT_FILTER", ".*")).thenReturn("i");
            assertThat(value.invoke(null, "İ", null)).isNull();
            when(properties.getProperty("HL7_LAB_MEASUREMENT_FILTER", ".*")).thenReturn(".*");
            for (String missing : new String[]{null, "", "DNR"}) assertThat(value.invoke(null, missing, null)).isNull();
            assertThat(value.invoke(null, "1".repeat(Measurement.DATA_FIELD_MAX_LENGTH + 1), null)).isNull();
            assertThat(logs.messages()).isEmpty();
        }
        var boundary = Hl7textResultsData.class.getDeclaredMethod("originalBoundary", String.class, String.class, int.class, java.util.Locale.class);
        boundary.setAccessible(true);
        assertThat(boundary.invoke(null, "İ", "inconsistent-shadow", 10, java.util.Locale.ROOT)).isEqualTo(-1);
    }

    @Test
    void shouldPreserveTheOriginalUnicodeLiteral_whenItsWholeCaseMappingMatches() {
        Measurement value = fileResult("\u0130", "i\u0307", "2026-03-10 12:00:00",
                LocalDateTime.parse("2026-03-10T14:00:00"));
        assertThat(value.getDataField()).isEqualTo("\u0130");
    }

    @Test
    void shouldNotInventPartOfAUnicodeCharacter_whenTheFilterSplitsItsCaseMapping() {
        try (LogCapture log = LogCapture.forLogger(Hl7textResultsData.class)) {
            assertThat(fileResult("\u0130", "i", "2026-03-10 12:00:00",
                    LocalDateTime.parse("2026-03-10T14:00:00"))).isNull();
            assertThat(log.messages()).anySatisfy(message -> assertThat(message)
                    .contains("split a Unicode case mapping", "lab 4616", "OBX 0-0")
                    .doesNotContain("\u0130"));
        }
    }

    @ParameterizedTest
    @MethodSource("unicodeMatchBoundaries")
    void shouldRetainOnlyWholeSourceCharacters_whenTheFilterStartsAfterACaseExpansion(
            String filter, String expected) {
        Measurement stored = fileResult("\u0130X", filter, "2026-03-10 12:00:00",
                LocalDateTime.parse("2026-03-10T14:00:00"));
        if (expected == null) assertThat(stored).isNull();
        else assertThat(stored.getDataField()).isEqualTo(expected);
    }

    private static Stream<Arguments> unicodeMatchBoundaries() {
        return Stream.of(Arguments.of("i", null), Arguments.of("\u0307x", null), Arguments.of("x", "X"));
    }

    @Test
    void shouldPreserveEnglishClinicalLiterals_whenTheServerLocaleIsTurkish() throws IOException {
        java.util.Locale original = java.util.Locale.getDefault();
        java.util.Locale display = java.util.Locale.getDefault(java.util.Locale.Category.DISPLAY);
        java.util.Locale format = java.util.Locale.getDefault(java.util.Locale.Category.FORMAT);
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertThat(fileResult("NEGATIVE", shippedFilter(), "2026-03-10 12:00:00",
                    LocalDateTime.parse("2026-03-10T14:00:00")).getDataField()).isEqualTo("NEGATIVE");
        } finally {
            java.util.Locale.setDefault(original);
            java.util.Locale.setDefault(java.util.Locale.Category.DISPLAY, display);
            java.util.Locale.setDefault(java.util.Locale.Category.FORMAT, format);
        }
    }

    @Test
    void shouldRefuseAnUnresolvedIncomingVersion_beforeChangingClinicalMeasurements() {
        LocalDateTime received = LocalDateTime.parse("2026-03-10T14:00:00");
        try (var versions = mockStatic(Hl7textResultsData.class, CALLS_REAL_METHODS)) {
            versions.when(() -> Hl7textResultsData.getMatchingLabs("4616")).thenReturn("1111,9999");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> fileResult("4.3", ".*",
                    "2026-03-10 07:00:00", received))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("absent");
        }
        org.mockito.Mockito.verifyNoInteractions(measurementDao,
                mockedBeans.get(io.github.carlos_emr.carlos.commn.dao.MeasurementsDeletedDao.class),
                mockedBeans.get(io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"4616,9999", "1111,4616,9999"})
    void shouldLeaveActiveMeasurementsUntouched_whenAnOlderVersionArrivesLast(String chain) {
        try (var versions = mockStatic(Hl7textResultsData.class, CALLS_REAL_METHODS)) {
            versions.when(() -> Hl7textResultsData.getMatchingLabs("4616")).thenReturn(chain);
            assertThat(fileResult("4.3", ".*", "2026-03-10 07:00:00",
                    LocalDateTime.parse("2026-03-10T14:00:00"))).isNull();
        }
        org.mockito.Mockito.verifyNoInteractions(measurementDao,
                mockedBeans.get(io.github.carlos_emr.carlos.commn.dao.MeasurementsDeletedDao.class),
                mockedBeans.get(io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao.class),
                mockedBeans.get(MeasurementMapDao.class));
    }

    /**
     * Files one A1C result through {@link Hl7textResultsData#populateMeasurementsTable} with the given
     * {@code HL7_LAB_MEASUREMENT_FILTER}, and returns the measurement it persisted, or {@code null} when it
     * persisted none.
     */
    private Measurement fileResult(String result, String filter, String observationTimestamp,
            LocalDateTime entryTimestamp) {
        return fileResult(result, filter, observationTimestamp, entryTimestamp, null);
    }

    private Measurement fileResult(String result, String filter, String observationTimestamp,
            LocalDateTime entryTimestamp, String status) {
        MessageHandler handler = mock(MessageHandler.class);
        when(handler.getOBXResultStatus(0, 0)).thenReturn(status);
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
                "Negative     | Negative",
                "NEGATIVE     | NEGATIVE",
                "NeGaTiVe     | NeGaTiVe",
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
        @CsvSource(delimiter = '|', value = {"see report", "Comment to follow", "(see below)", "Pending"})
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

    }

    /** The extension rows the last filing wrote. */
    @SuppressWarnings("unchecked")
    private List<MeasurementsExt> filedExtensions() {
        ArgumentCaptor<List<MeasurementsExt>> rows = ArgumentCaptor.forClass(List.class);
        verify((MeasurementsExtDao) mockedBeans.get(MeasurementsExtDao.class)).batchPersistAtomically(rows.capture(), anyInt());
        return rows.getValue();
    }

    private static boolean isTextMarker(MeasurementsExt row) {
        return MeasurementsExt.VALUE_TYPE_KEY.equals(row.getKeyVal()) && MeasurementsExt.TEXT_VALUE_TYPE.equals(row.getVal());
    }

    /**
     * #6122: a final or corrected result the filter does not match ({@code Pending}, {@code Not detected}) was not
     * filed, so the cumulative grid and the value history showed nothing for it. It is now filed whole, marked as text.
     */
    @Nested
    @DisplayName("a final result the shipped filter does not match")
    class FinalTextResults {

        private static final LocalDateTime ENTERED = LocalDateTime.parse("2026-10-13T10:05:19");
        private static final String OBSERVED = "2026-10-09 11:20:29";

        @ParameterizedTest(name = "[{0}] with status {1} is filed as text [{2}]")
        @CsvSource(delimiter = '|', value = {
                "Pending                   | F | Pending",
                "Not detected              | C | Not detected",
                "Resistant                 | F | Resistant",
                // Labs send the code in lower case, and many handlers report a word (#6122 review).
                "Pending                   | f | Pending",
                "Not detected              | c | Not detected",
                "Pending                   | Final | Pending",
                "Not detected              | Corrected | Not detected",
                "Not detected              | Edited | Not detected",
                "' Detected '              | F | Detected",
                "Detected<br />see comment | F | Detected see comment",
                "Detected <BR> see comment | F | Detected see comment"})
        void shouldFileWholeTextAndMarkIt_whenAFinalResultIsNotMatched(String result, String status, String expected)
                throws IOException {
            Measurement measurement = fileResult(result, shippedFilter(), OBSERVED, ENTERED, status);

            assertThat(measurement).isNotNull();
            assertThat(measurement.getDataField()).isEqualTo(expected);
            assertThat(filedExtensions()).filteredOn(Hl7textResultsDataUnitTest::isTextMarker).hasSize(1);
        }

        // A status padded with spaces is not final: only the exact token counts, as in CML's handler
        // and the CDS import (ObxResultStatusUnitTest), so its result stays unfiled as before #6122.
        @ParameterizedTest(name = "status [{0}] leaves Pending unfiled")
        @ValueSource(strings = {"P", "I", "X", "D", "U", "Preliminary", "Pending", "Partial", "Changed to Final", "",
                " F ", "F ", " c", "Final "})
        void shouldLeaveTheResultUnfiled_whenItIsNotFinal(String status) throws IOException {
            assertThat(fileResult("Pending", shippedFilter(), OBSERVED, ENTERED, status)).isNull();
            verify(measurementDao, never()).persist(any(Measurement.class));
        }

        @Test
        void shouldFileTheMatchedValueWithoutTheMarker_whenTheFilterMatches() throws IOException {
            Measurement measurement = fileResult("Negative", shippedFilter(), OBSERVED, ENTERED, "F");

            assertThat(measurement.getDataField()).isEqualTo("Negative");
            assertThat(filedExtensions()).noneMatch(Hl7textResultsDataUnitTest::isTextMarker)
                    .anyMatch(row -> "olis_status".equals(row.getKeyVal()) && "F".equals(row.getVal()));
        }

        @Test
        void shouldFileWholeText_whenItFillsTheDataField() throws IOException {
            // 255 characters, 247 of them outside the Basic Multilingual Plane: the column counts characters.
            String longest = "Pending " + "\uD835\uDC00".repeat(Measurement.DATA_FIELD_MAX_LENGTH - 8);

            Measurement measurement = fileResult(longest, shippedFilter(), OBSERVED, ENTERED, "F");

            assertThat(measurement.getDataField()).isEqualTo(longest);
        }

        @Test
        void shouldLeaveTextUnfiled_whenOneCharacterLongerThanTheDataField() throws IOException {
            // 256 characters, 248 of them outside the Basic Multilingual Plane: one more than the column holds.
            String tooLong = "Pending " + "\uD835\uDC00".repeat(Measurement.DATA_FIELD_MAX_LENGTH - 7);

            assertThat(tooLong.codePointCount(0, tooLong.length())).isEqualTo(Measurement.DATA_FIELD_MAX_LENGTH + 1);
            assertThat(fileResult(tooLong, shippedFilter(), OBSERVED, ENTERED, "F")).isNull();
            verify(measurementDao, never()).persist(any(Measurement.class));
        }

        @Test
        void shouldLeaveTextUnfiledWithoutLoggingIt_whenLongerThanTheDataField() throws IOException {
            String tooLong = "FAKE-TEXT-" + "x".repeat(Measurement.DATA_FIELD_MAX_LENGTH);

            try (LogCapture log = LogCapture.forLogger(Hl7textResultsData.class)) {
                assertThat(fileResult(tooLong, shippedFilter(), OBSERVED, ENTERED, "F")).isNull();
                // The warning says why and where, and never carries the result itself.
                assertThat(log.messages()).anySatisfy(message -> assertThat(message)
                        .contains("longer than a measurement holds", "lab 4616", "OBX 0-0")
                        .doesNotContain("FAKE-TEXT"));
            }
            verify(measurementDao, never()).persist(any(Measurement.class));
        }

        @Test
        void shouldReadTheStatusAndText_whenCalledDirectly() {
            assertThat(Hl7textResultsData.textResultValue("Pending", "F", null)).isEqualTo("Pending");
            assertThat(Hl7textResultsData.textResultValue("Pending", "C", null)).isEqualTo("Pending");
            assertThat(Hl7textResultsData.textResultValue("Pending", "Final", null)).isEqualTo("Pending");
            assertThat(Hl7textResultsData.textResultValue("Pending", "P", null)).isNull();
            assertThat(Hl7textResultsData.textResultValue("Pending", null, null)).isNull();
            for (String empty : new String[]{null, "", "   ", "<br />", "DNR"}) {
                assertThat(Hl7textResultsData.textResultValue(empty, "F", null)).isNull();
            }
        }
    }

    /**
     * #6122 review: the unit tests above give the filing the status a mock returns. These run real messages
     * through the real handlers, which report the status in their own words: CML and CLS say {@code Final},
     * CLS says {@code Corrected} and MDS says {@code Edited} for a correction. The CML message comes from the
     * Create Lab form's generator, the issue's own repro.
     */
    @Nested
    @DisplayName("a final word result, through the real lab handlers")
    class ThroughTheLabHandlers {

        private static final String TIME = "20261009112029";

        @ParameterizedTest(name = "Create Lab (CML) status [{0}] is reported as [{1}]; Pending filed: {2}")
        @CsvSource({"F,Final,true", "f,Final,true", "C,C,true", "c,c,true", "'',Final,true",
                "I,Pending,false", "P,P,false", "X,X,false"})
        void shouldFileTheWordResult_whenTheCreateLabReportIsFinal(String stat, String reported, boolean filed)
                throws Exception {
            io.github.carlos_emr.carlos.commn.model.LabTest test = new io.github.carlos_emr.carlos.commn.model.LabTest();
            test.setDate(inDefaultTimeZone(LocalDateTime.parse("2026-10-09T11:20:29")));
            test.setCodeType("ST");
            test.setCode("3767");
            test.setName("Hemoglobin A1C");
            test.setDescription("FAKE A1C");
            test.setCodeValue("Pending");
            test.setStat(stat);
            io.github.carlos_emr.carlos.commn.model.Lab lab = new io.github.carlos_emr.carlos.commn.model.Lab();
            lab.setLastName("FAKE-Workflow");
            lab.setFirstName("Synthetic");
            lab.setAccession("FAKE-CML-1");
            lab.setLabReqDate(test.getDate());
            lab.setTests(List.of(test));
            io.github.carlos_emr.carlos.lab.ca.all.parsers.CMLHandler handler =
                    new io.github.carlos_emr.carlos.lab.ca.all.parsers.CMLHandler();
            handler.init(io.github.carlos_emr.carlos.lab.ca.all.util.CMLLabHL7Generator.generate(lab));

            assertThat(handler.getOBXResultStatus(0, 0)).isEqualTo(reported);
            // The report's own final count and the filing use one rule.
            assertThat(handler.getOBXFinalResultCount()).isEqualTo(filed ? 1 : 0);
            expectFiled(fileThrough(handler), filed);
        }

        @ParameterizedTest(name = "CLS status [{0}] is reported as [{1}]; Pending filed: {2}")
        @CsvSource({"F,Final,true", "C,Corrected,true", "c,Corrected,true", "P,Preliminary,false", "I,I,false"})
        void shouldFileTheWordResult_whenTheClsHandlerSaysFinalOrCorrected(String status, String reported,
                boolean filed) throws Exception {
            io.github.carlos_emr.carlos.lab.ca.all.parsers.CLSHandler handler =
                    new io.github.carlos_emr.carlos.lab.ca.all.parsers.CLSHandler();
            handler.init(message("CLS", status));

            assertThat(handler.getOBXResultStatus(0, 0)).isEqualTo(reported);
            expectFiled(fileThrough(handler), filed);
        }

        @ParameterizedTest(name = "MDS status [{0}] is reported as [{1}]; Pending filed: {2}")
        @CsvSource({"F,Final,true", "f,Final,true", "C,Edited,true", "U,Changed to Final,false",
                "P,Preliminary,false", "I,Pending,false"})
        void shouldFileTheWordResult_whenTheMdsHandlerSaysFinalOrEdited(String status, String reported,
                boolean filed) throws Exception {
            io.github.carlos_emr.carlos.lab.ca.all.parsers.MDSHandler handler =
                    new io.github.carlos_emr.carlos.lab.ca.all.parsers.MDSHandler();
            handler.init(message("MDS", status));

            assertThat(handler.getOBXResultStatus(0, 0)).isEqualTo(reported);
            expectFiled(fileThrough(handler), filed);
        }

        /** A one-result FAKE report: A1C code 3767, result {@code Pending}, the given OBX-11 status. */
        private String message(String sender, String status) {
            String[] obr = new String[26], obx = new String[15];
            java.util.Arrays.fill(obr, "");
            java.util.Arrays.fill(obx, "");
            obr[0] = "OBR"; obr[1] = "1"; obr[2] = "FAKE-" + sender; obr[4] = "A1C^FAKE PANEL";
            obr[7] = TIME; obr[14] = TIME; obr[20] = "FAKE-ACC"; obr[25] = "F";
            obx[0] = "OBX"; obx[1] = "1"; obx[2] = "ST"; obx[3] = "3767^Hemoglobin A1C^FAKE A1C";
            obx[5] = "Pending"; obx[11] = status; obx[14] = TIME;
            String end = "\r";
            return "MSH|^~\\&|" + sender + "|FAKE-LAB|CARLOS|CLINIC|" + TIME + "||ORU^R01|FAKE-1-2-3|P|2.3" + end
                    + "PID|1||FAKE-HIN||FAKE-Workflow^Synthetic||19800102|F" + end
                    + "ORC|RE|FAKE-" + sender + end
                    + String.join("|", obr) + end + String.join("|", obx) + end;
        }

        private void expectFiled(Measurement measurement, boolean filed) {
            if (!filed) {
                assertThat(measurement).isNull();
                verify(measurementDao, never()).persist(any(Measurement.class));
                return;
            }
            assertThat(measurement).isNotNull();
            assertThat(measurement.getDataField()).isEqualTo("Pending");
            assertThat(filedExtensions()).filteredOn(Hl7textResultsDataUnitTest::isTextMarker).hasSize(1);
        }

        private Measurement fileThrough(MessageHandler handler) throws IOException {
            CarlosProperties properties = mock(CarlosProperties.class);
            when(properties.getProperty("HL7_LAB_MEASUREMENT_FILTER", ".*")).thenReturn(shippedFilter());
            when(properties.getProperty("MAP_BY_IDENTIFIER_AND_NAME", "false")).thenReturn("false");
            try (MockedStatic<Factory> factory = mockStatic(Factory.class);
                 MockedStatic<CarlosProperties> configuration = mockStatic(CarlosProperties.class)) {
                factory.when(() -> Factory.getHandler("4616")).thenReturn(handler);
                configuration.when(CarlosProperties::getInstance).thenReturn(properties);
                Hl7textResultsData.populateMeasurementsTable("4616", "42");
            }
            ArgumentCaptor<Measurement> measurement = ArgumentCaptor.forClass(Measurement.class);
            verify(measurementDao, atMost(1)).persist(measurement.capture());
            return measurement.getAllValues().isEmpty() ? null : measurement.getValue();
        }
    }

    private static String shippedFilter() throws IOException {
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
