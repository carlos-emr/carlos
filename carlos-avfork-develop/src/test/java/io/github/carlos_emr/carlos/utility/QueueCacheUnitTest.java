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
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link QueueCache} lifecycle behavior.
 */
@Tag("unit")
@DisplayName("QueueCache")
class QueueCacheUnitTest extends CarlosUnitTestBase {

    @BeforeEach
    void setUp() {
        QueueCache.resetSharedTimerForTesting();
    }

    @AfterEach
    void tearDown() {
        QueueCache.resetSharedTimerForTesting();
    }

    @Test
    @Tag("unit")
    @Tag("delete")
    void shouldCancelSharedTimer_whenShutdownInvoked() {
        new QueueCache<String, String>(2, 10, 1_000L, null);

        assertThat(QueueCache.isSharedTimerInitialized()).isTrue();

        QueueCache.shutdownSharedTimer();

        assertThat(QueueCache.isSharedTimerInitialized()).isFalse();
    }

    @Test
    @Tag("unit")
    @Tag("create")
    void shouldSkipSharedTimerScheduling_afterShutdown() {
        QueueCache.shutdownSharedTimer();

        new QueueCache<String, String>(2, 10, 1_000L, null);

        assertThat(QueueCache.isSharedTimerInitialized()).isFalse();
    }

    @Test
    @Tag("unit")
    @Tag("delete")
    void shouldBeIdempotent_whenShutdownInvokedTwice() {
        new QueueCache<String, String>(2, 10, 1_000L, null);

        QueueCache.shutdownSharedTimer();
        QueueCache.shutdownSharedTimer();

        assertThat(QueueCache.isSharedTimerInitialized()).isFalse();
    }

    @Test
    @Tag("unit")
    @Tag("update")
    void shouldClampShiftPeriodToOne_whenMaxTimeToCacheIsLessThanPools() {
        new QueueCache<String, String>(5, 10, 1L, null);

        assertThat(QueueCache.isSharedTimerInitialized()).isTrue();
    }

    @Test
    @Tag("unit")
    @Tag("create")
    void shouldRejectInvalidPoolCount_whenConstructingCache() {
        assertThatThrownBy(() -> new QueueCache<String, String>(0, 10, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pools must be greater than 0");
    }

}
