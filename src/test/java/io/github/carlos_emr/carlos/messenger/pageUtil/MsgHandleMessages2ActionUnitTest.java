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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.managers.MessagingManager;
import io.github.carlos_emr.carlos.managers.MessengerDemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Reply, Reply All and Forward on a message view open the compose page with that message's
 * forwarding key, so chart items saved from the message's Attachment window go out with the
 * reply or forward, and with no other message (issue #4673).
 *
 * @since 2026-10-17
 */
@Tag("unit")
@Tag("messenger")
@DisplayName("MsgHandleMessages2Action hands a reply or forward its message's saved attachments")
class MsgHandleMessages2ActionUnitTest extends CarlosUnitTestBase {

    private static final int MESSAGE_ID = 100;

    private final MockHttpSession session = new MockHttpSession();
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        MessagingManager messagingManager = createAndRegisterMock(MessagingManager.class);
        MessengerDemographicManager messengerDemographicManager =
                createAndRegisterMock(MessengerDemographicManager.class);

        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_msg"), eq("r"), isNull())).thenReturn(true);
        MessageTbl message = new MessageTbl();
        message.setId(MESSAGE_ID);
        message.setSubject("FAKE subject");
        message.setMessage("FAKE body");
        message.setSentBy("FAKE sender");
        message.setDate(new Date());
        message.setTime(new Date());
        when(messagingManager.getMessage(loggedInInfo, MESSAGE_ID)).thenReturn(message);
        when(messengerDemographicManager.getAttachedDemographicList(loggedInInfo, MESSAGE_ID)).thenReturn(List.of());

        request = new MockHttpServletRequest("POST", "/carlos/messenger/HandleMessages");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"reply", "replyAll", "forward"})
    @DisplayName("should open the compose page with the items saved for this message")
    void shouldHandTheComposePageThisMessagesForwardKey_whenReplyingOrForwarding(String button) throws Exception {
        String forwardKey = MsgAttachmentStaging.forwardKey(session, MESSAGE_ID);
        MsgAttachmentStaging.stageItems(session, forwardKey, "<root>FAKE chart item</root>");
        request.addParameter(button, "pressed");

        assertThat(execute()).isEqualTo("reply");

        String handedOver = (String) request.getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE);
        assertThat(handedOver).isEqualTo(forwardKey);
        assertThat(MsgAttachmentStaging.peek(session, handedOver).itemAttachment())
                .isEqualTo("<root>FAKE chart item</root>");
    }

    @Test
    @DisplayName("should hand no compose key over when deleting")
    void shouldHandOverNoComposeKey_whenDeleting() throws Exception {
        request.addParameter("delete", "pressed");

        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(request.getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE)).isNull();
    }

    private String execute() throws Exception {
        // Constructed after the static mocks: the action captures the request and its managers in
        // field initializers.
        MsgHandleMessages2Action action = new MsgHandleMessages2Action();
        action.setMessageNo(String.valueOf(MESSAGE_ID));
        return action.execute();
    }
}
