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
import io.github.carlos_emr.carlos.documentManager.htmlpdf.support.PrintMessages;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Unit tests for the render slots of {@link HtmlPdfRenderService}: two at a time, a bounded wait, fair
 * service, and what a render without a slot prints. Renders are held inside the layout step with a spy, so a
 * slot is held exactly as a real render holds it. The threads are coordinated with latches, semaphores and
 * a barrier, and by waiting for a condition, never for a time.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("HtmlPdfRenderService slot Unit Tests")
class HtmlPdfRenderServiceSlotUnitTest {

    private static final Duration SHORT_WAIT = Duration.ofMillis(200);
    private static final long TIMEOUT_SECONDS = 60;

    @TempDir
    Path tempRoot;

    @TempDir
    Path webRoot;

    private PrintLayoutService layout;
    private HtmlPdfRenderService service;
    private ExecutorService executor;
    private Gate gate;

    @BeforeEach
    void setUp() {
        layout = Mockito.spy(new PrintLayoutService());
        service = serviceWaiting(SHORT_WAIT);
        executor = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        if (gate != null) {
            gate.releaseAll();
        }
        executor.shutdownNow();
    }

    private HtmlPdfRenderService serviceWaiting(Duration wait) {
        return new HtmlPdfRenderService(new PrintDocumentComposer(), new PrintImageService(tempRoot), layout,
                new RenderedPdfValidator(), HtmlPdfTestServices.sharedFontFiles(), wait, 256L << 20, 100_000, tempRoot);
    }

    private HtmlPdfRenderRequest request(String body) {
        return new HtmlPdfRenderRequest("<html><body>" + body + "</body></html>", webRoot, List.of(webRoot),
                HtmlPdfRenderSource.FORM);
    }

    private Future<HtmlPdfRenderResult> submit(String threadName, String body) {
        Callable<HtmlPdfRenderResult> render = () -> {
            Thread.currentThread().setName(threadName);
            return service.render(request(body));
        };
        return executor.submit(render);
    }

    /** Waits until a condition holds: yields instead of sleeping, so nothing depends on how long a thread takes. */
    private static void awaitCondition(String what, java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting for " + what);
            }
            Thread.yield();
        }
    }

    /**
     * Holds the first {@code holders} layout calls until they are released one at a time, so the slots they
     * took stay taken; later calls run at once and are recorded by thread name.
     */
    private final class Gate {
        private final CountDownLatch holding;
        private final Semaphore permits = new Semaphore(0);
        private final AtomicInteger entered = new AtomicInteger();
        final List<String> laterCalls = new CopyOnWriteArrayList<>();

        Gate(int holders) throws Exception {
            holding = new CountDownLatch(holders);
            doAnswer(invocation -> {
                if (entered.incrementAndGet() <= holders) {
                    holding.countDown();
                    if (!permits.tryAcquire(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("a held render was never released");
                    }
                } else {
                    laterCalls.add(Thread.currentThread().getName());
                }
                return invocation.callRealMethod();
            }).when(layout).render(any(), any(), any(), any(), any(), any());
        }

        void awaitHolding() throws InterruptedException {
            assertThat(holding.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).as("holders in the layout step").isTrue();
        }

        void releaseOne() {
            permits.release();
        }

        void releaseAll() {
            permits.release(1000);
        }
    }

    /** Two renders that sit in the layout step, holding both slots. */
    private List<Future<HtmlPdfRenderResult>> holdBothSlots() throws Exception {
        gate = new Gate(2);
        List<Future<HtmlPdfRenderResult>> holders = List.of(submit("holder-1", "<p>holder one</p>"),
                submit("holder-2", "<p>holder two</p>"));
        gate.awaitHolding();
        assertThat(service.availableSlots()).isZero();
        return holders;
    }

    // ---- the share of image memory ----------------------------------------------------------

    @Test
    @DisplayName("should give each of the two slots a quarter of the heap divided between them")
    void shouldSplitQuarterOfHeap_betweenTwoSlots() {
        HtmlPdfRenderService production = new HtmlPdfRenderService(new PrintDocumentComposer(), new PrintImageService(),
                new PrintLayoutService(), new RenderedPdfValidator(), HtmlPdfTestServices.sharedFontFiles());

        assertThat(production.slotImageMemoryBytes()).isEqualTo(Runtime.getRuntime().maxMemory() / 4 / 2);
        assertThat(production.availableSlots()).isEqualTo(2);
        assertThat(production.slotsAreFair()).isTrue();
    }

    // ---- no slot free -----------------------------------------------------------------------

    @Test
    @DisplayName("should print raster images as boxes, with a note, when no slot is free within the wait")
    void shouldBoxImagesWithNote_whenNoSlotFree() throws Exception {
        List<Future<HtmlPdfRenderResult>> holders = holdBothSlots();
        TestImages.write(webRoot, "a.png", TestImages.png(40, 40));

        long started = System.nanoTime();
        HtmlPdfRenderResult result = service.render(request("<p>FAKE-Body</p><img src=\"a.png\">"));
        long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        // It waited for the slot, but only for the limit: the holders are still holding.
        assertThat(waitedMillis).isGreaterThanOrEqualTo(SHORT_WAIT.toMillis() - 20);
        assertThat(result.degradations()).containsExactly(HtmlPdfRenderDegradation.IMAGES_OMITTED_FOR_MEMORY);
        String text = String.join(" ", TestPdfs.pageTexts(result.pdf())).replaceAll("\\s+", " ");
        assertThat(text).startsWith(PrintMessages.noteFor(HtmlPdfRenderDegradation.IMAGES_OMITTED_FOR_MEMORY).orElseThrow())
                .contains("FAKE-Body").contains(PrintMessages.imageUnavailable());
        // The render without a slot did not give one back: both are still held by the holders.
        assertThat(service.availableSlots()).isZero();

        gate.releaseAll();
        for (Future<HtmlPdfRenderResult> holder : holders) {
            assertThat(holder.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).degradations()).isEmpty();
        }
        assertThat(service.availableSlots()).isEqualTo(HtmlPdfRenderService.RENDER_SLOTS);
    }

    @Test
    @DisplayName("should render a document without images normally when no slot is free")
    void shouldRenderNormally_whenNoSlotAndNoImages() throws Exception {
        holdBothSlots();

        HtmlPdfRenderResult result = service.render(request("<p>FAKE-Body</p>"));

        assertThat(result.degradations()).isEmpty();
        assertThat(String.join(" ", TestPdfs.pageTexts(result.pdf()))).contains("FAKE-Body");
    }

    // ---- giving the slot back ---------------------------------------------------------------

    @Test
    @DisplayName("should give the slot back when every attempt throws")
    void shouldReturnSlot_whenAttemptThrows() {
        doAnswer(invocation -> {
            throw new IllegalStateException("FAKE-secret");
        }).when(layout).render(any(), any(), any(), any(), any(), any());

        for (int i = 0; i < 3; i++) {
            assertThatExceptionOfType(PDFGenerationException.class).isThrownBy(() -> service.render(request("<p>x</p>")));
            assertThat(service.availableSlots()).isEqualTo(HtmlPdfRenderService.RENDER_SLOTS);
        }
    }

    // ---- interruption -----------------------------------------------------------------------

    @Test
    @DisplayName("should restore the interrupt flag and throw a retryable exception when the wait is interrupted")
    void shouldRestoreInterruptFlag_whenWaitInterrupted() throws Exception {
        service = serviceWaiting(Duration.ofSeconds(TIMEOUT_SECONDS));
        holdBothSlots();
        AtomicBoolean flagAfter = new AtomicBoolean();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                service.render(request("<p>waiting</p>"));
            } catch (Throwable failure) {
                thrown.set(failure);
                flagAfter.set(Thread.currentThread().isInterrupted());
            }
        }, "waiter");
        waiter.start();
        awaitCondition("the render to queue for a slot", () -> service.rendersWaitingForSlot() == 1);

        waiter.interrupt();
        waiter.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));

        assertThat(thrown.get()).isInstanceOfSatisfying(PDFGenerationException.class,
                failure -> assertThat(failure.isRetryable()).isTrue());
        assertThat(flagAfter.get()).as("interrupt flag restored").isTrue();
        assertThat(service.rendersWaitingForSlot()).isZero();
    }

    // ---- fairness and the limit -------------------------------------------------------------

    @Test
    @DisplayName("should serve the render that has waited longest first")
    void shouldServeEarlierWaiterFirst_forFairness() throws Exception {
        service = serviceWaiting(Duration.ofSeconds(TIMEOUT_SECONDS));
        assertThat(service.slotsAreFair()).isTrue();
        List<Future<HtmlPdfRenderResult>> holders = holdBothSlots();
        Future<HtmlPdfRenderResult> a = submit("render-A", "<p>A</p>");
        awaitCondition("A to queue", () -> service.rendersWaitingForSlot() == 1);
        Future<HtmlPdfRenderResult> b = submit("render-B", "<p>B</p>");
        awaitCondition("B to queue", () -> service.rendersWaitingForSlot() == 2);

        gate.releaseOne();
        a.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        b.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertThat(gate.laterCalls).containsExactly("render-A", "render-B");
        gate.releaseAll();
        for (Future<HtmlPdfRenderResult> holder : holders) {
            holder.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("should run two renders at the same time and never a third")
    void shouldRunTwoAtOnce_andNeverMore() throws Exception {
        service = serviceWaiting(Duration.ofSeconds(TIMEOUT_SECONDS));
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        // The first two layouts wait for each other, which they can only do if two renders run at once.
        CyclicBarrier firstTwo = new CyclicBarrier(2);
        doAnswer(invocation -> {
            peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            try {
                if (calls.incrementAndGet() <= 2) {
                    firstTwo.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
                return invocation.callRealMethod();
            } finally {
                inFlight.decrementAndGet();
            }
        }).when(layout).render(any(), any(), any(), any(), any(), any());

        List<Future<HtmlPdfRenderResult>> renders = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            renders.add(submit("render-" + i, "<p>document " + i + "</p>"));
        }
        for (Future<HtmlPdfRenderResult> render : renders) {
            assertThat(render.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).degradations()).isEmpty();
        }

        assertThat(peak.get()).isEqualTo(HtmlPdfRenderService.RENDER_SLOTS);
        assertThat(service.availableSlots()).isEqualTo(HtmlPdfRenderService.RENDER_SLOTS);
    }
}
