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

import java.util.Date;

import io.github.carlos_emr.carlos.commn.model.MessageTbl;
import io.github.carlos_emr.carlos.managers.MessagingManager;
import io.github.carlos_emr.carlos.managers.MessengerDemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Issue #4821: Messenger Forward replaced each line break of the original message with {@code >}, so a
 * message stored with {@code \n} breaks arrived as one line. Forward now quotes each line the way Reply does.
 *
 * @since 2026-10-17
 */
@Tag("unit")
@Tag("messenger")
@DisplayName("Messenger Forward and Reply quote every original line (#4821)")
class MsgHandleMessages2ActionQuoteUnitTest extends CarlosUnitTestBase {

    private static final int MESSAGE_NO = 5;

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/messenger/HandleMessages");
    private MessagingManager messagingManager;
    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;

    @BeforeEach
    void setUp() {
        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_msg"), eq("r"), isNull())).thenReturn(true);
        messagingManager = createAndRegisterMock(MessagingManager.class);
        createAndRegisterMock(MessengerDemographicManager.class);

        // The action reads the request and its managers when it is constructed.
        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
        loggedInInfoStatic = mockStatic(LoggedInInfo.class);
        loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mock(LoggedInInfo.class));
    }

    @AfterEach
    void tearDown() {
        loggedInInfoStatic.close();
        servletActionContext.close();
    }

    /** Presses {@code button} on a stored message with the given text and returns the draft the compose page gets. */
    private String draft(String button, String text) throws Exception {
        MessageTbl message = new MessageTbl();
        message.setMessage(text);
        message.setSubject("FAKE-PW team");
        message.setSentBy("FAKE-PW sender");
        message.setDate(new Date());
        message.setTime(new Date());
        when(messagingManager.getMessage(any(LoggedInInfo.class), eq(MESSAGE_NO))).thenReturn(message);
        request.setParameter(button, button);

        MsgHandleMessages2Action action = new MsgHandleMessages2Action();
        action.setMessageNo(String.valueOf(MESSAGE_NO));
        assertThat(action.execute()).isEqualTo("reply");
        return (String) request.getAttribute("ReText");
    }

    @Test
    @DisplayName("should quote every line when forwarding a message stored with \\n line breaks")
    void shouldQuoteEveryLine_whenForwardingLfMessage() throws Exception {
        assertThat(draft("forward", "FAKE-PW first\nFAKE-PW second\nFAKE-PW third"))
                .isEqualTo("\n\n\n>FAKE-PW first\n>FAKE-PW second\n>FAKE-PW third");
        assertThat(request.getAttribute("ReSubject")).isEqualTo("Fwd:FAKE-PW team");
    }

    @Test
    @DisplayName("should quote every line, with no stray carriage return before a quote mark, for \\r\\n line breaks")
    void shouldQuoteEveryLine_whenForwardingCrlfMessage() throws Exception {
        // A message typed in the browser is stored with the textarea's \r\n line breaks.
        String text = draft("forward", "FAKE-PW first\r\nFAKE-PW second\r\nFAKE-PW third");

        assertThat(text).isEqualTo("\n\n\n>FAKE-PW first\r\n>FAKE-PW second\r\n>FAKE-PW third");
        assertThat(text).doesNotContain("\r>");
    }

    @Test
    @DisplayName("should still quote every line on Reply")
    void shouldQuoteEveryLine_whenReplying() throws Exception {
        assertThat(draft("reply", "FAKE-PW first\nFAKE-PW second"))
                .endsWith(" wrote: \n>FAKE-PW first\n>FAKE-PW second");
    }
}
