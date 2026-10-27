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
package io.github.carlos_emr.carlos.demographic.cdsimport.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;

/** Process protocol and operator output only. Clinical meaning and completion remain authoritative in the shared engine. */
public final class CdsImportCli {
    private CdsImportCli() { }

    @FunctionalInterface
    public interface RuntimeFactory {
        CdsCliRuntime open(CdsCliConfiguration config, CdsCliArguments arguments,
                CdsDelegationVerifier.Delegation delegation) throws Exception;
    }

    public static int run(String[] argv, Map<String, String> environment, PrintStream out, PrintStream err,
            RuntimeFactory factory) {
        if (argv.length == 1 && "--capabilities".equals(argv[0])) {
            try {
                CdsCliConfiguration config = CdsCliConfiguration.load(environment);
                if (Runtime.version().feature() != 25) throw new IOException("CDS_JAVA25_REQUIRED");
                out.println("{\"protocol\":1,\"java\":25,\"cliHeapMiB\":" + config.heapMiB()
                        + ",\"operations\":[\"import\",\"status\",\"resume\",\"cancel\",\"report\"]}");
                return out.checkError() ? 2 : 0;
            } catch (IOException | RuntimeException failure) {
                err.println("CDS_CONFIGURATION: a trusted production configuration and Java25 runtime are required");
                return 2;
            }
        }
        final CdsCliArguments arguments;
        try { arguments = CdsCliArguments.parse(argv); }
        catch (RuntimeException failure) {
            err.println("CDS_ARGUMENTS: invalid or conflicting arguments"); return 1;
        }
        final CdsCliConfiguration config;
        try {
            config = CdsCliConfiguration.load(environment);
            if (Runtime.version().feature() != 25) throw new IOException("CDS_JAVA25_REQUIRED");
        } catch (IOException | RuntimeException failure) {
            err.println("CDS_CONFIGURATION: a trusted production configuration and Java25 runtime are required"); return 2;
        }
        final CdsDelegationVerifier.Delegation delegation;
        try { delegation = CdsDelegationVerifier.verify(environment, arguments, argv, Instant.now()); }
        catch (IOException | RuntimeException failure) {
            err.println("CDS_AUTHORIZATION: root-issued operator delegation could not be verified"); return 3;
        }
        if (("import".equals(arguments.operation()) || "resume".equals(arguments.operation()))
                && !Boolean.parseBoolean(config.properties().getProperty("cds.import.enabled", "false"))) {
            err.println("CDS_REFUSED: import admission is disabled in production configuration"); return 4;
        }
        return runAuthorized(config, arguments, delegation, out, err, factory);
    }

    static int runAuthorized(CdsCliConfiguration config, CdsCliArguments arguments,
            CdsDelegationVerifier.Delegation delegation, PrintStream out, PrintStream err, RuntimeFactory factory) {
        CdsCliRuntime runtime = null;
        int outcome = 2;
        try {
            line(out,Map.of("event","INITIALIZING","operation",arguments.operation()));
            runtime = factory.open(config, arguments, delegation);
            outcome = operate(runtime, arguments, delegation, out, err);
        } catch (CdsCliBootstrapException bootstrap) {
            err.println("CDS_BOOTSTRAP: production runtime initialization failed; no import was accepted");
            err.println(bootstrap.diagnostic()==null ? "CDS_DIAGNOSTIC: protected startup evidence could not be delivered"
                    : "CDS_DIAGNOSTIC: protected startup evidence="+bootstrap.diagnostic()); outcome=2;
        } catch (SecurityException denied) {
            err.println("CDS_AUTHORIZATION: operator/provider/source access denied"); outcome = 3;
        } catch (CdsImportException | CdsInputException refused) {
            err.println("CDS_INCOMPLETE: import was refused, failed or requires reconciliation; inspect the durable run"); outcome = 4;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); err.println("CDS_INTERRUPTED: inspect the durable run before resuming"); outcome = 130;
        } catch (Exception failure) {
            // Never print raw exception messages: source paths, XML values and SQL
            // diagnostics may contain PHI. The protected run/report carries detail.
            err.println("CDS_RUNTIME: operation failed; inspect durable run state before retrying"); outcome = 2;
        } finally {
            if (runtime != null) {
                try { runtime.close(); }
                catch (Exception cleanupFailure) {
                    err.println("CDS_CLEANUP: process resources did not retire cleanly; clinical results remain in durable run state");
                    if (outcome == 0) outcome = 2;
                }
            }
        }
        return outcome;
        // Fatal Errors intentionally propagate to the process supervisor. They
        // never become a fabricated clean completion or a retry of a clinical unit.
    }

    private static int operate(CdsCliRuntime runtime, CdsCliArguments arguments,
            CdsDelegationVerifier.Delegation delegation, PrintStream out, PrintStream err) throws Exception {
            String run = arguments.runId();
            if ("import".equals(arguments.operation())) {
                line(out, Map.of("event", "SUBMISSION", "submissionKey", delegation.nonce()));
                run = runtime.accept();
                // Emit the durable run handle before any clinical unit starts.
                line(out, Map.of("event", "ACCEPTED", "runId", run));
            }
            runtime.observe(run, arguments.operation());
            if ("import".equals(arguments.operation()) || "resume".equals(arguments.operation())) {
                execute(runtime, run, "resume".equals(arguments.operation()), out, err);
            } else if ("cancel".equals(arguments.operation())) runtime.cancel(run);
            Map<String, Object> summary = runtime.summary(run);
            line(out, consoleSummary(summary, arguments.operation(), "RESULT"));
            if ("report".equals(arguments.operation())) line(out, runtime.reports(run));
            if (out.checkError() || err.checkError()) {
                err.println("CDS_DELIVERY: console delivery failed; clinical results remain in durable run state"); return 2;
            }
            if ("status".equals(arguments.operation()) || "report".equals(arguments.operation())) return 0;
            if ("cancel".equals(arguments.operation())) return 4;
            return Boolean.TRUE.equals(summary.get("cleanComplete")) ? 0 : 4;
    }

    private static void execute(CdsCliRuntime runtime, String run, boolean resume, PrintStream out,
            PrintStream err) throws Exception {
        AtomicBoolean finished = new AtomicBoolean();
        Thread owner = Thread.currentThread();
        Thread hook = new Thread(() -> {
            owner.interrupt();
            try { runtime.cancel(run); }
            catch (Exception unavailable) { err.println("CDS_INTERRUPTED: cancellation requires durable reconciliation"); }
        }, "cds-cli-cancellation");
        Thread progress = new Thread(() -> {
            while (!finished.get()) {
                try {
                    Thread.sleep(5000);
                    if (!finished.get()) line(out, consoleSummary(runtime.summary(run), resume ? "resume" : "import", "PROGRESS"));
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt(); return;
                } catch (Exception unavailable) {
                    err.println("CDS_PROGRESS: progress unavailable; final durable receipt remains authoritative"); return;
                }
            }
        }, "cds-cli-progress");
        progress.setDaemon(true);
        Runtime.getRuntime().addShutdownHook(hook);
        try {
            progress.start(); runtime.execute(run, resume);
        } finally {
            finished.set(true); progress.interrupt();
            try { Runtime.getRuntime().removeShutdownHook(hook); }
            catch (IllegalStateException shuttingDown) { /* The signal supervisor is already retiring this process. */ }
            progress.join(2000);
        }
    }

    private static Map<String, Object> consoleSummary(Map<String, Object> summary, String operation, String event) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("event", event); result.put("operation", operation);
        for (String key : new String[]{"runId", "state", "dryRun", "discoveryComplete", "inventoryState",
                "discoveredFiles", "patientXmlFiles", "sourceBytes", "sourcePatientsIdentified", "sourcePatients",
                "targetCopiesPlanned", "expectedPatients", "expectedUnits", "plannedUnits", "committedUnits",
                "alreadyCompleteUnits", "pendingUnits", "outcomes", "categories", "blockingDiagnostics",
                "clinicalComplete", "reportState", "reportsDelivered", "cleanupState", "cleanupComplete",
                "cleanComplete", "operationTerminal", "reviewVersion", "reviewState", "reviewId",
                "pendingReviewCount", "inactiveDraftCount", "reviewErrorCode"}) {
            if (summary.containsKey(key)) result.put(key, summary.get(key));
        }
        return result;
    }

    private static void line(PrintStream out, Map<String, Object> value) throws IOException {
        // This branch is never loaded by --capabilities. Rows contain bounded
        // operational metadata only; datasets/reports never ride a byte array.
        String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        if (json.length() > 65536) throw new IOException("CDS_CONSOLE_METADATA_LIMIT");
        out.println(json);
        if (out.checkError()) throw new IOException("CDS_CONSOLE_DELIVERY");
    }
}
