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
package io.github.carlos_emr.carlos.drools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RuleBaseFactory}, the application-wide cache for compiled
 * Drools {@link KieBase} instances.
 *
 * <p>{@code RuleBaseFactory} uses a {@code ReadWriteLock}-protected
 * {@code QueueCache<String, KieBase>} to store compiled rule bases keyed by a
 * cache identifier (typically a SHA-256 digest of the DRL content or a logical
 * rule set name). This avoids expensive re-compilation of identical DRL on every
 * request to the decision support, flowsheet, prevention, and workflow subsystems.</p>
 *
 * <p>Tests are organized into four nested classes:</p>
 * <ul>
 *   <li>{@link PutAndGet} &mdash; basic put/get round-trip, overwrite, and isolation</li>
 *   <li>{@link Remove} &mdash; single-key removal and non-interference with other entries</li>
 *   <li>{@link FlushAll} &mdash; bulk cache invalidation</li>
 *   <li>{@link NullValidation} &mdash; null-safety of the public API</li>
 * </ul>
 *
 * <p>Each test starts with a clean cache ({@link RuleBaseFactory#flushAllCached()} in
 * {@code @BeforeEach}) to eliminate cross-test interference from the static cache.</p>
 *
 * @see RuleBaseFactory
 * @see DroolsHelper#createKieBaseFromDrl(String)
 * @since 2026-02-17
 */
@Tag("unit")
@Tag("drools")
@DisplayName("RuleBaseFactory")
class RuleBaseFactoryUnitTest {

    /**
     * Minimal valid DRL used to create real {@link KieBase} instances for the cache.
     * We use real compiled KieBase objects (not mocks) because the cache should work
     * with the actual Drools types that production code stores.
     */
    private static final String MINIMAL_VALID_DRL =
            "package test;\n" +
            "import java.util.concurrent.atomic.AtomicBoolean;\n" +
            "rule \"test-rule\"\n" +
            "    when $b : AtomicBoolean() then $b.set(true);\n" +
            "end\n";

    /**
     * Flush the entire static cache before each test to ensure complete isolation.
     * Without this, a KieBase stored by one test would be visible to subsequent tests,
     * making assertions about cache misses unreliable.
     */
    @BeforeEach
    void setUp() {
        RuleBaseFactory.flushAllCached();
    }

    /**
     * Helper that compiles a fresh {@link KieBase} from the minimal valid DRL.
     * Each invocation produces a distinct KieBase instance, which allows tests to
     * verify identity-based cache assertions (e.g., {@code isSameAs} vs.
     * {@code isNotSameAs}).
     *
     * @return a newly compiled {@link KieBase} instance
     * @throws RuntimeException if DRL compilation fails (should never happen with valid DRL)
     */
    private static KieBase createTestKieBase() {
        try {
            return DroolsHelper.createKieBaseFromDrl(MINIMAL_VALID_DRL);
        } catch (DroolsCompilationException e) {
            throw new RuntimeException("Failed to create test KieBase", e);
        }
    }

    /**
     * Tests for the basic put/get round-trip through the cache.
     */
    @Nested
    @DisplayName("putAndGet")
    class PutAndGet {

        /**
         * After storing a KieBase under a key, retrieving that key should return
         * the exact same instance (identity check, not equality).
         */
        @Test
        @DisplayName("should return cached KieBase when key exists")
        void shouldReturnCachedKieBase_whenKeyExists() {
            KieBase kieBase = createTestKieBase();

            // Store the compiled KieBase in the cache
            RuleBaseFactory.putRuleBase("test-key", kieBase);
            KieBase retrieved = RuleBaseFactory.getRuleBase("test-key");

            // Must be the exact same instance, not a copy or re-compilation
            assertThat(retrieved).isSameAs(kieBase);
        }

        /**
         * A cache miss (key never stored) should return null, not throw.
         * Callers rely on null to decide whether to compile and cache a new KieBase.
         */
        @Test
        @DisplayName("should return null when key not cached")
        void shouldReturnNull_whenKeyNotCached() {
            KieBase retrieved = RuleBaseFactory.getRuleBase("nonexistent-key");

            assertThat(retrieved).isNull();
        }

        /**
         * Storing a second KieBase under the same key should silently overwrite
         * the first entry. The cache does not throw or reject duplicate keys.
         */
        @Test
        @DisplayName("should overwrite existing entry when same key put again")
        void shouldOverwriteExistingEntry_whenSameKeyPutAgain() {
            KieBase first = createTestKieBase();
            KieBase second = createTestKieBase();

            // Store first, then overwrite with second
            RuleBaseFactory.putRuleBase("same-key", first);
            RuleBaseFactory.putRuleBase("same-key", second);
            KieBase retrieved = RuleBaseFactory.getRuleBase("same-key");

            // The second KieBase should have replaced the first
            assertThat(retrieved).isSameAs(second);
        }

        /**
         * Two different keys should store and retrieve their respective KieBase
         * instances independently, without cross-contamination.
         */
        @Test
        @DisplayName("should store different bases for different keys")
        void shouldStoreDifferentBases_forDifferentKeys() {
            KieBase baseA = createTestKieBase();
            KieBase baseB = createTestKieBase();

            RuleBaseFactory.putRuleBase("key-a", baseA);
            RuleBaseFactory.putRuleBase("key-b", baseB);

            // Each key should return its own distinct KieBase
            assertThat(RuleBaseFactory.getRuleBase("key-a")).isSameAs(baseA);
            assertThat(RuleBaseFactory.getRuleBase("key-b")).isSameAs(baseB);
        }
    }

    /**
     * Tests for single-key removal from the cache.
     */
    @Nested
    @DisplayName("remove")
    class Remove {

        /**
         * After removing a key, subsequent get calls for that key should return null.
         */
        @Test
        @DisplayName("should remove cached entry when key exists")
        void shouldRemoveCachedEntry_whenKeyExists() {
            KieBase kieBase = createTestKieBase();
            RuleBaseFactory.putRuleBase("to-remove", kieBase);

            RuleBaseFactory.removeRuleBase("to-remove");

            // The entry should no longer be in the cache
            assertThat(RuleBaseFactory.getRuleBase("to-remove")).isNull();
        }

        /**
         * Removing a key that was never cached should be a silent no-op.
         * The production code calls removeRuleBase defensively during cache
         * invalidation, so it must tolerate missing keys gracefully.
         */
        @Test
        @DisplayName("should not throw when removing non-existent key")
        void shouldNotThrow_whenRemovingNonExistentKey() {
            // Should complete without throwing any exception
            RuleBaseFactory.removeRuleBase("does-not-exist");
        }

        /**
         * Removing one key must not affect entries stored under other keys.
         * This verifies the cache's key-level isolation.
         */
        @Test
        @DisplayName("should not affect other entries when one removed")
        void shouldNotAffectOtherEntries_whenOneRemoved() {
            KieBase baseA = createTestKieBase();
            KieBase baseB = createTestKieBase();
            RuleBaseFactory.putRuleBase("keep", baseA);
            RuleBaseFactory.putRuleBase("remove", baseB);

            RuleBaseFactory.removeRuleBase("remove");

            // "keep" should still be present; "remove" should be gone
            assertThat(RuleBaseFactory.getRuleBase("keep")).isSameAs(baseA);
            assertThat(RuleBaseFactory.getRuleBase("remove")).isNull();
        }
    }

    /**
     * Tests for {@link RuleBaseFactory#flushAllCached()}, which clears every
     * entry from the cache regardless of key. Used during application reloads
     * or when DRL files are updated on disk.
     */
    @Nested
    @DisplayName("flushAll")
    class FlushAll {

        /**
         * After flushing, all previously stored entries should be gone.
         */
        @Test
        @DisplayName("should remove all entries when flushed")
        void shouldRemoveAllEntries_whenFlushed() {
            // Populate the cache with multiple entries
            RuleBaseFactory.putRuleBase("a", createTestKieBase());
            RuleBaseFactory.putRuleBase("b", createTestKieBase());
            RuleBaseFactory.putRuleBase("c", createTestKieBase());

            RuleBaseFactory.flushAllCached();

            // Every entry should now return null
            assertThat(RuleBaseFactory.getRuleBase("a")).isNull();
            assertThat(RuleBaseFactory.getRuleBase("b")).isNull();
            assertThat(RuleBaseFactory.getRuleBase("c")).isNull();
        }

        /**
         * The cache must remain functional after a flush. New entries stored after
         * the flush should be retrievable normally.
         */
        @Test
        @DisplayName("should accept new entries after flush")
        void shouldAcceptNewEntries_afterFlush() {
            RuleBaseFactory.putRuleBase("old", createTestKieBase());
            RuleBaseFactory.flushAllCached();

            // Store a new entry after the flush
            KieBase newBase = createTestKieBase();
            RuleBaseFactory.putRuleBase("new", newBase);

            assertThat(RuleBaseFactory.getRuleBase("new")).isSameAs(newBase);
        }
    }

    /**
     * Tests for null-safety of the public API methods.
     *
     * <p>The cache rejects null keys and null KieBase values via
     * {@link IllegalArgumentException}, but tolerates null keys in
     * {@code getRuleBase()} by returning null gracefully.</p>
     */
    @Nested
    @DisplayName("nullValidation")
    class NullValidation {

        /**
         * Storing with a null key is a programming error and should be rejected
         * immediately with an {@link IllegalArgumentException}.
         */
        @Test
        @DisplayName("should throw IllegalArgumentException when key is null")
        void shouldThrowIllegalArgumentException_whenKeyIsNull() {
            KieBase kieBase = createTestKieBase();

            assertThatThrownBy(() -> RuleBaseFactory.putRuleBase(null, kieBase))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("key");
        }

        /**
         * Storing a null KieBase value is a programming error (callers should
         * compile first, then cache) and should be rejected immediately.
         */
        @Test
        @DisplayName("should throw IllegalArgumentException when KieBase is null")
        void shouldThrowIllegalArgumentException_whenKieBaseIsNull() {
            assertThatThrownBy(() -> RuleBaseFactory.putRuleBase("key", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("KieBase");
        }

        /**
         * Getting with a null key should return null gracefully rather than
         * throwing. This allows callers to use null-safe patterns without
         * wrapping every get call in a null check.
         */
        @Test
        @DisplayName("should return null when get called with null")
        void shouldReturnNull_whenGetCalledWithNull() {
            KieBase result = RuleBaseFactory.getRuleBase(null);

            assertThat(result).isNull();
        }
    }
}
