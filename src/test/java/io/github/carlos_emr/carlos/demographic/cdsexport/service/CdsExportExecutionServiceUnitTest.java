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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportEngine;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.*;
import java.io.IOException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** No clinical fixture is mistaken for a qualified production engine or a published result. */
@Tag("unit")
@Tag("cds-export")
class CdsExportExecutionServiceUnitTest {
    private static final String RUN = "22222222-2222-4222-8222-222222222222", HASH = "a".repeat(64);
    private CdsExportLedgerService ledger;
    private CdsExportAccessService access;
    private CdsExportEngine engine;
    private CdsExportJobDocumentDto document;
    private CdsExportLeaseDto lease;
    private CdsExportExecutionService execution;

    @BeforeEach
    void initializeExplicitEngineFixture() {
        ledger = mock(CdsExportLedgerService.class);
        access = mock(CdsExportAccessService.class);
        engine = mock(CdsExportEngine.class);
        var command =
                new CdsExportCommand(
                        "test",
                        "100",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        EnumSet.allOf(CdsCategory.class),
                        "recipient",
                        "audit");
        document =
                new CdsExportJobDocumentDto(
                        1,
                        new CdsExportActorDto("operator", "100", null, null),
                        command,
                        HASH,
                        "instance",
                        "b".repeat(64),
                        "c".repeat(64));
        lease = new CdsExportLeaseDto(RUN, 1, "33333333-3333-4333-8333-333333333333", document);
        when(ledger.document(RUN)).thenReturn(document);
        when(ledger.claim(RUN, HASH)).thenReturn(lease);
        when(ledger.status(RUN))
                .thenReturn(
                        new CdsExportJobDto(
                                RUN,
                                CdsExportState.DISCOVERING,
                                0,
                                null,
                                0,
                                0,
                                false,
                                true,
                                1,
                                null,
                                Instant.EPOCH,
                                Instant.EPOCH,
                                false));
        when(engine.compatibilityDigest()).thenReturn(HASH);
        when(engine.qualifiedProfiles()).thenReturn(Set.of(CdsLogicalProfile.ONTARIO_MD_5_2));
        Properties properties = new Properties();
        properties.setProperty("cds.export.enabled", "true");
        execution =
                new CdsExportExecutionService(
                        ledger,
                        access,
                        new CdsExportConfigurationService(properties),
                        new CdsExportEngineRegistry(List.of(engine)));
    }

    @AfterEach
    void preserveThreadAndTransactionIsolation() {
        Thread.interrupted();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void shouldRenewOwnershipDuringBlockedEngineWork_withAnIndependentMetadataHeartbeat()
            throws Exception {
        var scheduler = mock(java.util.concurrent.ScheduledExecutorService.class);
        var task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(
                        task.capture(),
                        eq(20L),
                        eq(20L),
                        eq(java.util.concurrent.TimeUnit.SECONDS)))
                .thenReturn(null);
        Properties enabled = new Properties();
        enabled.setProperty("cds.export.enabled", "true");
        execution =
                new CdsExportExecutionService(
                        ledger,
                        access,
                        new CdsExportConfigurationService(enabled),
                        new CdsExportEngineRegistry(List.of(engine)),
                        () -> scheduler);
        doAnswer(
                        call -> {
                            // The engine deliberately makes no checkpoint while its I/O is blocked.
                            task.getValue().run();
                            verify(ledger).checkpoint(lease);
                            return null;
                        })
                .when(engine)
                .execute(any(), any());
        execution.execute(RUN);
        verify(scheduler).shutdownNow();
    }

    @Test
    void shouldRecordAnExpiredOwnGeneration_insteadOfSilentlyRepeatingTheAttempt()
            throws Exception {
        doThrow(new CdsExportOwnerFencedException()).when(engine).execute(any(), any());
        when(ledger.retireExpiredLease(lease)).thenReturn(true);
        execution.execute(RUN);
        verify(ledger).retireExpiredLease(lease);
        verify(ledger, never()).stop(any(), any(), anyString());
    }

    @Test
    void shouldSeparateMappingReviewFromIoFailure_withoutClassifyingByExceptionMessage()
            throws Exception {
        doThrow(
                        new CdsExportMappingReviewException(
                                "CDS_EXPORT_APPOINTMENT_PATIENT_DISPLAY_REQUIRES_REVIEW"))
                .when(engine)
                .execute(any(), any());
        execution.execute(RUN);
        verify(ledger).stop(lease, CdsExportState.REVIEW_REQUIRED, "CDS_EXPORT_UNMAPPED_DATA");
        doThrow(new IOException("CDS_EXPORT_APPOINTMENT_PATIENT_DISPLAY_REQUIRES_REVIEW"))
                .when(engine)
                .execute(any(), any());
        execution.execute(RUN);
        verify(ledger).stop(lease, CdsExportState.FAILED, "CDS_EXPORT_IO_FAILED");
    }

    @Test
    void shouldLeaveTheJobWaiting_withoutFallingBackToAnUnqualifiedEngine() {
        var unavailable =
                new CdsExportExecutionService(
                        ledger,
                        access,
                        new CdsExportConfigurationService(new Properties()),
                        new CdsExportEngineRegistry(List.of()));
        assertThat(unavailable.execute(RUN)).isFalse();
        verify(ledger, never()).claim(anyString(), anyString());
        verifyNoInteractions(access);
    }

    @Test
    void shouldRunNoClinicalWork_whenAnotherDurableOwnerAlreadyHoldsTheJob() throws Exception {
        when(ledger.claim(RUN, HASH)).thenReturn(null);
        assertThat(execution.execute(RUN)).isFalse();
        verify(engine, never()).execute(any(), any());
        verifyNoInteractions(access);
    }

    @Test
    void shouldRefuseAMereEngineReturn_asClinicalCompletion() {
        assertThat(execution.execute(RUN)).isTrue();
        verify(ledger).stop(lease, CdsExportState.FAILED, "CDS_EXPORT_ENGINE_DID_NOT_PUBLISH");
    }

    @Test
    void shouldRecheckActualPatientAndSensitiveCategoryRights_andThrottleOnlyMetadataProbes()
            throws Exception {
        doAnswer(
                        call -> {
                            CdsExportRunControl control = call.getArgument(1);
                            control.authorizePatient(27);
                            control.authorizePharmacy(27);
                            control.authorizeCategory(27, CdsCategory.LABORATORY_RESULTS, true);
                            long started = System.nanoTime();
                            for (int i = 0; i < 10_000; i++) control.checkpoint();
                            long elapsed = System.nanoTime() - started;
                            verify(ledger, atMost((int) (elapsed / 1_000_000_000L) + 3))
                                    .checkpoint(lease);
                            return null;
                        })
                .when(engine)
                .execute(any(), any());
        execution.execute(RUN);
        verify(access).requireScope(document.actor(), document.command());
        verify(access).requirePatient(document.actor(), document.command(), 27);
        verify(access)
                .requireCategory(
                        document.actor(),
                        document.command(),
                        27,
                        CdsCategory.LABORATORY_RESULTS,
                        true);
        verify(access).requirePharmacyRead(document.actor(), document.command(), 27);
    }

    private static CdsExportAccessService.PatientDecisions decisions(int patient) {
        var decisions = new CdsExportAccessService.PatientDecisions(patient);
        decisions.grant(CdsExportAccessService.Gate.PATIENT);
        decisions.grant(CdsExportAccessService.Gate.PHARMACY);
        decisions.grant(
                CdsExportAccessService.Gate.category(CdsCategory.LABORATORY_RESULTS, true));
        return decisions;
    }

    @Test
    void shouldOpenAndCloseOnePassPerPatientSession_insteadOfOneTransactionPerCheck()
            throws Exception {
        when(access.decidePatient(document.actor(), document.command(), 27))
                .thenReturn(decisions(27));
        doAnswer(
                        call -> {
                            CdsExportRunControl control = call.getArgument(1);
                            control.beginPatient(27);
                            for (int step = 0; step < 5; step++) {
                                control.authorizePatient(27);
                                control.authorizePharmacy(27);
                                control.authorizeCategory(
                                        27, CdsCategory.LABORATORY_RESULTS, true);
                            }
                            control.endPatient(27);
                            control.discardPatient();
                            return null;
                        })
                .when(engine)
                .execute(any(), any());

        execution.execute(RUN);

        verify(access, times(1)).decidePatient(document.actor(), document.command(), 27);
        var request =
                org.mockito.ArgumentCaptor.forClass(CdsExportAccessService.PatientRecheck.class);
        verify(access, times(1))
                .recheckPatient(eq(document.actor()), eq(document.command()), request.capture());
        assertThat(request.getValue().patient()).isEqualTo(27);
        assertThat(request.getValue().gates())
                .containsExactly(
                        CdsExportAccessService.Gate.PATIENT,
                        CdsExportAccessService.Gate.PHARMACY,
                        CdsExportAccessService.Gate.category(
                                CdsCategory.LABORATORY_RESULTS, true));
        verify(access, never()).requirePatient(any(), any(), anyInt());
        verify(access, never()).requireCategory(any(), any(), anyInt(), any(), anyBoolean());
    }

    @Test
    void shouldHoldThePatient_whenTheClosingPassOfItsSessionIsRefused() throws Exception {
        when(access.decidePatient(document.actor(), document.command(), 27))
                .thenReturn(decisions(27));
        doThrow(new SecurityException("CDS_EXPORT_AUTHORIZATION_DENIED"))
                .when(access)
                .recheckPatient(any(), any(), any());
        doAnswer(
                        call -> {
                            CdsExportRunControl control = call.getArgument(1);
                            control.beginPatient(27);
                            control.authorizePatient(27);
                            assertThatThrownBy(() -> control.endPatient(27))
                                    .isInstanceOf(SecurityException.class);
                            // The session is gone: nothing of it answers a later check.
                            control.authorizePatient(27);
                            return null;
                        })
                .when(engine)
                .execute(any(), any());

        execution.execute(RUN);

        verify(access).requirePatient(document.actor(), document.command(), 27);
    }

    @Test
    void shouldDecideEachReauthorizationPassFromTheDatabase_andKeepNothingAfterIt()
            throws Exception {
        when(access.decidePatient(document.actor(), document.command(), 27))
                .thenReturn(decisions(27));
        var notes =
                List.of(
                        new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                .CdsExportNoteAccessDto(91, 27, "17", "1"));
        doAnswer(
                        call -> {
                            CdsExportRunControl control = call.getArgument(1);
                            for (int pass = 0; pass < 3; pass++) {
                                control.beginAuthorizationPass(27);
                                control.authorizePatient(27);
                                control.authorizePharmacy(27);
                                control.authorizeNotes(CdsCategory.CLINICAL_NOTES, notes);
                                control.endAuthorizationPass();
                            }
                            // Outside a pass every check is a fresh one again.
                            control.authorizePatient(27);
                            return null;
                        })
                .when(engine)
                .execute(any(), any());

        execution.execute(RUN);

        verify(access, times(3)).decidePatient(document.actor(), document.command(), 27);
        verify(access, times(3))
                .requireNotesRead(
                        document.actor(), document.command(), CdsCategory.CLINICAL_NOTES, notes);
        verify(access, times(1)).requirePatient(document.actor(), document.command(), 27);
        verify(access, never()).recheckPatient(any(), any(), any());
    }

    @Test
    void shouldRefuseAReauthorizationPassInsideASession_soItsClosingPassCannotBeDropped()
            throws Exception {
        when(access.decidePatient(document.actor(), document.command(), 27))
                .thenReturn(decisions(27));
        doAnswer(
                        call -> {
                            CdsExportRunControl control = call.getArgument(1);
                            control.beginPatient(27);
                            assertThatThrownBy(() -> control.beginAuthorizationPass(27))
                                    .hasMessage("CDS_EXPORT_SESSION_ACTIVE");
                            assertThatThrownBy(control::endAuthorizationPass)
                                    .hasMessage("CDS_EXPORT_SESSION_ACTIVE");
                            control.endPatient(27);
                            return null;
                        })
                .when(engine)
                .execute(any(), any());

        execution.execute(RUN);

        verify(access).recheckPatient(any(), any(), any());
    }

    /** A stall window of {@code millis}, with the heartbeat run by hand through {@code beat}. */
    private CdsExportExecutionService stallWindow(
            long millis, org.mockito.ArgumentCaptor<Runnable> beat) {
        var scheduler = mock(java.util.concurrent.ScheduledExecutorService.class);
        when(scheduler.scheduleWithFixedDelay(
                        beat.capture(),
                        eq(20L),
                        eq(20L),
                        eq(java.util.concurrent.TimeUnit.SECONDS)))
                .thenReturn(null);
        Properties properties = new Properties();
        properties.setProperty("cds.export.enabled", "true");
        properties.setProperty("cds.export.maxDurationMillis", Long.toString(millis));
        return new CdsExportExecutionService(
                ledger,
                access,
                new CdsExportConfigurationService(properties),
                new CdsExportEngineRegistry(List.of(engine)),
                () -> scheduler);
    }

    @Test
    void shouldNeverStopAWorkerThatKeepsReachingCheckpoints_howeverLongTheRosterTakes()
            throws Exception {
        var beat = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        var limited = stallWindow(300, beat);
        doAnswer(
                        call -> {
                            CdsExportRunControl control = call.getArgument(1);
                            // 1.2 s of work under a 300 ms window: four times the configured time,
                            // but never silent for as long as the window.
                            for (int step = 0; step < 24; step++) {
                                Thread.sleep(50);
                                control.checkpoint();
                                beat.getValue().run();
                            }
                            return null;
                        })
                .when(engine)
                .execute(any(), any());

        limited.execute(RUN);

        // The engine returned normally; no time limit ended the attempt.
        verify(ledger).stop(lease, CdsExportState.FAILED, "CDS_EXPORT_ENGINE_DID_NOT_PUBLISH");
        verify(ledger, never()).stop(any(), eq(CdsExportState.REVIEW_REQUIRED), anyString());
        verify(ledger, never()).retireExpiredLease(any());
        verify(ledger, atLeast(24)).checkpoint(lease);
    }

    /**
     * An engine that passes one checkpoint, is then silent for longer than the 100 ms stall
     * window while the heartbeat runs twice, and finally returns to a checkpoint.
     */
    private void stallingEngine(org.mockito.ArgumentCaptor<Runnable> beat) throws Exception {
        doAnswer(
                        call -> {
                            CdsExportRunControl control = call.getArgument(1);
                            control.checkpoint();
                            beat.getValue().run();
                            int renewed =
                                    org.mockito.Mockito.mockingDetails(ledger).getInvocations()
                                            .stream()
                                            .filter(
                                                    invocation ->
                                                            invocation
                                                                    .getMethod()
                                                                    .getName()
                                                                    .equals("checkpoint"))
                                            .toList()
                                            .size();
                            // The engine thread reaches no checkpoint for longer than the window.
                            Thread.sleep(300);
                            beat.getValue().run();
                            beat.getValue().run();
                            // The heartbeat stopped renewing the lease ...
                            verify(ledger, times(renewed)).checkpoint(lease);
                            // ... and the returning worker may not continue.
                            control.checkpoint();
                            return null;
                        })
                .when(engine)
                .execute(any(), any());
    }

    @Test
    void shouldEndAStalledAttemptAsFailedUnderItsOwnCode_whenTheLeaseIsStillLive()
            throws Exception {
        var beat = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        var limited = stallWindow(100, beat);
        stallingEngine(beat);

        limited.execute(RUN);

        // The heartbeat that saw the silence recorded the outcome while the lease was live:
        // one named, resumable state, not a silent restart and not a lease-expiry race.
        verify(ledger).stop(lease, CdsExportState.FAILED, "CDS_EXPORT_WORKER_STALLED");
        verify(ledger, never()).retireExpiredLease(any());
        verify(ledger, never()).stop(any(), eq(CdsExportState.REVIEW_REQUIRED), anyString());
        verify(ledger, never())
                .stop(lease, CdsExportState.FAILED, "CDS_EXPORT_ENGINE_DID_NOT_PUBLISH");
    }

    @Test
    void shouldEndAStalledAttemptUnderTheSameCode_whenItsLeaseHadAlreadyExpired()
            throws Exception {
        var beat = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        var limited = stallWindow(100, beat);
        // The lease expired before anything could be recorded: a live stop is refused.
        doThrow(new CdsExportOwnerFencedException())
                .when(ledger)
                .stop(lease, CdsExportState.FAILED, "CDS_EXPORT_WORKER_STALLED");
        when(ledger.retireExpiredLease(lease, CdsExportState.FAILED, "CDS_EXPORT_WORKER_STALLED"))
                .thenReturn(true);
        stallingEngine(beat);

        limited.execute(RUN);

        // Retired under the stall's own code, never as a plain lease expiry, and only once: the
        // returning engine thread finds the outcome recorded and changes nothing.
        verify(ledger, atLeast(1))
                .retireExpiredLease(lease, CdsExportState.FAILED, "CDS_EXPORT_WORKER_STALLED");
        verify(ledger, never()).retireExpiredLease(lease);
        verify(ledger, never()).stop(any(), eq(CdsExportState.REVIEW_REQUIRED), anyString());
    }

    @Test
    void shouldKeepTryingToRecordAStall_untilTheLedgerAcceptsIt() throws Exception {
        var beat = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        var limited = stallWindow(100, beat);
        // The ledger is unreachable on the first beat that sees the stall, then answers.
        doThrow(new IllegalStateException("LEDGER_UNAVAILABLE"))
                .doNothing()
                .when(ledger)
                .stop(lease, CdsExportState.FAILED, "CDS_EXPORT_WORKER_STALLED");
        stallingEngine(beat);

        limited.execute(RUN);

        verify(ledger, atLeast(2)).stop(lease, CdsExportState.FAILED, "CDS_EXPORT_WORKER_STALLED");
        verify(ledger, never()).retireExpiredLease(any());
    }

    @Test
    void shouldNeverPublishFromAStalledAttempt_evenIfItsEngineReturnsNormally() throws Exception {
        var beat = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        var limited = stallWindow(100, beat);
        doAnswer(
                        call -> {
                            CdsExportRunControl control = call.getArgument(1);
                            control.checkpoint();
                            Thread.sleep(300);
                            beat.getValue().run();
                            // The engine swallows nothing and reaches no further checkpoint: it
                            // simply returns, as an engine that had finished its work would.
                            return null;
                        })
                .when(engine)
                .execute(any(), any());

        limited.execute(RUN);

        verify(ledger).stop(lease, CdsExportState.FAILED, "CDS_EXPORT_WORKER_STALLED");
        // The closing checkpoint of a returned engine is fenced like any other.
        verify(ledger, never())
                .stop(lease, CdsExportState.FAILED, "CDS_EXPORT_ENGINE_DID_NOT_PUBLISH");
    }

    @Test
    void shouldObserveSourceCaptureControlSeparately_andRefuseAuthorizationInsideThatSnapshot()
            throws Exception {
        doAnswer(
                        call -> {
                            CdsExportRunControl control = call.getArgument(1);
                            assertThatThrownBy(control::captureCheckpoint)
                                    .hasMessage("CDS_EXPORT_READ_CAPTURE_TRANSACTION_REQUIRED");
                            TransactionSynchronizationManager.setActualTransactionActive(true);
                            TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
                            try {
                                assertThatThrownBy(control::checkpoint)
                                        .hasMessage("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
                                assertThatThrownBy(() -> control.authorizePatient(27))
                                        .hasMessage(
                                                "CDS_EXPORT_AUTHORIZATION_INSIDE_CAPTURE_REFUSED");
                                assertThatThrownBy(() -> control.authorizePharmacy(27))
                                        .hasMessage(
                                                "CDS_EXPORT_AUTHORIZATION_INSIDE_CAPTURE_REFUSED");
                                control.captureCheckpoint();
                            } finally {
                                TransactionSynchronizationManager.setActualTransactionActive(false);
                                TransactionSynchronizationManager.setCurrentTransactionReadOnly(
                                        false);
                            }
                            return null;
                        })
                .when(engine)
                .execute(any(), any());
        execution.execute(RUN);
        verify(ledger).captureCheckpoint(lease);
    }

    @Test
    void shouldPauseForARevokedScope_beforeAnyClinicalWork() throws Exception {
        doThrow(new SecurityException("not public clinical detail"))
                .when(access)
                .requireScope(any(), any());
        execution.execute(RUN);
        verify(engine, never()).execute(any(), any());
        verify(ledger).stop(lease, CdsExportState.REVIEW_REQUIRED, "CDS_EXPORT_ACCESS_CHANGED");
    }

    @Test
    void shouldKeepExpectedReviewAndIoFailuresDistinct_withoutExposingSourceExceptionText()
            throws Exception {
        doThrow(new CdsExportReviewException(CdsExportReviewException.Reason.MISSING_ATTACHMENT))
                .when(engine)
                .execute(any(), any());
        execution.execute(RUN);
        verify(ledger).stop(lease, CdsExportState.REVIEW_REQUIRED, "CDS_EXPORT_MISSING_ATTACHMENT");
        doThrow(new IOException("PHI-SYNTHETIC-PATIENT-NAME")).when(engine).execute(any(), any());
        execution.execute(RUN);
        verify(ledger).stop(lease, CdsExportState.FAILED, "CDS_EXPORT_IO_FAILED");
    }

    @Test
    void shouldFailWithTheStorageFailuresOwnCode_andNeverStopAnExportForReview() throws Exception {
        // The store's reserve, by its exact code.
        doThrow(new IOException("CDS_EXPORT_DISK_RESERVE")).when(engine).execute(any(), any());
        execution.execute(RUN);
        verify(ledger).stop(lease, CdsExportState.FAILED, "CDS_EXPORT_DISK_RESERVE");
        // The operating system's refusal, wherever it sits in the cause chain.
        doThrow(
                        new IOException(
                                "CDS_EXPORT_STAGED_WRITE_FAILED",
                                new java.nio.file.FileSystemException(
                                        "member.stage", null, "No space left on device")))
                .when(engine)
                .execute(any(), any());
        execution.execute(RUN);
        doThrow(new IOException("Disk quota exceeded")).when(engine).execute(any(), any());
        execution.execute(RUN);
        verify(ledger, times(2)).stop(lease, CdsExportState.FAILED, "CDS_EXPORT_DISK_FULL");
        // No size or count of a package is a review stop any more: the retired ceiling codes are
        // ordinary failures if anything still raised them, and text around a code changes nothing.
        for (String retired :
                new String[] {
                    "CDS_EXPORT_PACKAGE_VOLUME_LIMIT",
                    "CDS_EXPORT_PACKAGE_ENTRY_LIMIT",
                    "CDS_EXPORT_PACKAGE_INVENTORY_LIMIT",
                    "CDS_EXPORT_DELIVERY_BYTE_LIMIT",
                    "CDS_EXPORT_COHORT_LIMIT_REQUIRES_REVIEW",
                    "CDS_EXPORT_SUPPORTING_FILES_RESOURCE_LIMIT",
                    "CDS_EXPORT_DISK_RESERVE and more"
                }) {
            doThrow(new IOException(retired)).when(engine).execute(any(), any());
            execution.execute(RUN);
        }
        verify(ledger, times(7)).stop(lease, CdsExportState.FAILED, "CDS_EXPORT_IO_FAILED");
        verify(ledger, never()).stop(any(), eq(CdsExportState.REVIEW_REQUIRED), anyString());
    }

    @Test
    void shouldRelinquishAStoppedForegroundWait_withoutRequestingDurableCancellation()
            throws Exception {
        doAnswer(
                        call -> {
                            Thread.currentThread().interrupt();
                            ((CdsExportRunControl) call.getArgument(1)).checkpoint();
                            return null;
                        })
                .when(engine)
                .execute(any(), any());
        doAnswer(
                        call -> {
                            assertThat(Thread.currentThread().isInterrupted()).isFalse();
                            return null;
                        })
                .when(ledger)
                .release(lease);
        execution.execute(RUN);
        verify(ledger).release(lease);
        verify(ledger, never()).cancel(anyString(), anyString());
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void shouldRespectAnExpiredOrReplacedOwner_withoutOverwritingTheNewAttempt() throws Exception {
        doAnswer(
                        call -> {
                            ((CdsExportRunControl) call.getArgument(1)).checkpoint();
                            return null;
                        })
                .when(engine)
                .execute(any(), any());
        doThrow(new CdsExportOwnerFencedException()).when(ledger).checkpoint(lease);
        execution.execute(RUN);
        verify(ledger, never()).stop(any(), any(), anyString());
        assertThat(execution.supervisionStopped()).isFalse();
    }

    @Test
    void shouldStopLocalSupervision_whenTheMetadataCompletionOutcomeCannotBeEstablished() {
        doThrow(new IllegalStateException("private storage detail"))
                .when(ledger)
                .stop(lease, CdsExportState.FAILED, "CDS_EXPORT_ENGINE_DID_NOT_PUBLISH");
        assertThatThrownBy(() -> execution.execute(RUN))
                .hasMessage("CDS_EXPORT_CONTROL_OUTCOME_UNKNOWN");
        assertThat(execution.supervisionStopped()).isTrue();
        assertThatThrownBy(() -> execution.execute(RUN))
                .hasMessage("CDS_EXPORT_CONTROL_OUTCOME_UNKNOWN");
    }
}
