/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.interceptor.Interceptor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("integration-ebs")
class DownloadInInterceptorUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldKeepPayloadLoggingOutOfDefaultTransport_whenConfigDoesNotOptIn() {
        var config = new EdtClientBuilderConfig();
        assertThat(config.isLoggingRequired()).isFalse();
        var client = mock(Client.class);
        var endpoint = mock(Endpoint.class);
        var normal = new ArrayList<Interceptor<? extends Message>>();
        var fault = new ArrayList<Interceptor<? extends Message>>();
        when(client.getEndpoint()).thenReturn(endpoint);
        when(endpoint.getInInterceptors()).thenReturn(normal);
        when(endpoint.getInFaultInterceptors()).thenReturn(fault);
        var builder = mock(EdtClientBuilder.class, CALLS_REAL_METHODS);
        builder.setConfig(config);
        builder.configureInInterceptor(client);
        assertThat(normal).noneMatch(DownloadInInterceptor.class::isInstance)
                .anyMatch(AttachmentCleanupInterceptor.class::isInstance);
        assertThat(fault).anyMatch(AttachmentCleanupInterceptor.class::isInstance);
    }

    @Test
    void shouldPropagateStreamFailure_whenReplayCannotBeRead() {
        var message = new MessageImpl();
        message.setContent(InputStream.class, new InputStream() {
            @Override public int read() throws IOException { throw new IOException("synthetic read refusal"); }
        });
        var interceptor = new DownloadInInterceptor();
        assertThatThrownBy(() -> interceptor.handleMessage(message))
                .isInstanceOf(Fault.class).hasCauseInstanceOf(IOException.class);
    }
}
