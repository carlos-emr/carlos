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
package io.github.carlos_emr.carlos.appt.status.service.impl;

import io.github.carlos_emr.carlos.commn.dao.AppointmentStatusDao;
import io.github.carlos_emr.carlos.commn.model.AppointmentStatus;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AppointmentStatusMgrImpl} legacy cache invalidation hooks.
 *
 * @since 2026-04-20
 */
@Tag("unit")
@Tag("appointment")
@DisplayName("AppointmentStatusMgrImpl unit tests")
class AppointmentStatusMgrImplUnitTest extends CarlosUnitTestBase {

    private AppointmentStatusDao appointmentStatusDao;
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        appointmentStatusDao = createAndRegisterMock(AppointmentStatusDao.class);
        cacheManager = createAndRegisterMock(CacheManager.class);
    }

    @AfterEach
    void clearAnyTransactionSynchronization() {
        // Defensive: tests that call TransactionSynchronizationManager.initSynchronization()
        // must clear it; if any test forgets, this prevents leaking state into subsequent tests.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("should clear appointment status cache when legacy dirty flag is set")
    void shouldClearAppointmentStatusCache_whenLegacyDirtyFlagSet() {
        Cache appointmentStatusesCache = mock(Cache.class);
        when(cacheManager.getCache("appointmentStatuses")).thenReturn(appointmentStatusesCache);

        AppointmentStatusMgrImpl.setCacheIsDirty(true);

        verify(cacheManager).getCache("appointmentStatuses");
        verify(appointmentStatusesCache).clear();
    }

    @Test
    @DisplayName("should not clear appointment status cache when legacy dirty flag is false")
    void shouldNotClearAppointmentStatusCache_whenLegacyDirtyFlagFalse() {
        AppointmentStatusMgrImpl.setCacheIsDirty(false);

        verify(cacheManager, never()).getCache("appointmentStatuses");
    }

    @Test
    @DisplayName("should resolve a fresh AppointmentStatusDao on each getCachedActiveStatuses call")
    void shouldResolveFreshDao_onEachGetCachedActiveStatusesCall() {
        AppointmentStatus firstStatus = new AppointmentStatus();
        firstStatus.setId(1);
        when(appointmentStatusDao.findActive()).thenReturn(List.of(firstStatus));

        assertThat(AppointmentStatusMgrImpl.getCachedActiveStatuses())
                .extracting(AppointmentStatus::getId)
                .containsExactly(1);

        // Swapping the registered mock simulates a Spring context refresh; the next call
        // must resolve the new DAO rather than returning the cached-first-bean result.
        AppointmentStatusDao secondAppointmentStatusDao = createAndRegisterMock(AppointmentStatusDao.class);
        AppointmentStatus secondStatus = new AppointmentStatus();
        secondStatus.setId(2);
        when(secondAppointmentStatusDao.findActive()).thenReturn(List.of(secondStatus));

        assertThat(AppointmentStatusMgrImpl.getCachedActiveStatuses())
                .extracting(AppointmentStatus::getId)
                .containsExactly(2);
    }

    @Test
    @DisplayName("should resolve a fresh CacheManager on each setCacheIsDirty call")
    void shouldResolveFreshCacheManager_onEachSetCacheIsDirtyCall() {
        Cache firstCache = mock(Cache.class);
        when(cacheManager.getCache("appointmentStatuses")).thenReturn(firstCache);
        AppointmentStatusMgrImpl.setCacheIsDirty(true);
        verify(cacheManager).getCache("appointmentStatuses");
        verify(firstCache).clear();

        CacheManager secondCacheManager = createAndRegisterMock(CacheManager.class);
        Cache secondCache = mock(Cache.class);
        when(secondCacheManager.getCache("appointmentStatuses")).thenReturn(secondCache);
        AppointmentStatusMgrImpl.setCacheIsDirty(true);
        verify(secondCacheManager).getCache("appointmentStatuses");
        verify(secondCache).clear();
    }

    @Test
    @DisplayName("should defer cache clear when transaction synchronization is active")
    void shouldDeferCacheClear_whenTransactionSynchronizationIsActive() {
        Cache appointmentStatusesCache = mock(Cache.class);
        when(cacheManager.getCache("appointmentStatuses")).thenReturn(appointmentStatusesCache);

        TransactionSynchronizationManager.initSynchronization();

        AppointmentStatusMgrImpl.setCacheIsDirty(true);

        verify(appointmentStatusesCache, never()).clear();
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertThat(syncs)
                .as("setCacheIsDirty must register a TransactionSynchronization to defer clear() until commit")
                .hasSize(1);

        // Simulate transaction commit; the registered afterCommit callback must run cache.clear()
        syncs.get(0).afterCommit();
        verify(appointmentStatusesCache).clear();
    }

    @Test
    @DisplayName("should not clear cache when active transaction rolls back")
    void shouldNotClearCache_whenActiveTransactionRollsBack() {
        Cache appointmentStatusesCache = mock(Cache.class);
        when(cacheManager.getCache("appointmentStatuses")).thenReturn(appointmentStatusesCache);

        TransactionSynchronizationManager.initSynchronization();

        AppointmentStatusMgrImpl.setCacheIsDirty(true);

        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertThat(syncs).hasSize(1);

        // Simulate rollback: afterCompletion is invoked with STATUS_ROLLED_BACK; afterCommit is NOT invoked.
        syncs.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(appointmentStatusesCache, never()).clear();
    }

    @Test
    @DisplayName("should swallow and warn when CacheManager bean is unavailable")
    void shouldSwallowAndWarn_whenCacheManagerBeanIsUnavailable() {
        // Override the broad parent stub so the CacheManager lookup raises a BeansException.
        // The setCacheIsDirty contract is to log+swallow rather than abort the JPA write that
        // triggered the callback, so this call must complete without throwing.
        springUtilsMock.when(() -> SpringUtils.getBean(CacheManager.class))
                .thenThrow(new NoSuchBeanDefinitionException(CacheManager.class));

        AppointmentStatusMgrImpl.setCacheIsDirty(true);

        verifyNoInteractions(cacheManager);
    }

    @Test
    @DisplayName("should swallow and warn when named appointmentStatuses cache is missing")
    void shouldSwallowAndWarn_whenNamedAppointmentStatusesCacheIsMissing() {
        // CacheManager bean is present but does not expose the expected cache. The contract
        // is to log+swallow rather than abort the JPA write.
        when(cacheManager.getCache("appointmentStatuses")).thenReturn(null);

        AppointmentStatusMgrImpl.setCacheIsDirty(true);

        verify(cacheManager).getCache("appointmentStatuses");
    }
}
