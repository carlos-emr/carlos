/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    void shouldSupersedeEveryOtherVersion_forANonCmlSource() {
        try (var versions = mockStatic(Hl7textResultsData.class, CALLS_REAL_METHODS)) {
            versions.when(() -> Hl7textResultsData.getMatchingLabs("4616")).thenReturn("1111,2222,4616");
            assertThat(fileResult("6.1", ".*", "2026-03-10 12:00:00", LocalDateTime.parse("2026-03-10T14:00:00")))
                    .isNotNull();
        }
        // Not only the neighbour in the display order: whichever version held the chart's values is
        // archived, so two versions' values are never left together.
        verify(measurementDao).findByValue("lab_no", "2222");
        verify(measurementDao).findByValue("lab_no", "1111");
    }

    @Test
    @DisplayName("should leave another patient's measurements alone when two reports share an accession number")
    void shouldLeaveAnotherPatientsMeasurements_whenAnAccessionIsShared() {
        // The version chain is resolved by accession number alone. A recycled or colliding accession
        // puts another patient's report in it, and that patient's values are not this report's to archive.
        Measurement otherPatients = new Measurement();
        otherPatients.setDataField("7.7");
        otherPatients.setDemographicId(4711);
        Measurement samePatients = new Measurement();
        samePatients.setDataField("5.1");
        samePatients.setDemographicId(42);
        when(measurementDao.findByValue("lab_no", "1111")).thenReturn(java.util.List.of(otherPatients));
        when(measurementDao.findByValue("lab_no", "2222")).thenReturn(java.util.List.of(samePatients));
        try (var versions = mockStatic(Hl7textResultsData.class, CALLS_REAL_METHODS)) {
            versions.when(() -> Hl7textResultsData.getMatchingLabs("4616")).thenReturn("1111,2222,4616");
            assertThat(fileResult("6.1", ".*", "2026-03-10 12:00:00", LocalDateTime.parse("2026-03-10T14:00:00")))
                    .isNotNull();
        }

        verify(measurementDao).batchRemoveAtomically(
                org.mockito.ArgumentMatchers.argThat(removed -> removed.size() == 1 && removed.get(0) == samePatients));
    }

    @ParameterizedTest
    @ValueSource(strings = {"4616,2222", "1111,4616,2222", "4616,1111,2222"})
    @DisplayName("should file a correction that sorts before the report it corrects, and archive the original")
    void shouldFileACorrection_whenFewerFinalResultsSortItBeforeTheOriginal(String chain) {
        // A generic HL7 correction (4 F + 1 C) counts fewer final results than its original (5 F), so the
        // display order puts it first. It is still the newest delivery: the highest lab number.
        Measurement original = new Measurement();
        original.setDataField("5.1");
        original.setDemographicId(42);
        when(measurementDao.findByValue("lab_no", "2222")).thenReturn(java.util.List.of(original));
        Measurement filed;
        try (var versions = mockStatic(Hl7textResultsData.class, CALLS_REAL_METHODS)) {
            versions.when(() -> Hl7textResultsData.getMatchingLabs("4616")).thenReturn(chain);
            filed = fileResult("12.7", ".*", "2026-03-10 12:00:00", LocalDateTime.parse("2026-03-10T14:00:00"));
        }

        assertThat(filed).isNotNull();
        assertThat(filed.getDataField()).isEqualTo("12.7");
        // The original's value leaves the chart, so the uncorrected and corrected values never coexist.
        verify(measurementDao).batchRemoveAtomically(java.util.List.of(original));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<io.github.carlos_emr.carlos.commn.model.MeasurementsDeleted>> archived =
                ArgumentCaptor.forClass(java.util.List.class);
        verify((MeasurementsDeletedDao) mockedBeans.get(MeasurementsDeletedDao.class)).batchPersistAtomically(archived.capture());
        assertThat(archived.getValue()).hasSize(1);
        for (String other : chain.split(",")) {
            if (!other.equals("4616")) verify(measurementDao).findByValue("lab_no", other);
        }
        verify(measurementDao, never()).findByValue("lab_no", "4616");
    }

    @Test
    @DisplayName("should replace a partial report with the final that follows it, as before")
    void shouldReplaceThePartial_whenTheFinalFollowsIt() {
        Measurement partial = new Measurement();
        partial.setDataField("5.1");
        when(measurementDao.findByValue("lab_no", "1111")).thenReturn(java.util.List.of(partial));
        Measurement filed;
        try (var versions = mockStatic(Hl7textResultsData.class, CALLS_REAL_METHODS)) {
            // The normal progression: more final results, a later report, a higher lab number.
            versions.when(() -> Hl7textResultsData.getMatchingLabs("4616")).thenReturn("1111,4616");
            filed = fileResult("5.2", ".*", "2026-03-10 12:00:00", LocalDateTime.parse("2026-03-10T14:00:00"));
        }

        assertThat(filed).isNotNull();
        verify(measurementDao).batchRemoveAtomically(java.util.List.of(partial));
    }

    @Test
    @DisplayName("should not archive anything for the only version of a report")
    void shouldFileWithoutArchiving_whenTheReportHasOneVersion() {
        try (var versions = mockStatic(Hl7textResultsData.class, CALLS_REAL_METHODS)) {
            versions.when(() -> Hl7textResultsData.getMatchingLabs("4616")).thenReturn("4616");
            assertThat(fileResult("6.1", ".*", "2026-03-10 12:00:00", LocalDateTime.parse("2026-03-10T14:00:00")))
                    .isNotNull();
        }
        verify(measurementDao, never()).findByValue(any(), any());
        verifyNoInteractions(mockedBeans.get(MeasurementsDeletedDao.class));
    }

    @Test
    @DisplayName("should keep protecting a CML chain by its revision order, not by arrival")
    void shouldLeaveCmlMeasurementsUntouched_whenALateHistoricalVersionHasTheHighestLabNumber() {
        // The same chain a generic source would file ("4616,2222": the incoming report first, with the
        // highest lab number). For CML the order is the lab's revision evidence: 2222 is current, and the
        // late delivery of an older version must not replace its measurements (#5820).
        try (var versions = mockStatic(Hl7textResultsData.class, CALLS_REAL_METHODS)) {
            versions.when(() -> Hl7textResultsData.getMatchingLabs("4616")).thenReturn("4616,2222");
            assertThat(fileResult(io.github.carlos_emr.carlos.lab.ca.all.parsers.CMLHandler.class, "4.3", ".*",
                    "2026-03-10 07:00:00", LocalDateTime.parse("2026-03-10T14:00:00"))).isNull();
        }
        verifyNoInteractions(measurementDao, mockedBeans.get(MeasurementsDeletedDao.class),
                mockedBeans.get(io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao.class));
    }

    @Test
    @DisplayName("should file the newest CML revision and archive every earlier version")
    void shouldFileTheCurrentCmlVersion_andArchiveEveryEarlierVersion() {
        try (var versions = mockStatic(Hl7textResultsData.class, CALLS_REAL_METHODS)) {
            versions.when(() -> Hl7textResultsData.getMatchingLabs("4616")).thenReturn("9999,1111,4616");
            assertThat(fileResult(io.github.carlos_emr.carlos.lab.ca.all.parsers.CMLHandler.class, "12.7", ".*",
                    "2026-03-10 12:00:00", LocalDateTime.parse("2026-03-10T14:00:00"))).isNotNull();
        }
        verify(measurementDao).findByValue("lab_no", "9999");
        verify(measurementDao).findByValue("lab_no", "1111");
    }

    @Test
    @DisplayName("should choose the current version by revision order for CML and by arrival for other sources")
    void shouldChooseCurrentVersion_byRevisionOrderOrArrival() {
        String[] correctionFirst = {"4616", "2222"};
        assertThat(Hl7textResultsData.currentVersion(correctionFirst, false)).isEqualTo("4616");
        assertThat(Hl7textResultsData.currentVersion(correctionFirst, true)).isEqualTo("2222");
        assertThat(Hl7textResultsData.currentVersion(new String[]{"1111", "2222", "4616"}, false)).isEqualTo("4616");
        assertThat(Hl7textResultsData.currentVersion(new String[]{"900", "1000", "99"}, false)).isEqualTo("1000");
        assertThat(Hl7textResultsData.currentVersion(new String[]{"4616"}, false)).isEqualTo("4616");
        assertThat(Hl7textResultsData.currentVersion(new String[]{"4616"}, true)).isEqualTo("4616");
    }

    @Test
    @DisplayName("should file the newest delivery when a patient match names the last version of the display order")
    void shouldFileTheNewestDelivery_whenAPatientMatchNamesAnotherVersion() {
        // The display order ends with the original (2222: five final results); the correction (4616) is current.
        Measurement filed;
        try (var versions = mockStatic(Hl7textResultsData.class, CALLS_REAL_METHODS)) {
            versions.when(() -> Hl7textResultsData.getMatchingLabs("2222")).thenReturn("4616,2222");
            versions.when(() -> Hl7textResultsData.getMatchingLabs("4616")).thenReturn("4616,2222");
            filed = fileResult(MessageHandler.class, "12.7", ".*", "2026-03-10 12:00:00",
                    LocalDateTime.parse("2026-03-10T14:00:00"),
                    () -> Hl7textResultsData.populateCurrentVersionMeasurements("2222", "42"));
        }

        // The correction's value is filed (the handler of 4616 supplied it), the original's is not read.
        assertThat(filed).isNotNull();
        assertThat(filed.getDataField()).isEqualTo("12.7");
        verify(measurementDao).findByValue("lab_no", "2222");
        verify(measurementDao, never()).findByValue("lab_no", "4616");
    }

    @Test
    @DisplayName("should refuse a patient match for a report that cannot be read, before any measurement changes")
    void shouldRefuseCurrentVersionFiling_whenTheNamedReportCannotBeRead() {
        try (MockedStatic<Factory> factory = mockStatic(Factory.class)) {
            factory.when(() -> Factory.getHandler("2222")).thenReturn(null);
            assertThatThrownBy(() -> Hl7textResultsData.populateCurrentVersionMeasurements("2222", "42"))
                    .isInstanceOf(IllegalStateException.class);
        }
        verifyNoInteractions(measurementDao, mockedBeans.get(MeasurementsDeletedDao.class));
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
        return fileResult(MessageHandler.class, result, filter, observationTimestamp, entryTimestamp);
    }

    /** As above, for a report read by the given handler type, which decides how its version chain is judged. */
    private Measurement fileResult(Class<? extends MessageHandler> source, String result, String filter,
            String observationTimestamp, LocalDateTime entryTimestamp) {
        return fileResult(source, result, filter, observationTimestamp, entryTimestamp,
                () -> Hl7textResultsData.populateMeasurementsTable("4616", "42"));
    }

    /**
     * As above, through the given entry point. Report 4616 carries the result; report 2222 is another
     * readable version of the same source with no result of its own.
     */
    private Measurement fileResult(Class<? extends MessageHandler> source, String result, String filter,
            String observationTimestamp, LocalDateTime entryTimestamp, Runnable filing) {
        MessageHandler handler = mock(source);
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
            factory.when(() -> Factory.getHandler("2222")).thenReturn(mock(source));
            configuration.when(CarlosProperties::getInstance).thenReturn(properties);
            clock.when(Calendar::getInstance).thenReturn(entryCalendar);

            filing.run();
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
