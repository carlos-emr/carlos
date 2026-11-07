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

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** A real bounded worker pool proves fatal reviews cannot enter an automatic retry loop. */
@Tag("unit")
class CdsImportSupervisionServiceUnitTest {
    private CdsImportReviewService reviews;
    private CdsImportLedgerService ledger;
    private CdsImportExecutionService execution;
    private CdsImportService service;
    private ThreadPoolExecutor worker;
    private CdsImportConfigurationService configuration;

    @BeforeEach void setUp() throws Exception {
        reviews=mock(CdsImportReviewService.class);
        ledger=mock(CdsImportLedgerService.class);
        execution=mock(CdsImportExecutionService.class);
        configuration=mock(CdsImportConfigurationService.class);
        var cleanup=mock(CdsImportCleanupService.class);
        when(configuration.enabled()).thenReturn(true);
        when(configuration.value("cds.import.worker.enabled", "true")).thenReturn("true");
        when(cleanup.housekeeping()).thenReturn(Map.of("state", "COMPLETE", "fullPassComplete", true));
        when(ledger.pending("", 16)).thenReturn(List.of());
        when(ledger.summary("run")).thenReturn(Map.of());
        when(reviews.summary("run")).thenReturn(Map.of());
        service=new CdsImportService(ledger, mock(CdsImportJobService.class), execution,
                mock(CdsImportResultService.class), mock(CdsImportAccessService.class), configuration, cleanup,
                reviews, mock(CdsImportFailureEvidenceService.class), mock(CdsImportObservationService.class));
        var field=CdsImportService.class.getDeclaredField("worker");field.setAccessible(true);
        worker=(ThreadPoolExecutor)field.get(service);
    }
    @AfterEach void close() {service.close();}

    @Test void shouldHoldRecoveredWork_untilExplicitPostRefreshWebBootstrap() throws Exception {
        var job=mock(io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto.class);
        when(job.id()).thenReturn("recovered");
        when(job.state()).thenReturn("RUNNING");
        when(ledger.pending("",16)).thenReturn(List.of(job));
        when(ledger.status("recovered")).thenReturn(job);
        var dispatcher=CdsImportService.class.getDeclaredField("dispatcher");dispatcher.setAccessible(true);
        try (var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.registerBean("cdsImportService",CdsImportService.class,()->service);
            context.refresh();
            // CARLOS publishes SpringUtils after refresh returns. No persisted
            // job may reach privilege checks during refresh or its events.
            assertThat(dispatcher.get(service)).isNull();
            verifyNoInteractions(ledger,execution);
            service.start();
            Object started=dispatcher.get(service);
            service.start();
            assertThat(started).isNotNull().isSameAs(dispatcher.get(service));
            verify(execution,timeout(3000)).execute("recovered");
        }
        service.start();
        assertThat(worker.isShutdown()).isTrue();
    }

    @Test void shouldNotStartDisabledWebWorker_atBootstrap() throws Exception {
        when(configuration.value("cds.import.worker.enabled","true")).thenReturn("false");
        service.start();
        var field=CdsImportService.class.getDeclaredField("dispatcher");field.setAccessible(true);
        assertThat(field.get(service)).isNull();
        verifyNoInteractions(ledger,execution);
    }

    @Test void shouldNotResurrectWorker_whenClosedBeforeBootstrap() throws Exception {
        service.close();service.start();service.close();
        var field=CdsImportService.class.getDeclaredField("dispatcher");field.setAccessible(true);
        assertThat(field.get(service)).isNull();
        assertThat(worker.isShutdown()).isTrue();
        verifyNoInteractions(ledger,execution);
    }

    @Test void shouldKeepFatalReviewSuspended_untilHealthySupervisorPersistsIt() throws Exception {
        when(reviews.process("failed")).thenThrow(new AssertionError("synthetic private failure"));
        call("scheduleReview", new Class<?>[]{String.class}, "failed");barrier();
        call("scheduleReview", new Class<?>[]{String.class}, "failed");barrier();
        verify(reviews, times(1)).process("failed");
        call("dispatch", new Class<?>[]{});
        verify(reviews).superviseFatal("failed");
        // The real DAO excludes suspended rows. A different accepted review may proceed after supervision.
        when(reviews.pending(16)).thenReturn(List.of("other"));
        call("dispatch", new Class<?>[]{});barrier();
        verify(reviews).process("other");verify(reviews, times(1)).process("failed");
    }
    @Test void shouldKeepWorkerSuspended_whenFatalPersistenceIsUnavailable() throws Exception {
        when(reviews.process("failed")).thenThrow(new AssertionError("synthetic private failure"));
        doThrow(new IOException("synthetic unavailable metadata")).doNothing().when(reviews).superviseFatal("failed");
        call("scheduleReview", new Class<?>[]{String.class}, "failed");barrier();
        call("dispatch", new Class<?>[]{});
        call("scheduleReview", new Class<?>[]{String.class}, "failed");barrier();
        verify(reviews, times(1)).process("failed");
        call("dispatch", new Class<?>[]{});verify(reviews, times(2)).superviseFatal("failed");
    }
    @Test void shouldMakeFatalSupervisorVisible_andStopAutomaticWork() throws Exception {
        when(reviews.process("failed")).thenThrow(new AssertionError("synthetic private failure"));
        doThrow(new AssertionError("synthetic supervisor resource failure")).when(reviews).superviseFatal("failed");
        call("scheduleReview", new Class<?>[]{String.class}, "failed");barrier();
        call("dispatch", new Class<?>[]{});call("dispatch", new Class<?>[]{});
        verify(reviews, times(1)).superviseFatal("failed");
        var summary=(Map<?, ?>)operatorSummary();
        assertThat(summary.get("workerAlert")).isEqualTo("IMPORT_SUPERVISOR_FATAL");
    }
    @ParameterizedTest
    @ValueSource(strings={"REVIEW_REQUIRED", "RECONCILIATION_REQUIRED"})
    void shouldAllowOnlyStoppedPendingReportDelivery_duringPendingReview(String state) throws Exception {
        var job=mock(io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto.class);
        when(job.state()).thenReturn(state); when(ledger.hasPendingReport("run")).thenReturn(true);
        when(ledger.status("run")).thenReturn(job); when(reviews.hasPending("run")).thenReturn(true);
        call("schedule", new Class<?>[]{String.class}, "run"); barrier();
        verify(execution).reportOnly("run");
        verify(execution, never()).execute("run");
    }
    @Test void shouldKeepBlockingClinicalResume_duringPendingReview() throws Exception {
        var job=mock(io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto.class);
        when(job.state()).thenReturn("RESUME_REQUESTED"); when(ledger.hasPendingReport("run")).thenReturn(true);
        when(ledger.status("run")).thenReturn(job); when(reviews.hasPending("run")).thenReturn(true);
        call("schedule", new Class<?>[]{String.class}, "run"); barrier();
        verifyNoInteractions(execution);
    }
    @Test void shouldNotRedispatchAlreadyStoppedReport_duringPendingReview() throws Exception {
        var job=mock(io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto.class);
        when(job.state()).thenReturn("REVIEW_REQUIRED"); when(ledger.hasPendingReport("run")).thenReturn(false);
        when(ledger.status("run")).thenReturn(job); when(reviews.hasPending("run")).thenReturn(true);
        call("schedule", new Class<?>[]{String.class}, "run"); barrier();
        verifyNoInteractions(execution);
    }
    @ParameterizedTest
    @ValueSource(strings={"REQUESTED", "VALIDATING", "OVERLAY_PUBLISHED", "PATIENT_DECLINED", "REVISION_PUBLISHED"})
    void shouldNotTerminateAcceptedReviewWaitingForWorker_withDeliveredStoppedReport(String state) throws Exception {
        var job=mock(io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto.class);
        when(job.state()).thenReturn("REVIEW_REQUIRED");
        when(ledger.status("run")).thenReturn(job);
        AtomicBoolean delivered=new AtomicBoolean();
        when(ledger.hasPendingReport("run")).thenAnswer(call->!delivered.get());
        when(ledger.summary("run")).thenAnswer(call->Map.of(
                "operationTerminal", delivered.get(), "cleanComplete", false, "reportsDelivered", delivered.get()));
        when(reviews.hasPending("run")).thenReturn(true);
        when(reviews.summary("run")).thenReturn(Map.of("pendingReviewCount", 1, "reviewState", state));
        doAnswer(call->{delivered.set(true);return null;}).when(execution).reportOnly("run");
        // This reproduces the durable review -> dispatcher report -> review-enqueue window.
        // The actual bounded facade worker delivers the report before any review is scheduled.
        call("schedule", new Class<?>[]{String.class}, "run"); barrier();
        assertThat(ledger.summary("run").get("operationTerminal")).isEqualTo(true);
        var summary=(Map<?, ?>)operatorSummary();
        assertThat(summary.get("reportsDelivered")).isEqualTo(true);
        assertThat(summary.get("pendingReviewCount")).isEqualTo(1);
        assertThat(summary.get("reviewState")).isEqualTo(state);
        assertThat(summary.get("operationTerminal")).isEqualTo(false);
        assertThat(summary.get("cleanComplete")).isEqualTo(false);
        verify(execution).reportOnly("run");
        verify(execution, never()).execute("run");
        verify(reviews, never()).process(anyString());
    }
    @Test void shouldKeepStoppedTerminalOutcomeAndExplicitRecoveryCode_forSupervisedFatalReview() throws Exception {
        when(ledger.summary("run")).thenReturn(Map.of("operationTerminal", true, "cleanComplete", true));
        when(reviews.summary("run")).thenReturn(Map.of(
                "pendingReviewCount", 1, "reviewState", "REVISION_PUBLISHED", "reviewErrorCode", "REVIEW_WORKER_FATAL"));
        var summary=(Map<?, ?>)operatorSummary();
        assertThat(summary.get("operationTerminal")).isEqualTo(true);
        assertThat(summary.get("cleanComplete")).isEqualTo(false);
        assertThat(summary.get("reviewErrorCode")).isEqualTo("REVIEW_WORKER_FATAL");
        assertThat(summary.get("pendingReviewCount")).isEqualTo(1);
    }
    @Test void shouldKeepPendingReviewCountTyped_withZeroNotRewritingLedgerOutcome() throws Exception {
        when(ledger.summary("run")).thenReturn(Map.of("operationTerminal", true, "cleanComplete", true));
        when(reviews.summary("run")).thenReturn(Map.of("pendingReviewCount", 0));
        var zero=(Map<?, ?>)operatorSummary();
        assertThat(zero.get("operationTerminal")).isEqualTo(true);
        assertThat(zero.get("cleanComplete")).isEqualTo(true);
        when(reviews.summary("run")).thenReturn(Map.of("pendingReviewCount", 2L));
        var positive=(Map<?, ?>)operatorSummary();
        assertThat(positive.get("operationTerminal")).isEqualTo(false);
        assertThat(positive.get("cleanComplete")).isEqualTo(false);
        assertThat(positive.get("pendingReviewCount")).isEqualTo(2L);
    }
    private void barrier() throws Exception {worker.submit(()->{}).get(5, TimeUnit.SECONDS);}
    private Object operatorSummary() throws Exception {
        Map<String, Object> combined=new java.util.LinkedHashMap<>(ledger.summary("run"));
        combined.putAll(reviews.summary("run"));
        return call("operatorSummary", new Class<?>[]{String.class, Map.class}, "run", combined);
    }

    private Object call(String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Method method=CdsImportService.class.getDeclaredMethod(name, parameterTypes);method.setAccessible(true);
        return method.invoke(service, arguments);
    }
}
