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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.commn.model.CdsClinicalException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsCareContactUnitTest {
    @TempDir Path directory;
    private final CdsFieldParser fields = new CdsFieldParser();
    private final CdsClinicalNormalizationService clinical = new CdsClinicalNormalizationService(fields);
    private final CdsCareNormalizationService care = new CdsCareNormalizationService(fields, clinical);
    private final CdsContactNormalizationService contacts = new CdsContactNormalizationService(fields);

    static Stream<Arguments> careKinds() {
        return Stream.of(
            Arguments.of("SmokingStatus", Map.of("Status", "Y"), "SKST", "Yes"),
            Arguments.of("SmokingPacks", Map.of("PerDay", "+0.50"), "POSK", "0.5"),
            Arguments.of("Weight", Map.of("Weight", "75.00", "WeightUnit", "kg"), "WT", "75"),
            Arguments.of("Height", Map.of("Height", "170", "HeightUnit", "cm"), "HT", "170"),
            Arguments.of("WaistCircumference", Map.of("WaistCircumference", "90", "WaistCircumferenceUnit", "cm"), "WAIS", "90"),
            Arguments.of("BloodPressure", Map.of("SystolicBP", "120", "DiastolicBP", "80", "BPUnit", "mmHg"), "BP", "120/80"),
            Arguments.of("DiabetesComplicationsScreening", Map.of("ExamCode", "67536-3"), "CDS_EXAM", "Performed"),
            Arguments.of("DiabetesMotivationalCounselling", Map.of("CounsellingPerformed", "Smoking Cessation"), "MCCS", "Yes"),
            Arguments.of("DiabetesSelfManagementCollaborative", Map.of("CodeValue", "44943-9", "DocumentedGoals", "Walk daily"), "CDS_GOALS", "Walk daily"),
            Arguments.of("DiabetesSelfManagementChallenges", Map.of("CodeValue", "44941-3", "ChallengesIdentified", "n"), "SMCD", "No"),
            Arguments.of("DiabetesEducationalSelfManagement", Map.of("EducationalTrainingPerformed", "N"), "DMME", "No"),
            Arguments.of("HypoglycemicEpisodes", Map.of("NumOfReportedEpisodes", "2"), "HYPE", "2"),
            Arguments.of("SelfMonitoringBloodGlucose", Map.of("SelfMonitoring", "Y"), "SMBG", "Yes"));
    }
    @ParameterizedTest @MethodSource("careKinds")
    void shouldMapEachCoreCareKind_toAnExplicitType(String section, Map<String, String> values,
            String expectedType, String expectedValue) throws Exception {
        var result = care.normalize(care(section, values), command(0));
        assertEquals(expectedType, result.measurementType()); assertEquals(expectedValue, result.value());
        assertEquals(LocalDate.of(2020, 1, 1), result.date().earliest());
        assertFalse(result.nativeOverflow()); assertFalse(result.uninterpreted());
    }
    @Test void shouldKeepSourceTextUninterpreted_insteadOfNumericValueOrNormalExamResult() throws Exception {
        var result = care.normalize(care("Weight", Map.of("Weight", "Unable to stand", "WeightUnit", "kg")), command(0));
        assertEquals("CDS_CARE_VALUE", result.measurementType()); assertEquals("Unable to stand", result.value());
        assertTrue(result.uninterpreted());
        assertEquals("CDS_CARE_VALUE_NOT_QUANTIFIED", result.diagnostics().getFirst().code());
        var exam = care.normalize(care("DiabetesComplicationsScreening", Map.of("ExamCode", "67536-3")), command(0));
        assertEquals("CDS_EXAM", exam.measurementType()); assertEquals("Performed", exam.value());
        assertNotEquals("FTLS", exam.measurementType()); assertNotEquals("NRTF", exam.measurementType());
    }
    @Test void shouldFeedImportedSmokingAssertion_toExistingNativeRecommendationConsumer() throws Exception {
        var consumer = mock(io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo.class, CALLS_REAL_METHODS);
        for (String source : java.util.List.of("Y", "N")) {
            var normalized = care.normalize(care("SmokingStatus", Map.of("Status", source)), command(0));
            var bean = new io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBean();
            bean.setDataField(normalized.value());
            doReturn(new java.util.ArrayList<>(java.util.List.of(bean))).when(consumer).getMeasurementData("SKST");
            assertEquals(source.equals("Y") ? 1 : 0, consumer.isDataEqualToYes("SKST"));
            assertTrue(normalized.diagnostics().stream().anyMatch(d -> d.code().equals("CDS_CARE_BOOLEAN_NORMALIZED")));
        }
    }
    @Test void shouldKeepHugeNumericValueOutOfNativeGraph_insteadOfInfinity() throws Exception {
        var result = care.normalize(care("Height", Map.of("Height", "9".repeat(1024), "HeightUnit", "cm")), command(0));
        assertTrue(result.uninterpreted()); assertEquals("CDS_CARE_VALUE", result.measurementType());
    }
    @Test void shouldKeepGoalsAsText_evenWhenEmptyAndNeverYesNo() throws Exception {
        var result = care.normalize(care("DiabetesSelfManagementCollaborative",
                Map.of("CodeValue", "44943-9", "DocumentedGoals", "")), command(0));
        assertEquals("CDS_GOALS", result.measurementType()); assertEquals("", result.value());
    }
    @Test void shouldNeitherReadUnboundedGoalsIntoMemory_norShowThemAsClippedClinicalStatement() throws Exception {
        var record = care("DiabetesSelfManagementCollaborative", Map.of("CodeValue", "44943-9",
                "DocumentedGoals", "X".repeat(CdsCareNormalizationService.GOAL_NATIVE_BYTE_BUDGET + 1)));
        CdsFieldParser guarded = new CdsFieldParser() {
            @Override public InputStream openValue(CdsRecordDto r, CdsFieldDto f) throws IOException {
                if (f.path().startsWith("DocumentedGoals")) throw new AssertionError("Unbounded goals were materialized");
                return super.openValue(r, f);
            }
        };
        var result = new CdsCareNormalizationService(guarded, new CdsClinicalNormalizationService(guarded)).normalize(record, command(0));
        assertTrue(result.nativeOverflow()); assertEquals(CdsCareNormalizationService.OVERFLOW_GOALS, result.value());
        assertTrue(result.sourceValueDigest().matches("[0-9a-f]{64}"));
        assertEquals("CDS_CARE_COMPLETE_VALUE_IN_SOURCE", result.diagnostics().getFirst().code());
    }
    @Test void shouldRefuseUnrepresentableNativeCareDateDuringPreflight_withoutAnyDaoWork() throws Exception {
        var record = CdsClinicalFixture.record(directory, "Weight", Map.of(
                "Weight", "75", "WeightUnit", "kg", "Date", "1582-10-10"));
        var value = care.normalize(record, command(0));
        var measurements = mock(MeasurementDao.class);
        var extensions = mock(MeasurementsExtDao.class);
        var types = mock(MeasurementTypeDao.class);
        var ledger = mock(CdsImportLedgerService.class);
        var persister = new CdsCarePersister(measurements, extensions, types, ledger);
        ReflectionTestUtils.setField(persister, "clinicalZone", "America/Toronto");
        var error = assertThrows(CdsClinicalException.class, () -> persister.validate(value));
        assertEquals("CDS_NATIVE_DATE_REQUIRES_REVIEW", error.code());
        assertEquals("1582-10-10", value.date().original());
        verifyNoInteractions(measurements, extensions, types, ledger);
    }

    @Test void shouldNeverDefaultOrGuess_forRequiredDateAndCoreUnits() throws Exception {
        var missing = CdsClinicalFixture.record(directory, "Weight", Map.of("Weight", "75", "WeightUnit", "kg"));
        assertThrows(CdsClinicalException.class, () -> care.normalize(missing, command(0)));
        assertThrows(CdsClinicalException.class,
                () -> care.normalize(care("Weight", Map.of("Weight", "150", "WeightUnit", "lb")), command(0)));
        assertEquals(LocalDate.of(2020, 1, 3), care.normalize(care("Height", Map.of("Height", "170", "HeightUnit", "cm")), command(2)).date().earliest());
    }
    @Test void shouldUseAcceptedLogicalValueForReviewedGoals_insteadOfOriginalBytesOrLength() throws Exception {
        var original = care("DiabetesSelfManagementCollaborative", Map.of("CodeValue", "44943-9",
                "DocumentedGoals", "X".repeat(100000)));
        var corrected = CdsClinicalFixture.corrected(original, "DocumentedGoals", "Reviewed goal");
        var value = care.normalize(corrected, command(0));
        assertEquals("Reviewed goal", value.value()); assertFalse(value.nativeOverflow());
        var small = care("DiabetesSelfManagementCollaborative", Map.of("CodeValue", "44943-9", "DocumentedGoals", "Original"));
        var enlarged = CdsClinicalFixture.corrected(small, "DocumentedGoals", "X".repeat(100000));
        assertTrue(care.normalize(enlarged, command(0)).nativeOverflow());
    }
    @Test void shouldUseUnknownSourceAuthorAndConfiguredCalendarAnchor_forNativeCare() throws Exception {
        var source = care.normalize(care("Weight", Map.of("Weight", "75", "WeightUnit", "kg")), command(0));
        var persister = new CdsCarePersister(mock(MeasurementDao.class), mock(MeasurementsExtDao.class),
                mock(MeasurementTypeDao.class), mock(CdsImportLedgerService.class));
        ReflectionTestUtils.setField(persister, "clinicalZone", "America/Toronto");
        var row = persister.candidate(42, source);
        assertEquals("", row.getProviderNo()); assertEquals("75", row.getDataField());
        assertEquals(Instant.parse("2020-01-01T05:00:00Z"), row.getDateObserved().toInstant());
        assertThrows(CdsImportException.class, () -> persister.write("run", 1, "unit", 42,
                care("Weight", Map.of("Weight", "75", "WeightUnit", "kg")), source));
    }
    @Test void shouldKeepSeparateMeaning_forContactNamesRolesPhonesAndUnknownConsent() throws Exception {
        LinkedHashMap<String, String> source = new LinkedHashMap<>();
        source.put("ContactPurpose[1]/PurposeAsEnum", "EC"); source.put("ContactPurpose[2]/PurposeAsEnum", "SDM");
        source.put("ContactPurpose[3]/PurposeAsEnum", "PA"); source.put("Name/FirstName", "F".repeat(50));
        source.put("Name/MiddleName", "M".repeat(50)); source.put("Name/LastName", "Source family");
        source.put("PhoneNumber[1]/@phoneNumberType", "W"); source.put("PhoneNumber[1]/areaCode", "416");
        source.put("PhoneNumber[1]/number", "5551234"); source.put("PhoneNumber[1]/exchange", "555");
        source.put("PhoneNumber[1]/extension", "123");
        var value = contacts.normalize(CdsClinicalFixture.record(directory, "Contact", source));
        assertEquals(101, value.firstName().length()); assertEquals(Boolean.TRUE, value.emergencyContact());
        assertEquals(Boolean.TRUE, value.substituteDecisionMaker()); assertEquals(java.util.List.of("Power of Attorney"), value.roles());
        assertEquals("123", value.phones().get("W").extension()); assertEquals(2, value.diagnostics().size());
        var persister = new CdsContactPersister(mock(ContactDao.class), mock(DemographicContactDao.class), mock(CdsImportLedgerService.class));
        var contact = persister.candidate(value); assertEquals("416-555-1234", contact.getWorkPhone());
        assertEquals("123", contact.getWorkPhoneExtension());
        var link = persister.association(42, 7, value.roles().getFirst(), command(0), value);
        assertNull(link.getConsentToContact()); assertFalse(link.isConsentToContact());
        assertEquals(2, link.getType()); assertEquals("7", link.getContactId());
    }
    @Test void shouldNotAssertCodedSubstituteDecisionMaker_fromPlainTextPurpose() throws Exception {
        var value = contacts.normalize(CdsClinicalFixture.record(directory, "Contact", Map.of("ContactPurpose/PurposeAsPlainText", "SDM")));
        assertNull(value.substituteDecisionMaker()); assertEquals(java.util.List.of("SDM"), value.roles());
    }
    @Test void shouldKeepRepeatedDistinctPhonesSourceOnly_insteadOfSelectingOrDiscardingOne() throws Exception {
        LinkedHashMap<String, String> source = new LinkedHashMap<>();
        source.put("PhoneNumber[1]/@phoneNumberType", "R"); source.put("PhoneNumber[1]/phoneNumber", "4165551111");
        source.put("PhoneNumber[2]/@phoneNumberType", "R"); source.put("PhoneNumber[2]/phoneNumber", "4165552222");
        var value = contacts.normalize(CdsClinicalFixture.record(directory, "Contact", source));
        assertTrue(value.phones().isEmpty());
        assertTrue(value.diagnostics().stream().anyMatch(d -> d.code().equals("CDS_CONTACT_AMBIGUOUS_PHONE_SOURCE_VIEW")));
    }
    @Test void shouldBoundNativeProjectionWithExplicitSourceReference_forArbitrarilyManyPurposes() throws Exception {
        LinkedHashMap<String, String> source = new LinkedHashMap<>();
        for (int i = 1; i <= 1000; i++) source.put("ContactPurpose[" + i + "]/PurposeAsPlainText", "Purpose " + i);
        source.put("ContactPurpose[1001]/PurposeAsEnum", "EC");
        var value = contacts.normalize(CdsClinicalFixture.record(directory, "Contact", source));
        assertEquals(java.util.List.of(CdsContactNormalizationService.MULTIPLE_PURPOSES), value.roles());
        assertEquals(Boolean.TRUE, value.emergencyContact());
        assertEquals(2, value.diagnostics().size());
    }
    @Test void shouldNeverProjectPartialPhone_fromIncompleteOrOverflowedStructuredPhone() throws Exception {
        for (String exchange : java.util.List.of("999", "X".repeat(100000))) {
            var value = contacts.normalize(CdsClinicalFixture.record(directory, "Contact", Map.of(
                    "PhoneNumber/@phoneNumberType", "R", "PhoneNumber/areaCode", "416",
                    "PhoneNumber/number", "5551234", "PhoneNumber/exchange", exchange)));
            assertTrue(value.phones().isEmpty());
            assertTrue(value.diagnostics().stream().anyMatch(d -> d.code().equals("CDS_CONTACT_PHONE_SOURCE_VIEW")));
        }
    }
    @Test void shouldNotRenderUnknownContactName_asLiteralNull() {
        assertEquals("Unknown", new io.github.carlos_emr.carlos.commn.model.Contact().getFormattedName());
    }
    @Test void shouldNotRescanSourceInsideClinicalWrite_withPreparedStrategies() throws Exception {
        var record = care("Weight", Map.of("Weight", "75", "WeightUnit", "kg"));
        var value = care.normalize(record, command(0));
        var normalization = mock(CdsCareNormalizationService.class);
        var persister = mock(CdsCarePersister.class); var preparation = mock(CdsNativePreparationService.class);
        var strategy = new CdsCareImportService(normalization, persister, preparation);
        when(normalization.normalize(record, command(0))).thenReturn(value);
        strategy.prepare("run", 1, "unit", record, command(0), null, "patient.xml");
        verify(preparation).store("run", 1, "unit", record.sha256(), command(0), value);
        clearInvocations(normalization);
        when(preparation.load("run", 1, "unit", record.sha256(), command(0), CdsCareElementDto.class)).thenReturn(value);
        when(persister.write("run", 1, "unit", 42, record, value)).thenReturn(Map.of("measurements", "7"));
        assertEquals(Map.of("measurements", "7"), strategy.write("run", 1, "unit", 42, record, command(0)));
        verifyNoInteractions(normalization);
    }
    private CdsRecordDto care(String section, Map<String, String> fields) throws IOException {
        Map<String, String> values = new LinkedHashMap<>(fields); values.put("Date", "2020-01-01");
        return CdsClinicalFixture.record(directory, section, values);
    }
    private CdsImportCommand command(int shift) {
        return new CdsImportCommand(directory, "care-test", "999998", null, null, "synthetic-fixture", "cds-5.2",
                false, false, false, shift, 1, true);
    }
}
