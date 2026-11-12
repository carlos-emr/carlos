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
package io.github.carlos_emr.carlos.providers.pageUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link DeleteMyGroupMembers2Action}, Preferences &#9656; View groups &#9656;
 * Delete (issue #4515): POST only, {@code _admin} write, and a redirect back to the group list
 * instead of the blank page the old include-based delete left.
 *
 * @since 2026-10-15
 */
@DisplayName("DeleteMyGroupMembers2Action")
@Tag("unit")
@Tag("security")
@Tag("delete")
class DeleteMyGroupMembers2ActionUnitTest extends CarlosWebTestBase {

    private static final Path GROUP_LIST_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/provider/providerdisplaymygroup.jsp");
    private static final Path NEW_GROUP_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/provider/providernewgroup.jsp");
    private static final Path SAVE_GROUP_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/provider/providersavemygroup.jsp");
    private static final Path PROVIDER_CONTROL_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/provider/providercontrol.jsp");

    private MyGroupMembershipService membershipService;

    @BeforeEach
    void setUpMembershipService() {
        membershipService = mock(MyGroupMembershipService.class);
        mockRequest.setContextPath("/carlos");
    }

    private DeleteMyGroupMembers2Action action() {
        return new DeleteMyGroupMembers2Action(mockSecurityInfoManager, membershipService);
    }

    private void tickMember() {
        mockRequest.addParameter("PWa1b2c3d4999998", "PWa1b2c3d4");
        mockRequest.addParameter("CSRF-TOKEN", "Q1W2-E3R4-T5Y6-U7I8-O9P0-A1S2-D3F4-G5H6");
    }

    @Test
    @DisplayName("should delete the ticked members and redirect to the group list")
    void shouldDeleteAndRedirectToGroupList_whenDeleteIsPosted() throws Exception {
        mockRequest.setMethod("POST");
        tickMember();

        assertThat(executeAction(action())).isEqualTo(ActionSupport.NONE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String[]>> posted = ArgumentCaptor.forClass(Map.class);
        verify(membershipService).deleteSelectedMembers(posted.capture());
        assertThat(posted.getValue()).containsKeys("PWa1b2c3d4999998", "CSRF-TOKEN");
        assertThat(mockResponse.getRedirectedUrl()).isEqualTo("/carlos/provider/ViewProviderDisplayMyGroup");
        verifySecurityCheck("_admin", "w");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "HEAD", "PUT"})
    @DisplayName("should refuse any method but POST before checking privileges or deleting")
    void shouldReturn405WithoutDeleting_whenMethodIsNotPost(String method) throws Exception {
        mockRequest.setMethod(method);
        tickMember();

        assertThat(executeAction(action())).isEqualTo(ActionSupport.NONE);

        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        assertThat(mockResponse.getRedirectedUrl()).isNull();
        verifyNoInteractions(membershipService);
        verify(mockSecurityInfoManager, never()).hasPrivilege(any(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("should refuse the delete without deleting anything when _admin write is missing")
    void shouldRejectWithoutDeleting_whenAdminWriteMissing() {
        denyPrivilege("_admin", "w");
        mockRequest.setMethod("POST");
        tickMember();

        DeleteMyGroupMembers2Action action = action();
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");
        verifyNoInteractions(membershipService);
    }

    @Test
    @DisplayName("should post Save and Delete straight to their actions instead of through providercontrol")
    void shouldPostGroupMutationsDirectly_insteadOfThroughProviderControlInclude() throws IOException {
        String groupList = Files.readString(GROUP_LIST_JSP, StandardCharsets.UTF_8);
        String newGroup = Files.readString(NEW_GROUP_JSP, StandardCharsets.UTF_8);
        String saveGroup = Files.readString(SAVE_GROUP_JSP, StandardCharsets.UTF_8);
        String providerControl = Files.readString(PROVIDER_CONTROL_JSP, StandardCharsets.UTF_8);

        // A redirect from a page that providercontrol.jsp include()s is ignored: the blank page.
        assertThat(groupList)
                .contains("/provider/DeleteMyGroupMembers\">")
                .contains("/provider/ViewProviderNewGroup\"")
                .doesNotContain("name=\"displaymode\"")
                .doesNotContain("name=\"submit_form\"");
        assertThat(newGroup)
                .contains("/provider/SaveMyGroup\"")
                .doesNotContain("name=\"displaymode\"")
                .doesNotContain("deleteGroupMember")
                .doesNotContain("getParameterNames");
        assertThat(saveGroup)
                .contains("/provider/ViewProviderDisplayMyGroup\"")
                .doesNotContain("displaymode=displaymygroup");
        // The save must run, and redirect, before the page writes any markup.
        assertThat(saveGroup.indexOf("sendRedirect(request.getContextPath() + \"/provider/ViewProviderDisplayMyGroup\")"))
                .isLessThan(saveGroup.indexOf("<html>"));
        assertThat(providerControl).doesNotContain("\"savemygroup\"");
    }
}
