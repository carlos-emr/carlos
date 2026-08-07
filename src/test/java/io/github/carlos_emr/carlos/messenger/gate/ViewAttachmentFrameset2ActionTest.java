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
package io.github.carlos_emr.carlos.messenger.gate;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ViewAttachmentFrameset2Action}.
 *
 * <p>The action is the only public gate in front of the attachment preview
 * frameset, so its privilege and method checks must stay intact to avoid
 * re-exposing the protected messenger popup host.
 *
 * @since 2026-04-15
 */
@DisplayName("ViewAttachmentFrameset2Action Tests")
@Tag("integration")
@Tag("messenger")
class ViewAttachmentFrameset2ActionTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";

    private ViewAttachmentFrameset2Action action;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        action = new ViewAttachmentFrameset2Action();
        java.lang.reflect.Field f =
                ViewAttachmentFrameset2Action.class.getDeclaredField("securityInfoManager");
        f.setAccessible(true);
        f.set(action, mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should throw SecurityException when _msg read privilege is denied")
    void shouldThrowSecurityException_whenReadPrivilegeDenied() {
        denyPrivilege("_msg", "r");
        getMockRequest().setMethod("GET");

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_msg");

        verifySecurityCheck("_msg", "r");
    }

    @Test
    @DisplayName("should return SUCCESS when _msg read privilege is granted for GET")
    void shouldReturnSuccess_whenReadPrivilegeGrantedForGet() throws Exception {
        allowPrivilege("_msg", "r");
        getMockRequest().setMethod("GET");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
    }

    @Test
    @DisplayName("should return 405 and Allow header for disallowed methods")
    void shouldReturn405AndAllowHeader_whenMethodIsNotGetOrHead() throws Exception {
        allowPrivilege("_msg", "r");
        getMockRequest().setMethod("POST");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(getMockResponse().getHeader("Allow")).isEqualTo("GET, HEAD");
    }
}
