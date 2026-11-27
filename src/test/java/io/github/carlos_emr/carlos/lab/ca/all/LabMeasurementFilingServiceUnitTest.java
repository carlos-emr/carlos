/**
 * Copyright (c) 2026. CARLOS EMR Project Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * CARLOS EMR Project: https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.lab.ca.all;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoChainLockDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsDeletedDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Filing one report's measurements (#6391): the locks come before every read and write, a report
 * that is not the current version files nothing, the replaced values are only this patient's, and
 * after the upload commits a failure is logged and audited, never thrown.
 */
@Tag("unit")
@Tag("service")
@DisplayName("LabMeasurementFilingService")
class LabMeasurementFilingServiceUnitTest extends CarlosUnitTestBase {

    private PlatformTransactionManager transactions;
    private Hl7TextInfoChainLockDao locks;
    private MeasurementDao measurementDao;
    private MeasurementsExtDao measurementsExtDao;
    private MeasurementsDeletedDao measurementsDeletedDao;

    @BeforeEach
    void setUp() {
        transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
        locks = mock(Hl7TextInfoChainLockDao.class);
        measurementDao = mock(MeasurementDao.class);
        measurementsExtDao = mock(MeasurementsExtDao.class);
        measurementsDeletedDao = mock(MeasurementsDeletedDao.class);
        when(locks.lockByLabNumber(anyInt())).thenReturn(new Hl7TextInfo());
        // A stored measurement has its id: the extensions name it.
        org.mockito.Mockito.doAnswer(invocation -> {
            org.springframework.test.util.ReflectionTestUtils.setField(invocation.getArgument(0), "id", 1);
            return null;
        }).when(measurementDao).persist(any(Measurement.class));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private LabMeasurementFilingService service(String chain) {
        return new LabMeasurementFilingService(transactions, locks, measurementDao, measurementsExtDao,
                measurementsDeletedDao, lab -> chain);
    }

    private static LabMeasurementFilingDto filing(String lab, Integer patient, boolean revisionOrdered) {
        Measurement m = new Measurement();
        m.setDataField("5.2");
        return new LabMeasurementFilingDto(lab, patient, revisionOrdered, "ACC-1", "FILL-1",
                List.of(new FiledMeasurementDto(m, Map.of("lab_no", lab))));
    }

    private static Measurement stored(Integer demographic) {
        Measurement m = new Measurement();
        m.setDemographicId(demographic);
        m.setType("A1C");
        m.setDataField("7");
        return m;
    }

    private void inTransactionWithSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    private static void commit() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
    }

    @Test
    void shouldTakeEveryLockBeforeAnyReadOrWrite_whenFilingTheCurrentVersion() {
        service("100,101").file(filing("101", 7, false));

        InOrder order = inOrder(locks, measurementDao, measurementsDeletedDao, measurementsExtDao);
        order.verify(locks).lockByAccession("ACC-1");
        order.verify(locks).lockByFillerOrderNumber("FILL-1");
        order.verify(locks).lockByLabNumber(101);
        order.verify(measurementDao).findByValue("lab_no", "100");
        order.verify(measurementsDeletedDao).batchPersistAtomically(anyList());
        order.verify(measurementDao).batchRemoveAtomically(anyList());
        order.verify(measurementDao).persist(any(Measurement.class));
        order.verify(measurementsExtDao).batchPersistAtomically(anyList(), eq(50));
    }

    @Test
    void shouldFileNothingAndArchiveNothing_whenALaterVersionExists() {
        var result = service("100,101").file(filing("100", 7, false));

        assertThat(result).isEqualTo(LabMeasurementFilingService.Result.NOT_CURRENT);
        verify(measurementDao, never()).persist(any(Measurement.class));
        verify(measurementDao, never()).batchRemoveAtomically(anyList());
        verifyNoInteractions(measurementsDeletedDao, measurementsExtDao);
    }

    @Test
    void shouldFileTheLastVersionOfTheLabsOwnOrder_whenTheChainIsRevisionOrdered() {
        // A CML chain is ordered by the lab's revision evidence: its last version is current even though
        // it does not have the highest lab number, and the highest one is then history.
        var higherNumber = service("101,100").file(filing("101", 7, true));
        var lastRevision = service("101,100").file(filing("100", 7, true));

        assertThat(higherNumber).isEqualTo(LabMeasurementFilingService.Result.NOT_CURRENT);
        assertThat(lastRevision).isEqualTo(LabMeasurementFilingService.Result.FILED);
        verify(measurementDao, times(1)).persist(any(Measurement.class));
    }

    @Test
    void shouldFileNothing_whenTheReportWasRemovedBeforeItRan() {
        when(locks.lockByLabNumber(101)).thenReturn(null);

        var result = service("101").file(filing("101", 7, false));

        assertThat(result).isEqualTo(LabMeasurementFilingService.Result.LAB_REMOVED);
        verifyNoInteractions(measurementDao, measurementsDeletedDao, measurementsExtDao);
    }

    @Test
    void shouldThrowBeforeAnyWrite_whenTheReportIsAbsentFromItsChain() {
        assertThatThrownBy(() -> service("100,102").file(filing("101", 7, false)))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(measurementDao, measurementsDeletedDao, measurementsExtDao);
    }

    @Test
    void shouldLockOnlyTheReportsOwnRow_whenItHasNoAccessionAndNoFillerNumber() {
        Measurement m = new Measurement();
        var dto = new LabMeasurementFilingDto("101", 7, false, null, "  ", List.of(new FiledMeasurementDto(m, Map.of())));

        service("101").file(dto);

        verify(locks).lockByAccession(null);
        verify(locks).lockByFillerOrderNumber("  ");
        verify(locks).lockByLabNumber(101);
    }

    @Test
    void shouldReplaceThisPatientsValuesIncludingTheReportsOwn_whenRefiling() {
        Measurement ownEarlier = stored(7);
        Measurement previousVersion = stored(7);
        Measurement otherPatient = stored(8);
        Measurement noPatientOfAnotherVersion = stored(null);
        when(measurementDao.findByValue("lab_no", "101")).thenReturn(List.of(ownEarlier));
        when(measurementDao.findByValue("lab_no", "100"))
                .thenReturn(List.of(previousVersion, otherPatient, noPatientOfAnotherVersion));

        service("100,101").file(filing("101", 7, false));

        // In chain order: version 100's value, then the report's own earlier value.
        verify(measurementDao).batchRemoveAtomically(List.of(previousVersion, ownEarlier));
    }

    @Test
    void shouldReplaceOnlyTheReportsOwnValues_whenNoPatientIsKnown() {
        Measurement own = stored(null);
        Measurement ownOfAPatient = stored(7);
        Measurement otherVersion = stored(7);
        when(measurementDao.findByValue("lab_no", "101")).thenReturn(List.of(own, ownOfAPatient));
        when(measurementDao.findByValue("lab_no", "100")).thenReturn(List.of(otherVersion));

        service("100,101").file(filing("101", null, false));

        verify(measurementDao).batchRemoveAtomically(List.of(own, ownOfAPatient));
    }

    @Test
    void shouldRetryTheWholeTransactionUpToThreeTimes_whenTheDatabaseRefusesALockAsJpaReportsIt() {
        // Nothing here translates JPA exceptions to Spring's: a real deadlock arrives as Jakarta's own.
        when(locks.lockByAccession("ACC-1")).thenThrow(new jakarta.persistence.PersistenceException(
                new jakarta.persistence.PessimisticLockException("deadlock")));

        assertThatThrownBy(() -> service("101").file(filing("101", 7, false)))
                .isInstanceOf(jakarta.persistence.PersistenceException.class);

        verify(transactions, times(LabMeasurementFilingService.MAX_ATTEMPTS)).getTransaction(any());
    }

    @Test
    void shouldRetryTheWholeTransactionUpToThreeTimes_whenSpringTranslatedTheLockFailure() {
        when(locks.lockByAccession("ACC-1")).thenThrow(new CannotAcquireLockException("deadlock"));

        assertThatThrownBy(() -> service("101").file(filing("101", 7, false)))
                .isInstanceOf(CannotAcquireLockException.class);

        verify(transactions, times(LabMeasurementFilingService.MAX_ATTEMPTS)).getTransaction(any());
    }

    @Test
    void shouldNotRetry_whenTheFailureIsNotALockFailure() {
        when(locks.lockByAccession("ACC-1")).thenThrow(new IllegalStateException("not a lock"));

        assertThatThrownBy(() -> service("101").file(filing("101", 7, false)))
                .isInstanceOf(IllegalStateException.class);

        verify(transactions, times(1)).getTransaction(any());
    }

    @Test
    void shouldRecogniseEveryWayTheDatabaseRefusesALock_inTheCauseChain() {
        var driver = new java.sql.SQLTransactionRollbackException("deadlock", "40001", 1213);
        assertThat(LabMeasurementFilingService.isLockFailure(new RuntimeException(new RuntimeException(driver))))
                .isTrue();
        assertThat(LabMeasurementFilingService.isLockFailure(
                new RuntimeException(new java.sql.SQLException("lock wait timeout", "HY000", 1205)))).isTrue();
        assertThat(LabMeasurementFilingService.isLockFailure(
                new jakarta.persistence.LockTimeoutException("timeout"))).isTrue();
        assertThat(LabMeasurementFilingService.isLockFailure(
                new org.hibernate.PessimisticLockException("lock", new java.sql.SQLException(), "select"))).isTrue();
        assertThat(LabMeasurementFilingService.isLockFailure(
                new org.hibernate.exception.LockAcquisitionException("deadlock", new java.sql.SQLException())))
                .isTrue();
        assertThat(LabMeasurementFilingService.isLockFailure(
                new org.hibernate.exception.LockTimeoutException("timeout", new java.sql.SQLException()))).isTrue();
        // Neither another SQL error, nor a failure with no lock anywhere in its chain, nor a cause cycle.
        assertThat(LabMeasurementFilingService.isLockFailure(
                new RuntimeException(new java.sql.SQLException("duplicate", "23000", 1062)))).isFalse();
        assertThat(LabMeasurementFilingService.isLockFailure(new IllegalStateException("x"))).isFalse();
        assertThat(LabMeasurementFilingService.isLockFailure(null)).isFalse();
        var a = new RuntimeException("a");
        var b = new RuntimeException("b", a);
        a.initCause(b);
        assertThat(LabMeasurementFilingService.isLockFailure(a)).isFalse();
    }

    @Test
    void shouldStopRetryingAndKeepTheInterrupt_whenTheBackoffIsInterrupted() {
        when(locks.lockByAccession("ACC-1")).thenThrow(new jakarta.persistence.PersistenceException(
                new jakarta.persistence.PessimisticLockException("deadlock")));
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> service("101").file(filing("101", 7, false)))
                    .isInstanceOf(jakarta.persistence.PersistenceException.class);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(transactions, times(1)).getTransaction(any());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldNotFileUntilTheUploadCommits_whenATransactionIsActive() {
        inTransactionWithSynchronization();
        var service = service("101");

        service.fileAfterCommit(filing("101", 7, false));

        verifyNoInteractions(locks, measurementDao, measurementsExtDao);
        commit();
        verify(measurementDao).persist(any(Measurement.class));
    }

    @Test
    void shouldFileNothing_whenTheUploadRollsBack() {
        inTransactionWithSynchronization();

        service("101").fileAfterCommit(filing("101", 7, false));
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verifyNoInteractions(locks, measurementDao, measurementsExtDao);
    }

    @Test
    void shouldFileAtOnceAndThrow_whenNoTransactionIsActive() {
        doThrow(new IllegalStateException("broken")).when(measurementDao).persist(any(Measurement.class));

        assertThatThrownBy(() -> service("101").fileAfterCommit(filing("101", 7, false)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldLogAuditAndContinueWithTheNextLab_whenAFilingAfterCommitFails() {
        doThrow(new IllegalStateException("Result 7.2 mmol/L for John Doe")).when(measurementDao)
                .persist(org.mockito.ArgumentMatchers.argThat((io.github.carlos_emr.carlos.commn.model.AbstractModel<?> m) ->
                        m instanceof Measurement x && "first".equals(x.getDataField())));
        inTransactionWithSynchronization();
        var service = service("101");
        Measurement first = new Measurement();
        first.setDataField("first");
        service.fileAfterCommit(new LabMeasurementFilingDto("101", 7, false, "ACC-1", null,
                List.of(new FiledMeasurementDto(first, Map.of()))));
        service.fileAfterCommit(filing("101", 7, false));

        try (LogCapture log = LogCapture.forLogger(LabMeasurementFilingService.class)) {
            commit();

            assertThat(log.messages()).anySatisfy(message -> assertThat(message)
                    .contains("101").contains("re-match the lab").doesNotContain("mmol").doesNotContain("John Doe"));
        }
        // The second lab's callback still ran and filed.
        verify(measurementDao, times(2)).persist(any(Measurement.class));
        logActionMock.verify(() -> LogAction.addLog(anyString(), anyString(), anyString(), anyString(), any(), anyString(),
                anyString()));
    }

    @Test
    void shouldNotThrow_whenTheAuditRowItselfFails() {
        doThrow(new IllegalStateException("broken")).when(measurementDao).persist(any(Measurement.class));
        logActionMock.when(() -> LogAction.addLog(anyString(), anyString(), anyString(), anyString(), any(), anyString(),
                anyString())).thenThrow(new IllegalStateException("audit down"));
        inTransactionWithSynchronization();
        service("101").fileAfterCommit(filing("101", 7, false));

        commit();
    }

    @Test
    void shouldRethrowAVirtualMachineError_whenFilingAfterCommit() {
        doThrow(new OutOfMemoryError("heap")).when(measurementDao).persist(any(Measurement.class));
        inTransactionWithSynchronization();
        service("101").fileAfterCommit(filing("101", 7, false));

        assertThatThrownBy(LabMeasurementFilingServiceUnitTest::commit).isInstanceOf(OutOfMemoryError.class);
    }

    @Test
    void shouldSwallowAnyOtherError_whenFilingAfterCommit() {
        doThrow(new AssertionError("boom")).when(measurementDao).persist(any(Measurement.class));
        inTransactionWithSynchronization();
        service("101").fileAfterCommit(filing("101", 7, false));

        commit();
    }

    @Test
    void shouldSayInfoNotError_whenTheReportWasRemovedBeforeTheCallbackRan() {
        when(locks.lockByLabNumber(101)).thenReturn(null);
        inTransactionWithSynchronization();
        service("101").fileAfterCommit(filing("101", 7, false));

        try (LogCapture log = LogCapture.forLogger(LabMeasurementFilingService.class)) {
            commit();

            assertThat(log.events()).noneMatch(event -> event.getLevel().isMoreSpecificThan(org.apache.logging.log4j.Level.ERROR));
            assertThat(log.messages()).anySatisfy(message -> assertThat(message).contains("removed"));
        }
        verifyNoInteractions(measurementDao, measurementsExtDao);
    }
}
