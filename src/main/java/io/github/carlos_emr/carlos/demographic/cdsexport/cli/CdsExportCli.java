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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessConfiguration;
import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessDelegationVerifier;
import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessLogging;
import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessScope;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportArtifactUnavailableException;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportControlOutcomeUnknownException;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportReviewException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Safe process protocol only; durable application state, not progress or adapter exit alone, establishes export completion. */
public final class CdsExportCli {
    private static final ObjectMapper JSON = new ObjectMapper();

    private CdsExportCli() {}

    @FunctionalInterface
    public interface RuntimeFactory {
        CdsExportCliRuntime open(
                CdsProcessConfiguration configuration,
                CdsExportCliArguments arguments,
                CdsProcessDelegationVerifier.Delegation delegation)
                throws Exception;
    }

    public static int run(
            String[] argv,
            Map<String, String> environment,
            PrintStream out,
            PrintStream err,
            RuntimeFactory factory) {
        if (argv.length == 1 && argv[0].equals("--capabilities")) {
            try {
                var configuration =
                        CdsProcessConfiguration.load(environment, CdsProcessScope.EXPORT);
                if (Runtime.version().feature() != 25) throw new IOException("CDS_JAVA25_REQUIRED");
                // The capability belongs to this installed export implementation. Admission,
                // source completeness and current authorization remain separate runtime gates.
                line(
                        out,
                        Map.of(
                                "engine",
                                "cds-export",
                                "protocol",
                                1,
                                "java",
                                25,
                                "cliHeapMiB",
                                configuration.heapMiB(),
                                "submissionLookup",
                                true,
                                "detachedSubmission",
                                true,
                                "qualifiedProfiles",
                                io
                                        .github
                                        .carlos_emr
                                        .carlos
                                        .demographic
                                        .cdsexport
                                        .engine
                                        .CdsExportClinicalEngine
                                        .installedProfiles()
                                        .stream()
                                        .map(
                                                io.github.carlos_emr.carlos.demographic.cds.contract
                                                                .CdsLogicalProfile
                                                        ::identifier)
                                        .sorted()
                                        .toList(),
                                "operations",
                                CdsExportCliArguments.OPERATIONS.stream().sorted().toList()));
                return out.checkError() ? 2 : 0;
            } catch (IOException | RuntimeException failure) {
                err.println(
                        "CDS_EXPORT_CONFIGURATION: trusted production configuration and Java25 are required");
                return 2;
            }
        }
        final CdsExportCliArguments arguments;
        try {
            arguments = CdsExportCliArguments.parse(argv);
        } catch (RuntimeException malformed) {
            err.println("CDS_EXPORT_ARGUMENTS: invalid or conflicting arguments");
            return 1;
        }
        final CdsProcessConfiguration configuration;
        try {
            configuration = CdsProcessConfiguration.load(environment, CdsProcessScope.EXPORT);
            if (Runtime.version().feature() != 25) throw new IOException("CDS_JAVA25_REQUIRED");
        } catch (IOException | RuntimeException failure) {
            err.println(
                    "CDS_EXPORT_CONFIGURATION: trusted production configuration and Java25 are required");
            return 2;
        }
        final CdsProcessDelegationVerifier.Delegation delegation;
        try {
            delegation =
                    CdsProcessDelegationVerifier.verify(
                            environment,
                            CdsProcessScope.EXPORT,
                            arguments.provider(),
                            arguments.operation(),
                            argv,
                            Instant.now());
        } catch (IOException | RuntimeException denied) {
            err.println(
                    "CDS_EXPORT_AUTHORIZATION: exact root-issued export delegation could not be verified");
            return 3;
        }
        return runAuthorized(configuration, arguments, delegation, out, err, factory);
    }

    static int runAuthorized(
            CdsProcessConfiguration configuration,
            CdsExportCliArguments arguments,
            CdsProcessDelegationVerifier.Delegation delegation,
            PrintStream out,
            PrintStream err,
            RuntimeFactory factory) {
        CdsExportCliRuntime runtime = null;
        int outcome = 2;
        // A durable job can exist once a submission was attempted or an existing job is addressed.
        boolean durable = !arguments.operation().equals("export");
        var retired = new java.util.concurrent.CountDownLatch(1);
        try {
            line(out, Map.of("event", "INITIALIZING", "operation", arguments.operation()));
            runtime = factory.open(configuration, arguments, delegation);
            String run = runtime.resolveRunId();
            if (arguments.operation().equals("export")) {
                String key =
                        arguments.submissionKey() == null
                                ? delegation.nonce()
                                : arguments.submissionKey();
                line(out, Map.of("event", "SUBMISSION", "submissionKey", key));
                durable = true;
                run = runtime.accept();
                line(out, Map.of("event", "ACCEPTED", "runId", run));
            }
            if (arguments.operation().equals("resume")) runtime.resume(run);
            if (arguments.operation().equals("cancel")) runtime.cancel(run);
            if (arguments.operation().equals("report"))
                line(out, java.util.Objects.requireNonNull(runtime.report(run)));
            if (arguments.operation().equals("download")) runtime.download(run);
            CdsExportJobDto state;
            boolean work =
                    arguments.operation().equals("export")
                            || arguments.operation().equals("resume");
            if (work && !arguments.detach()) state = foreground(runtime, run, out, err, retired);
            else state = runtime.status(run);
            line(out, summary(state, "RESULT"));
            if (out.checkError() || err.checkError()) {
                err.println(
                        "CDS_EXPORT_DELIVERY: console delivery failed; reconcile the original durable job");
                outcome = 2;
            } else if (!work || arguments.detach()) outcome = 0;
            else if (state.complete()) outcome = 0;
            else if (state.heldBack()) {
                err.println(
                        "CDS_EXPORT_PATIENTS_HELD: package ready without "
                                + state.blockedPatients()
                                + " held patient(s); they are listed in the package ReadMe and the report");
                outcome = 5;
            } else outcome = 4;
        } catch (SecurityException denied) {
            String missing =
                    io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportAccessService
                            .missingObject(denied);
            err.println(authorizationMessage(denied, missing));
            outcome = 3;
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            err.println(
                    "CDS_EXPORT_INTERRUPTED: waiting stopped; durable cancellation was not requested");
            outcome = 130;
        } catch (CdsExportArtifactUnavailableException unavailable) {
            err.println(
                    unavailable.kind() == CdsExportArtifactUnavailableException.Kind.REPORT
                            ? "CDS_EXPORT_INCOMPLETE: a verified diagnostic report is not available"
                            : "CDS_EXPORT_INCOMPLETE: a verified clinical package is not available");
            outcome = 4;
        } catch (CdsExportReviewException review) {
            err.println("CDS_EXPORT_REVIEW: " + review.reason().code());
            outcome = 4;
        } catch (CdsExportControlOutcomeUnknownException unknown) {
            // The documented exit table gives 2 to an unconfirmed result; 4 means the job itself
            // reached review or failure, which is not known here (review W3).
            err.println("CDS_EXPORT_UNCERTAIN: reconcile the original durable job before retrying");
            outcome = 2;
        } catch (Exception failed) {
            if (Thread.currentThread().isInterrupted()) {
                err.println(
                        "CDS_EXPORT_INTERRUPTED: waiting stopped; durable cancellation was not requested");
                outcome = 130;
            } else {
                // Name the safe code the failure carries, and do not send an operator to reconcile
                // a job this command never submitted (review W1).
                String code = safeCode(failed);
                if (runtime == null || !durable)
                    err.println(
                            "CDS_EXPORT_NOT_STARTED: "
                                    + (code == null ? "the export could not start" : code)
                                    + "; this command did not submit or change an export job");
                else
                    err.println(
                            "CDS_EXPORT_RUNTIME: "
                                    + (code == null ? "" : code + ": ")
                                    + "operation did not establish a clean completion; reconcile the original durable job");
                err.println(
                        "CDS_TECHNICAL: "
                                + new String(
                                        CdsProcessLogging.failureSummary(failed, runtime == null),
                                        StandardCharsets.UTF_8));
                outcome = 2;
            }
        } finally {
            try {
                if (runtime != null)
                    try {
                        runtime.close();
                    } catch (RuntimeException failed) {
                        err.println(
                                "CDS_EXPORT_CLEANUP: process resources did not retire cleanly; durable state remains authoritative");
                        if (outcome == 0) outcome = 2;
                    }
            } finally {
                retired.countDown();
            }
        }
        return outcome;
    }

    /** The first fixed CARLOS safe code in the failure chain, never free text or a path. */
    static String safeCode(Throwable failure) {
        int depth = 0;
        for (Throwable cursor = failure; cursor != null && depth < 16; cursor = cursor.getCause(), depth++) {
            String message = cursor.getMessage();
            if (message != null && message.matches("CDS_[A-Z0-9_]{1,90}")) return message;
        }
        return null;
    }

    /** Progress must retire before its owning Spring runtime closes, including interrupted waits. */
    static final class ProgressExecutor extends ScheduledThreadPoolExecutor {
        private final long closeWaitNanos;

        ProgressExecutor() {
            this(TimeUnit.SECONDS.toNanos(30));
        }

        ProgressExecutor(long closeWaitNanos) {
            super(
                    1,
                    task -> {
                        Thread thread = new Thread(task, "cds-export-progress");
                        thread.setDaemon(true);
                        return thread;
                    });
            if (closeWaitNanos < 1 || closeWaitNanos > TimeUnit.SECONDS.toNanos(30))
                throw new IllegalArgumentException("CDS_EXPORT_PROGRESS_CLOSE_BOUND_INVALID");
            this.closeWaitNanos = closeWaitNanos;
        }

        @Override
        public void close() {
            boolean interrupted = Thread.interrupted();
            long deadline = System.nanoTime() + closeWaitNanos;
            try {
                shutdownNow();
                while (!isTerminated()) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0)
                        throw new IllegalStateException("CDS_EXPORT_PROGRESS_CLEANUP_INCOMPLETE");
                    try {
                        if (!awaitTermination(remaining, TimeUnit.NANOSECONDS))
                            throw new IllegalStateException(
                                    "CDS_EXPORT_PROGRESS_CLEANUP_INCOMPLETE");
                    } catch (InterruptedException stop) {
                        interrupted = true;
                        shutdownNow();
                    }
                }
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }

    private static CdsExportJobDto foreground(
            CdsExportCliRuntime runtime,
            String run,
            PrintStream out,
            PrintStream err,
            java.util.concurrent.CountDownLatch retired)
            throws Exception {
        Thread owner = Thread.currentThread();
        Thread hook =
                new Thread(
                        () -> {
                            runtime.interruptWait();
                            owner.interrupt();
                            // The JVM halts when the hooks finish. Wait (bounded) until the owner
                            // has released its lease and closed the runtime, so a stopped
                            // foreground export can be resumed at once (review W4).
                            try {
                                if (!retired.await(30, TimeUnit.SECONDS))
                                    err.println(
                                            "CDS_EXPORT_STOP_WAIT_EXPIRED: the export had not"
                                                    + " released its lease after 30 seconds");
                            } catch (InterruptedException halted) {
                                Thread.currentThread().interrupt();
                            }
                        },
                        "cds-export-wait-stop");
        Runtime.getRuntime().addShutdownHook(hook);
        try (var progress = new ProgressExecutor()) {
            progress.scheduleWithFixedDelay(
                    () -> {
                        try {
                            line(out, summary(runtime.status(run), "PROGRESS"));
                        } catch (Exception unavailable) {
                            err.println(
                                    "CDS_EXPORT_PROGRESS: current authorized status is temporarily unavailable");
                        }
                    },
                    5,
                    15,
                    TimeUnit.SECONDS);
            // Hand-off (plan item 2.5): this process cannot render eForms, so a job that selects
            // Reports is first left to the online web worker, which claims queued jobs every few
            // seconds and renders them. This command then only watches. If no worker claims the
            // job for the whole window, it runs here and its eForms carry only their data summary,
            // noted "rendered form unavailable"; no patient is held for that.
            long handoff = runtime.rendererHandoffMillis(run);
            if (handoff > 0)
                line(out, Map.of("event", "HANDOFF", "runId", run, "seconds", handoff / 1000));
            long unclaimedSince = 0;
            boolean unclaimed = false, fallback = false;
            while (true) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                if (handoff > 0) {
                    CdsExportJobDto observed = runtime.status(run);
                    if (observed.state().terminal()) return observed;
                    long now = System.nanoTime();
                    if (!observed.waiting()) {
                        // Owned by a worker (the online one, or this command's own last attempt).
                        unclaimed = false;
                        Thread.sleep(1000);
                        continue;
                    }
                    if (!unclaimed) {
                        unclaimed = true;
                        unclaimedSince = now;
                    }
                    if (now - unclaimedSince < TimeUnit.MILLISECONDS.toNanos(handoff)) {
                        Thread.sleep(1000);
                        continue;
                    }
                    if (!fallback) {
                        fallback = true;
                        line(out, Map.of("event", "HANDOFF_FALLBACK", "runId", run));
                    }
                }
                CdsExportJobDto current = runtime.execute(run);
                if (current.state().terminal()) return current;
                Thread.sleep(1000);
            }
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException shuttingDown) {
                /* JVM owns the hook now. */
            }
        }
    }

    static Map<String, Object> summary(CdsExportJobDto job, String event) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event", event);
        result.put("runId", job.id());
        result.put("state", job.state().name());
        result.put("revision", job.revision());
        result.put("epoch", job.epoch());
        result.put("totalPatients", job.totalPatients());
        result.put("processedPatients", job.processedPatients());
        result.put("blockedPatients", job.blockedPatients());
        result.put("cancelRequested", job.cancelRequested());
        result.put("workerOwned", job.workerOwned());
        result.put("artifactAvailable", job.artifactAvailable());
        result.put("safeCode", job.safeCode());
        result.put("waiting", job.waiting());
        result.put("complete", job.complete());
        result.put("heldBack", job.heldBack());
        return result;
    }

    private static synchronized void line(PrintStream out, Object value) throws IOException {
        out.println(JSON.writeValueAsString(value));
    }

    /** One line naming why the export was refused, with no provider, patient or account names. */
    static String authorizationMessage(SecurityException denied, String missingObject) {
        if (missingObject != null)
            return "CDS_EXPORT_AUTHORIZATION: the export provider's roles lack read access to "
                    + missingObject
                    + "; grant it or leave out the categories that need it";
        if ("CDS_EXPORT_DELEGATION_NOT_CONFIGURED".equals(denied.getMessage()))
            return "CDS_EXPORT_DELEGATION_NOT_CONFIGURED: this operator has no export delegation;"
                    + " an administrator must set cds.export.operator.<user>.providers";
        if ("CDS_EXPORT_DELEGATION_DENIED".equals(denied.getMessage()))
            return "CDS_EXPORT_DELEGATION_DENIED: this operator's export delegation does not cover"
                    + " the requested provider or facility";
        return "CDS_EXPORT_AUTHORIZATION: current export scope is denied";
    }
}
