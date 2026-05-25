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
package io.github.carlos_emr.carlos.daos.security;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.model.security.SecProvider;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cache invalidation regression tests for {@link SecProviderDao}.
 *
 * <p>{@code SecProvider} and {@code Provider} map to the same {@code provider} table
 * (see CLAUDE.md "Dual Entity Mappings to Same Table"). Writes via {@link SecProviderDao}
 * must therefore evict the provider caches that provider DAO reads populate, otherwise
 * admin updates through the security UI would leave stale data in the provider caches.</p>
 */
@DisplayName("SecProviderDao cache invalidation")
@Tag("integration")
@Tag("dao")
@Tag("cache")
@Tag("security")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Isolated
class SecProviderDaoCacheIntegrationTest extends CarlosTestBase {

    @Autowired
    private SecProviderDao secProviderDao;

    @Autowired
    private ProviderDataDao providerDataDao;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<String> providerNosToCleanUp = new ArrayList<>();
    private TransactionTemplate transactionTemplate;
    private String uniquePrefix;

    @BeforeEach
    void setUpTransactionsAndCaches() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        String nanoStr = String.valueOf(System.nanoTime());
        uniquePrefix = nanoStr.substring(nanoStr.length() - 4);
        clearProviderCaches();
    }

    @AfterEach
    void cleanUpCommittedRows() {
        clearProviderCaches();
        for (String providerNo : providerNosToCleanUp) {
            transactionTemplate.executeWithoutResult(status -> {
                ProviderData provider = providerDataDao.findByProviderNo(providerNo);
                if (provider != null) {
                    providerDataDao.remove(provider);
                }
            });
        }
    }

    @Test
    @DisplayName("should evict provider caches when SecProviderDao save commits")
    void shouldEvictProviderCaches_whenSecProviderDaoSaveCommits() {
        seedProviderCaches();
        assertAllThreeCachesPopulated();

        String newProviderNo = uniquePrefix + "S1";
        providerNosToCleanUp.add(newProviderNo);
        transactionTemplate.executeWithoutResult(status -> secProviderDao.save(buildSecProvider(newProviderNo, "Saved", "User")));

        assertAllThreeCachesEvicted();
    }

    @Test
    @DisplayName("should evict provider caches when SecProviderDao saveOrUpdate commits")
    void shouldEvictProviderCaches_whenSecProviderDaoSaveOrUpdateCommits() {
        seedProviderCaches();
        assertAllThreeCachesPopulated();

        String newProviderNo = uniquePrefix + "S2";
        providerNosToCleanUp.add(newProviderNo);
        transactionTemplate.executeWithoutResult(status -> secProviderDao.saveOrUpdate(buildSecProvider(newProviderNo, "SaveOr", "Update")));

        assertAllThreeCachesEvicted();
    }

    @Test
    @DisplayName("should evict provider caches when SecProviderDao merge commits")
    void shouldEvictProviderCaches_whenSecProviderDaoMergeCommits() {
        String providerNo = uniquePrefix + "M1";
        providerNosToCleanUp.add(providerNo);
        SecProvider seeded = buildSecProvider(providerNo, "Merge", "Target");
        transactionTemplate.executeWithoutResult(status -> secProviderDao.save(seeded));

        seedProviderCaches();
        assertAllThreeCachesPopulated();

        transactionTemplate.executeWithoutResult(status -> {
            seeded.setLastName("Merged");
            secProviderDao.merge(seeded);
        });

        assertAllThreeCachesEvicted();
    }

    @Test
    @DisplayName("should evict provider caches when SecProviderDao delete commits")
    void shouldEvictProviderCaches_whenSecProviderDaoDeleteCommits() {
        String providerNo = uniquePrefix + "D1";
        providerNosToCleanUp.add(providerNo);
        SecProvider seeded = buildSecProvider(providerNo, "Delete", "Target");
        transactionTemplate.executeWithoutResult(status -> secProviderDao.save(seeded));

        seedProviderCaches();
        assertAllThreeCachesPopulated();

        transactionTemplate.executeWithoutResult(status -> {
            SecProvider attached = secProviderDao.findById(providerNo);
            secProviderDao.delete(attached);
            providerNosToCleanUp.remove(providerNo);
        });

        assertAllThreeCachesEvicted();
    }

    private void seedProviderCaches() {
        putCacheValue("providerNames", "name:" + uniquePrefix + "SE", "Cached Provider");
        putCacheValue("activeProviders", "filter:true", List.of("cached active provider"));
        putCacheValue("activeProviderSummaries", org.springframework.cache.interceptor.SimpleKey.EMPTY,
                List.of("cached active provider summary"));
    }

    private void assertAllThreeCachesPopulated() {
        assertThat(cacheEntryCount("providerNames")).isPositive();
        assertThat(cacheEntryCount("activeProviders")).isPositive();
        assertThat(cacheEntryCount("activeProviderSummaries")).isPositive();
    }

    private void assertAllThreeCachesEvicted() {
        assertThat(cacheEntryCount("providerNames")).isZero();
        assertThat(cacheEntryCount("activeProviders")).isZero();
        assertThat(cacheEntryCount("activeProviderSummaries")).isZero();
    }

    private SecProvider buildSecProvider(String providerNo, String firstName, String lastName) {
        SecProvider provider = new SecProvider();
        provider.setProviderNo(providerNo);
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        provider.setStatus("1");
        provider.setSex("F");
        provider.setProviderType("doctor");
        // Required NOT NULL from Provider.hbm.xml since Provider and SecProvider share the provider table.
        provider.setSpecialty("");
        return provider;
    }

    private void clearProviderCaches() {
        for (String cacheName : List.of("providerNames", "activeProviders", "activeProviderSummaries")) {
            org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
            assertThat(cache)
                    .as("cache %s should be present in the test context", cacheName)
                    .isNotNull();
            cache.clear();
        }
    }


    private void putCacheValue(String cacheName, Object key, Object value) {
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        assertThat(cache).isNotNull();
        cache.put(key, value);
    }

    @SuppressWarnings("unchecked")
    private long cacheEntryCount(String cacheName) {
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        assertThat(cache).isNotNull();
        return ((Cache<Object, Object>) cache.getNativeCache()).estimatedSize();
    }
}
