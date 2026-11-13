/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.MeasurementChart;
import jakarta.servlet.ServletContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Renderer plumbing that does not need a browser: the bundled script is read once, and a chart
 * request queues for a render slot instead of failing fast while slots are merely busy.
 */
@Tag("unit")
@Tag("measurement")
class BrowserChartRendererUnitTest {
    private ServletContext context;
    private final AtomicInteger libraryReads = new AtomicInteger();
    private MockedStatic<CarlosProperties> settings;

    @BeforeEach
    void setUp() {
        BrowserChartRenderer.forgetBundledScripts();
        context = mock(ServletContext.class);
        when(context.getResourceAsStream(anyString())).thenAnswer(invocation -> {
            libraryReads.incrementAndGet();
            return new ByteArrayInputStream("/* synthetic script */".getBytes(StandardCharsets.UTF_8));
        });
        CarlosProperties properties = mock(CarlosProperties.class);
        // A closed loopback port: the session request fails at once, after the slot and script steps.
        when(properties.getProperty("eform_pdf_browser_service_url", "http://127.0.0.1:9515")).thenReturn("http://127.0.0.1:1");
        settings = mockStatic(CarlosProperties.class);
        settings.when(CarlosProperties::getInstance).thenReturn(properties);
    }

    @AfterEach
    void tearDown() {
        settings.close();
        BrowserChartRenderer.forgetBundledScripts();
    }

    private static MeasurementChart chart() {
        MeasurementChart chart = mock(MeasurementChart.class);
        when(chart.toConfig()).thenReturn(java.util.Map.of("datasets", java.util.List.of(), "medications", java.util.List.of()));
        return chart;
    }

    private void render() throws IOException {
        BrowserChartRenderer.write(context, new ByteArrayOutputStream(), chart(), 100, 100, "png");
    }

    @Test
    void shouldPreserveInterruption_withoutReadingScriptsOrTakingASlot() {
        int permits = EFormBrowserPdfService.RENDER_SLOTS.availablePermits();
        int chartPermits = EFormBrowserPdfService.CHART_RENDER_SLOTS.availablePermits();
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(this::render).isInstanceOf(ChartRenderFailure.class)
                    .hasMessage("Chart rendering interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(libraryReads.get()).isZero();
            assertThat(EFormBrowserPdfService.RENDER_SLOTS.availablePermits()).isEqualTo(permits);
            assertThat(EFormBrowserPdfService.CHART_RENDER_SLOTS.availablePermits()).isEqualTo(chartPermits);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldReadBundledScriptsOnce_acrossRenders() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(this::render).isInstanceOf(IOException.class).hasMessage("Chart rendering failed");
        }
        assertThat(libraryReads.get()).as("resource reads for the library and draw script").isEqualTo(2);
    }

    /** ChartUtils.writeChartAsJPEG was called with quality 1.0; a PNG capture takes no quality. */
    @Test
    @SuppressWarnings("unchecked")
    void shouldCaptureJpegAtFullQuality_likeTheFormerCharts() throws Exception {
        for (String format : List.of("jpeg", "png")) {
            RenderBrowser driver = mock(RenderBrowser.class);
            when(driver.executeScript(eq("return window.carlosDrawChart(arguments[0], arguments[1], arguments[2]).length > 0;"),
                    any(), eq(100), eq(100))).thenReturn(true);
            when(driver.executeCdpCommand(eq("Page.captureScreenshot"), any()))
                    .thenReturn(java.util.Map.of("data", java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2, 3})));
            EFormBrowserPdfService.RendererBrowser session = new EFormBrowserPdfService.RendererBrowser(
                    driver, URI.create("http://127.0.0.1:9515"), "synthetic-session");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (var _ = mockConstruction(EFormBrowserPdfService.class,
                    (service, ignored) -> when(service.createDriver(any())).thenReturn(session))) {
                BrowserChartRenderer.write(context, output, chart(), 100, 100, format);
            }
            var captured = org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
            verify(driver).executeCdpCommand(eq("Page.captureScreenshot"), captured.capture());
            assertThat((java.util.Map<String, Object>) captured.getValue()).containsEntry("format", format);
            if ("jpeg".equals(format)) {
                assertThat((java.util.Map<String, Object>) captured.getValue()).containsEntry("quality", 100);
            } else {
                assertThat((java.util.Map<String, Object>) captured.getValue()).doesNotContainKey("quality");
            }
            assertThat(output.toByteArray()).containsExactly(1, 2, 3);
        }
    }

    /** ChartMeds grows 30px per drug past twenty (#6165); JFreeChart drew any count, so long lists stay drawable. */
    @Test
    void shouldAcceptTallMedicationCharts_withinTheHeightCap() {
        int fiveHundredMedications = 500 * 30;
        assertThat(fiveHundredMedications).isLessThanOrEqualTo(BrowserChartRenderer.MAX_HEIGHT);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MeasurementChart tooTall = chart();
        int height = BrowserChartRenderer.MAX_HEIGHT + 1;
        assertThatThrownBy(() -> BrowserChartRenderer.write(context, output, tooTall, 100, height, "png"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectEmptyChart_andReleaseSession() {
        RenderBrowser driver = mock(RenderBrowser.class);
        when(driver.executeScript(eq("return window.carlosDrawChart(arguments[0], arguments[1], arguments[2]).length > 0;"),
                any(), eq(100), eq(100))).thenReturn(false);
        EFormBrowserPdfService.RendererBrowser session = new EFormBrowserPdfService.RendererBrowser(
                driver, URI.create("http://127.0.0.1:9515"), "synthetic-session");
        int permits = EFormBrowserPdfService.RENDER_SLOTS.availablePermits();
        int chartPermits = EFormBrowserPdfService.CHART_RENDER_SLOTS.availablePermits();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (var _ = mockConstruction(EFormBrowserPdfService.class,
                (service, ignored) -> when(service.createDriver(any())).thenReturn(session))) {
            assertThatThrownBy(() -> BrowserChartRenderer.write(context, output, chart(), 100, 100, "png"))
                    .isInstanceOf(IOException.class).hasMessage("Chart image is unavailable");
        }
        assertThat(output.size()).as("a blank screenshot must never be emitted as a successful chart").isZero();
        verify(driver, never()).executeCdpCommand(anyString(), any());
        verify(driver).quit();
        assertThat(EFormBrowserPdfService.RENDER_SLOTS.availablePermits()).isEqualTo(permits);
        assertThat(EFormBrowserPdfService.CHART_RENDER_SLOTS.availablePermits()).isEqualTo(chartPermits);
    }

    /** #5853: eForm PDFs holding both shared browsers do not hold up a graph; it draws in its own. */
    @Test
    void shouldDrawAChart_whilePdfRendersHoldBothSharedSlots() throws Exception {
        RenderBrowser driver = mock(RenderBrowser.class);
        when(driver.executeScript(eq("return window.carlosDrawChart(arguments[0], arguments[1], arguments[2]).length > 0;"),
                any(), eq(100), eq(100))).thenReturn(true);
        when(driver.executeCdpCommand(eq("Page.captureScreenshot"), any()))
                .thenReturn(java.util.Map.of("data", java.util.Base64.getEncoder().encodeToString(new byte[]{4, 5, 6})));
        EFormBrowserPdfService.RendererBrowser session = new EFormBrowserPdfService.RendererBrowser(
                driver, URI.create("http://127.0.0.1:9515"), "synthetic-session");
        int chartPermits = EFormBrowserPdfService.CHART_RENDER_SLOTS.availablePermits();
        int held = EFormBrowserPdfService.RENDER_SLOTS.drainPermits();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (var _ = mockConstruction(EFormBrowserPdfService.class,
                (service, ignored) -> when(service.createDriver(any())).thenAnswer(invocation -> {
                    assertThat(EFormBrowserPdfService.CHART_RENDER_SLOTS.availablePermits())
                            .as("the graph draws in the chart-only browser").isEqualTo(chartPermits - 1);
                    return session;
                }))) {
            BrowserChartRenderer.write(context, output, chart(), 100, 100, "png");
        } finally {
            EFormBrowserPdfService.RENDER_SLOTS.release(held);
        }
        assertThat(output.toByteArray()).containsExactly(4, 5, 6);
        verify(driver).quit();
        assertThat(EFormBrowserPdfService.CHART_RENDER_SLOTS.availablePermits()).isEqualTo(chartPermits);
    }

    @Test
    void shouldQueueForRenderSlot_whileSlotsAreBusy() throws Exception {
        // Every browser busy: the shared ones and the chart-only one (#5853).
        int permits = EFormBrowserPdfService.RENDER_SLOTS.drainPermits();
        int chartPermits = EFormBrowserPdfService.CHART_RENDER_SLOTS.drainPermits();
        List<Thread> waiters = new ArrayList<>();
        List<String> outcomes = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch started = new CountDownLatch(6);
        CountDownLatch completed = new CountDownLatch(1);
        try {
            for (int i = 0; i < 6; i++) {
                Thread thread = new Thread(() -> {
                    started.countDown();
                    try {
                        render();
                        outcomes.add("rendered");
                    } catch (IOException failure) {
                        outcomes.add(failure.getMessage());
                    } finally {
                        completed.countDown();
                    }
                });
                waiters.add(thread);
                thread.start();
            }
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(completed.await(500, TimeUnit.MILLISECONDS))
                    .as("no render completes before a slot is released").isFalse();
            assertThat(outcomes).as("no chart request fails fast while slots are merely busy").isEmpty();
        } finally {
            EFormBrowserPdfService.RENDER_SLOTS.release(permits);
            EFormBrowserPdfService.CHART_RENDER_SLOTS.release(chartPermits);
        }
        for (Thread thread : waiters) thread.join(30_000);
        assertThat(outcomes).hasSize(6).containsOnly("Chart rendering failed");
    }
}
