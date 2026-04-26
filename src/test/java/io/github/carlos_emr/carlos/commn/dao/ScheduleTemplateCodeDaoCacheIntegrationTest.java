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
import io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode;
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

@DisplayName("ScheduleTemplateCodeDao cache invalidation")
@Tag("integration")
@Tag("dao")
@Tag("cache")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduleTemplateCodeDaoCacheIntegrationTest extends CarlosTestBase {

    private static final String CACHE_NAME = "scheduleTemplateCodes";

    @Autowired
    private ScheduleTemplateCodeDao scheduleTemplateCodeDao;

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
                ScheduleTemplateCode entity = scheduleTemplateCodeDao.find(id);
                if (entity != null) {
                    scheduleTemplateCodeDao.remove(entity);
                }
            });
        }
    }

    @Test
    @DisplayName("should cache code lookup results")
    void shouldCacheCodeLookupResults() {
        char code = randomCode();
        transactionTemplate.executeWithoutResult(status -> {
            ScheduleTemplateCode entity = buildScheduleTemplateCode(code);
            scheduleTemplateCodeDao.persist(entity);
            idsToCleanUp.add(entity.getId());
        });

        transactionTemplate.executeWithoutResult(status -> scheduleTemplateCodeDao.getByCode(code));

        assertThat(cacheEntryCount())
                .as("getByCode should populate scheduleTemplateCodes cache")
                .isPositive();
    }

    @Test
    @DisplayName("should evict scheduleTemplateCodes cache when persist commits")
    void shouldEvictScheduleTemplateCodesCache_whenPersistCommits() {
        transactionTemplate.executeWithoutResult(status -> scheduleTemplateCodeDao.findAll());
        assertThat(cacheEntryCount()).isPositive();

        transactionTemplate.executeWithoutResult(status -> {
            ScheduleTemplateCode entity = buildScheduleTemplateCode(randomCode());
            scheduleTemplateCodeDao.persist(entity);
            idsToCleanUp.add(entity.getId());
        });

        assertThat(cacheEntryCount())
                .as("persist must evict scheduleTemplateCodes cache after commit")
                .isZero();
    }

    private ScheduleTemplateCode buildScheduleTemplateCode(char code) {
        ScheduleTemplateCode entity = new ScheduleTemplateCode();
        entity.setCode(code);
        entity.setDuration("15");
        entity.setDescription("cache test");
        return entity;
    }

    private char randomCode() {
        return (char) ('A' + ThreadLocalRandom.current().nextInt(0, 26));
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
