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
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Real sockets exercise cancellation, admission, and recovery, not mocked timeout settings. */
@Tag("unit")
@Tag("patient-portal")
class PortalRequestDeadlineUnitTest {
    private HttpServer server;
    private ExecutorService serverWorkers;
    private String origin;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverWorkers = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(serverWorkers);
        server.createContext("/healthy", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        serverWorkers.shutdownNow();
    }

    private ClassicHttpRequest get(String path) {
        return ClassicRequestBuilder.get(origin + path).build();
    }

    private PatientPortalHttpClientExchange transport(Duration deadline) {
        return new PatientPortalHttpClientExchange(
                Duration.ofSeconds(1), Duration.ofSeconds(5), deadline, Set.of());
    }

    @Test
    void shouldAbortTricklingResponse_atDeadlineAndRecover() throws Exception {
        CountDownLatch disconnected = new CountDownLatch(1);
        server.createContext("/trickle", exchange -> {
            exchange.sendResponseHeaders(200, 1000);
            try (var out = exchange.getResponseBody()) {
                for (int i = 0; i < 100; i++) {
                    out.write(' ');
                    out.flush();
                    Thread.sleep(50);
                }
            } catch (IOException exception) {
                disconnected.countDown();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        try (var transport = transport(Duration.ofMillis(500))) {
            long start = System.nanoTime();
            assertThatThrownBy(() -> transport.send(get("/trickle")))
                    .isInstanceOf(SocketTimeoutException.class)
                    .hasMessage("portal request deadline exceeded");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
            assertThat(disconnected.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(transport.send(get("/healthy")).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void shouldRejectExcessWork_withoutSendingOrQueueingIt() throws Exception {
        int capacity = PatientPortalHttpClientExchange.MAX_CONCURRENT_REQUESTS;
        CountDownLatch entered = new CountDownLatch(capacity);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger hits = new AtomicInteger();
        server.createContext("/hold", exchange -> {
            hits.incrementAndGet();
            entered.countDown();
            try {
                release.await();
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        try (var transport = transport(Duration.ofSeconds(15));
                var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<PatientPortalHttpResponse>> pending = new ArrayList<>();
            try {
                for (int i = 0; i < capacity; i++) {
                    pending.add(callers.submit(() -> transport.send(get("/hold"))));
                }
                assertThat(entered.await(4, TimeUnit.SECONDS)).isTrue();
                long start = System.nanoTime();
                assertThatThrownBy(() -> transport.send(get("/hold")))
                        .isInstanceOf(IOException.class).hasMessage("portal transport is busy or closed");
                assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
                assertThat(hits.get()).isEqualTo(capacity);
            } finally {
                release.countDown();
            }
            for (var future : pending) {
                assertThat(future.get(3, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            }
            assertThat(transport.send(get("/healthy")).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void shouldPreserveInterrupt_andCancelTheOutstandingRequest() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/hold", exchange -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        AtomicReference<IOException> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        try (var transport = transport(Duration.ofSeconds(15))) {
            Thread caller = Thread.ofPlatform().start(() -> {
                try {
                    transport.send(get("/hold"));
                } catch (IOException exception) {
                    failure.set(exception);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            });
            try {
                assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                caller.interrupt();
                caller.join(3000);
                assertThat(caller.isAlive()).isFalse();
                assertThat(failure.get()).isInstanceOf(InterruptedIOException.class);
                assertThat(interrupted.get()).isTrue();
            } finally {
                release.countDown();
                caller.interrupt();
            }
        }
    }

    @Test
    void shouldAbortPendingExchange_whenClosed() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/hold", exchange -> {
            entered.countDown();
            try {
                release.await();
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        try (var transport = transport(Duration.ofSeconds(15));
                var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = callers.submit(() -> transport.send(get("/hold")));
            try {
                assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                transport.close();
                assertThatThrownBy(() -> pending.get(3, TimeUnit.SECONDS))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class)
                        .hasCauseInstanceOf(IOException.class);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void shouldRejectRequests_afterClose() throws Exception {
        var transport = transport(Duration.ofSeconds(1));
        transport.close();
        assertThatThrownBy(() -> transport.send(get("/healthy")))
                .isInstanceOf(IOException.class).hasMessage("portal transport is busy or closed");
    }
}
