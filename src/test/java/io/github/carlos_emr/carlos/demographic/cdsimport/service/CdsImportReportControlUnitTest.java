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
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Reporting has epoch/physical fencing even when cancellation or released admission forbids clinical writes. */
@Tag("unit")
class CdsImportReportControlUnitTest {
    private static final CdsImportControlDto RELEASED_CANCELLED=new CdsImportControlDto(true,true,1,true,false);

    @Test void everyReadChecksLocalBudget_butOnlyElapsedOrForcedBoundariesReadPhysicalControl() {
        var owner=owner();var clock=new AtomicLong();var budgets=new AtomicInteger();
        try(var control=control(owner,clock,budgets::incrementAndGet)) {
            for(int i=0;i<100;i++){control.checkBudget();control.check(false);}
            verify(owner,times(1)).control("run");assertThat(budgets.get()).isGreaterThan(200);
            clock.set(249_999_999L);control.check(false);verify(owner,times(1)).control("run");
            clock.incrementAndGet();control.check(false);verify(owner,times(2)).control("run");
            control.check(true);verify(owner,times(3)).control("run");
        }
    }

    @Test void changedBindingClosedScopeAndForeignThreadsCannotUseTheCapability() throws Exception {
        var owner=owner();var control=control(owner,new AtomicLong(),()->{});
        assertThatThrownBy(()->control.requireBinding("other",1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->control.requireBinding("run",2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CdsImportReportControl("run",-1,owner,()->0,()->{})).isInstanceOf(IllegalArgumentException.class);
        var foreign=new AtomicReference<RuntimeException>();
        Thread thread=new Thread(()->{try{control.check(true);}catch(RuntimeException failure){foreign.set(failure);}});
        thread.start();thread.join(5000);assertThat(thread.isAlive()).isFalse();
        assertThat(foreign.get()).isInstanceOf(IllegalStateException.class);
        control.close();assertThatThrownBy(()->control.check(false)).isInstanceOf(IllegalStateException.class);
        verify(owner,times(1)).control("run");
    }

    @Test void aLocalGenerationDeadlineStopsBeforeTheNextSqlPoll() {
        var owner=owner();var clock=new AtomicLong();
        try(var control=control(owner,clock,()->{
            if(clock.get()>=100_000_000L)throw new CdsImportException("IMPORT_REPORT_GENERATION_TIMEOUT");
        })) {
            clock.set(100_000_000L);
            assertThatThrownBy(()->control.check(false)).hasMessage("IMPORT_REPORT_GENERATION_TIMEOUT");
            verify(owner,times(1)).control("run");
        }
    }

    @Test void failedPhysicalRunOrEpochObservationsCannotBecomeCachedSuccess() {
        var invalid=new CdsImportControlDto[]{new CdsImportControlDto(false,true,1,true,false),
                new CdsImportControlDto(true,false,-1,true,false),new CdsImportControlDto(true,true,2,true,false)};
        for(var value:invalid) {
            var owner=owner();
            try(var control=control(owner,new AtomicLong(),()->{})) {
                when(owner.control("run")).thenReturn(value);
                String code=value.coordinatorOwned()?"OWNER_FENCED":"IMPORT_COORDINATOR_LOST";
                assertThatThrownBy(()->control.check(true)).hasMessage(code);
                assertThatThrownBy(()->control.check(false)).hasMessage(code);
                verify(owner,times(3)).control("run");
            }
        }
    }

    @Test void interruptsAndAmbientClinicalTransactionsNeverPerformAnOwnerSqlRead() {
        var owner=owner();
        try(var control=control(owner,new AtomicLong(),()->{})) {
            try {
                Thread.currentThread().interrupt();
                assertThatThrownBy(()->control.check(true)).hasMessage("REPORT_CANCELLED");
            } finally {Thread.interrupted();}
            try {
                TransactionSynchronizationManager.setActualTransactionActive(true);
                assertThatThrownBy(()->control.check(true)).hasMessage("AMBIENT_TRANSACTION_REFUSED");
            } finally {TransactionSynchronizationManager.setActualTransactionActive(false);}
            verify(owner,times(1)).control("run");
        }
    }

    @Test void cancelledAndCompletedJobsAfterAdmissionReleaseStillPublishAllFourPhysicalReports(
            @TempDir Path temp) throws Exception {
        for(boolean cancelled:new boolean[]{false,true}) {
            var f=fixture(Files.createDirectory(temp.resolve("case-"+cancelled)));var owner=owner(f.run);
            if(cancelled) {
                when(f.job.state()).thenReturn("CANCELLED_BEFORE_WRITE");
                when(f.ledger.patients(eq(f.run),anyString(),eq(100))).thenReturn(List.of());
                when(f.ledger.summary(f.run)).thenAnswer(call->{var summary=new java.util.LinkedHashMap<>(f.summary());
                    summary.put("state","CANCELLED_BEFORE_WRITE");summary.put("clinicalComplete",false);summary.put("cleanComplete",false);return summary;});
            }
            // A fresh report attempt is independent of a long-expired clinical execution attempt.
            f.clock.set(TimeUnit.DAYS.toNanos(2));f.results.generate(f.run,1,owner);
            assertReports(f);
            var receipt=new ObjectMapper().readTree(f.selected.get().getParent().resolve("completion.json").toFile());
            assertThat(receipt.path("clinicalComplete").asBoolean()).isEqualTo(!cancelled);
            assertThat(receipt.path("state").asText()).isEqualTo(cancelled?"CANCELLED_BEFORE_WRITE":"COMPLETE");
            verify(f.ledger,times(1)).status(f.run);verify(f.ledger,never()).finish(anyString(),anyLong());
            assertThat(mockingDetails(owner).getInvocations().stream().filter(i->i.getMethod().getName().equals("control")).count()).isBetween(3L,5L);
        }
    }

    @Test void anAlreadyClosedCoordinatorUsesOnlyTheExistingFreshEpochReportingPath(
            @TempDir Path temp) throws Exception {
        var f=fixture(temp);var dao=mock(io.github.carlos_emr.carlos.commn.dao.CdsImportCoordinatorDao.class);
        var lease=mock(io.github.carlos_emr.carlos.commn.dao.CdsImportCoordinatorDao.Lease.class);when(dao.acquire()).thenReturn(lease);
        var owner=new CdsImportCoordinatorService(dao).acquire();owner.close();
        f.results.generate(f.run,1,owner);assertReports(f);
        verify(lease,never()).control(anyString());
        assertThat(mockingDetails(f.ledger).getInvocations().stream().filter(i->i.getMethod().getName().equals("status")).count()).isGreaterThan(1);
        verify(dao,times(1)).acquire(); // Reporting never reacquires ownership or changes clinical outcome.
        verify(f.ledger,never()).finish(anyString(),anyLong());
    }

    @Test void aFailureBeforeInitialPreparationCanDeliverItsEpochZeroReports(
            @TempDir Path temp) throws Exception {
        var f=fixture(temp);var owner=owner(f.run);
        when(owner.control(f.run)).thenReturn(new CdsImportControlDto(true,true,0,true,false));
        when(f.job.ownerEpoch()).thenReturn(0L);when(f.job.state()).thenReturn("FAILED_BEFORE_WRITE");
        when(f.ledger.patients(eq(f.run),anyString(),eq(100))).thenReturn(List.of());
        when(f.ledger.summary(f.run)).thenAnswer(call->{var summary=new java.util.LinkedHashMap<>(f.summary());
            summary.put("state","FAILED_BEFORE_WRITE");summary.put("clinicalComplete",false);summary.put("cleanComplete",false);return summary;});
        when(f.cleanup.reconcile(eq(f.run),eq(0L),any(),any(),any(CdsImportReportControl.class))).thenReturn(f.clean);
        doAnswer(call->{if(call.getArgument(2)!=null)f.reportState.set(call.getArgument(2));if(call.getArgument(3)!=null)f.cleanupState.set(call.getArgument(3));return null;})
                .when(f.ledger).operational(eq(f.run),eq(0L),nullable(String.class),nullable(String.class),nullable(String.class));
        when(f.selection.select(eq(f.run),eq(0L),anyLong(),anyString(),anyString(),anyString())).thenAnswer(call->{
            f.selected.set(Path.of(call.<String>getArgument(4)));f.generation.incrementAndGet();f.reportState.set("DELIVERED");return f.job;
        });
        f.results.generate(f.run,0,owner);assertReports(f);
        var receipt=new ObjectMapper().readTree(f.selected.get().getParent().resolve("completion.json").toFile());
        assertThat(receipt.path("clinicalComplete").asBoolean()).isFalse();
        assertThat(receipt.path("state").asText()).isEqualTo("FAILED_BEFORE_WRITE");
        verify(f.ledger,never()).finish(anyString(),anyLong());
    }

    @Test void physicalLossDuringGenerationCannotFallBackOrSelectAReportSet(
            @TempDir Path temp) throws Exception {
        var f=fixture(temp);var owner=owner(f.run);
        when(f.cleanup.reconcile(eq(f.run),eq(1L),any(),any(),any(CdsImportReportControl.class))).thenAnswer(call->{
            when(owner.control(f.run)).thenReturn(new CdsImportControlDto(false,true,1,true,false));
            call.<CdsImportReportControl>getArgument(4).check(true);return f.clean;
        });
        assertThatThrownBy(()->f.results.generate(f.run,1,owner)).hasMessage("IMPORT_COORDINATOR_LOST");
        assertThat(f.reportState.get()).isEqualTo("FAILED");assertThat(f.job.state()).isEqualTo("COMPLETE");
        verify(f.selection,never()).select(anyString(),anyLong(),anyLong(),anyString(),anyString(),anyString());
        verify(f.ledger,times(1)).status(f.run); // No mid-generation legacy retry hides the lost owner.
    }

    @Test void aHealthyButUnreadableControlRemainsAVisibleFailure_andPreservesPriorReportBytes(
            @TempDir Path temp) throws Exception {
        var f=fixture(temp);f.results.generate(f.run,1);Path old=f.selected.get();byte[] original=Files.readAllBytes(old);long generation=f.generation.get();
        var owner=owner(f.run);var cause=new java.sql.SQLException("private-control-detail","HY000",71);
        when(owner.control(f.run)).thenThrow(new CdsImportException("IMPORT_CONTROL_UNAVAILABLE",false,cause));
        assertThatThrownBy(()->f.results.generate(f.run,1,owner)).hasMessage("IMPORT_CONTROL_UNAVAILABLE").hasCause(cause);
        assertThat(f.selected.get()).isEqualTo(old);assertThat(f.generation.get()).isEqualTo(generation);
        assertThat(Files.readAllBytes(old)).isEqualTo(original);assertThat(f.job.state()).isEqualTo("COMPLETE");
        assertThat(f.cleanupState.get()).isEqualTo("FAILED");
    }

    @Test void reportBudgetIsCheckedAgainAfterASlowPhysicalControlObservation(
            @TempDir Path temp) throws Exception {
        var f=fixture(temp);var owner=owner(f.run);
        when(owner.control(f.run)).thenAnswer(call->{f.clock.addAndGet(100_000_000L);return RELEASED_CANCELLED;});
        assertThatThrownBy(()->f.results.generate(f.run,1,owner)).hasMessage("IMPORT_REPORT_GENERATION_TIMEOUT");
        assertThat(f.reportState.get()).isEqualTo("FAILED");
        verify(f.selection,never()).select(anyString(),anyLong(),anyLong(),anyString(),anyString(),anyString());
    }

    @Test void typedCleanupAndDiagnosticScanningRejectMismatchedBindingsBeforeFilesystemAccess() throws Exception {
        var ledger=mock(CdsImportLedgerService.class);var files=mock(CdsImportFileService.class);
        var configuration=mock(CdsImportConfigurationService.class);var jobs=mock(CdsImportJobService.class);
        var cleanup=new CdsImportCleanupService(ledger,files,configuration,mock(CdsUploadService.class),mock(CdsPreacceptService.class));
        var planning=new CdsImportPlanningService(ledger,configuration,mock(CdsSnapshotService.class),mock(CdsXmlParser.class),
                new CdsFieldParser(),mock(CdsPatientIdentityResolver.class),mock(CdsImportNativeService.class),mock(CdsCourseService.class),files,jobs,mock(CdsPreflightIdentityService.class));
        try(var control=control(owner(),new AtomicLong(),()->{})) {
            assertThatThrownBy(()->cleanup.reconcile("other",1,null,()->{},control)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->planning.forEachValidationDiagnostic("run",2,row->{},control)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(ledger,files,configuration,jobs);
    }

    @Test void typedCleanupLosingItsEpochRetainsPrivateRecoveryPayloadAndJournal(
            @TempDir Path temp) throws Exception {
        cleanupFence(temp,false);
    }

    @Test void typedCleanupWithASameEpochLostCoordinatorCannotReconcileOrRemoveVerifiedBytes(
            @TempDir Path temp) throws Exception {
        cleanupFence(temp,true);
    }

    private static void cleanupFence(Path temp,boolean physicalLoss) throws Exception {
        var ledger=mock(CdsImportLedgerService.class);var files=mock(CdsImportFileService.class);var config=mock(CdsImportConfigurationService.class);
        Path staging=directory(temp.resolve("staging"));Path job=directory(staging.resolve("job"));Path documents=directory(temp.resolve("documents"));
        String run="run",unit="a".repeat(64);byte[] bytes={1,2,3};String digest=CdsPaths.hex(CdsPaths.sha256().digest(bytes));
        Path reports=directory(job.resolve("reports-1-"+UUID.randomUUID()));
        new ObjectMapper().writeValue(reports.resolve("owner.json").toFile(),Map.of("protocol",1,"runId",run,"ownerEpoch",1,"directory",reports.getFileName().toString()));
        Path pending=directory(documents.resolve(".cds-pending"));Path runRoot=directory(pending.resolve(run));String id=UUID.randomUUID().toString();Path operation=directory(runRoot.resolve(id));
        var ownership=new CdsDocumentPublicationService.PendingOwner(1,run,1,unit,"text","txt",digest,bytes.length,
                CdsDocumentPublicationService.filename(unit,"text","txt",digest),id,System.currentTimeMillis());
        new ObjectMapper().writeValue(operation.resolve("owner.json").toFile(),ownership);Path payload=Files.write(operation.resolve("payload.partial"),bytes);
        when(config.stagingRoot()).thenReturn(staging);when(config.limits()).thenReturn(CdsInputLimitsDto.defaults());when(ledger.snapshotLocation(run)).thenReturn(job.resolve("job.json").toString());
        var owner=owner();
        if(physicalLoss) {
            Files.createLink(documents.resolve(ownership.finalFilename()),payload);
            when(ledger.unitStatus(run,unit)).thenReturn(mock(CdsImportUnitDto.class));
            when(owner.control(run)).thenReturn(RELEASED_CANCELLED,RELEASED_CANCELLED,RELEASED_CANCELLED,
                    new CdsImportControlDto(false,true,1,true,false));
        } else when(ledger.unitStatus(run,unit)).thenAnswer(call->{when(owner.control(run)).thenReturn(new CdsImportControlDto(true,true,2,true,false));return mock(CdsImportUnitDto.class);});
        var cleanup=new CdsImportCleanupService(ledger,files,config,mock(CdsUploadService.class),mock(CdsPreacceptService.class));
        try(var paths=mockStatic(PathValidationUtils.class,CALLS_REAL_METHODS);var control=control(owner,new AtomicLong(),()->{})) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(documents.toFile());
            assertThatThrownBy(()->cleanup.reconcile(run,1,reports,()->{},control)).hasMessage(physicalLoss?"IMPORT_COORDINATOR_LOST":"OWNER_FENCED");
        }
        assertThat(Files.readAllBytes(payload)).isEqualTo(bytes);assertThat(Files.exists(operation.resolve("owner.json"))).isTrue();
        verify(files,never()).reconcilePublication(anyString(),anyLong(),anyString(),anyString(),anyLong());
        verify(ledger,never()).status(anyString());
    }

    private static Path directory(Path path)throws Exception {
        return Files.createDirectory(path,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    }
    private static CdsImportReportBudgetUnitTest.Fixture fixture(Path root)throws Exception {
        var test=new CdsImportReportBudgetUnitTest();test.temporary=root;var fixture=test.new Fixture();
        when(fixture.cleanup.reconcile(eq(fixture.run),eq(1L),any(),any(),any(CdsImportReportControl.class))).thenAnswer(call->{
            call.<Runnable>getArgument(3).run();call.<CdsImportReportControl>getArgument(4).checkBudget();return fixture.clean;
        });
        return fixture;
    }
    private static void assertReports(CdsImportReportBudgetUnitTest.Fixture f)throws Exception {
        assertThat(f.reportState.get()).isEqualTo("DELIVERED");assertThat(f.selected.get()).isNotNull();
        var manifest=new ObjectMapper().readTree(f.selected.get().toFile());assertThat(manifest.path("files").size()).isEqualTo(4);
        for(String kind:List.of("event","error","units","completion")) {
            var entry=manifest.path("files").path(kind);Path path=f.selected.get().getParent().resolve(entry.path("name").asText());
            byte[] bytes=Files.readAllBytes(path);assertThat(bytes.length).isEqualTo(entry.path("bytes").asInt());
            assertThat(CdsPaths.hex(CdsPaths.sha256().digest(bytes))).isEqualTo(entry.path("sha256").asText());
        }
    }
    private static CdsImportCoordinatorService.Owner owner(){return owner("run");}
    private static CdsImportCoordinatorService.Owner owner(String run) {
        var owner=mock(CdsImportCoordinatorService.Owner.class);when(owner.control(run)).thenReturn(RELEASED_CANCELLED);return owner;
    }
    private static CdsImportReportControl control(CdsImportCoordinatorService.Owner owner,AtomicLong clock,Runnable budget) {
        return new CdsImportReportControl("run",1,owner,clock::get,budget);
    }
}
