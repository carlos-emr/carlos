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

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * What stops a fake portal from collecting the service token and a patient's identity.
 *
 * <p>An invitation carries the patient's email, date of birth, and health card number, and every
 * request carries the service token, so a peer that successfully impersonates the portal harvests
 * all of it in one call. These tests stand up real TLS servers with generated certificates and
 * assert CARLOS refuses each impersonation, and that <b>no request body reaches the impostor</b> —
 * a handshake failure after the payload was sent would be no protection at all.
 *
 * <p>The failure this guards against is mundane: someone hits a certificate error in a local
 * environment and installs a trust-everything {@code SSLContext} to get past it. Nothing else in
 * the codebase would notice.
 */
@Tag("unit")
@Tag("patient-portal")
@DisplayName("Portal TLS trust")
class PortalTlsTrustUnitTest {

    private HttpsServer server;
    private final AtomicInteger requestsReceived = new AtomicInteger();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** A self-signed certificate and its key, for a server nobody has been told to trust. */
    private record Identity(KeyPair keyPair, X509Certificate certificate) {}

    private Identity identity(String commonName, String subjectAlternativeName) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=" + commonName);
        Instant now = Instant.now();
        JcaX509v3CertificateBuilder builder =
                new JcaX509v3CertificateBuilder(
                        subject,
                        BigInteger.valueOf(now.toEpochMilli()),
                        Date.from(now.minus(Duration.ofHours(1))),
                        Date.from(now.plus(Duration.ofHours(1))),
                        subject,
                        keyPair.getPublic());
        builder.addExtension(
                Extension.subjectAlternativeName,
                false,
                new GeneralNames(new GeneralName(GeneralName.iPAddress, subjectAlternativeName)));
        X509Certificate certificate =
                new JcaX509CertificateConverter()
                        .getCertificate(
                                builder.build(
                                        new JcaContentSignerBuilder("SHA256withRSA")
                                                .build(keyPair.getPrivate())));
        return new Identity(keyPair, certificate);
    }

    /** Starts an HTTPS server presenting the given identity, counting every request it receives. */
    private String startServer(Identity identity) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, "changeit".toCharArray());
        keyStore.setKeyEntry(
                "server",
                identity.keyPair().getPrivate(),
                "changeit".toCharArray(),
                new java.security.cert.Certificate[] {identity.certificate()});
        KeyManagerFactory keyManagers =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, "changeit".toCharArray());
        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keyManagers.getKeyManagers(), null, null);

        server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        server.createContext(
                "/",
                exchange -> {
                    requestsReceived.incrementAndGet();
                    byte[] body = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                });
        server.start();
        return "https://127.0.0.1:" + server.getAddress().getPort();
    }

    private ClassicHttpRequest request(String origin) {
        return ClassicRequestBuilder.get(origin + "/internal/carlos/ping")
                .setHeader("Authorization", "Bearer service-token-that-must-not-leak")
                .build();
    }

    private Duration quick() {
        return Duration.ofSeconds(5);
    }

    @Test
    @DisplayName("should refuse a portal whose certificate no trusted CA issued")
    void shouldRefuseUntrustedCertificate_andSendNothingToIt() throws Exception {
        String origin = startServer(identity("localhost", "127.0.0.1"));

        try (PatientPortalHttpClientExchange transport =
                new PatientPortalHttpClientExchange(quick(), quick())) {
            assertThatThrownBy(() -> transport.send(request(origin)))
                    .isInstanceOf(IOException.class);
        }
        assertThat(requestsReceived.get())
                .withFailMessage("the impostor received a request; the token and PHI would have"
                        + " reached it before the handshake was rejected")
                .isZero();
    }

    /**
     * The attack pinning exists to stop: a certificate that passes every standard check, because
     * the machine trusts the CA that issued it, but is not the portal's key.
     */
    @Test
    @DisplayName("should refuse a valid certificate that is not the pinned key")
    void shouldRefuseUnpinnedKey_evenWhenTheCertificateItselfIsValid() throws Exception {
        Identity server = identity("localhost", "127.0.0.1");
        Identity somebodyElse = identity("localhost", "127.0.0.1");
        String origin = startServer(server);
        Set<String> pinnedToSomebodyElse =
                Set.of(PortalCertificatePinning.pinFor(somebodyElse.certificate()));

        try (PatientPortalHttpClientExchange transport =
                new PatientPortalHttpClientExchange(quick(), quick(), pinnedToSomebodyElse)) {
            assertThatThrownBy(() -> transport.send(request(origin)))
                    .isInstanceOf(IOException.class);
        }
        assertThat(requestsReceived.get()).isZero();
    }

    /**
     * Pinning must not replace validation. A pin that matches an otherwise untrusted certificate
     * is still refused, because the platform trust manager runs first — the check that a
     * trust-everything implementation would quietly drop.
     */
    @Test
    @DisplayName("should still require a trusted chain even when the pin matches")
    void shouldRefuseUntrustedCertificate_evenWhenItsOwnPinIsConfigured() throws Exception {
        Identity server = identity("localhost", "127.0.0.1");
        String origin = startServer(server);
        Set<String> pinnedToThisServer =
                Set.of(PortalCertificatePinning.pinFor(server.certificate()));

        try (PatientPortalHttpClientExchange transport =
                new PatientPortalHttpClientExchange(quick(), quick(), pinnedToThisServer)) {
            assertThatThrownBy(() -> transport.send(request(origin)))
                    .isInstanceOf(IOException.class);
        }
        assertThat(requestsReceived.get()).isZero();
    }

    @Test
    @DisplayName("should compute a stable pin for a certificate")
    void shouldDeriveThePin_fromThePublicKey() throws Exception {
        Identity identity = identity("localhost", "127.0.0.1");

        String pin = PortalCertificatePinning.pinFor(identity.certificate());

        assertThat(pin).startsWith(PortalCertificatePinning.PIN_PREFIX);
        assertThat(pin).isEqualTo(PortalCertificatePinning.pinFor(identity.certificate()));
        assertThat(pin).isNotEqualTo(PortalCertificatePinning.pinFor(identity("localhost",
                "127.0.0.1").certificate()));
    }

    @Test
    @DisplayName("should refuse a pin that is not in the documented format")
    void shouldThrow_whenAPinIsMalformed() {
        assertThatThrownBy(() -> PortalCertificatePinning.over(Set.of("not-a-pin")))
                .isInstanceOf(PatientPortalConfigurationException.class);
        assertThatThrownBy(() -> PortalCertificatePinning.over(Set.of("sha256/")))
                .isInstanceOf(PatientPortalConfigurationException.class);
        assertThatThrownBy(() -> PortalCertificatePinning.over(Set.of()))
                .isInstanceOf(PatientPortalConfigurationException.class);
    }
}
