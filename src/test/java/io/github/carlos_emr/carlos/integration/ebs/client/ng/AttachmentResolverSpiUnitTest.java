/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import io.github.carlos_emr.carlos.integration.mcedt.mailbox.CidPrefixResourceResolver;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import jakarta.activation.DataHandler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.MessageImpl;
import org.apache.xml.security.signature.XMLSignatureStreamInput;
import org.apache.xml.security.utils.resolver.ResourceResolverContext;
import org.apache.xml.security.utils.resolver.ResourceResolverException;
import org.apache.xml.security.utils.resolver.ResourceResolverSpi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("integration-ebs")
class AttachmentResolverSpiUnitTest extends CarlosUnitTestBase {
    private static final String ID = "urn:FAKE_EBS_ATTACHMENT_ID";
    private static final String URI = "cid:urn%3AFAKE_EBS_ATTACHMENT_ID";
    private static final String BASE_URI = "https://FAKE_EBS_BASE_URI.invalid/";

    @BeforeAll
    static void initializeXmlSecurityMessages() {
        org.apache.xml.security.Init.init();
    }

    @AfterEach
    void clearResponseAttachments() {
        AttachmentCachingInterceptor.clear();
    }

    static Stream<ResourceResolverSpi> resolvers() {
        return Stream.of(new AttachmentResolverSpi(), new CidPrefixResourceResolver());
    }

    static Stream<Arguments> contentIdUris() {
        return resolvers().flatMap(resolver -> Stream.of(
                Arguments.of(resolver, "urn:FAKE_EBS+ATTACHMENT_ID", "cid:urn%3AFAKE_EBS+ATTACHMENT_ID"),
                Arguments.of(resolver, "urn:FAKE_EBS+ATTACHMENT_ID", "cid:urn%3AFAKE_EBS%2BATTACHMENT_ID"),
                Arguments.of(resolver, "urn:FAKE_EBS ATTACHMENT_ID", "cid:urn%3AFAKE_EBS%20ATTACHMENT_ID")));
    }

    @ParameterizedTest
    @MethodSource("contentIdUris")
    void shouldPreserveContentIdCharacters_whenUriContainsLiteralPlusOrPercentEscapes(
            ResourceResolverSpi resolver, String id, String uri) throws Exception {
        String payload = "FAKE_CONTENT_ID_PAYLOAD";
        cache(attachment(id, bytes(payload)));
        assertThat(resolver.engineCanResolveURI(context(uri))).isTrue();
        try (var result = (XMLSignatureStreamInput) resolver.engineResolveURI(context(uri))) {
            assertThat(new String(result.getUnprocessedInput().readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo(payload);
        }
    }

    @ParameterizedTest
    @MethodSource("resolvers")
    void shouldResolveExactUnreadBytes_withoutLoggingAttachmentData(ResourceResolverSpi resolver) throws Exception {
        String payload = "FAKE_EBS_ATTACHMENT_BODY_AND_CREDENTIAL";
        var body = spy(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
        var attachment = attachment(ID, body);
        when(attachment.toString()).thenReturn("FAKE_EBS_ATTACHMENT_OBJECT");
        cache(attachment);

        try (var logs = LogCapture.forLogger(resolver.getClass());
             var result = (XMLSignatureStreamInput) resolver.engineResolveURI(context(URI))) {
            assertThat(result.getUnprocessedInput()).isSameAs(body);
            verifyNoInteractions(body);
            assertPrivate(logs);
            assertThat(new String(result.getUnprocessedInput().readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo(payload);
        }
    }

    @ParameterizedTest
    @MethodSource("resolvers")
    void shouldUseCurrentResponseAttachments_whenAResolverInstanceIsReused(ResourceResolverSpi resolver) throws Exception {
        cache(attachment(ID, bytes("FAKE_FIRST_RESPONSE")));
        assertThat(read(resolver)).isEqualTo("FAKE_FIRST_RESPONSE");
        AttachmentCachingInterceptor.clear();
        cache(attachment(ID, bytes("FAKE_SECOND_RESPONSE")));

        assertThat(read(resolver)).isEqualTo("FAKE_SECOND_RESPONSE");

        AttachmentCachingInterceptor.clear();
        assertThatExceptionOfType(ResourceResolverException.class)
                .isThrownBy(() -> resolver.engineResolveURI(context(URI)))
                .withMessage("EBS attachment could not be resolved");
    }

    @ParameterizedTest
    @MethodSource("resolvers")
    void shouldKeepResponsesThreadOwned_whenSharedResolverHandlesConcurrentMessages(ResourceResolverSpi resolver)
            throws Exception {
        // Prime the retained instance as a real earlier response would. An instance cache
        // otherwise passes a racy two-first-use test depending on which assignment wins.
        cache(attachment(ID, bytes("FAKE_EARLIER_RESPONSE")));
        assertThat(read(resolver)).isEqualTo("FAKE_EARLIER_RESPONSE");
        AttachmentCachingInterceptor.clear();
        var ready = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> readOnResponseThread(resolver, ready, "FAKE_THREAD_ONE"));
            var second = workers.submit(() -> readOnResponseThread(resolver, ready, "FAKE_THREAD_TWO"));
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("FAKE_THREAD_ONE");
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("FAKE_THREAD_TWO");
        }
    }

    @ParameterizedTest
    @MethodSource("resolvers")
    void shouldReportMissingAttachment_withoutIncludingContextInDiagnostics(ResourceResolverSpi resolver) {
        cache();
        try (var logs = LogCapture.forLogger(resolver.getClass())) {
            assertThatExceptionOfType(ResourceResolverException.class)
                    .isThrownBy(() -> resolver.engineResolveURI(context(URI)))
                    .withMessage("EBS attachment could not be resolved")
                    .satisfies(failure -> {
                        assertThat(failure.getURI()).isEqualTo(URI);
                        assertThat(failure.getbaseURI()).isEqualTo(BASE_URI);
                    });
            assertPrivate(logs);
        }
    }

    @ParameterizedTest
    @MethodSource("resolvers")
    void shouldPropagateResolverFailure_withoutLoggingPrivateProviderIOException(ResourceResolverSpi resolver) throws Exception {
        var attachment = mock(Attachment.class);
        var data = mock(DataHandler.class);
        when(attachment.getId()).thenReturn(ID);
        when(attachment.getDataHandler()).thenReturn(data);
        when(data.getInputStream()).thenThrow(new IOException("FAKE_EBS_PRIVATE_PROVIDER_ERROR"));
        cache(attachment);

        try (var logs = LogCapture.forLogger(resolver.getClass())) {
            assertThatExceptionOfType(ResourceResolverException.class)
                    .isThrownBy(() -> resolver.engineResolveURI(context(URI)))
                    .withMessage("EBS attachment signature input could not be opened")
                    .withNoCause();
            assertPrivate(logs);
        }
    }

    @ParameterizedTest
    @MethodSource("resolvers")
    void shouldRefuseMalformedUri_withoutExposingDecoderExceptionText(ResourceResolverSpi resolver) {
        try (var logs = LogCapture.forLogger(resolver.getClass())) {
            assertThatExceptionOfType(ResourceResolverException.class)
                    .isThrownBy(() -> resolver.engineResolveURI(context("cid:urn:FAKE_EBS_PRIVATE%ZZ_URI")))
                    .withMessage("EBS attachment URI could not be decoded")
                    .withNoCause();
            assertPrivate(logs);
        }
    }

    private static String readOnResponseThread(ResourceResolverSpi resolver, CyclicBarrier ready, String payload)
            throws Exception {
        try {
            cache(attachment(ID, bytes(payload)));
            ready.await(10, TimeUnit.SECONDS);
            return read(resolver);
        } finally {
            AttachmentCachingInterceptor.clear();
        }
    }

    private static String read(ResourceResolverSpi resolver) throws Exception {
        try (var result = (XMLSignatureStreamInput) resolver.engineResolveURI(context(URI))) {
            return new String(result.getUnprocessedInput().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void assertPrivate(LogCapture logs) {
        assertThat(logs.messages()).isNotEmpty().allSatisfy(message -> assertThat(message).doesNotContain("FAKE_"));
        assertThat(logs.events()).allMatch(event -> event.getThrown() == null);
    }

    private static Attachment attachment(String id, InputStream bytes) throws IOException {
        var attachment = mock(Attachment.class);
        var data = mock(DataHandler.class);
        when(attachment.getId()).thenReturn(id);
        when(attachment.getDataHandler()).thenReturn(data);
        when(data.getInputStream()).thenReturn(bytes);
        return attachment;
    }

    private static ByteArrayInputStream bytes(String payload) {
        return new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static void cache(Attachment... attachments) {
        var message = new MessageImpl();
        message.setAttachments(List.of(attachments));
        new AttachmentCachingInterceptor().handleMessage(message);
    }

    private static ResourceResolverContext context(String uri) throws Exception {
        var document = XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder().newDocument();
        var attribute = document.createAttribute("URI");
        attribute.setValue(uri);
        return new ResourceResolverContext(attribute, BASE_URI, true);
    }
}
