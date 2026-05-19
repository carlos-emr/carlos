/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link QueueCache} lifecycle behavior.
 *
 * @since 2026-05-19
 */
@Tag("unit")
@DisplayName("QueueCache")
class QueueCacheUnitTest {

    @AfterEach
    void tearDown() {
        QueueCache.shutdownSharedTimer();
    }

    @Test
    void shouldCancelSharedTimer_whenShutdownInvoked() throws Exception {
        new QueueCache<String, String>(2, 10, 1_000L, null);

        assertThat(sharedTimer()).isNotNull();

        QueueCache.shutdownSharedTimer();

        assertThat(sharedTimer()).isNull();
    }

    private Object sharedTimer() throws Exception {
        Field field = QueueCache.class.getDeclaredField("timer");
        field.setAccessible(true);
        return field.get(null);
    }
}
