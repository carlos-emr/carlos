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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportKeys;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsPreacceptServiceUnitTest {
    @TempDir Path temp;
    private final CdsImportActorDto actor=new CdsImportActorDto("operator","1","1",null,null,null);

    @Test void shouldDeleteOnlyExpiredKnownUnacceptedCopies_andLeaveOriginalSourceUntouched() throws Exception {
        Fixture fixture=fixture("expired");create(fixture,70);
        long deleted=0;boolean complete=false;
        for(int i=0;i<20 && !complete;i++) {
            var status=fixture.cleanup.cleanup(32,16);deleted+=((Number)status.get("deletedFiles")).longValue();
            assertEquals(0L,status.get("uncertain"));complete=Boolean.TRUE.equals(status.get("fullPassComplete"));
        }
        assertTrue(complete);assertTrue(deleted>=70);assertFalse(Files.exists(fixture.root));
        assertEquals("user original",Files.readString(fixture.original));
    }

    @Test void shouldRetainActiveCopy_whenJournalTtlHasExpiredButOwnerStillHoldsLock() throws Exception {
        Fixture fixture=fixture("busy");
        try(var process=fixture.capture.enter(fixture.root);FileChannel channel=FileChannel.open(fixture.root.resolve("accept.lock"),StandardOpenOption.WRITE);FileLock owner=channel.lock()) {
            fixture.capture.begin(actor,fixture.command,fixture.root,owner);
            assertEquals(1L,fixture.cleanup.cleanup(32,200).get("busy"));assertTrue(Files.exists(fixture.root));
            Path output=temp.resolve("lock-probe.log");
            String javaBinary=Path.of(System.getProperty("java.home"),"bin","java").toString();
            String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
            Process probe=new ProcessBuilder(javaBinary,"-cp",cp,LockProbe.class.getName(),fixture.root.resolve("accept.lock").toString())
                    .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            assertTrue(probe.waitFor(10,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,probe.exitValue());
            assertTrue(Files.readString(output).contains("LOCK_REMAINS_BUSY"));
        }
    }

    @Test void shouldRetainUnknownCommitOutcome_evenWhenDatabaseRowIsNotVisibleAfterTtl() throws Exception {
        Fixture fixture=fixture("unknown");
        try(var process=fixture.capture.enter(fixture.root);FileChannel channel=FileChannel.open(fixture.root.resolve("accept.lock"),StandardOpenOption.WRITE);FileLock owner=channel.lock()) {
            var journal=fixture.capture.begin(actor,fixture.command,fixture.root,owner);
            byte[] bytes="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Path job=Files.write(fixture.root.resolve("job-"+CdsPaths.hex(CdsPaths.sha256().digest(bytes))+".json"),bytes);
            fixture.capture.accepting(journal,job,"a".repeat(64),owner);
        }
        var status=fixture.cleanup.cleanup(32,200);
        assertEquals("REVIEW_REQUIRED",status.get("state"));assertEquals(1L,status.get("uncertain"));assertTrue(Files.exists(fixture.root));
    }

    @Test void shouldReadBackAcceptedRow_andNeverDeleteItsSourceCopies() throws Exception {
        Fixture fixture=fixture("accepted");create(fixture,2);
        when(fixture.ledger.findSubmission("operator","accepted")).thenReturn(new CdsImportJobDto("run","operator","1","source","omd-5.2","ACCEPTED",
                "a".repeat(64),"b".repeat(64),"c".repeat(64),null,0,0,0,false,Instant.EPOCH,Instant.EPOCH,0,null,null));
        var status=fixture.cleanup.cleanup(32,200);
        assertEquals(1L,status.get("retainedAccepted"));assertTrue(Files.exists(fixture.root.resolve("accepted.seal")));
        assertTrue(Files.exists(fixture.root.resolve("snapshot.pending/members/000000000001.bin")));
    }

    @Test void shouldRetainUnknownFileAndSymlinkTargets_andReportManualReview() throws Exception {
        Fixture fixture=fixture("unknown-entry");create(fixture,1);
        Path secret=Files.writeString(temp.resolve("outside-secret"),"never unlink original");
        Files.createSymbolicLink(fixture.root.resolve("unrecognized"),secret);
        var status=fixture.cleanup.cleanup(32,200);
        assertEquals("REVIEW_REQUIRED",status.get("state"));assertEquals(1L,status.get("uncertain"));
        assertEquals("never unlink original",Files.readString(secret));
        Path retired=fixture.staging.resolve("preaccept-retired-"+fixture.generation[0]);
        assertTrue(Files.isSymbolicLink(retired.resolve("unrecognized")));
    }

    @Test void shouldPreserveUncertainCoverageAcrossBoundedBatches_untilFullCatalogPassEnds() throws Exception {
        Fixture first=fixture("first"),second=sibling(first,"second");create(first,1);create(second,1);
        Files.writeString(first.root.resolve("unexpected"),"unknown owned status");
        var one=first.cleanup.cleanup(1,200);assertEquals(1L,one.get("uncertain"));assertEquals(false,one.get("fullPassComplete"));
        var two=first.cleanup.cleanup(1,200);assertEquals(1L,two.get("uncertain"));assertEquals(true,two.get("fullPassComplete"));
        assertEquals("REVIEW_REQUIRED",two.get("state"));
    }

    @Test void shouldRefuseDamagedCatalog_withoutTouchingKnownSourceCopies() throws Exception {
        Fixture fixture=fixture("damaged");create(fixture,1);
        Path index=fixture.staging.resolve(".preaccept-index.jsonl");byte[] bytes=Files.readAllBytes(index);bytes[bytes.length/2]^=1;Files.write(index,bytes);
        var status=fixture.cleanup.cleanup(32,200);
        assertEquals("REVIEW_REQUIRED",status.get("state"));assertEquals(false,status.get("fullPassComplete"));
        assertTrue(Files.exists(fixture.root.resolve("snapshot.pending/members/000000000001.bin")));
    }

    @Test void shouldDiscardOnlyUncommittedIndexTail_beforeAnotherSourceCopyCanStart() throws Exception {
        Fixture first=fixture("one");create(first,1);
        Files.writeString(first.staging.resolve(".preaccept-index.jsonl"),"interrupted append",StandardOpenOption.APPEND);
        Fixture second=sibling(first,"two");create(second,1);
        var status=first.cleanup.cleanup(32,200);
        assertEquals("COMPLETE",status.get("state"));assertEquals(2L,status.get("deletedRoots"));
        assertEquals("user original",Files.readString(first.original));
    }

    @Test @Timeout(value=10,threadMode=Timeout.ThreadMode.SEPARATE_THREAD)
    void shouldTimeOutOnCrossProcessIndexLock_withoutChangingCommittedIndexAndRetrySameJournal() throws Exception {
        Fixture first=fixture("prior",200);create(first,1);Fixture next=sibling(first,"contended");
        Path index=first.staging.resolve(".preaccept-index.jsonl"),seal=first.staging.resolve(".preaccept-index.seal");
        byte[] indexBefore=Files.readAllBytes(index),sealBefore=Files.readAllBytes(seal);
        byte[] journal;
        try(IndexHolder holder=holdIndex(first.staging)) {
            long started=System.nanoTime();
            try(var process=next.capture.enter(next.root);FileChannel channel=FileChannel.open(next.root.resolve("accept.lock"),StandardOpenOption.WRITE);FileLock owner=channel.lock()) {
                CdsImportException failure=assertThrows(CdsImportException.class,()->next.capture.begin(actor,next.command,next.root,owner));
                assertEquals("PREACCEPT_INDEX_TIMEOUT",failure.getCode());assertTrue(owner.isValid());
            }
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)<2000,"Contended admission must return within its finite wait");
            journal=Files.readAllBytes(next.root.resolve("acceptance.seal"));
            assertFalse(Files.exists(next.root.resolve("indexed.seal")));
            assertFalse(Files.exists(next.root.resolve("accepting.seal")));
            assertArrayEquals(indexBefore,Files.readAllBytes(index));assertArrayEquals(sealBefore,Files.readAllBytes(seal));
            assertEquals("user original",Files.readString(first.original));assertTrue(holder.process.isAlive());
        }
        begin(next);begin(next);
        assertArrayEquals(journal,Files.readAllBytes(next.root.resolve("acceptance.seal")));
        try(var lines=Files.lines(index)){assertEquals(2,lines.count(),"Retry must append exactly once for the retained generation");}
        verifyNoInteractions(first.ledger);
    }

    @Test void shouldInterruptSameJvmQueuedAdmission_andReleaseOnlyItsOwnCoordination() throws Exception {
        Fixture first=fixture("waiting-file",5000),second=sibling(first,"waiting-jvm");
        AtomicReference<Throwable> firstFailure=new AtomicReference<>(),secondFailure=new AtomicReference<>();
        AtomicBoolean firstInterrupted=new AtomicBoolean(),secondInterrupted=new AtomicBoolean();
        Thread fileWaiter=admission(first,firstFailure,firstInterrupted),jvmWaiter=admission(second,secondFailure,secondInterrupted);
        try(IndexHolder holder=holdIndex(first.staging)) {
            try {
                fileWaiter.start();awaitFrame(fileWaiter,"acquireIndexFileLock");
                jvmWaiter.start();awaitFrame(jvmWaiter,"acquireIndexMutex");
                jvmWaiter.interrupt();jvmWaiter.join(2000);
                assertFalse(jvmWaiter.isAlive());assertCancelled(secondFailure.get());assertTrue(secondInterrupted.get());
                assertTrue(fileWaiter.isAlive(),"Cancelling the JVM waiter must not release the current process owner");
                assertTrue(holder.process.isAlive(),"Cancelling admission must not terminate the external lock owner");
                fileWaiter.interrupt();fileWaiter.join(2000);
                assertFalse(fileWaiter.isAlive());assertCancelled(firstFailure.get());assertTrue(firstInterrupted.get());
                assertFalse(Files.exists(first.staging.resolve(".preaccept-index.jsonl")));
            } finally {
                fileWaiter.interrupt();jvmWaiter.interrupt();fileWaiter.join(2000);jvmWaiter.join(2000);
            }
        }
        begin(first);begin(second);
        try(var lines=Files.lines(first.staging.resolve(".preaccept-index.jsonl"))){assertEquals(2,lines.count());}
    }

    @Test void shouldBoundOverlappingSameJvmFileLock_withoutReleasingTheExistingLock() throws Exception {
        Fixture fixture=fixture("overlapping",100);
        try(FileChannel channel=FileChannel.open(fixture.staging.resolve(".preaccept-index.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock held=channel.lock()) {
            CdsImportException failure=assertThrows(CdsImportException.class,()->begin(fixture));
            assertEquals("PREACCEPT_INDEX_TIMEOUT",failure.getCode());assertTrue(held.isValid());
            assertFalse(Files.exists(fixture.staging.resolve(".preaccept-index.jsonl")));
        }
        begin(fixture);assertTrue(Files.exists(fixture.root.resolve("indexed.seal")));
    }

    private CdsPreacceptService.Ownership begin(Fixture fixture) throws Exception {
        try(var process=fixture.capture.enter(fixture.root);FileChannel channel=FileChannel.open(fixture.root.resolve("accept.lock"),StandardOpenOption.WRITE);FileLock owner=channel.lock()) {
            return fixture.capture.begin(actor,fixture.command,fixture.root,owner);
        }
    }
    private Thread admission(Fixture fixture,AtomicReference<Throwable> failure,AtomicBoolean interrupted) {
        return new Thread(()->{try{begin(fixture);}catch(Throwable stopped){failure.set(stopped);interrupted.set(Thread.currentThread().isInterrupted());}},"preaccept-index-test");
    }
    private static void assertCancelled(Throwable failure) {
        assertInstanceOf(CdsImportException.class,failure);assertEquals("PREACCEPT_INDEX_CANCELLED",((CdsImportException)failure).getCode());
    }
    private static void awaitFrame(Thread thread,String method) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(System.nanoTime()<deadline) {
            for(StackTraceElement frame:thread.getStackTrace())if(frame.getMethodName().equals(method))return;
            if(!thread.isAlive())fail("Admission exited before entering "+method);
            Thread.sleep(5);
        }
        fail("Admission did not reach "+method);
    }
    private IndexHolder holdIndex(Path staging) throws Exception {
        Path output=temp.resolve("index-holder-"+java.util.UUID.randomUUID()+".log");
        String javaBinary=Path.of(System.getProperty("java.home"),"bin","java").toString();
        String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        Process process=new ProcessBuilder(javaBinary,"-cp",cp,IndexLockHolder.class.getName(),staging.resolve(".preaccept-index.lock").toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        IndexHolder holder=new IndexHolder(process);
        try {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<deadline && process.isAlive()) {
                if(Files.readString(output).contains("INDEX_LOCK_HELD"))return holder;
                Thread.sleep(5);
            }
            fail("External index owner did not acquire the real file lock: "+Files.readString(output));return holder;
        } catch(Throwable failure){holder.close();throw failure;}
    }
    private static final class IndexHolder implements AutoCloseable {
        private final Process process;
        private IndexHolder(Process process){this.process=process;}
        @Override public void close() throws Exception {
            process.getOutputStream().close();
            if(!process.waitFor(5,TimeUnit.SECONDS)){process.destroyForcibly();assertTrue(process.waitFor(5,TimeUnit.SECONDS));}
            assertEquals(0,process.exitValue());
        }
    }

    private void create(Fixture fixture,int files) throws Exception {
        try(var process=fixture.capture.enter(fixture.root);FileChannel channel=FileChannel.open(fixture.root.resolve("accept.lock"),StandardOpenOption.WRITE);FileLock owner=channel.lock()) {
            var journal=fixture.capture.begin(actor,fixture.command,fixture.root,owner);fixture.generation[0]=journal.generation();
            Path pending=Files.createDirectory(fixture.root.resolve("snapshot.pending")),members=Files.createDirectory(pending.resolve("members"));
            Files.createDirectory(pending.resolve("names"));Files.writeString(pending.resolve("files.jsonl"),"incomplete inventory");
            for(int i=1;i<=files;i++)Files.writeString(members.resolve(String.format(java.util.Locale.ROOT,"%012d.bin",i)),"owned copy");
        }
    }
    private Fixture fixture(String key) throws Exception {
        return fixture(key,5000);
    }
    private Fixture fixture(String key,long indexLockWaitMillis) throws Exception {
        Path staging=Files.createDirectory(temp.resolve("staging"),java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))),original=Files.writeString(temp.resolve("original.xml"),"user original");
        var ledger=mock(CdsImportLedgerService.class);var configuration=mock(CdsImportConfigurationService.class);
        when(configuration.stagingRoot()).thenReturn(staging);when(configuration.number(eq("cds.import.preacceptRetentionMillis"),anyLong())).thenReturn(1000L);
        var capture=new CdsPreacceptService(configuration,ledger,Clock.fixed(Instant.ofEpochMilli(1000),ZoneOffset.UTC),indexLockWaitMillis);
        var cleanup=new CdsPreacceptService(configuration,ledger,Clock.fixed(Instant.ofEpochMilli(10000),ZoneOffset.UTC));
        return make(staging,original,key,ledger,capture,cleanup);
    }
    private Fixture sibling(Fixture first,String key) throws Exception {return make(first.staging,first.original,key,first.ledger,first.capture,first.cleanup);}
    private Fixture make(Path staging,Path original,String key,CdsImportLedgerService ledger,CdsPreacceptService capture,CdsPreacceptService cleanup) throws Exception {
        Path root=Files.createDirectory(staging.resolve("job-"+CdsImportKeys.hash(actor.operator(),key)),java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")));Files.createFile(root.resolve("accept.lock"));
        var command=new CdsImportCommand(original,key,"1",null,null,"source","omd-5.2",false,false,false,0,null,false);
        return new Fixture(staging,root,original,command,ledger,capture,cleanup,new String[1]);
    }
    private record Fixture(Path staging,Path root,Path original,CdsImportCommand command,CdsImportLedgerService ledger,CdsPreacceptService capture,CdsPreacceptService cleanup,String[] generation) { }
    public static final class LockProbe {
        public static void main(String[] args) throws Exception {
            try(FileChannel file=FileChannel.open(Path.of(args[0]),StandardOpenOption.WRITE);FileLock lock=file.tryLock()) {
                if(lock!=null)throw new AssertionError("Janitor probe released another live process lock");
                System.out.println("LOCK_REMAINS_BUSY");
            }
        }
    }
    public static final class IndexLockHolder {
        public static void main(String[] args) throws Exception {
            try(FileChannel channel=FileChannel.open(Path.of(args[0]),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock ignored=channel.lock()) {
                System.out.println("INDEX_LOCK_HELD");System.out.flush();System.in.read();
            }
        }
    }
}
