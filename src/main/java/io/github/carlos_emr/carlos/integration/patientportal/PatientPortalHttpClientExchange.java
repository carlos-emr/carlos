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
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
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
import org.apache.hc.core5.util.Timeout;
import org.apache.logging.log4j.Logger;

/**
 * Pooled HTTP transport with TLS 1.2/1.3, standard certificate validation and optional leaf-key pins.
 * Redirects and automatic retries are disabled. Connect, pool-lease and read waits are bounded;
 * the read timeout is an inactivity timeout, not an overall request deadline.
 * Oversized decoded responses abort their connection rather than draining it for reuse.
 * Owners must close the client when it is no longer needed.
 */
class PatientPortalHttpClientExchange implements PatientPortalHttpExchange, Closeable {

    private static final Logger logger = MiscUtils.getLogger();

    private static final String PINNING_ON =
            "patient portal transport: certificate pinning active (%d pin(s))";
    private static final String PINNING_OFF =
            "patient portal transport: certificate pinning NOT configured; the portal is trusted on"
                    + " CA validation alone";

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

    private final CloseableHttpClient client;

    PatientPortalHttpClientExchange(PatientPortalSettings settings) {
        this(settings.connectTimeout(), settings.readTimeout(), settings.certificatePins());
    }

    /**
     * Takes only the values this class uses: the two timeouts, and the pins in the overload below.
     *
     * <p>Depending on the whole settings record would drag the https rule into a test that needs a
     * plain loopback socket, and would tempt a bypass in production code to satisfy a test. The
     * transport has no business knowing the base URL or the credential — the request it is handed
     * already carries both.
     */
    PatientPortalHttpClientExchange(Duration connectTimeout, Duration readTimeout) {
        this(connectTimeout, readTimeout, Set.of());
    }

    /**
     * @param certificatePins public-key pins the portal must present, or empty for standard TLS
     *     validation only
     */
    PatientPortalHttpClientExchange(
            Duration connectTimeout, Duration readTimeout, Set<String> certificatePins) {
        ConnectionConfig connectionConfig =
                ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
                        .build();
        PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(connectionConfig);
        // Record whether optional pinning is active without logging configuration values.
        logger.info(
                certificatePins == null || certificatePins.isEmpty()
                        ? PINNING_OFF
                        : String.format(Locale.ROOT, PINNING_ON, certificatePins.size()));
        // Keep the default hostname verifier and explicitly require modern TLS on both paths.
        SSLConnectionSocketFactoryBuilder socketFactoryBuilder =
                SSLConnectionSocketFactoryBuilder.create()
                        .setTlsVersions(TLS.V_1_2, TLS.V_1_3);
        if (certificatePins != null && !certificatePins.isEmpty()) {
            socketFactoryBuilder.setSslContext(pinnedContext(certificatePins));
        }
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
                        .disableAutomaticRetries()
                        .setDefaultRequestConfig(requestConfig)
                        .build();
    }

    @Override
    public PatientPortalHttpResponse send(ClassicHttpRequest request) throws IOException {
        return client.execute(request, PatientPortalHttpClientExchange::toResponse);
    }

    @Override
    public void close() throws IOException {
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
                Reader reader = new InputStreamReader(content, StandardCharsets.UTF_8)) {
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
                throw exception;
            }
        }
        return collected.toString();
    }
}
