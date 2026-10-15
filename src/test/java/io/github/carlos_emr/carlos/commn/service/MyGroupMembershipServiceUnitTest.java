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
package io.github.carlos_emr.carlos.commn.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.dao.MyGroupDao;
import io.github.carlos_emr.carlos.commn.model.MyGroupPrimaryKey;
import io.github.carlos_emr.carlos.commn.service.MyGroupMembershipService.SelectedMember;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MyGroupMembershipService}: how a group list form's parameters are read as
 * memberships (issue #4515, where the CSRFGuard token was parsed as one and the delete answered
 * HTTP 500), and how the delete reports what it removed.
 *
 * @since 2026-10-15
 */
@DisplayName("MyGroupMembershipService")
@Tag("unit")
@Tag("service")
class MyGroupMembershipServiceUnitTest {

    /** A CSRFGuard token is a long random value that is never a prefix of its own name. */
    private static final String CSRF_TOKEN = "Q1W2-E3R4-T5Y6-U7I8-O9P0-A1S2-D3F4-G5H6";

    private static Map<String, String[]> form(Object... nameValuePairs) {
        Map<String, String[]> parameters = new LinkedHashMap<>();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            Object value = nameValuePairs[i + 1];
            parameters.put((String) nameValuePairs[i],
                    value instanceof String[] values ? values : new String[] {(String) value});
        }
        return parameters;
    }

    @Nested
    @DisplayName("selectedMembers")
    class SelectedMembers {

        @Test
        @DisplayName("should ignore the CSRF token, submit button and legacy control fields")
        void shouldIgnoreControlFields_whenFormPostsTokenAndButtons() {
            Map<String, String[]> parameters = form(
                    "CSRF-TOKEN", CSRF_TOKEN,
                    "submit", "Delete",
                    "submit_form", "Delete",
                    "displaymode", "newgroup",
                    "PWa1b2c3d4999998", "PWa1b2c3d4");

            assertThat(MyGroupMembershipService.selectedMembers(parameters))
                    .containsExactly(new SelectedMember("PWa1b2c3d4", "999998"));
        }

        @Test
        @DisplayName("should read nothing when only control fields are posted")
        void shouldSelectNothing_whenOnlyControlFieldsArePosted() {
            assertThat(MyGroupMembershipService.selectedMembers(
                    form("CSRF-TOKEN", CSRF_TOKEN, "submit", "Delete"))).isEmpty();
        }

        @Test
        @DisplayName("should read the provider number as the part of the name after the group")
        void shouldReadProviderNumber_fromNameAfterGroupValue() {
            assertThat(MyGroupMembershipService.selectedMembers(form(
                    "Doctors101", "Doctors",
                    "1-1", "1",
                    "Night shift999998", "Night shift")))
                    .containsExactly(
                            new SelectedMember("Doctors", "101"),
                            new SelectedMember("1", "-1"),
                            new SelectedMember("Night shift", "999998"));
        }

        @Test
        @DisplayName("should read both memberships when two checkboxes share one name")
        void shouldReadBothMembers_whenTwoCheckboxesShareOneName() {
            // Group "AB" holding provider "C1" and group "ABC" holding provider "1".
            assertThat(MyGroupMembershipService.selectedMembers(
                    form("ABC1", new String[] {"AB", "ABC"})))
                    .containsExactly(new SelectedMember("AB", "C1"), new SelectedMember("ABC", "1"));
        }

        @Test
        @DisplayName("should skip values that do not start the name, are empty or equal it")
        void shouldSkipValue_whenItIsNotAProperPrefixOfTheName() {
            assertThat(MyGroupMembershipService.selectedMembers(form(
                    "GRP101", "OTHER",
                    "GRP102", "",
                    "GRP103", "GRP103",
                    "GRP104", new String[] {null},
                    "GRP105", "grp")))
                    .isEmpty();
        }

        @Test
        @DisplayName("should read a membership once when it is posted twice")
        void shouldReadMemberOnce_whenPostedTwice() {
            assertThat(MyGroupMembershipService.selectedMembers(
                    form("GRP101", new String[] {"GRP", "GRP"})))
                    .containsExactly(new SelectedMember("GRP", "101"));
        }

        @Test
        @DisplayName("should select nothing for a missing parameter map")
        void shouldSelectNothing_forNullParameters() {
            assertThat(MyGroupMembershipService.selectedMembers(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteSelectedMembers")
    class DeleteSelectedMembers {

        private MyGroupDao myGroupDao;
        private MyGroupMembershipService service;

        @BeforeEach
        void setUp() {
            myGroupDao = mock(MyGroupDao.class);
            service = new MyGroupMembershipService(myGroupDao);
        }

        @Test
        @DisplayName("should remove each selected membership by its composite key")
        void shouldRemoveEachSelectedMember_byCompositeKey() {
            when(myGroupDao.remove(any(Object.class))).thenReturn(true);

            int deleted = service.deleteSelectedMembers(form(
                    "CSRF-TOKEN", CSRF_TOKEN,
                    "submit", "Delete",
                    "GRP101", "GRP",
                    "GRP999998", "GRP"));

            assertThat(deleted).isEqualTo(2);
            ArgumentCaptor<Object> keys = ArgumentCaptor.forClass(Object.class);
            verify(myGroupDao, times(2)).remove(keys.capture());
            List<String> removed = keys.getAllValues().stream()
                    .map(MyGroupPrimaryKey.class::cast)
                    .map(key -> key.getMyGroupNo() + "/" + key.getProviderNo())
                    .toList();
            assertThat(removed).containsExactly("GRP/101", "GRP/999998");
        }

        @Test
        @DisplayName("should count only rows that existed")
        void shouldCountOnlyExistingRows_whenSelectionMatchesNoRow() {
            when(myGroupDao.remove(argThat((Object key) -> key instanceof MyGroupPrimaryKey k
                    && "101".equals(k.getProviderNo())))).thenReturn(true);

            int deleted = service.deleteSelectedMembers(form("GRP101", "GRP", "GRP102", "GRP"));

            assertThat(deleted).isEqualTo(1);
            verify(myGroupDao, times(2)).remove(any(Object.class));
        }

        @Test
        @DisplayName("should not touch the DAO when nothing is selected")
        void shouldNotTouchDao_whenNothingIsSelected() {
            int deleted = service.deleteSelectedMembers(form("CSRF-TOKEN", CSRF_TOKEN, "submit", "Delete"));

            assertThat(deleted).isZero();
            verifyNoInteractions(myGroupDao);
        }
    }
}
