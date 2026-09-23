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

import io.github.carlos_emr.carlos.utility.MiscUtils;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.Reader;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ssl.TLS;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.Logger;

/**
 * Pooled HTTP transport with TLS 1.2/1.3, standard certificate validation and required leaf-key pins.
 * Redirects and automatic retries are disabled. An overall deadline bounds servlet waiting in
 * addition to connect and read inactivity timeouts. At most four exchanges run, with no queue.
 * Cookie management is disabled so a response cannot add shared state to later staff requests.
 * Oversized decoded responses abort their connection rather than draining it for reuse.
 * Owners must close the client when it is no longer needed.
 */
class PatientPortalHttpClientExchange implements PatientPortalHttpExchange, Closeable {

    private static final Logger logger = MiscUtils.getLogger();

    private static final String PINNING_ON =
            "patient portal transport: certificate pinning active ({} pin(s))";

    /**
     * Cap on the decoded response we will hold in memory, counted in UTF-16 characters.
     *
     * <p>Characters rather than bytes because that is what a {@link StringBuilder} measures. For
     * multi-byte UTF-8 the wire payload may be larger, and the builder itself holds roughly two
     * bytes per character plus growth slack, so treat this as an order-of-magnitude bound rather
     * than an exact byte ceiling.
     */
    static final int MAX_RESPONSE_CHARS = 1024 * 1024;

    private static final int READ_BUFFER_CHARS = 8192;
    static final int MAX_CONCURRENT_REQUESTS = 4;
    /**
     * A pooled connection idle longer than this is checked before reuse. The portal (uvicorn, or
     * a proxy in front of it) closes idle keep-alive connections, and automatic retries are off,
     * so reusing a closed one would fail the call outright.
     */
    static final TimeValue VALIDATE_AFTER_INACTIVITY = TimeValue.ofMilliseconds(250);
    /** Retire idle connections before the portal does; uvicorn's default keep-alive is 5 s. */
    static final TimeValue MAX_IDLE = TimeValue.ofSeconds(2);

    private final CloseableHttpClient client;
    private final Duration requestTimeout;
    private final ThreadPoolExecutor workers;
    private final Set<HttpUriRequestBase> activeRequests = ConcurrentHashMap.newKeySet();

    PatientPortalHttpClientExchange(PatientPortalSettings settings) {
        this(settings.connectTimeout(), settings.readTimeout(), settings.requestTimeout(),
                settings.certificatePins());
    }

    /**
     * Takes only the transport timeouts and required pins.
     *
     * <p>Depending on the whole settings record would drag the https rule into a test that needs a
     * plain loopback socket, and would tempt a bypass in production code to satisfy a test. The
     * transport has no business knowing the base URL or the credential — the request it is handed
     * already carries both.
     *
     * @param certificatePins public-key pins the portal must present; must not be empty
     */
    PatientPortalHttpClientExchange(
            Duration connectTimeout, Duration readTimeout, Set<String> certificatePins) {
        this(connectTimeout, readTimeout, PatientPortalSettings.DEFAULT_REQUEST_TIMEOUT, certificatePins);
    }

    PatientPortalHttpClientExchange(Duration connectTimeout, Duration readTimeout,
            Duration requestTimeout, Set<String> certificatePins) {
        // Enforce pinning here too, so direct construction cannot bypass settings validation.
        SSLContext sslContext = pinnedContext(certificatePins);
        this.requestTimeout = requestTimeout;
        ConnectionConfig connectionConfig =
                ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
                        .setValidateAfterInactivity(VALIDATE_AFTER_INACTIVITY)
                        .build();
        PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(MAX_CONCURRENT_REQUESTS)
                        .setMaxConnPerRoute(MAX_CONCURRENT_REQUESTS)
                        .setDefaultConnectionConfig(connectionConfig);
        logger.info(PINNING_ON, certificatePins.size());
        // Keep the default hostname verifier and explicitly require modern TLS.
        SSLConnectionSocketFactoryBuilder socketFactoryBuilder =
                SSLConnectionSocketFactoryBuilder.create()
                        .setTlsVersions(TLS.V_1_2, TLS.V_1_3)
                        .setSslContext(sslContext);
        connectionManagerBuilder.setSSLSocketFactory(socketFactoryBuilder.build());
        PoolingHttpClientConnectionManager connectionManager = connectionManagerBuilder.build();
        RequestConfig requestConfig =
                RequestConfig.custom()
                        .setConnectionRequestTimeout(
                                Timeout.ofMilliseconds(connectTimeout.toMillis()))
                        .setResponseTimeout(Timeout.ofMilliseconds(readTimeout.toMillis()))
                        .setRedirectsEnabled(false)
                        .build();
        this.client =
                HttpClients.custom()
                        .setConnectionManager(connectionManager)
                        // Belt and braces on a security control: the builder switch and the
                        // per-request flag are independent paths to the same guarantee, and a
                        // redirect must never replay the bearer token at another host.
                        .disableRedirectHandling()
                        // Retries stay off: a POST must never be repeated behind the caller's
                        // back. Stale pooled connections are handled by the validation and idle
                        // eviction configured here instead.
                        .disableAutomaticRetries()
                        .evictIdleConnections(MAX_IDLE)
                        // The singleton transport serves unrelated staff and patients. The internal
                        // API authenticates every call with explicit bearer/assertion headers, so a
                        // response cookie is both unnecessary and unsafe shared request state.
                        .disableCookieManagement()
                        .setDefaultRequestConfig(requestConfig)
                        .build();
        // A stalled DNS lookup or TLS exchange need not respond to interruption. Keep it in
        // this bounded pool, retaining its slot until it actually exits, rather than retaining
        // a servlet thread or creating an unbounded queue of replacements after timeouts.
        this.workers = new ThreadPoolExecutor(0, MAX_CONCURRENT_REQUESTS, 30, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                Thread.ofPlatform().daemon().name("patient-portal-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public PatientPortalHttpResponse send(ClassicHttpRequest request) throws IOException {
        HttpUriRequestBase cancellable;
        try {
            cancellable = new HttpUriRequestBase(request.getMethod(), request.getUri());
        } catch (URISyntaxException exception) {
            throw new IOException("portal request URI is invalid");
        }
        cancellable.setHeaders(request.getHeaders());
        cancellable.setEntity(request.getEntity());
        Future<PatientPortalHttpResponse> pending;
        long started = System.nanoTime();
        try {
            pending = workers.submit(() -> {
                activeRequests.add(cancellable);
                try {
                    return client.execute(cancellable, PatientPortalHttpClientExchange::toResponse);
                } finally {
                    activeRequests.remove(cancellable);
                }
            });
        } catch (RejectedExecutionException exception) {
            throw new IOException("portal transport is busy or closed");
        }
        try {
            long remaining = TimeUnit.MILLISECONDS.toNanos(requestTimeout.toMillis())
                    - (System.nanoTime() - started);
            return pending.get(Math.max(0, remaining), TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            throw new SocketTimeoutException("portal request deadline exceeded");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("portal request interrupted");
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("portal transport failed", exception.getCause());
        } finally {
            if (!pending.isDone()) {
                // Cancellation must close the actual connection as well as interrupt the worker.
                cancellable.cancel();
                pending.cancel(true);
            }
        }
    }

    @Override
    public void close() throws IOException {
        workers.shutdownNow();
        activeRequests.forEach(HttpUriRequestBase::cancel);
        client.close();
    }

    /**
     * Builds an SSL context whose trust manager is the platform one plus a pin requirement.
     *
     * <p>The context is created with a null KeyManager and the default SecureRandom so that only
     * trust evaluation changes; nothing else about the JVM's TLS configuration is overridden.
     */
    private static SSLContext pinnedContext(Set<String> certificatePins) {
        try {
            // "TLS" means "the provider's best supported version". Naming a fixed version here
            // would freeze this channel at it; the floor is expressed on the socket factory above
            // via setTlsVersions, which is where it belongs.
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(
                    null, new TrustManager[] {PortalCertificatePinning.over(certificatePins)}, null);
            return context;
        } catch (GeneralSecurityException exception) {
            throw new PatientPortalConfigurationException(
                    "could not configure portal certificate pinning", exception);
        }
    }

    private static PatientPortalHttpResponse toResponse(ClassicHttpResponse response)
            throws IOException {
        HttpEntity entity = response.getEntity();
        String body = entity == null ? "" : readCapped(response);
        return new PatientPortalHttpResponse(response.getCode(), body);
    }

    /** Rejects oversize replies and discards their connection before stream close can drain it. */
    private static String readCapped(ClassicHttpResponse response) throws IOException {
        StringBuilder collected = new StringBuilder();
        char[] buffer = new char[READ_BUFFER_CHARS];
        try (InputStream content = response.getEntity().getContent();
                Reader reader = new InputStreamReader(content, StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT))) {
            try {
                int read;
                while ((read = reader.read(buffer, 0,
                        Math.min(buffer.length, MAX_RESPONSE_CHARS - collected.length() + 1))) >= 0) {
                    if (read > MAX_RESPONSE_CHARS - collected.length()) {
                        throw new PortalResponseTooLargeException(response.getCode());
                    }
                    collected.append(buffer, 0, read);
                }
            } catch (IOException | RuntimeException exception) {
                // HttpClient returns a ModalCloseable response. Abort before closing its entity:
                // a normal close drains the body for reuse and could wait on an endless tail.
                if (response instanceof ModalCloseable closeable) {
                    closeable.close(CloseMode.IMMEDIATE);
                }
                if (exception instanceof CharacterCodingException) {
                    throw new PortalResponseDecodingException(response.getCode());
                }
                throw exception;
            }
        }
        return collected.toString();
    }
}
