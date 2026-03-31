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
 * Unit tests for {@link QueueCache} multi-pool LRU cache.
 *
 * @since 2026-03-31
 */
@DisplayName("QueueCache Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class QueueCacheUnitTest {

    @Test
    @DisplayName("should store and retrieve value")
    void shouldStoreAndRetrieve() {
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
        assertThat(sizes[0]).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("should clone values when cloner provided")
    void shouldCloneValues_whenClonerProvided() {
        QueueCacheValueCloner<String> cloner = original -> new String(original);
        QueueCache<String, String> cache = new QueueCache<>(2, 100, cloner);
        cache.put("key", "value");
        String retrieved = cache.get("key");
        assertThat(retrieved).isEqualTo("value");
    }

    @Test
    @DisplayName("should overwrite existing key")
    void shouldOverwriteExistingKey() {
        QueueCache<String, String> cache = new QueueCache<>(2, 100, null);
        cache.put("key", "old");
        cache.put("key", "new");
        assertThat(cache.get("key")).isEqualTo("new");
    }
}
