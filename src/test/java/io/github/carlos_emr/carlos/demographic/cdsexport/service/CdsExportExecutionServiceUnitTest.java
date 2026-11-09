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
