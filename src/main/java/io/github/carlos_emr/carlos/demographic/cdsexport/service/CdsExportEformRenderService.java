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
 * <p>Throttle: one export render at a time in this JVM (the renderer has two slots, so a
 * clinician's print or fax always keeps one), at least {@code cds.export.eformRenderIntervalMillis}
 * apart (default 250 ms). Each render is bounded by the renderer's own deadlines (30 s for a slot,
 * 30 s to start the browser, 90 s to render). After three consecutive browser failures or timeouts
 * the renderer is treated as unavailable for five minutes, so a broken browser costs one data
 * summary note per eForm, not minutes per eForm. Render time is measured for every attempt.
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
        TIMEOUT("rendering timed out"),
        BROWSER_FAILURE("the rendering browser failed"),
        RENDERER_BUSY("the renderer stayed busy"),
        RENDERER_UNAVAILABLE("the rendering browser was unavailable"),
        NOT_IN_THIS_PROCESS("the export ran outside the web application, which renders eForms"),
        CHANGED("the eForm changed while the export ran"),
        TOO_LARGE("the rendered form exceeded the export's document size limit"),
        NOT_PREPARED("rendering was not attempted");

        private final String description;

        Reason(String description) {
            this.description = description;
        }

        /** A fixed, PHI-free sentence fragment for the data summary's note. */
        public String description() {
            return description;
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

    /** The renderer seam: a saved eForm rendered as {@code who}, with no approval. */
    @FunctionalInterface
    interface Renderer {
        EFormBrowserPdfService.RenderedEformPdf render(LoggedInInfo who, int fdid)
                throws PDFGenerationException;
    }

    /** One export render at a time in this JVM; fair, so jobs take turns. */
    static final Semaphore EXPORT_RENDERS = new Semaphore(1, true);

    static final int BREAKER_FAILURES = 3;
    static final long BREAKER_OPEN_NANOS = TimeUnit.MINUTES.toNanos(5);
    static final long WAIT_NANOS = TimeUnit.MINUTES.toNanos(15);

    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final Renderer renderer;
    private final Properties settings = new Properties();
    private final java.util.function.LongSupplier clock;
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
            @Qualifier("cdsExportProperties") Properties settings) {
        this(renderer(renderers.getIfAvailable()), settings, System::nanoTime);
    }

    CdsExportEformRenderService(
            Renderer renderer, Properties settings, java.util.function.LongSupplier clock) {
        this.renderer = renderer;
        this.clock = Objects.requireNonNull(clock);
        // Read at render time, not here: a malformed export setting must not stop the web
        // application from starting, only the export job that needs it.
        this.settings.putAll(Objects.requireNonNull(settings));
    }

    private static Renderer renderer(EFormBrowserPdfService service) {
        // No approval, ever: an omission that needs one makes the render unavailable.
        return service == null ? null : (who, fdid) -> service.renderSavedEformPdf(who, fdid, null);
    }

    /** True when this process can render eForms at all (the web application). */
    public boolean available() {
        return renderer != null;
    }

    /**
     * Renders one eForm and spools its PDF, or says why not.
     *
     * @param fdid the saved eForm, already selected as current and patient-linked
     * @param identity the freshly authorized effective provider (see {@code authorizeRendering})
     * @param spool the capture's encrypted spool
     * @param checkpoint cancellation and lease check; called outside every transaction
     * @throws SecurityException when the renderer refuses the identity's access
     * @throws IOException when the spool cannot be written
     */
    public Outcome render(
            int fdid,
            LoggedInInfo identity,
            CdsExportNativeSourceReader.Spool spool,
            Runnable checkpoint)
            throws IOException {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(spool);
        Objects.requireNonNull(checkpoint);
        if (fdid < 1) throw new IllegalArgumentException("CDS_EXPORT_EFORM_RENDER_SCOPE_INVALID");
        if (renderer == null) return Outcome.unavailable(Reason.NOT_IN_THIS_PROCESS, 0);
        long intervalNanos =
                TimeUnit.MILLISECONDS.toNanos(
                        CdsExportActivePlanService.number(
                                settings, "eformRenderIntervalMillis", 250, 0, 60_000));
        long maximumBytes =
                CdsExportActivePlanService.number(
                        settings, "maxDocumentBytes", 1_073_741_824L, 1, 1L << 40);
        long started = clock.getAsLong();
        if (open.get() && started - openUntil.get() < 0)
            return Outcome.unavailable(Reason.RENDERER_UNAVAILABLE, 0);
        if (!acquire(checkpoint)) return Outcome.unavailable(Reason.RENDERER_BUSY, elapsed(started));
        try {
            pace(checkpoint, intervalNanos);
            started = clock.getAsLong();
            try (var rendered = renderer.render(identity, fdid)) {
                checkpoint.run();
                long bytes = Files.size(rendered.path());
                if (bytes > maximumBytes) return done(Reason.TOO_LARGE, started, false);
                if (!pdf(rendered.path())) return done(Reason.BROWSER_FAILURE, started, true);
                var file = spool.stage("EFORM_RENDERING", out -> Files.copy(rendered.path(), out));
                if (file.plaintextBytes() != bytes) throw new IOException("CDS_EXPORT_EFORM_RENDER_SPOOL_INVALID");
                failures.set(0);
                return new Outcome(null, file, elapsed(started));
            } catch (EformContentUnavailableException incomplete) {
                checkpoint.run();
                return done(Reason.INCOMPLETE, started, false);
            } catch (PDFGenerationException failed) {
                checkpoint.run();
                if (failed.isRetryable()) return done(Reason.RENDERER_BUSY, started, false);
                return done(timedOut(failed) ? Reason.TIMEOUT : Reason.BROWSER_FAILURE, started, true);
            } catch (SecurityException denied) {
                throw denied;
            } catch (RuntimeException failed) {
                checkpoint.run();
                return done(Reason.BROWSER_FAILURE, started, true);
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

    /** Waits for the export render slot, observing cancellation every second. */
    private boolean acquire(Runnable checkpoint) {
        long deadline = clock.getAsLong() + WAIT_NANOS;
        while (true) {
            checkpoint.run();
            try {
                if (EXPORT_RENDERS.tryAcquire(1, TimeUnit.SECONDS)) return true;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                checkpoint.run();
                throw new java.util.concurrent.CancellationException("CDS_EXPORT_WAIT_INTERRUPTED");
            }
            if (clock.getAsLong() - deadline > 0) return false;
        }
    }

    /** Keeps export renders at least the configured interval apart. */
    private void pace(Runnable checkpoint, long intervalNanos) {
        long previous = lastFinished.get();
        if (previous == Long.MIN_VALUE || intervalNanos == 0) return;
        long remaining = previous + intervalNanos - clock.getAsLong();
        while (remaining > 0) {
            checkpoint.run();
            try {
                TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.SECONDS.toNanos(1)));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                checkpoint.run();
                throw new java.util.concurrent.CancellationException("CDS_EXPORT_WAIT_INTERRUPTED");
            }
            remaining = previous + intervalNanos - clock.getAsLong();
        }
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
