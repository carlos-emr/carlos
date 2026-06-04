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

import java.util.Arrays;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link QueueCache} cache and lifecycle behavior.
 */
@Tag("unit")
@Tag("fast")
@Tag("utility")
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
    @DisplayName("should store and retrieve value")
    void shouldStoreAndRetrieve_value() {
        QueueCache<String, String> cache = new QueueCache<>(2, 100, null);
        cache.put("key1", "value1");
        assertThat(cache.get("key1")).isEqualTo("value1");
    }

    @Test
    @DisplayName("should return null for missing key")
    void shouldReturnNull_forMissingKey() {
        QueueCache<String, String> cache = new QueueCache<>(2, 100, null);
        assertThat(cache.get("nonexistent")).isNull();
    }

    @Test
    @DisplayName("should store multiple entries")
    void shouldStoreMultipleEntries() {
        QueueCache<String, String> cache = new QueueCache<>(2, 100, null);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.put("c", "3");
        assertThat(cache.get("a")).isEqualTo("1");
        assertThat(cache.get("b")).isEqualTo("2");
        assertThat(cache.get("c")).isEqualTo("3");
    }

    @Test
    @DisplayName("should report pool sizes")
    void shouldReportPoolSizes() {
        QueueCache<String, String> cache = new QueueCache<>(3, 100, null);
        cache.put("key", "val");
        int[] sizes = cache.getPoolSizes();
        assertThat(sizes).hasSize(3);
        assertThat(Arrays.stream(sizes).sum()).isEqualTo(1);
    }

    @Test
    @DisplayName("should clone values when cloner provided")
    void shouldCloneValues_whenClonerProvided() {
        QueueCacheValueCloner<StringBuilder> cloner = StringBuilder::new;
        QueueCache<String, StringBuilder> cache = new QueueCache<>(2, 100, cloner);
        StringBuilder original = new StringBuilder("value");

        cache.put("key", original);

        StringBuilder retrieved = cache.get("key");
        assertThat(retrieved).hasToString("value");
        assertThat(retrieved).isNotSameAs(original);
    }

    @Test
    @DisplayName("should overwrite existing key")
    void shouldOverwriteExistingKey() {
        QueueCache<String, String> cache = new QueueCache<>(2, 100, null);
        cache.put("key", "old");
        cache.put("key", "new");
        assertThat(cache.get("key")).isEqualTo("new");
    }

    @Test
    @Tag("delete")
    @DisplayName("should cancel shared timer when shutdown invoked")
    void shouldCancelSharedTimer_whenShutdownInvoked() {
        new QueueCache<String, String>(2, 10, 1_000L, null);

        assertThat(QueueCache.isSharedTimerInitialized()).isTrue();

        QueueCache.shutdownSharedTimer();

        assertThat(QueueCache.isSharedTimerInitialized()).isFalse();
    }

    @Test
    @Tag("create")
    @DisplayName("should skip shared timer scheduling after shutdown")
    void shouldSkipSharedTimerScheduling_afterShutdown() {
        QueueCache.shutdownSharedTimer();

        new QueueCache<String, String>(2, 10, 1_000L, null);

        assertThat(QueueCache.isSharedTimerInitialized()).isFalse();
    }

    @Test
    @Tag("delete")
    @DisplayName("should be idempotent when shutdown invoked twice")
    void shouldBeIdempotent_whenShutdownInvokedTwice() {
        new QueueCache<String, String>(2, 10, 1_000L, null);

        QueueCache.shutdownSharedTimer();
        QueueCache.shutdownSharedTimer();

        assertThat(QueueCache.isSharedTimerInitialized()).isFalse();
    }

    @Test
    @Tag("update")
    @DisplayName("should clamp shift period to one when max cache time is less than pools")
    void shouldClampShiftPeriodToOne_whenMaxTimeToCacheIsLessThanPools() {
        new QueueCache<String, String>(5, 10, 1L, null);

        assertThat(QueueCache.isSharedTimerInitialized()).isTrue();
    }

    @Test
    @Tag("create")
    @DisplayName("should reject invalid pool count when constructing cache")
    void shouldRejectInvalidPoolCount_whenConstructingCache() {
        assertThatThrownBy(() -> new QueueCache<String, String>(0, 10, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pools must be greater than 0");
    }
}
