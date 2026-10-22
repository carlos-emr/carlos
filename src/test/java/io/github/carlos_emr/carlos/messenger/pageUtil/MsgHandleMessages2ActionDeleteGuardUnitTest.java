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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
 * Deleting a message from its view is the only write on {@code messenger/HandleMessages}: Reply,
 * Reply All and Forward just open the compose page. The route's name names no verb the method guard
 * knows and the write rides on a {@code delete} parameter, so before this guard a GET (which carries no
 * CSRF token) deleted the message (issue #3394).
 *
 * @since 2026-10-22
 */
@Tag("unit")
@Tag("security")
@Tag("messenger")
@DisplayName("MsgHandleMessages2Action deletes a message only on POST")
class MsgHandleMessages2ActionDeleteGuardUnitTest extends CarlosUnitTestBase {

    private static final int MESSAGE_ID = 100;

    private MessagingManager messagingManager;
    private MessengerDemographicManager messengerDemographicManager;
    private LoggedInInfo loggedInInfo;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityInfoManager securityInfoManager = createAndRegisterMock(SecurityInfoManager.class);
        messagingManager = createAndRegisterMock(MessagingManager.class);
        messengerDemographicManager = createAndRegisterMock(MessengerDemographicManager.class);

        loggedInInfo = mock(LoggedInInfo.class);
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

        request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        response = new MockHttpServletResponse();
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

    /** Builds the action as Struts would: the request in place first, then the bound parameters. */
    private String execute() throws Exception {
        MsgHandleMessages2Action action = new MsgHandleMessages2Action();
        action.setMessageNo(String.valueOf(MESSAGE_ID));
        String delete = request.getParameter("delete");
        if (delete != null) {
            action.setDelete(delete);
        }
        return action.execute();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT"})
    @DisplayName("should refuse a delete that is not posted, before touching the message")
    void shouldRefuseDelete_whenNotPosted(String httpMethod) throws Exception {
        request.setMethod(httpMethod);
        request.addParameter("delete", "Delete");

        assertThat(execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(messagingManager, messengerDemographicManager);
    }

    @Test
    @DisplayName("should refuse a GET delete whatever the button's value")
    void shouldRefuseDelete_whenGetCarriesAnyValue() throws Exception {
        request.setMethod("GET");
        request.addParameter("delete", "");

        assertThat(execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(messagingManager);
    }

    @Test
    @DisplayName("should refuse a GET whose delete reached the action only through Struts binding")
    void shouldRefuseDelete_whenBoundWithoutTheExactParameter() throws Exception {
        // E.g. a differently cased parameter name that the binder still maps to setDelete.
        request.setMethod("GET");
        MsgHandleMessages2Action action = new MsgHandleMessages2Action();
        action.setMessageNo(String.valueOf(MESSAGE_ID));
        action.setDelete("Delete");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(messagingManager, messengerDemographicManager);
    }

    @Test
    @DisplayName("should delete the message when the view's form posts Delete")
    void shouldDeleteMessage_whenPosted() throws Exception {
        request.setMethod("POST");
        request.addParameter("delete", "Delete");

        assertThat(execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(messagingManager).deleteMessage(loggedInInfo, MESSAGE_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reply", "replyAll", "forward"})
    @DisplayName("should still open the compose page by GET for Reply, Reply All and Forward")
    void shouldOpenCompose_whenGetRepliesOrForwards(String button) throws Exception {
        request.setMethod("GET");
        request.addParameter(button, "pressed");

        assertThat(execute()).isEqualTo("reply");
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
