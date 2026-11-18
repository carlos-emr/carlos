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

import io.github.carlos_emr.carlos.appt.status.service.AppointmentStatusMgr;
import io.github.carlos_emr.carlos.appt.status.service.AppointmentStatusStyleException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        appointmentStatusDao = createAndRegisterMock(AppointmentStatusDao.class);
        cacheManager = createAndRegisterMock(CacheManager.class);
        transactionManager = createAndRegisterMock(PlatformTransactionManager.class);
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

    @Test
    @DisplayName("should reset descriptions, colours and icons by stable status code")
    void shouldResetDescriptionsColoursAndIcons_byStableStatusCode() {
        Map<String, ResetExpectation> defaults = new LinkedHashMap<>();
        defaults.put("t", new ResetExpectation("To Do", "#FDFEC7", "starbill.gif"));
        defaults.put("T", new ResetExpectation("Daysheet Printed", "#FDFEC7", "todo.gif"));
        defaults.put("H", new ResetExpectation("Here", "#00ee00", "here.gif"));
        defaults.put("P", new ResetExpectation("Picked", "#FFBBFF", "picked.gif"));
        defaults.put("E", new ResetExpectation("Empty Room", "#FFFF33", "empty.gif"));
        defaults.put("a", new ResetExpectation("Customized 1", "#897DF8", "1.gif"));
        defaults.put("b", new ResetExpectation("Customized 2", "#897DF8", "2.gif"));
        defaults.put("c", new ResetExpectation("Customized 3", "#897DF8", "3.gif"));
        defaults.put("d", new ResetExpectation("Customized 4", "#897DF8", "4.gif"));
        defaults.put("e", new ResetExpectation("Customized 5", "#897DF8", "5.gif"));
        defaults.put("f", new ResetExpectation("Customized 6", "#897DF8", "5.gif"));
        defaults.put("h", new ResetExpectation("Confirmed", "#2fcccf", "thumb.png"));
        defaults.put("N", new ResetExpectation("No Show", "#cccccc", "noshow.gif"));
        defaults.put("C", new ResetExpectation("Cancelled", "#999999", "cancel.gif"));
        defaults.put("B", new ResetExpectation("Billed", "#3ea4e1", "billed.gif"));

        Map<String, Integer> ids = new LinkedHashMap<>();
        List<AppointmentStatus> stored = new java.util.ArrayList<>();
        int nextId = 301;
        for (String code : defaults.keySet()) {
            ids.put(code, nextId);
            stored.add(status(nextId, code));
            nextId++;
        }
        when(appointmentStatusDao.findAll()).thenReturn(stored);

        new AppointmentStatusMgrImpl().reset();

        verify(appointmentStatusDao).findAll();
        defaults.forEach((code, expectation) -> verify(appointmentStatusDao).modifyStyle(
                ids.get(code), expectation.description(), expectation.colour(), expectation.icon()));
        verifyNoMoreInteractions(appointmentStatusDao);
        // Every restyle in one transaction.
        verify(transactionManager).getTransaction(org.mockito.ArgumentMatchers.any());
        verify(transactionManager).commit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("should reset the exact code when a custom code shares its first letter")
    void shouldResetExactCode_whenCustomCodeSharesFirstLetter() {
        when(appointmentStatusDao.findAll()).thenReturn(List.of(status(77, "tX"), status(78, "t")));

        new AppointmentStatusMgrImpl().reset();

        verify(appointmentStatusDao, never()).modifyStyle(org.mockito.ArgumentMatchers.eq(77),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(appointmentStatusDao).modifyStyle(78, "To Do", "#FDFEC7", "starbill.gif");
    }

    @Test
    @DisplayName("should save a trimmed description for any status, including a core one")
    void shouldSaveTrimmedDescription_forAnyStatus() {
        when(appointmentStatusDao.modifyStyle(14, "Patient cancelled", null, null)).thenReturn(true);

        assertThat(new AppointmentStatusMgrImpl().updateDescription(14, "  Patient cancelled  ")).isTrue();

        verify(appointmentStatusDao).modifyStyle(14, "Patient cancelled", null, null);
        verify(appointmentStatusDao, never()).find(14);
    }

    @Test
    @DisplayName("should refuse a blank or over-long description without saving")
    void shouldRefuseDescription_whenBlankOrTooLong() {
        AppointmentStatusMgrImpl manager = new AppointmentStatusMgrImpl();
        String overlongDescription = "d".repeat(AppointmentStatusMgr.DESCRIPTION_MAX_LENGTH + 1);

        assertThatThrownBy(() -> manager.updateDescription(13, "   "))
                .isInstanceOf(AppointmentStatusStyleException.class)
                .extracting("messageKey").isEqualTo("admin.appt.status.mgr.error.descriptionRequired");
        assertThatThrownBy(() -> manager.updateDescription(13, overlongDescription))
                .isInstanceOf(AppointmentStatusStyleException.class)
                .extracting("messageKey").isEqualTo("admin.appt.status.mgr.error.descriptionLength");
        assertThat(manager.updateDescription(13, "d".repeat(AppointmentStatusMgr.DESCRIPTION_MAX_LENGTH))).isFalse();
        verify(appointmentStatusDao).modifyStyle(13, "d".repeat(AppointmentStatusMgr.DESCRIPTION_MAX_LENGTH), null, null);
        // The refused values reached no DAO call at all.
        verifyNoMoreInteractions(appointmentStatusDao);
    }

    @Test
    @DisplayName("should accept only #rrggbb colours")
    void shouldRefuseColour_whenNotHexTriplet() {
        AppointmentStatusMgrImpl manager = new AppointmentStatusMgrImpl();
        when(appointmentStatusDao.modifyStyle(13, null, "#AbCdEf", null)).thenReturn(true);

        assertThat(manager.updateColour(13, " #AbCdEf ")).isTrue();
        for (String bad : new String[]{null, "", "red", "#abc", "#abcdeg", "#abcdef;background:url(x)", "abcdef"}) {
            assertThatThrownBy(() -> manager.updateColour(13, bad))
                    .as("colour %s", bad)
                    .isInstanceOf(AppointmentStatusStyleException.class)
                    .extracting("messageKey").isEqualTo("admin.appt.status.mgr.error.color");
        }
        verify(appointmentStatusDao).modifyStyle(13, null, "#AbCdEf", null);
        verifyNoMoreInteractions(appointmentStatusDao);
    }

    @Test
    @DisplayName("should accept only icons from the icon set")
    void shouldRefuseIcon_whenOutsideIconSet() {
        AppointmentStatusMgrImpl manager = new AppointmentStatusMgrImpl();
        when(appointmentStatusDao.modifyStyle(13, null, null, "thumb.png")).thenReturn(true);

        assertThat(manager.updateIcon(13, "thumb.png")).isTrue();
        for (String bad : new String[]{null, "", "../../WEB-INF/web.xml", "Sthumb.png", "noshow.gif\"", "javascript:x"}) {
            assertThatThrownBy(() -> manager.updateIcon(13, bad))
                    .as("icon %s", bad)
                    .isInstanceOf(AppointmentStatusStyleException.class)
                    .extracting("messageKey").isEqualTo("admin.appt.status.mgr.error.icon");
        }
        verify(appointmentStatusDao).modifyStyle(13, null, null, "thumb.png");
        verifyNoMoreInteractions(appointmentStatusDao);
    }

    @Test
    @DisplayName("should enable or disable only a status whose editable flag is 1")
    void shouldChangeStatus_onlyWhenEditable() {
        AppointmentStatus editable = status(13, "N");
        editable.setEditable(1);
        AppointmentStatus core = status(14, "C");
        core.setEditable(0);
        when(appointmentStatusDao.find(13)).thenReturn(editable);
        when(appointmentStatusDao.find(14)).thenReturn(core);
        AppointmentStatusMgrImpl manager = new AppointmentStatusMgrImpl();

        assertThat(manager.changeStatus(13, 0)).isTrue();
        assertThat(manager.changeStatus(14, 0)).isFalse();
        assertThat(manager.changeStatus(99, 1)).isFalse();
        assertThatThrownBy(() -> manager.changeStatus(13, 2))
                .isInstanceOf(AppointmentStatusStyleException.class);

        verify(appointmentStatusDao).changeStatus(13, 0);
        verify(appointmentStatusDao, never()).changeStatus(14, 0);
    }

    @Test
    @DisplayName("should list only icons whose signed and verified copies exist")
    void shouldListIcons_withSignedAndVerifiedCopies() {
        java.nio.file.Path images = java.nio.file.Path.of("src/main/webapp/images");
        for (String icon : AppointmentStatusMgr.ICON_SET) {
            assertThat(images.resolve(icon)).as(icon).exists();
            assertThat(images.resolve("S" + icon)).as("S" + icon).exists();
            assertThat(images.resolve("V" + icon)).as("V" + icon).exists();
        }
    }

    private AppointmentStatus status(int id, String code) {
        AppointmentStatus status = new AppointmentStatus();
        status.setId(id);
        status.setStatus(code);
        return status;
    }

    private record ResetExpectation(String description, String colour, String icon) {}
}
