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
package io.github.carlos_emr.carlos.messenger.config.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.GroupMembersDao;
import io.github.carlos_emr.carlos.commn.dao.GroupsDao;
import io.github.carlos_emr.carlos.managers.MessengerGroupManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MsgMessengerAdmin2Action} privilege + method gating.
 *
 * <p>The action routes on a {@code method} request parameter. Each mutating
 * value (add/remove/create/delete/update) must require {@code _admin} write
 * and POST; the default fetch path must require {@code _admin} read. A missed
 * branch would let an authenticated non-admin either enumerate or alter
 * messenger groups.
 *
 * @since 2026-04-13
 */
@DisplayName("MsgMessengerAdmin2Action Tests")
@Tag("integration")
@Tag("messenger")
class MsgMessengerAdmin2ActionTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";

    @Mock
    private MessengerGroupManager mockGroupManager;
    @Mock
    private GroupsDao mockGroupsDao;
    @Mock
    private GroupMembersDao mockGroupMembersDao;

    private MsgMessengerAdmin2Action action;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(MessengerGroupManager.class, mockGroupManager);
        replaceSpringUtilsBean(GroupsDao.class, mockGroupsDao);
        replaceSpringUtilsBean(GroupMembersDao.class, mockGroupMembersDao);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        action = new MsgMessengerAdmin2Action();
        inject("securityInfoManager", mockSecurityInfoManager);
        inject("messengerGroupManager", mockGroupManager);
        inject("groupsDao", mockGroupsDao);
        inject("groupMembersDao", mockGroupMembersDao);
    }

    private void inject(String field, Object value) throws Exception {
        java.lang.reflect.Field f = MsgMessengerAdmin2Action.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(action, value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"add", "remove", "create", "delete", "update"})
    @DisplayName("should deny each mutating method when _admin write is missing")
    void shouldDenyMutation_whenAdminWriteMissing(String method) {
        denyPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", method);

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin");
    }

    @ParameterizedTest
    @ValueSource(strings = {"add", "remove", "create", "delete", "update"})
    @DisplayName("should reject each mutating method when HTTP is not POST")
    void shouldReject405_whenMutationMethodNotPost(String method) throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("GET");
        addRequestParameter("method", method);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("should deny default fetch path when _admin read is missing")
    void shouldDenyFetch_whenAdminReadMissing() {
        denyPrivilege("_admin", "r");

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin");
    }
}
