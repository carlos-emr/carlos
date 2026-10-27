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

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual execution entry points; the fixture models only durable unit acknowledgements, not clinical conformance. */
@Tag("unit")
class CdsImportAttemptBudgetUnitTest {
    @Test void combinedUnitTimeStopsTheAttempt_andSameAcceptedResumeDoesNotReplayCommittedUnits() throws Exception {
        Fixture f=new Fixture();
        try(var keys=f.keys()) {
            f.engine.execute("run");
            assertEquals(2,f.committed());assertEquals("INCOMPLETE",f.state.get());
            assertTrue(f.codes.contains("IMPORT_ATTEMPT_TIMEOUT"));
            verify(f.window,never()).allowReopen();assertEquals("EXPECTED",f.units.get(2).state());
            // A long stopped/review interval is not charged to the next explicit active attempt.
            f.clock.addAndGet(TimeUnit.DAYS.toNanos(2));when(f.ledger.pendingResumeEvent("run")).thenReturn(1L);
            f.engine.execute("run",true);
            assertEquals(3,f.committed());assertEquals("COMPLETE",f.state.get());
            for(int index=0;index<f.units.size();index++)verify(f.records,times(index==2?2:1)).write(eq("run"),anyLong(),eq(f.units.get(index)),any(),any(),any(),any(),any());
            verify(f.window,times(1)).allowReopen();verify(f.jobs,times(2)).load("run");
        }
    }

    @Test void verificationProbeStopsBeforeAnyClinicalWrite() throws Exception {
        Fixture f=new Fixture();
        doAnswer(call->{f.clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(100));f.fields.checkpoint();return null;})
                .when(f.verification).verify(eq("run"),eq(1L),any(CdsImportExecutionControl.class));
        try(var keys=f.keys()){f.engine.execute("run");}
        assertEquals(0,f.committed());assertTrue(f.codes.contains("IMPORT_ATTEMPT_TIMEOUT"));
        verify(f.records,never()).write(anyString(),anyLong(),any(),any(),any(),any(),any(),any());
        verify(f.window,never()).allowReopen();
    }

    @Test void snapshotAndPlanningShareTheSameAttemptBudget() throws Exception {
        Fixture f=new Fixture();when(f.ledger.isSealed("run")).thenReturn(false);
        when(f.jobs.snapshot(eq("run"),eq(1L),eq(f.document),any(CdsImportExecutionControl.class))).thenAnswer(call->{f.clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(60));call.<CdsImportExecutionControl>getArgument(3).check(false);return mock(CdsSnapshotDto.class);});
        doAnswer(call->{f.clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(50));call.<CdsImportExecutionControl>getArgument(4).check(false);return null;})
                .when(f.planning).plan(eq("run"),eq(1L),eq(f.document),any(),any(CdsImportExecutionControl.class));
        try(var keys=f.keys()){f.engine.execute("run");}
        verify(f.ledger).endPreflight(eq("run"),eq(1L),eq("FAILED_BEFORE_WRITE"),eq("IMPORT_ATTEMPT_TIMEOUT"),any());
        assertEquals(0,f.committed());verify(f.window,never()).allowReopen();
    }

    @Test void expiredClockNeverReclassifiesAnUnknownCommitAsATimeout() throws Exception {
        Fixture f=new Fixture();
        when(f.records.write(anyString(),anyLong(),any(),any(),any(),any(),any(),any())).thenAnswer(call->{
            f.clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(200));throw new CdsImportException("UNIT_COMMIT_OUTCOME_UNKNOWN",true,null);
        });
        try(var keys=f.keys()){f.engine.execute("run");}
        assertTrue(f.codes.contains("UNIT_COMMIT_OUTCOME_UNKNOWN"));assertFalse(f.codes.contains("IMPORT_ATTEMPT_TIMEOUT"));
        verify(f.ledger).markIncomplete("run",1L,"patient",true,false,true);
        verify(f.window,never()).allowReopen();
    }

    @Test void malformedControlAndAmbiguousSecondaryProbeProduceReportedNoncleanOutcomesWithProtectedCause(
            @org.junit.jupiter.api.io.TempDir Path root) throws Exception {
        for(int scenario=0;scenario<3;scenario++) {
            boolean ambiguous=scenario==1;
            String primaryState=scenario==2?"08S01":"HY000";
            Path evidenceRoot=java.nio.file.Files.createDirectory(root.resolve("scenario-"+scenario));
            Fixture f=new Fixture(evidenceRoot);
            var dao=mock(io.github.carlos_emr.carlos.commn.dao.CdsImportCoordinatorDao.class);
            var lease=mock(io.github.carlos_emr.carlos.commn.dao.CdsImportCoordinatorDao.Lease.class);
            when(dao.acquire()).thenReturn(lease);
            var secondary=new java.sql.SQLException("another-private-detail","HYT00",72);
            if(ambiguous)when(lease.physicalOwned()).thenReturn(true).thenThrow(secondary);
            else when(lease.physicalOwned()).thenReturn(true);
            var sqlFailure=new java.sql.SQLException("synthetic-secret-or-source-must-not-escape",primaryState,71);
            when(lease.control("run")).thenThrow(sqlFailure);
            var owner=new CdsImportCoordinatorService(dao).acquire();
            when(f.coordinator.acquire()).thenReturn(owner);
            try(var keys=f.keys()){f.engine.execute("run");}
            assertEquals("INCOMPLETE",f.state.get());assertEquals(0,f.committed());
            assertTrue(f.codes.contains("IMPORT_CONTROL_UNAVAILABLE"));
            verify(f.ledger).operational("run",1L,null,null,"IMPORT_CONTROL_UNAVAILABLE");
            verify(f.reports).generate(eq("run"),eq(1L),any(CdsImportCoordinatorService.Owner.class));verify(f.window,never()).allowReopen();
            verify(f.records,never()).write(anyString(),anyLong(),any(),any(),any(),any(),any(),any());
            List<Path> evidence;
            try(var files=java.nio.file.Files.list(evidenceRoot.resolve("diagnostics"))){evidence=files.toList();}
            assertEquals(1,evidence.size());
            var text=java.nio.file.Files.readString(evidence.getFirst());
            assertTrue(text.contains("java.sql.SQLException") && text.contains(primaryState));
            assertFalse(text.contains(sqlFailure.getMessage()) || text.contains(secondary.getMessage()));
            assertEquals(0,((int)java.nio.file.Files.getAttribute(evidence.getFirst(),"unix:mode"))&0077);
            if(ambiguous)assertArrayEquals(new Throwable[]{secondary},sqlFailure.getSuppressed());
            verify(lease,times(1)).control("run");verify(lease,times(2)).physicalOwned();
        }
    }

    @Test void reportControlFailureRetainsItsProtectedCause_withoutChangingCommittedClinicalResults(
            @org.junit.jupiter.api.io.TempDir Path root) throws Exception {
        Fixture f=new Fixture(root);
        when(f.records.write(anyString(),anyLong(),any(),any(),any(),any(),any(),any()))
                .thenReturn(new CdsImportReceiptDto("{}","digest"));
        var sql=new java.sql.SQLException("private-report-control-detail","HY000",71);
        var failure=new CdsImportException("IMPORT_CONTROL_UNAVAILABLE",false,sql);
        doThrow(failure).when(f.reports).generate(eq("run"),eq(1L),any(CdsImportCoordinatorService.Owner.class));
        try(var keys=f.keys()){f.engine.execute("run");}
        assertEquals(3,f.committed());assertEquals("COMPLETE",f.state.get());
        var detail=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(f.ledger).reportingFailureDiagnostic(eq("run"),eq(1L),eq("IMPORT_CONTROL_UNAVAILABLE"),detail.capture());
        String evidence=java.nio.file.Files.readString(Path.of(detail.getValue()));
        assertTrue(evidence.contains("java.sql.SQLException") && evidence.contains("HY000"));
        assertFalse(evidence.contains(sql.getMessage()));
        assertEquals(0,((int)java.nio.file.Files.getAttribute(Path.of(detail.getValue()),"unix:mode"))&0077);
        verify(f.ledger,never()).markIncomplete(anyString(),anyLong(),any(),anyBoolean(),anyBoolean(),anyBoolean());
    }

    @Test void unavailableReportingEvidenceAndDiagnosticsNeverReplaceTheOriginalClinicalOutcome() throws Exception {
        Fixture f=new Fixture();
        when(f.records.write(anyString(),anyLong(),any(),any(),any(),any(),any(),any()))
                .thenReturn(new CdsImportReceiptDto("{}","digest"));
        var primary=new CdsImportException("IMPORT_CONTROL_UNAVAILABLE");
        var evidenceFailure=new IllegalStateException("private-evidence-detail");
        var diagnosticFailure=new IllegalStateException("private-diagnostic-detail");
        doThrow(primary).when(f.reports).generate(eq("run"),eq(1L),any(CdsImportCoordinatorService.Owner.class));
        when(f.evidence.write("run",1L,primary)).thenThrow(evidenceFailure);
        doThrow(diagnosticFailure).when(f.ledger).reportingFailureDiagnostic("run",1L,"IMPORT_CONTROL_UNAVAILABLE",null);
        try(var keys=f.keys()){f.engine.execute("run");}
        assertEquals(3,f.committed());assertEquals("COMPLETE",f.state.get());
        assertArrayEquals(new Throwable[]{evidenceFailure,diagnosticFailure},primary.getSuppressed());
        verify(f.ledger,never()).markIncomplete(anyString(),anyLong(),any(),anyBoolean(),anyBoolean(),anyBoolean());
    }

    @Test void fatalSupervisionAlsoRetainsTheSpecificLegacyReportFailure_withoutClinicalReplay() throws Exception {
        Fixture f=new Fixture();var failure=new CdsImportException("IMPORT_CONTROL_UNAVAILABLE");
        doThrow(failure).when(f.reports).generate("run",1L);
        when(f.evidence.write("run",1L,failure)).thenReturn("/opaque/protected-report-failure.json");
        f.engine.superviseFatal("run");
        verify(f.ledger).workerStopped("run",1L);
        verify(f.ledger).reportingFailureDiagnostic("run",1L,"IMPORT_CONTROL_UNAVAILABLE","/opaque/protected-report-failure.json");
        verify(f.records,never()).write(anyString(),anyLong(),any(),any(),any(),any(),any(),any());
        verify(f.coordinator,times(1)).acquire();
    }

    @Test void aSavedEvidenceFileWithoutADurableDiagnosticStillLogsSafeRunCorrelation() throws Exception {
        Fixture f=new Fixture();
        when(f.records.write(anyString(),anyLong(),any(),any(),any(),any(),any(),any()))
                .thenReturn(new CdsImportReceiptDto("{}","digest"));
        var primary=new CdsImportException("IMPORT_CONTROL_UNAVAILABLE",false,
                new java.sql.SQLException("private-sql-message","HY000",71));
        String detail="/opaque/private-report-detail.json";
        when(f.evidence.write("run",1L,primary)).thenReturn(detail);
        doThrow(primary).when(f.reports).generate(eq("run"),eq(1L),any(CdsImportCoordinatorService.Owner.class));
        var diagnosticFailure=new IllegalStateException("private-diagnostic-message");
        doThrow(diagnosticFailure).when(f.ledger).reportingFailureDiagnostic("run",1L,"IMPORT_CONTROL_UNAVAILABLE",detail);
        try(var capture=io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(CdsImportExecutionService.class);
                var keys=f.keys()) {
            f.engine.execute("run");
            assertEquals(1,capture.events().size());
            var event=capture.events().getFirst();
            assertNull(event.getThrown());
            String message=event.getMessage().getFormattedMessage();
            assertTrue(message.contains("run run epoch 1") && message.contains("IMPORT_REPORT_FAILURE_EVIDENCE_UNAVAILABLE"));
            assertFalse(message.contains(detail) || message.contains("private-sql-message") || message.contains(diagnosticFailure.getMessage()));
        }
        assertEquals(3,f.committed());assertEquals("COMPLETE",f.state.get());
        assertArrayEquals(new Throwable[]{diagnosticFailure},primary.getSuppressed());
    }

    private static final class Fixture {
        final AtomicLong clock=new AtomicLong();
        final AtomicReference<String> state=new AtomicReference<>("READY"),patientState=new AtomicReference<>("PLANNED");
        final List<String> codes=new ArrayList<>();final List<CdsImportUnitDto> units=new ArrayList<>();
        final List<AtomicReference<String>> unitStates=new ArrayList<>();
        final CdsImportLedgerService ledger=mock(CdsImportLedgerService.class);
        final CdsImportUnitService unitService=mock(CdsImportUnitService.class);
        final CdsImportJobService jobs=mock(CdsImportJobService.class);
        final CdsImportPlanningService planning=mock(CdsImportPlanningService.class);
        final CdsImportNativeService records=mock(CdsImportNativeService.class);
        final CdsImportMaintenanceService.Owner window=mock(CdsImportMaintenanceService.Owner.class);
        final CdsImportFileVerificationService verification=mock(CdsImportFileVerificationService.class);
        final CdsFieldParser fields=new CdsFieldParser();
        final CdsImportCoordinatorService coordinator=mock(CdsImportCoordinatorService.class);
        final CdsImportResultService reports=mock(CdsImportResultService.class);
        final CdsImportFailureEvidenceService evidence;
        final CdsImportCommand command=new CdsImportCommand(Path.of("/opaque/source.xml"),"submission","1",null,null,"source","omd-5.2",false,false,false,0,null,false);
        final CdsImportJobDocumentDto document=new CdsImportJobDocumentDto(command,mock(CdsImportActorDto.class),null,null,Path.of("/opaque"));
        final CdsImportExecutionService engine;
        Fixture() throws Exception { this(null); }
        Fixture(Path evidenceRoot) throws Exception {
            var configuration=mock(CdsImportConfigurationService.class);var limits=mock(CdsInputLimitsDto.class);
            when(configuration.limits()).thenReturn(limits);when(limits.maxDurationMillis()).thenReturn(100L);
            var job=mock(CdsImportJobDto.class);when(ledger.status("run")).thenReturn(job);
            when(job.state()).thenAnswer(call->state.get());when(job.ownerEpoch()).thenReturn(1L);when(job.rulesetDigest()).thenReturn("rules");when(job.optionsDigest()).thenReturn("options");
            when(jobs.load("run")).thenReturn(document);when(ledger.isSealed("run")).thenReturn(true);when(ledger.ownsAdmission("run",1L)).thenReturn(true);
            var coordinatorOwner=mock(CdsImportCoordinatorService.Owner.class);
            when(coordinatorOwner.control("run")).thenReturn(new CdsImportControlDto(true,true,1L,false,true));
            when(coordinator.acquire()).thenReturn(coordinatorOwner);
            var maintenance=mock(CdsImportMaintenanceService.class);when(maintenance.acquire()).thenReturn(window);when(window.releaseVerified()).thenReturn(true);
            when(window.read(any())).thenAnswer(call->call.<java.util.function.Supplier<?>>getArgument(0).get());
            when(window.readIo(any())).thenAnswer(call->call.<CdsImportMaintenanceService.IoRead<?>>getArgument(0).get());
            when(ledger.claimPreparation(eq("run"),any(),any(),anyBoolean())).thenReturn(1L);
            when(ledger.claimPlanning(eq("run"),any(),any())).thenReturn(1L);when(ledger.claimExecution(eq("run"),any(),any())).thenReturn(1L);
            var patient=mock(CdsImportPatientDto.class);when(patient.patientKey()).thenReturn("patient");when(patient.state()).thenAnswer(call->patientState.get());
            when(ledger.patientStatus("run","patient")).thenReturn(patient);
            when(ledger.patients(eq("run"),anyString(),eq(100))).thenAnswer(call->call.<String>getArgument(1).isEmpty()?List.of(patient):List.of());
            for(int i=0;i<3;i++) {
                var unit=mock(CdsImportUnitDto.class);var unitState=new AtomicReference<>("EXPECTED");
                when(unit.unitKey()).thenReturn("unit"+i);when(unit.patientKey()).thenReturn("patient");when(unit.state()).thenAnswer(call->unitState.get());units.add(unit);unitStates.add(unitState);
                var work=mock(CdsImportWorkDto.class);var record=mock(CdsRecordDto.class);when(record.sourceXPath()).thenReturn("/record");when(work.record()).thenReturn(record);when(work.command()).thenReturn(command);
                when(planning.work("run",unit)).thenReturn(work);when(ledger.unitStatus("run",unit.unitKey())).thenReturn(unit);
            }
            when(ledger.units(eq("run"),eq("patient"),anyString(),eq(100))).thenAnswer(call->call.<String>getArgument(2).isEmpty()?units:List.of());
            when(ledger.readyUnits("run",100)).thenAnswer(call->units.stream().filter(u->u.state().equals("EXPECTED")).toList());
            when(records.write(anyString(),anyLong(),any(),any(),any(),any(),any(),any())).thenAnswer(call->{clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(40));return new CdsImportReceiptDto("{}","digest");});
            when(unitService.execute(eq("run"),eq(1L),anyString(),any(),any())).thenAnswer(call->{
                int i=Integer.parseInt(call.<String>getArgument(2).substring(4));
                if(unitStates.get(i).get().equals("COMMITTED"))return new CdsImportReceiptDto("{}","digest",true);
                CdsImportReceiptDto receipt=call.<CdsImportUnitService.Work>getArgument(3).write();unitStates.get(i).set("COMMITTED");return receipt;
            });
            doAnswer(call->{codes.add(call.getArgument(5));return null;}).when(ledger).diagnostic(anyString(),anyLong(),nullable(String.class),nullable(String.class),anyString(),anyString(),anyString(),nullable(String.class),anyString(),nullable(String.class),anyBoolean());
            doAnswer(call->{patientState.set("PARTIAL");return null;}).when(ledger).markIncomplete(anyString(),anyLong(),anyString(),anyBoolean(),anyBoolean(),anyBoolean());
            doAnswer(call->{patientState.set("COMPLETE");return null;}).when(ledger).finalizePatient(anyString(),anyLong(),anyString(),any());
            when(ledger.finish("run",1L)).thenAnswer(call->{state.set(patientState.get().equals("COMPLETE")?"COMPLETE":"INCOMPLETE");return job;});
            when(ledger.heldPatients("run")).thenAnswer(call->patientState.get().equals("COMPLETE")?0L:1L);
            CdsImportFailureEvidenceService selectedEvidence=mock(CdsImportFailureEvidenceService.class);
            if(evidenceRoot!=null) {
                when(jobs.root("run")).thenReturn(evidenceRoot);
                selectedEvidence=new CdsImportFailureEvidenceService(jobs,mock(CdsImportFileService.class));
            }
            evidence=selectedEvidence;
            engine=new CdsImportExecutionService(ledger,unitService,jobs,planning,records,mock(CdsImportAccessService.class),configuration,maintenance,reports,coordinator,verification,mock(CdsImportTargetSafetyService.class),evidence,fields,clock::get);
        }
        int committed(){return (int)unitStates.stream().filter(s->s.get().equals("COMMITTED")).count();}
        MockedStatic<CdsImportJobService> keys(){var keys=mockStatic(CdsImportJobService.class);keys.when(()->CdsImportJobService.rulesetDigest(eq(command),any())).thenReturn("rules");keys.when(()->CdsImportJobService.optionsDigest(command)).thenReturn("options");return keys;}
    }
}
