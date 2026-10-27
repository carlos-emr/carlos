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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real filesystem fault shapes; mocked metadata describes only the proven owning database boundary. */
class CdsImportCleanupServiceUnitTest {
    @TempDir Path temporary;
    private final String run=UUID.randomUUID().toString(),unit="a".repeat(64),digest="b".repeat(64);
    private final long epoch=2;
    private CdsImportLedgerService ledger;
    private CdsImportFileService files;
    private CdsImportConfigurationService configuration;
    private CdsImportCleanupService cleanup;
    private Path document,job,current;
    private final AtomicReference<List<CdsImportFileOwnershipDto>> owned=new AtomicReference<>(List.of());

    @BeforeEach void setUp() throws Exception {
        ledger=mock(CdsImportLedgerService.class);files=mock(CdsImportFileService.class);configuration=mock(CdsImportConfigurationService.class);
        Path staging=directory(temporary.resolve("staging"));job=directory(staging.resolve("job"));document=directory(temporary.resolve("documents"));
        Path request=job.resolve("job.json");Files.writeString(request,"{}");Files.writeString(job.resolve("accepted-source.xml"),"accepted immutable source");
        current=directory(job.resolve("reports-"+epoch+"-"+UUID.randomUUID()));
        new ObjectMapper().writeValue(current.resolve("owner.json").toFile(),Map.of("protocol",1,"runId",run,"ownerEpoch",epoch,"directory",current.getFileName().toString()));
        when(configuration.stagingRoot()).thenReturn(staging);when(configuration.limits()).thenReturn(CdsInputLimitsDto.defaults());when(ledger.snapshotLocation(run)).thenReturn(request.toString());
        when(ledger.status(run)).thenReturn(new CdsImportJobDto(run,"operator","999998","fixture","omd-5.2","INCOMPLETE",digest,digest,digest,digest,1,1,epoch,false,Instant.EPOCH,Instant.EPOCH,0,null,null));
        when(ledger.unitStatus(run,unit)).thenReturn(new CdsImportUnitDto("patient",unit,"Reports",1,digest,"opaque","EXPECTED",null,null));
        when(files.ownedFiles(eq(run),anyString(),eq(200))).thenAnswer(invocation -> invocation.<String>getArgument(1).isEmpty()?owned.get():List.of());
        cleanup=new CdsImportCleanupService(ledger,files,configuration,mock(CdsUploadService.class),mock(CdsPreacceptService.class));
    }

    @Test void retainsAcceptedSourceAndUnreferencedResumablePublication() throws Exception {
        byte[] content="clinical report".getBytes(java.nio.charset.StandardCharsets.UTF_8);String hash=hash(content);
        Path nativeFile=document.resolve(filename(hash));Files.write(nativeFile,content);
        owned.set(List.of(new CdsImportFileOwnershipDto(file(nativeFile,hash,content.length),0)));
        try(MockedStatic<PathValidationUtils> paths=mockStatic(PathValidationUtils.class,CALLS_REAL_METHODS)) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(document.toFile());
            var result=cleanup.reconcile(run,epoch,current);
            assertThat(result.state()).isEqualTo("COMPLETE");assertThat(result.retained().get("RESUMABLE_NATIVE_PUBLICATION").artifacts()).isEqualTo(1);
            assertThat(Files.readAllBytes(nativeFile)).isEqualTo(content);assertThat(Files.readString(job.resolve("accepted-source.xml"))).isEqualTo("accepted immutable source");
            verify(files,never()).beginRetirement(anyString(),anyLong(),anyString());
        }
    }

    @Test void reconcilesLostRegistrationAndRemovesOnlyPrivateAliasAndJournal() throws Exception {
        byte[] content="source representation".getBytes(java.nio.charset.StandardCharsets.UTF_8);String hash=hash(content);
        Path operation=operation(content,hash,run);Path payload=operation.resolve("payload.partial"),destination=document.resolve(filename(hash));Files.createLink(destination,payload);
        doAnswer(invocation -> {var file=file(destination,hash,content.length);owned.set(List.of(new CdsImportFileOwnershipDto(file,0)));return file;})
                .when(files).reconcilePublication(run,epoch,destination.toString(),hash,content.length);
        try(MockedStatic<PathValidationUtils> paths=mockStatic(PathValidationUtils.class,CALLS_REAL_METHODS)) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(document.toFile());
            var result=cleanup.reconcile(run,epoch,current);
            assertThat(result.state()).isEqualTo("COMPLETE");assertThat(result.removedDirectories()).isEqualTo(1);
            assertThat(Files.exists(operation)).isFalse();assertThat(Files.readAllBytes(destination)).isEqualTo(content);
            assertThat(result.removed().get("PUBLISHED_PRIVATE_ALIAS").logicalBytes()).isEqualTo((long)content.length);
            verify(files).reconcilePublication(run,epoch,destination.toString(),hash,content.length);
        }
    }

    @Test void removesOwnedUnpublishedPartialButPreservesUnknownOwnership() throws Exception {
        byte[] content={1,2,3};String hash=hash(content);Path known=operation(content,hash,run),unknown=operation(content,hash,UUID.randomUUID().toString());
        try(MockedStatic<PathValidationUtils> paths=mockStatic(PathValidationUtils.class,CALLS_REAL_METHODS)) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(document.toFile());
            var result=cleanup.reconcile(run,epoch,current);
            assertThat(result.state()).isEqualTo("FAILED");assertThat(Files.exists(known)).isFalse();
            assertThat(Files.readAllBytes(unknown.resolve("payload.partial"))).isEqualTo(content);assertThat(Files.exists(unknown.resolve("owner.json"))).isTrue();
            assertThat(result.safeCodes()).contains("CLEANUP_PRIVATE_OPERATION_REQUIRES_REVIEW");
            assertThat(result.retained().get("UNCERTAIN_PRIVATE_OPERATION").logicalBytes()).isNull();
        }
    }

    @Test void retainsRecoveryPayloadIfRegisteredFinalFileIsMissing() throws Exception {
        byte[] content={4,5,6};String hash=hash(content);Path operation=operation(content,hash,run),destination=document.resolve(filename(hash));
        when(files.findLocation(destination.toString())).thenReturn(file(destination,hash,content.length));
        try(MockedStatic<PathValidationUtils> paths=mockStatic(PathValidationUtils.class,CALLS_REAL_METHODS)) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(document.toFile());
            var result=cleanup.reconcile(run,epoch,current);
            assertThat(result.state()).isEqualTo("FAILED");assertThat(Files.readAllBytes(operation.resolve("payload.partial"))).isEqualTo(content);
            assertThat(Files.exists(operation.resolve("owner.json"))).isTrue();verify(files,never()).reconcilePublication(anyString(),anyLong(),anyString(),anyString(),anyLong());
        }
    }

    @Test void changedFinalBytesNeverAdoptOrDeleteRecoveryEvidence() throws Exception {
        byte[] content={7,8,9};String hash=hash(content);Path operation=operation(content,hash,run),destination=document.resolve(filename(hash));Files.write(destination,new byte[]{9,8,7});
        try(MockedStatic<PathValidationUtils> paths=mockStatic(PathValidationUtils.class,CALLS_REAL_METHODS)) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(document.toFile());
            var result=cleanup.reconcile(run,epoch,current);
            assertThat(result.state()).isEqualTo("FAILED");assertThat(Files.readAllBytes(operation.resolve("payload.partial"))).isEqualTo(content);
            assertThat(Files.readAllBytes(destination)).isEqualTo(new byte[]{9,8,7});verify(files,never()).reconcilePublication(anyString(),anyLong(),anyString(),anyString(),anyLong());
        }
    }

    @Test void countsRemovedZeroBytePayloadAsAnArtifact() throws Exception {
        byte[] content={};Path operation=operation(content,hash(content),run);
        try(MockedStatic<PathValidationUtils> paths=mockStatic(PathValidationUtils.class,CALLS_REAL_METHODS)) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(document.toFile());
            var result=cleanup.reconcile(run,epoch,current);
            assertThat(result.state()).isEqualTo("COMPLETE");assertThat(Files.exists(operation)).isFalse();
            assertThat(result.removed().get("ABANDONED_PRIVATE_PAYLOAD").artifacts()).isEqualTo(1);
            assertThat(result.removed().get("ABANDONED_PRIVATE_PAYLOAD").logicalBytes()).isZero();
        }
    }

    @Test void reportBudgetExpiryRetainsPrivateRecoveryEvidence_beforeAnyDeletion() throws Exception {
        byte[] content={3,4,5};String hash=hash(content);Path operation=operation(content,hash,run),destination=document.resolve(filename(hash));
        var elapsed=new java.util.concurrent.atomic.AtomicLong();
        when(files.findLocation(destination.toString())).thenAnswer(call->{elapsed.set(100);return null;});
        Runnable budget=()->{if(elapsed.get()>=100)throw new CdsImportException("IMPORT_REPORT_GENERATION_TIMEOUT");};
        try(MockedStatic<PathValidationUtils> paths=mockStatic(PathValidationUtils.class,CALLS_REAL_METHODS)) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(document.toFile());
            assertThatThrownBy(()->cleanup.reconcile(run,epoch,current,budget)).hasMessage("IMPORT_REPORT_GENERATION_TIMEOUT");
            assertThat(Files.readAllBytes(operation.resolve("payload.partial"))).isEqualTo(content);
            assertThat(Files.exists(operation.resolve("owner.json"))).isTrue();
            assertThat(Files.readString(job.resolve("accepted-source.xml"))).isEqualTo("accepted immutable source");
            verify(files,never()).reconcilePublication(anyString(),anyLong(),anyString(),anyString(),anyLong());
        }
    }

    private Path operation(byte[] content,String hash,String ownerRun) throws Exception {
        Path pending=directory(document.resolve(".cds-pending")),runRoot=directory(pending.resolve(run));String id=UUID.randomUUID().toString();Path operation=directory(runRoot.resolve(id));
        var owner=new CdsDocumentPublicationService.PendingOwner(1,ownerRun,epoch,unit,"text","txt",hash,content.length,filename(hash),id,System.currentTimeMillis());
        new ObjectMapper().writeValue(operation.resolve("owner.json").toFile(),owner);Files.write(operation.resolve("payload.partial"),content);return operation;
    }
    private static Path directory(Path path) throws Exception {if(!Files.exists(path))Files.createDirectory(path,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));return path;}
    private CdsImportFileDto file(Path path,String hash,long bytes){return new CdsImportFileDto("file",path.toString(),hash,bytes,"PUBLISHED",1);}
    private String filename(String hash){return "cds-"+unit+"-text-"+hash+".txt";}
    private static String hash(byte[] bytes){var hash=CdsPaths.sha256();hash.update(bytes);return CdsPaths.hex(hash.digest());}
}
