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
package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.WebServiceException;
import jakarta.xml.ws.soap.SOAPFaultException;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transport.http.HttpClientHTTPConduit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import ca.ontario.health.edt.EDTDelegate;
import ca.ontario.health.edt.UploadData;
import ca.ontario.health.hcv.HCValidation;
import ca.ontario.health.hcv.Requests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real builder, TLS, WS-Security and MTOM exchanges with an independent synthetic peer. */
@Tag("unit")
@Tag("mcedt")
@Timeout(30)
class EdtClientBuilderTransportUnitTest {
    private static final List<BigInteger> RESOURCE_IDS = List.of(BigInteger.ONE);
    private final Requests hcvRequest = new Requests();
    @TempDir static Path keyDirectory;
    @TempDir Path temporaryDirectory;
    private static EbsHttpsTestFixture.Keys keys;
    // CXF finalizes ClientProxy by closing its client. Retain the handlers until explicit
    // close so GC cannot race ClientImpl.destroy (which is not safe to invoke twice).
    private final List<ClientProxy> clientProxies = new ArrayList<>();
    private final ConcurrentLinkedQueue<InputStream> replays = new ConcurrentLinkedQueue<>();
    private final AtomicInteger faultChains = new AtomicInteger();
    private Bus previousBus;
    private Bus bus;
    private EbsHttpsTestFixture peer;

    @BeforeAll
    static void createKeys() throws Exception {
        keys = EbsHttpsTestFixture.createKeys(keyDirectory);
    }

    @BeforeEach
    void setUp() throws Exception {
        previousBus = BusFactory.getThreadDefaultBus(false);
        bus = BusFactory.newInstance().createBus();
        BusFactory.setThreadDefaultBus(bus);
        peer = new EbsHttpsTestFixture(keys, false);
    }

    @AfterEach
    void tearDown() throws IOException {
        try {
            for (ClientProxy proxy : clientProxies) proxy.close();
        } finally {
            try {
                BusFactory.setThreadDefaultBus(previousBus);
                if (bus != null) bus.shutdown(true);
            } finally {
                if (peer != null) peer.close();
            }
        }
    }

    @Test
    void shouldValidateHcv_withSignedEncryptedSoap() throws Exception {
        HCValidation client = client(HCValidation.class);
        assertThat(client.validate(hcvRequest, "en").getAuditUID()).isEqualTo(EbsHttpsTestFixture.AUDIT);
        peer.assertHealthy();
        assertThat(peer.requests).hasSize(1);
    }

    @Test
    void shouldExchangeMtom_withSignedUploadAndEncryptedDownload() throws Exception {
        EDTDelegate client = client(EDTDelegate.class);
        UploadData upload = new UploadData();
        upload.setContent(EbsHttpsTestFixture.PAYLOAD);
        upload.setDescription("synthetic");
        upload.setResourceType("fixture");
        assertThat(client.upload(List.of(upload)).getAuditID()).isEqualTo(EbsHttpsTestFixture.AUDIT);
        assertReplaysReleased();
        EbsHttpsTestFixture.Request request = peer.requests.element();
        assertThat(request.multipart()).isTrue();
        assertThat(request.attachments()).hasSize(1);
        assertThat(request.attachments().getFirst()).containsExactly(EbsHttpsTestFixture.PAYLOAD);
        var result = client.download(RESOURCE_IDS);
        assertThat(result.getAuditID()).isEqualTo(EbsHttpsTestFixture.AUDIT);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().getFirst().getContent()).containsExactly(EbsHttpsTestFixture.PAYLOAD);
        assertThat(peer.sentEncryptedAttachment).isTrue();
        peer.assertHealthy();
        assertReplaysReleased();
    }

    @ParameterizedTest
    @EnumSource(value = EbsHttpsTestFixture.Mode.class, names = {
            "UNSIGNED", "TAMPERED_SIGNATURE", "UNTRUSTED_SIGNER", "EXPIRED", "TAMPERED_ATTACHMENT"})
    void shouldRejectInvalidSecurity_withEncryptedMtom(EbsHttpsTestFixture.Mode mode) throws Exception {
        EDTDelegate client = client(EDTDelegate.class);
        peer.mode = mode;
        assertThatThrownBy(() -> client.download(RESOURCE_IDS))
                .isInstanceOf(SOAPFaultException.class);
        peer.assertHealthy();
        assertThat(peer.requests).hasSize(1);
        assertReplaysReleased();
        peer.mode = EbsHttpsTestFixture.Mode.NORMAL;
        assertThat(client.download(RESOURCE_IDS).getData().getFirst().getContent())
                .containsExactly(EbsHttpsTestFixture.PAYLOAD);
        assertReplaysReleased();
    }

    @ParameterizedTest
    @EnumSource(value = EbsHttpsTestFixture.Mode.class, names = {"STALLED_HEADERS", "STALLED_BODY"})
    void shouldAbortStalledResponse_withSocketReadTimeout(EbsHttpsTestFixture.Mode mode) throws Exception {
        EDTDelegate client = client(EDTDelegate.class);
        // Warm TLS, crypto and the endpoint before imposing a short read timeout.
        client.download(RESOURCE_IDS);
        long existingSpills;
        try (var files = Files.list(temporaryDirectory)) { existingSpills = files.count(); }
        ((HTTPConduit) ClientProxy.getClient(client).getConduit()).getClient().setReceiveTimeout(500);
        peer.mode = mode;
        assertThatThrownBy(() -> client.download(RESOURCE_IDS))
                .isInstanceOf(WebServiceException.class)
                .satisfies(error -> assertThat(hasCause(error, SocketTimeoutException.class)).isTrue());
        assertReplaysReleased();
        try (var files = Files.list(temporaryDirectory)) {
            assertThat(files.count()).as("stalled read must not leak a partial replay").isEqualTo(existingSpills);
        }
        peer.assertHealthy();
        peer.mode = EbsHttpsTestFixture.Mode.NORMAL;
        ((HTTPConduit) ClientProxy.getClient(client).getConduit()).getClient().setReceiveTimeout(3000);
        assertThat(client.download(RESOURCE_IDS).getData().getFirst().getContent())
                .containsExactly(EbsHttpsTestFixture.PAYLOAD);
        assertReplaysReleased();
    }

    @Test
    void shouldRejectTruncatedResponse_withoutReturningPartialData() throws Exception {
        EDTDelegate client = client(EDTDelegate.class);
        peer.mode = EbsHttpsTestFixture.Mode.TRUNCATED;
        assertThatThrownBy(() -> client.download(RESOURCE_IDS)).isInstanceOf(WebServiceException.class);
        peer.assertHealthy();
        assertThat(peer.requests).as("truncation follows peer request verification").hasSize(1);
        assertThat(replays).isEmpty(); // Rejected during caching, before hand-off to CXF.
        assertThat(AttachmentCachingInterceptor.getAttachments()).isNull();
        try (var files = Files.list(temporaryDirectory)) { assertThat(files).isEmpty(); }
    }

    @ParameterizedTest
    @EnumSource(value = EbsHttpsTestFixture.Mode.class, names = {"SOAP_FAULT", "ENCRYPTED_SOAP_FAULT"})
    void shouldReleaseReplay_withSignedServerSoapFault(EbsHttpsTestFixture.Mode mode) throws Exception {
        HCValidation client = client(HCValidation.class);
        peer.mode = mode;
        assertThatThrownBy(() -> client.validate(hcvRequest, "en"))
                .isInstanceOf(SOAPFaultException.class).hasMessageContaining("synthetic service fault");
        peer.assertHealthy();
        assertThat(faultChains).hasValue(1);
        assertReplaysReleased();
        try (var files = Files.list(temporaryDirectory)) { assertThat(files).isEmpty(); }
        peer.mode = EbsHttpsTestFixture.Mode.NORMAL;
        assertThat(client.validate(hcvRequest, "en").getAuditUID()).isEqualTo(EbsHttpsTestFixture.AUDIT);
        assertReplaysReleased();
    }

    @ParameterizedTest
    @EnumSource(value = EbsHttpsTestFixture.Mode.class, names = {"SOAP_FAULT", "ENCRYPTED_SOAP_FAULT"})
    void shouldClearAttachments_withMultipartServerFault(EbsHttpsTestFixture.Mode mode) throws Exception {
        EDTDelegate client = client(EDTDelegate.class);
        peer.mode = mode;
        assertThatThrownBy(() -> client.download(RESOURCE_IDS))
                .isInstanceOf(SOAPFaultException.class).hasMessageContaining("synthetic service fault");
        peer.assertHealthy();
        assertThat(faultChains).hasValue(1);
        assertReplaysReleased();
        peer.mode = EbsHttpsTestFixture.Mode.NORMAL;
        assertThat(client.download(RESOURCE_IDS).getData().getFirst().getContent())
                .containsExactly(EbsHttpsTestFixture.PAYLOAD);
        assertReplaysReleased();
    }

    @Test
    void shouldReleaseReplay_whenInboundFaultProcessingFails() throws Exception {
        EDTDelegate client = client(EDTDelegate.class);
        peer.mode = EbsHttpsTestFixture.Mode.SOAP_FAULT;
        Client cxf = ClientProxy.getClient(client);
        var failure = new AbstractPhaseInterceptor<Message>(Phase.UNMARSHAL) {
            { addBefore(org.apache.cxf.binding.soap.interceptor.Soap11FaultInInterceptor.class.getName()); }
            @Override public void handleMessage(Message message) {
                throw new org.apache.cxf.interceptor.Fault(new IOException("synthetic fault handler failure"));
            }
        };
        cxf.getInFaultInterceptors().add(failure);
        assertThatThrownBy(() -> client.download(RESOURCE_IDS)).isInstanceOf(WebServiceException.class);
        assertThat(faultChains).hasValue(1);
        peer.assertHealthy();
        assertReplaysReleased();
        cxf.getInFaultInterceptors().remove(failure);
        peer.mode = EbsHttpsTestFixture.Mode.NORMAL;
        assertThat(client.download(RESOURCE_IDS).getData().getFirst().getContent())
                .containsExactly(EbsHttpsTestFixture.PAYLOAD);
        assertReplaysReleased();
    }

    @Test
    void shouldAcceptProgressingDownload_beyondOneReadTimeout() throws Exception {
        EDTDelegate client = client(EDTDelegate.class);
        client.download(RESOURCE_IDS);
        ((HTTPConduit) ClientProxy.getClient(client).getConduit()).getClient().setReceiveTimeout(1000);
        peer.mode = EbsHttpsTestFixture.Mode.PROGRESSING_BODY;
        long started = System.nanoTime();
        assertThat(client.download(RESOURCE_IDS).getData().getFirst().getContent())
                .containsExactly(EbsHttpsTestFixture.PAYLOAD);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isGreaterThan(1000);
        peer.assertHealthy();
        assertReplaysReleased();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TLSv1.2", "TLSv1.3"})
    void shouldNegotiateSupportedTls_withExplicitProtocol(String protocol) throws Exception {
        HCValidation client = client(HCValidation.class);
        ((HTTPConduit) ClientProxy.getClient(client).getConduit()).getTlsClientParameters()
                .setSecureSocketProtocol(protocol);
        assertThat(client.validate(hcvRequest, "en").getAuditUID()).isEqualTo(EbsHttpsTestFixture.AUDIT);
        assertThat(peer.negotiatedProtocol).isEqualTo(protocol);
        peer.assertHealthy();
    }

    @Test
    void shouldVerifyServerIdentity_withScopedTransport() throws Exception {
        HCValidation client = client(HCValidation.class);
        HTTPConduit conduit = (HTTPConduit) ClientProxy.getClient(client).getConduit();
        conduit.getTlsClientParameters().setTrustManagers(null);
        assertThatThrownBy(() -> client.validate(hcvRequest, "en")).isInstanceOf(WebServiceException.class);
        conduit.getTlsClientParameters().setTrustManagers(keys.trust());
        ((BindingProvider) client).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                peer.address().replace("localhost", "127.0.0.1") + "/ebs");
        assertThatThrownBy(() -> client.validate(hcvRequest, "en")).isInstanceOf(WebServiceException.class);
        assertThat(peer.requests).isEmpty();
        ((BindingProvider) client).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                peer.address() + "/ebs");
        assertThat(client.validate(hcvRequest, "en").getAuditUID()).isEqualTo(EbsHttpsTestFixture.AUDIT);
        peer.assertHealthy();
    }

    @Test
    void shouldRequireClientCertificate_withMutualTls() throws Exception {
        peer.close();
        peer = new EbsHttpsTestFixture(keys, true);
        HCValidation client = client(HCValidation.class);
        assertThatThrownBy(() -> client.validate(hcvRequest, "en")).isInstanceOf(WebServiceException.class);
        assertThat(peer.requests).isEmpty();
        ((HTTPConduit) ClientProxy.getClient(client).getConduit()).getTlsClientParameters()
                .setKeyManagers(keys.clientIdentity());
        assertThat(client.validate(hcvRequest, "en").getAuditUID()).isEqualTo(EbsHttpsTestFixture.AUDIT);
        peer.assertHealthy();
    }

    @Test
    void shouldKeepOtherClientsOnDefaultTransport_withSharedBus() throws Exception {
        HCValidation ebs = client(HCValidation.class);
        JAXRSClientFactoryBean factory = new JAXRSClientFactoryBean();
        factory.setBus(bus);
        factory.setAddress(peer.address() + "/control");
        WebClient control = factory.createWebClient();
        AtomicBoolean usedUrlConnection = new AtomicBoolean();
        WebClient.getConfig(control).getOutInterceptors().add(backendProbe(usedUrlConnection));
        HTTPConduit conduit = WebClient.getConfig(control).getHttpConduit();
        assertThat(conduit).isExactlyInstanceOf(HttpClientHTTPConduit.class);
        var tls = new org.apache.cxf.configuration.jsse.TLSClientParameters();
        tls.setTrustManagers(keys.trust());
        conduit.setTlsClientParameters(tls);
        conduit.getClient().setReceiveTimeout(3000);
        try {
            assertThat(control.get(String.class)).isEqualTo("control-ok");
            assertThat(usedUrlConnection).isFalse();
            assertThat(ebs.validate(hcvRequest, "en").getAuditUID()).isEqualTo(EbsHttpsTestFixture.AUDIT);
            assertThat(control.get(String.class)).isEqualTo("control-ok");
            assertThat(usedUrlConnection).isFalse();
            assertThat(WebClient.getConfig(control).getRequestContext())
                    .doesNotContainKeys("use.async.http.conduit", "force.urlconnection.http.conduit");
            peer.assertHealthy();
        } finally { control.close(); }
    }

    @Test
    void shouldExchangeConcurrentDownloads_withIndependentClients() throws Exception {
        List<EDTDelegate> ports = new ArrayList<>();
        for (int i = 0; i < 4; i++) ports.add(client(EDTDelegate.class));
        List<Callable<Boolean>> work = new ArrayList<>();
        for (EDTDelegate port : ports) work.add(() -> {
            for (int i = 0; i < 2; i++) {
                assertThat(port.download(RESOURCE_IDS).getData().getFirst().getContent())
                        .containsExactly(EbsHttpsTestFixture.PAYLOAD);
                assertThat(AttachmentCachingInterceptor.getAttachments()).isNull();
            }
            return true;
        });
        try (var workers = Executors.newFixedThreadPool(4)) {
            for (var result : workers.invokeAll(work)) assertThat(result.get()).isTrue();
        }
        assertThat(peer.requests).hasSize(8);
        peer.assertHealthy();
        assertReplaysReleased();
    }

    private void assertReplaysReleased() {
        assertThat(AttachmentCachingInterceptor.getAttachments()).as("attachment ThreadLocal").isNull();
        assertThat(replays).isNotEmpty();
        for (InputStream replay : replays) {
            assertThatThrownBy(replay::read).as("file-backed entity replay is closed").isInstanceOf(IOException.class);
        }
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) return true;
        }
        return false;
    }

    private static AbstractPhaseInterceptor<Message> backendProbe(AtomicBoolean usedUrlConnection) {
        return new AbstractPhaseInterceptor<>(Phase.PRE_STREAM) {
            @Override public void handleMessage(Message message) {
                usedUrlConnection.set(Boolean.TRUE.equals(message.get("USING_URLCONNECTION")));
            }
        };
    }

    private <T> T client(Class<T> contract) {
        EdtClientBuilderConfig config = new EdtClientBuilderConfig();
        config.setServiceUrl(peer.address() + "/ebs");
        config.setConformanceKey("fixture-conformance");
        config.setAuditId("fixture-audit");
        config.setServiceId("fixture-service");
        config.setKeystoreUser("client");
        config.setKeystorePassword(EbsHttpsTestFixture.PASSWORD);
        config.setUserNameTokenUser("synthetic-user");
        config.setUserNameTokenPassword(EbsHttpsTestFixture.TOKEN_PASSWORD);
        config.setMtomEnabled(contract == EDTDelegate.class);
        EdtClientBuilder builder = new EdtClientBuilder(config);
        builder.setClientKeystoreFilename(keys.clientProperties().toUri().toString());
        T port = builder.build(contract);
        Client cxf = ClientProxy.getClient(port);
        clientProxies.add((ClientProxy) java.lang.reflect.Proxy.getInvocationHandler(port));
        cxf.getInFaultInterceptors().add(new AbstractPhaseInterceptor<Message>(Phase.RECEIVE) {
            @Override public void handleMessage(Message message) { faultChains.incrementAndGet(); }
        });
        cxf.getInInterceptors().add(new AbstractPhaseInterceptor<Message>(Phase.RECEIVE) {
            {
                addAfter(DynamicWSS4JInInterceptor.class.getName());
                addBefore(org.apache.cxf.interceptor.AttachmentInInterceptor.class.getName());
            }
            @Override public void handleMessage(Message message) {
                replays.add(message.getContent(InputStream.class));
            }
        });
        cxf.getOutInterceptors().add(new AbstractPhaseInterceptor<Message>(Phase.PRE_STREAM) {
            @Override public void handleMessage(Message message) {
                assertThat(message).containsEntry("USING_URLCONNECTION", Boolean.TRUE)
                        .doesNotContainEntry("use.async.http.conduit", Boolean.TRUE);
            }
        });
        // Trust only the generated peer certificate; keep hostname verification and the builder's TLS policy.
        HTTPConduit conduit = (HTTPConduit) cxf.getConduit();
        conduit.getTlsClientParameters().setTrustManagers(keys.trust());
        conduit.getClient().setConnectionTimeout(3000);
        conduit.getClient().setReceiveTimeout(3000);
        BindingProvider binding = (BindingProvider) port;
        binding.getRequestContext().put("attachment-directory", temporaryDirectory.toString());
        binding.getRequestContext().put("attachment-memory-threshold", 128);
        return port;
    }
}
