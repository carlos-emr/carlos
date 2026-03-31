/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DeamonThreadFactory} daemon thread creation.
 *
 * @since 2026-03-31
 */
@DisplayName("DeamonThreadFactory Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class DeamonThreadFactoryUnitTest {

    @Test
    @DisplayName("should create daemon threads")
    void shouldCreateDaemonThreads() {
        DeamonThreadFactory factory = new DeamonThreadFactory();
        Thread thread = factory.newThread(() -> {});
        assertThat(thread.isDaemon()).isTrue();
    }

    @Test
    @DisplayName("should create non-null thread")
    void shouldCreateNonNullThread() {
        DeamonThreadFactory factory = new DeamonThreadFactory();
        Thread thread = factory.newThread(() -> {});
        assertThat(thread).isNotNull();
    }

    @Test
    @DisplayName("should implement ThreadFactory")
    void shouldImplementThreadFactory() {
        assertThat(new DeamonThreadFactory()).isInstanceOf(java.util.concurrent.ThreadFactory.class);
    }
}
