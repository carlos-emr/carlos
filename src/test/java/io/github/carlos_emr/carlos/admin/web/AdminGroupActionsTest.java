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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.admin.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import java.util.Map;

import io.github.carlos_emr.carlos.commn.service.MyGroupMembershipService;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import jakarta.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for schedule provider-group creation gates, and for the Delete of
 * Search/Edit/Delete Groups, which {@link AdminNewGroup2Action} performs (issue #4515).
 *
 * @since 2026-05-21
 */
@DisplayName("Admin group action security gates")
@Tag("unit")
@Tag("admin")
@Tag("security")
class AdminGroupActionsTest extends CarlosWebTestBase {

    private static final String SCHEDULE_GROUP_CREATE_OBJECT = "_admin.schedule.groupCreate";
    private static final Path LEGACY_ADMIN_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/admin/admin.jsp");
    private static final Path NEW_GROUP_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/admin/adminnewgroup.jsp");
    private static final Path ADMINISTRATION_LEFT_NAV =
            Path.of("src/main/webapp/WEB-INF/jsp/administration/leftNav.jspf");
    private static final Pattern RESIZE_HELPER_FUNCTION_GUARD = Pattern.compile(
            "typeof\\s+parent\\.parent\\.resizeIframe\\s*={2,3}\\s*(['\"])function\\1");
    private static final Path GROUP_LIST_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/admin/admindisplaymygroup.jsp");

    private MyGroupMembershipService membershipService;

    @BeforeEach
    void setUpMembershipService() {
        membershipService = mock(MyGroupMembershipService.class);
    }

    private AdminNewGroup2Action newGroupAction() {
        return new AdminNewGroup2Action(mockSecurityInfoManager, membershipService);
    }

    @Test
    @DisplayName("should navigate to group create form without mutation parameters")
    void shouldNavigateWithoutMutationParameters_toGroupCreateForm() throws IOException {
        String legacyAdmin = Files.readString(LEGACY_ADMIN_JSP, StandardCharsets.UTF_8);
        String administrationLeftNav =
                Files.readString(ADMINISTRATION_LEFT_NAV, StandardCharsets.UTF_8);

        assertThat(legacyAdmin)
                .contains("${pageContext.request.contextPath}/admin/AdminNewGroup")
                .doesNotContain("AdminNewGroup?submit=");
        assertThat(administrationLeftNav)
                .contains("${ctx}/admin/AdminNewGroup")
                .doesNotContain("AdminNewGroup?submit=");
    }

    @Test
    @DisplayName("should tolerate administration shells without legacy resize helper")
    void shouldTolerateMissingResizeHelper_whenGroupCreateFormLoads() throws IOException {
        String newGroup = Files.readString(NEW_GROUP_JSP, StandardCharsets.UTF_8);

        assertThat(newGroup).containsPattern(RESIZE_HELPER_FUNCTION_GUARD);
    }

    @Test
    @DisplayName("should return success when group create privilege is granted")
    void shouldReturnSuccess_whenGroupCreatePrivilegeGranted() throws Exception {
        assertThat(executeAction(newGroupAction())).isEqualTo(ActionSupport.SUCCESS);

        verifySecurityCheck(SCHEDULE_GROUP_CREATE_OBJECT, "w");
        // Opening the form is not a delete.
        verifyNoInteractions(membershipService);
        assertThat(mockRequest.getAttribute(AdminNewGroup2Action.MEMBERS_DELETED_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("should reject view when group create privilege is missing")
    void shouldRejectView_whenGroupCreatePrivilegeMissing() {
        denyPrivilege(SCHEDULE_GROUP_CREATE_OBJECT, "w");

        AdminNewGroup2Action action = newGroupAction();
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining(SCHEDULE_GROUP_CREATE_OBJECT);
    }

    @Test
    @DisplayName("should delete the selected members when the group list posts Delete with its CSRF token")
    void shouldDeleteSelectedMembers_whenDeleteIsPosted() throws Exception {
        mockRequest.setMethod("POST");
        mockRequest.addParameter("PWa1b2c3d4999998", "PWa1b2c3d4");
        mockRequest.addParameter("submit", "Delete");
        mockRequest.addParameter("CSRF-TOKEN", "Q1W2-E3R4-T5Y6-U7I8-O9P0-A1S2-D3F4-G5H6");
        when(membershipService.deleteSelectedMembers(anyMap())).thenReturn(1);

        assertThat(executeAction(newGroupAction())).isEqualTo(ActionSupport.SUCCESS);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String[]>> posted = ArgumentCaptor.forClass(Map.class);
        verify(membershipService).deleteSelectedMembers(posted.capture());
        // The whole form is handed over; the service, not the action, ignores the control fields.
        assertThat(posted.getValue()).containsKeys("PWa1b2c3d4999998", "submit", "CSRF-TOKEN");
        assertThat(mockRequest.getAttribute(AdminNewGroup2Action.MEMBERS_DELETED_ATTRIBUTE)).isEqualTo(1);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verifySecurityCheck(SCHEDULE_GROUP_CREATE_OBJECT, "w");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should refuse a Delete that is not a POST without deleting anything")
    void shouldReturn405WithoutDeleting_whenDeleteArrivesWithoutPost(String method) throws Exception {
        mockRequest.setMethod(method);
        mockRequest.addParameter("PWa1b2c3d4999998", "PWa1b2c3d4");
        mockRequest.addParameter("submit", "Delete");

        assertThat(executeAction(newGroupAction())).isEqualTo(ActionSupport.NONE);

        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(membershipService);
        assertThat(mockRequest.getAttribute(AdminNewGroup2Action.MEMBERS_DELETED_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("should refuse a Delete without deleting anything when group create privilege is missing")
    void shouldRejectDeleteWithoutDeleting_whenGroupCreatePrivilegeMissing() {
        denyPrivilege(SCHEDULE_GROUP_CREATE_OBJECT, "w");
        mockRequest.setMethod("POST");
        mockRequest.addParameter("PWa1b2c3d4999998", "PWa1b2c3d4");
        mockRequest.addParameter("submit", "Delete");

        AdminNewGroup2Action action = newGroupAction();
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining(SCHEDULE_GROUP_CREATE_OBJECT);
        verifyNoInteractions(membershipService);
    }

    @Test
    @DisplayName("should leave reading members out of the JSP and post the list's Delete to the action")
    void shouldKeepDeleteOutOfJsp_forGroupPages() throws IOException {
        String newGroup = Files.readString(NEW_GROUP_JSP, StandardCharsets.UTF_8);
        String groupList = Files.readString(GROUP_LIST_JSP, StandardCharsets.UTF_8);

        // The scriptlet that cut every parameter name at its value's length is gone (#4515).
        assertThat(newGroup)
                .doesNotContain("getParameterNames")
                .doesNotContain("deleteGroupMember")
                .contains("AdminNewGroup2Action.MEMBERS_DELETED_ATTRIBUTE")
                // The deletion notice has no group name field; onload focus must not throw on it.
                .contains("var groupName = document.UPDATEPRE && document.UPDATEPRE.mygroup_no;");
        assertThat(groupList)
                .contains("ACTION=\"${pageContext.request.contextPath}/admin/AdminNewGroup\"")
                .contains("name=\"submit\"");
    }

    @Test
    @DisplayName("should reject save when group create privilege is missing")
    void shouldRejectSave_whenGroupCreatePrivilegeMissing() {
        denyPrivilege(SCHEDULE_GROUP_CREATE_OBJECT, "w");
        mockRequest.setMethod("POST");

        AdminSaveMyGroup2Action action = new AdminSaveMyGroup2Action();
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining(SCHEDULE_GROUP_CREATE_OBJECT);
    }

    @Test
    @DisplayName("should return success when saving with POST and group create privilege")
    void shouldReturnSuccess_whenSavingWithPost() throws Exception {
        mockRequest.setMethod("POST");

        assertThat(executeAction(new AdminSaveMyGroup2Action())).isEqualTo(ActionSupport.SUCCESS);

        verifySecurityCheck(SCHEDULE_GROUP_CREATE_OBJECT, "w");
    }

    @Test
    @DisplayName("should return 405 when saving with GET")
    void shouldReturn405_whenSavingWithGet() throws Exception {
        mockRequest.setMethod("GET");

        assertThat(executeAction(new AdminSaveMyGroup2Action())).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);

        // Security is checked before method validation so unauthorized callers do not learn route behavior.
        verifySecurityCheck(SCHEDULE_GROUP_CREATE_OBJECT, "w");
    }
}
