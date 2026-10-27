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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real protected report publication with deterministic time; clinical writes are deliberately absent. */
@Tag("unit")
class CdsImportReportBudgetUnitTest {
    @TempDir Path temporary;

    @Test void combinedRowsTimeOut_withoutSelectingOrChangingClinicalCompletion_andSameRunRetrySucceeds() throws Exception {
        Fixture f=new Fixture();Path old=Files.writeString(f.jobRoot.resolve("prior-report-proof"),"retained prior report");
        f.rowMillis.set(60);
        CdsImportException failure=assertThrows(CdsImportException.class,()->f.results.generate(f.run,1));
        assertEquals("IMPORT_REPORT_GENERATION_TIMEOUT",failure.getCode());assertEquals("FAILED",f.reportState.get());
        assertEquals("COMPLETE",f.job.state());assertEquals("retained prior report",Files.readString(old));
        verify(f.selection,never()).select(anyString(),anyLong(),anyLong(),anyString(),anyString(),anyString());
        verify(f.ledger).diagnostic(eq(f.run),eq(1L),isNull(),isNull(),eq("WARNING"),eq("IMPORT_REPORT_GENERATION_TIMEOUT"),eq("REPORTING"),isNull(),anyString(),isNull(),eq(false));
        // Retained source/run are unchanged; stopped elapsed time is excluded from a fresh report attempt.
        f.clock.addAndGet(TimeUnit.DAYS.toNanos(2));f.rowMillis.set(0);
        f.results.generate(f.run,1);
        assertEquals("DELIVERED",f.reportState.get());assertNotNull(f.selected.get());
        var receipt=new ObjectMapper().readTree(f.selected.get().getParent().resolve("completion.json").toFile());
        assertTrue(receipt.path("cleanComplete").asBoolean());assertEquals("COMPLETE",receipt.path("state").asText());
        var errors=Files.readString(f.selected.get().getParent().resolve("ImportErrorLog.jsonl"));
        assertTrue(errors.contains("IMPORT_REPORT_GENERATION_TIMEOUT"));assertTrue(errors.contains("\"blocking\":false"));
        assertEquals("retained prior report",Files.readString(old));
        verify(f.ledger,never()).finish(anyString(),anyLong());
    }

    @Test void cleanupReceivesTheSameReportBudget_andTimeoutDoesNotPublishACompletion() throws Exception {
        Fixture f=new Fixture();
        when(f.cleanup.reconcile(eq(f.run),eq(1L),any(),any())).thenAnswer(call->{
            f.clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(100));call.<Runnable>getArgument(3).run();return f.clean;
        });
        assertTimeoutFailure(f);
        assertEquals("COMPLETE",f.job.state());assertEquals("FAILED",f.reportState.get());
        try(var paths=Files.walk(f.jobRoot)){assertFalse(paths.anyMatch(p->p.getFileName().toString().equals("completion.json")));}
    }

    @Test void timeoutRetainsAnAlreadyDeliveredImmutableGeneration_untilExplicitSameRunRecovery() throws Exception {
        Fixture f=new Fixture();f.results.generate(f.run,1);
        Path previous=f.selected.get();byte[] manifest=Files.readAllBytes(previous),receipt=Files.readAllBytes(previous.getParent().resolve("completion.json"));
        assertEquals(1,f.generation.get());f.rowMillis.set(60);
        assertEquals("IMPORT_REPORT_GENERATION_TIMEOUT",assertThrows(CdsImportException.class,()->f.results.generate(f.run,1)).getCode());
        assertEquals("DELIVERED",f.reportState.get());assertEquals("FAILED",f.cleanupState.get());
        assertFalse((Boolean)f.summary().get("cleanComplete"));assertEquals(previous,f.selected.get());assertEquals(1,f.generation.get());
        assertArrayEquals(manifest,Files.readAllBytes(previous));assertArrayEquals(receipt,Files.readAllBytes(previous.getParent().resolve("completion.json")));
        f.rowMillis.set(0);f.clock.addAndGet(TimeUnit.DAYS.toNanos(1));f.results.generate(f.run,1);
        assertEquals(2,f.generation.get());assertNotEquals(previous,f.selected.get());assertTrue((Boolean)f.summary().get("cleanComplete"));
        assertArrayEquals(manifest,Files.readAllBytes(previous));assertArrayEquals(receipt,Files.readAllBytes(previous.getParent().resolve("completion.json")));
        verify(f.ledger,never()).finish(anyString(),anyLong());verify(f.ledger,never()).finalizePatient(anyString(),anyLong(),anyString(),any());
    }

    @Test void expiryInTheFirstGenerationProbeStillRetainsPriorDeliveryAcknowledgement() throws Exception {
        Fixture f=new Fixture();f.results.generate(f.run,1);
        Path previous=f.selected.get();byte[] manifest=Files.readAllBytes(previous),receipt=Files.readAllBytes(previous.getParent().resolve("completion.json"));
        when(f.ledger.status(f.run)).thenAnswer(call->{f.clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(100));return f.job;});
        assertEquals("IMPORT_REPORT_GENERATION_TIMEOUT",assertThrows(CdsImportException.class,()->f.results.generate(f.run,1)).getCode());
        assertEquals("DELIVERED",f.reportState.get());assertEquals("FAILED",f.cleanupState.get());
        assertEquals(previous,f.selected.get());assertEquals(1,f.generation.get());
        assertArrayEquals(manifest,Files.readAllBytes(previous));assertArrayEquals(receipt,Files.readAllBytes(previous.getParent().resolve("completion.json")));
    }

    @Test void budgetExpiryAfterReceiptCreationStillRefusesTheFinalSelection() throws Exception {
        Fixture f=new Fixture();AtomicLong summaries=new AtomicLong();
        when(f.ledger.summary(f.run)).thenAnswer(call->{if(summaries.incrementAndGet()==3)f.clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(100));return f.summary();});
        assertTimeoutFailure(f);
        assertEquals("FAILED",f.reportState.get());
        try(var paths=Files.walk(f.jobRoot)){assertTrue(paths.anyMatch(p->p.getFileName().toString().equals("completion.json")),"Unselected receipt is retained evidence, not delivery");}
    }

    @Test void validationScanProbesEvenXmlMembersWithNoDiagnosticRows() throws Exception {
        Fixture f=new Fixture();Path snapshotRoot=Files.createDirectory(f.jobRoot.resolve("snapshot"));
        var snapshot=new CdsSnapshotDto(snapshotRoot,snapshotRoot.resolve("files.jsonl"),"a".repeat(64),3,3,3);
        var jobs=mock(CdsImportJobService.class);var snapshots=mock(CdsSnapshotService.class);
        var document=new CdsImportJobDocumentDto(null,null,snapshot,null,f.jobRoot);when(jobs.load(f.run)).thenReturn(document);
        when(snapshots.load(eq(snapshotRoot),eq(false),any())).thenReturn(snapshot);
        doAnswer(call->{
            var consumer=call.<java.util.function.Consumer<CdsSnapshotFileDto>>getArgument(1);
            for(int i=0;i<3;i++){f.clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(60));consumer.accept(new CdsSnapshotFileDto("patient"+i+".xml",snapshotRoot.resolve("opaque"+i),"b".repeat(64),1,true));}
            return null;
        }).when(snapshots).forEachXml(eq(snapshot),any());
        var planning=new CdsImportPlanningService(f.ledger,f.configuration,snapshots,mock(CdsXmlParser.class),new CdsFieldParser(),mock(CdsPatientIdentityResolver.class),mock(CdsImportNativeService.class),mock(CdsCourseService.class),mock(CdsImportFileService.class),jobs,mock(CdsPreflightIdentityService.class));
        var results=new CdsImportResultService(f.ledger,f.selection,f.configuration,planning,new CdsFieldParser(),f.cleanup,f.clock::get);
        CdsImportException failure=assertThrows(CdsImportException.class,()->results.generate(f.run,1));
        assertEquals("IMPORT_REPORT_GENERATION_TIMEOUT",failure.getCode());
        verify(f.selection,never()).select(anyString(),anyLong(),anyLong(),anyString(),anyString(),anyString());
    }

    private static void assertTimeoutFailure(Fixture f) throws Exception {
        assertEquals("IMPORT_REPORT_GENERATION_TIMEOUT",assertThrows(CdsImportException.class,()->f.results.generate(f.run,1)).getCode());
        verify(f.selection,never()).select(anyString(),anyLong(),anyLong(),anyString(),anyString(),anyString());
    }
    final class Fixture {
        final String run=UUID.randomUUID().toString();final AtomicLong clock=new AtomicLong(),rowMillis=new AtomicLong();
        final AtomicReference<String> reportState=new AtomicReference<>("PENDING"),cleanupState=new AtomicReference<>("PENDING");final AtomicReference<Path> selected=new AtomicReference<>();
        final AtomicLong generation=new AtomicLong();
        final CdsImportLedgerService ledger=mock(CdsImportLedgerService.class);final CdsImportReportService selection=mock(CdsImportReportService.class);
        final CdsImportConfigurationService configuration=mock(CdsImportConfigurationService.class);final CdsImportCleanupService cleanup=mock(CdsImportCleanupService.class);
        final CdsImportJobDto job=mock(CdsImportJobDto.class);final Path jobRoot;
        final List<CdsImportDiagnosticDto> history=new java.util.ArrayList<>();
        final CdsImportCleanupDto clean=new CdsImportCleanupDto("COMPLETE",Map.of(),Map.of(),List.of(),true,0);
        final CdsImportResultService results;
        Fixture() throws Exception {
            Path staging=Files.createDirectory(temporary.resolve("staging-"+run),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            jobRoot=Files.createDirectory(staging.resolve("job"),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            Path request=Files.writeString(jobRoot.resolve("job.json"),"{}");
            var limits=mock(CdsInputLimitsDto.class);when(limits.maxDurationMillis()).thenReturn(100L);when(configuration.limits()).thenReturn(limits);when(configuration.stagingRoot()).thenReturn(staging);
            when(ledger.snapshotLocation(run)).thenReturn(request.toString());when(ledger.status(run)).thenReturn(job);when(job.ownerEpoch()).thenReturn(1L);when(job.state()).thenReturn("COMPLETE");
            when(job.sourceDigest()).thenReturn("a".repeat(64));when(job.manifestDigest()).thenReturn("a".repeat(64));when(job.createdAt()).thenReturn(Instant.EPOCH);
            when(job.reportGeneration()).thenAnswer(call->generation.get());
            when(ledger.summary(run)).thenAnswer(call->summary());
            when(ledger.diagnostics(eq(run),anyLong(),eq(100))).thenAnswer(call->call.<Long>getArgument(1)==0?List.copyOf(history):List.of());
            doAnswer(call->{
                if("IMPORT_REPORT_GENERATION_TIMEOUT".equals(call.getArgument(5))) {
                    var diagnostic=mock(CdsImportDiagnosticDto.class);when(diagnostic.id()).thenReturn(1L);when(diagnostic.code()).thenReturn("IMPORT_REPORT_GENERATION_TIMEOUT");
                    when(diagnostic.severity()).thenReturn("WARNING");when(diagnostic.stage()).thenReturn("REPORTING");when(diagnostic.safeSummary()).thenReturn("Previous report generation attempt timed out");history.add(diagnostic);
                }
                return null;
            }).when(ledger).diagnostic(eq(run),eq(1L),isNull(),isNull(),anyString(),anyString(),anyString(),isNull(),anyString(),isNull(),eq(false));
            var patients=new java.util.ArrayList<CdsImportPatientDto>();
            for(int i=0;i<3;i++) {var patient=mock(CdsImportPatientDto.class);String key="p"+i;when(patient.patientKey()).thenReturn(key);when(patient.state()).thenReturn("COMPLETE");when(patient.sourcePatientId()).thenAnswer(call->{clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(rowMillis.get()));return key;});patients.add(patient);}
            when(ledger.patients(eq(run),anyString(),eq(100))).thenAnswer(call->call.<String>getArgument(1).isEmpty()?patients:List.of());
            when(cleanup.reconcile(eq(run),eq(1L),any(),any())).thenAnswer(call->{call.<Runnable>getArgument(3).run();return clean;});
            doAnswer(call->{if(call.getArgument(2)!=null)reportState.set(call.getArgument(2));if(call.getArgument(3)!=null)cleanupState.set(call.getArgument(3));return null;}).when(ledger).operational(eq(run),eq(1L),nullable(String.class),nullable(String.class),nullable(String.class));
            when(selection.select(eq(run),eq(1L),anyLong(),anyString(),anyString(),anyString())).thenAnswer(call->{assertFalse((Boolean)summary().get("cleanComplete"),"No clean completion before selecting this physical report generation");assertEquals(generation.get(),call.<Long>getArgument(2));selected.set(Path.of(call.<String>getArgument(4)));generation.incrementAndGet();reportState.set("DELIVERED");return job;});
            results=new CdsImportResultService(ledger,selection,configuration,mock(CdsImportPlanningService.class),new CdsFieldParser(),cleanup,clock::get);
        }
        Map<String,Object> summary(){return Map.of("state","COMPLETE","clinicalComplete",true,"discoveryComplete",true,"dryRun",false,"reportState",reportState.get(),"reportsDelivered",reportState.get().equals("DELIVERED"),"cleanComplete",reportState.get().equals("DELIVERED") && cleanupState.get().equals("COMPLETE"));}
    }
}
