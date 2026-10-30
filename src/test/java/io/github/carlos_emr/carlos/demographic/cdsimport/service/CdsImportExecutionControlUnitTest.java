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
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsImportExecutionControlUnitTest {
    private static final CdsImportControlDto VALID = new CdsImportControlDto(true,true,7,false,true);

    @Test void shouldShareOnePollAcrossFieldAndFileReads_withForcedBoundariesAlwaysFresh() throws Exception {
        var clock=new AtomicLong();var fields=new CdsFieldParser();var owner=owner();
        try(var scope=scope(owner,fields,clock,10000,()->{})) {
            for(int index=0;index<100;index++){fields.checkpoint();scope.check(false);}
            verify(owner,times(1)).control("run");
            clock.set(249_999_999);fields.checkpoint();scope.check(false);
            verify(owner,times(1)).control("run");
            clock.incrementAndGet();fields.checkpoint();scope.check(false);
            verify(owner,times(2)).control("run");
            scope.check(true);verify(owner,times(3)).control("run");
            assertThatThrownBy(()->scope.requireBinding("other",7)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->scope.requireBinding("run",8)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void shouldBackOffUnchangedOwnerPolls_toOneEveryTwoSecondsDuringLongReads() throws Exception {
        var clock=new AtomicLong();var fields=new CdsFieldParser();var owner=owner();
        try(var scope=scope(owner,fields,clock,10000,()->{})) {
            int expected=1;
            for(long next:new long[]{250_000_000L,750_000_000L,1_750_000_000L,3_750_000_000L,5_750_000_000L}) {
                clock.set(next-1);fields.checkpoint();verify(owner,times(expected)).control("run");
                clock.set(next);fields.checkpoint();verify(owner,times(++expected)).control("run");
            }
            // Every forced boundary still reads durable state at once.
            scope.check(true);verify(owner,times(++expected)).control("run");
        }
    }

    @Test void shouldCheckOverallBudgetOnEveryBoundedFieldRead_evenBeforeNextSqlPoll() throws Exception {
        var clock=new AtomicLong();var fields=new CdsFieldParser();var owner=owner();
        var budgets=new AtomicInteger();
        try(var scope=scope(owner,fields,clock,10000,()->{
            budgets.incrementAndGet();if(clock.get()>=100_000_000)throw new CdsImportException("IMPORT_ATTEMPT_TIMEOUT");
        })) {
            fields.checkpoint();clock.set(100_000_000);
            assertThatThrownBy(fields::checkpoint).hasMessage("IMPORT_ATTEMPT_TIMEOUT");
            verify(owner,times(1)).control("run");assertThat(budgets.get()).isGreaterThanOrEqualTo(4);
        }
    }

    @Test void shouldCheckStageBudgetOnEveryBoundedFieldRead_withoutSqlHidingDeadline() throws Exception {
        var clock=new AtomicLong();var fields=new CdsFieldParser();var owner=owner();
        try(var scope=scope(owner,fields,clock,100,()->{})) {
            clock.set(100_000_000);
            assertThatThrownBy(fields::checkpoint).hasMessage("IMPORT_FILE_VERIFICATION_TIMEOUT");
            verify(owner,times(1)).control("run");
        }
        clock.set(0);
        when(owner.control("run")).thenAnswer(call->{clock.addAndGet(100_000_000);return VALID;});
        assertThatThrownBy(()->scope(owner,fields,clock,100,()->{})).hasMessage("IMPORT_FILE_VERIFICATION_TIMEOUT");
    }

    @Test void shouldPreservePreviousScopeOnFailedConstruction_withoutRegisteringBrokenOne() throws Exception {
        var fields=new CdsFieldParser();var previous=new AtomicInteger();var owner=owner();
        when(owner.control("run")).thenReturn(null);
        try(var prior=fields.control(previous::incrementAndGet)) {
            assertThatThrownBy(()->scope(owner,fields,new AtomicLong(),10000,()->{})).hasMessage("IMPORT_COORDINATOR_LOST");
            fields.checkpoint();assertThat(previous).hasValue(1);
        }
        fields.checkpoint();assertThat(previous).hasValue(1);
    }

    @Test void shouldNeverCacheSuccess_whenOwnershipChangesOrCancellationArrives() throws Exception {
        var values=new CdsImportControlDto[]{new CdsImportControlDto(false,true,7,false,true),
            new CdsImportControlDto(true,false,-1,false,false),new CdsImportControlDto(true,true,8,false,true),
            new CdsImportControlDto(true,true,7,false,false),new CdsImportControlDto(true,true,7,true,true)};
        var codes=new String[]{"IMPORT_COORDINATOR_LOST","OWNER_FENCED","OWNER_FENCED","OWNER_FENCED","IMPORT_CANCELLED"};
        for(int index=0;index<values.length;index++) {
            var owner=owner();var clock=new AtomicLong();var fields=new CdsFieldParser();
            try(var scope=scope(owner,fields,clock,10000,()->{})) {
                when(owner.control("run")).thenReturn(values[index]);
                String expected=codes[index];
                assertThatThrownBy(()->scope.check(true)).hasMessage(expected);
                assertThatThrownBy(()->scope.check(false)).hasMessage(expected);
                verify(owner,times(3)).control("run");
            }
        }
    }

    @Test void shouldRefuseWorkOnInterruptOrScopeEnd_withoutAnotherSqlRead() throws Exception {
        var owner=owner();var fields=new CdsFieldParser();
        var scope=scope(owner,fields,new AtomicLong(),10000,()->{});
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(()->scope.check(false)).hasMessage("IMPORT_CANCELLED");
        } finally {Thread.interrupted();scope.close();}
        assertThatThrownBy(()->scope.check(true)).isInstanceOf(IllegalStateException.class);
        verify(owner,times(1)).control("run");
    }

    @Test void shouldPollMonotonicElapsed_acrossNegativeValuesAndNanoTimeWrap() throws Exception {
        for(long initial:new long[]{-1_000_000_000L,Long.MAX_VALUE-100_000_000L}) {
            var clock=new AtomicLong(initial);var owner=owner();var fields=new CdsFieldParser();
            try(var scope=scope(owner,fields,clock,10000,()->{})) {
                clock.addAndGet(249_999_999);fields.checkpoint();verify(owner,times(1)).control("run");
                clock.incrementAndGet();fields.checkpoint();verify(owner,times(2)).control("run");
            }
        }
    }

    @Test void shouldShareOneBoundProbeAcrossAcceptedSnapshotAndPlanning_withoutFullStatusPolling(
            @org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        var f=planning(temp,2L<<20);var clock=new AtomicLong();var owner=owner();var checks=new AtomicInteger();
        try(var control=scope(owner,f.fields,clock,10000,checks::incrementAndGet)) {
            var loaded=f.jobs.snapshot("run",7,f.document,control);
            f.planner.plan("run",7,f.document,loaded,control);
            assertThat(loaded).isEqualTo(f.snapshot);
            // Construction and the completed integrity boundary are fresh; all hot reads share them.
            verify(owner,times(2)).control("run");
            verify(f.ledger,times(2)).status("run");
            verify(f.ledger).seal(eq("run"),eq(7L),matches("[0-9a-f]{64}"),eq(1L),eq(8L));
            assertThat(checks.get()).isGreaterThan(50);
        }
    }

    @Test void shouldRejectForeignOrClosedBindingsInSnapshotPlanningAndPublication_beforeAnyDataAccess(
            @org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        var f=planning(temp,0);var control=scope(owner(),f.fields,new AtomicLong(),10000,()->{});
        clearInvocations(f.ledger,f.files);
        try {
            assertThatThrownBy(()->f.jobs.snapshot("other",7,null,control)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->f.jobs.snapshot("run",8,null,control)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->f.planner.plan("other",7,null,null,control)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->f.planner.plan("run",8,null,null,control)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->f.planner.publishValues("other",7,null,control)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->f.planner.publishValues("run",8,null,control)).isInstanceOf(IllegalArgumentException.class);
        } finally {control.close();}
        assertThatThrownBy(()->f.jobs.snapshot("run",7,null,control)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->f.planner.plan("run",7,null,null,control)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->f.planner.publishValues("run",7,null,control)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(f.ledger,f.files);
        assertThat(Files.exists(f.document.jobRoot().resolve("planning.lock"))).isFalse();
    }

    @Test void shouldTreatChangedDurableSnapshotEpochAsOwnerFence_insteadOfCallerBindingError(
            @org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        var f=planning(temp,0);var previous=f.ledger.status("run");
        when(f.ledger.status("run")).thenReturn(new CdsImportJobDto(previous.id(),previous.operator(),previous.providerNo(),
                previous.sourceNamespace(),previous.profile(),previous.state(),previous.sourceDigest(),previous.optionsDigest(),
                previous.rulesetDigest(),null,0,0,8,false,Instant.EPOCH,Instant.EPOCH,0,null,null));
        try(var control=scope(owner(),f.fields,new AtomicLong(),10000,()->{})) {
            assertThatThrownBy(()->f.jobs.snapshot("run",7,f.document,control)).hasMessage("OWNER_FENCED");
        }
        verify(f.snapshots,never()).load(any(),anyBoolean(),any(),any());
    }

    @Test void shouldTreatPlanningCoordinatorFailureAsOperational_insteadOfSealingClinicalReview(
            @org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        for(String code:new String[]{"IMPORT_COORDINATOR_LOST","IMPORT_CONTROL_UNAVAILABLE"}) {
            var f=planning(Files.createDirectory(temp.resolve(code)),0);var owner=owner();var clock=new AtomicLong();
            doAnswer(call->{
                when(owner.control("run")).thenThrow(new CdsImportException(code));clock.set(250_000_000L);return null;
            }).when(f.nativeRecords).validate(any(),any(),any(),anyString());
            try(var control=scope(owner,f.fields,clock,10000,()->{})) {
                assertThatThrownBy(()->f.planner.plan("run",7,f.document,f.snapshot,control)).hasMessage(code);
            }
            verify(f.ledger,never()).seal(anyString(),anyLong(),anyString(),anyLong(),anyLong());
            verify(f.ledger,never()).stage("run",7,"REVIEW_REQUIRED");
        }
    }

    @Test void shouldCheckOverallBudgetOnActualXmlReadsBeforeNextSqlPoll_withoutPublishingParseSeal(
            @org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        var f=planning(temp,0);var owner=owner();var clock=new AtomicLong();var reads=new AtomicInteger();
        try(var control=scope(owner,f.fields,clock,10000,()->{
            if(reads.incrementAndGet()>=20)throw new CdsImportException("IMPORT_ATTEMPT_TIMEOUT");
        })) {
            Path parsed=temp.resolve("budget-parse");
            assertThatThrownBy(()->new CdsXmlParser().parse(f.source,parsed,CdsInputLimitsDto.defaults(),
                    ()->false,record->{},null,()->control.check(false))).hasMessage("IMPORT_ATTEMPT_TIMEOUT");
            assertThat(Files.isDirectory(parsed)).isTrue();
            assertThat(Files.exists(parsed.resolve("parse.seal"))).isFalse();
            assertThat(clock.get()).isZero();verify(owner,times(1)).control("run");
        }
    }

    @Test void shouldCheckOverallBudgetOnActualSnapshotHashReads_beforeNextSqlPoll(
            @org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        var f=planning(temp,8L<<20);var owner=owner();var clock=new AtomicLong();var checks=new AtomicInteger();
        try(var control=scope(owner,f.fields,clock,10000,()->{
            if(checks.incrementAndGet()>=80)throw new CdsImportException("IMPORT_ATTEMPT_TIMEOUT");
        })) {
            assertThatThrownBy(()->f.jobs.snapshot("run",7,f.document,control)).hasMessage("IMPORT_ATTEMPT_TIMEOUT");
            assertThat(clock.get()).isZero();verify(owner,times(1)).control("run");
            verify(f.ledger,times(1)).status("run");
        }
        assertThat(new CdsSnapshotService().load(f.snapshot.root())).isEqualTo(f.snapshot);
    }

    @Test void shouldRetainPartialBytesOnSnapshotCopyDeadline_withoutPublishingImmutableSnapshot(
            @org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        var f=planning(temp,2L<<20);Path target=Files.createDirectory(temp.resolve("copy-job"));
        var owner=owner();var fields=new CdsFieldParser();var clock=new AtomicLong();
        try(var control=scope(owner,fields,clock,10000,()->{
            Path members=target.resolve("stopped.pending/members");
            if(Files.isDirectory(members))try(var paths=Files.walk(members)) {
                if(paths.filter(Files::isRegularFile).anyMatch(path->{try{return Files.size(path)>65536;}catch(java.io.IOException failed){throw new java.io.UncheckedIOException(failed);}}))
                    throw new CdsImportException("IMPORT_ATTEMPT_TIMEOUT");
            } catch(java.io.IOException failed){throw new java.io.UncheckedIOException(failed);}
        })) {
            assertThatThrownBy(()->new CdsSnapshotService().snapshot(f.source.getParent(),f.source.getParent(),target,
                    "stopped",CdsInputLimitsDto.defaults(),()->false,()->control.check(false))).hasMessage("IMPORT_ATTEMPT_TIMEOUT");
            verify(owner,times(1)).control("run");
        }
        assertThat(Files.exists(target.resolve("stopped"))).isFalse();
        assertThat(Files.exists(target.resolve("stopped.pending"))).isTrue();
        try(var files=Files.walk(target.resolve("stopped.pending/members"))) {
            assertThat(files.filter(Files::isRegularFile).mapToLong(path->{try{return Files.size(path);}catch(java.io.IOException failed){throw new java.io.UncheckedIOException(failed);}}).sum())
                    .isBetween(65537L,2L<<20);
        }
        assertThat(Files.size(f.source.getParent().resolve("blob.bin"))).isEqualTo(2L<<20);
    }

    @Test void shouldShareBoundProbeAndSameEpochBlobProofInPublication_butRehashAfterEpochChange(
            @org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        var f=planning(temp,0);var work=publication(f,temp);var owner=owner();var clock=new AtomicLong();
        Set<String> verified=new HashSet<>();
        when(f.files.verifiedFor(anyString(),anyLong(),anyString(),anyString(),anyLong()))
                .thenAnswer(call->verified.contains(call.getArgument(1)+":"+call.getArgument(2)));
        doAnswer(call->{verified.add(call.getArgument(1)+":"+call.getArgument(2));return null;})
                .when(f.files).publishedVerified(anyString(),anyLong(),anyString(),anyString(),anyLong());
        try(var control=scope(owner,f.fields,clock,10000,()->{})) {
            f.planner.publishValues("run",7,work,control);f.planner.publishValues("run",7,work,control);
            verify(owner,times(1)).control("run");
        }
        verify(f.files,times(1)).publishedVerified(eq("run"),eq(7L),anyString(),anyString(),anyLong());
        verify(f.ledger,never()).status("run");
        byte[] value=Files.readAllBytes(work.record().valuesFile());value[0]^=1;Files.write(work.record().valuesFile(),value);
        when(owner.control("run")).thenReturn(new CdsImportControlDto(true,true,8,false,true));
        try(var control=new CdsImportExecutionControl("run",8,owner,"IMPORT_PREPARATION_TIMEOUT",10000,clock::get,()->{},f.fields)) {
            assertThatThrownBy(()->f.planner.publishValues("run",8,work,control)).hasMessage("VALUE_SPOOL_HASH_MISMATCH");
        }
        verify(f.files,never()).publishedVerified(eq("run"),eq(8L),anyString(),anyString(),anyLong());
    }

    @Test void shouldRefuseDurableVerificationProofInPublication_afterLocalReadDeadline(
            @org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        var f=planning(temp,0);var work=publication(f,temp);var owner=owner();var clock=new AtomicLong();var checks=new AtomicInteger();
        try(var control=scope(owner,f.fields,clock,10000,()->{
            if(checks.incrementAndGet()>=5)throw new CdsImportException("IMPORT_ATTEMPT_TIMEOUT");
        })) {
            assertThatThrownBy(()->f.planner.publishValues("run",7,work,control)).hasMessage("IMPORT_ATTEMPT_TIMEOUT");
            verify(owner,times(1)).control("run");
        }
        verify(f.files,never()).publishedVerified(anyString(),anyLong(),anyString(),anyString(),anyLong());
        verify(f.ledger,never()).status("run");
    }

    @Test void shouldRejectMissingLowLevelCallbacks_beforeAnyFilesystemAccess() {
        var snapshots=new CdsSnapshotService();
        assertThatThrownBy(()->snapshots.load(null,true,()->false,null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->snapshots.snapshot(null,null,null,"unused",CdsInputLimitsDto.defaults(),()->false,null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CdsXmlParser().parse(null,null,CdsInputLimitsDto.defaults(),()->false,record->{},null,null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void shouldStopSourceClassificationWithinValidCommentAndPiPrefix_beforeRootOrSnapshotSeal(
            @org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        for(boolean cancellation:new boolean[]{false,true}) {
            markupStop(Files.createDirectory(temp.resolve("prefix-"+cancellation)),true,cancellation);
        }
    }

    @Test void shouldStopAttachmentDiscoveryWithinValidCommentAndPiSuffix_beforeEofAndSnapshotSeal(
            @org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        for(boolean cancellation:new boolean[]{false,true}) {
            markupStop(Files.createDirectory(temp.resolve("suffix-"+cancellation)),false,cancellation);
        }
    }

    @Test void shouldNotSwallowCheckedCancellationAsXmlCandidate_duringClassification(
            @org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        Path xml=markupSource(temp,true);Path durable=Files.createDirectory(temp.resolve("job"));
        Path pending=durable.resolve("stopped.pending");Path copied=pending.resolve("members/000000000001.bin");
        long sourceBytes=Files.size(xml);var probes=new AtomicInteger();
        try {
            var failure=org.junit.jupiter.api.Assertions.assertThrows(
                    io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException.class,
                    ()->new CdsSnapshotService().snapshot(xml.getParent(),xml.getParent(),durable,"stopped",
                            CdsInputLimitsDto.defaults(),()->false,()->{
                                try {
                                    if(Files.isRegularFile(copied) && Files.size(copied)==sourceBytes && probes.incrementAndGet()==3)
                                        Thread.currentThread().interrupt();
                                } catch(java.io.IOException failed){throw new java.io.UncheckedIOException(failed);}
                            }));
            assertThat(failure.code()).isEqualTo("CDS_INPUT_CANCELLED");
        } finally {Thread.interrupted();}
        assertThat(Files.exists(durable.resolve("stopped"))).isFalse();
        assertThat(Files.exists(pending.resolve("snapshot.seal"))).isFalse();
        try(var names=Files.list(pending.resolve("names"))) {assertThat(names.count()).isZero();}
        assertThat(Files.mismatch(xml,copied)).isEqualTo(-1);
    }

    private static Path markupSource(Path temp,boolean prefix) throws Exception {
        Path sources=Files.createDirectory(temp.resolve("sources"));Path xml=sources.resolve("source.xml");
        byte[] body;
        try(var input=CdsImportExecutionControlUnitTest.class.getResourceAsStream("/demographic/cds-import-summary.xml")) {
            body=new String(input.readNBytes(65536),StandardCharsets.UTF_8)
                    .replaceFirst("^<\\?xml[^>]*>\\s*","").replaceAll(">\\s+<","><").getBytes(StandardCharsets.UTF_8);
        }
        byte[] markup="<!--bounded--><?probe safe?>".getBytes(StandardCharsets.UTF_8);
        try(var output=new java.io.BufferedOutputStream(Files.newOutputStream(xml),65536)) {
            if(!prefix)output.write(body);
            for(int index=0;index<(prefix?2048:262144);index++)output.write(markup);
            if(prefix)output.write(body);
        }
        // The short markup tokens surround a real pinned-schema fixture; no large scalar is fabricated.
        var valid=new CdsXmlParser().parse(xml,temp.resolve("schema-check"),CdsInputLimitsDto.defaults(),()->false,record->{});
        assertThat(valid.records()).isPositive();
        return xml;
    }

    private static void markupStop(Path temp,boolean prefix,boolean cancellation) throws Exception {
        Path xml=markupSource(temp,prefix);Path sources=xml.getParent();
        Path durable=Files.createDirectory(temp.resolve("job"));Path pending=durable.resolve("stopped.pending");
        Path copied=pending.resolve("members/000000000001.bin");long sourceBytes=Files.size(xml);
        var owner=owner();var fields=new CdsFieldParser();var clock=new AtomicLong();var probes=new AtomicInteger();
        int stopAfter=prefix?3:256;
        try(var control=scope(owner,fields,clock,10000,()->{
            try {
                if(Files.isRegularFile(copied) && Files.size(copied)==sourceBytes && probes.incrementAndGet()==stopAfter) {
                    if(cancellation) {
                        clock.set(250_000_000L);
                        when(owner.control("run")).thenReturn(new CdsImportControlDto(true,true,7,true,true));
                    } else throw new CdsImportException("IMPORT_ATTEMPT_TIMEOUT");
                }
            } catch(java.io.IOException failed){throw new java.io.UncheckedIOException(failed);}
        })) {
            assertThatThrownBy(()->new CdsSnapshotService().snapshot(prefix?sources:xml,sources,durable,
                    "stopped",CdsInputLimitsDto.defaults(),()->false,()->control.check(false)))
                    .hasMessage(cancellation?"IMPORT_CANCELLED":"IMPORT_ATTEMPT_TIMEOUT");
        }
        assertThat(probes.get()).isEqualTo(stopAfter);
        assertThat(Files.exists(durable.resolve("stopped"))).isFalse();
        assertThat(Files.exists(pending.resolve("snapshot.seal"))).isFalse();
        assertThat(Files.mismatch(xml,copied)).isEqualTo(-1);
        if(prefix)try(var names=Files.list(pending.resolve("names"))) {
            assertThat(names.count()).isZero(); // Root classification never reached its durable name marker.
        }
        verify(owner,times(cancellation?2:1)).control("run");
    }

    private static CdsImportWorkDto publication(Planning f,Path temp) throws Exception {
        var records=new ArrayList<CdsRecordDto>();Path folder=temp.resolve("publication");
        var parsed=new CdsXmlParser().parse(f.source,folder,CdsInputLimitsDto.defaults(),()->false,records::add);
        return new CdsImportWorkDto(records.getFirst(),f.snapshot,"source.xml",parsed.sourceSha256(),
                folder.resolve("parse-result.json"),f.document.actor(),f.document.command());
    }

    private static Planning planning(Path temp,long blobBytes) throws Exception {
        Path sources=Files.createDirectory(temp.resolve("sources"));Path source=sources.resolve("source.xml");
        try(var input=CdsImportExecutionControlUnitTest.class.getResourceAsStream("/demographic/cds-import-summary.xml")) {
            Files.writeString(source,new String(input.readNBytes(65536),StandardCharsets.UTF_8));
        }
        if(blobBytes>0)try(var output=Files.newOutputStream(sources.resolve("blob.bin"))) {
            byte[] buffer=new byte[65536];for(long bytes=0;bytes<blobBytes;bytes+=buffer.length)output.write(buffer,0,(int)Math.min(buffer.length,blobBytes-bytes));
        }
        Path root=Files.createDirectory(temp.resolve("job"));var snapshots=spy(new CdsSnapshotService());
        var snapshot=snapshots.snapshot(sources,sources,root,"snapshot",CdsInputLimitsDto.defaults(),()->false);
        var configuration=mock(CdsImportConfigurationService.class);when(configuration.limits()).thenReturn(CdsInputLimitsDto.defaults());
        var policy=new CdsImportConfigurationService(new Properties());
        when(configuration.normalizationPolicyDigest(any())).thenAnswer(call->policy.normalizationPolicyDigest(call.getArgument(0)));
        var command=new CdsImportCommand(source,"submission","1",null,null,"source","omd-5.2",false,false,false,0,null,false);
        var actor=new CdsImportActorDto("operator","1","1",null,null,null);
        String rules=CdsImportJobService.rulesetDigest(command,configuration);
        var document=new CdsImportJobDocumentDto(command,actor,snapshot,null,root,rules);
        var ledger=mock(CdsImportLedgerService.class);
        when(ledger.status("run")).thenReturn(new CdsImportJobDto("run","operator","1","source","omd-5.2","PREFLIGHTING",
                snapshot.sha256(),CdsImportJobService.optionsDigest(command),rules,null,0,0,7,false,Instant.EPOCH,Instant.EPOCH,0,null,null));
        when(ledger.planPatient(anyString(),anyLong(),anyString(),anyString(),anyString(),anyString())).thenAnswer(call->
                new CdsImportPatientDto(call.getArgument(2),call.getArgument(3),call.getArgument(4),
                        CdsImportKeys.hash("source",call.getArgument(3),call.getArgument(4)),null,"PLANNED",false,0));
        var identity=mock(CdsPatientIdentityResolver.class);
        when(identity.source(any(),any())).thenReturn(new CdsPatientIdentityDto("source-id","First","Last",LocalDate.of(1971,3,4),"123","ON","F","AC"));
        when(identity.resolve(any(),any())).thenReturn(new CdsIdentityDecisionDto(CdsIdentityDecisionDto.Outcome.NO_MATCH,null));
        var nativeRecords=mock(CdsImportNativeService.class);var fields=new CdsFieldParser();var files=mock(CdsImportFileService.class);
        var identityRegistry=mock(CdsPreflightIdentityService.class);
        when(identityRegistry.register(anyString(),anyLong(),anyString(),anyString(),any(),anyBoolean())).thenReturn(true);
        var jobPaths=mock(CdsImportJobService.class);when(jobPaths.root("run")).thenReturn(temp);
        var planner=new CdsImportPlanningService(ledger,configuration,snapshots,new CdsXmlParser(),fields,identity,
                nativeRecords,mock(CdsCourseService.class),files,jobPaths,identityRegistry);
        var jobs=new CdsImportJobService(ledger,configuration,snapshots,mock(CdsUploadService.class));
        clearInvocations(ledger,snapshots);
        return new Planning(ledger,planner,jobs,document,snapshot,source,fields,files,nativeRecords,snapshots);
    }
    private record Planning(CdsImportLedgerService ledger,CdsImportPlanningService planner,CdsImportJobService jobs,
            CdsImportJobDocumentDto document,CdsSnapshotDto snapshot,Path source,CdsFieldParser fields,CdsImportFileService files,
            CdsImportNativeService nativeRecords,CdsSnapshotService snapshots) {}

    private static CdsImportCoordinatorService.Owner owner() {
        var owner=mock(CdsImportCoordinatorService.Owner.class);when(owner.control("run")).thenReturn(VALID);return owner;
    }
    private static CdsImportExecutionControl scope(CdsImportCoordinatorService.Owner owner,CdsFieldParser fields,
            AtomicLong clock,long millis,Runnable budget) {
        return new CdsImportExecutionControl("run",7,owner,"IMPORT_FILE_VERIFICATION_TIMEOUT",millis,clock::get,budget,fields);
    }
}
