/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.activation.DataHandler;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.ext.logging.LoggingInInterceptor;
import org.apache.cxf.ext.logging.LoggingOutInterceptor;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.message.Attachment;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("integration-ebs")
class DownloadInInterceptorUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldKeepDiagnosticsOutOfDefaultTransport_whenConfigDoesNotOptIn() {
        var config = new EdtClientBuilderConfig();
        assertThat(config.isLoggingRequired()).isFalse();
        var configured = configure(config);
        assertThat(configured.inbound()).noneMatch(DownloadInInterceptor.class::isInstance)
                .noneMatch(LoggingInInterceptor.class::isInstance)
                .anyMatch(AttachmentCleanupInterceptor.class::isInstance);
        assertThat(configured.outbound()).noneMatch(DownloadInInterceptor.class::isInstance)
                .noneMatch(LoggingOutInterceptor.class::isInstance);
        assertThat(configured.fault()).anyMatch(AttachmentCleanupInterceptor.class::isInstance);
    }

    @Test
    @ResourceLock("SYSTEM_OUT")
    void shouldKeepPayloadAndCredentialsPrivate_whenDiagnosticsAreEnabled() throws Exception {
        var config = new EdtClientBuilderConfig();
        config.setLoggingRequired(true);
        config.setUserNameTokenUser("FAKE_EBS_USERNAME");
        config.setUserNameTokenPassword("FAKE_EBS_PASSWORD");
        config.setKeystorePassword("FAKE_EBS_KEYSTORE_PASSWORD");
        var configured = configure(config);
        assertThat(configured.inbound()).noneMatch(LoggingInInterceptor.class::isInstance);
        assertThat(configured.outbound()).noneMatch(LoggingOutInterceptor.class::isInstance);
        var diagnostics = java.util.stream.Stream.concat(configured.inbound().stream(), configured.outbound().stream())
                .filter(DownloadInInterceptor.class::isInstance).map(DownloadInInterceptor.class::cast).toList();
        assertThat(diagnostics).hasSize(2);

        String payload = "<soap><password>FAKE_EBS_PASSWORD</password><patient>FAKE_EBS_PATIENT</patient></soap>";
        var body = spy(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
        var attachment = mock(Attachment.class);
        var data = mock(DataHandler.class);
        when(attachment.getId()).thenReturn("FAKE_EBS_ATTACHMENT_ID");
        when(attachment.getDataHandler()).thenReturn(data);
        when(data.getInputStream()).thenReturn(new ByteArrayInputStream("FAKE_EBS_ATTACHMENT_BODY".getBytes(StandardCharsets.UTF_8)));
        var message = new MessageImpl();
        message.setContent(InputStream.class, body);
        message.put(Message.ATTACHMENTS, List.of(attachment));
        message.put(Message.RESPONSE_CODE, 200);
        message.put(Message.PROTOCOL_HEADERS, java.util.Map.of("Authorization", List.of("FAKE_EBS_PASSWORD")));
        var console = new ByteArrayOutputStream();
        var original = System.out;
        try (var output = new PrintStream(console, true, StandardCharsets.UTF_8);
             var logs = LogCapture.forLogger(DownloadInInterceptor.class)) {
            System.setOut(output);
            try {
                for (var diagnostic : diagnostics) diagnostic.handleMessage(message);
            } finally {
                System.setOut(original);
            }
            assertThat(logs.messages()).containsExactly("EBS response processed: httpStatus=200", "EBS request prepared");
            assertThat(logs.messages().toString()).doesNotContain("FAKE_EBS_");
            assertThat(logs.events()).allMatch(event -> event.getThrown() == null);
        }
        assertThat(console.toString(StandardCharsets.UTF_8)).doesNotContain("FAKE_EBS_");
        assertThat(message.getContent(InputStream.class)).isSameAs(body);
        verifyNoInteractions(body, attachment, data);
        assertThat(new String(body.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(payload);
    }

    @Test
    void shouldLeaveTransportFailuresToConsumer_whenDiagnosticsSeeAnUnreadableStream() throws Exception {
        var message = new MessageImpl();
        InputStream body = mock(InputStream.class);
        when(body.read()).thenThrow(new IOException("FAKE_EBS_PRIVATE_READ_ERROR"));
        message.setContent(InputStream.class, body);
        new DownloadInInterceptor().handleMessage(message);
        assertThat(message.getContent(InputStream.class)).isSameAs(body);
        verifyNoInteractions(body);
        assertThatThrownBy(body::read).isInstanceOf(IOException.class);
    }

    @Test
    void shouldAvoidMaterializingAttachments_whenDiagnosticsSeeALazyCollection() {
        var message = new MessageImpl();
        Collection<?> attachments = mock(Collection.class);
        message.put(Message.ATTACHMENTS, attachments);
        new DownloadInInterceptor().handleMessage(message);
        verifyNoInteractions(attachments);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 99, 600, Integer.MAX_VALUE})
    void shouldOmitInvalidStatusValues_whenTransportMetadataIsOutOfRange(int status) {
        var message = new MessageImpl();
        message.put(Message.RESPONSE_CODE, status);
        try (var logs = LogCapture.forLogger(DownloadInInterceptor.class)) {
            new DownloadInInterceptor().handleMessage(message);
            assertThat(logs.messages()).containsExactly("EBS response processed: httpStatus=-1");
        }
    }

    @Test
    void shouldAvoidArbitraryStatusFormatting_whenMetadataContainsSensitiveText() {
        var message = new MessageImpl();
        Object sensitive = mock(Object.class);
        when(sensitive.toString()).thenReturn("FAKE_EBS_SENSITIVE_STATUS");
        message.put(Message.RESPONSE_CODE, sensitive);
        try (var logs = LogCapture.forLogger(DownloadInInterceptor.class)) {
            new DownloadInInterceptor().handleMessage(message);
            assertThat(logs.messages()).containsExactly("EBS response processed: httpStatus=-1");
        }
        verifyNoInteractions(sensitive);
    }

    private record Configured(List<Interceptor<? extends Message>> inbound,
                              List<Interceptor<? extends Message>> outbound,
                              List<Interceptor<? extends Message>> fault) { }

    private static Configured configure(EdtClientBuilderConfig config) {
        var client = mock(Client.class);
        var endpoint = mock(Endpoint.class);
        var configured = new Configured(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        when(client.getEndpoint()).thenReturn(endpoint);
        when(endpoint.getInInterceptors()).thenReturn(configured.inbound());
        when(endpoint.getOutInterceptors()).thenReturn(configured.outbound());
        when(endpoint.getInFaultInterceptors()).thenReturn(configured.fault());
        var builder = mock(EdtClientBuilder.class, CALLS_REAL_METHODS);
        builder.setConfig(config);
        builder.configureInInterceptor(client);
        builder.configureOutInterceptor(client);
        return configured;
    }
}
