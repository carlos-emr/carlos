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
package io.github.carlos_emr.carlos.PMmodule.dao;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
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
 * Cache invalidation and key-distinctness regression tests for {@link ProviderDao}.
 *
 * <p>PR 1883 introduced {@code providerNames}, {@code activeProviders}, and
 * {@code activeProviderSummaries} caches. These tests verify:
 * <ul>
 *   <li>{@code saveProvider} and {@code updateProvider} evict all three caches after commit.</li>
 *   <li>{@code getProviderName} and {@code getProviderNameLastFirst} use distinct cache keys for the
 *       same providerNo (the {@code providerNames} cache is shared between the two methods).</li>
 * </ul></p>
 */
@DisplayName("ProviderDao cache invalidation and key distinctness")
@Tag("integration")
@Tag("dao")
@Tag("cache")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProviderDaoCacheIntegrationTest extends CarlosTestBase {

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
    @DisplayName("should store distinct entries under name: and nameLastFirst: prefixes for the same providerNo")
    void shouldStoreDistinctEntries_forNameAndNameLastFirstKeys() {
        String providerNo = newProviderNo();
        createProvider(providerNo, "John", "Smith");
        clearProviderCaches();

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(providerDao.getProviderName(providerNo)).isEqualTo("John Smith");
            assertThat(providerDao.getProviderNameLastFirst(providerNo)).isEqualTo("Smith, John");
        });

        // Both calls cache under the providerNames namespace; if the two keys collided the second
        // read would hit the first cached entry, leaving only one entry. Verify both entries persist
        // AND that the VALUES behind each key are distinct (a same-count but same-value cache would
        // silently produce "Smith, John" for both keys).
        assertThat(cacheEntryCount("providerNames"))
                .as("getProviderName and getProviderNameLastFirst must use distinct cache keys")
                .isEqualTo(2);

        org.springframework.cache.Cache providerNames = cacheManager.getCache("providerNames");
        assertThat(providerNames).isNotNull();
        assertThat(providerNames.get("name:" + providerNo).get())
                .as("key 'name:%s' must map to the first-last formatted name", providerNo)
                .isEqualTo("John Smith");
        assertThat(providerNames.get("nameLastFirst:" + providerNo).get())
                .as("key 'nameLastFirst:%s' must map to the last-first formatted name", providerNo)
                .isEqualTo("Smith, John");
    }

    @Test
    @DisplayName("should evict provider caches when ProviderDao saveProvider persists a new provider")
    void shouldEvictProviderCaches_whenProviderDaoSaveProviderPersists() {
        // Seed an existing cached read so saveProvider has something to evict.
        String existingProviderNo = newProviderNo();
        createProvider(existingProviderNo, "Seed", "Provider");
        transactionTemplate.executeWithoutResult(status -> {
            providerDao.getActiveProviders();
            providerDao.getActiveProviderSummaries();
            providerDao.getProviderName(existingProviderNo);
        });

        assertThat(cacheEntryCount("providerNames")).isPositive();
        assertThat(cacheEntryCount("activeProviders")).isPositive();
        assertThat(cacheEntryCount("activeProviderSummaries")).isPositive();

        String newProviderNo = newProviderNo();
        providerNosToCleanUp.add(newProviderNo);
        transactionTemplate.executeWithoutResult(status -> {
            Provider provider = new Provider();
            provider.setProviderNo(newProviderNo);
            provider.setFirstName("Fresh");
            provider.setLastName("Saved");
            provider.setStatus("1");
            provider.setProviderType("doctor");
            provider.setSex("F");
            provider.setSpecialty("Family Medicine");
            providerDao.saveProvider(provider);
        });

        assertThat(cacheEntryCount("providerNames")).isZero();
        assertThat(cacheEntryCount("activeProviders")).isZero();
        assertThat(cacheEntryCount("activeProviderSummaries")).isZero();
    }

    @Test
    @DisplayName("should evict provider caches when ProviderDao updateProvider mutates an existing provider")
    void shouldEvictProviderCaches_whenProviderDaoUpdateProviderMutates() {
        String providerNo = newProviderNo();
        createProvider(providerNo, "Alice", "Original");

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(providerDao.getProviderName(providerNo)).isEqualTo("Alice Original");
        });
        assertThat(cacheEntryCount("providerNames")).isEqualTo(1);

        transactionTemplate.executeWithoutResult(status -> {
            Provider provider = providerDao.getProvider(providerNo);
            provider.setLastName("Updated");
            providerDao.updateProvider(provider);
        });

        assertThat(cacheEntryCount("providerNames")).isZero();

        transactionTemplate.executeWithoutResult(status -> {
            assertThat(providerDao.getProviderName(providerNo)).isEqualTo("Alice Updated");
        });
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

    private String newProviderNo() {
        return String.format("C%05d", ThreadLocalRandom.current().nextInt(10000, 100000));
    }
}
