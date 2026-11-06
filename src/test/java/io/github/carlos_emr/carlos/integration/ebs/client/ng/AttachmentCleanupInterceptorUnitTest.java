/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.apache.cxf.message.MessageImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("integration-ebs")
class AttachmentCleanupInterceptorUnitTest extends CarlosUnitTestBase {
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
}
