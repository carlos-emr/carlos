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
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
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

/**
 * Cache invalidation regression tests for {@link AppointmentStatusDao}.
 *
 * <p>Verifies that write paths (persist/merge/remove/saveEntity/modifyStatus/changeStatus)
 * correctly evict the {@code appointmentStatuses} cache after commit. A refactor that
 * dropped one of these {@code @CacheEvict} annotations would otherwise ship silently.</p>
 */
@DisplayName("AppointmentStatusDao cache invalidation")
@Tag("integration")
@Tag("dao")
@Tag("cache")
@Tag("appointment")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AppointmentStatusDaoCacheIntegrationTest extends CarlosTestBase {

    private static final String CACHE_NAME = "appointmentStatuses";

    @Autowired
    private AppointmentStatusDao appointmentStatusDao;

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
                AppointmentStatus persisted = appointmentStatusDao.find(id);
                if (persisted != null) {
                    appointmentStatusDao.remove(persisted);
                }
            });
        }
    }

    @Test
    @DisplayName("should evict appointmentStatuses cache when persist commits")
    void shouldEvictAppointmentStatusesCache_whenPersistCommits() {
        transactionTemplate.executeWithoutResult(status -> appointmentStatusDao.findAll());
        // findAll uses key 'all' — exactly one cache entry after seeding.
        assertThat(cacheEntryCount()).isEqualTo(1);

        transactionTemplate.executeWithoutResult(status -> {
            AppointmentStatus entity = buildStatus("X", "cache-test");
            appointmentStatusDao.persist(entity);
            idsToCleanUp.add(entity.getId());
        });

        assertThat(cacheEntryCount())
                .as("persist must evict the appointmentStatuses cache after commit")
                .isZero();
    }

    @Test
    @DisplayName("should evict appointmentStatuses cache when modifyStatus commits")
    void shouldEvictAppointmentStatusesCache_whenModifyStatusCommits() {
        AppointmentStatus seeded = persistSeed("Y", "original");

        transactionTemplate.executeWithoutResult(status -> appointmentStatusDao.findAll());
        assertThat(cacheEntryCount()).isPositive();

        transactionTemplate.executeWithoutResult(status ->
                appointmentStatusDao.modifyStatus(seeded.getId(), "modified", "#abcdef"));

        assertThat(cacheEntryCount())
                .as("modifyStatus must evict the appointmentStatuses cache after commit")
                .isZero();
    }

    @Test
    @DisplayName("should evict appointmentStatuses cache when changeStatus commits")
    void shouldEvictAppointmentStatusesCache_whenChangeStatusCommits() {
        AppointmentStatus seeded = persistSeed("Z", "active");

        transactionTemplate.executeWithoutResult(status -> appointmentStatusDao.findActive());
        assertThat(cacheEntryCount()).isPositive();

        transactionTemplate.executeWithoutResult(status ->
                appointmentStatusDao.changeStatus(seeded.getId(), 0));

        assertThat(cacheEntryCount())
                .as("changeStatus must evict the appointmentStatuses cache after commit")
                .isZero();
    }

    @Test
    @DisplayName("should evict appointmentStatuses cache when saveEntity commits")
    void shouldEvictAppointmentStatusesCache_whenSaveEntityCommits() {
        transactionTemplate.executeWithoutResult(status -> appointmentStatusDao.findAll());
        assertThat(cacheEntryCount()).isPositive();

        transactionTemplate.executeWithoutResult(status -> {
            AppointmentStatus entity = buildStatus("S", "save-entity");
            AppointmentStatus saved = appointmentStatusDao.saveEntity(entity);
            idsToCleanUp.add(saved.getId());
        });

        assertThat(cacheEntryCount())
                .as("saveEntity must evict the appointmentStatuses cache after commit")
                .isZero();
    }

    @Test
    @DisplayName("should evict appointmentStatuses cache when remove commits")
    void shouldEvictAppointmentStatusesCache_whenRemoveCommits() {
        AppointmentStatus seeded = persistSeed("R", "to-remove");

        transactionTemplate.executeWithoutResult(status -> appointmentStatusDao.findAll());
        assertThat(cacheEntryCount()).isPositive();

        transactionTemplate.executeWithoutResult(status -> {
            AppointmentStatus managed = appointmentStatusDao.find(seeded.getId());
            appointmentStatusDao.remove(managed);
            idsToCleanUp.remove(seeded.getId());
        });

        assertThat(cacheEntryCount())
                .as("remove must evict the appointmentStatuses cache after commit")
                .isZero();
    }

    @Test
    @DisplayName("should evict appointmentStatuses cache when batchPersist completes")
    void shouldEvictAppointmentStatusesCache_whenBatchPersistCompletes() {
        transactionTemplate.executeWithoutResult(status -> appointmentStatusDao.findAll());
        assertThat(cacheEntryCount()).isPositive();

        List<AppointmentStatus> entities = List.of(
                buildStatus("B", "batch-1"),
                buildStatus("C", "batch-2")
        );
        appointmentStatusDao.batchPersist(entities);
        entities.forEach(e -> idsToCleanUp.add(e.getId()));

        assertThat(cacheEntryCount())
                .as("batchPersist must evict the appointmentStatuses cache (beforeInvocation = true)")
                .isZero();
    }

    @Test
    @DisplayName("should evict appointmentStatuses cache before batchPersist runs to prevent stale-entries on partial failure")
    void shouldEvictAppointmentStatusesCache_beforeBatchPersistRuns() {
        transactionTemplate.executeWithoutResult(status -> appointmentStatusDao.findAll());
        assertThat(cacheEntryCount()).isPositive();

        // Regression test for the partial-batch-commit bug: verify that cache eviction
        // fires BEFORE the batch body runs, so a mid-loop failure still leaves the cache
        // empty rather than pinning entries whose underlying data has partially changed.
        // We prove this with an invalid entity that makes batchPersist throw, then assert
        // the cache is empty — only reachable if eviction happened before the exception.
        AppointmentStatus invalid = buildStatus("X", "intentionally-invalid");
        invalid.setStatus(null);

        try {
            appointmentStatusDao.batchPersist(List.of(invalid));
        } catch (RuntimeException expected) {
            // Expected: null NOT NULL violation or similar
        }

        assertThat(cacheEntryCount())
                .as("batchPersist must evict BEFORE the body runs — otherwise a partial-commit failure leaves stale entries until TTL")
                .isZero();
    }

    @Test
    @DisplayName("should evict appointmentStatuses cache when batchRemove completes")
    void shouldEvictAppointmentStatusesCache_whenBatchRemoveCompletes() {
        AppointmentStatus first = persistSeed("G", "batch-rem-1");
        AppointmentStatus second = persistSeed("H", "batch-rem-2");

        transactionTemplate.executeWithoutResult(status -> appointmentStatusDao.findAll());
        assertThat(cacheEntryCount()).isPositive();

        appointmentStatusDao.batchRemove(List.of(first, second));
        idsToCleanUp.remove(first.getId());
        idsToCleanUp.remove(second.getId());

        assertThat(cacheEntryCount())
                .as("batchRemove must evict the appointmentStatuses cache")
                .isZero();
    }

    private AppointmentStatus persistSeed(String statusCode, String description) {
        AppointmentStatus seeded = new AppointmentStatus();
        transactionTemplate.executeWithoutResult(status -> {
            seeded.setStatus(statusCode);
            seeded.setDescription(description);
            seeded.setColor("#ffffff");
            seeded.setActive(1);
            seeded.setEditable(1);
            appointmentStatusDao.persist(seeded);
            idsToCleanUp.add(seeded.getId());
        });
        return seeded;
    }

    private AppointmentStatus buildStatus(String statusCode, String description) {
        AppointmentStatus entity = new AppointmentStatus();
        entity.setStatus(statusCode);
        entity.setDescription(description);
        entity.setColor("#ffffff");
        entity.setActive(1);
        entity.setEditable(1);
        return entity;
    }

    private void clearCache() {
        org.springframework.cache.Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).as("cache %s should be present in the test context", CACHE_NAME).isNotNull();
        cache.clear();
    }

    @SuppressWarnings("unchecked")
    private long cacheEntryCount() {
        org.springframework.cache.Cache cache = cacheManager.getCache(CACHE_NAME);
        assertThat(cache).isNotNull();
        return ((Cache<Object, Object>) cache.getNativeCache()).estimatedSize();
    }
}
