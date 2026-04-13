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
package io.github.carlos_emr.carlos.messenger.pageUtil;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.messenger.docxfer.util.MsgCommxml;
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
 * Unit tests for {@link MsgAdjustAttachments2Action}.
 *
 * <p>Covers privilege enforcement, POST-only method check, the xmlDoc null
 * guard, and the invalid-session-bean fallback path.
 *
 * @since 2026-04-13
 */
@DisplayName("MsgAdjustAttachments2Action Tests")
@Tag("integration")
@Tag("messenger")
class MsgAdjustAttachments2ActionTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";
    private MsgAdjustAttachments2Action action;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        action = new MsgAdjustAttachments2Action();
        java.lang.reflect.Field f = MsgAdjustAttachments2Action.class.getDeclaredField("securityInfoManager");
        f.setAccessible(true);
        f.set(action, mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should throw SecurityException when _msg write privilege is denied")
    void shouldThrowSecurityException_whenWritePrivilegeDenied() {
        denyPrivilege("_msg", "w");
        getMockRequest().setMethod("POST");

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_msg");
    }

    @Test
    @DisplayName("should reject non-POST with 405")
    void shouldReturn405_whenMethodIsNotPost() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("GET");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("should redirect to DisplayMessages when session bean is missing")
    void shouldRedirect_whenSessionBeanIsMissing() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("POST");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getRedirectedUrl()).contains("/messenger/DisplayMessages.do");
    }

    @Test
    @DisplayName("should return 400 when xmlDoc param is missing")
    void shouldReturn400_whenXmlDocMissing() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("POST");
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo(TEST_PROVIDER);
        getMockSession().setAttribute("msgSessionBean", bean);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    @DisplayName("should mutate session bean and redirect to DemographicLinkMsg on valid POST")
    void shouldMutateBean_andRedirectToDemographicLinkMsg_whenPostIsValid() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("POST");
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo(TEST_PROVIDER);
        getMockSession().setAttribute("msgSessionBean", bean);

        String encodedDoc = MsgCommxml.encode64(SAMPLE_XML);
        addRequestParameter("xmlDoc", encodedDoc);
        addRequestParameter("id", "42");
        addRequestParameter("item1", "on");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getRedirectedUrl())
                .endsWith("/demographic/DemographicLinkMsg.do");
        assertThat(bean.getAttachment()).isNotNull();
        assertThat(bean.getMessageId()).isEqualTo("42");
    }

    @Test
    @DisplayName("should set bean messageId to null when id param is not digits")
    void shouldNullifyMessageId_whenIdIsNonNumeric() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("POST");
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo(TEST_PROVIDER);
        getMockSession().setAttribute("msgSessionBean", bean);

        addRequestParameter("xmlDoc", MsgCommxml.encode64(SAMPLE_XML));
        addRequestParameter("id", "42; DROP TABLE demographic");

        executeAction(action);

        // MsgSessionBean.getMessageId() normalizes null to an empty string; assert
        // the non-numeric input did not survive intact onto the bean.
        assertThat(bean.getMessageId()).isEmpty();
    }

    private static final String SAMPLE_XML =
            "<root><table><item itemId=\"1\" removable=\"true\">a</item></table></root>";
}
