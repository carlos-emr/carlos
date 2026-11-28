/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.activation.DataHandler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.cxf.attachment.AttachmentDataSource;
import org.apache.cxf.attachment.AttachmentDeserializer;
import org.apache.cxf.attachment.AttachmentImpl;
import org.apache.cxf.attachment.LazyAttachmentCollection;
import org.apache.cxf.message.MessageImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("unit")
@Tag("integration-ebs")
class AttachmentCleanupInterceptorUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldReleaseLoadedCache_withoutReadingFurtherAttachments(@TempDir Path directory) throws Exception {
        var message = new MessageImpl();
        message.put(AttachmentDeserializer.ATTACHMENT_DIRECTORY, directory.toFile());
        message.put(AttachmentDeserializer.ATTACHMENT_MEMORY_THRESHOLD, 128);
        var source = new AttachmentDataSource("application/octet-stream", new ByteArrayInputStream(new byte[512]));
        source.cache(message);
        try (var files = Files.list(directory)) {
            assertThat(files).hasSize(1);
        }
        var deserializer = mock(AttachmentDeserializer.class);
        var attachments = new LazyAttachmentCollection(deserializer, 4);
        attachments.add(new AttachmentImpl("synthetic", new DataHandler(source)));
        message.setAttachments(attachments);

        new AttachmentCleanupInterceptor().handleMessage(message);

        assertThat(directory).isEmptyDirectory();
        verifyNoInteractions(deserializer);
    }

    @Test
    void shouldCloseOwnedReplayAndClearAttachments_afterNormalAndFaultProcessing() throws Exception {
        for (boolean fault : new boolean[]{false, true}) {
            var message = new MessageImpl();
            message.setAttachments(List.of(org.mockito.Mockito.mock(org.apache.cxf.message.Attachment.class)));
            new AttachmentCachingInterceptor().handleMessage(message);
            var closed = new java.util.concurrent.atomic.AtomicInteger();
            var replay = new ByteArrayInputStream(new byte[]{1}) {
                @Override public void close() throws IOException { closed.incrementAndGet(); super.close(); }
            };
            String key = DynamicWSS4JInInterceptor.class.getName() + ".replay";
            message.put(key, replay);
            var interceptor = new AttachmentCleanupInterceptor();
            if (fault) {
                @SuppressWarnings("unchecked")
                var guard = (org.apache.cxf.phase.PhaseInterceptor<org.apache.cxf.message.Message>)
                        interceptor.getAdditionalInterceptors().iterator().next();
                guard.handleFault(message);
            }
            else interceptor.handleMessage(message);
            assertThat(closed.get()).isEqualTo(1);
            assertThat(message.containsKey(key)).isFalse();
            assertThat(AttachmentCachingInterceptor.getAttachments()).isNull();
            interceptor.handleMessage(message);
            assertThat(closed.get()).isEqualTo(1);
        }
    }
    @Test
    void shouldPreservePrimaryFaultAndClearThreadLocal_whenReplayCloseAlsoFails() {
        var message = new MessageImpl();
        var primary = new org.apache.cxf.interceptor.Fault(new IOException("synthetic primary"));
        message.setContent(Exception.class, primary);
        message.setAttachments(List.of(org.mockito.Mockito.mock(org.apache.cxf.message.Attachment.class)));
        new AttachmentCachingInterceptor().handleMessage(message);
        var closes = new java.util.concurrent.atomic.AtomicInteger();
        String key = DynamicWSS4JInInterceptor.class.getName() + ".replay";
        message.put(key, new ByteArrayInputStream(new byte[]{1}) {
            @Override public void close() throws IOException {
                closes.incrementAndGet(); throw new IOException("synthetic cleanup");
            }
        });
        var interceptor = new AttachmentCleanupInterceptor();
        @SuppressWarnings("unchecked")
        var guard = (org.apache.cxf.phase.PhaseInterceptor<org.apache.cxf.message.Message>)
                interceptor.getAdditionalInterceptors().iterator().next();
        guard.handleFault(message);
        assertThat(message.getContent(Exception.class)).isSameAs(primary);
        assertThat(AttachmentCachingInterceptor.getAttachments()).isNull();
        assertThat(message.containsKey(key)).isFalse();
        interceptor.handleMessage(message);
        assertThat(closes.get()).isEqualTo(1);
    }

}
