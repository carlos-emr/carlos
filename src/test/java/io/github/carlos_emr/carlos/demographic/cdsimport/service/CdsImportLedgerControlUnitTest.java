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

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsImportLedgerControlUnitTest {
    private final CdsImportRunDao runs=mock(CdsImportRunDao.class);
    private final CdsImportEventDao events=mock(CdsImportEventDao.class);
    private final CdsImportAdmissionDao admission=mock(CdsImportAdmissionDao.class);
    private CdsImportLedgerService ledger;
    private CdsImportRun run;
    @BeforeEach void setup() {
        var manager=mock(PlatformTransactionManager.class);when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ledger=new CdsImportLedgerService(runs,mock(CdsImportPatientDao.class),mock(CdsImportUnitDao.class),mock(CdsImportDependencyDao.class),mock(CdsImportLineageDao.class),mock(CdsImportCheckpointDao.class),mock(CdsImportDiagnosticDao.class),admission,events,mock(CdsImportIdentityDao.class),manager);
        run=row("run","RUNNING");run.setOwnerEpoch(7);run.setCancelRequested(true);
        when(runs.lock("run")).thenReturn(run);when(runs.get("run")).thenReturn(run);
        when(events.pendingResumeEvent("run")).thenReturn(12L);
    }
    @ParameterizedTest
    @ValueSource(strings={"ACCEPTED","CLI_ACCEPTED","RUNNING","FINALIZING","DISCOVERING","VALIDATING","PREFLIGHT","PREFLIGHTING","SEALING","PLANNING","READY","REPORTING","RECONCILING","PAUSED"})
    void activeResumeRecordsIntentWithoutDeclaringTheOldProcessInactive(String state) {
        run.setState(state);ledger.requestResume("run");
        assertThat(run.getState()).isEqualTo(state);assertThat(run.isCancelRequested()).isTrue();assertThat(run.getOwnerEpoch()).isEqualTo(7);
        assertThat(ledger.summary("run")).containsEntry("resumeRequested",true).containsEntry("operationTerminal",false);
        assertThat(ledger.pendingResumeEvent("run")).isEqualTo(12);
        ledger.cancel("run");assertThat(ledger.summary("run")).containsEntry("resumeRequested",false);assertThat(ledger.pendingResumeEvent("run")).isZero();
    }
    @Test void reviewOnlyResumeAuditDoesNotAuthorizeClinicalRecovery() {
        assertThat(ledger.pendingResumeEvent("run")).isZero();
        verify(events,never()).pendingResumeEvent(anyString());
    }
    @Test void definitiveRefusalCannotRetireANewerResumeOrCancellation() {
        ledger.requestResume("run");when(events.pendingResumeEvent("run")).thenReturn(14L);
        ledger.refuseResumeIntent("run",12,"IMPORT_RULESET_OR_OPTIONS_INCOMPATIBLE");assertThat(run.isResumeRequested()).isTrue();
        ledger.refuseResumeIntent("run",14,"ACCESS_REVOKED");assertThat(run.isResumeRequested()).isFalse();assertThat(run.isCancelRequested()).isTrue();assertThat(run.getOwnerEpoch()).isEqualTo(7);
        ledger.requestResume("run");ledger.cancel("run");ledger.refuseResumeIntent("run",14,"ACCESS_REVOKED");assertThat(run.isCancelRequested()).isTrue();assertThat(run.isResumeRequested()).isFalse();
    }
    @Test void retainedAdmissionOwnerCannotStarveBehindEarlierAcceptedJobs() {
        run.setState("INCOMPLETE");run.setResumeRequested(true);run.setReportState("DELIVERED");
        var gate=new CdsImportAdmission();gate.setId("global");gate.setOwnerRunId("run");when(admission.get("global")).thenReturn(gate);
        when(runs.pending("",2)).thenReturn(List.of(row("a","ACCEPTED"),row("b","ACCEPTED")));
        assertThat(ledger.pending("",2)).extracting(value->value.id()).containsExactly("run","a");
    }
    @Test void laterCancellationAndNewReviewRefuseAStaleQueuedResumeUnderTheRunLock() {
        run.setState("RESUME_REQUESTED");ledger.cancel("run");assertThat(run.getState()).isEqualTo("CANCELLED_BEFORE_WRITE");
        // A newly accepted review clears its old cancellation, but does not grant clinical execution.
        run.setCancelRequested(false);when(runs.reviewBlocksExecution("run")).thenReturn(true);
        var gate=new CdsImportAdmission();gate.setId("global");gate.setOwnerRunId("run");when(admission.lock("global")).thenReturn(gate);
        var coordinator=mock(CdsImportCoordinatorService.Owner.class);var window=mock(CdsImportMaintenanceService.Owner.class);
        when(window.read(any())).thenAnswer(call->((java.util.function.Supplier<?>)call.getArgument(0)).get());
        assertThatThrownBy(()->ledger.claimPreparation("run",coordinator,window,true)).hasMessage("IMPORT_REVIEW_PENDING");
        assertThat(run.getOwnerEpoch()).isEqualTo(7);assertThat(run.isResumeRequested()).isFalse();
        verify(events,never()).pendingResumeEvent(anyString());
    }
    @Test void fatalResumeIsRetiredOnlyByHealthySupervisionWithItsExactObservedControl() throws Exception {
        var state=mock(CdsImportLedgerService.class);var status=mock(io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto.class);
        when(state.status("run")).thenReturn(status);when(status.ownerEpoch()).thenReturn(7L);when(state.pendingResumeEvent("run")).thenReturn(12L);
        var jobs=mock(CdsImportJobService.class);var fatal=new AssertionError("synthetic exhausted worker");when(jobs.load("run")).thenThrow(fatal);
        var coordinator=mock(CdsImportCoordinatorService.class);when(coordinator.acquire()).thenReturn(mock(CdsImportCoordinatorService.Owner.class));
        var engine=new CdsImportExecutionService(state,mock(CdsImportUnitService.class),jobs,mock(CdsImportPlanningService.class),mock(CdsImportNativeService.class),mock(CdsImportAccessService.class),mock(CdsImportConfigurationService.class),mock(CdsImportMaintenanceService.class),mock(CdsImportResultService.class),coordinator,mock(CdsImportFileVerificationService.class),mock(CdsImportTargetSafetyService.class),mock(CdsImportFailureEvidenceService.class),new CdsFieldParser());
        assertThatThrownBy(()->engine.execute("run")).isSameAs(fatal);
        verify(state,never()).refuseResumeIntent(anyString(),anyLong(),anyString());
        engine.superviseFatal("run");engine.superviseFatal("run");
        verify(state,times(1)).refuseResumeIntent("run",12L,"WORKER_FATAL");
    }
    @ParameterizedTest
    @ValueSource(strings={"PLANNING","RUNNING"})
    void laterCancelStillWinsBetweenPreparationAndTheActualPhaseCommit(String phase) {
        var gate=new CdsImportAdmission();gate.setId("global");gate.setOwnerRunId("run");when(admission.lock("global")).thenReturn(gate);
        var coordinator=mock(CdsImportCoordinatorService.Owner.class);var window=mock(CdsImportMaintenanceService.Owner.class);
        when(window.read(any())).thenAnswer(call->((java.util.function.Supplier<?>)call.getArgument(0)).get());
        ledger.requestResume("run");long prepared=ledger.claimPreparation("run",coordinator,window,true);
        assertThat(run.isResumeRequested()).isTrue();assertThat(ledger.pendingResumeEvent("run")).isEqualTo(12);
        if("RUNNING".equals(phase))run.setExecutionDigest("a".repeat(64));
        ledger.cancel("run");
        assertThatThrownBy(()->claimPhase(phase,coordinator,window)).hasMessage("IMPORT_CANCELLED");
        assertThat(run.getOwnerEpoch()).isEqualTo(prepared);assertThat(run.isCancelRequested()).isTrue();assertThat(run.isResumeRequested()).isFalse();
    }
    @ParameterizedTest
    @ValueSource(strings={"PLANNING","RUNNING"})
    void newReviewOrDraftDefersThePhaseWithoutConsumingANewerResume(String phase) {
        var gate=new CdsImportAdmission();gate.setId("global");gate.setOwnerRunId("run");when(admission.lock("global")).thenReturn(gate);
        var coordinator=mock(CdsImportCoordinatorService.Owner.class);var window=mock(CdsImportMaintenanceService.Owner.class);
        when(window.read(any())).thenAnswer(call->((java.util.function.Supplier<?>)call.getArgument(0)).get());
        ledger.requestResume("run");long prepared=ledger.claimPreparation("run",coordinator,window,true);
        if("RUNNING".equals(phase))run.setExecutionDigest("a".repeat(64));
        when(events.pendingResumeEvent("run")).thenReturn(14L);when(runs.reviewBlocksExecution("run")).thenReturn(true);
        assertThatThrownBy(()->claimPhase(phase,coordinator,window)).hasMessage("IMPORT_REVIEW_PENDING");
        assertThat(run.getOwnerEpoch()).isEqualTo(prepared);assertThat(run.isResumeRequested()).isTrue();assertThat(ledger.pendingResumeEvent("run")).isEqualTo(14);
    }
    private long claimPhase(String phase,CdsImportCoordinatorService.Owner coordinator,CdsImportMaintenanceService.Owner window) {
        return "PLANNING".equals(phase)?ledger.claimPlanning("run",coordinator,window):ledger.claimExecution("run",coordinator,window);
    }
    private static CdsImportRun row(String id,String state) {
        var row=new CdsImportRun();row.setId(id);row.setState(state);row.setOperator("fixture");row.setProviderNo("999998");row.setUpdatedAt(Instant.EPOCH);row.setCreatedAt(Instant.EPOCH);row.setReportState("DELIVERED");row.setCleanupState("COMPLETE");return row;
    }
}
