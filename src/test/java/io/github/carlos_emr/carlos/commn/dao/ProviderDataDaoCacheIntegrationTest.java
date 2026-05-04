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
package io.github.carlos_emr.carlos.commn.dao;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.provider.dto.ProviderSummaryDTO;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for provider cache invalidation when the shared {@code provider}
 * table is mutated through {@link ProviderDataDao}.
 *
 * <p>PR 1883 added Spring/Caffeine caching for {@link ProviderDao} reads, but
 * legacy write paths still reach the same table through {@link ProviderDataDao}.
 * These tests verify that those writes evict the provider caches after commit.</p>
 */
@DisplayName("ProviderDataDao cache invalidation")
@Tag("integration")
@Tag("dao")
@Tag("cache")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProviderDataDaoCacheIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProviderDao providerDao;

    @Autowired
    private ProviderDataDao providerDataDao;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<String> providerNosToCleanUp = new ArrayList<>();
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUpTransactionsAndCaches() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
    @DisplayName("should evict provider list caches when ProviderDataDao persists a new provider row")
    void shouldEvictProviderListCaches_whenProviderDataDaoPersistsNewProviderRow() {
        String existingProviderNo = newProviderNo();
        createProvider(existingProviderNo, "Cache", "Seed");

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(providerDao.getActiveProviders())
                    .extracting(Provider::getProviderNo)
                    .contains(existingProviderNo);
            assertThat(providerDao.getActiveProviderSummaries())
                    .extracting(ProviderSummaryDTO::getProviderNo)
                    .contains(existingProviderNo);
        });

        putCacheValue("providerNames", "name:" + existingProviderNo, "Stale Provider Name");
        assertThat(cacheEntryCount("providerNames")).isEqualTo(1);
        assertThat(cacheEntryCount("activeProviders")).isEqualTo(1);
        assertThat(cacheEntryCount("activeProviderSummaries")).isEqualTo(1);

        String newProviderNo = newProviderNo();
        providerNosToCleanUp.add(newProviderNo);

        transactionTemplate.executeWithoutResult(status -> providerDataDao.persist(buildProviderData(
                newProviderNo, "Fresh", "Persisted"
        )));

        assertThat(cacheEntryCount("providerNames")).isZero();
        assertThat(cacheEntryCount("activeProviders")).isZero();
        assertThat(cacheEntryCount("activeProviderSummaries")).isZero();

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(providerDao.getProviderName(newProviderNo)).isEqualTo("Fresh Persisted");
            assertThat(providerDao.getActiveProviders())
                    .extracting(Provider::getProviderNo)
                    .contains(newProviderNo);
            assertThat(providerDao.getActiveProviderSummaries())
                    .filteredOn(summary -> summary.getProviderNo().equals(newProviderNo))
                    .singleElement()
                    .satisfies(summary -> {
                        assertThat(summary.getFirstName()).isEqualTo("Fresh");
                        assertThat(summary.getLastName()).isEqualTo("Persisted");
                    });
        });
    }

    @Test
    @DisplayName("should evict provider caches when ProviderDataDao merges an existing provider row")
    void shouldEvictProviderCaches_whenProviderDataDaoMergesExistingProviderRow() {
        String providerNo = newProviderNo();
        createProvider(providerNo, "John", "Smith");

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(providerDao.getActiveProviders())
                    .filteredOn(provider -> provider.getProviderNo().equals(providerNo))
                    .singleElement()
                    .satisfies(provider -> {
                        assertThat(provider.getFirstName()).isEqualTo("John");
                        assertThat(provider.getLastName()).isEqualTo("Smith");
                    });
            assertThat(providerDao.getActiveProviderSummaries())
                    .filteredOn(summary -> summary.getProviderNo().equals(providerNo))
                    .singleElement()
                    .satisfies(summary -> {
                        assertThat(summary.getFirstName()).isEqualTo("John");
                        assertThat(summary.getLastName()).isEqualTo("Smith");
                    });
        });

        putCacheValue("providerNames", "name:" + providerNo, "Stale Provider Name");
        assertThat(cacheEntryCount("providerNames")).isEqualTo(1);
        assertThat(cacheEntryCount("activeProviders")).isEqualTo(1);
        assertThat(cacheEntryCount("activeProviderSummaries")).isEqualTo(1);

        transactionTemplate.executeWithoutResult(status -> {
            ProviderData provider = providerDataDao.findByProviderNo(providerNo);
            provider.setFirstName("Jane");
            provider.setLastName("Updated");
            providerDataDao.merge(provider);
        });

        assertThat(cacheEntryCount("providerNames")).isZero();
        assertThat(cacheEntryCount("activeProviders")).isZero();
        assertThat(cacheEntryCount("activeProviderSummaries")).isZero();

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(providerDao.getProviderName(providerNo)).isEqualTo("Jane Updated");
            assertThat(providerDao.getActiveProviders())
                    .filteredOn(provider -> provider.getProviderNo().equals(providerNo))
                    .singleElement()
                    .satisfies(provider -> {
                        assertThat(provider.getFirstName()).isEqualTo("Jane");
                        assertThat(provider.getLastName()).isEqualTo("Updated");
                    });
            assertThat(providerDao.getActiveProviderSummaries())
                    .filteredOn(summary -> summary.getProviderNo().equals(providerNo))
                    .singleElement()
                    .satisfies(summary -> {
                        assertThat(summary.getFirstName()).isEqualTo("Jane");
                        assertThat(summary.getLastName()).isEqualTo("Updated");
                    });
        });
    }

    @Test
    @DisplayName("should evict all three provider caches when ProviderDataDao batchPersist completes")
    void shouldEvictAllThreeProviderCaches_whenProviderDataDaoBatchPersistCompletes() {
        putCacheValue("providerNames", "name:__seed__", "Stale Name");
        putCacheValue("activeProviders", "anyKey", "Stale Active");
        putCacheValue("activeProviderSummaries", "anyKey", "Stale Summary");
        assertThat(cacheEntryCount("providerNames")).isEqualTo(1);
        assertThat(cacheEntryCount("activeProviders")).isEqualTo(1);
        assertThat(cacheEntryCount("activeProviderSummaries")).isEqualTo(1);

        String first = newProviderNo();
        String second = newProviderNo();
        providerNosToCleanUp.add(first);
        providerNosToCleanUp.add(second);
        providerDataDao.batchPersist(List.of(
                buildProviderData(first, "Batch", "Persist1"),
                buildProviderData(second, "Batch", "Persist2")
        ));

        assertThat(cacheEntryCount("providerNames")).isZero();
        assertThat(cacheEntryCount("activeProviders")).isZero();
        assertThat(cacheEntryCount("activeProviderSummaries")).isZero();
    }

    @Test
    @DisplayName("should evict all three provider caches before batchPersist runs to prevent stale entries on partial failure")
    void shouldEvictAllThreeProviderCaches_beforeBatchPersistRuns() {
        putCacheValue("providerNames", "name:__seed__", "Stale Name");
        putCacheValue("activeProviders", "anyKey", "Stale Active");
        putCacheValue("activeProviderSummaries", "anyKey", "Stale Summary");

        ProviderData invalid = buildProviderData(newProviderNo(), "Bad", "Data");
        // provider_no is the PK column; clearing it forces a persistence failure.
        invalid.set(null);

        try {
            providerDataDao.batchPersist(List.of(invalid));
        } catch (RuntimeException expected) {
            // Expected: persistence failure on null PK
        }

        assertThat(cacheEntryCount("providerNames"))
                .as("providerNames must be evicted BEFORE batchPersist body runs")
                .isZero();
        assertThat(cacheEntryCount("activeProviders")).isZero();
        assertThat(cacheEntryCount("activeProviderSummaries")).isZero();
    }

    @Test
    @DisplayName("should evict all three provider caches when ProviderDataDao batchRemove completes")
    void shouldEvictAllThreeProviderCaches_whenProviderDataDaoBatchRemoveCompletes() {
        String firstNo = newProviderNo();
        String secondNo = newProviderNo();
        createProvider(firstNo, "Batch", "Remove1");
        createProvider(secondNo, "Batch", "Remove2");

        putCacheValue("providerNames", "name:__seed__", "Stale Name");
        putCacheValue("activeProviders", "anyKey", "Stale Active");
        putCacheValue("activeProviderSummaries", "anyKey", "Stale Summary");

        ProviderData first = providerDataDao.findByProviderNo(firstNo);
        ProviderData second = providerDataDao.findByProviderNo(secondNo);
        providerDataDao.batchRemove(List.of(first, second));
        providerNosToCleanUp.remove(firstNo);
        providerNosToCleanUp.remove(secondNo);

        assertThat(cacheEntryCount("providerNames")).isZero();
        assertThat(cacheEntryCount("activeProviders")).isZero();
        assertThat(cacheEntryCount("activeProviderSummaries")).isZero();
    }

    private void createProvider(String providerNo, String firstName, String lastName) {
        providerNosToCleanUp.add(providerNo);
        transactionTemplate.executeWithoutResult(status -> {
            Provider provider = new Provider();
            provider.setProviderNo(providerNo);
            provider.setFirstName(firstName);
            provider.setLastName(lastName);
            provider.setStatus("1");
            provider.setProviderType("doctor");
            provider.setSex("F");
            provider.setSpecialty("Family Medicine");
            providerDao.saveProvider(provider);
        });
    }

    private ProviderData buildProviderData(String providerNo, String firstName, String lastName) {
        ProviderData provider = new ProviderData();
        provider.set(providerNo);
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        provider.setStatus("1");
        provider.setProviderType("doctor");
        provider.setSex("F");
        provider.setSpecialty("Family Medicine");
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

    @SuppressWarnings("unchecked")
    private long cacheEntryCount(String cacheName) {
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        assertThat(cache).isNotNull();
        return ((Cache<Object, Object>) cache.getNativeCache()).estimatedSize();
    }

    private void putCacheValue(String cacheName, Object key, Object value) {
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        assertThat(cache).isNotNull();
        cache.put(key, value);
    }

    private String newProviderNo() {
        return String.format("T%05d", ThreadLocalRandom.current().nextInt(10000, 100000));
    }
}
