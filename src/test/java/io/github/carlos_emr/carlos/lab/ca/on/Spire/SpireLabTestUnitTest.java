/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.lab.ca.on.Spire;

import io.github.carlos_emr.carlos.commn.dao.LabTestResultsDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.logging.LoggerLevelOverride;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SpireLabTest debug log sanitization")
@Tag("unit")
@Tag("lab")
@Tag("security")
class SpireLabTestUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should sanitize demographic number in populate-demo debug log")
    void shouldSanitizeDemographicNo_inPopulateDemoDebugLog() {
        PatientLabRoutingDao patientLabRoutingDao = mock(PatientLabRoutingDao.class);
        registerMock(PatientLabRoutingDao.class, patientLabRoutingDao);
        when(patientLabRoutingDao.findByLabNoAndLabType(789, "CML")).thenReturn(List.of());

        SpireLabTest spireLabTest = new SpireLabTest();
        spireLabTest.demographicNo = "123\r\nforged";

        try (LogCapture capture = LogCapture.forLogger(SpireLabTest.class)) {
            ReflectionTestUtils.invokeMethod(spireLabTest, "populateDemoNo", "789");

            assertThat(capture.messages()).contains("going out 123\\r\\nforged");
        }
    }

    @Test
    @DisplayName("should skip demographic debug log when debug is disabled")
    void shouldSkipDemoDebugLog_whenDebugIsDisabled() {
        PatientLabRoutingDao patientLabRoutingDao = mock(PatientLabRoutingDao.class);
        registerMock(PatientLabRoutingDao.class, patientLabRoutingDao);
        when(patientLabRoutingDao.findByLabNoAndLabType(789, "CML")).thenReturn(List.of());

        SpireLabTest spireLabTest = new SpireLabTest();
        spireLabTest.demographicNo = "123\r\nforged";

        try (LogCapture capture = LogCapture.forLogger(SpireLabTest.class);
                LoggerLevelOverride ignored = LoggerLevelOverride.disableDebug(SpireLabTest.class)) {
            ReflectionTestUtils.invokeMethod(spireLabTest, "populateDemoNo", "789");

            assertThat(capture.messages()).isEmpty();
        }
    }

    @Test
    @DisplayName("should sanitize lab id and not log SQL in lab-result debug log")
    void shouldSanitizeLabId_withoutSqlInLabResultDebugLog() {
        LabTestResultsDao labTestResultsDao = mock(LabTestResultsDao.class);
        registerMock(LabTestResultsDao.class, labTestResultsDao);
        when(labTestResultsDao.findByLabPatientPhysicialInfoId(456)).thenReturn(List.of());

        SpireLabTest spireLabTest = new SpireLabTest();

        try (LogCapture capture = LogCapture.forLogger(SpireLabTest.class)) {
            ReflectionTestUtils.invokeMethod(spireLabTest, "populateLabResultData", "456");

            assertThat(capture.messages())
                    .contains("querying labTestResults for id 456")
                    .noneMatch(message -> message.contains("select * from labTestResults"));
        }
    }

    @Test
    @DisplayName("should skip lab-result debug log when debug is disabled")
    void shouldSkipLabResultDebugLog_whenDebugIsDisabled() {
        LabTestResultsDao labTestResultsDao = mock(LabTestResultsDao.class);
        registerMock(LabTestResultsDao.class, labTestResultsDao);
        when(labTestResultsDao.findByLabPatientPhysicialInfoId(456)).thenReturn(List.of());

        SpireLabTest spireLabTest = new SpireLabTest();

        try (LogCapture capture = LogCapture.forLogger(SpireLabTest.class);
                LoggerLevelOverride ignored = LoggerLevelOverride.disableDebug(SpireLabTest.class)) {
            ReflectionTestUtils.invokeMethod(spireLabTest, "populateLabResultData", "456");

            assertThat(capture.messages()).isEmpty();
        }
    }

}
