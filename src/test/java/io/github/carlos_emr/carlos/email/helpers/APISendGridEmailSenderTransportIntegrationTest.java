/* Copyright (c) 2026 CARLOS Contributors. Published under the GPL GNU General Public License. */
package io.github.carlos_emr.carlos.email.helpers;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.EmailConfig;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.hc.core5.ssl.SSLContexts;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real loopback HTTPS verifies captured bytes, response handling and absence of hidden retries. */
@Tag("integration")
class APISendGridEmailSenderTransportIntegrationTest {
    @TempDir Path tempDir;

    @ParameterizedTest
    @ValueSource(ints = {202, 401, 429, 503, 307, 0})
    void shouldSendCapturedBytesOnce_whenProviderOutcomeVaries(int status) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var name = new X500Name("CN=localhost");
        var certificate = new JcaX509v3CertificateBuilder(name, BigInteger.ONE,
                Date.from(Instant.now().minusSeconds(60)), Date.from(Instant.now().plusSeconds(3600)),
                name, pair.getPublic());
        certificate.addExtension(Extension.subjectAlternativeName, false,
                new GeneralNames(new GeneralName(GeneralName.iPAddress, "127.0.0.1")));
        var cert = new JcaX509CertificateConverter().getCertificate(certificate.build(
                new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
        var store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        char[] password = new char[0];
        store.setKeyEntry("test", pair.getPrivate(), password, new java.security.cert.Certificate[]{cert});
        var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, password);
        var serverContext = SSLContext.getInstance("TLS");
        serverContext.init(keys.getKeyManagers(), null, null);
        var clientContext = SSLContexts.custom().loadTrustMaterial(store, null);
        var server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext));
        List<byte[]> received = new CopyOnWriteArrayList<>();
        List<String> authorization = new CopyOnWriteArrayList<>();
        server.createContext("/send", exchange -> {
            received.add(exchange.getRequestBody().readAllBytes());
            authorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
            if (status != 0) {
                exchange.getResponseHeaders().set("Location", "/redirect-target");
                exchange.sendResponseHeaders(status, -1);
            }
            exchange.close();
        });
        server.start();
        String property = "carlos.email.sendgrid.allowedHosts";
        String original = System.getProperty(property);
        System.setProperty(property, "127.0.0.1");
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), any(), any(), any())).thenReturn(true);
        try (var spring = mockStatic(SpringUtils.class); var ssl = mockStatic(SSLContexts.class)) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            // Trust only this test's ephemeral certificate; production client/HTTPS validation stays real.
            ssl.when(SSLContexts::custom).thenReturn(clientContext);
            var config = new EmailConfig(EmailConfig.EmailType.API, EmailConfig.EmailProvider.SENDGRID,
                    "sender@example.test");
            config.setSenderFirstName("Synthetic");
            config.setSenderLastName("Sender");
            config.setConfigDetailsJson("{\"api_key\":\"synthetic-key\",\"end_point\":\"https://127.0.0.1:"
                    + server.getAddress().getPort() + "/send\"}");
            Path attachment = tempDir.resolve("attachment.txt");
            Files.writeString(attachment, "original synthetic content");
            var sender = new APISendGridEmailSender(mock(LoggedInInfo.class), config,
                    new String[]{"recipient@example.test"}, "Synthetic subject", "Body", List.of(
                    new EmailAttachment("attachment.txt", attachment.toString(), null, 0)));
            byte[] returned = sender.prepareArtifactBytes();
            byte[] archived = returned.clone();
            assertThat(sender.describePreparedAttachments()).singleElement()
                    .satisfies(metadata -> assertThat(metadata.getContentType()).isEqualTo("text/plain"));
            var payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(archived);
            assertThat(payload.path("attachments").get(0).path("type").asText()).isEqualTo("text/plain");
            java.util.Arrays.fill(returned, (byte) 0);
            Files.writeString(attachment, "changed after preparation");
            if (status == 202) {
                sender.sendPrepared();
            } else {
                assertThatThrownBy(sender::sendPrepared).isInstanceOfSatisfying(EmailSendingException.class,
                        e -> assertThat(e.isDeliveryOutcomeUncertain()).isEqualTo(status == 0));
            }
            assertThat(received).hasSize(1);
            assertThat(received.get(0)).containsExactly(archived);
            assertThat(authorization).containsExactly("Bearer synthetic-key");
            assertThat(new String(archived, java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("synthetic-key");
            assertThatThrownBy(sender::sendPrepared).isInstanceOf(EmailSendingException.class)
                    .hasMessageContaining("must be prepared");
        } finally {
            server.stop(0);
            if (original == null) System.clearProperty(property); else System.setProperty(property, original);
        }
    }
}
