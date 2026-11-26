/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsDeletedDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementsDeleted;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.logging.LoggerLevelOverride;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Unlink of an HL7 report (#6214): the patient routing is cleared, every reading the report filed
 * is moved to the measurement archive with an audit row of its own, the report goes back to its
 * providers' inboxes as new, and nothing is written for a chart closed to the caller.
 */
@DisplayName("LabPatientUnlinkService")
@Tag("unit")
@Tag("lab")
@Tag("security")
class LabPatientUnlinkServiceUnitTest extends CarlosUnitTestBase {

    private static final int REPORT = 456;
    private static final int PATIENT = 123;
    private static final String HL7 = "HL7";

    private PatientLabRoutingDao patientRoutings;
    private ProviderLabRoutingDao providerRoutings;
    private MeasurementDao measurements;
    private MeasurementsDeletedDao archive;
    private SecurityInfoManager security;
    private LoggedInInfo actor;
    private LabPatientUnlinkService service;

    @BeforeEach
    void setUp() {
        patientRoutings = mock(PatientLabRoutingDao.class);
        providerRoutings = mock(ProviderLabRoutingDao.class);
        measurements = mock(MeasurementDao.class);
        archive = mock(MeasurementsDeletedDao.class);
        security = mock(SecurityInfoManager.class);
        actor = mock(LoggedInInfo.class);
        when(actor.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(eq(actor), eq("_lab"), eq("u"), anyString())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(actor), anyInt())).thenReturn(true);
        when(providerRoutings.findAllLabRoutingByIdandType(REPORT, HL7)).thenReturn(List.of());
        service = new LabPatientUnlinkService(patientRoutings, providerRoutings, measurements, archive, security);
    }

    private PatientLabRouting routedTo(int patient) {
        PatientLabRouting routing = new PatientLabRouting(REPORT, HL7, patient);
        when(patientRoutings.findByLabNoAndLabType(REPORT, HL7)).thenReturn(List.of(routing));
        return routing;
    }

    private static Measurement reading(int id, int patient, String value) {
        Measurement reading = new Measurement(id);
        reading.setDemographicId(patient);
        reading.setType("A1C");
        reading.setProviderNo("0");
        reading.setDataField(value);
        reading.setMeasuringInstruction("");
        reading.setComments("synthetic comment");
        reading.setDateObserved(new Date(1767225600000L));
        reading.setCreateDate(new Date(1767312000000L));
        return reading;
    }

    private void filed(Measurement... readings) {
        when(measurements.findByValue("lab_no", Integer.toString(REPORT))).thenReturn(List.of(readings));
    }

    @Test
    @DisplayName("should archive every reading the report filed when it is unlinked")
    void shouldArchiveFiledReadings_whenReportIsUnlinked() {
        PatientLabRouting routing = routedTo(PATIENT);
        Measurement first = reading(9001, PATIENT, "5.6");
        Measurement second = reading(9002, PATIENT, "7.1");
        filed(first, second);

        LabPatientUnlinkService.Result result = service.unlinkReport(actor, REPORT, "incorrect match", "127.0.0.1");

        assertThat(result).isEqualTo(new LabPatientUnlinkService.Result(true, PATIENT, 2));
        assertThat(routing.getDemographicNo()).isEqualTo(PatientLabRoutingDao.UNMATCHED);
        assertThat(routing.getDateModified()).isNotNull();
        verify(patientRoutings).merge(routing);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MeasurementsDeleted>> copies = ArgumentCaptor.forClass(List.class);
        verify(archive).batchPersistAtomically(copies.capture());
        assertThat(copies.getValue()).extracting(MeasurementsDeleted::getOriginalId).containsExactly(9001, 9002);
        assertThat(copies.getValue()).extracting(MeasurementsDeleted::getDataField).containsExactly("5.6", "7.1");
        assertThat(copies.getValue()).allSatisfy(copy -> {
            assertThat(copy.getDemographicNo()).isEqualTo(PATIENT);
            assertThat(copy.getType()).isEqualTo("A1C");
            assertThat(copy.getComments()).isEqualTo("synthetic comment");
            assertThat(copy.getDateObserved()).isEqualTo(new Date(1767225600000L));
            assertThat(copy.getDateEntered()).isEqualTo(new Date(1767312000000L));
        });
        verify(measurements).batchRemoveAtomically(List.of(first, second));
    }

    @Test
    @DisplayName("should take the report lock before reading or changing the routing")
    void shouldLockReport_beforeReadingRouting() {
        routedTo(PATIENT);
        filed(reading(9001, PATIENT, "5.6"));

        service.unlinkReport(actor, REPORT, "incorrect match", "127.0.0.1");

        InOrder order = inOrder(providerRoutings, patientRoutings, measurements, archive);
        order.verify(providerRoutings).lockRoutingReport(REPORT);
        order.verify(patientRoutings).findByLabNoAndLabType(REPORT, HL7);
        order.verify(measurements).findByValue("lab_no", Integer.toString(REPORT));
        order.verify(patientRoutings).merge(any(PatientLabRouting.class));
        order.verify(archive).batchPersistAtomically(anyList());
        order.verify(measurements).batchRemoveAtomically(anyList());
    }

    @Test
    @DisplayName("should audit the unlink and each archived reading by identifier only")
    void shouldAuditUnlinkAndEachArchivedReading_byIdentifierOnly() {
        routedTo(PATIENT);
        filed(reading(9001, PATIENT, "5.6"), reading(9002, PATIENT, "7.1"));

        service.unlinkReport(actor, REPORT, "incorrect match", "127.0.0.1");

        logActionMock.verify(() -> LogAction.addLogStrict("999998", LogConst.UNLINK, LogConst.CON_HL7_LAB,
                "456", "127.0.0.1", "123", "incorrect match"));
        logActionMock.verify(() -> LogAction.addLogStrict("999998", LogConst.ARCHIVE, LogConst.CON_MEASUREMENT,
                "9001", "127.0.0.1", "123", "lab 456 unlinked"));
        logActionMock.verify(() -> LogAction.addLogStrict("999998", LogConst.ARCHIVE, LogConst.CON_MEASUREMENT,
                "9002", "127.0.0.1", "123", "lab 456 unlinked"));
    }

    @Test
    @DisplayName("should unlink as before without touching the archive when the report filed no readings")
    void shouldUnlinkWithoutArchiving_whenReportFiledNoReadings() {
        PatientLabRouting routing = routedTo(PATIENT);
        filed();

        LabPatientUnlinkService.Result result = service.unlinkReport(actor, REPORT, "incorrect match", "127.0.0.1");

        assertThat(result).isEqualTo(new LabPatientUnlinkService.Result(true, PATIENT, 0));
        assertThat(routing.getDemographicNo()).isEqualTo(PatientLabRoutingDao.UNMATCHED);
        verifyNoInteractions(archive);
        verify(measurements, never()).batchRemoveAtomically(anyList());
    }

    @Test
    @DisplayName("should archive a reading once when its report extension is stored twice")
    void shouldArchiveReadingOnce_whenItsExtensionIsRepeated() {
        routedTo(PATIENT);
        Measurement reading = reading(9001, PATIENT, "5.6");
        filed(reading, reading);

        LabPatientUnlinkService.Result result = service.unlinkReport(actor, REPORT, "incorrect match", "127.0.0.1");

        assertThat(result.archivedMeasurements()).isEqualTo(1);
        verify(measurements).batchRemoveAtomically(List.of(reading));
        logActionMock.verify(() -> LogAction.addLogStrict(anyString(), eq(LogConst.ARCHIVE), anyString(),
                anyString(), anyString(), anyString(), anyString()), times(1));
    }

    @Test
    @DisplayName("should archive readings left on a chart by an earlier unlink when the report is unlinked again")
    void shouldArchiveLeftoverReadings_whenUnmatchedReportIsUnlinkedAgain() {
        // An OSCAR 19 or pre-#6214 unlink cleared the routing and left the readings on the chart.
        routedTo(PatientLabRoutingDao.UNMATCHED);
        filed(reading(9001, PATIENT, "5.6"));

        LabPatientUnlinkService.Result result = service.unlinkReport(actor, REPORT, "clean up", "127.0.0.1");

        assertThat(result).isEqualTo(new LabPatientUnlinkService.Result(true, PatientLabRoutingDao.UNMATCHED, 1));
        verify(security).hasPrivilege(actor, "_lab", "u", "123");
        verify(security).isAllowedAccessToPatientRecord(actor, PATIENT);
        logActionMock.verify(() -> LogAction.addLogStrict("999998", LogConst.ARCHIVE, LogConst.CON_MEASUREMENT,
                "9001", "127.0.0.1", "123", "lab 456 unlinked"));
    }

    @Test
    @DisplayName("should change nothing when the report has no patient routing")
    void shouldChangeNothing_whenReportHasNoPatientRouting() {
        when(patientRoutings.findByLabNoAndLabType(REPORT, HL7)).thenReturn(List.of());

        LabPatientUnlinkService.Result result = service.unlinkReport(actor, REPORT, "incorrect match", "127.0.0.1");

        assertThat(result).isEqualTo(new LabPatientUnlinkService.Result(false, PatientLabRoutingDao.UNMATCHED, 0));
        verify(patientRoutings, never()).merge(any());
        verifyNoInteractions(measurements, archive);
        verify(providerRoutings, never()).findAllLabRoutingByIdandType(anyInt(), anyString());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should refuse before any write when the routed patient's record is locked to the caller")
    void shouldRefuseBeforeAnyWrite_whenRoutedPatientRecordIsLocked() {
        PatientLabRouting routing = routedTo(PATIENT);
        filed(reading(9001, PATIENT, "5.6"));
        when(security.isAllowedAccessToPatientRecord(actor, PATIENT)).thenReturn(false);

        assertThatThrownBy(() -> service.unlinkReport(actor, REPORT, "incorrect match", "127.0.0.1"))
                .isInstanceOf(SecurityException.class);

        assertThat(routing.getDemographicNo()).isEqualTo(PATIENT);
        verify(patientRoutings, never()).merge(any());
        verifyNoInteractions(archive);
        verify(measurements, never()).batchRemoveAtomically(anyList());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should refuse before any write without the patient-specific _lab update right")
    void shouldRefuseBeforeAnyWrite_withoutPatientSpecificLabUpdateRight() {
        routedTo(PATIENT);
        filed(reading(9001, PATIENT, "5.6"));
        when(security.hasPrivilege(actor, "_lab", "u", "123")).thenReturn(false);

        assertThatThrownBy(() -> service.unlinkReport(actor, REPORT, "incorrect match", "127.0.0.1"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_lab)");

        verify(patientRoutings, never()).merge(any());
        verifyNoInteractions(archive);
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should refuse before any write when a chart holding one of the readings is locked to the caller")
    void shouldRefuseBeforeAnyWrite_whenChartHoldingReadingIsLocked() {
        routedTo(PATIENT);
        filed(reading(9001, PATIENT, "5.6"), reading(9002, 777, "7.1"));
        when(security.isAllowedAccessToPatientRecord(actor, 777)).thenReturn(false);

        assertThatThrownBy(() -> service.unlinkReport(actor, REPORT, "incorrect match", "127.0.0.1"))
                .isInstanceOf(SecurityException.class);

        verify(patientRoutings, never()).merge(any());
        verifyNoInteractions(archive);
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should put the report back in each provider's inbox as new with the unlink noted")
    void shouldReturnReportToProviderInboxes_asNew() {
        routedTo(PATIENT);
        filed();
        ProviderLabRoutingModel acknowledged = new ProviderLabRoutingModel();
        acknowledged.setLabNo(REPORT);
        acknowledged.setLabType(HL7);
        acknowledged.setProviderNo("100002");
        acknowledged.setStatus("A");
        acknowledged.setComment("Reviewed");
        ProviderLabRoutingModel uncommented = new ProviderLabRoutingModel();
        uncommented.setLabNo(REPORT);
        uncommented.setLabType(HL7);
        uncommented.setProviderNo("0");
        uncommented.setStatus("N");
        when(providerRoutings.findAllLabRoutingByIdandType(REPORT, HL7)).thenReturn(List.of(acknowledged, uncommented));

        service.unlinkReport(actor, REPORT, "incorrect match", "127.0.0.1");

        assertThat(acknowledged.getStatus()).isEqualTo("N");
        assertThat(acknowledged.getComment()).isEqualTo("Reviewed Lab unlinked from incorrect demographic number: 123");
        assertThat(acknowledged.getTimestamp()).isNotNull();
        assertThat(uncommented.getComment()).isEqualTo("Lab unlinked from incorrect demographic number: 123");
        verify(providerRoutings).merge(acknowledged);
        verify(providerRoutings).merge(uncommented);
        logActionMock.verify(() -> LogAction.addLogStrict("999998", LogConst.UNLINK, LogConst.CON_HL7_LAB, "456",
                "127.0.0.1", "123", "Provider 100002 Ack status from A to N"));
    }

    @Test
    @DisplayName("should refuse an invalid report number before taking any lock")
    void shouldRefuseInvalidReportNumber_beforeTakingAnyLock() {
        assertThatThrownBy(() -> service.unlinkReport(actor, 0, "incorrect match", "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(providerRoutings, patientRoutings, measurements, archive);
    }

    @Test
    @DisplayName("should sanitize lab and demographic identifiers in the unlink debug log")
    void shouldSanitizeIdentifiers_inUnlinkDebugLog() {
        routedTo(PATIENT);
        filed();

        try (LogCapture capture = LogCapture.forLogger(LabPatientUnlinkService.class)) {
            service.unlinkReport(actor, REPORT, "incorrect match", "127.0.0.1");

            assertThat(capture.messages()).contains("Unlinked lab segmentID=456 from demographic=123");
        }
    }

    @Test
    @DisplayName("should skip the unlink debug log when debug is disabled")
    void shouldSkipDebugLog_whenDebugIsDisabled() {
        routedTo(PATIENT);
        filed();

        try (LogCapture capture = LogCapture.forLogger(LabPatientUnlinkService.class);
                LoggerLevelOverride ignored = LoggerLevelOverride.disableDebug(LabPatientUnlinkService.class)) {
            service.unlinkReport(actor, REPORT, "incorrect match", "127.0.0.1");

            assertThat(capture.messages()).isEmpty();
        }
    }
}
