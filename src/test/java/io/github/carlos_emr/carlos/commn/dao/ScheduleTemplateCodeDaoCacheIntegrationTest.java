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
import io.github.carlos_emr.carlos.config.CacheConfig;
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

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScheduleTemplateCodeDao cache invalidation")
@Tag("integration")
@Tag("dao")
@Tag("cache")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScheduleTemplateCodeDaoCacheIntegrationTest extends CarlosTestBase {

    private static final String CACHE_NAME = CacheConfig.SCHEDULE_TEMPLATE_CODES;

    @Autowired
    private ScheduleTemplateCodeDao scheduleTemplateCodeDao;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final char[] TEST_CODES = {
            '!', '#', '$', '%', '&', '*', '+', '-', '/', ':', ';', '<', '=', '>', '?', '@', '^', '_', '~'
    };

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
    @DisplayName("should cache code lookup results when called")
    void shouldCacheCodeLookupResults_whenCalled() {
        char code = nextUnusedCode();
        Integer insertedId = transactionTemplate.execute(status -> {
            ScheduleTemplateCode entity = buildScheduleTemplateCode(code);
            scheduleTemplateCodeDao.persist(entity);
            idsToCleanUp.add(entity.getId());
            return entity.getId();
        });

        ScheduleTemplateCode cached = transactionTemplate.execute(status -> scheduleTemplateCodeDao.getByCode(code));
        ScheduleTemplateCode secondLookup = transactionTemplate.execute(status -> scheduleTemplateCodeDao.getByCode(code));

        assertThat(cached)
                .as("getByCode should return the schedule template code inserted by this test")
                .isNotNull();
        assertThat(cached.getId()).isEqualTo(insertedId);
        assertThat(cacheEntryCount())
                .as("getByCode should populate scheduleTemplateCodes cache")
                .isPositive();
        assertThat(secondLookup)
                .as("second getByCode call should be served from the scheduleTemplateCodes cache")
                .isSameAs(cached);
    }

    @Test
    @DisplayName("should evict scheduleTemplateCodes cache when persist commits")
    void shouldEvictScheduleTemplateCodesCache_whenPersistCommits() {
        char cachedCode = nextUnusedCode();
        transactionTemplate.executeWithoutResult(status -> {
            ScheduleTemplateCode entity = buildScheduleTemplateCode(cachedCode);
            scheduleTemplateCodeDao.persist(entity);
            idsToCleanUp.add(entity.getId());
        });
        char mutationCode = nextUnusedCodeExcept(cachedCode);

        clearCache();
        transactionTemplate.executeWithoutResult(status -> {
            scheduleTemplateCodeDao.findAll();
            scheduleTemplateCodeDao.getByCode(cachedCode);
            scheduleTemplateCodeDao.findTemplateCodes();
        });
        assertThat(cacheEntryCount())
                .as("test should seed multiple scheduleTemplateCodes cache keys before mutation")
                .isGreaterThanOrEqualTo(3);

        transactionTemplate.executeWithoutResult(status -> {
            ScheduleTemplateCode entity = buildScheduleTemplateCode(mutationCode);
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

    private char nextUnusedCode() {
        for (char candidate : TEST_CODES) {
            if (scheduleTemplateCodeDao.getByCode(candidate) == null) {
                return candidate;
            }
        }
        throw new AssertionError("No unused one-character schedule template code is available for this test");
    }

    private char nextUnusedCodeExcept(char excludedCode) {
        for (char candidate : TEST_CODES) {
            if (candidate != excludedCode && scheduleTemplateCodeDao.getByCode(candidate) == null) {
                return candidate;
            }
        }
        throw new AssertionError("No second unused one-character schedule template code is available for this test");
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
