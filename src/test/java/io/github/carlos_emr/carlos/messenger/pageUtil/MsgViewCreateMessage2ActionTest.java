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
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

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
 * Unit tests for {@link MsgViewCreateMessage2Action}.
 *
 * <p>Covers the privilege-denied path, the success forward and the compose key the page is
 * handed: the transfer step's key, or a new key holding a copy of the items saved for the message
 * a view's Compose link names.
 *
 * @since 2026-04-13
 */
@DisplayName("MsgViewCreateMessage2Action Tests")
@Tag("integration")
@Tag("messenger")
class MsgViewCreateMessage2ActionTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";
    private MsgViewCreateMessage2Action action;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        action = new MsgViewCreateMessage2Action();
        java.lang.reflect.Field f = MsgViewCreateMessage2Action.class.getDeclaredField("securityInfoManager");
        f.setAccessible(true);
        f.set(action, mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should throw SecurityException when _msg read privilege is denied")
    void shouldThrowSecurityException_whenReadPrivilegeDenied() {
        denyPrivilege("_msg", "r");

        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_msg");

        verifySecurityCheck("_msg", "r");
    }

    @Test
    @DisplayName("should return SUCCESS when _msg read privilege is granted")
    void shouldReturnSuccess_whenReadPrivilegeGranted() throws Exception {
        allowPrivilege("_msg", "r");

        String result = executeAction(action);

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        // A plain visit starts a new message: the compose page makes its own key.
        assertThat(getMockRequest().getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("should hand the compose page the key the transferred items were staged under")
    void shouldHandOverComposeKey_whenRedirectedFromTransfer() throws Exception {
        allowPrivilege("_msg", "r");
        String key = MsgAttachmentStaging.newKey();
        getMockRequest().setParameter(MsgAttachmentStaging.KEY_PARAMETER, key);

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

        assertThat(getMockRequest().getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE)).isEqualTo(key);
    }

    @Test
    @DisplayName("should hand the compose page its own key holding the items saved for the message its Compose link names")
    void shouldHandOverCopyOfSavedItems_whenComposeLinkNamesItsMessage() throws Exception {
        allowPrivilege("_msg", "r");
        String forwardKey = MsgAttachmentStaging.forwardKey(getMockSession(), 100);
        MsgAttachmentStaging.stageItems(getMockSession(), forwardKey, "<root>FAKE chart item</root>");
        getMockRequest().setParameter(MsgViewCreateMessage2Action.FORWARD_MESSAGE_PARAMETER, "100");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

        String handedOver = (String) getMockRequest().getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE);
        assertThat(handedOver).isNotEqualTo(forwardKey);
        assertThat(MsgAttachmentStaging.peek(getMockSession(), handedOver).itemAttachment())
                .isEqualTo("<root>FAKE chart item</root>");
    }

    @Test
    @DisplayName("should start a new message when the Compose link names no valid message")
    void shouldStartNewMessage_whenForwardMessageIsNotAMessageId() throws Exception {
        allowPrivilege("_msg", "r");
        for (String value : new String[] {"0", "-1", "abc", "01", "99999999999"}) {
            getMockRequest().setParameter(MsgViewCreateMessage2Action.FORWARD_MESSAGE_PARAMETER, value);

            assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

            assertThat(getMockRequest().getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE)).as(value).isNull();
        }
    }

    @Test
    @DisplayName("should ignore a malformed compose key")
    void shouldIgnoreComposeKey_whenMalformed() throws Exception {
        allowPrivilege("_msg", "r");
        getMockRequest().setParameter(MsgAttachmentStaging.KEY_PARAMETER, "<script>");

        assertThat(executeAction(action)).isEqualTo(ActionSupport.SUCCESS);

        assertThat(getMockRequest().getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE)).isNull();
    }
}
