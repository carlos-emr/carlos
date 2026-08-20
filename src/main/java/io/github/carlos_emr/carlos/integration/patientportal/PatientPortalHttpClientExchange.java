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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Set;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;

/**
 * Sends portal requests over Apache HttpClient 5, the client CARLOS already uses for outbound HTTP.
 *
 * <p>Four settings here are security-relevant rather than tuning:
 *
 * <ul>
 *   <li><b>Redirects are disabled.</b> Following one would replay the {@code Authorization} header
 *       at whatever host the response named, handing the portal service token to it. A redirect
 *       from the portal is a misconfiguration, and failing is the correct response.
 *   <li><b>Both timeouts are set where HttpClient 5 actually reads them.</b> The socket connect
 *       timeout lives on {@link ConnectionConfig} and the read timeout on {@link RequestConfig}.
 *       This distinction is not cosmetic: {@code RequestConfig.setConnectionRequestTimeout} is the
 *       <em>pool-lease</em> wait, and an earlier revision set only that, leaving the real connect
 *       timeout at the library default of three minutes. A portal host that accepted SYN and never
 *       completed the handshake would then pin a Tomcat worker for three minutes per call — the
 *       EMR-wide availability failure this control exists to prevent.
 *   <li><b>The response body is capped.</b> A compromised or malfunctioning portal should not be
 *       able to exhaust heap through a reply CARLOS reads into memory.
 *   <li><b>The portal's public key can be pinned.</b> When pins are configured, the socket factory
 *       requires the leaf key to match one of them <em>in addition to</em> normal validation. Note
 *       what happens when they are not: pinning is simply skipped, so a misspelled property key or
 *       a value blanked during a config merge downgrades a deployment that believes it is pinned to
 *       CA-only TLS, with nothing logged or rejected. See {@code PortalCertificatePinning} for the
 *       threat that makes pinning worth configuring.
 * </ul>
 *
 * <p>The client is built once per instance rather than per request. A per-request client meant a
 * fresh TLS handshake and a fresh empty connection pool on every staff action, and it also made the
 * pool-lease timeout structurally unreachable. Callers that own an instance should {@link #close()}
 * it.
 *
 * @since 2026-08-19
 */
class PatientPortalHttpClientExchange implements PatientPortalHttpExchange, Closeable {

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
        if (certificatePins != null && !certificatePins.isEmpty()) {
            // Single-argument SSLConnectionSocketFactory keeps HttpClient's default hostname
            // verifier. Passing a verifier here would be the second classic way to disable a TLS
            // check while appearing to configure one.
            connectionManagerBuilder.setSSLSocketFactory(
                    new SSLConnectionSocketFactory(pinnedContext(certificatePins)));
        }
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
        String body = entity == null ? "" : readCapped(entity);
        return new PatientPortalHttpResponse(response.getCode(), body);
    }

    /**
     * Reads the body, stopping once {@link #MAX_RESPONSE_CHARS} characters have been collected.
     *
     * <p>Truncation is not signalled to the caller, and callers must not assume a truncated body
     * will fail to parse. It usually will — a document cut mid-token raises a Jackson EOF — but
     * Jackson does not reject trailing content by default, so a reply whose first complete JSON
     * value ends inside the cap parses cleanly and the truncation is invisible. That is acceptable
     * only because the cap exists to bound memory, not to validate the peer: every documented portal
     * reply is far smaller, so reaching the cap already means the peer is not the portal.
     */
    private static String readCapped(HttpEntity entity) throws IOException {
        StringBuilder collected = new StringBuilder();
        char[] buffer = new char[READ_BUFFER_CHARS];
        try (InputStream content = entity.getContent();
                Reader reader = new InputStreamReader(content, StandardCharsets.UTF_8)) {
            int read;
            while (collected.length() < MAX_RESPONSE_CHARS && (read = reader.read(buffer)) >= 0) {
                int remaining = MAX_RESPONSE_CHARS - collected.length();
                collected.append(buffer, 0, Math.min(read, remaining));
            }
        }
        return collected.toString();
    }
}
