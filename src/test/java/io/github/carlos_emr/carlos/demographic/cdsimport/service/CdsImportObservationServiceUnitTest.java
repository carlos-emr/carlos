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
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobBindingDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsImportObservationServiceUnitTest {
    private static final String SOURCE="a".repeat(64);
    private static final String OPTIONS="b".repeat(64);
    private static final String RULESET="c".repeat(64);
    @TempDir Path temporary;
    private final ObjectMapper json=new ObjectMapper();
    private final CdsImportActorDto actor=new CdsImportActorDto("operator", "1", null, null, null, null);
    private CdsImportLedgerService ledger;
    private CdsImportReviewService reviews;
    private CdsImportAccessService access;
    private CdsImportConfigurationService configuration;
    private CdsImportJobService jobs;
    private CdsImportObservationService observations;
    private CdsImportService facade;
    private RecordingTransactions transactions;
    private Path jobRoot;
    private Path metadata;
    private CdsImportCommand command;
    private CdsImportJobDocumentDto document;
    private final AtomicReference<CdsImportJobBindingDto> prefetched=new AtomicReference<>();
    private final AtomicReference<CdsImportJobBindingDto> current=new AtomicReference<>();
    private final AtomicReference<Map<String, Object>> projection=new AtomicReference<>();
    private final List<String> order=new ArrayList<>();

    @BeforeEach void prepare() throws Exception {
        ledger=mock(CdsImportLedgerService.class);
        reviews=mock(CdsImportReviewService.class);
        access=mock(CdsImportAccessService.class);
        configuration=mock(CdsImportConfigurationService.class);
        transactions=new RecordingTransactions();
        Path staging=Files.createDirectory(temporary.resolve("private"));
        jobRoot=Files.createDirectory(staging.resolve("job-current"));
        when(configuration.stagingRoot()).thenReturn(staging);
        command=new CdsImportCommand(temporary.resolve("source.xml"), "submission", "1", null, null,
                "registered-source", "pcon-2024-02", false, false, true, 0, null, false);
        document=new CdsImportJobDocumentDto(command, actor, null, null, jobRoot, RULESET);
        writeDocument(json.writeValueAsBytes(document));
        when(ledger.jobBinding("run")).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "metadata prefetch must end before file IO");
            return prefetched.get();
        });
        when(ledger.jobBindingInReadTransaction("run")).thenAnswer(call -> {
            requireReadScope();
            order.add("current-binding");
            return current.get();
        });
        projection.set(Map.of("state", "READY", "cancelRequested", false,
                "operationTerminal", false, "cleanComplete", false));
        when(ledger.summaryInReadTransaction("run")).thenAnswer(call -> {
            requireReadScope();
            order.add("clinical-projection");
            return projection.get();
        });
        when(reviews.summaryInReadTransaction("run")).thenAnswer(call -> {
            requireReadScope();
            order.add("review-projection");
            return Map.of("pendingReviewCount", 0, "reviewState", "IDLE");
        });
        when(access.context(eq(actor), eq(command), isNull())).thenAnswer(call -> {
            requireReadScope();
            order.add("authorize-actor");
            return null;
        });
        doAnswer(call -> {
            requireReadScope();
            order.add("authorize-viewer");
            return null;
        }).when(access).requireView(any(), any());
        jobs=new CdsImportJobService(ledger, configuration, mock(CdsSnapshotService.class), mock(CdsUploadService.class),
                mock(CdsPreacceptService.class));
        observations=new CdsImportObservationService(ledger, reviews, access, transactions);
        facade=facade(observations);
    }

    @AfterEach void close() {
        facade.close();
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
    }

    @Test void authorizedSummarySharesOneReadOnlyScopeAndKeepsPrivateBindingOutOfResponse() throws Exception {
        Map<String, Object> result=facade.summary(actor, "run");
        assertEquals(List.of("current-binding", "authorize-actor", "clinical-projection", "review-projection"), order);
        assertEquals(1, transactions.begins);
        assertEquals(1, transactions.commits);
        assertEquals(0, transactions.rollbacks);
        assertEquals("READY", result.get("state"));
        String rendered=json.writeValueAsString(result);
        assertFalse(rendered.contains(metadata.toString()));
        assertFalse(rendered.contains(jobRoot.toString()));
        assertFalse(rendered.contains("documentLocation"));
        assertFalse(rendered.contains("CdsImportJobBindingDto"));
        assertFalse(result.values().stream().anyMatch(value -> value instanceof CdsImportJobBindingDto
                || value instanceof CdsImportJobService.ReadContext));
        verify(ledger, never()).status(anyString());
        verify(ledger, never()).snapshotLocation(anyString());
        verify(ledger, never()).summary(anyString());
        verify(reviews, never()).summary(anyString());
    }

    @Test void wrongActorOrProviderIsDeniedBeforeAnyPrivateFileCanBeRead() throws Exception {
        Files.delete(metadata);
        for (CdsImportActorDto denied : java.util.Arrays.asList(null,
                new CdsImportActorDto("another-operator", "1", null, null, null, null),
                new CdsImportActorDto("operator", "2", null, null, null, null))) {
            assertThrows(SecurityException.class, () -> facade.summary(denied, "run"));
        }
        assertEquals(0, transactions.begins);
        verifyNoInteractions(access, reviews);
        verify(ledger, never()).jobBindingInReadTransaction(anyString());
        verify(ledger, never()).summaryInReadTransaction(anyString());
        verify(ledger, never()).authorizationStopped(anyString(), anyLong());
    }

    @Test void changedJobBytesAreRejectedBeforeAuthorizationOrProjection() throws Exception {
        Files.writeString(metadata, "private altered request");
        var failure=assertThrows(CdsImportException.class, () -> facade.summary(actor, "run"));
        assertEquals("JOB_DOCUMENT_HASH_MISMATCH", failure.getCode());
        assertEquals(0, transactions.begins);
        verifyNoInteractions(access, reviews);
        verify(ledger, never()).summaryInReadTransaction(anyString());
    }

    @Test void actorDelegationSelectorsMustMatchTheProtectedDocumentBeforeRightsAreConsulted() throws Exception {
        var forged=new CdsImportActorDto("operator", "1", "1", 12, null, null);
        assertThrows(SecurityException.class, () -> facade.summary(forged, "run"));
        assertEquals(0, transactions.begins);
        verifyNoInteractions(access, reviews);
        verify(ledger, never()).authorizationStopped(anyString(), anyLong());
    }

    @Test void aDifferentCurrentOwnerCannotBeReportedAsTheOriginalActorsRevocation() throws Exception {
        var before=current.get().job();
        var other=new CdsImportJobDto(before.id(), "other-operator", before.providerNo(), before.sourceNamespace(),
                before.profile(), before.state(), before.sourceDigest(), before.optionsDigest(), before.rulesetDigest(),
                before.manifestDigest(), before.expectedPatients(), before.expectedUnits(), before.ownerEpoch(),
                before.cancelRequested(), before.createdAt(), before.updatedAt(), before.reportGeneration(),
                before.reportLocation(), before.reportDigest());
        current.set(new CdsImportJobBindingDto(other, metadata.toString()));
        assertThrows(SecurityException.class, () -> facade.summary(actor, "run"));
        assertEquals(1, transactions.rollbacks);
        verifyNoInteractions(access, reviews);
        verify(ledger, never()).summaryInReadTransaction(anyString());
        verify(ledger, never()).authorizationStopped(anyString(), anyLong());
    }

    @Test void privateMetadataCapacityRemainsBoundedBeforeAuthorization() throws Exception {
        writeDocument(new byte[65537]);
        var failure=assertThrows(CdsImportException.class, () -> facade.summary(actor, "run"));
        assertEquals("JOB_METADATA_CAPACITY_EXCEEDED", failure.getCode());
        assertEquals(0, transactions.begins);
        verifyNoInteractions(access, reviews);
    }

    @Test void malformedButHashNamedJobIsRejectedBeforeReadScope() throws Exception {
        writeDocument("{malformed".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(java.io.IOException.class, () -> facade.summary(actor, "run"));
        assertEquals(0, transactions.begins);
        verifyNoInteractions(access, reviews);
    }

    @ParameterizedTest
    @ValueSource(strings={"operator", "provider", "namespace", "profile", "ruleset", "root"})
    void validlyHashedDocumentMustMatchItsAcceptedBinding(String changed) throws Exception {
        var otherActor="operator".equals(changed)
                ? new CdsImportActorDto("another-operator", "1", null, null, null, null) : actor;
        var otherCommand=new CdsImportCommand(command.input(), command.submissionKey(),
                "provider".equals(changed) ? "2" : command.providerNo(), null, null,
                "namespace".equals(changed) ? "other-source" : command.sourceNamespace(),
                "profile".equals(changed) ? "cds-5.2" : command.sourceProfile(), false, false, true, 0, null, false);
        var other=new CdsImportJobDocumentDto(otherCommand, otherActor, null, null,
                "root".equals(changed) ? jobRoot.resolveSibling("other-root") : jobRoot,
                "ruleset".equals(changed) ? "d".repeat(64) : RULESET);
        writeDocument(json.writeValueAsBytes(other));
        var failure=assertThrows(CdsImportException.class, () -> facade.summary(actor, "run"));
        assertEquals("JOB_DOCUMENT_BINDING_MISMATCH", failure.getCode());
        assertEquals(0, transactions.begins);
        verifyNoInteractions(access, reviews);
    }

    @ParameterizedTest
    @ValueSource(strings={"run", "location", "source", "options", "ruleset", "namespace", "profile"})
    void aCurrentImmutableBindingChangeCannotUseAnEarlierContext(String changed) throws Exception {
        var context=jobs.loadContext(prefetched.get());
        var before=prefetched.get().job();
        var altered=new CdsImportJobDto("run".equals(changed) ? "other-run" : before.id(), before.operator(), before.providerNo(),
                "namespace".equals(changed) ? "other-source" : before.sourceNamespace(),
                "profile".equals(changed) ? "cds-5.2" : before.profile(), before.state(),
                "source".equals(changed) ? "e".repeat(64) : before.sourceDigest(),
                "options".equals(changed) ? "e".repeat(64) : before.optionsDigest(),
                "ruleset".equals(changed) ? "e".repeat(64) : before.rulesetDigest(), before.manifestDigest(),
                before.expectedPatients(), before.expectedUnits(), before.ownerEpoch(), before.cancelRequested(),
                before.createdAt(), before.updatedAt(), before.reportGeneration(), before.reportLocation(), before.reportDigest());
        current.set(new CdsImportJobBindingDto(altered,
                "location".equals(changed) ? jobRoot.resolve("job-"+"f".repeat(64)+".json").toString() : metadata.toString()));
        var failure=assertThrows(CdsImportException.class, () -> observations.summary(actor, context));
        assertEquals("JOB_DOCUMENT_BINDING_MISMATCH", failure.getCode());
        assertEquals(1, transactions.rollbacks);
        verifyNoInteractions(access, reviews);
        verify(ledger, never()).summaryInReadTransaction(anyString());
    }

    @Test void currentMutableRunStateAndEpochAreReadInsteadOfPrefetchedState() throws Exception {
        current.set(new CdsImportJobBindingDto(job("COMPLETE", 8, true), metadata.toString()));
        projection.set(Map.of("state", "COMPLETE", "cancelRequested", true,
                "operationTerminal", true, "cleanComplete", true));
        var result=facade.summary(actor, "run");
        assertEquals("READY", prefetched.get().job().state());
        assertEquals("COMPLETE", result.get("state"));
        assertEquals(8L, current.get().job().ownerEpoch());
        assertEquals(true, result.get("cancelRequested"));
        verify(access).context(actor, command, null);
    }

    @Test void validatedJobContextCannotRedirectAnotherRunsWorkOrValuePublication() throws Exception {
        var context=jobs.loadContext(prefetched.get());
        var planning=mock(CdsImportPlanningService.class, CALLS_REAL_METHODS);
        var control=mock(CdsImportExecutionControl.class);
        assertThrows(IllegalArgumentException.class, () -> planning.work("foreign-run", null, context));
        assertThrows(IllegalArgumentException.class, () -> planning.publishValues("foreign-run", 3L, null, control, context));
        verify(control).requireBinding("foreign-run", 3L);
        verify(control, never()).check(anyBoolean());
    }

    @Test void everyRequestReauthorizesAndRevocationWritesOnlyAfterReadRollback() throws Exception {
        facade.summary(actor, "run");
        SecurityException revoked=new SecurityException("private revoked detail");
        doThrow(revoked).when(access).context(actor, command, null);
        when(ledger.status("run")).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return job("RUNNING", 27, false);
        });
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(1, transactions.rollbacks);
            order.add("durable-revocation");
            return null;
        }).when(ledger).authorizationStopped("run", 27L);
        assertSame(revoked, assertThrows(SecurityException.class, () -> facade.summary(actor, "run")));
        verify(access, times(2)).context(actor, command, null);
        verify(ledger, times(1)).summaryInReadTransaction("run");
        verify(reviews, times(1)).summaryInReadTransaction("run");
        verify(ledger).authorizationStopped("run", 27L);
        assertEquals(1, transactions.commits);
        assertEquals(1, transactions.rollbacks);
        assertEquals("durable-revocation", order.getLast());
    }

    @Test void webDenialPrecedesAggregatesAndNeverReadsPrivateJobMetadata() throws Exception {
        Files.delete(metadata);
        LoggedInInfo viewer=mock(LoggedInInfo.class);
        SecurityException denied=new SecurityException("viewer denied");
        doThrow(denied).when(access).requireView(viewer, current.get().job());
        assertSame(denied, assertThrows(SecurityException.class, () -> facade.summary(viewer, "run")));
        verify(ledger, never()).jobBinding(anyString());
        verify(ledger, never()).summaryInReadTransaction(anyString());
        verifyNoInteractions(reviews);
        verify(access, never()).context(any(), any(), any());
        verify(ledger, never()).authorizationStopped(anyString(), anyLong());
        assertEquals(1, transactions.rollbacks);
    }

    @Test void authorizedWebSummaryNeedsNoPrivateJobFileAndUsesFreshRightsEachTime() throws Exception {
        Files.delete(metadata);
        LoggedInInfo viewer=mock(LoggedInInfo.class);
        assertEquals("READY", facade.summary(viewer, "run").get("state"));
        assertEquals("READY", facade.summary(viewer, "run").get("state"));
        verify(access, times(2)).requireView(viewer, current.get().job());
        verify(ledger, never()).jobBinding(anyString());
        assertEquals(2, transactions.begins);
        assertEquals(2, transactions.commits);
    }

    @Test void compositeStatusReturnsTheAuthorizedCurrentJobAndSummaryWithoutPrivateFileIo() throws Exception {
        Files.delete(metadata);
        transactions.expectedIsolation=TransactionDefinition.ISOLATION_REPEATABLE_READ;
        LoggedInInfo viewer=mock(LoggedInInfo.class);
        current.set(new CdsImportJobBindingDto(job("INCOMPLETE", 8, true, 2), metadata.toString()));
        projection.set(Map.of("state", "INCOMPLETE", "cancelRequested", true,
                "operationTerminal", true, "reportsDelivered", true, "cleanComplete", false));

        var result=facade.observe(viewer, "run");

        assertEquals(List.of("current-binding", "authorize-viewer", "clinical-projection", "review-projection"), order);
        assertEquals("READY", prefetched.get().job().state());
        assertSame(current.get().job(), result.job());
        assertEquals(2, result.job().reportGeneration());
        assertEquals(true, result.summary().get("reportsDelivered"));
        assertEquals(false, result.summary().get("cleanComplete"));
        assertEquals(1, transactions.begins);
        assertEquals(1, transactions.commits);
        assertEquals(0, transactions.rollbacks);
        assertThrows(UnsupportedOperationException.class, () -> result.summary().put("operationTerminal", false));
        assertFalse(result.toString().contains(metadata.toString()));
        assertFalse(result.toString().contains(jobRoot.toString()));
        verify(ledger, never()).status(anyString());
        verify(ledger, never()).jobBinding(anyString());
        verify(ledger, never()).summary(anyString());
    }

    @Test void compositeStatusDenialPrecedesEveryAggregateAndReturnsNoJob() throws Exception {
        Files.delete(metadata);
        transactions.expectedIsolation=TransactionDefinition.ISOLATION_REPEATABLE_READ;
        LoggedInInfo viewer=mock(LoggedInInfo.class);
        SecurityException denied=new SecurityException("viewer denied");
        doThrow(denied).when(access).requireView(viewer, current.get().job());
        assertSame(denied, assertThrows(SecurityException.class, () -> facade.observe(viewer, "run")));
        verify(ledger, never()).summaryInReadTransaction(anyString());
        verify(ledger, never()).jobBinding(anyString());
        verifyNoInteractions(reviews);
        assertEquals(1, transactions.rollbacks);
    }

    @Test void observationRefusesAnAmbientTransactionBeforeAnyProtectedRead() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        try {
            var failure=assertThrows(CdsImportException.class, () -> observations.summary(mock(LoggedInInfo.class), "run"));
            assertEquals("AMBIENT_TRANSACTION_REFUSED", failure.getCode());
            var statusFailure=assertThrows(CdsImportException.class, () -> observations.status(mock(LoggedInInfo.class), "run"));
            assertEquals("AMBIENT_TRANSACTION_REFUSED", statusFailure.getCode());
            verify(ledger, never()).jobBindingInReadTransaction(anyString());
            assertEquals(0, transactions.begins);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        }
    }

    @Test void legacyFacadeUsesTheRealRequiredManagerAndMissingManagerCannotSilentlyFallBack() throws Exception {
        when(ledger.observationTransactionManager()).thenReturn(transactions);
        CdsImportService legacy=new CdsImportService(ledger, jobs, mock(CdsImportExecutionService.class),
                mock(CdsImportResultService.class), access, configuration, mock(CdsImportCleanupService.class), reviews,
                mock(CdsImportFailureEvidenceService.class));
        try {
            assertEquals("READY", legacy.summary(actor, "run").get("state"));
            assertEquals(1, transactions.begins);
            verify(ledger).observationTransactionManager();
        } finally { legacy.close(); }
        assertThrows(NullPointerException.class, () -> new CdsImportObservationService(ledger, reviews, access, null));
        when(ledger.observationTransactionManager()).thenReturn(null);
        assertThrows(NullPointerException.class, () -> new CdsImportObservationService(ledger, reviews, access));
    }

    private CdsImportService facade(CdsImportObservationService selected) {
        return new CdsImportService(ledger, jobs, mock(CdsImportExecutionService.class), mock(CdsImportResultService.class),
                access, configuration, mock(CdsImportCleanupService.class), reviews, mock(CdsImportFailureEvidenceService.class), selected);
    }

    private void writeDocument(byte[] bytes) throws Exception {
        metadata=jobRoot.resolve("job-"+CdsPaths.hex(CdsPaths.sha256().digest(bytes))+".json");
        Files.write(metadata, bytes);
        var binding=new CdsImportJobBindingDto(job("READY", 1, false), metadata.toString());
        prefetched.set(binding);
        current.set(binding);
    }

    private CdsImportJobDto job(String state, long epoch, boolean cancel) {
        return job(state, epoch, cancel, 0);
    }

    private CdsImportJobDto job(String state, long epoch, boolean cancel, long generation) {
        return new CdsImportJobDto("run", "operator", "1", "registered-source", "pcon-2024-02", state,
                SOURCE, OPTIONS, RULESET, null, 0, 0, epoch, cancel, Instant.EPOCH, Instant.EPOCH, generation,
                generation==0 ? null : jobRoot.resolve("private-report.json").toString(), generation==0 ? null : "d".repeat(64));
    }

    private void requireReadScope() {
        CdsImportLedgerService.requireObservationTransaction();
        assertEquals(1, transactions.begins-transactions.commits-transactions.rollbacks,
                "authorization and both projections must share the one live read scope");
    }

    private static final class RecordingTransactions extends AbstractPlatformTransactionManager {
        int begins;
        int commits;
        int rollbacks;
        int expectedIsolation=TransactionDefinition.ISOLATION_READ_COMMITTED;

        @Override protected Object doGetTransaction() { return new Object(); }

        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
            assertTrue(definition.isReadOnly());
            assertEquals(expectedIsolation, definition.getIsolationLevel());
            assertEquals(120, definition.getTimeout());
            begins++;
        }

        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
