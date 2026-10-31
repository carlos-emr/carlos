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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.service;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderRequest;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderResult;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderSource;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an HTML document into a US Letter PDF with Flying Saucer: the one entry point of the HTML-to-PDF
 * module. Callers build an {@link HtmlPdfRenderRequest}, call {@link #render}, and decide what to tell
 * their own users; nothing here knows about HTTP, sessions or patients.
 *
 * <p><b>What it does.</b> A render takes a bounded wait for one of two <em>render slots</em>, then runs
 * <em>attempts</em> on the rungs of a ladder until one produces a valid PDF: {@code NORMAL} (the document
 * as authored), {@code SAFE} (without the author's styles and inline graphics) and {@code TEXT_ONLY} (the
 * text in reading order). Failure is the last resort: a document that cannot be laid out is printed more
 * plainly, with a note on the page saying so, and only a document that cannot be printed at all throws.
 * Each attempt starts again from the request's HTML with a fresh set of degradations, so nothing from a
 * failed attempt leaks into the next; only what is true of the whole render (no webapp root, no slot)
 * carries forward. HTML larger than {@link #TEXT_ONLY_HTML_CHARS} characters, not counting
 * {@code data:} payloads, starts at {@code TEXT_ONLY}.</p>
 *
 * <p><b>Resources.</b> At most {@link #RENDER_SLOTS} renders hold a slot at once, and a render waits at most
 * {@link #SLOT_WAIT} for one: Flying Saucer's layout cannot be interrupted, so the module bounds what it
 * starts instead of timing it out. A quarter of the heap is set aside for images and shared equally between
 * the slots, so how a document's images are reduced depends only on the document and the heap size, never on
 * load. A render that finds no slot free is not refused: it prints with every raster image as a box and a
 * note on the page ({@code IMAGES_OMITTED_FOR_MEMORY}). The slot, the private base directory and each
 * attempt's image directory are owned by this method and released whatever happens.</p>
 *
 * <p><b>Failures.</b> A whitespace-only document, bundled fonts that cannot be loaded, a base directory that
 * cannot be made, an interrupted wait and an exhausted ladder throw {@link PDFGenerationException}. An
 * exhausted ladder carries the last attempt's internal exception as its cause; that exception names the
 * mode, the phase and the original exception's class only, never its message (a Flying Saucer error can
 * quote document text), so a caller that logs the whole exception logs no PHI. An {@link Error} other than a
 * {@link LinkageError} or a {@link StackOverflowError} (an {@link OutOfMemoryError}, for one) is not caught.
 * Invalid requests are rejected by {@link HtmlPdfRenderRequest} itself.</p>
 *
 * <p><b>Logging.</b> One line with the token {@code htmlpdf-render} per successful render, with the source,
 * the mode that succeeded, the duration and the degradation names, at the level of its most serious
 * degradation ({@code DEBUG} when there is none); one {@code htmlpdf-attempt-failed} line per failed attempt
 * ({@code WARN}, {@code ERROR} for a {@link LinkageError}, which is a packaging defect). Neither contains a
 * path, a file name, a URL or any document content, and the exception thrown is not also logged here.</p>
 *
 * <p>Safe for concurrent use: the beans it uses are stateless, and each render's objects stay on its
 * thread.</p>
 *
 * @since 2026-10-29
 */
@Service
public class HtmlPdfRenderService {

    private static final Logger logger = MiscUtils.getLogger();

    /**
     * How many renders may hold a slot at once. {@code ConvertToEdoc} is already serialised, so one slot
     * serves a form render and the other a Messenger render.
     */
    static final int RENDER_SLOTS = 2;

    /**
     * How long a render waits for a slot. It is the module's only wait: a fax with several attachments
     * queued behind {@code ConvertToEdoc}'s lock stays far inside the reverse proxy's 300 s read timeout.
     */
    static final Duration SLOT_WAIT = Duration.ofSeconds(10);

    /** The heap is divided by this to give the memory set aside for images, shared by all slots. */
    static final int HEAP_DIVISOR_FOR_IMAGES = 4;

    /**
     * HTML longer than this many characters, not counting {@code data:} payloads, goes straight to
     * {@code TEXT_ONLY}.
     *
     * <p>Measured when the module was added, with a one-off tool that went with wkhtmltopdf (Flying Saucer
     * 10.7.0, JDK 25, 4 cores of a 2.1 GHz Xeon, 2 GB heap, a four-column laboratory table of 77 characters
     * a row). A normal-mode layout takes 0.6 s at 153,000 characters (2000 rows, the reference fixture),
     * 3.4 s at 769,000, 7.9 s at 1,003,000 (13,000 rows), 15.6 s at 1,549,000, 26.9 s at 2,017,000 and
     * 31.7 s at 2,173,000: 30 s is reached at about 2.1 million. The limit is the largest round number
     * for which the worst case of the ladder stays under 60 s (a 10 s wait for a slot, a normal-mode
     * attempt, a safe-mode attempt that costs as much, and the text-only attempt): 10 + 7.9 + 7.9 + 0.8
     * = 27 s, where 2 million would give 66 s. Real documents are far below it. The time grows with the
     * square of the number of rows (Flying Saucer looks for the next sibling of a block while it collapses
     * margins), so a document of very short rows reaches the same time with fewer characters.</p>
     */
    static final int TEXT_ONLY_HTML_CHARS = 1_000_000;

    /**
     * A {@code data:} URI that stands on its own: it does not end a longer word ({@code metadata:} in a
     * paragraph is text), it has a media type of up to 200 characters and a comma, and its payload runs to the
     * character that ends it in an attribute or in CSS {@code url()}: a quote, a parenthesis, white space, or
     * the start or the end of a tag (an unquoted attribute value ends at {@code >}, and must not take the
     * markup after it along).
     */
    private static final Pattern DATA_URI = Pattern.compile("(?<![\\w-])data:[^,\"'()<>\\s]{0,200},[^\"'()<>\\s]+",
            Pattern.CASE_INSENSITIVE);

    private final PrintDocumentComposer composer;
    private final PrintImageService imageService;
    private final PrintLayoutService layoutService;
    private final RenderedPdfValidator validator;
    private final BundledFontFileService fontFiles;

    private final Duration slotWait;
    private final long slotImageMemoryBytes;
    private final int textOnlyHtmlChars;
    private final Path tempRoot;

    /** Fair, so a render that has waited longest gets the next free slot. */
    private final Semaphore slots = new Semaphore(RENDER_SLOTS, true);

    /**
     * Production wiring: two slots, a 10 s wait, a quarter of the heap for images shared between the
     * slots, the JVM's temporary directory.
     *
     * @param composer the document rules
     * @param imageService the image step
     * @param layoutService layout and placement on Letter
     * @param validator the output checks
     * @param fontFiles the bundled fonts
     */
    @Autowired
    public HtmlPdfRenderService(PrintDocumentComposer composer, PrintImageService imageService,
            PrintLayoutService layoutService, RenderedPdfValidator validator, BundledFontFileService fontFiles) {
        this(composer, imageService, layoutService, validator, fontFiles, SLOT_WAIT,
                Runtime.getRuntime().maxMemory() / HEAP_DIVISOR_FOR_IMAGES / RENDER_SLOTS, TEXT_ONLY_HTML_CHARS,
                Path.of(System.getProperty("java.io.tmpdir")));
    }

    /**
     * For tests, which set the limits they exercise.
     *
     * @param slotWait how long a render waits for a slot
     * @param slotImageMemoryBytes the memory share for images of a render that holds a slot
     * @param textOnlyHtmlChars the HTML length above which a render starts at {@code TEXT_ONLY}
     * @param tempRoot where the private base directory is made
     */
    HtmlPdfRenderService(PrintDocumentComposer composer, PrintImageService imageService,
            PrintLayoutService layoutService, RenderedPdfValidator validator, BundledFontFileService fontFiles,
            Duration slotWait, long slotImageMemoryBytes, int textOnlyHtmlChars, Path tempRoot) {
        this.composer = Objects.requireNonNull(composer, "composer");
        this.imageService = Objects.requireNonNull(imageService, "imageService");
        this.layoutService = Objects.requireNonNull(layoutService, "layoutService");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.fontFiles = Objects.requireNonNull(fontFiles, "fontFiles");
        this.slotWait = Objects.requireNonNull(slotWait, "slotWait");
        this.slotImageMemoryBytes = slotImageMemoryBytes;
        this.textOnlyHtmlChars = textOnlyHtmlChars;
        this.tempRoot = Objects.requireNonNull(tempRoot, "tempRoot");
    }

    /**
     * Renders a document to a US Letter PDF.
     *
     * @param request the document, the directory its relative references resolve against and the
     *        directories it may read from
     * @return a complete PDF and every step down the render took to produce it (empty when the document
     *         printed as authored)
     * @throws PDFGenerationException if the HTML is only white space, the bundled fonts cannot be loaded,
     *         the private base directory cannot be made, the wait for a render slot is interrupted (the
     *         thread's interrupt status is restored and the exception is retryable), or no rung of the
     *         ladder produced a valid PDF; the message and the cause name no path and no document content
     */
    public HtmlPdfRenderResult render(HtmlPdfRenderRequest request) throws PDFGenerationException {
        Objects.requireNonNull(request, "request");
        long started = System.nanoTime();
        if (request.html().isBlank()) {
            throw new PDFGenerationException("There is no HTML to convert to PDF");
        }
        // Every rung needs DejaVu Sans, so a build defect is reported at once instead of retried.
        BundledFontSet fonts = fontFiles.fontSet();

        PrintMode first = exceedsTextOnlyLimit(request.html()) ? PrintMode.TEXT_ONLY : PrintMode.NORMAL;
        Set<HtmlPdfRenderDegradation> renderLevel = EnumSet.noneOf(HtmlPdfRenderDegradation.class);
        Path privateBase = null;
        boolean slotHeld = false;
        try {
            Path baseDirectory = request.baseDirectory();
            if (baseDirectory == null) {
                privateBase = makePrivateBase();
                baseDirectory = privateBase;
                renderLevel.add(HtmlPdfRenderDegradation.WEBAPP_ROOT_UNAVAILABLE);
            }
            slotHeld = takeSlot();
            long imageMemory = slotHeld ? slotImageMemoryBytes : 0;

            List<Path> allowedDirectories = new ArrayList<>(request.allowedDirectories());
            if (privateBase != null) {
                allowedDirectories.add(privateBase);
            }
            HtmlPdfRenderException last = null;
            for (PrintMode mode : PrintMode.values()) {
                if (mode.compareTo(first) < 0) {
                    continue;
                }
                Set<HtmlPdfRenderDegradation> degradations = EnumSet.noneOf(HtmlPdfRenderDegradation.class);
                degradations.addAll(renderLevel);
                // The service records these itself, before the attempt, so the document note can list them.
                if (mode == PrintMode.SAFE) {
                    degradations.add(HtmlPdfRenderDegradation.SAFE_MODE);
                } else if (mode == PrintMode.TEXT_ONLY) {
                    degradations.add(HtmlPdfRenderDegradation.TEXT_ONLY);
                }
                try {
                    byte[] pdf = attempt(mode, request.html(), baseDirectory, allowedDirectories, fonts, imageMemory,
                            degradations);
                    logOutcome(request.source(), mode, started, degradations);
                    return new HtmlPdfRenderResult(pdf, degradations);
                } catch (HtmlPdfRenderException failure) {
                    logFailedAttempt(request.source(), failure);
                    last = failure;
                }
            }
            throw new PDFGenerationException("HTML could not be converted to PDF", last);
        } finally {
            if (slotHeld) {
                slots.release();
            }
            PreparedPrintImages.deleteRecursively(privateBase);
        }
    }

    // ---- one attempt ------------------------------------------------------------------------

    /**
     * One attempt on one rung: the document rules, the images, the style sheet and note, layout, placement
     * on Letter and the output check. The attempt's image directory is deleted when it ends.
     */
    private byte[] attempt(PrintMode mode, String html, Path baseDirectory, List<Path> allowedDirectories,
            BundledFontSet fonts, long imageMemory, Set<HtmlPdfRenderDegradation> degradations) {
        HtmlPdfRenderException.Phase phase = HtmlPdfRenderException.Phase.PREPARE;
        try {
            Document document = composer.prepare(html, mode, degradations);
            phase = HtmlPdfRenderException.Phase.IMAGES;
            try (PreparedPrintImages images = imageService.prepare(document, baseDirectory, allowedDirectories, mode,
                    imageMemory, degradations)) {
                phase = HtmlPdfRenderException.Phase.PREPARE;
                String xhtml = composer.finish(document, mode, fonts, degradations);
                List<Path> readable = new ArrayList<>(allowedDirectories);
                if (images.directory() != null) {
                    readable.add(images.directory());
                }
                phase = HtmlPdfRenderException.Phase.LAYOUT_PASS_1;
                byte[] pdf = layoutService.render(mode, xhtml, baseDirectory, readable, fonts, degradations);
                phase = HtmlPdfRenderException.Phase.VALIDATE;
                RenderedPdfValidator.Result check = validator.validate(pdf);
                if (!check.valid()) {
                    // The messages name the failed check only, so they are safe to log.
                    logger.warn("htmlpdf-output-check-failed mode={} checks={}", mode, check.messages());
                    throw new HtmlPdfRenderException(mode, phase, new OutputCheckFailed());
                }
                return pdf;
            }
        } catch (HtmlPdfRenderException failure) {
            throw failure;
        } catch (Exception | LinkageError | StackOverflowError failure) {
            // A stack overflow from a deeply nested document unwinds to here; the attempt's objects are
            // discarded, so the next rung can still run. Other Errors (OutOfMemoryError) are not caught.
            throw new HtmlPdfRenderException(mode, phase, failure);
        }
    }

    /** Stands for the failed check in the attempt's exception, whose message names its class only. */
    private static final class OutputCheckFailed extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        OutputCheckFailed() {
            super("The rendered PDF failed its checks");
        }
    }

    // ---- slots and directories --------------------------------------------------------------

    /**
     * Waits for a render slot.
     *
     * @return {@code true} if one was taken, {@code false} if none was free within the wait
     * @throws PDFGenerationException if the wait was interrupted; the interrupt status is restored
     */
    private boolean takeSlot() throws PDFGenerationException {
        try {
            return slots.tryAcquire(slotWait.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            // Nothing has started, so the caller may try again; rendering on with the flag set would only
            // fail every file read that follows.
            throw new PDFGenerationException("Interrupted while waiting to convert HTML to PDF", true);
        }
    }

    /** An empty directory, readable by this render alone, standing in for a webapp root that is not known. */
    private Path makePrivateBase() throws PDFGenerationException {
        try {
            return Files.createTempDirectory(tempRoot, "carlos-pdf-base-").toAbsolutePath().normalize();
        } catch (IOException | RuntimeException e) {
            throw new PDFGenerationException("HTML could not be converted to PDF: no private working directory", e);
        }
    }

    // ---- size limit -------------------------------------------------------------------------

    /** Whether the HTML, not counting the payloads of {@code data:} URIs, is over the text-only limit. */
    private boolean exceedsTextOnlyLimit(String html) {
        if (html.length() <= textOnlyHtmlChars) {
            return false;
        }
        long counted = html.length();
        Matcher payloads = DATA_URI.matcher(html);
        while (payloads.find()) {
            counted -= payloads.end() - payloads.start();
        }
        return counted > textOnlyHtmlChars;
    }

    // ---- logging ----------------------------------------------------------------------------

    private static void logOutcome(HtmlPdfRenderSource source, PrintMode mode, long startedNanos,
            Set<HtmlPdfRenderDegradation> degradations) {
        // Log4j levels are ordered the other way round: the smallest intLevel is the most serious.
        Level level = degradations.stream().map(HtmlPdfRenderDegradation::logLevel)
                .min(Comparator.comparingInt(Level::intLevel)).orElse(Level.DEBUG);
        logger.log(level, "htmlpdf-render source={} mode={} durationMs={} degradations={}", source, mode,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos), degradations);
    }

    private static void logFailedAttempt(HtmlPdfRenderSource source, HtmlPdfRenderException failure) {
        // The message holds the mode, the phase and the original exception's class, never its message.
        logger.log(failure.linkageError() ? Level.ERROR : Level.WARN, "htmlpdf-attempt-failed source={} {}", source,
                failure.getMessage());
    }

    // ---- for tests --------------------------------------------------------------------------

    /** The memory share for images of a render that holds a slot. */
    long slotImageMemoryBytes() {
        return slotImageMemoryBytes;
    }

    /** How many render slots are free right now. */
    int availableSlots() {
        return slots.availablePermits();
    }

    /** How many renders are waiting for a slot right now. */
    int rendersWaitingForSlot() {
        return slots.getQueueLength();
    }

    /** Whether the slots go to the render that has waited longest. */
    boolean slotsAreFair() {
        return slots.isFair();
    }
}
