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
 * Reply, Reply All and Forward on a message view open the compose page with a key of its own
 * that starts with the chart items saved from the message's Attachment window, so they go out
 * with the reply or forward and with no other message, and what one compose window attaches
 * stays out of another opened from the same message (issue #4673).
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
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("101");
        message.setSentByNo("101");
        when(createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.MessageTblDao.class).find(MESSAGE_ID))
                .thenReturn(message);
        createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao.class);

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
    @DisplayName("should open the compose page with its own key holding the items saved for this message")
    void shouldHandTheComposePageCopyOfSavedItems_whenReplyingOrForwarding(String button) throws Exception {
        String forwardKey = MsgAttachmentStaging.forwardKey(session, MESSAGE_ID);
        MsgAttachmentStaging.stageItems(session, forwardKey, "<root>FAKE chart item</root>");
        request.addParameter(button, "pressed");

        assertThat(execute()).isEqualTo("reply");
        assertThat(request.getAttribute("archiveMessageId")).isEqualTo(MESSAGE_ID);

        String handedOver = (String) request.getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE);
        assertThat(MsgAttachmentStaging.isKey(handedOver)).isTrue();
        assertThat(handedOver).isNotEqualTo(forwardKey);
        assertThat(MsgAttachmentStaging.peek(session, handedOver).itemAttachment())
                .isEqualTo("<root>FAKE chart item</root>");
        assertThat(MsgAttachmentStaging.peek(session, forwardKey).itemAttachment())
                .as("the saved items stay for the message's other compose windows")
                .isEqualTo("<root>FAKE chart item</root>");
    }

    @Test
    @DisplayName("should keep each reply's chart PDFs to its own window when two replies are opened from one message")
    void shouldKeepPdfsToTheirOwnReply_whenTwoRepliesAreOpenedFromOneMessage() throws Exception {
        String forwardKey = MsgAttachmentStaging.forwardKey(session, MESSAGE_ID);
        MsgAttachmentStaging.stageItems(session, forwardKey, "<root>FAKE chart item</root>");
        request.addParameter("reply", "pressed");
        execute();
        String first = (String) request.getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE);
        request.removeAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE);
        execute();
        String second = (String) request.getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE);

        MsgAttachmentStaging.stagePdfs(session, first, 900001, "FAKE pdf record");

        assertThat(second).isNotEqualTo(first);
        assertThat(MsgAttachmentStaging.peek(session, first).pdfAttachment()).isEqualTo("FAKE pdf record");
        assertThat(MsgAttachmentStaging.peek(session, second).pdfAttachment()).isNull();
        assertThat(MsgAttachmentStaging.peek(session, forwardKey).pdfAttachment()).isNull();
        assertThat(MsgAttachmentStaging.peek(session, second).itemAttachment())
                .isEqualTo("<root>FAKE chart item</root>");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"reply", "replyAll", "forward"})
    @DisplayName("should reconstruct a reply or forward on GET while retaining its compose attachments")
    void shouldRetainComposeAttachments_whenReloadingReplyOrForward(String operation) throws Exception {
        String composeKey = MsgAttachmentStaging.newKey();
        MsgAttachmentStaging.stageItems(session, composeKey, "<root>FAKE own item</root>");
        MsgAttachmentStaging.stagePdfs(session, composeKey, 900001, "FAKE own PDF");
        request.setMethod("GET");
        request.addParameter(operation, "1");
        request.addParameter(MsgAttachmentStaging.KEY_PARAMETER, composeKey);

        assertThat(execute()).isEqualTo("reply");

        assertThat(request.getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE)).isEqualTo(composeKey);
        assertThat(request.getAttribute("ReSubject")).isEqualTo(
                "forward".equals(operation) ? "Fwd:FAKE subject" : "Re:FAKE subject");
        assertThat((String) request.getAttribute("ReText")).contains("FAKE body");
        assertThat(MsgAttachmentStaging.peek(session, composeKey).itemAttachment())
                .isEqualTo("<root>FAKE own item</root>");
        assertThat(MsgAttachmentStaging.peek(session, composeKey).pdfAttachment())
                .isEqualTo("FAKE own PDF");
    }

    @Test
    @DisplayName("should open the compose page with nothing staged when nothing was saved for this message")
    void shouldHandOverEmptyKey_whenNothingWasSavedForThisMessage() throws Exception {
        request.addParameter("forward", "pressed");

        assertThat(execute()).isEqualTo("reply");

        String handedOver = (String) request.getAttribute(MsgAttachmentStaging.KEY_ATTRIBUTE);
        assertThat(MsgAttachmentStaging.isKey(handedOver)).isTrue();
        assertThat(MsgAttachmentStaging.peek(session, handedOver)).isNull();
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
