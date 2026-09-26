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
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.github.carlos_emr.carlos.commn.model.Groups;
import io.github.carlos_emr.carlos.messenger.data.MsgProviderData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

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

    private MsgMessengerAdmin2Action action;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(MessengerGroupManager.class, mockGroupManager);
        replaceSpringUtilsBean(GroupsDao.class, mockGroupsDao);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        action = new MsgMessengerAdmin2Action();
        inject("securityInfoManager", mockSecurityInfoManager);
        inject("messengerGroupManager", mockGroupManager);
        inject("groupsDao", mockGroupsDao);
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
        assertThat(getMockResponse().getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    @DisplayName("should deny default fetch path when _admin read is missing")
    void shouldDenyFetch_whenAdminReadMissing() {
        denyPrivilege("_admin", "r");

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_admin");
    }

    @Test
    @DisplayName("should populate groups + localContacts request attrs on fetch happy path")
    void shouldPopulateFetchAttributes_onAdminReadHappyPath() throws Exception {
        allowPrivilege("_admin", "r");
        Map<Groups, List<MsgProviderData>> groups = new HashMap<>();
        List<MsgProviderData> contacts = List.of();
        when(mockGroupManager.getAllGroupsWithMembers(any())).thenReturn(groups);
        when(mockGroupManager.getAllLocalMessengerContactList(any())).thenReturn(contacts);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(getMockRequest().getAttribute("groups")).isSameAs(groups);
        assertThat(getMockRequest().getAttribute("localContacts")).isSameAs(contacts);
    }

    @Test
    @DisplayName("should invoke addMember on valid add-method POST")
    void shouldInvokeAddMember_onValidAddPost() throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", "add");
        addRequestParameter("member", TEST_PROVIDER + "-0-1");
        addRequestParameter("group", "7");

        when(mockGroupManager.addMemberIfAbsent(any(), any(), eq(7)))
                .thenReturn(new MessengerGroupManager.AddMemberResult(1, true));
        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(mockGroupManager).addMemberIfAbsent(any(), any(), eq(7));
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(getMockResponse().getContentType()).startsWith("application/json");
        assertThat(getMockResponse().getContentAsString()).isEqualTo("{\"success\":true}");
    }

    @Test
    @DisplayName("should answer 409 and write nothing when the contact is already in the group")
    void shouldReturnConflict_whenMemberAlreadyInGroup() throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", "add");
        addRequestParameter("member", TEST_PROVIDER + "-0-1");
        addRequestParameter("group", "7");
        when(mockGroupManager.addMemberIfAbsent(any(), any(), eq(7)))
                .thenReturn(new MessengerGroupManager.AddMemberResult(1, false));

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_CONFLICT);
        assertThat(getMockResponse().getContentAsString()).contains("\"reason\":\"duplicate\"");
        verify(mockGroupManager).addMemberIfAbsent(any(), any(), eq(7));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "-3", "7.5"})
    @DisplayName("should answer 400 and write nothing when the group id is not a non-negative integer")
    void shouldReturnBadRequest_whenGroupIdInvalid(String groupId) throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", "add");
        addRequestParameter("member", TEST_PROVIDER + "-0-1");
        addRequestParameter("group", groupId);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(mockGroupManager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc-def", "-9-0-145", " ", "123-", "123--1", "123-0-1-2-3",
            "123-2147483648", "123-0-2147483648", "123-0-1-2147483648", "1234567", "123-0-x"})
    void shouldRejectMalformedMember_withoutManagerCalls(String member) throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", "add");
        addRequestParameter("member", member);
        addRequestParameter("group", "7");
        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(400);
        assertThat(getMockResponse().getContentAsString()).contains("\"reason\":\"invalid\"");
        verifyNoInteractions(mockGroupManager);
    }

    @Test
    void shouldReturnBadRequest_whenGroupWasDeleted() throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", "add");
        addRequestParameter("member", TEST_PROVIDER + "-0-1");
        addRequestParameter("group", "7");
        when(mockGroupManager.addMemberIfAbsent(any(), any(), eq(7)))
                .thenThrow(new MessengerGroupManager.UnknownGroupException());
        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void shouldPreserveAllFourContactComponents_whenMemberHasFourComponents() throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", "add");
        addRequestParameter("member", "123-4-145-6");
        addRequestParameter("group", "7");
        when(mockGroupManager.addMemberIfAbsent(any(), any(), eq(7)))
                .thenReturn(new MessengerGroupManager.AddMemberResult(1, true));
        executeAction(action);
        var contact = org.mockito.ArgumentCaptor.forClass(io.github.carlos_emr.carlos.messenger.data.ContactIdentifier.class);
        verify(mockGroupManager).addMemberIfAbsent(any(), contact.capture(), eq(7));
        assertThat(contact.getValue().getContactId()).isEqualTo("123");
        assertThat(contact.getValue().getFacilityId()).isEqualTo(4);
        assertThat(contact.getValue().getClinicLocationNo()).isEqualTo(145);
        assertThat(contact.getValue().getGroupId()).isEqualTo(6);
    }

    @Test
    @DisplayName("should answer 400 and write nothing when no member is given")
    void shouldReturnBadRequest_whenMemberMissing() throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", "add");
        addRequestParameter("group", "7");

        executeAction(action);

        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        verifyNoInteractions(mockGroupManager);
    }

    @Test
    @DisplayName("should invoke addGroup on valid create-method POST")
    void shouldInvokeAddGroup_onValidCreatePost() throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", "create");
        addRequestParameter("groupName", "Clinicians");
        addRequestParameter("parentId", "0");
        when(mockGroupManager.getAllGroupsWithMembers(any())).thenReturn(new HashMap<>());
        when(mockGroupManager.getAllLocalMessengerContactList(any())).thenReturn(List.of());

        executeAction(action);

        verify(mockGroupManager).addGroup(any(), eq("Clinicians"), eq(0));
    }

    @Test
    @DisplayName("should invoke removeGroup on valid remove-method POST with group but no member")
    void shouldInvokeRemoveGroup_onValidRemoveGroupPost() throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", "remove");
        addRequestParameter("group", "7");
        when(mockGroupManager.removeGroup(any(), eq(7))).thenReturn(true);

        executeAction(action);

        verify(mockGroupManager).removeGroup(any(), eq(7));
    }

    @ParameterizedTest
    @CsvSource({"remove, abc", "remove, -1", "remove, 2147483648",
            "create, abc", "create, -1", "create, 2147483648",
            "delete, abc", "delete, -1", "delete, 2147483648",
            "update, abc", "update, -1", "update, 2147483648"})
    void shouldRejectMalformedGroupId_withoutMutating(String method, String group) throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", method);
        addRequestParameter("group", group);
        addRequestParameter("parentId", group);
        addRequestParameter("groupName", "Valid");
        action.setGrpNo(group);
        action.setUpdate(java.util.ResourceBundle.getBundle("oscarResources", getMockRequest().getLocale())
                .getString("messenger.config.MessengerAdmin.btnUpdateGroupMembers"));
        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(400);
        verifyNoInteractions(mockGroupManager, mockGroupsDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"123-x", "123-2147483648", "123--1", "-9-0-1"})
    void shouldRejectMalformedRemoval_withoutMutating(String member) throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", "remove");
        addRequestParameter("member", member);
        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(400);
        verifyNoInteractions(mockGroupManager);
    }

    @Test
    void shouldRejectStaleLegacyUpdate_withoutWritingAddressBook() throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", "update");
        action.setGrpNo("7");
        action.setUpdate(java.util.ResourceBundle.getBundle("oscarResources", getMockRequest().getLocale())
                .getString("messenger.config.MessengerAdmin.btnUpdateGroupMembers"));
        doThrow(new MessengerGroupManager.UnknownGroupException()).when(mockGroupManager)
                .replaceGroupMembers(any(), eq(7), any());
        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void shouldRejectMissingLegacyOperation_insteadOfThrowing() throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", "update");
        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(400);
        verifyNoInteractions(mockGroupManager);
    }

    @Test
    void shouldRejectAmbiguousLegacyOperation_withoutChoosingDeletion() throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", "update");
        action.setGrpNo("7");
        var bundle = java.util.ResourceBundle.getBundle("oscarResources", getMockRequest().getLocale());
        action.setUpdate(bundle.getString("messenger.config.MessengerAdmin.btnUpdateGroupMembers"));
        action.setDelete(bundle.getString("messenger.config.MessengerAdmin.btnDeleteThisGroup"));
        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(400);
        verifyNoInteractions(mockGroupManager, mockGroupsDao);
    }

    @Test
    void shouldReturnConflict_whenDeletingGroupWithChildren() throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        addRequestParameter("method", "remove");
        addRequestParameter("group", "7");
        when(mockGroupManager.removeGroup(any(), eq(7))).thenThrow(new MessengerGroupManager.GroupHasChildrenException());
        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(409);
        assertThat(getMockResponse().getContentAsString()).contains("\"reason\":\"children\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRejectLegacyCreation_withoutMutatingOnReadRequest(String method) throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod(method);
        var legacy = new MsgMessengerCreateGroup2Action();
        legacy.setGroupName("Child");
        legacy.setParentID("7");
        legacy.setType2("1");
        assertThat(legacy.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(405);
        verifyNoInteractions(mockGroupManager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2"})
    void shouldHandleMissingGroup_inLegacyCreationAndRename(String operation) throws Exception {
        allowPrivilege("_admin", "w");
        getMockRequest().setMethod("POST");
        var legacy = new MsgMessengerCreateGroup2Action();
        legacy.setGroupName("Child");
        legacy.setParentID("7");
        legacy.setType2(operation);
        if ("1".equals(operation)) {
            when(mockGroupManager.addGroup(any(), any(), eq(7))).thenThrow(new MessengerGroupManager.UnknownGroupException());
        } else {
            doThrow(new MessengerGroupManager.UnknownGroupException()).when(mockGroupManager).renameGroup(any(), eq(7), any());
        }
        assertThat(legacy.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(400);
    }
}
