/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Published under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.fax.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Proves rejected HTTP responses return before the server releases their remaining bytes.
 * This detects implicit body draining during stream or response closure, including gzip wrappers.
 *
 * @since 2026-10-19
 */
@Tag("integration")
@Tag("fax")
@Timeout(30)
class FaxResponseAbortIntegrationTest {
    private static final String ALLOWED_HOSTS = "carlos.fax.middleware.allowedHosts";
    private static final int BODY_BYTES = 2 * 1024 * 1024;

    @TempDir
    Path directory;
    private String previousCap;
    private String previousHosts;

    enum Transport {
        SRFAX_DOCUMENT, SRFAX_METADATA, MIDDLEWARE_DOCUMENT, MIDDLEWARE_XML, MIDDLEWARE_METADATA
    }

    enum Reply {
        CHUNKED, DECLARED, GZIP, MALFORMED, HTTP_ERROR, GZIP_SUCCESS
    }

    @BeforeEach
    void configureLimits() {
        previousCap = CarlosProperties.getInstance().getProperty(BoundedResponseReader.MAX_RESPONSE_MB_PROPERTY);
        CarlosProperties.getInstance().setProperty(BoundedResponseReader.MAX_RESPONSE_MB_PROPERTY, "1");
        previousHosts = System.getProperty(ALLOWED_HOSTS);
        System.setProperty(ALLOWED_HOSTS, "127.0.0.1");
    }

    @AfterEach
    void restoreLimits() {
        if (previousCap == null) {
            CarlosProperties.getInstance().remove(BoundedResponseReader.MAX_RESPONSE_MB_PROPERTY);
        } else {
            CarlosProperties.getInstance().setProperty(BoundedResponseReader.MAX_RESPONSE_MB_PROPERTY, previousCap);
        }
        if (previousHosts == null) {
            System.clearProperty(ALLOWED_HOSTS);
        } else {
            System.setProperty(ALLOWED_HOSTS, previousHosts);
        }
    }

    static Stream<Arguments> rejectedResponses() {
        return Stream.of(Transport.values()).flatMap(transport -> Stream.of(Reply.values())
                .filter(reply -> reply != Reply.GZIP_SUCCESS).map(reply -> Arguments.of(transport, reply)));
    }

    @ParameterizedTest
    @MethodSource("rejectedResponses")
    void shouldRejectResponse_withoutWaitingForRemainingBody(Transport transport, Reply reply) throws Exception {
        try (var server = new PausedResponse(transport, reply);
                var executor = Executors.newSingleThreadExecutor()) {
            var rejected = executor.submit(() -> {
                try {
                    invoke(transport, server.endpoint());
                    return null;
                } catch (FaxProviderException expected) {
                    return expected;
                }
            });
            try {
                // The response's tail is withheld until finally. A graceful drain cannot finish here.
                FaxProviderException failure = rejected.get(8, TimeUnit.SECONDS);
                assertThat(failure).isNotNull();
                assertThat(server.requests.get()).as("HTTP transport must not repeat the fax operation").isEqualTo(1);
                if (reply == Reply.HTTP_ERROR) {
                    assertThat(failure).hasMessageContaining("503");
                } else if (reply == Reply.MALFORMED) {
                    assertThat(failure.isTransient()).isFalse();
                } else {
                    assertThat(failure).hasStackTraceContaining("Fax response exceeds fax.max_response_mb");
                }
            } finally {
                server.releaseTail.countDown();
                rejected.cancel(true);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = Transport.class, names = {"SRFAX_METADATA", "MIDDLEWARE_METADATA"})
    void shouldReadSuccessfulGzipReply_withoutClosingErrors(Transport transport) throws Exception {
        try (var server = new PausedResponse(transport, Reply.GZIP_SUCCESS)) {
            assertThat(invoke(transport, server.endpoint())).isEqualTo(3512L);
        }
    }

    @Test
    void shouldCloseAdaptedResponse_withoutRecursiveEntityClosure() throws Exception {
        try (var response = org.apache.hc.client5.http.impl.classic.CloseableHttpResponse.adapt(
                new org.apache.hc.core5.http.message.BasicClassicHttpResponse(200))) {
            response.setEntity(new org.apache.hc.core5.http.io.entity.StringEntity("{}",
                    org.apache.hc.core5.http.ContentType.APPLICATION_JSON));
            FaxResponseStream.abortOnClose(response);
            try (FaxResponseStream input = FaxResponseStream.open(response.getEntity())) {
                assertThat(input.read()).isEqualTo('{');
            }
        }
    }

    private long invoke(Transport transport, String endpoint) throws Exception {
        SRFaxProviderClient srfax = new SRFaxProviderClient(endpoint);
        if (transport == Transport.SRFAX_DOCUMENT) {
            try (FaxDownload download = srfax.downloadDocument(endpoint, List.of())) {
                return download.metadata().path("jobId").asLong();
            }
        }
        if (transport == Transport.SRFAX_METADATA) {
            return srfax.postForm(endpoint, List.of()).path("jobId").asLong();
        }
        FaxConfig config = new FaxConfig() {
            @Override
            public String getFaxPasswd() {
                return "dummy";
            }

            @Override
            public String getPasswd() {
                return "dummy";
            }
        };
        config.setProviderType(FaxConfig.ProviderType.MIDDLEWARE);
        config.setFaxUser("dummy");
        config.setSiteUser("dummy");
        config.setUrl(endpoint);
        FaxJob fax = new FaxJob();
        fax.setFile_name("synthetic.pdf");
        fax.setUser("dummy");
        fax.setJobId(3512L);
        var middleware = new MiddlewareFaxProviderClient();
        if (transport == Transport.MIDDLEWARE_XML) {
            Path file = Files.writeString(directory.resolve("outgoing.pdf"), "small synthetic document");
            return middleware.sendFax(config, fax, file).getJobId();
        }
        if (transport == Transport.MIDDLEWARE_METADATA) {
            return middleware.fetchFaxStatus(config, fax).getJobId();
        }
        FaxJob downloaded = middleware.downloadFax(config, fax);
        // Also clean up if a regression unexpectedly accepts the supposedly rejected document.
        if (downloaded.getDocumentPath() != null) {
            Files.deleteIfExists(downloaded.getDocumentPath());
        }
        return downloaded.getJobId() == null ? 0 : downloaded.getJobId();
    }

    /** Holds the final bytes back to distinguish prompt rejection from a blocking body drain. */
    private static final class PausedResponse implements AutoCloseable {
        private final HttpServer server;
        private final CountDownLatch releaseTail = new CountDownLatch(1);
        private final AtomicInteger requests = new AtomicInteger();

        private PausedResponse(Transport transport, Reply reply) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> respond(exchange, transport, reply));
            server.start();
        }

        private void respond(HttpExchange exchange, Transport transport, Reply reply) {
            requests.incrementAndGet();
            try (var request = exchange.getRequestBody()) {
                request.transferTo(OutputStream.nullOutputStream());
                boolean gzip = reply == Reply.GZIP || reply == Reply.GZIP_SUCCESS;
                if (gzip) {
                    exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                }
                byte[] prefix = prefix(transport, reply).getBytes(StandardCharsets.UTF_8);
                byte[] suffix = suffix(transport).getBytes(StandardCharsets.UTF_8);
                long length = reply == Reply.DECLARED ? prefix.length + (long) BODY_BYTES + suffix.length : 0;
                exchange.sendResponseHeaders(reply == Reply.HTTP_ERROR ? 503 : 200, length);
                try (OutputStream output = gzip ? new GZIPOutputStream(exchange.getResponseBody(), true)
                        : exchange.getResponseBody()) {
                    output.write(prefix);
                    if (reply == Reply.GZIP_SUCCESS) {
                        return;
                    }
                    byte[] bytes = new byte[8192];
                    Arrays.fill(bytes, (byte) 'A');
                    for (int written = 0; written < BODY_BYTES; written += bytes.length) {
                        output.write(bytes);
                    }
                    output.flush();
                    if (releaseTail.await(20, TimeUnit.SECONDS)) {
                        output.write(suffix);
                    }
                }
            } catch (IOException _) {
                // The expected abort can close the socket while the handler is writing its prefix.
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        }

        private static String prefix(Transport transport, Reply reply) {
            if (reply == Reply.GZIP_SUCCESS) {
                return "{\"jobId\":3512,\"status\":\"SENT\"}";
            }
            if (reply == Reply.MALFORMED) {
                return "!";
            }
            return switch (transport) {
                case SRFAX_DOCUMENT -> "{\"Result\":\"";
                case SRFAX_METADATA -> "{\"payload\":\"";
                case MIDDLEWARE_DOCUMENT -> "{\"document\":\"";
                case MIDDLEWARE_XML -> "<FaxJob><document>";
                case MIDDLEWARE_METADATA -> "{\"statusString\":\"";
            };
        }

        private static String suffix(Transport transport) {
            return switch (transport) {
                case SRFAX_DOCUMENT -> "\",\"Status\":\"Success\"}";
                case MIDDLEWARE_DOCUMENT -> "\",\"status\":\"RECEIVED\"}";
                case MIDDLEWARE_XML -> "</document><jobId>3512</jobId><status>SENT</status></FaxJob>";
                default -> "\"}";
            };
        }

        private String endpoint() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            releaseTail.countDown();
            server.stop(0);
        }
    }
}
