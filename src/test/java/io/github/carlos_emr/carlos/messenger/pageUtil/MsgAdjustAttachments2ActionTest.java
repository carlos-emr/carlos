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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MsgAdjustAttachments2Action}.
 *
 * <p>Covers privilege enforcement, POST-only method check, the xmlDoc null
 * guard, the invalid-session-bean fallback path, and the forwarding hand-over:
 * the selection is staged under the key of the message view it is forwarded
 * from, for the compose page that view's Compose link opens (issue #4673).
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
    @DisplayName("should reject non-POST with 405 and Allow: POST header")
    void shouldReturn405_whenMethodIsNotPost() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("GET");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        assertThat(getMockResponse().getHeader("Allow")).isEqualTo("POST");
    }

    @Test
    @DisplayName("should redirect to DisplayMessages when session bean is missing")
    void shouldRedirect_whenSessionBeanIsMissing() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("POST");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getRedirectedUrl()).contains("/messenger/DisplayMessages");
    }

    @Test
    @DisplayName("should return 400 when xmlDoc param is missing")
    void shouldReturn400_whenXmlDocMissing() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("POST");
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo(TEST_PROVIDER);
        getMockSession().setAttribute("msgSessionBean", bean);
        addRequestParameter(MsgAttachmentStaging.KEY_PARAMETER, MsgAttachmentStaging.newKey());

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertNothingStaged();
    }

    @Test
    @DisplayName("should stage the selection for its own message view's compose and hand over to the patient search")
    void shouldStageSelectionForItsMessageView_whenPostIsValid() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("POST");
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo(TEST_PROVIDER);
        getMockSession().setAttribute("msgSessionBean", bean);
        String forwardKey = MsgAttachmentStaging.newKey();

        addRequestParameter("xmlDoc", MsgCommxml.encode64(TWO_ITEM_XML));
        addRequestParameter("id", "42");
        addRequestParameter("item1", "on");
        addRequestParameter(MsgAttachmentStaging.KEY_PARAMETER, forwardKey);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getRedirectedUrl())
                .endsWith("/demographic/DemographicLinkMsg");
        MsgAttachmentStaging.Staged staged = MsgAttachmentStaging.peek(getMockSession(), forwardKey);
        assertThat(staged).isNotNull();
        assertThat(staged.itemAttachment()).contains("itemId=\"1\"").doesNotContain("itemId=\"2\"");
        assertThat(staged.pdfAttachment()).isNull();
        // Issue #4673: on the login-wide bean the selection went out with the next message sent
        // from any compose window, for any patient.
        assertThat(bean.getAttachment()).isNull();
    }

    @Test
    @DisplayName("should keep each message view's selection for its own compose when two views forward")
    void shouldKeepEachViewsSelection_whenTwoMessageViewsForward() throws Exception {
        allowPrivilege("_msg", "w");
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo(TEST_PROVIDER);
        getMockSession().setAttribute("msgSessionBean", bean);
        String firstView = MsgAttachmentStaging.newKey();
        String secondView = MsgAttachmentStaging.newKey();

        forward(firstView, "item1");
        forward(secondView, "item2");

        assertThat(MsgAttachmentStaging.peek(getMockSession(), firstView).itemAttachment())
                .contains("itemId=\"1\"").doesNotContain("itemId=\"2\"");
        assertThat(MsgAttachmentStaging.peek(getMockSession(), secondView).itemAttachment())
                .contains("itemId=\"2\"").doesNotContain("itemId=\"1\"");
    }

    @Test
    @DisplayName("should refuse a selection without its message view's key and stage nothing")
    void shouldRefuseAndStageNothing_whenMessageViewKeyIsMissingOrMalformed() throws Exception {
        allowPrivilege("_msg", "w");
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo(TEST_PROVIDER);
        getMockSession().setAttribute("msgSessionBean", bean);

        for (String key : new String[]{null, "", "not-a-key", MsgAttachmentStaging.newKey() + "x"}) {
            nextRequest();
            addRequestParameter("xmlDoc", MsgCommxml.encode64(TWO_ITEM_XML));
            addRequestParameter("item1", "on");
            if (key != null) {
                addRequestParameter(MsgAttachmentStaging.KEY_PARAMETER, key);
            }

            String result = executeAction(action);

            assertThat(result).as(String.valueOf(key)).isEqualTo(ActionSupport.NONE);
            assertThat(getMockResponse().getStatus()).as(String.valueOf(key))
                    .isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            assertNothingStaged();
        }
    }

    @Test
    @DisplayName("should return 400 when xmlDoc decodes to malformed XML")
    void shouldReturn400_whenXmlDocIsMalformed() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("POST");
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo(TEST_PROVIDER);
        getMockSession().setAttribute("msgSessionBean", bean);

        // "this is not xml" encoded — decodes to non-empty garbage that XML
        // parsing rejects (parseXML returns null, previously NPE -> 500).
        addRequestParameter("xmlDoc", MsgCommxml.encode64("not xml"));
        addRequestParameter(MsgAttachmentStaging.KEY_PARAMETER, MsgAttachmentStaging.newKey());

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
        assertThat(bean.getAttachment()).isNull();
        assertNothingStaged();
    }

    @Test
    @DisplayName("should silently ignore non-digit item* parameter suffix")
    void shouldIgnoreNonNumericItemSuffix_forItemParameters() throws Exception {
        allowPrivilege("_msg", "w");
        getMockRequest().setMethod("POST");
        MsgSessionBean bean = new MsgSessionBean();
        bean.setProviderNo(TEST_PROVIDER);
        getMockSession().setAttribute("msgSessionBean", bean);

        String forwardKey = MsgAttachmentStaging.newKey();
        addRequestParameter("xmlDoc", MsgCommxml.encode64(SAMPLE_XML));
        // itemfoo is ignored; item1 is kept.
        addRequestParameter("itemfoo", "on");
        addRequestParameter("item1", "on");
        addRequestParameter(MsgAttachmentStaging.KEY_PARAMETER, forwardKey);

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getRedirectedUrl())
                .endsWith("/demographic/DemographicLinkMsg");
        assertThat(MsgAttachmentStaging.peek(getMockSession(), forwardKey).itemAttachment())
                .contains("itemId=\"1\"");
        assertThat(bean.getAttachment()).isNull();
    }

    /** Posts Save Attachments from the message view with {@code forwardKey}, ticking {@code item}. */
    private void forward(String forwardKey, String item) throws Exception {
        nextRequest();
        addRequestParameter("xmlDoc", MsgCommxml.encode64(TWO_ITEM_XML));
        addRequestParameter(item, "on");
        addRequestParameter(MsgAttachmentStaging.KEY_PARAMETER, forwardKey);
        assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getRedirectedUrl()).endsWith("/demographic/DemographicLinkMsg");
    }

    /** A new POST in the same login session. */
    private void nextRequest() {
        mockRequest = new MockHttpServletRequest();
        mockRequest.setSession(mockSession);
        mockRequest.setMethod("POST");
        mockResponse = new MockHttpServletResponse();
        requestParameters.clear();
    }

    private void assertNothingStaged() {
        assertThat(getMockSession().getAttributeNames().asIterator())
                .toIterable().noneMatch(name -> name.startsWith(MsgAttachmentStaging.class.getName()));
    }

    private static final String SAMPLE_XML =
            "<root><table><item itemId=\"1\" removable=\"true\">a</item></table></root>";

    private static final String TWO_ITEM_XML = "<root><table><item itemId=\"1\" removable=\"true\">a</item>"
            + "<item itemId=\"2\" removable=\"true\">b</item></table></root>";
}
