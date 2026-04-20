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
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AppointmentStatusMgrImpl} legacy cache invalidation hooks.
 *
 * @since 2026-04-20
 */
@Tag("appointment")
@DisplayName("AppointmentStatusMgrImpl unit tests")
class AppointmentStatusMgrImplUnitTest extends CarlosUnitTestBase {

    private AppointmentStatusDao appointmentStatusDao;
    private CacheManager cacheManager;
    private Cache appointmentStatusesCache;

    @BeforeEach
    void setUp() {
        appointmentStatusDao = createAndRegisterMock(AppointmentStatusDao.class);
        cacheManager = createAndRegisterMock(CacheManager.class);
        appointmentStatusesCache = mock(Cache.class);
    }

    @Test
    @DisplayName("should clear appointment status cache when legacy dirty flag is set")
    void shouldClearAppointmentStatusCache_whenLegacyDirtyFlagSet() {
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
}
