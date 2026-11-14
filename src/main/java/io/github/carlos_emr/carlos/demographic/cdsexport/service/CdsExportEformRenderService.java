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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.commn.dao.CdsExportStatementWaitReadSupport;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.eform.util.EFormBrowserPdfService;
import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Renders one saved eForm for the CDS export through the clinical eForm renderer ({@link
 * EFormBrowserPdfService}), as the job's effective provider, and spools the PDF encrypted.
 *
 * <p>The export never approves anything: it renders with no approval, so a form with an
 * omission the renderer would only deliver after a clinician's exact approval is not rendered.
 * Nothing about a render can hold a patient. Every outcome other than a complete PDF is a
 * {@link Reason}, and the eForm still travels in its CARLOS data summary with a note naming that
 * reason. Only a changed authorization (a {@link SecurityException}) and cancellation pass through,
 * as on every other export path.
 *
 * <p>Throttle: one export render at a time in this JVM, so the export never holds more than one
 * of the renderer's two slots and a clinician's print or fax always keeps the other, at least
 * {@code cds.export.eformRenderIntervalMillis} apart (default 250 ms). A busy renderer is waited
 * for, never given up on: the export waits for its own turn and for a render slot as long as it
 * takes, keeping its place in the renderer's queue (and asking again, a little later each time,
 * when the queue is full). After three consecutive browser failures or timeouts the renderer is
 * left alone for five minutes, and the export waits that out and then tries again. Every wait
 * runs the caller's wait check at least once a second: a cancellation ends it at once, the lease
 * is renewed, and, a wait not being progress, the job's stall detector still ends an attempt that
 * only ever waits. A render itself is bounded by the renderer's own deadlines (30 s to start the
 * browser, 90 s to render); a form that fails within them is tried once more by the caller
 * ({@link CdsExportEformRenderingSourceReader}). Render time is measured for every attempt.
 *
 * <p>In a process without the web application (the command-line export) there is no renderer
 * bean: every eForm is {@link Reason#NOT_IN_THIS_PROCESS}.
 *
 * @since 2026-11-13
 */
@Service
public class CdsExportEformRenderService {
    /** Why an eForm's rendered form is not in the package. Codes are fixed and carry no PHI. */
    public enum Reason {
        INCOMPLETE(
                "the renderer could not show all of the form's content, and an export never"
                        + " approves an incomplete form"),
        /** One attempt timed out; the caller tries the form once more. */
        TIMEOUT("rendering timed out"),
        /** One attempt failed in the browser; the caller tries the form once more. */
        BROWSER_FAILURE("the rendering browser failed"),
        /** The second attempt, after the patient's other eForms, timed out too. */
        TIMEOUT_RETRIED("rendering timed out on the second of two attempts"),
        /** The second attempt, after the patient's other eForms, failed in the browser too. */
        BROWSER_FAILURE_RETRIED("the rendering browser failed on the second of two attempts"),
        NOT_IN_THIS_PROCESS("the export ran outside the web application, which renders eForms"),
        CHANGED("the eForm changed while the export ran"),
        NOT_PREPARED("rendering was not attempted");

        private final String description;

        Reason(String description) {
            this.description = description;
        }

        /** A fixed, PHI-free sentence fragment for the data summary's note. */
        public String description() {
            return description;
        }

        /** True for a failure within the renderer's own deadlines, which is worth a second try. */
        public boolean retryable() {
            return this == TIMEOUT || this == BROWSER_FAILURE;
        }

        /** This reason given by a second attempt: a failure says it was tried twice. */
        public Reason onSecondAttempt() {
            return switch (this) {
                case TIMEOUT -> TIMEOUT_RETRIED;
                case BROWSER_FAILURE -> BROWSER_FAILURE_RETRIED;
                default -> this;
            };
        }
    }

    /** A spooled complete render, or the reason there is none; {@code millis} is the attempt's time. */
    public record Outcome(Reason reason, CdsExportStagedFileDto file, long millis) {
        public Outcome {
            if ((reason == null) == (file == null) || millis < 0)
                throw new IllegalArgumentException("CDS_EXPORT_EFORM_RENDER_OUTCOME_INVALID");
        }

        public boolean rendered() {
            return file != null;
        }

        static Outcome unavailable(Reason reason, long millis) {
            return new Outcome(Objects.requireNonNull(reason), null, millis);
        }

        @Override
        public String toString() {
            return "CdsExportEformRenderService.Outcome[" + (reason == null ? "RENDERED" : reason) + "]";
        }
    }

    /**
     * The renderer seam: a saved eForm rendered as {@code who}, with no approval, waiting for a
     * render slot as long as it takes and running {@code waitingForSlot} about once a second
     * while it waits (it ends the wait by throwing).
     */
    @FunctionalInterface
    interface Renderer {
        EFormBrowserPdfService.RenderedEformPdf render(
                LoggedInInfo who, int fdid, Runnable waitingForSlot) throws PDFGenerationException;
    }

    /** How the export pauses while it waits; a test advances its clock instead. */
    @FunctionalInterface
    interface Pause {
        void pause(long nanos) throws InterruptedException;
    }

    /** One export render at a time in this JVM; fair, so jobs take turns. */
    static final Semaphore EXPORT_RENDERS = new Semaphore(1, true);

    static final int BREAKER_FAILURES = 3;
    static final long BREAKER_OPEN_NANOS = TimeUnit.MINUTES.toNanos(5);

    /** The longest a wait goes without running the caller's wait check. */
    static final long CHECK_NANOS = TimeUnit.SECONDS.toNanos(1);

    /**
     * After the renderer's queue refused a render for being full, the pause before asking again:
     * the first, then doubled up to the longest (the renderer's own 30-second slot wait).
     */
    static final long FULL_QUEUE_FIRST_PAUSE_NANOS = TimeUnit.SECONDS.toNanos(1);

    static final long FULL_QUEUE_LONGEST_PAUSE_NANOS = TimeUnit.SECONDS.toNanos(30);

    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final Renderer renderer;
    private final Properties settings = new Properties();
    private final java.util.function.LongSupplier clock;
    private final Pause pause;
    private final AtomicInteger failures = new AtomicInteger();
    // The breaker is open while open is set and the clock has not reached openUntil. nanoTime
    // values are compared by difference only, never against a sentinel, so they cannot overflow.
    private final java.util.concurrent.atomic.AtomicBoolean open =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final AtomicLong openUntil = new AtomicLong();
    private final AtomicLong lastFinished = new AtomicLong(Long.MIN_VALUE);

    @Autowired
    public CdsExportEformRenderService(
            ObjectProvider<EFormBrowserPdfService> renderers,
            @Qualifier("cdsExportProperties") Properties settings,
            PlatformTransactionManager transactions) {
        this(renderer(renderers.getIfAvailable(), reads(transactions)), settings, System::nanoTime);
    }

    CdsExportEformRenderService(
            Renderer renderer, Properties settings, java.util.function.LongSupplier clock) {
        this(renderer, settings, clock, TimeUnit.NANOSECONDS::sleep);
    }

    CdsExportEformRenderService(
            Renderer renderer,
            Properties settings,
            java.util.function.LongSupplier clock,
            Pause pause) {
        this.renderer = renderer;
        this.clock = Objects.requireNonNull(clock);
        this.pause = Objects.requireNonNull(pause);
        // Read at render time, not here: a malformed export setting must not stop the web
        // application from starting, only the export job that needs it.
        this.settings.putAll(Objects.requireNonNull(settings));
    }

    private static Renderer renderer(
            EFormBrowserPdfService service, EFormBrowserPdfService.SavedEformReads reads) {
        // No approval, ever: an omission that needs one makes the render unavailable.
        return service == null
                ? null
                : (who, fdid, waiting) ->
                        service.renderSavedEformPdf(who, fdid, null, reads, waiting);
    }

    /**
     * How the renderer's own reads run for the export: the saved eForm and the effective
     * provider's {@code _eform} and {@code _measurement} privileges, each in a short read-only
     * transaction of its own whose statements wait for a slow database and are made again
     * rather than cut off by the connection's socket timeout ({@link
     * CdsExportStatementWaitReadSupport#transaction}). A privilege read the server ended at its
     * deadline would otherwise refuse the job as changed access, or print the form without the
     * measurements it shows. No transaction is held while the browser renders.
     */
    static EFormBrowserPdfService.SavedEformReads reads(PlatformTransactionManager transactions) {
        var template = new TransactionTemplate(Objects.requireNonNull(transactions));
        template.setReadOnly(true);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return new EFormBrowserPdfService.SavedEformReads() {
            @Override
            public <T> T run(java.util.function.Supplier<T> read) {
                return CdsExportStatementWaitReadSupport.transaction(
                        template,
                        status -> {
                            CdsExportStatementWaitReadSupport.arm();
                            return read.get();
                        });
            }
        };
    }

    /** True when this process can render eForms at all (the web application). */
    public boolean available() {
        return renderer != null;
    }

    /**
     * Renders one eForm and spools its PDF, or says why not. Waits for its turn, for an open
     * breaker to close and for a render slot as long as each takes; only a render that ran and
     * failed, or a form the renderer could not show completely, is a reason.
     *
     * @param fdid the saved eForm, already selected as current and patient-linked
     * @param identity the freshly authorized effective provider (see {@code authorizeRendering})
     * @param spool the capture's encrypted spool
     * @param checkpoint cancellation and lease check after the render's own work; called outside
     *     every transaction
     * @param waiting the check while waiting ({@code waitCheckpoint}): ends the wait by throwing
     *     once the attempt has ended, and is not progress; called outside every transaction, at
     *     least once a second while the export waits
     * @throws SecurityException when the renderer refuses the identity's access
     * @throws IOException when the spool cannot be written
     */
    public Outcome render(
            int fdid,
            LoggedInInfo identity,
            CdsExportNativeSourceReader.Spool spool,
            Runnable checkpoint,
            Runnable waiting)
            throws IOException {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(spool);
        Objects.requireNonNull(checkpoint);
        Objects.requireNonNull(waiting);
        if (fdid < 1) throw new IllegalArgumentException("CDS_EXPORT_EFORM_RENDER_SCOPE_INVALID");
        if (renderer == null) return Outcome.unavailable(Reason.NOT_IN_THIS_PROCESS, 0);
        long intervalNanos =
                TimeUnit.MILLISECONDS.toNanos(
                        CdsExportActivePlanService.number(
                                settings, "eformRenderIntervalMillis", 250, 0, 60_000));
        acquire(waiting);
        try {
            // A renderer that failed three times in a row is left alone for five minutes; the
            // export waits that out and then tries, rather than giving the form up.
            awaitBreaker(waiting);
            pace(waiting, intervalNanos);
            // The export's own wait for a slot inside the renderer: when it ends (a
            // cancellation, a lost lease), that is the export's outcome, not the renderer's.
            var ended = new java.util.concurrent.atomic.AtomicReference<RuntimeException>();
            Runnable waitingForSlot =
                    () -> {
                        try {
                            waiting.run();
                        } catch (RuntimeException stop) {
                            ended.set(stop);
                            throw stop;
                        }
                    };
            long fullQueuePause = FULL_QUEUE_FIRST_PAUSE_NANOS;
            long started;
            EFormBrowserPdfService.RenderedEformPdf made;
            while (true) {
                started = clock.getAsLong();
                // Only the renderer's own failures are classified; a cancellation or lease check
                // below always propagates.
                try {
                    made = renderer.render(identity, fdid, waitingForSlot);
                    break;
                } catch (EformContentUnavailableException incomplete) {
                    checkpoint.run();
                    return done(Reason.INCOMPLETE, started, false);
                } catch (PDFGenerationException failed) {
                    if (failed.isRetryable()) {
                        // No render started: the renderer's queue was full. Ask again later,
                        // a little later each time; the renderer is healthy, so the breaker
                        // does not count it.
                        waitFor(waiting, fullQueuePause);
                        fullQueuePause =
                                Math.min(fullQueuePause * 2, FULL_QUEUE_LONGEST_PAUSE_NANOS);
                        continue;
                    }
                    checkpoint.run();
                    return done(
                            timedOut(failed) ? Reason.TIMEOUT : Reason.BROWSER_FAILURE,
                            started,
                            true);
                } catch (SecurityException denied) {
                    throw denied;
                } catch (RuntimeException failed) {
                    if (failed == ended.get()) throw failed;
                    // The database gone is not a browser failure: it stops the job under its own
                    // code (CDS_EXPORT_DATABASE_UNAVAILABLE), and the job can be resumed.
                    if (io.github.carlos_emr.carlos.demographic.cdsexport.support
                            .CdsExportDatabaseUnavailableException.lost(failed)) throw failed;
                    checkpoint.run();
                    return done(Reason.BROWSER_FAILURE, started, true);
                }
            }
            try (var rendered = made) {
                checkpoint.run();
                long bytes;
                boolean pdf;
                try {
                    bytes = Files.size(rendered.path());
                    pdf = pdf(rendered.path());
                } catch (IOException unreadable) {
                    // The renderer's own output vanished or cannot be read: a renderer fault.
                    return done(Reason.BROWSER_FAILURE, started, true);
                }
                // A rendered form of any size is spooled whole (streamed from the renderer's
                // file): cds.export.maxDocumentBytes, which dropped a larger one, is retired.
                if (!pdf) return done(Reason.BROWSER_FAILURE, started, true);
                // A spool write failure is the export's own storage failing: it stops the job.
                var file = spool.stage("EFORM_RENDERING", out -> Files.copy(rendered.path(), out));
                if (file.plaintextBytes() != bytes)
                    throw new IOException("CDS_EXPORT_EFORM_RENDER_SPOOL_INVALID");
                failures.set(0);
                return new Outcome(null, file, elapsed(started));
            }
        } finally {
            lastFinished.set(clock.getAsLong());
            EXPORT_RENDERS.release();
        }
    }

    private Outcome done(Reason reason, long started, boolean rendererFault) {
        if (rendererFault && failures.incrementAndGet() >= BREAKER_FAILURES) {
            openUntil.set(clock.getAsLong() + BREAKER_OPEN_NANOS);
            open.set(true);
            failures.set(0);
        } else if (!rendererFault) {
            failures.set(0);
        }
        return Outcome.unavailable(reason, elapsed(started));
    }

    private long elapsed(long started) {
        return Math.max(0, TimeUnit.NANOSECONDS.toMillis(clock.getAsLong() - started));
    }

    /** Waits for the export's turn as long as it takes, running the wait check every second. */
    private void acquire(Runnable waiting) {
        while (true) {
            waiting.run();
            try {
                if (EXPORT_RENDERS.tryAcquire(1, TimeUnit.SECONDS)) return;
            } catch (InterruptedException interrupted) {
                throw cancelled(waiting);
            }
        }
    }

    /** Waits until an open breaker closes; returns at once when it is closed. */
    private void awaitBreaker(Runnable waiting) {
        while (open.get()) {
            long remaining = openUntil.get() - clock.getAsLong();
            if (remaining <= 0) return;
            waitFor(waiting, remaining);
        }
    }

    /** Keeps export renders at least the configured interval apart. */
    private void pace(Runnable waiting, long intervalNanos) {
        long previous = lastFinished.get();
        if (previous == Long.MIN_VALUE || intervalNanos == 0) return;
        long remaining = previous + intervalNanos - clock.getAsLong();
        if (remaining > 0) waitFor(waiting, remaining);
    }

    /** Waits {@code nanos}, running the wait check before each pause of at most a second. */
    private void waitFor(Runnable waiting, long nanos) {
        long until = clock.getAsLong() + nanos;
        long remaining = nanos;
        while (remaining > 0) {
            waiting.run();
            try {
                pause.pause(Math.min(remaining, CHECK_NANOS));
            } catch (InterruptedException interrupted) {
                throw cancelled(waiting);
            }
            remaining = until - clock.getAsLong();
        }
    }

    /** An interrupted wait: the attempt's own outcome if the check has one, else a cancellation. */
    private static java.util.concurrent.CancellationException cancelled(Runnable waiting) {
        Thread.currentThread().interrupt();
        waiting.run();
        return new java.util.concurrent.CancellationException("CDS_EXPORT_WAIT_INTERRUPTED");
    }

    /** The renderer's own deadline message; its other failures are browser failures. */
    private static boolean timedOut(PDFGenerationException failed) {
        String message = failed.getMessage();
        return message != null && message.contains("timed out");
    }

    private static boolean pdf(java.nio.file.Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return Arrays.equals(input.readNBytes(PDF.length), PDF);
        }
    }
}
