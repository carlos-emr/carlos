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
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportSubmissionConflictException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Tag("unit")
@Tag("cds-export")
class CdsExportServiceUnitTest {
    private static final String RUN = "22222222-2222-4222-8222-222222222222", HASH = "a".repeat(64);
    private CdsExportLedgerService ledger;
    private CdsExportAccessService access;
    private CdsExportExecutionService execution;
    private CdsExportEngine engine;
    private CdsExportService service;
    private CdsExportCommand command;
    private CdsExportActorDto actor;
    private CdsExportJobDocumentDto document;
    private CdsExportJobDto job;
    private LoggedInInfo viewer;

    @BeforeEach
    void configureExplicitTestEngine() throws Exception {
        ledger = mock(CdsExportLedgerService.class);
        access = mock(CdsExportAccessService.class);
        execution = mock(CdsExportExecutionService.class);
        engine = mock(CdsExportEngine.class);
        viewer = new LoggedInInfo();
        command =
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
        actor = new CdsExportActorDto("operator", "100", null, null);
        document =
                new CdsExportJobDocumentDto(
                        1, actor, command, HASH, "instance", "b".repeat(64), "c".repeat(64));
        job =
                new CdsExportJobDto(
                        RUN,
                        CdsExportState.ACCEPTED,
                        0,
                        null,
                        0,
                        0,
                        false,
                        false,
                        0,
                        null,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        false);
        when(access.webActor(viewer, command)).thenReturn(actor);
        when(access.delegatedActor("operator", command)).thenReturn(actor);
        when(access.delegatedSession("operator", "100", null)).thenReturn(actor);
        when(access.operatorSession(viewer)).thenReturn(actor);
        when(ledger.document(RUN)).thenReturn(document);
        when(ledger.owner(RUN))
                .thenReturn(new CdsExportJobOwnerDto(actor.operatorKey(), "100", null));
        when(ledger.accept(any())).thenReturn(job);
        when(ledger.status(RUN)).thenReturn(job);
        when(engine.compatibilityDigest()).thenReturn(HASH);
        when(engine.qualifiedProfiles()).thenReturn(Set.of(CdsLogicalProfile.ONTARIO_MD_5_2));
        when(engine.bind(actor, command)).thenReturn(document);
        service = configured(true, true, List.of(engine));
    }

    @AfterEach
    void retireOwnedWorkers() {
        if (service != null) service.close();
    }

    @Test
    void shouldAcceptAndReturnAnUnknownTotal_withoutStartingAnImplicitCliWorker() throws Exception {
        service.start();
        assertThat(service.acceptDelegated("operator", command)).isEqualTo(job);
        verify(ledger).accept(document);
        verify(execution, never()).execute(anyString());
        assertThat(job.totalPatients()).isNull();
        assertThat(job.artifactAvailable()).isFalse();
    }

    @Test
    @Timeout(10)
    void shouldReturnTheDurableBrowserHandle_whileSourceWorkRemainsBlocked() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), retire = new CountDownLatch(1);
        doAnswer(
                        call -> {
                            entered.countDown();
                            try {
                                retire.await();
                            } catch (InterruptedException stopped) {
                                Thread.currentThread().interrupt();
                            }
                            return true;
                        })
                .when(execution)
                .execute(RUN);
        try {
            service.start();
            assertThat(service.submit(viewer, command).id()).isEqualTo(RUN);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(retire.getCount()).isEqualTo(1);
        } finally {
            retire.countDown();
        }
    }

    @Test
    void shouldRecoverOriginalAcceptance_withoutRebindingRotatedServerPolicy() throws Exception {
        when(ledger.findSubmission(actor.operatorKey(), command.submissionKey())).thenReturn(job);
        assertThat(service.acceptDelegated("operator", command)).isEqualTo(job);
        verify(engine, never()).bind(any(), any());
        verify(ledger, never()).accept(any());
    }

    @Test
    void shouldRecoverAnAlreadyAcceptedHandle_whenNewAdmissionHasSinceBeenDisabled()
            throws Exception {
        service.close();
        service = configured(false, false, List.of());
        when(ledger.findSubmission(actor.operatorKey(), command.submissionKey())).thenReturn(job);
        assertThat(service.acceptDelegated("operator", command)).isEqualTo(job);
        verify(engine, never()).bind(any(), any());
        verify(ledger, never()).accept(any());
    }

    @Test
    void shouldRefuseChangedIntent_forTheSameSubmissionKey() {
        when(ledger.findSubmission(actor.operatorKey(), command.submissionKey())).thenReturn(job);
        var changed =
                new CdsExportCommand(
                        "test",
                        "100",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(28),
                        EnumSet.allOf(CdsCategory.class),
                        "recipient",
                        "audit");
        when(access.delegatedActor("operator", changed)).thenReturn(actor);
        assertThatThrownBy(() -> service.acceptDelegated("operator", changed))
                .hasMessage("CDS_EXPORT_SUBMISSION_CONFLICT");
    }

    @Test
    void shouldRecoverConcurrentUnchangedIntent_withoutReplacingItsOriginalBindings()
            throws Exception {
        when(ledger.accept(document)).thenThrow(new CdsExportSubmissionConflictException());
        when(ledger.submission(actor.operatorKey(), command.submissionKey())).thenReturn(job);
        assertThat(service.acceptDelegated("operator", command)).isEqualTo(job);
    }

    @Test
    void shouldRejectAnEngineThatSubstitutesActorOrAcceptedIntent_forServiceContract() throws Exception {
        when(engine.bind(actor, command))
                .thenReturn(
                        new CdsExportJobDocumentDto(
                                1,
                                new CdsExportActorDto("another", "100", null, null),
                                command,
                                HASH,
                                "instance",
                                "b".repeat(64),
                                "c".repeat(64)));
        assertThatThrownBy(() -> service.acceptDelegated("operator", command))
                .hasMessage("CDS_EXPORT_JOB_BINDING_INVALID");
        verify(ledger, never()).accept(any());
    }

    @Test
    void shouldKeepMetadataAndCancellationAuthorized_whenNewExportAdmissionIsDisabled() {
        service.close();
        service = configured(false, false, List.of());
        assertThat(service.observeDelegated("operator", "100", RUN)).isEqualTo(job);
        service.cancelDelegated("operator", "100", RUN);
        verify(access, times(2)).requireObserve(eq(actor), any(CdsExportJobOwnerDto.class));
        verify(ledger).cancel(RUN, actor.operatorKey());
        assertThatThrownBy(() -> service.acceptDelegated("operator", command))
                .hasMessage("CDS_EXPORT_ADMISSION_DISABLED");
    }

    @Test
    void shouldRequireExactCliProviderScope_andCurrentMetadataAuthority() {
        doThrow(new SecurityException("unapproved current scope"))
                .when(access)
                .delegatedSession("operator", "200", null);
        assertThatThrownBy(() -> service.observeDelegated("operator", "200", RUN))
                .isInstanceOf(SecurityException.class);
        doThrow(new SecurityException("private denial detail"))
                .when(access)
                .requireObserve(eq(actor), any(CdsExportJobOwnerDto.class));
        assertThatThrownBy(() -> service.cancelDelegated("operator", "100", RUN))
                .isInstanceOf(SecurityException.class);
        verify(ledger, never()).cancel(anyString(), anyString());
    }

    @Test
    @Timeout(10)
    void shouldRediscoverAcceptedWork_afterAWebNodeRestarts() throws Exception {
        CountDownLatch recovered = new CountDownLatch(1);
        when(ledger.pending(16)).thenReturn(List.of(RUN));
        doAnswer(
                        call -> {
                            recovered.countDown();
                            return true;
                        })
                .when(execution)
                .execute(RUN);
        service.start();
        assertThat(recovered.await(5, TimeUnit.SECONDS)).isTrue();
        verify(ledger, never()).accept(any());
    }

    @Test
    void shouldLeaveAnAcceptedJobWaiting_whenTheLocalDispatcherIsDisabled() throws Exception {
        service.close();
        service = configured(true, false, List.of(engine));
        service.start();
        assertThat(service.submit(viewer, command)).isEqualTo(job);
        assertThat(service.dispatcherRunning()).isFalse();
        verify(execution, never()).execute(anyString());
    }

    private CdsExportService configured(
            boolean enabled, boolean worker, List<CdsExportEngine> installed) {
        Properties policy = new Properties();
        policy.setProperty("cds.export.enabled", Boolean.toString(enabled));
        policy.setProperty("cds.export.worker.enabled", Boolean.toString(worker));
        return new CdsExportService(
                ledger,
                access,
                new CdsExportConfigurationService(policy),
                new CdsExportEngineRegistry(installed),
                execution);
    }
}
