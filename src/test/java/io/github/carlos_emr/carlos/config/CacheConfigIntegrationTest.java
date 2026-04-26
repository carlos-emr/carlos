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
package io.github.carlos_emr.carlos.config;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for {@link CacheConfig} Spring wiring.
 *
 * <p>The original cache PR registered {@code @Cacheable} / {@code @CacheEvict}
 * annotations on reference-data DAOs but did not wire {@link CacheConfig} into the Spring
 * context, so every annotation became a silent no-op. This test fails fast if
 * the {@link CacheManager} bean is missing or if any expected cache name drops
 * out of the catalogue.</p>
 *
 * @since 2026-04-20
 */
@Tag("integration")
@Tag("cache")
@DisplayName("CacheConfig Spring wiring")
// NOT_SUPPORTED overrides the @Transactional inherited from CarlosTestBase so the rollback-
// safety tests below can drive transaction boundaries explicitly through TransactionTemplate.
// TransactionAwareCacheManagerProxy only fires afterCommit hooks when a real transaction
// commits, so tests must manage their own commits rather than relying on the test-wide rollback.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CacheConfigIntegrationTest extends CarlosTestBase {

    private static final String[] EXPECTED_CACHE_NAMES = {
            "providerNames",
            "activeProviders",
            "activeProviderSummaries",
            "appointmentStatuses",
            "measurementTypes",
            "lookupLists",
            "appointmentTypes",
            "scheduleTemplateCodes",
            "facilities"
    };

    private static final String ROLLBACK_TEST_CACHE = "appointmentStatuses";
    private static final String SEED_KEY = "cacheConfigTest:seed";
    private static final String SEED_VALUE = "initialValue";
    private static final String ROLLBACK_KEY = "cacheConfigTest:rolledBackKey";
    private static final String COMMIT_KEY = "cacheConfigTest:committedKey";
    private static final String COMMIT_VALUE = "committedValue";

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate requiresNewTx;

    @AfterEach
    void clearRollbackSafetyTestKeys() {
        // Scrub every key the rollback-safety tests could have left behind. Keeps tests order-independent
        // when the whole class runs and guards against leaks into other integration tests via the shared cache.
        Cache cache = cacheManager.getCache(ROLLBACK_TEST_CACHE);
        if (cache != null) {
            cache.evict(SEED_KEY);
            cache.evict(ROLLBACK_KEY);
            cache.evict(COMMIT_KEY);
        }
    }

    private TransactionTemplate requiresNewTx() {
        if (requiresNewTx == null) {
            requiresNewTx = new TransactionTemplate(transactionManager);
            requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
        return requiresNewTx;
    }

    @Test
    @DisplayName("should register CacheManager bean when Spring context loads")
    void shouldRegisterCacheManager_whenSpringContextLoads() {
        assertThat(cacheManager)
                .as("CacheManager bean must be registered; missing bean means every @Cacheable/@CacheEvict is a silent no-op")
                .isNotNull();
    }

    @Test
    @DisplayName("should wrap delegate in TransactionAwareCacheManagerProxy for rollback safety")
    void shouldWrapDelegateInTransactionAwareProxy_forRollbackSafety() {
        assertThat(cacheManager)
                .as("CacheManager must be transaction-aware so cache writes defer to commit")
                .isInstanceOf(TransactionAwareCacheManagerProxy.class);
    }

    @Test
    @DisplayName("should expose all expected cache names when CacheConfig loads")
    void shouldExposeAllExpectedCacheNames_whenCacheConfigLoads() {
        assertThat(cacheManager.getCacheNames())
                .as("cache names must match the DAO @Cacheable(value=...) declarations; a typo here silently disables caching")
                .containsExactlyInAnyOrder(EXPECTED_CACHE_NAMES);
    }

    @Test
    @DisplayName("should back each cache with Caffeine when CacheConfig loads")
    void shouldBackEachCacheWithCaffeine_whenCacheConfigLoads() {
        for (String name : EXPECTED_CACHE_NAMES) {
            Cache cache = cacheManager.getCache(name);
            assertThat(cache)
                    .as("cache %s must be resolvable from the CacheManager", name)
                    .isNotNull();
            assertThat(cache.getNativeCache())
                    .as("cache %s must be backed by a Caffeine cache", name)
                    .isInstanceOf(com.github.benmanes.caffeine.cache.Cache.class);
        }
    }

    @Test
    @DisplayName("should discard deferred put and evict when transaction rolls back")
    void shouldDiscardDeferredPutAndEvict_whenTransactionRollsBack() {
        Cache cache = cacheManager.getCache(ROLLBACK_TEST_CACHE);
        assertThat(cache).isNotNull();

        // Seed the cache outside any transaction; put is applied immediately.
        cache.put(SEED_KEY, SEED_VALUE);
        assertThat(cache.get(SEED_KEY))
                .as("seed put outside a transaction must land in the cache immediately")
                .isNotNull();

        requiresNewTx().executeWithoutResult(status -> {
            cache.put(ROLLBACK_KEY, "shouldNotPersist");
            cache.evict(SEED_KEY);
            status.setRollbackOnly();
        });

        // TransactionAwareCacheManagerProxy defers put/evict until afterCommit; on rollback both are discarded.
        assertThat(cache.get(ROLLBACK_KEY))
                .as("put inside a rolled-back transaction must not be visible")
                .isNull();
        assertThat(cache.get(SEED_KEY))
                .as("evict inside a rolled-back transaction must not wipe the pre-transaction entry")
                .isNotNull();
        assertThat(cache.get(SEED_KEY).get()).isEqualTo(SEED_VALUE);
    }

    @Test
    @DisplayName("should apply deferred put when transaction commits")
    void shouldApplyDeferredPut_whenTransactionCommits() {
        Cache cache = cacheManager.getCache(ROLLBACK_TEST_CACHE);
        assertThat(cache).isNotNull();

        requiresNewTx().executeWithoutResult(status -> {
            cache.put(COMMIT_KEY, COMMIT_VALUE);
            // Spring's TransactionAwareCacheDecorator also exposes an intra-transaction view
            // via get(), but the authoritative assertion is post-commit below.
        });

        assertThat(cache.get(COMMIT_KEY))
                .as("put inside a committed transaction must be visible after commit")
                .isNotNull();
        assertThat(cache.get(COMMIT_KEY).get()).isEqualTo(COMMIT_VALUE);
    }
}
