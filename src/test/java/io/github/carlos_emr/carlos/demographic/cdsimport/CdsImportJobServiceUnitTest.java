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
package io.github.carlos_emr.carlos.demographic.cdsimport;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportJobCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsUploadDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsUploadChunkDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsImportJobServiceUnitTest {
    @TempDir Path temp;
    private CdsImportLedgerService ledger;
    private CdsImportJobService service;
    private CdsImportConfigurationService configuration;
    private CdsUploadService uploads;
    private Path source;
    private boolean webUpload;
    private final AtomicReference<CdsImportJobCommand> accepted=new AtomicReference<>();
    private final AtomicReference<CdsImportJobDto> job=new AtomicReference<>();
    private final CdsImportActorDto actor=new CdsImportActorDto("operator","1","1",null,null,null);

    @BeforeEach void prepare() throws Exception {
        Path sources=Files.createDirectory(temp.resolve("sources"));
        source=Files.writeString(sources.resolve("patient.xml"),"<OmdCds xmlns='cds'><PatientRecord/></OmdCds>");
        ledger=mock(CdsImportLedgerService.class);
        configuration=mock(CdsImportConfigurationService.class);
        when(configuration.stagingRoot()).thenReturn(temp.resolve("private"));
        when(configuration.allowedRoot()).thenReturn(sources);
        when(configuration.value("cds.import.uploadRoot","")).thenReturn("");
        when(configuration.limits()).thenReturn(CdsInputLimitsDto.defaults());
        var policy=new CdsImportConfigurationService(new java.util.Properties());
        when(configuration.normalizationPolicyDigest(any())).thenAnswer(call->policy.normalizationPolicyDigest(call.getArgument(0)));
        when(ledger.findSubmission(anyString(),anyString())).thenAnswer(call->accepted.get()!=null
                && accepted.get().submissionKey().equals(call.getArgument(1)) ? job.get() : null);
        when(ledger.accept(any())).thenAnswer(call->{
            CdsImportJobCommand command=call.getArgument(0); accepted.set(command);
            var value=new CdsImportJobDto("run",command.operator(),command.providerNo(),command.sourceNamespace(),command.profile(),
                    "CLI_ACCEPTED",command.sourceDigest(),command.optionsDigest(),command.rulesetDigest(),null,0,0,0,false,
                    Instant.EPOCH,Instant.EPOCH,0,null,null);
            job.set(value); return value;
        });
        when(ledger.status("run")).thenAnswer(call->job.get());
        when(ledger.snapshotLocation("run")).thenAnswer(call->accepted.get().snapshotLocation());
        when(ledger.jobBinding("run")).thenAnswer(call->new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobBindingDto(job.get(),ledger.snapshotLocation("run")));
        uploads=mock(CdsUploadService.class);
        service=new CdsImportJobService(ledger,configuration,new CdsSnapshotService(),uploads);
    }

    @Test void shouldPublishHashedImmutableJobAndSnapshot_beforeDurableAcceptance() throws Exception {
        var command=command(false);
        service.accept(actor,command);
        var document=service.load("run");
        assertEquals(command,document.command()); assertEquals(actor,document.actor());
        assertNotNull(document.snapshot()); assertTrue(accepted.get().cli());
        assertTrue(Path.of(accepted.get().snapshotLocation()).getFileName().toString().matches("job-[0-9a-f]{64}\\.json"));
        assertEquals(document.snapshot().sha256(),service.snapshot("run",document).sha256());
        assertTrue(Files.exists(source));
    }

    @Test void shouldReturnPriorAcceptanceBeforeReadingOriginal_whenCallerRetriesAfterSourceRetirement() throws Exception {
        var command=command(false); var first=service.accept(actor,command);
        Files.delete(source);
        assertEquals(first,service.accept(actor,command));
        verify(ledger,times(1)).accept(any());
        CdsImportException changed=assertThrows(CdsImportException.class,()->service.accept(actor,command(true)));
        assertEquals("SUBMISSION_CONFLICT",changed.getCode());
    }

    @Test void shouldRejectChangedRequestDocument_beforeUsingItsPathsOrOptions() throws Exception {
        service.accept(actor,command(false)); Path metadata=Path.of(accepted.get().snapshotLocation());
        byte[] bytes=Files.readAllBytes(metadata); bytes[bytes.length/2]^=1; Files.write(metadata,bytes);
        CdsImportException changed=assertThrows(CdsImportException.class,()->service.load("run"));
        assertEquals("JOB_DOCUMENT_HASH_MISMATCH",changed.getCode());
    }

    @Test void shouldShareClinicalOptionDigest_betweenPreviewAndActualWithoutSharingAcceptance() {
        assertEquals(CdsImportJobService.optionsDigest(command(false)),CdsImportJobService.optionsDigest(command(true)));
    }

    @Test void shouldPublishSnapshotBeforeRetiringUpload_andResumeAfterOriginalIsGone() throws Exception {
        String upload=webSource(false);
        var command=command(false);service.accept(actor,command);var document=service.load("run");
        assertNull(document.snapshot());
        var snapshot=service.snapshot("run",document);
        verify(uploads).snapshotPublished(actor.operator(),upload,"run",snapshot.sha256());
        Files.delete(source);
        assertEquals(snapshot.sha256(),service.snapshot("run",document).sha256());
        assertEquals(job.get(),service.accept(actor,command));
    }

    @Test void shouldRetainRawUploadAndWithholdPublication_whenChunkProofDiffersFromSnapshot() throws Exception {
        webSource(true);service.accept(actor,command(false));
        CdsImportException failure=assertThrows(CdsImportException.class,()->service.snapshot("run",service.load("run")));
        assertEquals("UPLOADED_SOURCE_CHANGED",failure.getCode());
        verify(uploads,never()).snapshotPublished(anyString(),anyString(),anyString(),anyString());
        assertTrue(Files.exists(source));
    }

    @Test void shouldKeepUploadForActualAttempt_whenOnlyPreviewSnapshotIsPublished() throws Exception {
        String upload=webSource(false);service.accept(actor,command(true));service.snapshot("run",service.load("run"));
        verify(uploads,never()).snapshotPublished(anyString(),anyString(),anyString(),anyString());
        verify(uploads).sourceForAcceptedRun(actor.operator(),upload,"run",command(true).submissionKey(),true);
        verify(uploads,times(1)).claimForSubmission(actor.operator(),upload);
        verify(uploads,times(2)).submitted(actor.operator(),upload,"run",true);
        assertTrue(Files.exists(source));
    }

    @Test void shouldWaitForQueuedPreviewSnapshot_beforeAcceptingActualFromLegacyClaim() throws Exception {
        String upload=webSource(false);
        service.accept(actor,command(true));
        var preview=job.get();
        String previewDocument=accepted.get().snapshotLocation();
        var original=uploads.status(actor.operator(),upload);
        when(uploads.status(actor.operator(),upload)).thenReturn(new CdsUploadDto(original.id(),original.fileName(),
                original.expectedBytes(),original.receivedBytes(),original.chunkBytes(),original.committedChunks(),
                "SUBMITTING",original.manifestSha256(),original.submissionKey(),null,preview.id(),0));
        when(ledger.findSubmission(actor.operator(),command(false).submissionKey())).thenReturn(null);
        var pending=assertThrows(CdsImportException.class,()->service.accept(actor,command(false)));
        assertEquals("UPLOAD_PREVIEW_SNAPSHOT_PENDING",pending.getCode());
        verify(ledger,times(1)).accept(any());
        assertTrue(Files.exists(source));
        service.snapshot("run",service.load("run"));
        when(ledger.snapshotLocation("run")).thenReturn(previewDocument);
        doAnswer(call->{
            CdsImportJobCommand value=call.getArgument(0);
            return new CdsImportJobDto("actual",value.operator(),value.providerNo(),value.sourceNamespace(),value.profile(),
                    "ACCEPTED",value.sourceDigest(),value.optionsDigest(),value.rulesetDigest(),null,0,0,0,false,
                    Instant.EPOCH,Instant.EPOCH,0,null,null);
        }).when(ledger).accept(any());
        var actual=service.accept(actor,command(false));
        assertEquals("actual",actual.id());
        verify(uploads).submitted(actor.operator(),upload,actual.id(),false);
        verify(ledger,times(2)).accept(any());
    }

    @Test void shouldRefuseNewPreview_whenActualAcceptanceAcknowledgementWasLost() throws Exception {
        String upload=webSource(false);
        service.accept(actor,command(false));
        // Model a crash after durable acceptance but before the upload's actual run ID was stored.
        var original=uploads.status(actor.operator(),upload);
        when(uploads.status(actor.operator(),upload)).thenReturn(new CdsUploadDto(original.id(),original.fileName(),
                original.expectedBytes(),original.receivedBytes(),original.chunkBytes(),original.committedChunks(),
                "SUBMITTING",original.manifestSha256(),original.submissionKey(),null,null,0));
        var failure=assertThrows(CdsImportException.class,()->service.accept(actor,command(true)));
        assertEquals("UPLOAD_ACTUAL_ALREADY_ACCEPTED",failure.getCode());
        verify(uploads,times(2)).submitted(actor.operator(),upload,"run",false);
        verify(ledger,times(1)).accept(any());
    }

    @Test void shouldWaitForOrphanPreviewSnapshot_whenPreviewAcceptanceAcknowledgementWasLost() throws Exception {
        String upload=webSource(false);
        service.accept(actor,command(true));
        var original=uploads.status(actor.operator(),upload);
        when(uploads.status(actor.operator(),upload)).thenReturn(new CdsUploadDto(original.id(),original.fileName(),
                original.expectedBytes(),original.receivedBytes(),original.chunkBytes(),original.committedChunks(),
                "SUBMITTING",original.manifestSha256(),original.submissionKey(),null,null,0));
        var failure=assertThrows(CdsImportException.class,()->service.accept(actor,command(false)));
        assertEquals("UPLOAD_PREVIEW_SNAPSHOT_PENDING",failure.getCode());
        verify(uploads,times(2)).submitted(actor.operator(),upload,"run",true);
        verify(ledger,times(1)).accept(any());
        assertTrue(Files.exists(source));
    }

    private String webSource(boolean corruptProof) throws Exception {
        webUpload=true;
        Path uploadsRoot=Files.createDirectory(temp.resolve("uploads"));
        String upload="11111111-1111-1111-1111-111111111111";
        Path folder=Files.createDirectory(uploadsRoot.resolve(upload));
        source=Files.copy(source,folder.resolve("source.xml"));
        byte[] bytes=Files.readAllBytes(source);
        String hash=CdsPaths.hex(CdsPaths.sha256().digest(corruptProof ? new byte[]{1} : bytes));
        byte[] journal=ByteBuffer.allocate(52).putLong(0).putLong(0).putInt(bytes.length).put(HexFormat.of().parseHex(hash)).array();
        String manifest=CdsPaths.hex(CdsPaths.sha256().digest(journal));
        when(configuration.value("cds.import.uploadRoot","")).thenReturn(uploadsRoot.toString());
        when(configuration.uploadRoot()).thenReturn(uploadsRoot);
        when(uploads.status(actor.operator(),upload)).thenReturn(new CdsUploadDto(upload,"patient.xml",bytes.length,bytes.length,
                4L<<20,1,"COMPLETE",manifest,"web-upload-fixed",null,null,0));
        when(uploads.claimForSubmission(actor.operator(),upload)).thenReturn(source);
        when(uploads.sourceForAcceptedRun(eq(actor.operator()),eq(upload),anyString(),anyString(),anyBoolean())).thenReturn(source);
        when(uploads.chunks(actor.operator(),upload,0,100)).thenReturn(List.of(new CdsUploadChunkDto(0,0,bytes.length,hash)));
        return upload;
    }

    private CdsImportCommand command(boolean dryRun) {
        return new CdsImportCommand(source,webUpload ? "web-upload-fixed"+(dryRun ? "-dry-run" : "-import") : "submission","1",null,null,"test-source","omd-5.2",false,dryRun,false,0,null,false);
    }
}
