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

import io.github.carlos_emr.carlos.commn.dao.LookupListDao;
import io.github.carlos_emr.carlos.commn.dao.LookupListItemDao;
import io.github.carlos_emr.carlos.commn.model.LookupList;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
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
 * Unit tests for {@link LookupListManager} admin lookup list CRUD operations.
 *
 * @since 2026-03-31
 * @see LookupListManager
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LookupListManager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("admin")
class LookupListManagerUnitTest extends CarlosUnitTestBase {

    @Mock private LookupListDao mockLookupListDao;
    @Mock private LookupListItemDao mockLookupListItemDao;
    @Mock private SecurityInfoManager mockSecurityInfoManager;

    private LookupListManager manager;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        registerMock(LookupListDao.class, mockLookupListDao);
        registerMock(LookupListItemDao.class, mockLookupListItemDao);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);

        manager = new LookupListManager();
        injectDependency(manager, "lookupListDao", mockLookupListDao);
        injectDependency(manager, "lookupListItemDao", mockLookupListItemDao);
        injectDependency(manager, "securityInfoManager", mockSecurityInfoManager);

        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        // Grant admin write/update/delete by default
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), anyString(), isNull()))
                .thenReturn(true);
    }

    // -----------------------------------------------------------------------
    // findAllActiveLookupLists
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findAllActiveLookupLists")
    class FindAllActiveLookupLists {

        @Test
        @DisplayName("should return all active lookup lists")
        void shouldReturnAllActiveLists() {
            LookupList list = new LookupList();
            list.setId(1);
            when(mockLookupListDao.findAllActive()).thenReturn(List.of(list));

            List<LookupList> result = manager.findAllActiveLookupLists(loggedInInfo);

            assertThat(result).hasSize(1);
        }
    }

    // -----------------------------------------------------------------------
    // findLookupListByName
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findLookupListByName")
    class FindLookupListByName {

        @Test
        @DisplayName("should return list when found by name")
        void shouldReturnList_whenFoundByName() {
            LookupList expected = new LookupList();
            expected.setName("Salutations");
            when(mockLookupListDao.findByName("Salutations")).thenReturn(expected);

            LookupList result = manager.findLookupListByName(loggedInInfo, "Salutations");

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("should return null when not found")
        void shouldReturnNull_whenNotFound() {
            when(mockLookupListDao.findByName("NonExistent")).thenReturn(null);

            LookupList result = manager.findLookupListByName(loggedInInfo, "NonExistent");

            assertThat(result).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // addLookupList
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("addLookupList")
    class AddLookupList {

        @Test
        @DisplayName("should persist and return lookup list")
        void shouldPersistAndReturn() {
            LookupList list = new LookupList();
            list.setName("NewList");

            LookupList result = manager.addLookupList(loggedInInfo, list);

            assertThat(result).isSameAs(list);
            verify(mockLookupListDao).persist(list);
        }

        @Test
        @DisplayName("should throw when admin write privilege denied")
        void shouldThrow_whenAdminWriteDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq(SecurityInfoManager.WRITE), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.addLookupList(loggedInInfo, new LookupList()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Access Denied");
        }
    }

    // -----------------------------------------------------------------------
    // addLookupListItem
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("addLookupListItem")
    class AddLookupListItem {

        @Test
        @DisplayName("should persist and return item")
        void shouldPersistAndReturn() {
            LookupListItem item = new LookupListItem();
            item.setLabel("Option A");

            LookupListItem result = manager.addLookupListItem(loggedInInfo, item);

            assertThat(result).isSameAs(item);
            verify(mockLookupListItemDao).persist(item);
        }

        @Test
        @DisplayName("should throw when admin write privilege denied")
        void shouldThrow_whenWriteDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq(SecurityInfoManager.WRITE), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.addLookupListItem(loggedInInfo, new LookupListItem()))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // -----------------------------------------------------------------------
    // findLookupListItemsByLookupListId
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findLookupListItemsByLookupListId")
    class FindItemsByListId {

        @Test
        @DisplayName("should return active items for lookup list")
        void shouldReturnActiveItems() {
            LookupListItem item = new LookupListItem();
            when(mockLookupListItemDao.findActiveByLookupListId(1)).thenReturn(List.of(item));

            List<LookupListItem> result = manager.findLookupListItemsByLookupListId(loggedInInfo, 1);

            assertThat(result).hasSize(1);
        }
    }

    // -----------------------------------------------------------------------
    // findLookupListItemById
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("findLookupListItemById")
    class FindItemById {

        @Test
        @DisplayName("should return item when ID is positive")
        void shouldReturnItem_whenIdPositive() {
            LookupListItem expected = new LookupListItem();
            when(mockLookupListItemDao.find(42)).thenReturn(expected);

            LookupListItem result = manager.findLookupListItemById(loggedInInfo, 42);

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("should return null when ID is zero or negative")
        void shouldReturnNull_whenIdZeroOrNegative() {
            LookupListItem result = manager.findLookupListItemById(loggedInInfo, 0);

            assertThat(result).isNull();
            verify(mockLookupListItemDao, never()).find(anyInt());
        }
    }

    // -----------------------------------------------------------------------
    // updateLookupListItem
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("updateLookupListItem")
    class UpdateItem {

        @Test
        @DisplayName("should merge item and return ID")
        void shouldMergeAndReturnId() {
            LookupListItem item = new LookupListItem();
            item.setId(42);

            Integer result = manager.updateLookupListItem(loggedInInfo, item);

            assertThat(result).isEqualTo(42);
            verify(mockLookupListItemDao).merge(item);
        }

        @Test
        @DisplayName("should throw when admin update privilege denied")
        void shouldThrow_whenUpdateDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq(SecurityInfoManager.UPDATE), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.updateLookupListItem(loggedInInfo, new LookupListItem()))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // -----------------------------------------------------------------------
    // removeLookupListItem
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("removeLookupListItem")
    class RemoveItem {

        @Test
        @DisplayName("should set item inactive and return true")
        void shouldSetInactiveAndReturnTrue() {
            LookupListItem item = new LookupListItem();
            item.setId(42);
            item.setActive(true);
            when(mockLookupListItemDao.find(42)).thenReturn(item);

            boolean result = manager.removeLookupListItem(loggedInInfo, 42);

            assertThat(result).isTrue();
            assertThat(item.getActive()).isFalse();
            verify(mockLookupListItemDao).merge(item);
        }

        @Test
        @DisplayName("should return false when item not found")
        void shouldReturnFalse_whenItemNotFound() {
            // ID 0 won't even call find()
            boolean result = manager.removeLookupListItem(loggedInInfo, 0);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should throw when admin delete privilege denied")
        void shouldThrow_whenDeleteDenied() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_admin"), eq(SecurityInfoManager.DELETE), isNull()))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.removeLookupListItem(loggedInInfo, 42))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // -----------------------------------------------------------------------
    // updateLookupListItemDisplayOrder
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("updateLookupListItemDisplayOrder")
    class UpdateDisplayOrder {

        @Test
        @DisplayName("should update display order and return true")
        void shouldUpdateDisplayOrderAndReturnTrue() {
            LookupListItem item = new LookupListItem();
            item.setId(42);
            item.setDisplayOrder(1);
            when(mockLookupListItemDao.find(42)).thenReturn(item);

            boolean result = manager.updateLookupListItemDisplayOrder(loggedInInfo, 42, 5);

            assertThat(result).isTrue();
            assertThat(item.getDisplayOrder()).isEqualTo(5);
        }

        @Test
        @DisplayName("should return false when item not found")
        void shouldReturnFalse_whenItemNotFound() {
            boolean result = manager.updateLookupListItemDisplayOrder(loggedInInfo, 0, 5);

            assertThat(result).isFalse();
        }
    }
}
