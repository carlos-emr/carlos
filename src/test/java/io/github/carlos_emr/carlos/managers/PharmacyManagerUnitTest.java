/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.DemographicPharmacyDao;
import io.github.carlos_emr.carlos.commn.dao.PharmacyInfoDao;
import io.github.carlos_emr.carlos.commn.model.DemographicPharmacy;
import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PharmacyManager} drug dispensing operations.
 *
 * @since 2026-03-31
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PharmacyManager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("pharmacy")
class PharmacyManagerUnitTest extends CarlosUnitTestBase {

    @Mock private DemographicPharmacyDao mockDemographicPharmacyDao;
    @Mock private PharmacyInfoDao mockPharmacyInfoDao;

    private PharmacyManager manager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        registerMock(DemographicPharmacyDao.class, mockDemographicPharmacyDao);
        registerMock(PharmacyInfoDao.class, mockPharmacyInfoDao);

        manager = new PharmacyManager();
        injectDependency(manager, "demographicPharmacyDao", mockDemographicPharmacyDao);
        injectDependency(manager, "pharmacyInfoDao", mockPharmacyInfoDao);

        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
    }

    private DemographicPharmacy createDemographicPharmacy(int id, int demoNo, int pharmacyId) {
        DemographicPharmacy dp = new DemographicPharmacy();
        dp.setId(id);
        dp.setDemographicNo(demoNo);
        dp.setPharmacyId(pharmacyId);
        dp.setStatus("1");
        return dp;
    }

    @Nested
    @DisplayName("getPharmacies")
    class GetPharmacies {

        @Test
        @DisplayName("should return pharmacies with details for demographic")
        void shouldReturnPharmacies_withDetailsForDemographic() {
            DemographicPharmacy dp = createDemographicPharmacy(1, 100, 50);
            when(mockDemographicPharmacyDao.findAllByDemographicId(100)).thenReturn(List.of(dp));
            when(mockPharmacyInfoDao.getPharmacy(50)).thenReturn(new PharmacyInfo());

            List<DemographicPharmacy> result = manager.getPharmacies(loggedInInfo, 100);

            assertThat(result).hasSize(1);
            verify(mockPharmacyInfoDao).getPharmacy(50);
        }

        @Test
        @DisplayName("should return null when DAO returns null")
        void shouldReturnNull_whenDaoReturnsNull() {
            when(mockDemographicPharmacyDao.findAllByDemographicId(999)).thenReturn(null);

            List<DemographicPharmacy> result = manager.getPharmacies(loggedInInfo, 999);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("getDemographicPharmacy")
    class GetDemographicPharmacy {

        @Test
        @DisplayName("should return pharmacy by ID with details")
        void shouldReturnPharmacy_byIdWithDetails() {
            DemographicPharmacy dp = createDemographicPharmacy(1, 100, 50);
            when(mockDemographicPharmacyDao.find(1)).thenReturn(dp);
            when(mockPharmacyInfoDao.getPharmacy(50)).thenReturn(new PharmacyInfo());

            DemographicPharmacy result = manager.getDemographicPharmacy(loggedInInfo, 1);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null when not found")
        void shouldReturnNull_whenNotFound() {
            when(mockDemographicPharmacyDao.find(999)).thenReturn(null);

            DemographicPharmacy result = manager.getDemographicPharmacy(loggedInInfo, 999);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("addPharmacy")
    class AddPharmacy {

        @Test
        @DisplayName("should add pharmacy to demographic")
        void shouldAddPharmacy_toDemographic() {
            DemographicPharmacy expected = createDemographicPharmacy(1, 100, 50);
            when(mockDemographicPharmacyDao.addPharmacyToDemographic(50, 100, 1)).thenReturn(expected);

            DemographicPharmacy result = manager.addPharmacy(loggedInInfo, 100, 50, 1);

            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("removePharmacy")
    class RemovePharmacy {

        @Test
        @DisplayName("should set status to inactive when removing")
        void shouldSetStatusToInactive_whenRemoving() {
            DemographicPharmacy dp = createDemographicPharmacy(1, 100, 50);
            when(mockDemographicPharmacyDao.find(1)).thenReturn(dp);

            manager.removePharmacy(loggedInInfo, 100, 1);

            assertThat(dp.getStatus()).isEqualTo("0");
            verify(mockDemographicPharmacyDao).saveEntity(dp);
        }

        @Test
        @DisplayName("should throw when pharmacy not found")
        void shouldThrow_whenPharmacyNotFound() {
            when(mockDemographicPharmacyDao.find(999)).thenReturn(null);

            assertThatThrownBy(() -> manager.removePharmacy(loggedInInfo, 100, 999))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unable to locate");
        }

        @Test
        @DisplayName("should throw when pharmacy belongs to different demographic")
        void shouldThrow_whenPharmacyBelongsToDifferentDemographic() {
            DemographicPharmacy dp = createDemographicPharmacy(1, 200, 50);
            when(mockDemographicPharmacyDao.find(1)).thenReturn(dp);

            assertThatThrownBy(() -> manager.removePharmacy(loggedInInfo, 100, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("doesn't belong");
        }
    }

    @Nested
    @DisplayName("getPharmacy")
    class GetPharmacy {

        @Test
        @DisplayName("should return pharmacy info by ID")
        void shouldReturnPharmacyInfo_byId() {
            PharmacyInfo expected = new PharmacyInfo();
            when(mockPharmacyInfoDao.find(42)).thenReturn(expected);

            PharmacyInfo result = manager.getPharmacy(loggedInInfo, 42);

            assertThat(result).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("savePharmacyInfo")
    class SavePharmacyInfo {

        @Test
        @DisplayName("should persist new pharmacy when ID is null")
        void shouldPersistNew_whenIdIsNull() {
            PharmacyInfo info = new PharmacyInfo();
            info.setId(null);

            manager.savePharmacyInfo(loggedInInfo, info);

            verify(mockPharmacyInfoDao).persist(info);
            verify(mockPharmacyInfoDao, never()).merge(any());
        }

        @Test
        @DisplayName("should persist new pharmacy when ID is zero")
        void shouldPersistNew_whenIdIsZero() {
            PharmacyInfo info = new PharmacyInfo();
            info.setId(0);

            manager.savePharmacyInfo(loggedInInfo, info);

            verify(mockPharmacyInfoDao).persist(info);
        }

        @Test
        @DisplayName("should merge existing pharmacy when ID is positive")
        void shouldMergeExisting_whenIdIsPositive() {
            PharmacyInfo info = new PharmacyInfo();
            info.setId(42);

            manager.savePharmacyInfo(loggedInInfo, info);

            verify(mockPharmacyInfoDao).merge(info);
            verify(mockPharmacyInfoDao, never()).persist(any());
        }
    }

    @Nested
    @DisplayName("setDoNotContact")
    class SetDoNotContact {

        @Test
        @DisplayName("should toggle consent to contact flag")
        void shouldToggleConsentFlag() {
            DemographicPharmacy dp = createDemographicPharmacy(1, 100, 50);
            when(mockDemographicPharmacyDao.find(1)).thenReturn(dp);

            manager.setDoNotContact(loggedInInfo, 1, true);

            assertThat(dp.isConsentToContact()).isTrue();
            verify(mockDemographicPharmacyDao).saveEntity(dp);
        }
    }
}
