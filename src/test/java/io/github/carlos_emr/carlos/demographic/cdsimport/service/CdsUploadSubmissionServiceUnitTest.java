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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.CdsImportAdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.CdsUploadDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportAdmission;
import io.github.carlos_emr.carlos.commn.model.CdsUpload;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportJobCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsUploadImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobBindingDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsUploadDto;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.ByteArrayInputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Real upload state/files and job admission; only database storage and the outer worker facade are doubles. */
class CdsUploadSubmissionServiceUnitTest extends CarlosUnitTestBase {
    @TempDir Path temporary;
    private static final String OPERATOR = "web-42";
    private final Map<String, CdsUpload> uploadRows = new HashMap<>();
    private final Map<String, CdsImportJobCommand> accepted = new HashMap<>();
    private final Map<String, CdsImportJobDto> jobsById = new HashMap<>();
    private final Map<String, String> submissionRuns = new HashMap<>();
    private Properties properties;
    private CdsImportAdmission budget;
    private CdsUploadService uploads;
    private CdsImportAccessService access;
    private CdsImportService imports;
    private CdsImportLedgerService ledger;
    private CdsImportJobService jobs;
    private CdsUploadSubmissionService service;
    private LoggedInInfo viewer;
    private final CdsImportActorDto actor = new CdsImportActorDto(OPERATOR, "999998", "999998", 42, null, null);

    @BeforeEach void prepare() throws Exception {
        properties = new Properties();
        properties.setProperty("cds.import.enabled", "true");
        properties.setProperty("cds.import.uploadRoot", temporary.resolve("uploads").toString());
        properties.setProperty("cds.import.stagingRoot", temporary.resolve("staging").toString());
        properties.setProperty("cds.import.source.clinic.profiles", "omd-5.2");
        properties.setProperty("cds.import.upload.maxChunkBytes", "65536");
        properties.setProperty("cds.import.minimumFreeDiskBytes", "1");
        var configuration = new CdsImportConfigurationService(properties);
        var storage = mock(CdsUploadDao.class);
        var admission = mock(CdsImportAdmissionDao.class);
        budget = new CdsImportAdmission(); budget.setId("UPLOAD_BUDGET");
        when(admission.lock("UPLOAD_BUDGET")).thenReturn(budget);
        when(storage.get(anyString())).thenAnswer(call -> uploadRows.get(call.getArgument(0)));
        when(storage.lock(anyString())).thenAnswer(call -> uploadRows.get(call.getArgument(0)));
        when(storage.lockActualRun(anyString())).thenAnswer(call -> uploadRows.values().stream()
                .filter(row -> call.getArgument(0).equals(row.getRunId())).findFirst().orElse(null));
        when(storage.reservedCount()).thenAnswer(call -> uploadRows.values().stream().filter(row -> !row.isQuotaReleased()).count());
        doAnswer(call -> { CdsUpload row = call.getArgument(0); uploadRows.put(row.getId(), row); return null; })
                .when(storage).persist(any());
        doAnswer(call -> call.getArgument(0)).when(storage).merge(any());
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        uploads = spy(new CdsUploadService(storage, admission, configuration, transactions));

        ledger = mock(CdsImportLedgerService.class);
        when(ledger.findSubmission(anyString(), anyString())).thenAnswer(call ->
                jobsById.get(submissionRuns.get(call.getArgument(1))));
        when(ledger.accept(any())).thenAnswer(call -> persistAcceptance(call.getArgument(0)));
        when(ledger.status(anyString())).thenAnswer(call -> jobsById.get(call.getArgument(0)));
        when(ledger.snapshotLocation(anyString())).thenAnswer(call -> accepted.get(call.getArgument(0)).snapshotLocation());
        when(ledger.jobBinding(anyString())).thenAnswer(call -> new CdsImportJobBindingDto(
                jobsById.get(call.getArgument(0)), accepted.get(call.getArgument(0)).snapshotLocation()));
        jobs = new CdsImportJobService(ledger, configuration, new CdsSnapshotService(), uploads);
        access = mock(CdsImportAccessService.class);
        viewer = mock(LoggedInInfo.class);
        var security = new Security(); security.setSecurityNo(42);
        when(viewer.getLoggedInSecurity()).thenReturn(security);
        when(viewer.getLoggedInProviderNo()).thenReturn("999998");
        when(access.webActor(same(viewer), any())).thenReturn(actor);
        imports = mock(CdsImportService.class);
        when(imports.submit(same(viewer), any())).thenAnswer(call -> jobs.accept(actor, call.getArgument(1)));
        service = new CdsUploadSubmissionService(access, imports, uploads, configuration);
    }

    @Test void shouldKeepCompletedUploadAbandonable_whenAdmissionIsDisabled() throws Exception {
        var upload = completedUpload();
        properties.setProperty("cds.import.enabled", "false");
        var disabled = new CdsUploadSubmissionService(access, imports, uploads, new CdsImportConfigurationService(properties));
        assertThatThrownBy(() -> disabled.submit(viewer, options(upload.id(), false, true)))
                .hasMessage("IMPORT_ADMISSION_DISABLED");
        assertThat(uploads.status(OPERATOR, upload.id()).state()).isEqualTo("COMPLETE");
        verify(uploads, never()).claimForSubmission(anyString(), anyString());
        verifyNoInteractions(imports, ledger);
        assertThat(uploads.abandon(OPERATOR, upload.id()).state()).isEqualTo("ABANDONED");
        assertThat(budget.getReservedBytes()).isZero();
        assertThat(temporary.resolve("uploads").resolve(upload.id())).doesNotExist();
    }

    @Test void shouldKeepCompletedUploadAbandonable_whenAuthorizationIsRevoked() throws Exception {
        var upload = completedUpload();
        when(access.webActor(same(viewer), any())).thenThrow(new SecurityException("IMPORT_ACCESS_DENIED"));
        assertThatThrownBy(() -> service.submit(viewer, options(upload.id(), false, true)))
                .hasMessage("IMPORT_ACCESS_DENIED");
        assertThat(uploads.status(OPERATOR, upload.id()).state()).isEqualTo("COMPLETE");
        verify(uploads, never()).claimForSubmission(anyString(), anyString());
        verifyNoInteractions(imports, ledger);
        assertThat(uploads.abandon(OPERATOR, upload.id()).state()).isEqualTo("ABANDONED");
    }

    @Test void shouldAvoidFalseUncertainty_whenAdmissionClosesBeforeJobAcceptance() throws Exception {
        var upload = completedUpload();
        properties.setProperty("cds.import.enabled", "false");
        var disabledJobs = new CdsImportJobService(ledger, new CdsImportConfigurationService(properties), new CdsSnapshotService(), uploads);
        when(imports.submit(same(viewer), any())).thenAnswer(call -> disabledJobs.accept(actor, call.getArgument(1)));
        assertThatThrownBy(() -> service.submit(viewer, options(upload.id(), false, true)))
                .hasMessage("IMPORT_ADMISSION_DISABLED");
        assertThat(uploads.status(OPERATOR, upload.id()).state()).isEqualTo("COMPLETE");
        verify(uploads, never()).claimForSubmission(anyString(), anyString());
        verify(ledger, never()).accept(any());
        assertThat(uploads.abandon(OPERATOR, upload.id()).state()).isEqualTo("ABANDONED");
    }

    @Test void shouldKeepCompletedUploadAbandonable_whenAcceptanceLockIsBusy() throws Exception {
        var upload = completedUpload();
        Path source = uploads.sourceForSubmission(OPERATOR, upload.id());
        try (var channel = FileChannel.open(source.resolveSibling("body.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                var lock = channel.lock()) {
            assertThatThrownBy(() -> service.submit(viewer, options(upload.id(), false, true))).hasMessage("UPLOAD_BUSY");
        }
        assertThat(uploads.status(OPERATOR, upload.id()).state()).isEqualTo("COMPLETE");
        verify(uploads, never()).claimForSubmission(anyString(), anyString());
        verify(ledger, never()).accept(any());
        assertThat(uploads.abandon(OPERATOR, upload.id()).state()).isEqualTo("ABANDONED");
    }

    @Test void shouldClaimAndAcknowledgeOnce_whenJobAcceptanceSucceeds() throws Exception {
        var upload = completedUpload();
        var job = service.submit(viewer, options(upload.id(), false, true));
        assertThat(uploads.status(OPERATOR, upload.id()).state()).isEqualTo("SUBMITTED");
        verify(uploads, times(1)).claimForSubmission(OPERATOR, upload.id());
        verify(uploads, times(1)).submitted(OPERATOR, upload.id(), job.id(), false);
        var effective = ArgumentCaptor.forClass(CdsImportCommand.class);
        verify(imports).submit(same(viewer), effective.capture());
        assertThat(effective.getValue().providerNo()).isEqualTo("999998");
        assertThat(effective.getValue().submissionKey()).isEqualTo(upload.submissionKey() + "-import");
        assertThat(effective.getValue().matchProviderNames()).isTrue();
        assertThat(effective.getValue().input()).isEqualTo(temporary.resolve("uploads").resolve(upload.id()).resolve("source.xml"));
    }

    @Test void shouldRetainUncertainBytesAndReuseTheJob_whenAcceptanceAcknowledgementIsLost() throws Exception {
        var upload = completedUpload();
        var first = new AtomicBoolean(true);
        doAnswer(call -> {
            var job = persistAcceptance(call.getArgument(0));
            if (first.getAndSet(false)) throw new CdsImportException("SIMULATED_ACCEPTANCE_ACK_UNKNOWN", true, null);
            return job;
        }).when(ledger).accept(any());
        assertThatThrownBy(() -> service.submit(viewer, options(upload.id(), false, true)))
                .hasMessage("SIMULATED_ACCEPTANCE_ACK_UNKNOWN");
        assertThat(uploads.status(OPERATOR, upload.id()).state()).isEqualTo("SUBMITTING");
        assertThat(uploads.sourceForSubmission(OPERATOR, upload.id())).exists();
        assertThatThrownBy(() -> uploads.abandon(OPERATOR, upload.id())).hasMessage("UPLOAD_ACCEPTANCE_RECONCILIATION_REQUIRED");
        var retried = service.submit(viewer, options(upload.id(), false, true));
        assertThat(uploads.status(OPERATOR, upload.id()).runId()).isEqualTo(retried.id());
        verify(ledger, times(1)).accept(any());
        verify(uploads, times(1)).claimForSubmission(OPERATOR, upload.id());
    }

    @Test void shouldRefuseReplacementAndAvoidAnotherClaim_whenAcceptedOptionsChange() throws Exception {
        var upload = completedUpload();
        var job = service.submit(viewer, options(upload.id(), false, true));
        assertThatThrownBy(() -> service.submit(viewer, options(upload.id(), false, false)))
                .hasMessage("SUBMISSION_CONFLICT");
        assertThat(uploads.status(OPERATOR, upload.id()).runId()).isEqualTo(job.id());
        verify(ledger, times(1)).accept(any());
        verify(uploads, times(1)).claimForSubmission(OPERATOR, upload.id());
    }

    @Test void shouldSerializeCompetingOptions_whenAcceptanceIsAlreadyInProgress() throws Exception {
        var upload = completedUpload();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("acceptance release timed out");
            return persistAcceptance(call.getArgument(0));
        }).when(ledger).accept(any());
        var executor = Executors.newSingleThreadExecutor();
        var first = executor.submit(() -> service.submit(viewer, options(upload.id(), false, true)));
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.submit(viewer, options(upload.id(), false, false)))
                    .hasMessage("PREACCEPT_IO_BUSY");
            assertThat(uploads.status(OPERATOR, upload.id()).state()).isEqualTo("SUBMITTING");
        } finally {
            release.countDown(); executor.shutdown();
        }
        var job = first.get(10, TimeUnit.SECONDS);
        assertThat(uploads.status(OPERATOR, upload.id()).runId()).isEqualTo(job.id());
        assertThatThrownBy(() -> service.submit(viewer, options(upload.id(), false, false))).hasMessage("SUBMISSION_CONFLICT");
        verify(ledger, times(1)).accept(any());
        verify(uploads, times(1)).claimForSubmission(OPERATOR, upload.id());
    }

    @Test void shouldReusePublishedJobWithoutRawBytes_whenPreviewThenActualSubmissionIsRetried() throws Exception {
        var upload = completedUpload();
        var preview = service.submit(viewer, options(upload.id(), true, true));
        assertThatThrownBy(() -> service.submit(viewer, options(upload.id(), false, true)))
                .hasMessage("UPLOAD_PREVIEW_SNAPSHOT_PENDING");
        assertThat(uploads.status(OPERATOR, upload.id()).state()).isEqualTo("SUBMITTED_DRY_RUN");
        jobs.snapshot(preview.id(), jobs.load(preview.id()));
        var actual = service.submit(viewer, options(upload.id(), false, true));
        assertThat(actual.id()).isNotEqualTo(preview.id());
        var snapshot = jobs.snapshot(actual.id(), jobs.load(actual.id()));
        assertThat(snapshot).isNotNull();
        assertThat(uploads.status(OPERATOR, upload.id()).state()).isEqualTo("RETIRED");
        Path retired = uploads.sourceForSubmission(OPERATOR, upload.id());
        assertThat(retired).doesNotExist();
        assertThat(service.submit(viewer, options(upload.id(), false, true))).isEqualTo(actual);
        verify(ledger, times(2)).accept(any());
        verify(uploads, times(2)).claimForSubmission(OPERATOR, upload.id());
    }

    private CdsUploadDto completedUpload() throws Exception {
        byte[] bytes = "<OmdCds xmlns='cds'><PatientRecord/></OmdCds>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var upload = uploads.begin(OPERATOR, "source.xml", bytes.length);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        uploads.append(OPERATOR, upload.id(), 0, bytes.length, hash, new ByteArrayInputStream(bytes));
        return uploads.finish(OPERATOR, upload.id());
    }

    private CdsImportJobDto persistAcceptance(CdsImportJobCommand command) {
        String run = UUID.randomUUID().toString();
        var job = new CdsImportJobDto(run, command.operator(), command.providerNo(), command.sourceNamespace(), command.profile(),
                "CLI_ACCEPTED", command.sourceDigest(), command.optionsDigest(), command.rulesetDigest(), null, 0, 0, 0, false,
                Instant.now(), Instant.now(), 0, null, null);
        accepted.put(run, command); jobsById.put(run, job); submissionRuns.put(command.submissionKey(), run);
        return job;
    }

    private static CdsUploadImportCommand options(String uploadId, boolean dryRun, boolean matchProviders) {
        return new CdsUploadImportCommand(uploadId, null, null, null, "clinic", "omd-5.2", false, dryRun, matchProviders, 0, null, false);
    }
}
