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

import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestImages;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.TestPdfs;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderRequest;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderResult;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderSource;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.engine.BundledFontSet;
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.PrintMessages;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link HtmlPdfRenderService}: the ladder, what a failed attempt leaks, the size limit and
 * the log line. They run the real document rules, image step, layout and output check; failures are injected
 * with Mockito spies on the layout, image and validation steps. The slot behaviour is in
 * {@link HtmlPdfRenderServiceSlotUnitTest}.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("HtmlPdfRenderService Unit Tests")
class HtmlPdfRenderServiceUnitTest {

    @TempDir
    Path tempRoot;

    @TempDir
    Path webRoot;

    private PrintDocumentComposer composer;
    private PrintLayoutService layout;
    private PrintImageService images;
    private RenderedPdfValidator validator;
    private HtmlPdfRenderService service;

    @BeforeEach
    void setUp() {
        composer = Mockito.spy(new PrintDocumentComposer());
        layout = Mockito.spy(new PrintLayoutService());
        images = Mockito.spy(new PrintImageService(tempRoot));
        validator = Mockito.spy(new RenderedPdfValidator());
        service = serviceWith(HtmlPdfTestServices.sharedFontFiles(), 100_000);
    }

    private HtmlPdfRenderService serviceWith(BundledFontFileService fontFiles, int textOnlyHtmlChars) {
        return new HtmlPdfRenderService(composer, images, layout, validator, fontFiles,
                Duration.ofMillis(200), 256L << 20, textOnlyHtmlChars, tempRoot);
    }

    private HtmlPdfRenderRequest request(String html) {
        return new HtmlPdfRenderRequest(html, webRoot, List.of(webRoot), HtmlPdfRenderSource.FORM);
    }

    private static String page(String body) {
        return "<html><head><title>FAKE-Title</title></head><body>" + body + "</body></html>";
    }

    @Test
    @DisplayName("should never print hidden attributes or hidden ancestor content on any rung")
    void shouldNotPrintHiddenContent_inEveryRenderMode() throws Exception {
        String html = page("<p>FAKE-visible</p><input hidden value='FAKE-hidden-input'>"
                + "<textarea hidden>FAKE-hidden-note</textarea><div hidden><p>FAKE-hidden-child</p></div>"
                + "<span hidden='false'>FAKE-hidden-false</span>");
        for (PrintMode mode : PrintMode.values()) {
            if (mode == PrintMode.SAFE) {
                failLayoutIn(new IllegalStateException("forced"), PrintMode.NORMAL);
            } else if (mode == PrintMode.TEXT_ONLY) {
                failLayoutIn(new IllegalStateException("forced"), PrintMode.NORMAL, PrintMode.SAFE);
            }
            HtmlPdfRenderResult result = service.render(request(html));
            assertThat(text(result)).as("printed text in %s", mode)
                    .contains("FAKE-visible").doesNotContain("FAKE-hidden");
        }
    }

    private static String text(HtmlPdfRenderResult result) throws IOException {
        return String.join("\n", TestPdfs.pageTexts(result.pdf())).replaceAll("\\s+", " ").strip();
    }

    /** Makes the layout step throw {@code failure} on the rungs named, and run for real on the others. */
    private void failLayoutIn(Throwable failure, PrintMode... modes) {
        Set<PrintMode> failing = Set.of(modes);
        doAnswer(invocation -> {
            if (failing.contains(invocation.<PrintMode>getArgument(0))) {
                throw failure;
            }
            return invocation.callRealMethod();
        }).when(layout).render(any(), any(), any(), any(), any(), any());
    }

    private List<String> directoriesIn(Path directory, String prefix) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.map(path -> path.getFileName().toString()).filter(name -> name.startsWith(prefix)).sorted().toList();
        }
    }

    // ---- the ladder -------------------------------------------------------------------------

    @Test
    @DisplayName("should print in safe mode, with the note first, when the normal layout fails")
    void shouldUseSafeMode_whenNormalLayoutFails() throws Exception {
        failLayoutIn(new IllegalStateException("FAKE-secret layout text"), PrintMode.NORMAL);

        HtmlPdfRenderResult result = service.render(request(page("<p>FAKE-Body paragraph</p>")));

        assertThat(result.degradations()).containsExactly(HtmlPdfRenderDegradation.SAFE_MODE);
        assertThat(text(result)).startsWith(PrintMessages.noteFor(HtmlPdfRenderDegradation.SAFE_MODE).orElseThrow())
                .contains("FAKE-Body paragraph");
        verify(layout, times(2)).render(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("should print the text only, with the note first, when safe mode fails too")
    void shouldUseTextOnly_whenSafeModeFails() throws Exception {
        failLayoutIn(new IllegalStateException("FAKE-secret layout text"), PrintMode.NORMAL, PrintMode.SAFE);

        HtmlPdfRenderResult result = service.render(request(page("<h1>FAKE-Heading</h1><p>FAKE-Body paragraph</p>")));

        // The attempt that failed does not leave its SAFE_MODE behind: each attempt has its own set.
        assertThat(result.degradations()).containsExactly(HtmlPdfRenderDegradation.TEXT_ONLY);
        assertThat(text(result)).startsWith(PrintMessages.noteFor(HtmlPdfRenderDegradation.TEXT_ONLY).orElseThrow())
                .contains("FAKE-Heading").contains("FAKE-Body paragraph");
    }

    @Test
    @DisplayName("should throw a PDFGenerationException whose chain holds no document text when every rung fails")
    void shouldThrowPdfGenerationException_whenAllModesFail() {
        failLayoutIn(new IllegalStateException("FAKE-secret layout text"), PrintMode.values());

        assertThatThrownBy(() -> service.render(request(page("<p>FAKE-Body</p>"))))
                .isInstanceOfSatisfying(PDFGenerationException.class, thrown -> {
                    assertThat(thrown.getMessage()).isEqualTo("HTML could not be converted to PDF");
                    assertThat(thrown.isRetryable()).isFalse();
                    assertThat(thrown.getCause()).isInstanceOf(HtmlPdfRenderException.class);
                    HtmlPdfRenderException last = (HtmlPdfRenderException) thrown.getCause();
                    assertThat(last.mode()).isEqualTo(PrintMode.TEXT_ONLY);
                    assertThat(last.phase()).isEqualTo(HtmlPdfRenderException.Phase.LAYOUT_PASS_1);
                    for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
                        assertThat(String.valueOf(cause.getMessage())).doesNotContain("FAKE-");
                    }
                });
        verify(layout, times(3)).render(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("should retry a LinkageError on the next rung and log it at ERROR with the class only")
    void shouldRetryAndLogError_forLinkageError() throws Exception {
        failLayoutIn(new NoClassDefFoundError("FAKE-missing class"), PrintMode.NORMAL);

        try (LogCapture capture = LogCapture.forLogger(HtmlPdfRenderService.class)) {
            HtmlPdfRenderResult result = service.render(request(page("<p>FAKE-Body</p>")));

            assertThat(result.degradations()).containsExactly(HtmlPdfRenderDegradation.SAFE_MODE);
            List<LogEvent> failures = capture.events().stream()
                    .filter(event -> event.getMessage().getFormattedMessage().startsWith("htmlpdf-attempt-failed")).toList();
            assertThat(failures).hasSize(1);
            assertThat(failures.get(0).getLevel()).isEqualTo(Level.ERROR);
            assertThat(failures.get(0).getMessage().getFormattedMessage()).contains("source=FORM").contains("NORMAL")
                    .contains("LAYOUT_PASS_1").contains("NoClassDefFoundError").doesNotContain("FAKE-");
        }
    }

    @Test
    @DisplayName("should log a failed attempt that is not a LinkageError at WARN")
    void shouldLogFailedAttempt_atWarn() throws Exception {
        failLayoutIn(new IllegalStateException("FAKE-secret layout text"), PrintMode.NORMAL);

        try (LogCapture capture = LogCapture.forLogger(HtmlPdfRenderService.class)) {
            service.render(request(page("<p>x</p>")));

            LogEvent failure = capture.events().get(0);
            assertThat(failure.getLevel()).isEqualTo(Level.WARN);
            assertThat(failure.getMessage().getFormattedMessage()).startsWith("htmlpdf-attempt-failed source=FORM")
                    .contains("IllegalStateException").doesNotContain("FAKE-");
        }
    }

    @Test
    @DisplayName("should treat a stack overflow in an attempt as a failure of that attempt")
    void shouldRetry_afterStackOverflow() throws Exception {
        failLayoutIn(new StackOverflowError(), PrintMode.NORMAL);

        HtmlPdfRenderResult result = service.render(request(page("<p>FAKE-Body</p>")));

        assertThat(result.degradations()).containsExactly(HtmlPdfRenderDegradation.SAFE_MODE);
    }

    @Test
    @DisplayName("should not catch an OutOfMemoryError, and should still release everything it held")
    void shouldNotCatchOutOfMemoryError_butCleanUp() throws Exception {
        TestImages.write(webRoot, "pixel.wbmp", TestImages.wbmp(8, 8));
        failLayoutIn(new OutOfMemoryError("FAKE-heap"), PrintMode.NORMAL);
        HtmlPdfRenderRequest withoutBase = new HtmlPdfRenderRequest(page("<img src=\"pixel.wbmp\">"), null, List.of(webRoot),
                HtmlPdfRenderSource.FORM);

        assertThatThrownBy(() -> service.render(withoutBase)).isInstanceOf(OutOfMemoryError.class);

        assertThat(service.availableSlots()).isEqualTo(HtmlPdfRenderService.RENDER_SLOTS);
        assertThat(directoriesIn(tempRoot, "carlos-pdf-")).isEmpty();
        verify(layout, times(1)).render(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("should retry on the next rung when the output check fails, and log the check by name")
    void shouldRetry_whenOutputCheckFails() throws Exception {
        doAnswer(invocation -> new RenderedPdfValidator.Result(List.of("The PDF has no pages."), false))
                .doCallRealMethod().when(validator).validate(any());

        try (LogCapture capture = LogCapture.forLogger(HtmlPdfRenderService.class)) {
            HtmlPdfRenderResult result = service.render(request(page("<p>FAKE-Body</p>")));

            assertThat(result.degradations()).containsExactly(HtmlPdfRenderDegradation.SAFE_MODE);
            assertThat(capture.messages()).anyMatch(message -> message.startsWith("htmlpdf-output-check-failed mode=NORMAL")
                    && message.contains("The PDF has no pages."));
            assertThat(capture.messages()).anyMatch(message -> message.startsWith("htmlpdf-attempt-failed")
                    && message.contains("VALIDATE"));
        }
    }

    @Test
    @DisplayName("should retry on the next rung when the image step fails, and name the phase")
    void shouldRetry_whenImageStepFails() throws Exception {
        doAnswer(invocation -> {
            throw new IOException("FAKE-disk full at " + tempRoot);
        }).doCallRealMethod().when(images).prepare(any(), any(), any(), any(), Mockito.anyLong(), any());

        try (LogCapture capture = LogCapture.forLogger(HtmlPdfRenderService.class)) {
            HtmlPdfRenderResult result = service.render(request(page("<p>FAKE-Body</p>")));

            assertThat(result.degradations()).containsExactly(HtmlPdfRenderDegradation.SAFE_MODE);
            assertThat(capture.messages()).anyMatch(message -> message.startsWith("htmlpdf-attempt-failed")
                    && message.contains("IMAGES") && message.contains("IOException") && !message.contains("FAKE-")
                    && !message.contains(tempRoot.toString()));
        }
    }

    @Test
    @DisplayName("should start each attempt from the original HTML, so a failed attempt's degradations do not leak")
    void shouldStartEachAttempt_fromTheOriginalHtml() throws Exception {
        String html = page("<p>FAKE-Body</p><svg width=\"10\" height=\"10\"><rect width=\"5\" height=\"5\"/>"
                + "<image xlink:href=\"http://127.0.0.1:1/x.png\"/></svg>");
        failLayoutIn(new IllegalStateException("FAKE"), PrintMode.NORMAL);

        HtmlPdfRenderResult result = service.render(request(html));

        // Normal mode cleaned the SVG before its layout failed; safe mode boxes the SVG and cleans nothing.
        assertThat(result.degradations()).containsExactly(HtmlPdfRenderDegradation.SAFE_MODE);
    }

    @Test
    @DisplayName("should keep what is true of the whole render on every rung")
    void shouldKeepRenderLevelDegradations_onEveryRung() throws Exception {
        failLayoutIn(new IllegalStateException("FAKE"), PrintMode.NORMAL, PrintMode.SAFE);
        HtmlPdfRenderRequest withoutBase = new HtmlPdfRenderRequest(page("<p>FAKE-Body</p>"), null, List.of(webRoot),
                HtmlPdfRenderSource.FORM);

        HtmlPdfRenderResult result = service.render(withoutBase);

        assertThat(result.degradations()).containsExactlyInAnyOrder(HtmlPdfRenderDegradation.WEBAPP_ROOT_UNAVAILABLE,
                HtmlPdfRenderDegradation.TEXT_ONLY);
    }

    @Test
    @DisplayName("should give the layout the image directory of the attempt, and delete it afterwards, also when layout fails")
    void shouldAllowImageDirectory_andDeleteIt() throws Exception {
        TestImages.write(webRoot, "pixel.wbmp", TestImages.wbmp(8, 8));
        failLayoutIn(new IllegalStateException("FAKE"), PrintMode.NORMAL);

        HtmlPdfRenderResult result = service.render(request(page("<img src=\"pixel.wbmp\">")));

        assertThat(result.degradations()).contains(HtmlPdfRenderDegradation.SAFE_MODE, HtmlPdfRenderDegradation.IMAGE_CONVERTED);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Path>> allowed = ArgumentCaptor.forClass(List.class);
        verify(layout, times(2)).render(any(), any(), any(), allowed.capture(), any(), any());
        for (List<Path> directories : allowed.getAllValues()) {
            assertThat(directories).hasSize(2).first().isEqualTo(webRoot);
            assertThat(directories.get(1).getFileName().toString()).startsWith("carlos-pdf-images-");
            assertThat(directories.get(1).getParent()).isEqualTo(tempRoot);
        }
        assertThat(directoriesIn(tempRoot, "carlos-pdf-")).isEmpty();
    }

    // ---- phases -----------------------------------------------------------------------------

    private void assertLastFailure(HtmlPdfRenderException.Phase expected) {
        assertThatThrownBy(() -> service.render(request(page("<p>FAKE-Body</p>"))))
                .isInstanceOfSatisfying(PDFGenerationException.class, thrown -> {
                    HtmlPdfRenderException last = (HtmlPdfRenderException) thrown.getCause();
                    assertThat(last.phase()).isEqualTo(expected);
                    assertThat(last.mode()).isEqualTo(PrintMode.TEXT_ONLY);
                    assertThat(last.getMessage()).doesNotContain("FAKE-");
                });
    }

    @Test
    @DisplayName("should name the document rules as the phase when parsing and cleaning fail")
    void shouldNamePrepare_whenDocumentRulesFail() {
        doAnswer(invocation -> {
            throw new IllegalStateException("FAKE-markup");
        }).when(composer).prepare(any(), any(), any());

        assertLastFailure(HtmlPdfRenderException.Phase.PREPARE);
    }

    @Test
    @DisplayName("should name the document rules as the phase when the style sheet and serialisation fail")
    void shouldNamePrepare_whenFinishFails() {
        doAnswer(invocation -> {
            throw new IllegalStateException("FAKE-markup");
        }).when(composer).finish(any(), any(), any(), any());

        assertLastFailure(HtmlPdfRenderException.Phase.PREPARE);
    }

    @Test
    @DisplayName("should name the image step as the phase when it fails")
    void shouldNameImages_whenImageStepFails() throws Exception {
        doAnswer(invocation -> {
            throw new IOException("FAKE-disk");
        }).when(images).prepare(any(), any(), any(), any(), Mockito.anyLong(), any());

        assertLastFailure(HtmlPdfRenderException.Phase.IMAGES);
    }

    @Test
    @DisplayName("should name the first layout pass as the phase when an unexpected exception comes out of layout")
    void shouldNameLayout_whenLayoutFails() {
        failLayoutIn(new IllegalStateException("FAKE-layout"), PrintMode.values());

        assertLastFailure(HtmlPdfRenderException.Phase.LAYOUT_PASS_1);
    }

    @Test
    @DisplayName("should name the output check as the phase when it fails")
    void shouldNameValidate_whenOutputCheckFails() {
        doAnswer(invocation -> new RenderedPdfValidator.Result(List.of("The PDF has no pages."), false))
                .when(validator).validate(any());

        assertLastFailure(HtmlPdfRenderException.Phase.VALIDATE);
    }

    @Test
    @DisplayName("should keep the phase an exception from the layout step already carries")
    void shouldKeepPhase_ofLayoutException() {
        failLayoutIn(new HtmlPdfRenderException(PrintMode.TEXT_ONLY, HtmlPdfRenderException.Phase.PLACE,
                new IllegalStateException("FAKE-place")), PrintMode.values());

        assertLastFailure(HtmlPdfRenderException.Phase.PLACE);
    }

    // ---- the base directory -----------------------------------------------------------------

    @Test
    @DisplayName("should render against a private empty base directory when there is no webapp root, and delete it")
    void shouldReportMissingWebappRoot_withNullBase() throws Exception {
        HtmlPdfRenderRequest withoutBase = new HtmlPdfRenderRequest(page("<p>FAKE-Body</p>"), null, List.of(webRoot),
                HtmlPdfRenderSource.FORM);

        HtmlPdfRenderResult result = service.render(withoutBase);

        assertThat(result.degradations()).containsExactly(HtmlPdfRenderDegradation.WEBAPP_ROOT_UNAVAILABLE);
        assertThat(TestPdfs.pageSizes(result.pdf())).hasSize(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Path>> allowed = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Path> base = ArgumentCaptor.forClass(Path.class);
        verify(layout).render(eq(PrintMode.NORMAL), any(), base.capture(), allowed.capture(), any(), any());
        assertThat(base.getValue().getFileName().toString()).startsWith("carlos-pdf-base-");
        assertThat(base.getValue().getParent()).isEqualTo(tempRoot);
        assertThat(allowed.getValue()).containsExactly(webRoot, base.getValue());
        assertThat(directoriesIn(tempRoot, "carlos-pdf-")).isEmpty();
    }

    @Test
    @DisplayName("should not make a private base directory when the request has a base")
    void shouldNotMakePrivateBase_whenRequestHasOne() throws Exception {
        service.render(request(page("<p>FAKE-Body</p>")));

        ArgumentCaptor<Path> base = ArgumentCaptor.forClass(Path.class);
        verify(layout).render(any(), any(), base.capture(), any(), any(), any());
        assertThat(base.getValue()).isEqualTo(webRoot);
        assertThat(directoriesIn(tempRoot, "carlos-pdf-")).isEmpty();
    }

    @Test
    @DisplayName("should fail at once when the private base directory cannot be made")
    void shouldFail_whenPrivateBaseCannotBeMade() throws IOException {
        Path notADirectory = Files.writeString(webRoot.resolve("not-a-directory"), "x");
        HtmlPdfRenderService broken = new HtmlPdfRenderService(new PrintDocumentComposer(), images, layout, validator,
                HtmlPdfTestServices.sharedFontFiles(), Duration.ofMillis(200), 256L << 20, 100_000, notADirectory);
        HtmlPdfRenderRequest withoutBase = new HtmlPdfRenderRequest(page("<p>x</p>"), null, List.of(webRoot),
                HtmlPdfRenderSource.FORM);

        assertThatExceptionOfType(PDFGenerationException.class).isThrownBy(() -> broken.render(withoutBase))
                .withMessageStartingWith("HTML could not be converted to PDF").satisfies(thrown -> assertThat(thrown.isRetryable()).isFalse());

        assertThat(broken.availableSlots()).isEqualTo(HtmlPdfRenderService.RENDER_SLOTS);
        verify(layout, never()).render(any(), any(), any(), any(), any(), any());
    }

    // ---- the size limit ---------------------------------------------------------------------

    @Test
    @DisplayName("should go straight to text-only mode when the HTML is over the limit")
    void shouldGoStraightToTextOnly_whenHtmlOverLimit() throws Exception {
        HtmlPdfRenderService limited = serviceWith(HtmlPdfTestServices.sharedFontFiles(), 1000);
        String html = page("<p>FAKE-Body " + "word ".repeat(400) + "</p>");
        assertThat(html.length()).isGreaterThan(1000);

        HtmlPdfRenderResult result = limited.render(request(html));

        assertThat(result.degradations()).containsExactly(HtmlPdfRenderDegradation.TEXT_ONLY);
        verify(layout, times(1)).render(eq(PrintMode.TEXT_ONLY), any(), any(), any(), any(), any());
        verify(layout, never()).render(eq(PrintMode.NORMAL), any(), any(), any(), any(), any());
        verify(layout, never()).render(eq(PrintMode.SAFE), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("should not count the payload of a data: URI, however it is written, towards the limit")
    void shouldIgnoreDataUriPayloads_forSizeLimit() throws Exception {
        HtmlPdfRenderService limited = serviceWith(HtmlPdfTestServices.sharedFontFiles(), 1000);
        String payload = java.util.Base64.getEncoder().encodeToString(TestImages.png(400, 400));
        assertThat(payload.length()).isGreaterThan(3000);
        String html = page("<p>FAKE-Body</p><img src=\"data:image/png;base64," + payload + "\">"
                + "<img src='DATA:image/png;base64," + payload + "'>"
                + "<div style=\"background: url(data:image/png;base64," + payload + ")\">x</div>");

        HtmlPdfRenderResult result = limited.render(request(html));

        assertThat(result.degradations()).isEmpty();
        verify(layout, times(1)).render(eq(PrintMode.NORMAL), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("should count everything that is not a data: payload, so a long document of text is over the limit")
    void shouldCountTextAndMarkup_towardsLimit() throws Exception {
        HtmlPdfRenderService limited = serviceWith(HtmlPdfTestServices.sharedFontFiles(), 1000);
        String payload = java.util.Base64.getEncoder().encodeToString(TestImages.png(400, 400));
        String html = page("<img src=\"data:image/png;base64," + payload + "\"><p>" + "word ".repeat(300) + "</p>");

        HtmlPdfRenderResult result = limited.render(request(html));

        assertThat(result.degradations()).containsExactly(HtmlPdfRenderDegradation.TEXT_ONLY);
    }

    @Test
    @DisplayName("should keep a document of exactly the limit on the normal rung, and move one character more to text-only")
    void shouldDrawTheLimit_atExactlyTheNumberOfCharacters() throws Exception {
        HtmlPdfRenderService limited = serviceWith(HtmlPdfTestServices.sharedFontFiles(), 1000);
        String start = "<html><body><p>FAKE-Body</p><!--";
        String end = "--></body></html>";
        String atLimit = start + "x".repeat(1000 - start.length() - end.length()) + end;
        assertThat(atLimit).hasSize(1000);

        assertThat(limited.render(request(atLimit)).degradations()).isEmpty();
        assertThat(limited.render(request(atLimit + " ")).degradations()).containsExactly(HtmlPdfRenderDegradation.TEXT_ONLY);
    }

    @Test
    @DisplayName("should keep a document on the normal rung when it is exactly the limit without its data: payloads")
    void shouldDrawTheLimit_afterLeavingOutPayloads() throws Exception {
        HtmlPdfRenderService limited = serviceWith(HtmlPdfTestServices.sharedFontFiles(), 1000);
        String uri = "data:image/png;base64," + "A".repeat(500);
        String start = "<html><body><p>FAKE-Body</p><!--";
        String middle = "--><img src=\"";
        String end = "\"></body></html>";
        int padding = 1000 - start.length() - middle.length() - end.length();
        String atLimit = start + "x".repeat(padding) + middle + uri + end;
        assertThat(atLimit.length() - uri.length()).isEqualTo(1000);

        assertThat(limited.render(request(atLimit)).degradations()).doesNotContain(HtmlPdfRenderDegradation.TEXT_ONLY);
        assertThat(limited.render(request(atLimit.replace("--><img", "-->.<img"))).degradations())
                .containsExactly(HtmlPdfRenderDegradation.TEXT_ONLY);
    }

    @Test
    @DisplayName("should end a data: URI at a quote, a parenthesis or white space, and count the markup after it")
    void shouldEndDataUri_atTheCharacterThatEndsIt() throws Exception {
        HtmlPdfRenderService limited = serviceWith(HtmlPdfTestServices.sharedFontFiles(), 1000);
        String uri = "data:image/png;base64," + "A".repeat(300);
        String markup = "<b>x</b>".repeat(200);

        for (String html : List.of("<img src=\"" + uri + "\">" + markup, "<img src='" + uri + "'>" + markup,
                "<div style=\"background:url(" + uri + ")\">" + markup + "</div>", "<img srcset=\"" + uri + " 2x\">" + markup)) {
            assertThat(limited.render(request("<html><body>" + html + "</body></html>")).degradations())
                    .as(html.substring(0, 24)).containsExactly(HtmlPdfRenderDegradation.TEXT_ONLY);
        }
    }

    @Test
    @DisplayName("should end a data: URI of an unquoted attribute at the end of the tag, and count the markup after it")
    void shouldEndDataUri_atTheEndOfAnUnquotedAttribute() throws Exception {
        HtmlPdfRenderService limited = serviceWith(HtmlPdfTestServices.sharedFontFiles(), 1000);
        String uri = "data:image/png;base64," + "A".repeat(300);
        String markup = "<b>x</b>".repeat(200);

        assertThat(limited.render(request("<html><body><img src=" + uri + ">" + markup + "</body></html>")).degradations())
                .containsExactly(HtmlPdfRenderDegradation.TEXT_ONLY);
    }

    @Test
    @DisplayName("should count text that only looks like a data: URI: the end of a longer word, or one with no comma")
    void shouldCountText_thatOnlyLooksLikeADataUri() throws Exception {
        HtmlPdfRenderService limited = serviceWith(HtmlPdfTestServices.sharedFontFiles(), 1000);
        String run = "A".repeat(1500);

        for (String text : List.of("metadata:text/plain,", "my-data:text/plain,", "data1:text/plain,", "data:text/plain ", "data:" + "x".repeat(300) + ",")) {
            String html = "<html><body><p>" + text + run + "</p></body></html>";
            assertThat(limited.render(request(html)).degradations()).as(text).containsExactly(HtmlPdfRenderDegradation.TEXT_ONLY);
        }
    }

    @Test
    @DisplayName("should report the duration in milliseconds")
    void shouldLogDuration_inMilliseconds() throws Exception {
        try (LogCapture capture = LogCapture.forLogger(HtmlPdfRenderService.class)) {
            service.render(request(page("<p>FAKE-Body</p>")));

            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("durationMs=(\\d+) ").matcher(capture.messages().get(0));
            assertThat(matcher.find()).isTrue();
            assertThat(Long.parseLong(matcher.group(1))).isBetween(1L, 60_000L);
        }
    }

    // ---- input ------------------------------------------------------------------------------

    @Test
    @DisplayName("should print normally when the text holds characters that are not valid XML")
    void shouldPrintNormally_withXmlInvalidCharacters() throws Exception {
        String html = "<html><head><title>FAKE" + (char) 2 + "-Title</title><style>p { color: #123456 } /* " + (char) 1
                + " */</style></head><body><p title=\"FAKE" + (char) 3 + "-tip\">FAKE" + (char) 1 + " value</p>"
                + "<input type=\"text\" value=\"FAKE" + (char) 4 + "-input\"></body></html>";

        HtmlPdfRenderResult result = service.render(request(html));

        assertThat(result.degradations()).isEmpty();
        assertThat(text(result)).contains("FAKE value").contains("FAKE-input");
    }

    @Test
    @DisplayName("should fail at once, without a slot or any work, when the HTML is only white space")
    void shouldFailAtOnce_forWhitespaceOnlyHtml() {
        for (String html : List.of("", "   ", " \n\t \r\n ")) {
            assertThatExceptionOfType(PDFGenerationException.class).isThrownBy(() -> service.render(request(html)))
                    .satisfies(thrown -> {
                        assertThat(thrown.isRetryable()).isFalse();
                        assertThat(thrown.getCause()).isNull();
                    });
        }
        verify(layout, never()).render(any(), any(), any(), any(), any(), any());
        assertThat(service.availableSlots()).isEqualTo(HtmlPdfRenderService.RENDER_SLOTS);
    }

    @Test
    @DisplayName("should fail at once, without trying another rung, when the bundled fonts cannot be loaded")
    void shouldFailAtOnce_whenDejaVuCannotLoad() throws IOException {
        PDFGenerationException fontFailure = new PDFGenerationException("The bundled font SANS_REGULAR is missing");
        HtmlPdfRenderService broken = serviceWith(new BundledFontFileService() {
            @Override
            BundledFontSet fontSet() throws PDFGenerationException {
                throw fontFailure;
            }
        }, 100_000);

        assertThatThrownBy(() -> broken.render(request(page("<p>FAKE-Body</p>")))).isSameAs(fontFailure);

        verify(layout, never()).render(any(), any(), any(), any(), any(), any());
        verify(images, never()).prepare(any(), any(), any(), any(), Mockito.anyLong(), any());
    }

    @Test
    @DisplayName("should print one blank page for markup without visible content")
    void shouldPrintBlankPage_forMarkupWithoutContent() throws Exception {
        for (String html : List.of("<html><body></body></html>", "<p></p>", "<div> </div><span></span>", "<!-- comment -->",
                "<script>var x = 1;</script>")) {
            HtmlPdfRenderResult result = service.render(request(html));

            assertThat(result.degradations()).as(html).isEmpty();
            assertThat(TestPdfs.pageSizes(result.pdf())).as(html).hasSize(1);
            assertThat(text(result)).as(html).isEmpty();
        }
    }

    @Test
    @DisplayName("should reject a null request")
    void shouldRejectNull_whenRequestMissing() {
        assertThatNullPointerException().isThrownBy(() -> service.render(null));
    }

    @Test
    @DisplayName("should reject null collaborators")
    void shouldRejectNull_whenCollaboratorMissing() {
        BundledFontFileService fonts = HtmlPdfTestServices.sharedFontFiles();
        assertThatNullPointerException().isThrownBy(() -> new HtmlPdfRenderService(null, images, layout, validator, fonts));
        assertThatNullPointerException().isThrownBy(() -> new HtmlPdfRenderService(new PrintDocumentComposer(), null, layout, validator, fonts));
        assertThatNullPointerException().isThrownBy(() -> new HtmlPdfRenderService(new PrintDocumentComposer(), images, null, validator, fonts));
        assertThatNullPointerException().isThrownBy(() -> new HtmlPdfRenderService(new PrintDocumentComposer(), images, layout, null, fonts));
        assertThatNullPointerException().isThrownBy(() -> new HtmlPdfRenderService(new PrintDocumentComposer(), images, layout, validator, null));
    }

    // ---- logging ----------------------------------------------------------------------------

    @Test
    @DisplayName("should write one line at the level of the most serious degradation, with no path and no content")
    void shouldLogOneLine_atMostSeriousLevel() throws Exception {
        TestImages.write(webRoot, "pixel.wbmp", TestImages.wbmp(8, 8));
        String html = page("<p>FAKE-Patient Name</p><img src=\"pixel.wbmp\"><img src=\"missing-FAKE-file.png\">");

        try (LogCapture capture = LogCapture.forLogger(HtmlPdfRenderService.class)) {
            HtmlPdfRenderResult result = service.render(request(html));

            assertThat(result.degradations()).containsExactlyInAnyOrder(HtmlPdfRenderDegradation.IMAGE_CONVERTED,
                    HtmlPdfRenderDegradation.IMAGE_UNAVAILABLE);
            assertThat(capture.events()).hasSize(1);
            LogEvent line = capture.events().get(0);
            assertThat(line.getLevel()).isEqualTo(Level.WARN);
            assertThat(line.getMessage().getFormattedMessage()).startsWith("htmlpdf-render source=FORM mode=NORMAL durationMs=")
                    .endsWith("degradations=[IMAGE_CONVERTED, IMAGE_UNAVAILABLE]").doesNotContain("FAKE-")
                    .doesNotContain(webRoot.toString()).doesNotContain(tempRoot.toString());
        }
    }

    @Test
    @DisplayName("should log at DEBUG when nothing degraded")
    void shouldLogAtDebug_whenNoDegradation() throws Exception {
        try (LogCapture capture = LogCapture.forLogger(HtmlPdfRenderService.class)) {
            service.render(request(page("<p>FAKE-Body</p>")));

            assertThat(capture.events()).hasSize(1);
            assertThat(capture.events().get(0).getLevel()).isEqualTo(Level.DEBUG);
            assertThat(capture.messages().get(0)).startsWith("htmlpdf-render source=FORM mode=NORMAL durationMs=")
                    .endsWith("degradations=[]");
        }
    }

    @Test
    @DisplayName("should log at INFO when only content-preserving degradations happened")
    void shouldLogAtInfo_forContentKeepingDegradations() throws Exception {
        TestImages.write(webRoot, "pixel.wbmp", TestImages.wbmp(8, 8));

        try (LogCapture capture = LogCapture.forLogger(HtmlPdfRenderService.class)) {
            service.render(request(page("<img src=\"pixel.wbmp\">")));

            assertThat(capture.events()).hasSize(1);
            assertThat(capture.events().get(0).getLevel()).isEqualTo(Level.INFO);
        }
    }

    @Test
    @DisplayName("should log at ERROR when a rung had to be skipped, after the WARN of the failed attempt")
    void shouldLogAtError_whenSafeModeWasUsed() throws Exception {
        failLayoutIn(new IllegalStateException("FAKE"), PrintMode.NORMAL);

        try (LogCapture capture = LogCapture.forLogger(HtmlPdfRenderService.class)) {
            service.render(request(page("<p>FAKE-Body</p>")));

            assertThat(capture.events()).hasSize(2);
            assertThat(capture.events().get(0).getLevel()).isEqualTo(Level.WARN);
            assertThat(capture.events().get(1).getLevel()).isEqualTo(Level.ERROR);
            assertThat(capture.messages().get(1)).startsWith("htmlpdf-render source=FORM mode=SAFE durationMs=")
                    .endsWith("degradations=[SAFE_MODE]");
        }
    }

    @Test
    @DisplayName("should name the source of the request in the line")
    void shouldNameTheSource_inTheLogLine() throws Exception {
        try (LogCapture capture = LogCapture.forLogger(HtmlPdfRenderService.class)) {
            for (HtmlPdfRenderSource source : HtmlPdfRenderSource.values()) {
                service.render(new HtmlPdfRenderRequest(page("<p>x</p>"), webRoot, List.of(webRoot), source));
            }

            List<String> messages = new ArrayList<>(capture.messages());
            for (int i = 0; i < HtmlPdfRenderSource.values().length; i++) {
                assertThat(messages.get(i)).startsWith("htmlpdf-render source=" + HtmlPdfRenderSource.values()[i] + " ");
            }
        }
    }

    @Test
    @DisplayName("should release the slot after a render, whether it succeeds or fails")
    void shouldReleaseSlot_afterEveryRender() throws Exception {
        service.render(request(page("<p>FAKE-Body</p>")));
        assertThat(service.availableSlots()).isEqualTo(HtmlPdfRenderService.RENDER_SLOTS);

        failLayoutIn(new IllegalStateException("FAKE"), PrintMode.values());
        assertThatExceptionOfType(PDFGenerationException.class).isThrownBy(() -> service.render(request(page("<p>x</p>"))));
        assertThat(service.availableSlots()).isEqualTo(HtmlPdfRenderService.RENDER_SLOTS);
    }
}
