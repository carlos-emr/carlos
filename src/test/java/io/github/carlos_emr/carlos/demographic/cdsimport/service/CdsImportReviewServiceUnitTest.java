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
import io.github.carlos_emr.carlos.demographic.cdsimport.command.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.*;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@SuppressWarnings("unchecked")
class CdsImportReviewServiceUnitTest {
    @TempDir Path root;
    private static final String RUN="19df2cb9-cc1a-40c6-898c-014242438e25";
    private static final String RECORD="/OmdCds[1]/PatientRecord[1]/Demographics[1]";
    private final CdsImportLedgerService ledger=mock(CdsImportLedgerService.class);
    private final CdsImportJobService jobs=mock(CdsImportJobService.class);
    private final CdsImportAccessService access=mock(CdsImportAccessService.class);
    private final CdsImportReviewDao reviews=mock(CdsImportReviewDao.class);
    private final CdsImportRunDao runs=mock(CdsImportRunDao.class);
    private final CdsImportDiagnosticDao diagnostics=mock(CdsImportDiagnosticDao.class);
    private final CdsSnapshotService snapshots=mock(CdsSnapshotService.class);
    private final CdsCorrectionLookupService corrections=mock(CdsCorrectionLookupService.class);
    private final CdsImportCoordinatorService coordinator=mock(CdsImportCoordinatorService.class);
    private final CdsUnwrittenRevisionReviewService revisions=mock(CdsUnwrittenRevisionReviewService.class);
    private final EntityManager entityManager=mock(EntityManager.class);
    private final TypedQuery<Long> count=mock(TypedQuery.class);
    private final AtomicReference<CdsImportReview> stored=new AtomicReference<>();
    private final Map<String,CdsImportReview> history=new LinkedHashMap<>();
    private final CdsFieldParser fields=new CdsFieldParser();
    private final CdsXmlParser parser=new CdsXmlParser();
    private final CdsImportActorDto actor=new CdsImportActorDto("web-42","T099","T099",42,null,null);
    private final LoggedInInfo viewer=mock(LoggedInInfo.class);
    private CdsImportReviewService service;
    private CdsImportRun run;
    private CdsImportDiagnostic diagnostic;
    private CdsImportJobDocumentDto document;
    private CdsImportJobDto status;

    @BeforeEach
    void prepare() throws Exception {
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var config=mock(CdsImportConfigurationService.class);when(config.limits()).thenReturn(CdsInputLimitsDto.defaults());
        service=new CdsImportReviewService(ledger,jobs,config,access,reviews,runs,diagnostics,snapshots,parser,fields,corrections,coordinator,revisions,manager);
        var field=CdsImportReviewService.class.getDeclaredField("entityManager");field.setAccessible(true);field.set(service,entityManager);
        when(entityManager.createQuery(anyString(),eq(Long.class))).thenReturn(count);
        when(count.setParameter(anyString(),any())).thenReturn(count);when(count.getSingleResult()).thenReturn(0L);
        run=new CdsImportRun();run.setId(RUN);run.setState("REVIEW_REQUIRED");
        when(runs.lock(RUN)).thenReturn(run);when(runs.get(RUN)).thenReturn(run);
        diagnostic=new CdsImportDiagnostic();diagnostic.setId(1L);diagnostic.setRunId(RUN);
        diagnostic.setStage("PREFLIGHT");diagnostic.setBlocking(true);diagnostic.setSourcePath(RECORD);
        when(diagnostics.lock(1L)).thenReturn(diagnostic);when(diagnostics.get(1L)).thenReturn(diagnostic);
        var command=new CdsImportCommand(root.resolve("source.xml"),UUID.randomUUID().toString(),"T099",null,null,"source","pcon-2024-02",false,false,true,0,null,false);
        document=new CdsImportJobDocumentDto(command,actor,null,null,root);
        status=mock(CdsImportJobDto.class);when(ledger.status(RUN)).thenReturn(status);
        when(jobs.load(RUN)).thenReturn(document);when(jobs.root(RUN)).thenReturn(root);
        when(access.webActor(viewer,command)).thenReturn(actor);
        when(ledger.claimReviewPlanning(RUN)).thenReturn(5L);when(ledger.lockPlanningOwned(RUN,5L)).thenReturn(run);
        when(ledger.lockReviewAdministrativeOwned(RUN,5L)).thenReturn(run);
        when(coordinator.acquire()).thenReturn(mock(CdsImportCoordinatorService.Owner.class));
        when(reviews.pendingRun(eq(RUN),anyInt())).thenReturn(List.of());
        doAnswer(call -> {stored.set(call.getArgument(0));history.put(stored.get().getId(),stored.get());return null;}).when(reviews).persist(any());
        when(reviews.get(anyString())).thenAnswer(call -> history.get(call.getArgument(0)));
        when(reviews.lock(anyString())).thenAnswer(call -> history.get(call.getArgument(0)));
        when(reviews.latestDraft(eq(RUN),anyString())).thenAnswer(call -> history.values().stream()
                .filter(row -> "DRAFT_REQUIRES_CORRECTION".equals(row.getState()) && call.getArgument(1).equals(row.getSourceKey()))
                .max(java.util.Comparator.comparingLong(CdsImportReview::getReviewVersion)).orElse(null));
        when(reviews.origins(eq(RUN),anyCollection())).thenAnswer(call -> ((java.util.Collection<String>)call.getArgument(1)).stream()
                .map(history::get).filter(java.util.Objects::nonNull).map(row -> new CdsImportReviewOriginDto(row.getId(),row.getOperator(),row.getSecurityNo(),row.getSourceKey(),row.getReviewedSourceXPath(),row.getState(),row.getReviewVersion())).toList());
        when(ledger.patients(RUN,"",100)).thenReturn(List.of());when(ledger.dependencyEvidence(RUN,"",100)).thenReturn(List.of());
        doAnswer(call -> {run.setState("PLANNING");return null;}).when(ledger).resetUnsealedPlan(eq(RUN),eq(5L),anyLong());
    }

    @Test
    void shouldDurablyAcceptOnlyPendingMetadata_whenAnAuthorizedCorrectionIsRequested() throws Exception {
        String id=service.accept(viewer,RUN,command("M"));
        assertThat(stored.get().getId()).isEqualTo(id);assertThat(stored.get().getState()).isEqualTo("REQUESTED");
        assertThat(run.getReviewVersion()).isEqualTo(1);assertThat(stored.get().getPlanSha256()).isEmpty();
        assertThat(diagnostic.isResolved()).isFalse();verify(ledger,never()).resetUnsealedPlan(anyString(),anyLong(),anyLong());
        verify(access).webActor(viewer,document.command());
    }

    @Test
    void shouldRejectStaleVersionAndCommittedTargets_whenReviewCannotSafelyReplan() throws Exception {
        run.setReviewVersion(2);
        assertThatThrownBy(() -> service.accept(viewer,RUN,command("M"))).hasMessage("REVIEW_VERSION_CONFLICT");
        run.setReviewVersion(0);when(count.getSingleResult()).thenReturn(1L);
        assertThatThrownBy(() -> service.accept(viewer,RUN,command("M"))).hasMessage("REVIEW_AFTER_WRITE_REQUIRES_RECONCILIATION");
        verify(reviews,never()).persist(any());assertThat(run.getReviewVersion()).isZero();
    }

    @Test
    void shouldRefuseActiveOrSealedRuns_whenAnOldDiagnosticStillExists() throws Exception {
        run.setState("PLANNING");
        assertThatThrownBy(() -> service.accept(viewer,RUN,command("M"))).hasMessage("REVIEW_JOB_NOT_STOPPED");
        run.setState("REVIEW_REQUIRED");run.setManifestDigest("a".repeat(64));
        assertThatThrownBy(() -> service.accept(viewer,RUN,command("M"))).hasMessage("REVIEW_AFTER_SEAL_REFUSED");
        verify(reviews,never()).persist(any());
    }

    @Test
    void shouldKeepOriginalBlockers_whenReviewerAuthorizationIsDenied() throws Exception {
        when(access.webActor(viewer,document.command())).thenThrow(new SecurityException("denied"));
        assertThatThrownBy(() -> service.accept(viewer,RUN,command("M"))).isInstanceOf(SecurityException.class);
        assertThat(diagnostic.isResolved()).isFalse();verify(reviews,never()).persist(any());
    }

    @ParameterizedTest
    @ValueSource(strings={"F","INVALID"})
    void shouldValidatePinnedSchemaAndRetainRawSource_whenReviewedScalarRepairsValidOrInvalidOriginal(String original) throws Exception {
        var source=source(original);String before=Files.readString(source.path());
        Path originalValues=Path.of(diagnostic.getProtectedDetailLocation()).getParent().resolve("values.bin");
        byte[] lexical=Files.readAllBytes(originalValues);
        String id=service.accept(viewer,RUN,command("M"));
        assertThat(service.process(id)).isEqualTo(RUN);
        assertThat(stored.get().getState()).isEqualTo("OVERLAY_READY");
        assertThat(stored.get().getManifestSha256()).hasSize(64);assertThat(stored.get().getPlanBytes()).isPositive();
        assertThat(Files.readString(source.path())).isEqualTo(before);assertThat(Files.readAllBytes(originalValues)).isEqualTo(lexical);
        verify(ledger).resetUnsealedPlan(RUN,5L,1L);assertThat(diagnostic.isResolved()).isFalse();
        verify(corrections,never()).revalidated(anyString(),anyLong(),anyLong(),anyString());
    }

    @Test
    void shouldRejectInvalidReplacementBeforeReset_whenPinnedSchemaStillRefusesTheValue() throws Exception {
        var source=source("F");byte[] before=Files.readAllBytes(source.path());
        String id=service.accept(viewer,RUN,command("NOT_A_GENDER"));
        assertThatThrownBy(() -> service.process(id)).isInstanceOf(CdsInputException.class);
        assertThat(stored.get().getState()).isEqualTo("DRAFT_REQUIRES_CORRECTION");assertThat(stored.get().getSafeErrorCode()).isEqualTo("CDS_XML_SCHEMA_INVALID");
        assertThat(stored.get().getPlanBytes()).isPositive();assertThat(Files.readAllBytes(source.path())).isEqualTo(before);
        verify(ledger,never()).resetUnsealedPlan(anyString(),anyLong(),anyLong());assertThat(diagnostic.isResolved()).isFalse();
    }

    @Test
    void shouldRetainInactiveEditsUntilEntireXmlValidates_whenTwoInvalidScalarsAreCorrectedSequentially() throws Exception {
        var source=source("INVALID","INVALID");String original=Files.readString(source.path());diagnostic.setSourcePath("/files[1]");
        String first=service.accept(viewer,RUN,command("M"));
        assertThatThrownBy(() -> service.process(first)).isInstanceOf(CdsInputException.class);
        assertThat(history.get(first).getState()).isEqualTo("DRAFT_REQUIRES_CORRECTION");
        var next=new CdsImportReviewCommand("CORRECT_VALUE",1L,"",null,1,
                "/OmdCds[1]/PatientRecord[1]/AllergiesAndAdverseReactions[1]/Severity[1]","MO","Fixture verified second scalar");
        String second=service.accept(viewer,RUN,next);assertThat(service.process(second)).isEqualTo(RUN);
        assertThat(history.get(first).getState()).isEqualTo("OVERLAY_READY");assertThat(history.get(second).getState()).isEqualTo("OVERLAY_READY");
        verify(ledger).resetUnsealedPlan(RUN,5L,2L);verify(ledger,never()).resetUnsealedPlan(RUN,5L,1L);
        assertThat(Files.readString(source.path())).isEqualTo(original);assertThat(diagnostic.isResolved()).isFalse();
    }

    @Test
    void shouldRequireAnotherOperatorToRestateInactiveScalars_whenAReviewDraftChangesOwnership() throws Exception {
        source("INVALID","INVALID");diagnostic.setSourcePath("/files[1]");String first=service.accept(viewer,RUN,command("M"));
        assertThatThrownBy(() -> service.process(first)).isInstanceOf(CdsInputException.class);
        var secondActor=new CdsImportActorDto("web-43","T099","T099",43,null,null);
        when(access.webActor(viewer,document.command())).thenReturn(secondActor);
        var secondCommand=new CdsImportReviewCommand("CORRECT_VALUE",1L,"",null,1,
                "/OmdCds[1]/PatientRecord[1]/AllergiesAndAdverseReactions[1]/Severity[1]","MO","Fixture reviewer verifies severity");
        String second=service.accept(viewer,RUN,secondCommand);
        assertThatThrownBy(() -> service.process(second)).hasMessage("REVIEW_OTHER_OPERATOR_CONFIRMATION_REQUIRED");
        verify(ledger,never()).resetUnsealedPlan(anyString(),anyLong(),anyLong());
        var restated=new CdsImportReviewCommand("CORRECT_VALUE",1L,"",null,2,RECORD+"/Gender[1]","M","Fixture reviewer verifies inherited scalar");
        String third=service.accept(viewer,RUN,restated);assertThat(service.process(third)).isEqualTo(RUN);
        assertThat(history.get(first).getState()).isEqualTo("DRAFT_REQUIRES_CORRECTION");
        assertThat(history.get(second).getState()).isEqualTo("OVERLAY_READY");assertThat(history.get(third).getState()).isEqualTo("OVERLAY_READY");
        verify(ledger).resetUnsealedPlan(RUN,5L,3L);
    }

    @Test
    void shouldRefuseChangedArchivedEvidence_whenInterruptedPublishedResetIsResumed() throws Exception {
        source("F");String id=service.accept(viewer,RUN,command("M"));service.process(id);
        stored.get().setState("OVERLAY_PUBLISHED");Files.writeString(Path.of(stored.get().getPlanLocation()),"changed");
        clearInvocations(ledger);
        assertThatThrownBy(() -> service.process(id)).hasMessage("REVIEW_PLAN_PROOF_CHANGED");
        assertThat(stored.get().getState()).isEqualTo("OVERLAY_PUBLISHED");
        assertThat(stored.get().getSafeErrorCode()).isEqualTo("REVIEW_PLAN_PROOF_CHANGED");
        verify(ledger,never()).resetUnsealedPlan(anyString(),anyLong(),anyLong());
    }

    @Test
    void shouldPreserveRejectionStateInOperatorSummary_whenNoPendingReviewRemains() {
        var rejected=new CdsImportReview();rejected.setId(UUID.randomUUID().toString());rejected.setState("REJECTED");rejected.setSafeErrorCode("CDS_XML_SCHEMA_INVALID");
        when(reviews.latest(RUN)).thenReturn(rejected);
        assertThat(service.summary(RUN)).containsEntry("reviewState","REJECTED").containsEntry("pendingReviewCount",0)
                .containsEntry("reviewErrorCode","CDS_XML_SCHEMA_INVALID");
    }

    @Test
    void shouldBlockResumeWithInactiveDrafts_whenNoWorkerRequestIsPending() {
        when(reviews.inactiveDraftCount(RUN)).thenReturn(1L);
        assertThat(service.hasPending(RUN)).isTrue();
        assertThat(service.summary(RUN)).containsEntry("inactiveDraftCount",1L);
    }

    @Test
    void shouldRefuseIncompletePlanArchive_whenIndependentLedgerCountsDiffer() throws Exception {
        source("F");String id=service.accept(viewer,RUN,command("M"));
        var actual=mock(TypedQuery.class);
        when(entityManager.createQuery("select count(p) from CdsImportPatient p where p.runId=:run",Long.class)).thenReturn(actual);
        when(actual.setParameter(anyString(),any())).thenReturn(actual);when(actual.getSingleResult()).thenReturn(1L);
        assertThatThrownBy(() -> service.process(id)).hasMessage("REVIEW_PLAN_ARCHIVE_INCOMPLETE");
        assertThat(stored.get().getState()).isEqualTo("REJECTED");
        verify(ledger,never()).resetUnsealedPlan(anyString(),anyLong(),anyLong());
    }

    @Test
    void shouldPublishExplicitUnwrittenRevisionOnlyAfterProof_whenAnAlreadyAcceptedRunConflictsWithCanonicalHistory() throws Exception {
        source("F");String id=revision();assertThat(service.process(id)).isEqualTo(RUN);
        assertThat(stored.get().getState()).isEqualTo("REVISION_READY");
        assertThat(stored.get().getPatientKey()).isEqualTo("a".repeat(64));
        verify(revisions).approve(eq(RUN),eq(5L),eq(1L),any());verify(ledger).resetUnsealedPlan(RUN,5L,1L);
        assertThat(diagnostic.isResolved()).isFalse();
    }

    @Test
    void shouldRefuseChangedRevisionSidecarProof_whenPublishedAdministrativeResetIsRecovered() throws Exception {
        source("F");String id=revision();service.process(id);stored.get().setState("REVISION_PUBLISHED");
        Files.writeString(Path.of(stored.get().getManifestLocation()),"tampered");clearInvocations(ledger);
        assertThatThrownBy(() -> service.process(id)).hasMessage("REVIEW_PLAN_PROOF_CHANGED");
        assertThat(stored.get().getState()).isEqualTo("REVISION_PUBLISHED");
        verify(ledger,never()).resetUnsealedPlan(anyString(),anyLong(),anyLong());
    }

    @Test
    void shouldLatchFatalReviewWithoutDiscardingPublishedProof_whenHealthySupervisorStopsAutomaticRetries() throws Exception {
        source("F");String id=service.accept(viewer,RUN,command("M"));service.process(id);
        stored.get().setState("OVERLAY_PUBLISHED");String digest=stored.get().getManifestSha256();
        service.superviseFatal(id);
        assertThat(stored.get().getState()).isEqualTo("OVERLAY_PUBLISHED");assertThat(stored.get().getManifestSha256()).isEqualTo(digest);
        assertThat(stored.get().getSafeErrorCode()).isEqualTo("REVIEW_WORKER_FATAL");
    }

    @Test
    void shouldRequireExplicitAuditedTransactionToRetryFatalReview_whenOriginalRequestRemainsImmutable() throws Exception {
        source("F");String id=service.accept(viewer,RUN,command("M"));String payload=stored.get().getPayload();
        stored.get().setSafeErrorCode("REVIEW_WORKER_FATAL");run.setCancelRequested(true);
        when(reviews.pendingRun(RUN,2)).thenReturn(List.of(stored.get()));
        assertThatThrownBy(() -> service.retryFatal(RUN,actor)).hasMessage("REVIEW_REQUIRES_TRANSACTION");
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {assertThat(service.retryFatal(RUN,actor)).isEqualTo(id);}
        finally {org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);}
        assertThat(stored.get().getSafeErrorCode()).isNull();assertThat(stored.get().getPayload()).isEqualTo(payload);
        assertThat(run.isCancelRequested()).isFalse();verify(ledger).actorEvent(run,actor,"REVIEW_FATAL_RETRY_REQUESTED");
    }

    private String revision() throws Exception {
        String patient="a".repeat(64);diagnostic.setPatientKey(patient);diagnostic.setCode("SOURCE_REVISION_REVIEW_REQUIRED");
        when(revisions.prepare(eq(RUN),eq(5L),eq(patient),anyString(),eq(document),any(),any(),any())).thenAnswer(call -> {
            Path attempt=call.getArgument(6);Map<String,Map<String,Object>> artifacts=new LinkedHashMap<>();
            for(String name:List.of("revision-context.json","lineage-approval.json","prior-run-provenance.jsonl")) {
                Path file=Files.writeString(attempt.resolve(name),"immutable-proof");
                artifacts.put(name,Map.of("sha256",CdsPaths.hex(CdsPaths.sha256().digest(Files.readAllBytes(file))),"bytes",Files.size(file)));
            }
            var proof=new CdsImportRevisionDto(RUN,patient,patient,"source","source-patient","ordinary","0".repeat(64),"1".repeat(64),"2".repeat(64),
                    "3".repeat(64),"4".repeat(64),"5".repeat(64),null,0,true,List.of(),0,false);
            var xml=new CdsSnapshotFileDto("source.xml",root.resolve("source.xml"),"6".repeat(64),Files.size(root.resolve("source.xml")),true);
            return new CdsUnwrittenRevisionReviewService.Prepared(proof,xml,RECORD,artifacts);
        });
        return service.accept(viewer,RUN,new CdsImportReviewCommand("APPROVE_UNWRITTEN_REVISION",1L,patient,null,0,null,null,"Verified explicit zero-write source revision"));
    }

    @Test
    void shouldCancelOnlyUnpublishedRequests_whenPublishedAdministrativeResetMustRemainRecoverable() {
        var requested=new CdsImportReview();requested.setId("requested");requested.setState("REQUESTED");var published=new CdsImportReview();published.setId("published");published.setState("OVERLAY_PUBLISHED");
        when(reviews.lock("requested")).thenReturn(requested);when(reviews.lock("published")).thenReturn(published);
        when(reviews.pendingRun(RUN,2)).thenReturn(List.of(requested,published));service.cancel(RUN);
        assertThat(requested.getState()).isEqualTo("CANCELLED");assertThat(published.getState()).isEqualTo("OVERLAY_PUBLISHED");
    }

    private CdsImportReviewCommand command(String replacement) {
        return new CdsImportReviewCommand("CORRECT_VALUE",1L,"",null,0,RECORD+"/Gender[1]",replacement,"Fixture verified clinical source correction");
    }
    private CdsSnapshotFileDto source(String gender) throws Exception {return source(gender,null);}
    private CdsSnapshotFileDto source(String gender,String severity) throws Exception {
        String xml;
        try(var input=getClass().getResourceAsStream("/demographic/cds-import-summary.xml")){xml=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
        xml=xml.replace("<Gender>F</Gender>","<Gender>"+gender+"</Gender>");
        if(severity!=null) xml=xml.replace("<Severity>MO</Severity>","<Severity>"+severity+"</Severity>");
        Path file=Files.writeString(root.resolve("source.xml"),xml);String sha=CdsPaths.hex(CdsPaths.sha256().digest(Files.readAllBytes(file)));
        Path parsed=Files.createDirectory(root.resolve("parsed"));Path folder=parsed.resolve(CdsImportKeys.hash("source.xml",sha));
        try {parser.parse(file,folder,CdsInputLimitsDto.defaults(),() -> false,record -> { });}
        catch(CdsInputException expected){if(!gender.equals("INVALID") && !"INVALID".equals(severity)) throw expected;}
        diagnostic.setProtectedDetailLocation(folder.resolve("parse-result.json").toString());
        var source=new CdsSnapshotFileDto("source.xml",file,sha,Files.size(file),true);
        var snapshot=mock(CdsSnapshotDto.class);when(jobs.snapshot(RUN,document)).thenReturn(snapshot);
        doAnswer(call -> {((Consumer<CdsSnapshotFileDto>)call.getArgument(1)).accept(source);return null;}).when(snapshots).forEachXml(eq(snapshot),any());
        return source;
    }
}
