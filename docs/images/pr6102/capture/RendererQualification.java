/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.MeasurementChart;
import jakarta.servlet.ServletContext;
import java.io.*;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Disposable installed-runtime checks: real Chromium PNG/PDF work, admission, cleanup and server locales.
 * @since 2026-11-09
 */
public final class RendererQualification {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static ServletContext context;
    private static Path output;
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static MeasurementChart chart() {
        return io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.QualificationChart.create();
    }
    private static byte[] png() throws Exception {
        var bytes = new ByteArrayOutputStream(); BrowserChartRenderer.write(context, bytes, chart(), 1000, 600, "png");
        var image = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));
        require(image != null && image.getWidth() == 1000 && image.getHeight() == 600, "Invalid real chart image");
        return bytes.toByteArray();
    }
    private static byte[] pdf(byte[] image) throws Exception {
        try (var lease = EFormBrowserPdfService.ADMISSION.acquire(RenderAdmission.Kind.PDF, Duration.ofSeconds(30))) {
            require(lease != null, "PDF work unexpectedly refused");
            var session = new EFormBrowserPdfService().createDriver(EFormBrowserPdfService.buildBrowserOptions(
                    EFormBrowserPdfService.resolveChromiumPath(), !EFormBrowserPdfService.sandboxEnabled(), "http://127.0.0.1"));
            try {
                var browser = session.driver(); browser.setTimeouts(Duration.ofSeconds(30), Duration.ofSeconds(30)); browser.get("about:blank");
                browser.executeScript("document.body.innerHTML='<h1>SYNTHETIC print control</h1><img width=1000 height=600>';document.querySelector('img').src=arguments[0];",
                        "data:image/png;base64," + Base64.getEncoder().encodeToString(image));
                long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos(); boolean loaded = false;
                while (!loaded && System.nanoTime() < deadline) {
                    loaded = Boolean.TRUE.equals(browser.executeScript("return document.querySelector('img').complete&&document.querySelector('img').naturalWidth===1000;"));
                    if (!loaded) Thread.sleep(10);
                }
                require(loaded, "Print image did not load");
                var response = browser.executeCdpCommand("Page.printToPDF", Map.of("printBackground", true, "landscape", true));
                byte[] bytes = Base64.getDecoder().decode((String) response.get("data"));
                require(bytes.length > 5000 && new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF"), "Invalid real PDF");
                return bytes;
            } finally { EFormBrowserPdfService.teardownQuietly(session); }
        }
    }
    private static int sessions() throws Exception {
        // The capability URL remains private: no URI, response, or exception is printed.
        URI service = EFormBrowserPdfService.validateBrowserServiceUrl(EFormBrowserPdfService.resolveBrowserServiceUrl());
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            var response = client.send(HttpRequest.newBuilder(URI.create(service.toString().replaceAll("/$", "") + "/sessions"))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            require(response.statusCode() == 200, "Cannot inspect renderer cleanup");
            return JSON.readTree(response.body()).path("value").size();
        }
    }
    public static void main(String[] args) {
        try { qualify(args); }
        catch (Throwable failure) {
            System.err.println("FAIL native renderer qualification: " + (failure instanceof AssertionError ? failure.getMessage() : failure.getClass().getSimpleName()));
            System.exit(1);
        }
    }
    private static void qualify(String[] args) throws Exception {
        Path war = Path.of(args[0]).toAbsolutePath().normalize(); output = Path.of(args[2]); Files.createDirectories(output);
        try (var in = Files.newInputStream(Path.of(args[1]))) { CarlosProperties.getInstance().load(in); }
        context = (ServletContext) Proxy.newProxyInstance(ServletContext.class.getClassLoader(), new Class<?>[]{ServletContext.class}, (proxy, method, values) -> {
            if (!method.getName().equals("getResourceAsStream")) throw new UnsupportedOperationException(method.getName());
            Path file = war.resolve(((String) values[0]).replaceFirst("^/", "")).normalize();
            require(file.startsWith(war), "Resource escaped installed WAR"); return Files.newInputStream(file);
        });
        require(sessions() == 0 && EFormBrowserPdfService.RENDER_SLOTS.availablePermits() == 2, "Renderer must begin idle");
        var results = new LinkedHashMap<String, Object>();
        Locale original = Locale.getDefault();
        try {
            for (String locale : List.of("en-CA", "fr-CA", "es-ES", "pl-PL", "pt-BR")) {
                Locale.setDefault(Locale.forLanguageTag(locale));
                var config = chart().toConfig(); String json = JSON.writeValueAsString(config);
                var decimal = new java.text.DecimalFormat("0.00", java.text.DecimalFormatSymbols.getInstance()); decimal.setRoundingMode(java.math.RoundingMode.HALF_EVEN);
                require(json.contains(decimal.format(0.125)) && json.contains(decimal.format(0.0625)), "Server HALF_EVEN locale labels missing");
                byte[] image = png(); Files.write(output.resolve(locale + ".png"), image); Files.write(output.resolve(locale + ".pdf"), pdf(image));
                JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve(locale + ".json").toFile(), config);
            }
        } finally { Locale.setDefault(original); }
        // Native mixed work uses the same installed admission manager and actual Chromium.
        byte[] image = png(); var tasks = new ArrayList<Future<?>>(); var peak = new AtomicInteger(); var stop = new AtomicBoolean();
        Thread observer = new Thread(() -> { while (!stop.get()) { peak.accumulateAndGet(2 - EFormBrowserPdfService.RENDER_SLOTS.availablePermits(), Math::max); Thread.yield(); } });
        try (var pool = Executors.newFixedThreadPool(10)) {
            observer.start();
            for (int i = 0; i < 10; i++) { final boolean asPdf = i % 3 == 0; tasks.add(pool.submit(() -> { try { if (asPdf) pdf(image); else png(); } catch (Exception failure) { throw new CompletionException("Native mixed render failed", failure); } })); }
            for (var task : tasks) task.get(90, TimeUnit.SECONDS);
        } finally { stop.set(true); observer.join(5000); }
        require(peak.get() == 2 && EFormBrowserPdfService.RENDER_SLOTS.availablePermits() == 2 && sessions() == 0, "Mixed renders leaked or exceeded the budget");
        // Exercise the deployed queue ceilings and cross-class fairness while both slots are held.
        var first = EFormBrowserPdfService.ADMISSION.acquire(RenderAdmission.Kind.CHART, Duration.ZERO);
        var second = EFormBrowserPdfService.ADMISSION.acquire(RenderAdmission.Kind.CHART, Duration.ZERO);
        var pdfAdmitted = new CountDownLatch(1); var allowPdfs = new CountDownLatch(1);
        var chartAdmissions = new AtomicInteger(); var waiters = new ArrayList<Thread>(); var failures = new ConcurrentLinkedQueue<Throwable>();
        try {
            for (int i = 0; i < 20; i++) {
                final boolean asPdf = i >= 16;
                Thread worker = new Thread(() -> {
                    try (var lease = EFormBrowserPdfService.ADMISSION.acquire(asPdf ? RenderAdmission.Kind.PDF : RenderAdmission.Kind.CHART, Duration.ofSeconds(15))) {
                        require(lease != null, "Queued work unexpectedly refused");
                        if (asPdf) { pdfAdmitted.countDown(); require(allowPdfs.await(10, TimeUnit.SECONDS), "PDF fairness gate timed out"); }
                        else chartAdmissions.incrementAndGet();
                    } catch (Throwable failure) { failures.add(failure); }
                });
                waiters.add(worker); worker.start();
                long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
                while (worker.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) Thread.yield();
                require(worker.getState() == Thread.State.TIMED_WAITING, "Native admission waiter did not queue");
            }
            try { png(); throw new AssertionError("Chart queue overflow was accepted"); }
            catch (ChartRenderFailure failure) { require(failure.reason() == ChartRenderFailure.Reason.BUSY, "Queue overflow was not typed BUSY"); }
            require(EFormBrowserPdfService.ADMISSION.acquire(RenderAdmission.Kind.PDF, Duration.ZERO) == null, "PDF queue ceiling exceeded");
            second.close(); require(pdfAdmitted.await(3, TimeUnit.SECONDS), "Queued PDF starved behind borrowed chart slots");
            require(chartAdmissions.get() == 0, "Chart work overtook queued PDF work");
        } finally {
            allowPdfs.countDown(); if (first != null) first.close(); if (second != null) second.close();
            for (Thread worker : waiters) worker.join(5000);
            for (Thread worker : waiters) if (worker.isAlive()) { worker.interrupt(); worker.join(2000); }
        }
        require(failures.isEmpty() && chartAdmissions.get() == 16 && EFormBrowserPdfService.RENDER_SLOTS.availablePermits() == 2,
                "Bounded admission lost or leaked queued work");
        // An interrupted queued chart must never start Chrome or retain its queue ticket/permit.
        int held = EFormBrowserPdfService.RENDER_SLOTS.drainPermits(); var outcome = new AtomicReference<Throwable>(); var preserved = new AtomicBoolean();
        Thread interrupted = new Thread(() -> { try { png(); outcome.set(new AssertionError("Interrupted chart rendered")); } catch (Throwable failure) { outcome.set(failure); preserved.set(Thread.currentThread().isInterrupted()); } });
        try {
            interrupted.start(); long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (interrupted.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) Thread.yield();
            require(interrupted.getState() == Thread.State.TIMED_WAITING, "Chart did not queue"); interrupted.interrupt(); interrupted.join(5000);
            require(!interrupted.isAlive() && outcome.get() instanceof ChartRenderFailure failure && failure.reason() == ChartRenderFailure.Reason.INTERRUPTED && preserved.get(), "Interrupted chart was not explicit");
        } finally { interrupted.interrupt(); interrupted.join(5000); EFormBrowserPdfService.RENDER_SLOTS.release(held); }
        png(); require(sessions() == 0 && EFormBrowserPdfService.RENDER_SLOTS.availablePermits() == 2, "Interrupted work prevented recovery");
        results.put("locales", List.of("en-CA", "fr-CA", "es-ES", "pl-PL", "pt-BR")); results.put("mixedRealRenders", 10); results.put("peakAdmission", peak.get());
        results.put("boundedQueues", Map.of("chart", 16, "pdf", 4)); results.put("pdfFairness", true);
        results.put("queuedInterruptionPreserved", true); results.put("remainingSessions", sessions()); results.put("remainingPermits", EFormBrowserPdfService.RENDER_SLOTS.availablePermits());
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("qualification.json").toFile(), results); System.out.println("PASS native locale PNG/PDF, mixed render admission, interruption and cleanup");
    }
}
