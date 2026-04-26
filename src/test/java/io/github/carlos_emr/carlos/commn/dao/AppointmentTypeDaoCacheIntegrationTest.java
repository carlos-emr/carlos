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
import io.github.carlos_emr.carlos.commn.model.AppointmentType;
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

@DisplayName("AppointmentTypeDao cache invalidation")
@Tag("integration")
@Tag("dao")
@Tag("cache")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppointmentTypeDaoCacheIntegrationTest extends CarlosTestBase {

    private static final String CACHE_NAME = "appointmentTypes";

    @Autowired
    private AppointmentTypeDao appointmentTypeDao;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Integer> idsToCleanUp = new ArrayList<>();
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUpTransactionsAndCaches() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        clearCache();
    }

    @AfterEach
    void cleanUpCommittedRows() {
        clearCache();
        for (Integer id : idsToCleanUp) {
            transactionTemplate.executeWithoutResult(status -> {
                AppointmentType entity = appointmentTypeDao.find(id);
                if (entity != null) {
                    appointmentTypeDao.remove(entity);
                }
            });
        }
    }

    @Test
    @DisplayName("should cache listAll results")
    void shouldCacheListAllResults() {
        transactionTemplate.executeWithoutResult(status -> {
            AppointmentType first = buildAppointmentType("cacheType_" + randomSuffix());
            appointmentTypeDao.persist(first);
            idsToCleanUp.add(first.getId());
        });

        transactionTemplate.executeWithoutResult(status -> appointmentTypeDao.listAll());

        assertThat(cacheEntryCount())
                .as("listAll should populate appointmentTypes cache")
                .isPositive();
    }

    @Test
    @DisplayName("should evict appointmentTypes cache when persist commits")
    void shouldEvictAppointmentTypesCache_whenPersistCommits() {
        transactionTemplate.executeWithoutResult(status -> appointmentTypeDao.listAll());
        assertThat(cacheEntryCount()).isPositive();

        transactionTemplate.executeWithoutResult(status -> {
            AppointmentType inserted = buildAppointmentType("cacheType_" + randomSuffix());
            appointmentTypeDao.persist(inserted);
            idsToCleanUp.add(inserted.getId());
        });

        assertThat(cacheEntryCount())
                .as("persist must evict appointmentTypes cache after commit")
                .isZero();
    }

    private AppointmentType buildAppointmentType(String name) {
        AppointmentType entity = new AppointmentType();
        entity.setName(name);
        entity.setDuration(15);
        return entity;
    }

    private String randomSuffix() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999));
    }

    private void clearCache() {
        org.springframework.cache.Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).as("cache %s should exist", CACHE_NAME).isNotNull();
        cache.clear();
    }

    @SuppressWarnings("unchecked")
    private long cacheEntryCount() {
        org.springframework.cache.Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        Cache<Object, Object> nativeCache = (Cache<Object, Object>) cache.getNativeCache();
        return nativeCache.asMap().size();
    }
}
