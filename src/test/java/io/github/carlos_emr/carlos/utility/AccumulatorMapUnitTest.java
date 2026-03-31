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
 * Unit tests for {@link AccumulatorMap} counting data structure.
 *
 * @since 2026-03-31
 */
@DisplayName("AccumulatorMap Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("utility")
class AccumulatorMapUnitTest {

    private AccumulatorMap<String> map;

    @BeforeEach
    void setUp() {
        map = new AccumulatorMap<>();
    }

    @Nested
    @DisplayName("increment")
    class Increment {

        @Test
        @DisplayName("should start at 1 for new key")
        void shouldStartAtOne_forNewKey() {
            map.increment("a");
            assertThat(map.get("a")).isEqualTo(1);
        }

        @Test
        @DisplayName("should increment existing key by 1")
        void shouldIncrementExisting_byOne() {
            map.increment("a");
            map.increment("a");
            assertThat(map.get("a")).isEqualTo(2);
        }

        @Test
        @DisplayName("should increment by specified value")
        void shouldIncrement_bySpecifiedValue() {
            map.increment("a", 5);
            assertThat(map.get("a")).isEqualTo(5);
        }

        @Test
        @DisplayName("should accumulate increments by specified values")
        void shouldAccumulate_bySpecifiedValues() {
            map.increment("a", 3);
            map.increment("a", 7);
            assertThat(map.get("a")).isEqualTo(10);
        }

        @Test
        @DisplayName("should track multiple keys independently")
        void shouldTrackMultipleKeys_independently() {
            map.increment("a");
            map.increment("b");
            map.increment("a");
            assertThat(map.get("a")).isEqualTo(2);
            assertThat(map.get("b")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getTotalOfAllValues")
    class GetTotalOfAllValues {

        @Test
        @DisplayName("should return zero for empty map")
        void shouldReturnZero_forEmptyMap() {
            assertThat(map.getTotalOfAllValues()).isZero();
        }

        @Test
        @DisplayName("should return sum of all values")
        void shouldReturnSum_ofAllValues() {
            map.increment("a", 3);
            map.increment("b", 5);
            map.increment("c", 2);
            assertThat(map.getTotalOfAllValues()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("countInstancesOfValue")
    class CountInstancesOfValue {

        @Test
        @DisplayName("should count matching values")
        void shouldCountMatching() {
            map.increment("a", 5);
            map.increment("b", 5);
            map.increment("c", 3);
            assertThat(map.countInstancesOfValue(5)).isEqualTo(2);
        }

        @Test
        @DisplayName("should return zero when no match")
        void shouldReturnZero_whenNoMatch() {
            map.increment("a", 1);
            assertThat(map.countInstancesOfValue(99)).isZero();
        }
    }

    @Nested
    @DisplayName("TreeMap ordering")
    class TreeMapOrdering {

        @Test
        @DisplayName("should maintain sorted key order")
        void shouldMaintainSortedOrder() {
            map.increment("c");
            map.increment("a");
            map.increment("b");
            assertThat(map.firstKey()).isEqualTo("a");
            assertThat(map.lastKey()).isEqualTo("c");
        }
    }
}
