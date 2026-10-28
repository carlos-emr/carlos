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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsImportLedgerControlUnitTest {
    private final CdsImportRunDao runs=mock(CdsImportRunDao.class);
    private final CdsImportEventDao events=mock(CdsImportEventDao.class);
    private final CdsImportAdmissionDao admission=mock(CdsImportAdmissionDao.class);
    private final CdsImportPatientDao patients=mock(CdsImportPatientDao.class);
    private final CdsImportUnitDao unitRows=mock(CdsImportUnitDao.class);
    private final CdsImportDiagnosticDao diagnosticRows=mock(CdsImportDiagnosticDao.class);
    private final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    private CdsImportLedgerService ledger;
    private CdsImportRun run;
    @BeforeEach void setup() {
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ledger=new CdsImportLedgerService(runs,patients,unitRows,mock(CdsImportDependencyDao.class),mock(CdsImportLineageDao.class),mock(CdsImportCheckpointDao.class),diagnosticRows,admission,events,mock(CdsImportIdentityDao.class),manager);
        run=row("run","RUNNING");run.setOwnerEpoch(7);run.setCancelRequested(true);
        when(runs.lock("run")).thenReturn(run);when(runs.get("run")).thenReturn(run);
        when(events.pendingResumeEvent("run")).thenReturn(12L);
        when(diagnosticRows.summary(eq("run"),anyString(),anyString(),anyString())).thenReturn(new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportDiagnosticSummaryDto(0,0,0,0));
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
        var jobs=mock(CdsImportJobService.class);var fatal=new AssertionError("synthetic exhausted worker");when(jobs.loadContext("run")).thenThrow(fatal);
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
    @Test void summaryRetainsExactPendingUnknownInventoryAndHelperAccounting() {
        run.setDiscoveredFiles(-1);run.setPatientXmlFiles(-1);run.setSourceBytes(-1);
        when(patients.summaryOutcomes("run",17)).thenReturn(List.of(new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportPatientOutcomeDto("PLANNED",2,2),new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportPatientOutcomeDto("REFUSED",1,2)));
        when(unitRows.summaryCategories("run",129)).thenReturn(List.of(
                new Object[]{"ClinicalNotes",4L,1L,1L,1L},new Object[]{"ClinicalNotes_FIELDS",2L,1L,0L,1L}));
        var summary=ledger.summary("run");
        assertThat(summary).containsEntry("identifiedPatients",3L).containsEntry("targetCopiesPlanned",3L)
                .containsEntry("sourcePatientsIdentified",2L).containsEntry("sourcePatients",null)
                .containsEntry("expectedPatients",null).containsEntry("expectedUnits",null).containsEntry("sourceBytes",null)
                .containsEntry("plannedUnits",6L).containsEntry("committedUnits",2L).containsEntry("alreadyCompleteUnits",1L)
                .containsEntry("pendingUnits",2L).containsEntry("committedClinicalOccurrences",1L).containsEntry("alreadyCompleteClinicalOccurrences",1L);
        var categories=(List<?>)summary.get("categories");assertThat(categories).hasSize(1);
        var category=(java.util.Map<?,?>)categories.getFirst();
        assertThat(category.get("expected")).isEqualTo(4L);assertThat(category.get("pending")).isEqualTo(2L);
        assertThat(category.get("expectedHelperUnits")).isEqualTo(2L);assertThat(category.get("pendingHelperUnits")).isEqualTo(1L);
    }
    @Test void sealedDeclaredInventoryStaysDistinctFromEmptyObservedGroups() {
        run.setExecutionDigest("a".repeat(64));run.setExpectedPatients(17);run.setExpectedUnits(99);
        assertThat(ledger.summary("run")).containsEntry("inventoryState","SEALED").containsEntry("expectedPatients",17L)
                .containsEntry("expectedUnits",99L).containsEntry("plannedUnits",0L).containsEntry("identifiedPatients",0L)
                .containsEntry("committedUnits",0L).containsEntry("alreadyCompleteUnits",0L).containsEntry("pendingUnits",0L)
                .containsEntry("outcomes",java.util.Map.of()).containsEntry("categories",List.of());
    }
    @ParameterizedTest
    @ValueSource(ints={17,129})
    void aggregateCardinalityStillFailsClosed(int count) {
        if(count==17) when(patients.summaryOutcomes("run",17)).thenReturn(java.util.stream.IntStream.range(0,17)
                .mapToObj(i->new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportPatientOutcomeDto("OUTCOME"+i,1,1)).toList());
        else when(unitRows.summaryCategories("run",129)).thenReturn(java.util.stream.IntStream.range(0,129)
                .mapToObj(i->new Object[]{"CATEGORY"+i,1L,0L,0L,1L}).toList());
        assertThatThrownBy(()->ledger.summary("run")).hasMessage(count==17?"UNEXPECTED_OUTCOME_CARDINALITY":"UNEXPECTED_CATEGORY_CARDINALITY");
    }
    @Test void observationProjectionRequiresAnActualReadOnlyBoundary() {
        assertThatThrownBy(()->ledger.summaryInReadTransaction("run")).hasMessage("CDS_OBSERVATION_READ_TRANSACTION_REQUIRED");
        assertThatThrownBy(()->ledger.jobBindingInReadTransaction("run")).hasMessage("CDS_OBSERVATION_READ_TRANSACTION_REQUIRED");
        try {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
            assertThatThrownBy(()->ledger.summaryInReadTransaction("run")).hasMessage("CDS_OBSERVATION_READ_TRANSACTION_REQUIRED");
            assertThatThrownBy(()->ledger.jobBindingInReadTransaction("run")).hasMessage("CDS_OBSERVATION_READ_TRANSACTION_REQUIRED");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        }
        verifyNoInteractions(runs,patients,unitRows,manager);
    }
    @Test void observationProjectionUsesTheExistingReadBoundaryAndPrivateBinding() {
        run.setSnapshotLocation("/opaque/private-job-document");
        try {
            TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
            var binding=ledger.jobBindingInReadTransaction("run");
            assertThat(binding.job().id()).isEqualTo("run");assertThat(binding.documentLocation()).isEqualTo("/opaque/private-job-document");
            assertThat(binding.toString()).doesNotContain("private-job-document");
            assertThat(ledger.summaryInReadTransaction("run")).containsEntry("runId","run");
            assertThatThrownBy(()->ledger.summary("run")).hasMessage("AMBIENT_TRANSACTION_REFUSED");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        }
        verifyNoInteractions(manager);
    }
    @ParameterizedTest @ValueSource(ints={0,1,199,200,201,400})
    void shouldRetainEveryUnitDigestKey_whenFrozenPagesReachTheirBoundary(int size) {
        var source=new java.util.ArrayList<CdsImportUnit>(); var expected=io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys.digest();
        for (int i=0;i<size;i++) {
            var unit=new CdsImportUnit(); unit.setUnitKey(String.format("%064x",i)); unit.setLineageKey("lineage");
            unit.setSourceDigest("a".repeat(64)); unit.setCategory("ClinicalNotes"); unit.setOrdinal(i); unit.setSpoolLocation("/private/"+i); source.add(unit);
            for (String value:List.of(unit.getUnitKey(),unit.getLineageKey(),unit.getSourceDigest(),unit.getCategory(),Long.toString(unit.getOrdinal()),unit.getSpoolLocation(),"END_UNIT"))
                io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys.add(expected,value);
        }
        when(unitRows.page(eq("run"),eq("patient"),anyString(),eq(200))).thenAnswer(call->source.stream()
                .filter(unit->unit.getUnitKey().compareTo(call.getArgument(2))>0).limit(200).toList());
        String actual=org.springframework.test.util.ReflectionTestUtils.invokeMethod(ledger,"patientDigest","run","patient");
        assertThat(actual).isEqualTo(java.util.HexFormat.of().formatHex(expected.digest()));
        verify(unitRows,times(size==0?1:size/200+1)).page(eq("run"),eq("patient"),anyString(),eq(200));
    }
    @ParameterizedTest @ValueSource(ints={0,1,199,200,201,400})
    void shouldRetainEveryPatientDigest_whenSealedPagesReachTheirBoundary(int size) {
        var source=new java.util.ArrayList<CdsImportPatient>(); var expected=io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys.digest();
        for (int i=0;i<size;i++) {
            var patient=new CdsImportPatient(); patient.setId("id"+i); patient.setPatientKey(String.format("%064x",i));
            patient.setLineageKey("lineage"); patient.setSourceDigest("a".repeat(64)); patient.setExecutionDigest("b".repeat(64)); patient.setExpectedUnits(i); source.add(patient);
            for (String value:List.of(patient.getPatientKey(),patient.getLineageKey(),patient.getSourceDigest(),patient.getExecutionDigest(),Long.toString(patient.getExpectedUnits())))
                io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys.add(expected,value);
        }
        when(patients.page(eq("run"),anyString(),eq(200))).thenAnswer(call->source.stream()
                .filter(patient->patient.getPatientKey().compareTo(call.getArgument(1))>0).limit(200).toList());
        String actual=org.springframework.test.util.ReflectionTestUtils.invokeMethod(ledger,"executionDigest","run");
        assertThat(actual).isEqualTo(java.util.HexFormat.of().formatHex(expected.digest()));
        verify(patients,times(size==0?1:size/200+1)).page(eq("run"),anyString(),eq(200));
    }

    @Test void shouldMatchLegacyReadFilters_whenExecutingCurrentHqlProjections() {
        var factory=new org.hibernate.cfg.Configuration().addAnnotatedClass(CdsImportPatient.class)
                .addAnnotatedClass(CdsImportDiagnostic.class).addAnnotatedClass(CdsImportReview.class)
                .setProperty("jakarta.persistence.jdbc.url","jdbc:h2:mem:read-projections-"+java.util.UUID.randomUUID()+";MODE=MySQL")
                .setProperty("jakarta.persistence.jdbc.driver","org.h2.Driver")
                .setProperty("hibernate.generate_statistics","true")
                .setProperty("hibernate.hbm2ddl.auto","create-drop").buildSessionFactory();
        try (factory;var actual=factory.createEntityManager()) {
            actual.getTransaction().begin();
            CdsReadProjectionTestFixture.verify(actual,java.util.UUID.randomUUID().toString(),java.util.UUID.randomUUID().toString());
            actual.getTransaction().rollback();
        }
    }
    @ParameterizedTest @ValueSource(strings={"COMPLETE","DRY_RUN_COMPLETE","RUNNING"})
    void shouldPreserveReleasePrecedence_whenCurrentDiagnosticFlagsChange(String state) {
        run.setState(state);
        var values=List.of(new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportDiagnosticSummaryDto(7,1,1,1),
                new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportDiagnosticSummaryDto(6,0,1,1),
                new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportDiagnosticSummaryDto(5,0,0,1),
                new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportDiagnosticSummaryDto(4,0,0,0));
        var expected=List.of("REQUIRES_RECONCILIATION","RELEASE_DEFERRED","RELEASE_PENDING","RELEASED");
        for (int i=0;i<values.size();i++) {
            when(diagnosticRows.summary(eq("run"),anyString(),anyString(),anyString())).thenReturn(values.get(i));
            var summary=ledger.summary("run");
            assertThat(summary).containsEntry("unresolvedReviewItems",values.get(i).unresolved())
                    .containsEntry("maintenanceReleaseState","RUNNING".equals(state)?"NOT_APPLICABLE":expected.get(i));
        }
        verify(diagnosticRows,times(4)).summary(eq("run"),anyString(),anyString(),anyString());
        verify(diagnosticRows,never()).unresolvedOperationalCode(anyString(),anyString(),anyInt());
    }

    private static CdsImportRun row(String id,String state) {
        var row=new CdsImportRun();row.setId(id);row.setState(state);row.setOperator("fixture");row.setProviderNo("999998");row.setUpdatedAt(Instant.EPOCH);row.setCreatedAt(Instant.EPOCH);row.setReportState("DELIVERED");row.setCleanupState("COMPLETE");return row;
    }
}
