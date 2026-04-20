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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for {@link CacheConfig} Spring wiring.
 *
 * <p>The original cache PR registered {@code @Cacheable} / {@code @CacheEvict}
 * annotations on six DAOs but did not wire {@link CacheConfig} into the Spring
 * context, so every annotation became a silent no-op. This test fails fast if
 * the {@link CacheManager} bean is missing or if any expected cache name drops
 * out of the catalogue.</p>
 *
 * @since 2026-04-20
 */
@Tag("integration")
@Tag("cache")
@DisplayName("CacheConfig Spring wiring")
class CacheConfigIntegrationTest extends CarlosTestBase {

    private static final String[] EXPECTED_CACHE_NAMES = {
            "providerNames",
            "activeProviders",
            "activeProviderSummaries",
            "appointmentStatuses",
            "measurementTypes",
            "lookupLists"
    };

    @Autowired
    private CacheManager cacheManager;

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
}
