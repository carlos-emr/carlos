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
package io.github.carlos_emr.carlos.demographic.cdsexport.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessConfiguration;
import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessDelegationVerifier;
import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessScope;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportState;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportArtifactUnavailableException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportCliUnitTest {
    @Test
    void shouldVerifyProgressCloseWaits_forInterruptedWorkToRetire() throws Exception {
        var started = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        try (var progress = new CdsExportCli.ProgressExecutor()) {
            progress.execute(
                    () -> {
                        started.countDown();
                        try {
                            new CountDownLatch(1).await();
                        } catch (InterruptedException stop) {
                            Thread.currentThread().interrupt();
                        } finally {
                            finished.countDown();
                        }
                    });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(finished.getCount()).isZero();
    }

    @Test
    void shouldVerifyProgressClosePreservesTheCallersInterruptFlag_forCliContract() {
        try {
            try (var progress = new CdsExportCli.ProgressExecutor()) {
                Thread.currentThread().interrupt();
            }
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldVerifyProgressCloseRefusesWorkThatDoesNotRetire_withinItsBound() throws Exception {
        var started = new CountDownLatch(1);
        var release = new Semaphore(0);
        try (var progress = new CdsExportCli.ProgressExecutor(TimeUnit.MILLISECONDS.toNanos(25))) {
            progress.execute(
                    () -> {
                        started.countDown();
                        release.acquireUninterruptibly();
                    });
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(progress::close)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("CDS_EXPORT_PROGRESS_CLEANUP_INCOMPLETE");
            } finally {
                release.release();
                progress.shutdownNow();
                progress.awaitTermination(5, TimeUnit.SECONDS);
            }
            assertThat(progress.isTerminated()).isTrue();
        }
    }

    private static final String RUN = "22222222-2222-4222-8222-222222222222",
            NONCE = "33333333-3333-4333-8333-333333333333";
    private ByteArrayOutputStream output, error;
    private PrintStream out, err;
    private CdsExportCliRuntime runtime;
    private CdsProcessConfiguration configuration;
    private CdsProcessDelegationVerifier.Delegation delegation;

    @BeforeEach
    void prepareExplicitProcessFixture() throws Exception {
        output = new ByteArrayOutputStream();
        error = new ByteArrayOutputStream();
        out = new PrintStream(output, true, StandardCharsets.UTF_8);
        err = new PrintStream(error, true, StandardCharsets.UTF_8);
        runtime = mock(CdsExportCliRuntime.class);
        configuration = new CdsProcessConfiguration(Path.of("/synthetic"), new Properties(), 512);
        delegation = new CdsProcessDelegationVerifier.Delegation("operator", 1234, NONCE);
        when(runtime.resolveRunId()).thenReturn(RUN);
        when(runtime.accept()).thenReturn(RUN);
        when(runtime.status(RUN)).thenReturn(job(CdsExportState.ACCEPTED, false));
        when(runtime.execute(RUN)).thenReturn(job(CdsExportState.READY, true));
    }

    @AfterEach
    void retireFixtureStreams() {
        Thread.interrupted();
        out.close();
        err.close();
    }

    @Test
    void shouldPrintDurableSubmissionAndAcceptance_beforeAnyClinicalExecution() throws Exception {
        doAnswer(
                        call -> {
                            assertThat(output.toString(StandardCharsets.UTF_8))
                                    .contains(
                                            "\"event\":\"SUBMISSION\"",
                                            "\"event\":\"ACCEPTED\"",
                                            RUN);
                            return job(CdsExportState.READY, true);
                        })
                .when(runtime)
                .execute(RUN);
        assertThat(run(fresh())).isZero();
        verify(runtime).close();
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("\"artifactAvailable\":true");
    }

    @Test
    void shouldReturnDetachedWaiting_withoutExecutingOrClaimingClinicalCompletion()
            throws Exception {
        var args = new ArrayList<>(List.of(fresh()));
        args.add("--detach");
        assertThat(run(args.toArray(String[]::new))).isZero();
        verify(runtime, never()).execute(anyString());
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains(
                        "\"waiting\":true",
                        "\"totalPatients\":null",
                        "\"artifactAvailable\":false");
    }

    @Test
    void shouldRefuseAReadyLabelWithoutAPublishedArtifact_andPreserveReviewOutcomes()
            throws Exception {
        when(runtime.execute(RUN)).thenReturn(job(CdsExportState.READY, false));
        assertThat(run(fresh())).isEqualTo(4);
        when(runtime.execute(RUN)).thenReturn(job(CdsExportState.REVIEW_REQUIRED, false));
        assertThat(run(fresh())).isEqualTo(4);
    }

    @Test
    void shouldTreatAStatusExitAsObservation_only() throws Exception {
        assertThat(run(new String[] {"--operation", "status", "--provider", "100", "--run", RUN}))
                .isZero();
        verify(runtime, never()).execute(anyString());
        verify(runtime, never()).accept();
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("\"state\":\"ACCEPTED\"", "\"artifactAvailable\":false");
    }

    @Test
    void shouldReportAnExpiredCaptureOwnerAsWaiting_withTheSameIncompleteOutcomeAsTheBrowser() {
        when(runtime.status(RUN)).thenReturn(job(CdsExportState.CAPTURING, false));
        assertThat(run(new String[] {"--operation", "status", "--provider", "100", "--run", RUN}))
                .isZero();
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("\"waiting\":true", "\"complete\":false");
    }

    @Test
    void shouldRefuseAReadyArtifactWithUnknownOrBlockedPatients_asClinicalSuccess() {
        when(runtime.execute(RUN))
                .thenReturn(
                        new CdsExportJobDto(
                                RUN,
                                CdsExportState.READY,
                                1,
                                null,
                                0,
                                0,
                                false,
                                false,
                                1,
                                null,
                                Instant.EPOCH,
                                Instant.EPOCH,
                                true));
        assertThat(run(fresh())).isEqualTo(4);
        when(runtime.execute(RUN))
                .thenReturn(
                        new CdsExportJobDto(
                                RUN,
                                CdsExportState.READY,
                                1,
                                1L,
                                1,
                                1,
                                false,
                                false,
                                1,
                                null,
                                Instant.EPOCH,
                                Instant.EPOCH,
                                true));
        assertThat(run(fresh())).isEqualTo(4);
    }

    @Test
    void shouldStopForegroundWaiting_withoutIssuingCancellation() throws Exception {
        doAnswer(
                        call -> {
                            Thread.currentThread().interrupt();
                            throw new java.util.concurrent.CancellationException();
                        })
                .when(runtime)
                .execute(RUN);
        assertThat(run(fresh())).isEqualTo(130);
        verify(runtime, never()).cancel(anyString());
        verify(runtime).close();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void shouldKeepAnUnavailableVerifiedReport_orPackageIncomplete() throws Exception {
        doThrow(
                        new CdsExportArtifactUnavailableException(
                                CdsExportArtifactUnavailableException.Kind.REPORT))
                .when(runtime)
                .report(RUN);
        assertThat(run(new String[] {"--operation", "report", "--provider", "100", "--run", RUN}))
                .isEqualTo(4);
        doThrow(
                        new CdsExportArtifactUnavailableException(
                                CdsExportArtifactUnavailableException.Kind.PACKAGE))
                .when(runtime)
                .download(RUN);
        assertThat(
                        run(
                                new String[] {
                                    "--operation",
                                    "download",
                                    "--provider",
                                    "100",
                                    "--run",
                                    RUN,
                                    "--artifact",
                                    "opaque",
                                    "--output",
                                    "/safe/export.cms"
                                }))
                .isEqualTo(4);
    }

    @Test
    void shouldKeepSourceMessagesAndCredentials_outOfTechnicalConsoleEvidence() throws Exception {
        doThrow(new IllegalStateException("PHI-SECRET-PATIENT credential=password"))
                .when(runtime)
                .accept();
        assertThat(run(fresh())).isEqualTo(2);
        assertThat(output.toString(StandardCharsets.UTF_8) + error.toString(StandardCharsets.UTF_8))
                .doesNotContain("PHI-SECRET-PATIENT", "credential=password");
    }

    @Test
    void shouldFailConsoleDeliveryOrCleanup_withoutConvertingItToClinicalSuccess()
            throws Exception {
        doThrow(new IllegalStateException("cleanup detail")).when(runtime).close();
        assertThat(run(fresh())).isEqualTo(2);
    }

    @Test
    void shouldProbeTheExportHeapWithoutSpring_andAdvertiseOnlyTheInstalledOntarioMdProfile()
            throws Exception {
        AtomicInteger opened = new AtomicInteger();
        try (var loader = mockStatic(CdsProcessConfiguration.class)) {
            loader.when(() -> CdsProcessConfiguration.load(anyMap(), eq(CdsProcessScope.EXPORT)))
                    .thenReturn(configuration);
            int status =
                    CdsExportCli.run(
                            new String[] {"--capabilities"},
                            Map.of(),
                            out,
                            err,
                            (configured, args, proof) -> {
                                opened.incrementAndGet();
                                return runtime;
                            });
            assertThat(status).isZero();
            assertThat(opened).hasValue(0);
            assertThat(output.toString(StandardCharsets.UTF_8))
                    .contains(
                            "\"engine\":\"cds-export\"",
                            "\"qualifiedProfiles\":[\"omd-5.2\"]",
                            "\"cliHeapMiB\":512")
                    .doesNotContain("pcon-2024-02");
        }
    }

    private int run(String[] arguments) {
        return CdsExportCli.runAuthorized(
                configuration,
                CdsExportCliArguments.parse(arguments),
                delegation,
                out,
                err,
                (selected, args, proof) -> runtime);
    }

    private static CdsExportJobDto job(CdsExportState state, boolean available) {
        return new CdsExportJobDto(
                RUN,
                state,
                1,
                state == CdsExportState.READY ? 1L : null,
                state == CdsExportState.READY ? 1 : 0,
                0,
                false,
                false,
                1,
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                available);
    }

    private static String[] fresh() {
        return new String[] {
            "--operation",
            "export",
            "--provider",
            "100",
            "--profile",
            "omd-5.2",
            "--physicians",
            "100",
            "--recipient",
            "recipient",
            "--audit-plan",
            "audit"
        };
    }
}
