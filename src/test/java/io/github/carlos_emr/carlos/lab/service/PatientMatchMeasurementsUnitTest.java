/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class PatientMatchMeasurementsUnitTest extends CarlosUnitTestBase {
    private MeasurementDao measurements;
    private MockedConstruction<CommonLabResultData> versions;
    private MockedStatic<CommonLabResultData> common;

    @BeforeEach
    void setUp() {
        createAndRegisterMock(PatientLabRoutingDao.class);
        createAndRegisterMock(ProviderLabRoutingDao.class);
        createAndRegisterMock(QueueDocumentLinkDao.class);
        createAndRegisterMock(SecurityInfoManager.class);
        measurements = createAndRegisterMock(MeasurementDao.class);
        versions = mockConstruction(CommonLabResultData.class, (mock, context) ->
                when(mock.getMatchingLabs("555", "HL7")).thenReturn("555,556"));
        common = mockStatic(CommonLabResultData.class, CALLS_REAL_METHODS);
        common.when(() -> CommonLabResultData.populateMeasurementsTable(anyString(), anyString(), anyString()))
                .thenAnswer(call -> null);
    }

    @AfterEach
    void tearDown() {
        common.close();
        versions.close();
    }

    @Test
    void shouldMoveImportedValuesAndKeepAnnotations_whenPatientIsCorrected() {
        var value = new Measurement();
        value.setDemographicId(12);
        value.setDataField("5.6");
        value.setComments("existing annotation");
        when(measurements.findByValue("lab_no", "556")).thenReturn(List.of(value));
        assertThat(CommonLabResultData.updatePatientLabRouting("555", "42", "HL7")).isTrue();
        assertThat(value.getDemographicId()).isEqualTo(42);
        assertThat(value.getDataField()).isEqualTo("5.6");
        assertThat(value.getComments()).isEqualTo("existing annotation");
        verify(measurements).merge(value);
        common.verify(() -> CommonLabResultData.populateMeasurementsTable(anyString(), anyString(), anyString()), never());
    }

    @Test
    void shouldAvoidDuplicateMeasurements_whenPatientIsMatchedAgain() {
        var value = new Measurement();
        value.setDemographicId(42);
        when(measurements.findByValue("lab_no", "556")).thenReturn(List.of(value));
        assertThat(CommonLabResultData.updatePatientLabRouting("555", "42", "HL7")).isTrue();
        verify(measurements, never()).merge(any());
        common.verify(() -> CommonLabResultData.populateMeasurementsTable(anyString(), anyString(), anyString()), never());
    }

    @Test
    void shouldImportOnlyLatestVersion_whenUnmatchedLabHasNoMeasurements() {
        assertThat(CommonLabResultData.updatePatientLabRouting("555", "42", "HL7")).isTrue();
        common.verify(() -> CommonLabResultData.populateMeasurementsTable("556", "42", "HL7"));
        common.verify(() -> CommonLabResultData.populateMeasurementsTable("555", "42", "HL7"), never());
    }

    @Test
    void shouldReportFailure_whenMovingMeasurementFails() {
        var value = new Measurement();
        value.setDemographicId(12);
        when(measurements.findByValue("lab_no", "556")).thenReturn(List.of(value));
        doThrow(new IllegalStateException("database failure")).when(measurements).merge(value);
        assertThat(CommonLabResultData.updatePatientLabRouting("555", "42", "HL7")).isFalse();
    }
}
