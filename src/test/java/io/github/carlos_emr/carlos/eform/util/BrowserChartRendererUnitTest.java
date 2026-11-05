/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.MeasurementChart;
import jakarta.servlet.ServletContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
    void shouldReadBundledScriptsOnce_acrossRenders() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(this::render).isInstanceOf(IOException.class).hasMessage("Chart rendering failed");
        }
        assertThat(libraryReads.get()).as("resource reads for the library and draw script").isEqualTo(2);
    }

    @Test
    void shouldQueueForRenderSlot_whileSlotsAreBusy() throws Exception {
        int permits = EFormBrowserPdfService.RENDER_SLOTS.drainPermits();
        List<Thread> waiters = new ArrayList<>();
        List<String> outcomes = java.util.Collections.synchronizedList(new ArrayList<>());
        CountDownLatch started = new CountDownLatch(6);
        try {
            for (int i = 0; i < 6; i++) {
                Thread thread = new Thread(() -> {
                    started.countDown();
                    try {
                        render();
                        outcomes.add("rendered");
                    } catch (IOException failure) {
                        outcomes.add(failure.getMessage());
                    }
                });
                waiters.add(thread);
                thread.start();
            }
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(500);
            assertThat(outcomes).as("no chart request fails fast while slots are merely busy").isEmpty();
        } finally {
            EFormBrowserPdfService.RENDER_SLOTS.release(permits);
        }
        for (Thread thread : waiters) thread.join(30_000);
        assertThat(outcomes).hasSize(6).containsOnly("Chart rendering failed");
    }
}
