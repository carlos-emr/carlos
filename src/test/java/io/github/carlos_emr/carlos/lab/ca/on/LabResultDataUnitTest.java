/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.lab.ca.on;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.logging.LoggerLevelOverride;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("LabResultData debug log sanitization")
@Tag("unit")
@Tag("lab")
@Tag("security")
class LabResultDataUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should sanitize segment and lab type in matched-patient debug log")
    void shouldSanitizeIdentifiers_inMatchedPatientDebugLog() {
        registerMock(OscarLogDao.class, mock(OscarLogDao.class));
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        LabResultData labResultData = new LabResultData();
        labResultData.segmentID = "abc\r\nforged";
        labResultData.labType = "DOC\nfake";
        labResultData.isMatchedToPatient = true;

        try (LogCapture capture = LogCapture.forLogger(LabResultData.class)) {
            assertThat(labResultData.isMatchedToPatient()).isTrue();

            assertThat(capture.messages())
                    .contains("in isMatchedToPatient, segmentID=abc\\r\\nforged, labType=DOC\\nfake");
        }
    }

    @Test
    @DisplayName("should skip matched-patient debug log when debug is disabled")
    void shouldSkipDebugLog_whenDebugIsDisabled() {
        registerCommonLabResultDataMocks();
        LabResultData labResultData = new LabResultData();
        labResultData.segmentID = "abc\r\nforged";
        labResultData.labType = "DOC\nfake";
        labResultData.isMatchedToPatient = true;

        try (LogCapture capture = LogCapture.forLogger(LabResultData.class);
                LoggerLevelOverride ignored = LoggerLevelOverride.disableDebug(LabResultData.class)) {
            assertThat(labResultData.isMatchedToPatient()).isTrue();

            assertThat(capture.messages()).isEmpty();
        }
    }

    @Test
    void shouldSortConsistently_whenLabSourcesShareIdsOrDatesAreUnavailable() {
        registerCommonLabResultDataMocks();
        var labs = new java.util.ArrayList<LabResultData>();
        for (String source : java.util.List.of("HL7", "MDS", "CML", "BCP")) {
            for (String id : java.util.List.of("2", "10", "10", "legacy")) {
                LabResultData lab = new LabResultData(source);
                lab.setSegmentID(id);
                lab.setAttachmentUnavailable(true);
                labs.add(lab);
            }
        }
        for (LabResultData left : labs) for (LabResultData right : labs) {
            assertThat(Integer.signum(left.compareTo(right))).isEqualTo(-Integer.signum(right.compareTo(left)));
            assertThat(left.compareTo(left)).isZero();
        }
        java.util.Collections.sort(labs);
        assertThat(labs.subList(0, 8)).allMatch(lab -> "10".equals(lab.getSegmentID()));
        assertThat(labs.getFirst().getLabType()).isEqualTo("BCP");
        for (LabResultData lab : labs) {
            assertThat(lab.getDateObj()).isNull();
            assertThat(lab.getDiscipline()).isNull();
        }
    }

    private void registerCommonLabResultDataMocks() {
        registerMock(OscarLogDao.class, mock(OscarLogDao.class));
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
    }

}
