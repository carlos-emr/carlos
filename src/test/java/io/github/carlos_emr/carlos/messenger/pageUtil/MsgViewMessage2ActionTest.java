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

import org.apache.struts2.ActionSupport;
import io.github.carlos_emr.carlos.managers.MessagingManager;
import io.github.carlos_emr.carlos.managers.MessengerDemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.messenger.data.MsgDisplayMessage;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.mockito.*;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link MsgViewMessage2Action}.
 *
 * <p>Validates security enforcement, redirect on invalid/zero message ID
 * (ConversionUtils fix), and mark-as-read skip when boxType is sent.</p>
 *
 * @since 2026-02-20
 */
@DisplayName("MsgViewMessage2Action Tests")
@Tag("integration")
@Tag("messenger")
class MsgViewMessage2ActionTest extends CarlosWebTestBase {

    @Mock
    private MessagingManager mockMessagingManager;

    @Mock
    private MessengerDemographicManager mockDemoManager;

    private MsgViewMessage2Action action;

    private static final String TEST_PROVIDER = "999998";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(MessagingManager.class, mockMessagingManager);
        replaceSpringUtilsBean(MessengerDemographicManager.class, mockDemoManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(loggedInInfoKey, mockLoggedInInfo);
        setSessionAttribute("user", TEST_PROVIDER);

        action = new MsgViewMessage2Action();

        injectField("securityInfoManager", mockSecurityInfoManager);
        injectField("messagingManager", mockMessagingManager);
        injectField("messengerDemographicManager", mockDemoManager);
    }

    private void injectField(String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = MsgViewMessage2Action.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(action, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject " + fieldName, e);
        }
    }

    @Test
    @DisplayName("should throw SecurityException when no session exists")
    void shouldThrowSecurityException_whenNoSession() {
        // Given
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(loggedInInfoKey, null);

        // When/Then
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("No valid session");
    }

    @Test
    @DisplayName("should throw SecurityException when read privilege is denied")
    void shouldThrowSecurityException_whenReadPrivilegeDenied() {
        // Given
        denyPrivilege("_msg", "r");

        // When/Then
        assertThatThrownBy(() -> executeAction(action))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_msg");
    }

    @Test
    @DisplayName("should redirect when message ID is zero or invalid")
    void shouldRedirect_whenMessageIdIsZeroOrInvalid() throws Exception {
        // Given - invalid messageID parameter (ConversionUtils returns 0)
        allowPrivilege("_msg", "r");
        addRequestParameter("messageID", "invalid");

        // When
        String result = executeAction(action);

        // Then - should redirect (return NONE)
        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getRedirectedUrl()).contains("DisplayMessages.jsp");
    }

    @Test
    @DisplayName("should redirect when message is not found")
    void shouldRedirect_whenMessageNotFound() throws Exception {
        // Given - valid ID but no message exists
        allowPrivilege("_msg", "r");
        addRequestParameter("messageID", "12345");
        when(mockMessagingManager.getInboxMessage(any(LoggedInInfo.class), eq(12345)))
                .thenReturn(null);

        // When
        String result = executeAction(action);

        // Then
        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(getMockResponse().getRedirectedUrl()).contains("DisplayMessages.jsp");
    }

    @Test
    @DisplayName("should not mark as read when boxType is sent")
    void shouldNotMarkAsRead_whenBoxTypeIsSent() throws Exception {
        // Given
        allowPrivilege("_msg", "r");
        addRequestParameter("messageID", "100");
        addRequestParameter("boxType", "1"); // sent items

        MsgDisplayMessage msg = createMockMessage("100");
        when(mockMessagingManager.getInboxMessage(any(LoggedInInfo.class), eq(100)))
                .thenReturn(msg);
        when(mockDemoManager.getAttachedDemographicNameMap(any(), anyInt()))
                .thenReturn(new HashMap<>());

        // When
        executeAction(action);

        // Then - setMessageRead should NOT be called for sent items
        verify(mockMessagingManager, never()).setMessageRead(any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("should mark message as read when boxType is not sent")
    void shouldMarkMessageAsRead_whenBoxTypeIsNotSent() throws Exception {
        // Given - inbox view (no boxType param means inbox)
        allowPrivilege("_msg", "r");
        addRequestParameter("messageID", "100");

        MsgDisplayMessage msg = createMockMessage("100");
        when(mockMessagingManager.getInboxMessage(any(LoggedInInfo.class), eq(100)))
                .thenReturn(msg);
        when(mockDemoManager.getAttachedDemographicNameMap(any(), anyInt()))
                .thenReturn(new HashMap<>());

        // When
        executeAction(action);

        // Then - setMessageRead SHOULD be called for inbox items
        verify(mockMessagingManager).setMessageRead(any(), eq(100L), eq(TEST_PROVIDER));
    }

    @Test
    @DisplayName("should not attach demographic when demoNo is zero")
    void shouldNotAttachDemographic_whenDemoNoIsZero() throws Exception {
        // Given
        allowPrivilege("_msg", "r");
        addRequestParameter("messageID", "100");
        addRequestParameter("linkMsgDemo", "true");
        addRequestParameter("demographic_no", "invalid"); // converts to 0

        MsgDisplayMessage msg = createMockMessage("100");
        when(mockMessagingManager.getInboxMessage(any(LoggedInInfo.class), eq(100)))
                .thenReturn(msg);
        when(mockDemoManager.getAttachedDemographicNameMap(any(), anyInt()))
                .thenReturn(new HashMap<>());

        // When
        executeAction(action);

        // Then - attachDemographicToMessage should NOT be called since demoNo is 0
        verify(mockDemoManager, never()).attachDemographicToMessage(any(), anyInt(), anyInt());
    }

    private MsgDisplayMessage createMockMessage(String messageId) {
        MsgDisplayMessage msg = new MsgDisplayMessage();
        msg.setMessageId(messageId);
        msg.setThesubject("Test Subject");
        msg.setSentby("TestSender");
        msg.setSentto("TestRecipient");
        msg.setThetime("10:00");
        msg.setThedate("2026-01-15");
        msg.setAttach("0");
        msg.setPdfAttach("0");
        msg.setMessageBody("Test message body");
        msg.setType(0);
        msg.setTypeLink(null);
        return msg;
    }
}
