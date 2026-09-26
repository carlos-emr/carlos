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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.GroupMembersDao;
import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.model.GroupMembers;
import io.github.carlos_emr.carlos.commn.model.OscarCommLocations;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.messenger.data.ContactIdentifier;
import io.github.carlos_emr.carlos.messenger.data.MsgProviderData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Messenger membership rules in {@link MessengerGroupManager} (issue #3964).
 *
 * <p>{@code groupMembers_tbl} has no unique key, so {@code addMember} is the only guard
 * against a second membership row for the same contact and group (which makes every group
 * message reach that provider twice). The contact list must also leave out every negative
 * provider number: {@code -1} is the system account and other negative numbers are how
 * administrators deactivate a provider.</p>
 *
 * @since 2026-09-26
 */
@Tag("unit")
@Tag("manager")
@Tag("messenger")
@ExtendWith(MockitoExtension.class)
@DisplayName("MessengerGroupManager membership rules")
class MessengerGroupManagerMembershipUnitTest {

    private static final String PROVIDER_NO = "101";
    private static final int GROUP_ID = 7;

    @Mock
    private SecurityInfoManager securityInfoManager;
    @Mock
    private GroupMembersDao groupMembersDao;
    @Mock
    private ProviderManager2 providerManager;
    @Mock
    private OscarCommLocationsDao oscarCommLocationsDao;

    @InjectMocks
    private MessengerGroupManager manager;

    private final LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

    @BeforeEach
    void allowAdmin() {
        lenient().when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin"), any(), isNull()))
                .thenReturn(true);
    }

    private static ContactIdentifier contact() {
        return new ContactIdentifier(PROVIDER_NO + "-0-1");
    }

    private static GroupMembers row(int id, int groupId) {
        GroupMembers member = new GroupMembers();
        member.setId(id);
        member.setGroupId(groupId);
        member.setProviderNo(PROVIDER_NO);
        member.setFacilityId(0);
        return member;
    }

    private void stubMembership(int groupId, GroupMembers result) {
        when(groupMembersDao.findByIdentity(argThat(ci -> ci != null
                && PROVIDER_NO.equals(ci.getContactId())
                && ci.getFacilityId() == 0
                && ci.getGroupId() == groupId))).thenReturn(result);
    }

    @Nested
    @DisplayName("addMember")
    class AddMember {

        @Test
        @DisplayName("should return the existing id and write nothing when the contact is already in the group")
        void shouldSkipInsert_whenContactAlreadyInGroup() {
            stubMembership(GROUP_ID, row(42, GROUP_ID));

            int id = manager.addMember(loggedInInfo, contact(), GROUP_ID);

            assertThat(id).isEqualTo(42);
            verify(groupMembersDao, never()).persist(any());
        }

        @Test
        @DisplayName("should not write a second registry row when the contact is already a Messenger member")
        void shouldSkipInsert_whenContactAlreadyRegistered() {
            stubMembership(0, row(5, 0));

            int id = manager.addMember(loggedInInfo, contact(), 0);

            assertThat(id).isEqualTo(5);
            verify(groupMembersDao, never()).persist(any());
        }

        @Test
        @DisplayName("should write only the group row when the contact is registered but not in the group")
        void shouldPersistGroupRowOnly_whenRegisteredButNotInGroup() {
            stubMembership(GROUP_ID, null);
            stubMembership(0, row(5, 0));

            manager.addMember(loggedInInfo, contact(), GROUP_ID);

            ArgumentCaptor<GroupMembers> saved = ArgumentCaptor.forClass(GroupMembers.class);
            verify(groupMembersDao, times(1)).persist(saved.capture());
            assertThat(saved.getValue().getGroupId()).isEqualTo(GROUP_ID);
            assertThat(saved.getValue().getProviderNo()).isEqualTo(PROVIDER_NO);
        }

        @Test
        @DisplayName("should write the registry row and the group row for a brand-new member")
        void shouldPersistRegistryAndGroupRows_forNewMember() {
            stubMembership(GROUP_ID, null);
            stubMembership(0, null);

            manager.addMember(loggedInInfo, contact(), GROUP_ID);

            ArgumentCaptor<GroupMembers> saved = ArgumentCaptor.forClass(GroupMembers.class);
            verify(groupMembersDao, times(2)).persist(saved.capture());
            assertThat(saved.getAllValues()).extracting(GroupMembers::getGroupId).containsExactly(0, GROUP_ID);
        }

        @Test
        @DisplayName("should leave the caller's contact identifier group id unchanged")
        void shouldNotMutateCallerIdentifier_duringLookup() {
            stubMembership(GROUP_ID, null);
            stubMembership(0, null);
            ContactIdentifier contact = contact();
            contact.setGroupId(3);

            manager.addMember(loggedInInfo, contact, GROUP_ID);

            assertThat(contact.getGroupId()).isEqualTo(3);
        }

        @Test
        @DisplayName("should refuse to add without _admin write")
        void shouldThrowSecurityException_whenAdminWriteMissing() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.WRITE, null))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.addMember(loggedInInfo, contact(), GROUP_ID))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_admin)");
            verify(groupMembersDao, never()).persist(any());
        }
    }

    @Nested
    @DisplayName("isGroupMember")
    class IsGroupMember {

        @Test
        @DisplayName("should report true when a membership row exists")
        void shouldReturnTrue_whenMembershipRowExists() {
            stubMembership(GROUP_ID, row(42, GROUP_ID));

            assertThat(manager.isGroupMember(loggedInInfo, contact(), GROUP_ID)).isTrue();
        }

        @Test
        @DisplayName("should report false when no membership row exists")
        void shouldReturnFalse_whenNoMembershipRow() {
            stubMembership(GROUP_ID, null);

            assertThat(manager.isGroupMember(loggedInInfo, contact(), GROUP_ID)).isFalse();
        }

        @Test
        @DisplayName("should refuse the lookup without _admin read")
        void shouldThrowSecurityException_whenAdminReadMissing() {
            when(securityInfoManager.hasPrivilege(loggedInInfo, "_admin", SecurityInfoManager.READ, null))
                    .thenReturn(false);

            assertThatThrownBy(() -> manager.isGroupMember(loggedInInfo, contact(), GROUP_ID))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_admin)");
        }
    }

    @Nested
    @DisplayName("getAllLocalMessengerContactList")
    class ContactList {

        private Provider provider(String providerNo, String lastName) {
            Provider provider = new Provider();
            provider.setProviderNo(providerNo);
            provider.setLastName(lastName);
            provider.setFirstName("Test");
            return provider;
        }

        @Test
        @DisplayName("should leave out the system account and every deactivated (negative) provider number")
        void shouldExcludeNegativeProviderNumbers_fromContactList() {
            OscarCommLocations location = new OscarCommLocations();
            location.setId(1);
            when(oscarCommLocationsDao.findByCurrent1(1)).thenReturn(List.of(location));
            when(groupMembersDao.getCountAll()).thenReturn(0);
            when(providerManager.getProviders(loggedInInfo, Boolean.TRUE)).thenReturn(List.of(
                    provider("-1", "System"),
                    provider("-101", "Deactivated"),
                    provider("-7", "Retired"),
                    provider("101", "Active"),
                    provider("202", "")));

            List<MsgProviderData> contacts = manager.getAllLocalMessengerContactList(loggedInInfo);

            assertThat(contacts).extracting(c -> c.getId().getContactId()).containsExactly("101");
        }

        @Test
        @DisplayName("should still build the list when no current comm location is configured")
        void shouldUseLocationZero_whenNoCurrentLocation() {
            when(oscarCommLocationsDao.findByCurrent1(1)).thenReturn(List.of());
            when(groupMembersDao.getCountAll()).thenReturn(0);
            when(providerManager.getProviders(loggedInInfo, Boolean.TRUE))
                    .thenReturn(List.of(provider("101", "Active")));

            List<MsgProviderData> contacts = manager.getAllLocalMessengerContactList(loggedInInfo);

            assertThat(contacts).singleElement()
                    .satisfies(c -> assertThat(c.getId().getClinicLocationNo()).isZero());
        }
    }

    @Nested
    @DisplayName("isContactableProviderNo")
    class ContactableProviderNo {

        @ParameterizedTest
        @ValueSource(strings = {"-1", "-101", "-"})
        @DisplayName("should reject negative provider numbers")
        void shouldReturnFalse_forNegativeProviderNumbers(String providerNo) {
            assertThat(MessengerGroupManager.isContactableProviderNo(providerNo)).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("should reject a missing provider number")
        void shouldReturnFalse_forMissingProviderNumber(String providerNo) {
            assertThat(MessengerGroupManager.isContactableProviderNo(providerNo)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"101", "999998", "0"})
        @DisplayName("should accept ordinary provider numbers")
        void shouldReturnTrue_forOrdinaryProviderNumbers(String providerNo) {
            assertThat(MessengerGroupManager.isContactableProviderNo(providerNo)).isTrue();
        }
    }
}
